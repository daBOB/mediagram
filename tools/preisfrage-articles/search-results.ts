// Reading a search results page in the browser: the result cards, or that Amazon wants a robot check.
import { existsSync, readdirSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { type Browser, type Page, chromium } from "playwright-core";
import type { RawResult } from "./articles";

const RESULT_CARD = 'div[data-component-type="s-search-result"][data-asin]';

/**
 * Chromium from `PREISFRAGE_CHROME`, else the newest one Playwright has installed: its own pick is a
 * headless shell of exactly its version, which a PC that ran another Playwright often lacks.
 */
export async function launchBrowser(): Promise<Browser> {
  return chromium.launch({ headless: true, executablePath: process.env.PREISFRAGE_CHROME ?? newestChromium() });
}

// shortcut: Linux layout of ~/.cache/ms-playwright only; add the macOS path if it is ever run there.
function newestChromium(): string | undefined {
  const root = join(homedir(), ".cache/ms-playwright");
  const builds = existsSync(root) ? readdirSync(root).filter((d) => /^chromium-\d+$/.test(d)) : [];
  return builds
    .sort((a, b) => Number(b.slice(9)) - Number(a.slice(9)))
    .map((d) => join(root, d, "chrome-linux64/chrome"))
    .find(existsSync);
}

export async function readSearchPage(page: Page): Promise<RawResult[] | "robot check"> {
  const captcha = await page.locator('form[action*="validateCaptcha"]').count();
  if (captcha > 0 || /robot check|captcha/i.test(await page.title())) return "robot check";
  return page.$$eval(RESULT_CARD, readCards);
}

// Runs inside the page, so it uses nothing from this module. `textContent`, not `innerText`:
// the same answer with or without the page's styles, so saved pages test what a live one gives.
const readCards = (cards: Element[]) =>
  cards.map((card) => {
    const el = card as HTMLElement;
    const text = (n: Element | null) => (n?.textContent ?? "").replace(/\s+/g, " ").trim();
    const all = text(el);
    return {
      asin: el.dataset.asin ?? "",
      title: text(el.querySelector("h2")) || (el.querySelector("img.s-image")?.getAttribute("alt") ?? ""),
      image: el.querySelector("img.s-image")?.getAttribute("src") ?? "",
      price: text(el.querySelector(".a-price:not([data-a-strike]) .a-offscreen")),
      reviews: text(el.querySelector('a[href*="customerReviews"]')),
      sponsored: !!el.querySelector(".puis-sponsored-label-text") || /Gesponsert|Sponsored/.test(all),
      text: all,
    };
  });
