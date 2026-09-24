/**
 * What the start page shows.
 *
 * Two facts make the page: what arrived recently, and what was underway.
 * The second is the one with rules — which episode of a show to offer when
 * the viewer has a position in one and has finished another — and every one
 * of those rules is asserted here, because none of them is visible from a
 * screenshot until it is wrong.
 */

import { describe, expect, test } from "bun:test";
import { groupLibrary, type CatalogSet } from "../public/lib/library.js";
import { homeShelves } from "../public/lib/catalog/home-shelves.js";

const set = (over: Record<string, unknown> = {}): CatalogSet => ({
  setId: `01SET${Math.random().toString(36).slice(2, 10).toUpperCase()}`,
  kind: "movie",
  title: "A Title",
  show: null,
  path: null,
  chap: null,
  season: null,
  episode: null,
  year: null,
  container: "mp4",
  vcodec: "h264",
  acodec: "aac",
  duration: 3600,
  total: 1000,
  partCount: 1,
  addedAt: 1_000,
  ...over,
});

/** An episode of `show`, numbered, with a stable id to record state against. */
const episode = (show: string, season: number, number: number, over: Record<string, unknown> = {}) =>
  set({
    setId: `${show}-s${season}e${number}`.toUpperCase().replace(/[^A-Z0-9]/g, ""),
    kind: "ep",
    show,
    season,
    episode: String(number),
    title: `${show} ${season}x${number}`,
    ...over,
  });

/** A position half way through an hour, which `resumeAt` accepts. */
const halfway = (setId: string, updatedAt: number) => ({
  setId,
  at: 1800,
  duration: 3600,
  updatedAt,
});

/** The page's inputs, from a flat list of sets and some watch state. */
function shelvesOf(
  sets: CatalogSet[],
  state: { progress?: ReturnType<typeof halfway>[]; watched?: Record<string, number> } = {},
  limit?: number,
) {
  const watched = state.watched ?? {};
  return homeShelves({
    library: groupLibrary(sets),
    byId: new Map(sets.map((one) => [one.setId, one])),
    // The store hands these over newest first; so does this.
    progress: [...(state.progress ?? [])].sort((a, b) => b.updatedAt - a.updatedAt),
    watchedAt: (setId: string) => watched[setId] ?? null,
    limit,
  });
}

describe("what arrived recently", () => {
  test("films are newest first, whatever the shelf sorted them by", () => {
    const shelves = shelvesOf([
      set({ title: "Old", addedAt: 1_000 }),
      set({ title: "New", addedAt: 3_000 }),
      set({ title: "Middle", addedAt: 2_000 }),
    ]);

    expect(shelves.latestMovies.map((one) => one.title)).toEqual(["New", "Middle", "Old"]);
  });

  test("a show ranks by its newest episode, not by its first", () => {
    // The point of the rule: a series still being uploaded keeps its place,
    // and one whose first episode is ancient is not buried by that.
    const shelves = shelvesOf([
      episode("Long Running", 1, 1, { addedAt: 100 }),
      episode("Long Running", 9, 9, { addedAt: 9_000 }),
      episode("One Season", 1, 1, { addedAt: 5_000 }),
    ]);

    expect(shelves.latestSeries.map((one) => one.name)).toEqual(["Long Running", "One Season"]);
  });

  test("courses rank the same way, by their newest lesson", () => {
    const shelves = shelvesOf([
      set({ kind: "tut", show: "Java", title: "L1", addedAt: 10 }),
      set({ kind: "tut", show: "Rust", title: "L1", addedAt: 20 }),
      set({ kind: "tut", show: "Java", title: "L2", addedAt: 30 }),
    ]);

    expect(shelves.latestCourses.map((one) => one.name)).toEqual(["Java", "Rust"]);
  });

  test("a row holds no more than the limit", () => {
    const many = Array.from({ length: 12 }, (_, n) => set({ title: `Film ${n}`, addedAt: n }));

    expect(shelvesOf(many).latestMovies).toHaveLength(6);
    // The heading still counts the whole shelf, not the six on the row.
    expect(shelvesOf(many).totals.latestMovies).toBe(12);
    expect(shelvesOf(many, {}, 3).latestMovies).toHaveLength(3);
  });

  test("a library with nothing in it gives rows that are empty, not absent", () => {
    // The view decides what to draw; the model never answers `undefined`.
    const shelves = shelvesOf([]);

    expect(shelves.latestMovies).toEqual([]);
    expect(shelves.nextUp).toEqual([]);
    expect(shelves.continues).toEqual([]);
  });
});

