import { expect, test } from "bun:test";
import { similarTo } from "../public/lib/catalog/similar.js";

const t = (setId: string, genres: string[], extra: Record<string, unknown> = {}) => ({ setId, genres, ...extra });

test("franchise first, then unwatched, then genres shared, then popularity", () => {
  const dune = t("dune2", ["Sci-Fi", "Adventure"], { collectionId: 7 });
  const picks = similarTo(dune, [
    dune,
    t("dune1", ["Drama"], { collectionId: 7 }),
    t("arrival", ["Sci-Fi"], { popularity: 50 }),
    t("interstellar", ["Sci-Fi", "Adventure"], { popularity: 10 }),
    t("seen", ["Sci-Fi", "Adventure"], { popularity: 99 }),
    t("romcom", ["Romance"]),
  ], (item) => item.setId === "seen");
  expect(picks.map((item) => item.setId)).toEqual(["dune1", "interstellar", "arrival", "seen"]);
});

test("a title with nothing in common with anything has no similar titles", () => {
  expect(similarTo(t("a", []), [t("b", ["Drama"])])).toEqual([]);
});
