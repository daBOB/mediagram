// Fetches odd amazon.de articles for Preisfrage and stores the list and photos on the home cache
// server, or in a folder with `--dry-run DIR`. Run by hand on a PC: `bun run fetch`.
// Polite: one page every 4-8 s, cookies declined, no more searching after a robot check.
import { mkdirSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { parseArgs } from "node:util";
import { type Article, type Skip, sortResult } from "./articles";
import { putBlob, readCacheConfig } from "./cache-upload";
import { planSearches, searchUrl } from "./search-plan";
import { launchBrowser, readSearchPage } from "./search-results";
import terms from "./terms.json";

/** Fewer than this and last week's list is left in place. */
const MIN_ARTICLES = 60;
const PAUSE_MS = [4000, 8000];
const USER_AGENT =
  "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36";

const { values: args } = parseArgs({
  options: {
    "dry-run": { type: "string" },
    searches: { type: "string", default: "16" },
    config: { type: "string", default: join(homedir(), ".config/mediagram/config.toml") },
  },
});
const dryRun = args["dry-run"];
// Read first, so a missing server fails before Amazon is asked anything.
const cache = dryRun ? null : readCacheConfig(args.config!);
const store = async (name: string, body: Uint8Array<ArrayBuffer>, type: string) => {
  if (cache) await putBlob(cache, `/v1/game/preisfrage/${name}`, body, type);
  else writeFileSync(join(dryRun!, name), body);
};
if (dryRun) mkdirSync(join(dryRun, "pages"), { recursive: true });

const browser = await launchBrowser();
const context = await browser.newContext({
  locale: "de-DE",
  timezoneId: "Europe/Berlin",
  viewport: { width: 1366, height: 900 },
  userAgent: USER_AGENT,
});
const page = await context.newPage();
const articles: Article[] = [];
const held = new Set<string>();
let robotCheck = false;

for (const [i, search] of planSearches(terms, Number(args.searches)).entries()) {
  if (i > 0) await page.waitForTimeout(PAUSE_MS[0] + Math.random() * (PAUSE_MS[1] - PAUSE_MS[0]));
  const label = `"${search.term}" page ${search.page}${search.newest ? " newest" : ""} ${search.band[0] / 100}-${search.band[1] / 100} €`;
  const res = await page.goto(searchUrl(search), { waitUntil: "domcontentloaded", timeout: 30000 }).catch((e) => {
    console.log(`${label}: ${e.message}`);
    return undefined;
  });
  if (res === undefined) continue;
  const cards = await readSearchPage(page);
  if (cards === "robot check") {
    const shot = join(dryRun ?? ".", "robot-check.png");
    await page.screenshot({ path: shot });
    console.log(`${label}: robot check (${shot}). No more searches this run.`);
    robotCheck = true;
    break;
  }
  await page.locator("#sp-cc-rejectall-link").click({ timeout: 2000 }).catch(() => {});
  if (dryRun) writeFileSync(join(dryRun, "pages", `${String(i + 1).padStart(2, "0")}.html`), await page.content());
  const skipped: Partial<Record<Skip, number>> = {};
  let kept = 0;
  for (const card of cards) {
    const sorted = sortResult(card, held);
    if (typeof sorted === "string") {
      skipped[sorted] = (skipped[sorted] ?? 0) + 1;
    } else {
      articles.push(sorted);
      kept++;
    }
  }
  const skips = Object.entries(skipped).map(([why, n]) => `${n} ${why}`);
  console.log(`${label}: HTTP ${res?.status()}, ${cards.length} results, ${kept} kept${skips.length ? `; skipped ${skips.join(", ")}` : ""}`);
}
await browser.close();

// Photos first, so the list never names a photo that is not there.
const stored: Article[] = [];
for (let i = 0; i < articles.length; i += 4) {
  const batch = articles.slice(i, i + 4);
  const done = await Promise.all(batch.map((a) => storePhoto(a).then(() => a, (e) => console.log(`${a.asin}: ${e.message}`))));
  stored.push(...done.filter((a): a is Article => !!a));
}
if (stored.length < MIN_ARTICLES) {
  console.log(`Only ${stored.length} articles with a photo (at least ${MIN_ARTICLES}): the list is left as it was.`);
  process.exit(robotCheck ? 2 : 1);
}
const list = { version: 1, fetched: new Date().toISOString(), articles: stored };
await store("articles.json", new TextEncoder().encode(JSON.stringify(list)), "application/json");
console.log(`${stored.length} articles stored${dryRun ? ` in ${dryRun}` : ` on ${cache!.server}`}.`);
process.exit(robotCheck ? 2 : 0);

async function storePhoto(article: Article): Promise<void> {
  const res = await fetch(article.image, { headers: { "user-agent": USER_AGENT } });
  if (!res.ok || !res.headers.get("content-type")?.startsWith("image/jpeg")) {
    throw new Error(`photo: HTTP ${res.status} ${res.headers.get("content-type")}`);
  }
  await store(`photos-${article.asin}.jpg`, new Uint8Array(await res.arrayBuffer()), "image/jpeg");
}
