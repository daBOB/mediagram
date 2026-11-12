// PROTOTYPE, throwaway. The question: do amazon.de *search* results give odd, unknown articles
// (not bestsellers) with a usable price spread? Random search terms aimed at curiosities, a random
// results page, sometimes sorted by newest; sponsored results skipped; the review count is kept so
// "few reviews" can stand for "nobody knows this". Same politeness as the bestseller spike: one page
// every few seconds, cookies declined, stop at the first robot check.
// Run: bun fetch-search-spike.ts [pages]   → search-items.json, search-items.html, search-run.log
import { chromium } from "playwright-core";
import { writeFileSync, appendFileSync } from "node:fs";
import { homedir } from "node:os";

const CHROME = `${homedir()}/.cache/ms-playwright/chromium-1228/chrome-linux64/chrome`;
const TERMS = [
  "kurioses gadget", "skurrile geschenke", "verrückte erfindungen", "scherzartikel", "küchenhelfer gadget",
  "usb gadget", "garten gadget", "büro gadget", "auto gadget", "haustier gadget", "wichtelgeschenk",
  "nützliche erfindungen", "lustige geschenke", "seltsame dinge", "camping gadget", "badezimmer gadget",
];
const PAGES = Number(process.argv[2] || 8);
// Amazon's price filter p_36 takes euro cents; a random band per search spreads the prices.
const BANDS = [[500, 2000], [2000, 5000], [5000, 15000], [15000, 50000], null];
// Books, e-books and films make dull guesses; their result text names the format.
const MEDIA = /Kindle|Taschenbuch|Gebundene Ausgabe|Broschiert|Hörbuch|Audible|DVD|Blu-ray|Prime Video|Audio-CD/;
// Family night: gag gifts bring adult novelties; a title blocklist catches the obvious ones.
const ADULT = /penis|brust|busen|nackt|naughty|erotik|\bsex|kondom|dildo|vibrator|stripper|wichs|ab 18|18\+|po[ -]?shorts|furz/i;
const PAUSE_MS = [4000, 8000];
const here = import.meta.dir;
const log = (line: string) => { console.log(line); appendFileSync(`${here}/search-run.log`, line + "\n"); };
const pick = <T,>(list: T[]) => list[Math.floor(Math.random() * list.length)];

type Item = { asin: string; title: string; cents: number; image: string; reviews: number | null; term: string; page: number; newest: boolean; checked: string };

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const context = await browser.newContext({
  locale: "de-DE", timezoneId: "Europe/Berlin", viewport: { width: 1366, height: 900 },
  userAgent: "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36",
});
const page = await context.newPage();
const items: Item[] = [];
const stats: { term: string; page: number; newest: boolean; status: number | null; results: number; sponsored: number; kept: number }[] = [];
const terms = [...TERMS].sort(() => Math.random() - 0.5).slice(0, PAGES);

