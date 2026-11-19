/**
 * Runs `fixtures/film-versions/` against `film-versions.js`: how copies of a
 * film group into one card, in what order, how each is labelled, and what is
 * watched and where each copy resumes.
 *
 * The Android catalog reads the same two files, so a case here is a rule both
 * surfaces must agree on.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { catalogSet } from "./support/catalog-set";
import {
  asFilms, continueRows, filmIndex, filmPlayed, groupFilms, versionLabel, versionPosition, versionsOf,
} from "../public/lib/film-versions.js";
import { groupDepartments } from "../public/lib/departments.js";
import { resumeAt } from "../public/lib/resume-point.js";

const FIXTURES = join(import.meta.dir, "fixtures", "film-versions");
const read = (name: string) => JSON.parse(readFileSync(join(FIXTURES, name), "utf8"));

type Row = { setId: string; at: number; duration: number; updatedAt: number };

describe("grouping.json", () => {
  for (const { name, sets, expect: wanted } of read("grouping.json")) {
    test(name, () => {
      const films = groupFilms(sets.map((over: object) => catalogSet(over)));
      expect(films.map((film) => ({
        setId: film.setId,
        versions: versionsOf(film).map((version) => version.setId),
        labels: versionsOf(film).map(versionLabel),
      }))).toEqual(wanted);
    });
  }
});

describe("watch-state.json", () => {
  const { film: copies, single, cases } = read("watch-state.json");
  const film = groupFilms(copies.map((over: object) => catalogSet({ ...over, showKey: "tmdb-movie-1" })))[0]!;
  const alone = catalogSet({ setId: single, showKey: "tmdb-movie-2" });
  const index = filmIndex([film, alone]);
  const filmOf = (setId: string) => index.get(setId) ?? null;

  for (const { name, progress, watched, expect: wanted } of cases) {
    test(name, () => {
      const rows = new Map<string, Row>(progress.map((row: Row) => [row.setId, row]));
      const progressOf = (setId: string) => rows.get(setId) ?? null;
      const watchedAt = (setId: string) => watched[setId] ?? null;
      const newestFirst = [...rows.values()].sort((a, b) => b.updatedAt - a.updatedAt);

      expect(filmPlayed(film, watchedAt)).toBe(wanted.played);
      expect(Object.fromEntries(versionsOf(film).map((version) =>
        [version.setId, resumeAt(versionPosition(version, film, progressOf, watchedAt))]))).toEqual(wanted.resume);
      expect(continueRows(newestFirst, filmOf, watchedAt)
        .filter((row) => resumeAt(row) !== null).map((row) => row.setId)).toEqual(wanted.continue);
    });
  }
});

describe("films on the shelves", () => {
  test("Movies and Anime films show one card per film", () => {
    const library = groupDepartments([
      catalogSet({ setId: "HD", title: "Endgame", showKey: "tmdb-movie-1", quality: "1080p" }),
      catalogSet({ setId: "UHD", title: "Endgame", showKey: "tmdb-movie-1", quality: "2160p" }),
      catalogSet({ setId: "A1", title: "Akira", showKey: "tmdb-movie-2", anime: true }),
      catalogSet({ setId: "A2", title: "Akira", showKey: "tmdb-movie-2", anime: true, variant: "Remastered" }),
    ]);
    expect(library.movies.map((film) => [film.setId, versionsOf(film).length])).toEqual([["UHD", 2]]);
    expect(library.anime.singles.map((film) => [film.setId, versionsOf(film).length])).toEqual([["A1", 2]]);
  });

  test("a film is offline when any copy is held in full", () => {
    const [film] = groupFilms([
      catalogSet({ setId: "HD", showKey: "tmdb-movie-1", quality: "1080p", offline: true }),
      catalogSet({ setId: "UHD", showKey: "tmdb-movie-1", quality: "2160p" }),
    ]);
    expect(film!.offline).toBe(true);
  });

  test("a stored copy shows as its whole film, once, and everything else as it came", () => {
    const hd = catalogSet({ setId: "HD", showKey: "tmdb-movie-1", quality: "1080p" });
    const uhd = catalogSet({ setId: "UHD", showKey: "tmdb-movie-1", quality: "2160p" });
    const episode = { ...catalogSet({ setId: "E1", kind: "ep" }), excerpt: "kept" };
    const index = filmIndex(groupFilms([hd, uhd]));
    const shown = asFilms([hd, episode, uhd], (setId: string) => index.get(setId) ?? null);
    expect(shown.map((item) => item.setId)).toEqual(["UHD", "E1"]);
    expect(shown[1]).toBe(episode);
  });
});