describe("what is underway", () => {
  const show = [episode("Show", 1, 1), episode("Show", 1, 2), episode("Show", 1, 3)];

  test("a show nobody has touched is not on the row", () => {
    expect(shelvesOf(show).nextUp).toEqual([]);
  });

  test("a part-watched episode is offered, as itself", () => {
    const shelves = shelvesOf(show, { progress: [halfway(show[1]!.setId, 5_000)] });

    expect(shelves.nextUp).toHaveLength(1);
    expect(shelves.nextUp[0]!.set.setId).toBe(show[1]!.setId);
    expect(shelves.nextUp[0]!.resume).toBe(true);
  });

  test("a finished episode offers the one after it", () => {
    const shelves = shelvesOf(show, { watched: { [show[0]!.setId]: 5_000 } });

    expect(shelves.nextUp[0]!.set.setId).toBe(show[1]!.setId);
    expect(shelves.nextUp[0]!.resume).toBe(false);
  });

  test("being part-way through beats what follows a finished episode", () => {
    // Finished 1 and then started 2: the viewer is in the middle of 2, and
    // offering 3 would skip what they are actually watching.
    const shelves = shelvesOf(show, {
      watched: { [show[0]!.setId]: 5_000 },
      progress: [halfway(show[1]!.setId, 6_000)],
    });

    expect(shelves.nextUp[0]!.set.setId).toBe(show[1]!.setId);
    expect(shelves.nextUp[0]!.resume).toBe(true);
  });

  test("an episode already watched out of order is skipped, not offered again", () => {
    const shelves = shelvesOf(show, {
      watched: { [show[0]!.setId]: 5_000, [show[1]!.setId]: 1_000 },
    });

    expect(shelves.nextUp[0]!.set.setId).toBe(show[2]!.setId);
  });

  test("a show with nothing left leaves the row", () => {
    const shelves = shelvesOf(show, {
      watched: { [show[0]!.setId]: 1, [show[1]!.setId]: 2, [show[2]!.setId]: 3 },
    });

    expect(shelves.nextUp).toEqual([]);
  });

  test("a glance is not being underway", () => {
    // Twenty seconds into an hour is looking at it, which `resumeAt`
    // refuses — and a show with no other mark has nothing to caption.
    const shelves = shelvesOf(show, {
      progress: [{ setId: show[0]!.setId, at: 20, duration: 3600, updatedAt: 5_000 }],
    });

    expect(shelves.nextUp).toEqual([]);
  });

  test("shows come in the order they were last watched", () => {
    const other = [episode("Other", 1, 1), episode("Other", 1, 2)];
    const shelves = shelvesOf([...show, ...other], {
      progress: [halfway(show[0]!.setId, 1_000)],
      // Finished more recently than the other show was left part-way.
      watched: { [other[0]!.setId]: 9_000 },
    });

    expect(shelves.nextUp.map((entry) => entry.collection.name)).toEqual(["Other", "Show"]);
  });

  test("a completion ranks a show even though it cleared the position", () => {
    // The case the dated `watched` row exists for: finishing an episode
    // erases its progress, and without the date this show would rank last.
    const other = [episode("Other", 1, 1), episode("Other", 1, 2)];
    const shelves = shelvesOf([...show, ...other], {
      progress: [halfway(other[0]!.setId, 8_000)],
      watched: { [show[0]!.setId]: 9_000 },
    });

    expect(shelves.nextUp[0]!.collection.name).toBe("Show");
  });

  test("courses are underway the same way series are", () => {
    const lessons = [
      set({ kind: "tut", show: "Java", title: "L1", path: null }),
      set({ kind: "tut", show: "Java", title: "L2", path: null }),
    ];
    const shelves = shelvesOf(lessons, { watched: { [lessons[0]!.setId]: 5_000 } });

    expect(shelves.nextUp).toHaveLength(1);
    expect(shelves.nextUp[0]!.set.title).toBe("L2");
  });
});

describe("the same title never appears twice", () => {
  const show = [episode("Show", 1, 1), episode("Show", 1, 2)];

  test("an episode under Next up is not also under Continue", () => {
    const film = set({ title: "A Film" });
    const shelves = shelvesOf([...show, film], {
      progress: [halfway(show[0]!.setId, 9_000), halfway(film.setId, 8_000)],
    });

    expect(shelves.nextUp[0]!.set.setId).toBe(show[0]!.setId);
    expect(shelves.continues.map((one) => one.setId)).toEqual([film.setId]);
    // The count still agrees with the Continue shelf, which lists both.
    expect(shelves.totals.continues).toBe(2);
    expect(shelves.totals.nextUp).toBe(1);
  });

  test("an episode Next up had no room for still reaches Continue", () => {
    // Nothing underway silently disappears from the page: the subtraction
    // is against what is *shown*, not against everything that qualified.
    const other = [episode("Other", 1, 1), episode("Other", 1, 2)];
    const shelves = shelvesOf(
      [...show, ...other],
      {
        progress: [halfway(show[0]!.setId, 9_000), halfway(other[0]!.setId, 8_000)],
      },
      1,
    );

    expect(shelves.nextUp.map((entry) => entry.set.setId)).toEqual([show[0]!.setId]);
    expect(shelves.continues.map((one) => one.setId)).toEqual([other[0]!.setId]);
  });

  test("Continue is most recent first and holds only resumable titles", () => {
    const older = set({ title: "Older" });
    const newer = set({ title: "Newer" });
    const glanced = set({ title: "Glanced" });
    const shelves = shelvesOf([older, newer, glanced], {
      progress: [
        halfway(older.setId, 1_000),
        halfway(newer.setId, 2_000),
        { setId: glanced.setId, at: 5, duration: 3600, updatedAt: 3_000 },
      ],
    });

    expect(shelves.continues.map((one) => one.title)).toEqual(["Newer", "Older"]);
  });

  test("a position against a title the catalog no longer holds is dropped", () => {
    const film = set({ title: "A Film" });
    const shelves = shelvesOf([film], {
      progress: [halfway("01GONEFROMTHECATALOG", 9_000), halfway(film.setId, 1_000)],
    });

    expect(shelves.continues.map((one) => one.title)).toEqual(["A Film"]);
  });
});