writeFileSync(`${here}/search-run.log`, `run ${new Date().toISOString()}\n`);
for (const [i, term] of terms.entries()) {
  if (i > 0) await page.waitForTimeout(PAUSE_MS[0] + Math.random() * (PAUSE_MS[1] - PAUSE_MS[0]));
  const pageNo = pick([1, 2, 3, 4]);
  const newest = Math.random() < 0.4;
  const band = pick(BANDS);
  const url = `https://www.amazon.de/s?k=${encodeURIComponent(term)}&page=${pageNo}${newest ? "&s=date-desc-rank" : ""}${band ? `&rh=p_36%3A${band[0]}-${band[1]}` : ""}`;
  const response = await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 }).catch(e => { log(`${term}: ${e.message}`); return null; });
  const status = response?.status() ?? null;

  if (await page.locator('form[action*="validateCaptcha"]').count() > 0 || /robot check|captcha/i.test(await page.title())) {
    log(`${term}: HTTP ${status}, robot check. Stopping here.`);
    await page.screenshot({ path: `${here}/robot-check.png` });
    break;
  }
  const reject = page.locator("#sp-cc-rejectall-link");
  if (await reject.count()) await reject.click().catch(() => {});

  const results = await page.$$eval('div[data-component-type="s-search-result"][data-asin]', (nodes) => nodes.map((n) => {
    const el = n as HTMLElement;
    const img = el.querySelector("img.s-image") as HTMLImageElement | null;
    const title = (el.querySelector("h2") as HTMLElement | null)?.innerText.trim() || img?.alt || "";
    const price = (el.querySelector(".a-price:not([data-a-strike]) .a-offscreen") as HTMLElement | null)?.textContent || "";
    const reviewsLabel = (el.querySelector('a[href*="customerReviews"]') as HTMLElement | null)?.innerText || "";
    const reviews = reviewsLabel.replace(/[^\d]/g, "");
    return { asin: el.dataset.asin || "", title, image: img?.src || "", price, reviews: reviews ? Number(reviews) : null,
      sponsored: /Gesponsert|Sponsored/.test(el.innerText), text: el.innerText };
  }));
  const organic = results.filter(r => !r.sponsored);
  let kept = 0, media = 0, adult = 0;
  for (const r of organic) {
    const m = r.price.match(/(\d{1,3}(?:\.\d{3})*,\d{2})/);
    if (!r.asin || !r.title || !m || items.some(x => x.asin === r.asin)) continue;
    const cents = Math.round(Number(m[1].replace(/\./g, "").replace(",", ".")) * 100);
    if (cents === 0 || MEDIA.test(r.text)) { media++; continue; }
    if (ADULT.test(r.title)) { adult++; continue; }
    items.push({ asin: r.asin, title: r.title, cents, image: r.image, reviews: r.reviews, term, page: pageNo, newest, checked: new Date().toISOString() });
    kept++;
  }
  stats.push({ term, page: pageNo, newest, status, results: results.length, sponsored: results.length - organic.length, kept });
  log(`"${term}" page ${pageNo}${newest ? " newest" : ""}${band ? ` ${band[0] / 100}-${band[1] / 100} €` : ""}: HTTP ${status}, ${results.length} results, ${results.length - organic.length} sponsored, ${media} media, ${adult} adult, ${kept} kept`);
}
await browser.close();

writeFileSync(`${here}/search-items.json`, JSON.stringify({ fetched: new Date().toISOString(), stats, items }, null, 2));
const euro = (c: number) => (c / 100).toLocaleString("de-DE", { minimumFractionDigits: 2 }) + " €";
writeFileSync(`${here}/search-items.html`, `<!doctype html><meta charset="utf-8"><title>Preisfrage spike: amazon.de search</title>
<style>body{font:14px system-ui;margin:20px;background:#f6f4ef}table{border-collapse:collapse;margin-bottom:16px}td,th{padding:4px 8px;border-bottom:1px solid #ddd;text-align:left}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:10px}.c{background:#fff;border-radius:8px;padding:8px}
.c img{width:100%;height:140px;object-fit:contain}.p{font-weight:700;color:#c2410c}.t{font-size:12px;height:3.6em;overflow:hidden}small{color:#5d6772}</style>
<h1>Spike: amazon.de search results, ${items.length} articles with a price</h1>
<table><tr><th>Search</th><th>Page</th><th>Newest</th><th>HTTP</th><th>Results</th><th>Sponsored</th><th>Kept</th></tr>
${stats.map(s => `<tr><td>${s.term}</td><td>${s.page}</td><td>${s.newest ? "yes" : ""}</td><td>${s.status}</td><td>${s.results}</td><td>${s.sponsored}</td><td>${s.kept}</td></tr>`).join("")}</table>
<div class="grid">${items.map(x => `<div class="c"><img src="${x.image}" alt=""><div class="p">${euro(x.cents)}</div><div class="t">${x.title}</div><small>${x.term} · ${x.reviews === null ? "no reviews" : x.reviews + " reviews"}</small></div>`).join("")}</div>`);
log(`done: ${items.length} articles with a price → search-items.json, search-items.html`);
