import { afterAll, beforeAll, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import type { Browser, Page } from "playwright-core";
import { launchBrowser, readSearchPage } from "./search-results";

let browser: Browser;
let page: Page;

beforeAll(async () => {
  browser = await launchBrowser();
  const context = await browser.newContext({ javaScriptEnabled: false });
  await context.route("**/*", (r) => r.abort());
  page = await context.newPage();
});
afterAll(() => browser?.close());

const load = async (name: string) => {
  await page.setContent(readFileSync(`${import.meta.dir}/fixtures/${name}`, "utf8"), { waitUntil: "domcontentloaded" });
  return readSearchPage(page);
};

test("a plain page gives every result with title, price, reviews and photo", async () => {
  const read = await load("search-plain.html");
  if (read === "robot check") throw new Error("not a robot check");
  expect(read.map((r) => r.asin)).toEqual(["B0HCPRLZ6D", "B0GF25D3LF", "B07884JP1K", "B01HTU9TLY", "B07H7ZDBV4"]);
  expect(read[1]).toMatchObject({
    title: expect.stringMatching(/^2 Stück Phone Holder, Daumen Hoch Handyständer/),
    price: "8,99 €",
    reviews: "(21)",
    sponsored: false,
    image: "https://m.media-amazon.com/images/I/51zMYoZPA7L._AC_UY218_.jpg",
  });
  expect(read[0].reviews).toBe("");
});

test("sponsored results are marked", async () => {
  const read = await load("search-newest-band.html");
  if (read === "robot check") throw new Error("not a robot check");
  expect(read.filter((r) => r.sponsored).map((r) => r.asin)).toEqual(["B0HBPJD1ZB", "B0FT7W8WSS"]);
  expect(read.filter((r) => !r.sponsored)).toHaveLength(4);
});

test("books and priceless results come through for the filters to judge", async () => {
  const read = await load("search-with-books.html");
  if (read === "robot check") throw new Error("not a robot check");
  const byAsin = Object.fromEntries(read.map((r) => [r.asin, r]));
  expect(byAsin["B0H67X1GRR"].text).toMatch(/Taschenbuch|Kindle/);
  expect(byAsin["3831038147"]).toBeDefined();
  expect(byAsin["3958541127"].price).toBe("");
});

test("the robot check is recognised", async () => {
  expect(await load("search-robot-check.html")).toBe("robot check");
});
