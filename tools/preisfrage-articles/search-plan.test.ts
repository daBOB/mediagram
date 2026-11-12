import { expect, test } from "bun:test";
import { BANDS, planSearches, searchUrl } from "./search-plan";

const terms = Array.from({ length: 20 }, (_, i) => `term ${i}`);

test("sixteen searches cover each price band four times", () => {
  const plan = planSearches(terms, 16);
  expect(plan).toHaveLength(16);
  for (const band of BANDS) expect(plan.filter((s) => s.band === band)).toHaveLength(4);
});

test("terms are not repeated while there are unused ones, pages are 1-4", () => {
  const plan = planSearches(terms, 16);
  expect(new Set(plan.map((s) => s.term)).size).toBe(16);
  expect(plan.every((s) => s.page >= 1 && s.page <= 4)).toBe(true);
  expect(planSearches(["a", "b"], 4).map((s) => s.term).sort()).toEqual(["a", "a", "b", "b"]);
});

test("about 40 % of searches are sorted by newest", () => {
  let newest = 0;
  for (let i = 0; i < 50; i++) newest += planSearches(terms, 16).filter((s) => s.newest).length;
  expect(newest / 800).toBeGreaterThan(0.3);
  expect(newest / 800).toBeLessThan(0.5);
});

test("the url carries term, page, order and the band in cents", () => {
  const url = searchUrl({ term: "küchen gadget", page: 3, newest: true, band: BANDS[1] });
  expect(url).toBe(
    "https://www.amazon.de/s?k=k%C3%BCchen%20gadget&page=3&s=date-desc-rank&rh=p_36%3A2000-5000",
  );
  expect(searchUrl({ term: "x", page: 1, newest: false, band: BANDS[0] })).toBe(
    "https://www.amazon.de/s?k=x&page=1&rh=p_36%3A500-2000",
  );
});
