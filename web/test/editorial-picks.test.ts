import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import {
  COVER_COUNT, DAY_MS, arrivedWithin, dayOf, homeEditorial, seededRandom,
} from "../public/lib/catalog/editorial-picks.js";
import { catalogSet } from "./support/catalog-set";

const NOW = 20_000 * DAY_MS + 3_600_000;

/** A film with artwork, a score and a popularity, unless told otherwise. */
function film(setId: string, over: Record<string, unknown> = {}) {
  return catalogSet({
    setId, title: setId, poster: `p-${setId}`, backdrop: `b-${setId}`,
    rating: 6, popularity: 10, addedAt: 1, ...over,
  });
}

function picks(movies: ReturnType<typeof film>[], over: Partial<Parameters<typeof homeEditorial>[0]> = {}) {
  return homeEditorial({
    movies,
    byId: new Map(movies.map((set) => [set.setId, set])),
    isWatched: () => false,
    editorsChoice: null,
    now: NOW,
    ...over,
  });
}

const ids = (sets: { setId: string }[]) => sets.map((set) => set.setId);

describe("the features", () => {
  test("lead with the pinned editor's choice, then trending by popularity and a staff pick by score", () => {
    const movies = [
      film("pinned", { rating: 5 }),
      film("popular", { popularity: 90, rating: 5 }),
      film("acclaimed", { rating: 9 }),
      film("plain", { rating: 4 }),
    ];
    const { features } = picks(movies, { editorsChoice: "pinned" });
    expect(features.map((f) => [f.kind, f.set.setId])).toEqual([
      ["editor", "pinned"], ["trending", "popular"], ["staff", "acclaimed"],
    ]);
  });

  test("honour a pin even on a watched title without a backdrop", () => {
    const movies = [film("seen", { backdrop: null }), film("a"), film("b")];
    const { features } = picks(movies, { editorsChoice: "seen", isWatched: (id) => id === "seen" });
    expect(features[0]).toMatchObject({ kind: "editor", set: { setId: "seen" } });
  });

  test("without a pin, the lead follows the staff rule and says so", () => {
    const { features } = picks([film("a", { rating: 9 }), film("b", { rating: 8 }), film("c", { rating: 7 })]);
    expect(features[0]!.kind).toBe("staff");
    expect(new Set(ids(features.map((f) => f.set))).size).toBe(features.length);
  });

  test("with no popularity recorded, trending becomes the newest arrival, labelled as that", () => {
    const movies = [film("old", { popularity: null, addedAt: 1 }), film("fresh", { popularity: null, addedAt: 9 })];
    const trending = picks(movies).features.find((f) => f.kind === "new");
    expect(trending?.set.setId).toBe("fresh");
    expect(picks(movies).features.some((f) => f.kind === "trending")).toBe(false);
  });

  test("never feature a watched film or one with no artwork, except by pin", () => {
    const movies = [film("seen", { popularity: 99 }), film("bare", { backdrop: null, poster: null, popularity: 98 }), film("ok")];
    const { features, cover } = picks(movies, { isWatched: (id) => id === "seen" });
    const shown = ids([...features.map((f) => f.set), ...cover]);
    expect(shown).not.toContain("seen");
    expect(shown).not.toContain("bare");
  });

  test("take a poster when there is no backdrop, but the cover never does", () => {
    const movies = [film("poster-only", { backdrop: null, popularity: 99 })];
    const { features, cover } = picks(movies);
    expect(features.map((f) => f.set.setId)).toContain("poster-only");
    expect(cover).toEqual([]);
  });
});

describe("the cover", () => {
  test("is drawn from what the features left, and holds at most five films", () => {
    const movies = Array.from({ length: 12 }, (_, i) => film(`f${i}`, { rating: i }));
    const { cover, features } = picks(movies);
    expect(cover.length).toBe(COVER_COUNT);
    const featured = new Set(ids(features.map((f) => f.set)));
    expect(cover.some((set) => featured.has(set.setId))).toBe(false);
  });

  test("stays the same all day, so a redraw does not reshuffle it", () => {
    const movies = Array.from({ length: 12 }, (_, i) => film(`f${i}`));
    expect(ids(picks(movies).cover)).toEqual(ids(picks(movies, { now: NOW + 3_600_000 }).cover));
  });
});

