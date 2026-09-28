/**
 * What plays after a title, and what the server is asked to fetch ahead of
 * it. The preload rule is a decision about Telegram traffic from a
 * flood-limited account — the next two series episodes, and nothing for a
 * lesson, a documentary or a hand-built list — so it is pinned here, beside
 * the "next" it must always agree with.
 */

import { describe, expect, test } from "bun:test";
import { catalogSet as set } from "./support/catalog-set";
import { groupDepartments } from "../public/lib/departments.js";
import { playsNext } from "../public/lib/playback/plays-next.js";
import type { CatalogSet, Library } from "../public/lib/library.js";

/** A library the way the session builds one. */
function libraryOf(sets: CatalogSet[]): Library {
  return groupDepartments(sets);
}

const e1 = set({ kind: "ep", show: "Star City", season: 1, episode: "1", title: "S1E1" });
const e2 = set({ kind: "ep", show: "Star City", season: 1, episode: "2", title: "S1E2" });
const e3 = set({ kind: "ep", show: "Star City", season: 2, episode: "1", title: "S2E1" });
const e4 = set({ kind: "ep", show: "Star City", season: 2, episode: "2", title: "S2E2" });
const l1 = set({ kind: "tut", show: "Kurs", path: "A", episode: "1", title: "A1" });
const l2 = set({ kind: "tut", show: "Kurs", path: "A", episode: "2", title: "A2" });
const d1 = set({ kind: "docu", show: "Terra X", episode: "1", title: "Terra 1" });
const d2 = set({ kind: "docu", show: "Terra X", episode: "2", title: "Terra 2" });
const film = set({ kind: "movie", title: "Blade" });
const a1 = set({ kind: "ep", show: "Dragonball", episode: "1", title: "A1", anime: true });
const a2 = set({ kind: "ep", show: "Dragonball", episode: "2", title: "A2", anime: true });
const library = libraryOf([e1, e2, e3, e4, l1, l2, d1, d2, film, a1, a2]);

describe("playsNext", () => {
  test("an episode plays on through its show and preloads the next two, across seasons, and no more", () => {
    expect(playsNext(library, e1, null)).toEqual({ next: e2, preload: [e2.setId, e3.setId] });
  });

  test("the second-to-last episode preloads the one left", () => {
    expect(playsNext(library, e3, null)).toEqual({ next: e4, preload: [e4.setId] });
  });

  test("the last episode has nothing next and preloads nothing", () => {
    expect(playsNext(library, e4, null)).toEqual({ next: null, preload: [] });
  });

  test("a lesson plays on through its course but preloads nothing", () => {
    expect(playsNext(library, l1, null)).toEqual({ next: l2, preload: [] });
  });

  test("a documentary plays on through its collection but preloads nothing", () => {
    expect(playsNext(library, d1, null)).toEqual({ next: d2, preload: [] });
  });

  test("a film belongs to no collection: nothing next, nothing preloaded", () => {
    expect(playsNext(library, film, null)).toEqual({ next: null, preload: [] });
  });

  test("a list plays on in its own order and preloads nothing, even when it holds episodes", () => {
    expect(playsNext(library, e1, [e1, e4, film])).toEqual({ next: e4, preload: [] });
  });

  test("an anime episode plays the next one, and preloads the same as any other show", () => {
    expect(playsNext(library, a1, null)).toEqual({ next: a2, preload: [a2.setId] });
  });
});
