import { expect, test } from "bun:test";
import { franchisesIn } from "../public/lib/catalog/collections-page.js";
import { MCU_ID, mcuIn } from "../public/lib/catalog/mcu.js";

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

const mcuFilm = (setId: string, tmdb: number, extra: Record<string, unknown> = {}) =>
  ({ setId, kind: "movie", year: 2000, collectionId: null, showKey: `tmdb-movie-${tmdb}`, ...extra });

test("the MCU is one more franchise, its films in story order, not release order", () => {
  const found = franchisesIn([
    mcuFilm("iron-man", 1726, { year: 2008 }),
    mcuFilm("first-avenger", 1771, { year: 2011 }),
    mcuFilm("captain-marvel", 299537, { year: 2019, backdrop: "cm-bg", popularity: 9 }),
    mcuFilm("not-mcu", 557),
  ]);
  const mcu = found.find((f) => f.id === MCU_ID);
  expect(mcu?.films.map((f) => f.setId)).toEqual(["first-avenger", "captain-marvel", "iron-man"]);
  expect(mcu?.order).toBe("In story order");
  expect(mcu?.art).toBe("cm-bg");
});

test("a film held twice is in the MCU once, and Endgame is its Encore cut", () => {
  const standard = mcuFilm("endgame", 299534, { quality: "1080p" });
  const encore = mcuFilm("endgame-encore", 299534, { quality: "2160p", variant: "Encore" });
  const mcu = mcuIn([standard, encore, mcuFilm("infinity-war", 299536)]);
  expect(mcu?.films.map((f) => f.setId)).toEqual(["infinity-war", "endgame-encore"]);
  expect(mcu?.films[1].versions.map((v: { setId: string }) => v.setId)).toEqual(["endgame", "endgame-encore"]);
});

test("one MCU film is a title, not a collection", () => {
  expect(mcuIn([mcuFilm("iron-man", 1726)])).toBeNull();
  expect(franchisesIn([mcuFilm("iron-man", 1726)])).toEqual([]);
});
