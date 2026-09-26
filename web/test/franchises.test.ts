import { expect, test } from "bun:test";
import { franchisesIn } from "../public/lib/catalog/collections-page.js";

const film = (setId: string, year: number, collectionId: number | null, extra: Record<string, unknown> = {}) =>
  ({ setId, year, collectionId, collectionName: collectionId ? `Franchise ${collectionId}` : null, ...extra });

test("franchises need two films, run largest first, in release order, pictured by their most popular", () => {
  const found = franchisesIn([
    film("nemesis", 2002, 1, { popularity: 5, backdrop: "n-bg" }),
    film("first-contact", 1996, 1, { popularity: 30, backdrop: "fc-bg" }),
    film("tmp", 1979, 1),
    film("dune2", 2024, 2, { backdrop: "d2-bg" }),
    film("dune", 2021, 2),
    film("alone", 2000, 3),
    film("none", 2000, null),
  ]);
  expect(found.map((f) => [f.id, f.films.map((x) => x.setId), f.art])).toEqual([
    [1, ["tmp", "first-contact", "nemesis"], "fc-bg"],
    [2, ["dune", "dune2"], "d2-bg"],
  ]);
});
