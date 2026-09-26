import { expect, test } from "bun:test";
import { genreIndex } from "../public/lib/catalog/utility-pages.js";

const t = (setId: string, genres: string[], popularity: number, backdrop: string | null = `${setId}-bg`) =>
  ({ setId, genres, popularity, backdrop });

test("genres by size, each pictured by its most popular title not already used", () => {
  const index = genreIndex([
    t("zoo", ["Animation", "Family"], 99),
    t("up", ["Animation", "Family"], 50),
    t("coco", ["Animation"], 10),
    t("bare", ["Family"], 80, null),
  ]);
  expect(index).toEqual([
    { name: "Animation", count: 3, art: "zoo-bg" },
    { name: "Family", count: 3, art: "up-bg" },
  ]);
});

test("a genre whose only picture is taken still shows it rather than nothing", () => {
  expect(genreIndex([t("only", ["A", "B"], 1)]).map((g) => g.art)).toEqual(["only-bg", "only-bg"]);
});
