// PROTOTYPE, throwaway. The question: can we read amazon.de bestseller pages ourselves with
// Playwright, slowly, from home, and get enough articles (title, price, photo) for Preisfrage?
// Polite on purpose: one page every few seconds, cookies declined, and the run stops at the first
// robot check instead of trying to get past it.
// Run: bun fetch-bestsellers-spike.ts [category ...]   → items.json, items.html, run.log beside it
import { chromium } from "playwright-core";
import { writeFileSync, appendFileSync } from "node:fs";
import { homedir } from "node:os";

const CHROME = `${homedir()}/.cache/ms-playwright/chromium-1228/chrome-linux64/chrome`;
const CATEGORIES = process.argv.slice(2).length
  ? process.argv.slice(2)
  : ["kitchen", "toys", "diy", "drugstore", "sports", "pet-supplies"];
const PAUSE_MS = [4000, 8000];
const here = import.meta.dir;
const log = (line: string) => { console.log(line); appendFileSync(`${here}/run.log`, line + "\n"); };

type Item = { asin: string; title: string; cents: number; image: string; category: string; rank: number; checked: string };

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const context = await browser.newContext({
  locale: "de-DE",
  timezoneId: "Europe/Berlin",
  viewport: { width: 1366, height: 900 },
  // Headless Chromium names itself "HeadlessChrome"; this is the same browser's ordinary name.
  userAgent: "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36",
});
const page = await context.newPage();
const items: Item[] = [];
const stats: { category: string; status: number | null; found: number; priced: number; note: string }[] = [];

writeFileSync(`${here}/run.log`, `run ${new Date().toISOString()}\n`);
for (const [i, category] of CATEGORIES.entries()) {
  if (i > 0) await page.waitForTimeout(PAUSE_MS[0] + Math.random() * (PAUSE_MS[1] - PAUSE_MS[0]));
  const url = `https://www.amazon.de/gp/bestsellers/${category}`;
  const response = await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 }).catch(e => { log(`${category}: ${e.message}`); return null; });
  const status = response?.status() ?? null;

  const robotCheck = await page.locator('form[action*="validateCaptcha"]').count() > 0 || /robot check|captcha/i.test(await page.title());
  if (robotCheck) {
    stats.push({ category, status, found: 0, priced: 0, note: "robot check, run stopped" });
    log(`${category}: HTTP ${status}, robot check. Stopping here.`);
    await page.screenshot({ path: `${here}/robot-check.png` });
    break;
  }
  const reject = page.locator("#sp-cc-rejectall-link");
  if (await reject.count()) await reject.click().catch(() => {});

  // The list loads its second half on scroll.
  for (let y = 0; y < 6; y++) { await page.mouse.wheel(0, 1600); await page.waitForTimeout(400); }

  const found = await page.$$eval("[data-asin]", (nodes) => nodes.map((n) => {
    const el = n as HTMLElement;
    const img = el.querySelector("img") as HTMLImageElement | null;
    const text = el.innerText || "";
    const price = text.match(/(\d{1,3}(?:\.\d{3})*,\d{2})\s?€/);
    const rank = text.match(/#(\d+)/);
    return { asin: el.dataset.asin || "", title: img?.alt || "", image: img?.src || "", price: price ? price[1] : "", rank: rank ? Number(rank[1]) : 0 };
  }));
  const fresh = found.filter(f => f.asin && f.title && !items.some(x => x.asin === f.asin));
  const priced = fresh.filter(f => f.price);
  for (const f of priced) {
    items.push({ asin: f.asin, title: f.title, cents: Math.round(Number(f.price.replace(/\./g, "").replace(",", ".")) * 100),
      image: f.image, category, rank: f.rank, checked: new Date().toISOString() });
  }
  stats.push({ category, status, found: fresh.length, priced: priced.length, note: (await page.title()).slice(0, 60) });
  log(`${category}: HTTP ${status}, ${fresh.length} articles, ${priced.length} with a price ("${(await page.title()).slice(0, 60)}")`);
}
await browser.close();

writeFileSync(`${here}/items.json`, JSON.stringify({ fetched: new Date().toISOString(), stats, items }, null, 2));
const euro = (c: number) => (c / 100).toLocaleString("de-DE", { minimumFractionDigits: 2 }) + " €";
writeFileSync(`${here}/items.html`, `<!doctype html><meta charset="utf-8"><title>Preisfrage spike: amazon.de</title>
<style>body{font:14px system-ui;margin:20px;background:#f6f4ef}table{border-collapse:collapse;margin-bottom:16px}td,th{padding:4px 8px;border-bottom:1px solid #ddd;text-align:left}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:10px}.c{background:#fff;border-radius:8px;padding:8px}
.c img{width:100%;height:140px;object-fit:contain}.p{font-weight:700;color:#c2410c}.t{font-size:12px;height:3.6em;overflow:hidden}</style>
<h1>Spike: amazon.de bestseller pages, ${items.length} articles with a price</h1>
<table><tr><th>Category</th><th>HTTP</th><th>Articles</th><th>With price</th><th>Note</th></tr>
${stats.map(s => `<tr><td>${s.category}</td><td>${s.status}</td><td>${s.found}</td><td>${s.priced}</td><td>${s.note}</td></tr>`).join("")}</table>
<div class="grid">${items.map(x => `<div class="c"><img src="${x.image}" alt=""><div class="p">${euro(x.cents)}</div><div class="t">${x.title}</div><small>${x.category} #${x.rank}</small></div>`).join("")}</div>`);
log(`done: ${items.length} articles with a price → items.json, items.html`);