describe("the typographic break", () => {
  test("quotes a real tagline from a film not already featured", () => {
    const movies = [film("a"), film("b"), film("quoted", { tagline: "Ein Satz.", backdrop: null, poster: null })];
    expect(picks(movies).quote?.setId).toBe("quoted");
  });

  test("has no quote when nothing carries a tagline", () => {
    expect(picks([film("a")]).quote).toBeNull();
  });

  test("lists this month's arrivals, newest first, and nothing older", () => {
    const movies = [
      film("older", { addedAt: NOW - 40 * DAY_MS }),
      film("recent", { addedAt: NOW - 2 * DAY_MS }),
      film("newest", { addedAt: NOW - DAY_MS }),
    ];
    expect(ids(arrivedWithin(movies, NOW))).toEqual(["newest", "recent"]);
  });
});

test("this month lists the arrivals after the ones the Recently added row shows", () => {
  const movies = [
    film("newest", { addedAt: NOW - DAY_MS }),
    film("recent", { addedAt: NOW - 2 * DAY_MS }),
    film("earlier", { addedAt: NOW - 3 * DAY_MS }),
  ];
  expect(ids(picks(movies, { onRow: new Set(["newest", "recent"]) }).thisMonth)).toEqual(["earlier"]);
});

test("the seeded source repeats for a seed and differs across days", () => {
  const first = seededRandom(dayOf(NOW));
  const again = seededRandom(dayOf(NOW));
  expect([first(), first()]).toEqual([again(), again()]);
  expect(seededRandom(1)()).not.toBe(seededRandom(2)());
});

/**
 * `web/test/fixtures/editorial-picks/home-editorial.json` pins the exact
 * cover order, feature picks and quote `seededRandom`'s mulberry32
 * produces for a handful of seeded scenarios — the Android port
 * (`EditorialPicks.kt`) is pinned against the same file. This is what
 * proves that file is not drifting from what `homeEditorial` actually
 * does; a Kotlin-only fixture would prove nothing about the web.
 */
describe("the shared editorial-picks fixture", () => {
  interface FixtureFilm {
    setId: string;
    title: string;
    poster: string | null;
    backdrop: string | null;
    rating: number | null;
    popularity: number | null;
    addedAt: number;
    tagline: string | null;
  }
  interface Case {
    name: string;
    movies: FixtureFilm[];
    editorsChoice: string | null;
    now: number;
    onRow: string[];
    watchedIds?: string[];
    expect: {
      cover: string[];
      features: { kind: string; setId: string }[];
      quote: string | null;
      thisMonth: string[];
    };
  }

  const path = join(import.meta.dir, "fixtures", "editorial-picks", "home-editorial.json");
  const cases = JSON.parse(readFileSync(path, "utf8")) as Case[];

  test("the fixture file holds cases", () => {
    expect(cases.length).toBeGreaterThan(0);
  });

  for (const one of cases) {
    test(one.name, () => {
      const movies = one.movies.map((film) => catalogSet({
        setId: film.setId, title: film.title, poster: film.poster, backdrop: film.backdrop,
        rating: film.rating, popularity: film.popularity, addedAt: film.addedAt, tagline: film.tagline,
      }));
      const byId = new Map(movies.map((set) => [set.setId, set]));
      const watched = new Set(one.watchedIds ?? []);
      const picks = homeEditorial({
        movies, byId, isWatched: (id) => watched.has(id),
        editorsChoice: one.editorsChoice, now: one.now, onRow: new Set(one.onRow),
      });

      expect({
        cover: picks.cover.map((set) => set.setId),
        features: picks.features.map((f) => ({ kind: f.kind, setId: f.set.setId })),
        quote: picks.quote?.setId ?? null,
        thisMonth: picks.thisMonth.map((set) => set.setId),
      }).toEqual(one.expect);
    });
  }
});
