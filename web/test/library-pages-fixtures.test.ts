/**
 * Runs `fixtures/library-pages/cases.json` against the web's own pure rules —
 * `groupDepartments`, `homeShelves`, `homeEditorial` and `departmentUnderway` —
 * wired the way `app.js` and the department pages wire them. Android's page
 * projection (`LibraryPages.kt`) is held to the same file, so a case here is
 * what both surfaces must draw from one library. The web is authoritative: a
 * case that only passes after changing those modules does not belong here.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { catalogSet } from "./support/catalog-set";
import type { CatalogSet, Collection } from "../public/lib/library.js";
import { groupDepartments } from "../public/lib/departments.js";
import { departmentUnderway, homeShelves, type NextUpEntry } from "../public/lib/catalog/home-shelves.js";
import { homeEditorial } from "../public/lib/catalog/editorial-picks.js";

interface FixtureSet {
  setId: string;
  kind: string;
  title: string;
  show?: string;
  season?: number;
  episode?: number;
  addedAt: number;
  anime?: boolean;
  poster?: string;
  backdrop?: string;
  rating?: number;
  popularity?: number;
  tagline?: string;
}

interface Underway {
  continues: string[];
  nextUp: { setId: string; resume: boolean }[];
}

interface Case {
  name: string;
  now: number;
  editorsChoice: string | null;
  sets: FixtureSet[];
  progress: { setId: string; at: number; duration: number; updatedAt: number }[];
  watched: Record<string, number>;
  expect: {
    departments: {
      movies: string[];
      series: string[];
      anime: { shows: string[]; films: string[] };
      documentaries: { collections: string[]; singles: string[] };
      tutorials: string[];
      audiobooks: string[];
    };
    home: Underway & {
      latestMovies: string[];
      latestSeries: string[];
      latestCourses: string[];
      latestDocumentaries: string[];
      totals: { continues: number; nextUp: number; latestMovies: number; latestSeries: number; latestCourses: number; latestDocumentaries: number };
    };
    editorial: {
      cover: string[];
      features: { kind: string; setId: string }[];
      quote: string | null;
      thisMonth: string[];
    };
    underway: Record<"series" | "anime" | "documentaries" | "tutorials" | "audiobooks", Underway>;
  };
}

const cases = JSON.parse(readFileSync(join(import.meta.dir, "fixtures", "library-pages", "cases.json"), "utf8")) as Case[];

const ids = (sets: CatalogSet[]) => sets.map((set) => set.setId);
const names = (collections: Collection[]) => collections.map((collection) => collection.name);
const underwayOf = (rows: { continues: CatalogSet[]; nextUp: NextUpEntry[] }): Underway => ({
  continues: ids(rows.continues),
  nextUp: rows.nextUp.map((entry) => ({ setId: entry.set.setId, resume: entry.resume })),
});

describe("the shared library-pages fixture", () => {
  test("the fixture file holds cases", () => {
    expect(cases.length).toBeGreaterThan(0);
  });

  for (const one of cases) {
    test(one.name, () => {
      const sets = one.sets.map((set) => catalogSet({
        setId: set.setId, kind: set.kind, title: set.title, show: set.show ?? null,
        season: set.season ?? null, episode: set.episode == null ? null : String(set.episode),
        addedAt: set.addedAt, anime: set.anime ?? false, poster: set.poster ?? null, backdrop: set.backdrop ?? null,
        rating: set.rating ?? null, popularity: set.popularity ?? null, tagline: set.tagline ?? null,
      }));
      const library = groupDepartments(sets);
      const byId = new Map(sets.map((set) => [set.setId, set]));
      // The store hands these over newest first; so does this.
      const progress = [...one.progress].sort((a, b) => b.updatedAt - a.updatedAt);
      const watchedAt = (setId: string) => one.watched[setId] ?? null;
      const watch = { byId, progress, watchedAt };

      expect({
        movies: ids(library.movies),
        series: names(library.series),
        anime: { shows: names(library.anime.collections), films: ids(library.anime.singles) },
        documentaries: { collections: names(library.documentaries.collections), singles: ids(library.documentaries.singles) },
        tutorials: names(library.tutorials),
        audiobooks: ids(library.audiobooks),
      }).toEqual(one.expect.departments);

      const shelves = homeShelves({ library, ...watch });
      expect({
        ...underwayOf(shelves),
        latestMovies: ids(shelves.latestMovies),
        latestSeries: names(shelves.latestSeries),
        latestCourses: names(shelves.latestCourses),
        latestDocumentaries: ids(shelves.latestDocumentaries),
        totals: shelves.totals,
      }).toEqual(one.expect.home);

      const picks = homeEditorial({
        movies: library.movies,
        byId,
        isWatched: (setId: string) => watchedAt(setId) !== null,
        editorsChoice: one.editorsChoice,
        now: one.now,
        onRow: new Set(ids(shelves.latestMovies)),
      });
      expect({
        cover: ids(picks.cover),
        features: picks.features.map((feature: { kind: string; set: CatalogSet }) => ({ kind: feature.kind, setId: feature.set.setId })),
        quote: picks.quote?.setId ?? null,
        thisMonth: ids(picks.thisMonth),
      }).toEqual(one.expect.editorial);

      // Each department's own `keep`, as its page in `department-pages.js` and `anime-department.js` passes it.
      expect({
        series: underwayOf(departmentUnderway({ collections: library.series, keep: (set) => set.kind === "ep" && !set.anime, ...watch })),
        anime: underwayOf(departmentUnderway({ collections: library.anime.collections, keep: (set) => set.anime, ...watch })),
        documentaries: underwayOf(departmentUnderway({ collections: [], keep: (set) => set.kind === "docu", ...watch })),
        tutorials: underwayOf(departmentUnderway({ collections: library.tutorials, keep: (set) => set.kind === "tut" && !set.anime, ...watch })),
        audiobooks: underwayOf(departmentUnderway({ collections: [], keep: (set) => set.kind === "book", ...watch })),
      }).toEqual(one.expect.underway);
    });
  }
});
