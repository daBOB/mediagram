/**
 * The one database this player writes.
 *
 * The behaviour worth pinning is what happens when it cannot write at all: a
 * player whose state directory is read-only must lose watch positions, not
 * the library.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];

/** A store with one profile already in it, which is the usual case. */
const stateIn = () => {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-state-"));
  dirs.push(dir);
  // Nested on purpose: the directory must be created, not assumed.
  const state = new WatchState(join(dir, "nested", "state.db"));
  const me = state.createProfile("André")!;
  return { state, me: me.id };
};

afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

describe("positions", () => {
  test("a position is kept, and moving it replaces rather than piles up", () => {
    const { state, me } = stateIn();
    state.setProgress(me, "01SET", 120, 2400);
    state.setProgress(me, "01SET", 480, 2400);

    const { progress } = state.snapshot(me);
    expect(progress).toHaveLength(1);
    expect(progress[0]).toMatchObject({ setId: "01SET", at: 480, duration: 2400 });
  });

  test("a runtime nobody knows is kept as nothing, not as zero", () => {
    const { state, me } = stateIn();
    state.setProgress(me, "01SET", 90, null);
    expect(state.snapshot(me).progress[0]!.duration).toBeNull();
  });

  test("a negative position is not a position", () => {
    const { state, me } = stateIn();
    state.setProgress(me, "01SET", -30, 2400);
    expect(state.snapshot(me).progress[0]!.at).toBe(0);
  });

  test("clearing forgets it", () => {
    const { state, me } = stateIn();
    state.setProgress(me, "01SET", 120, 2400);
    state.clearProgress(me, "01SET");
    expect(state.snapshot(me).progress).toEqual([]);
  });

  test("the most recently watched comes first", () => {
    const { state, me } = stateIn();
    state.setProgress(me, "01OLD", 10, 100);
    Bun.sleepSync(2);
    state.setProgress(me, "01NEW", 10, 100);
    expect(state.snapshot(me).progress.map((p) => p.setId)).toEqual(["01NEW", "01OLD"]);
  });
});

describe("the watchlist", () => {
  test("adds once, however many times it is asked", () => {
    const { state, me } = stateIn();
    state.setWatchlisted(me, "01SET", true);
    state.setWatchlisted(me, "01SET", true);
    expect(state.snapshot(me).watchlist).toEqual(["01SET"]);
  });

  test("and removes", () => {
    const { state, me } = stateIn();
    state.setWatchlisted(me, "01SET", true);
    state.setWatchlisted(me, "01SET", false);
    expect(state.snapshot(me).watchlist).toEqual([]);
  });
});

describe("collections", () => {
  test("a list keeps the order things were put in it", () => {
    const { state, me } = stateIn();
    const list = state.createCollection(me, "Sunday night")!;
    for (const set of ["01C", "01A", "01B"]) state.addToCollection(me, list.id, set);

    const [held] = state.snapshot(me).collections;
    expect(held!.name).toBe("Sunday night");
    expect(held!.items).toEqual(["01C", "01A", "01B"]);
  });

  test("adding the same title twice leaves it in once, where it was", () => {
    const { state, me } = stateIn();
    const list = state.createCollection(me, "Kurs")!;
    state.addToCollection(me, list.id, "01A");
    state.addToCollection(me, list.id, "01B");
    state.addToCollection(me, list.id, "01A");

    expect(state.snapshot(me).collections[0]!.items).toEqual(["01A", "01B"]);
  });

  test("a name is tidied, and one that is only space is refused", () => {
    const { state, me } = stateIn();
    expect(state.createCollection(me, "  Sunday   night  ")!.name).toBe("Sunday night");
    expect(state.createCollection(me, "   ")).toBeNull();
    expect(state.createCollection(me, null as never)).toBeNull();
  });

  test("renaming keeps the items", () => {
    const { state, me } = stateIn();
    const list = state.createCollection(me, "Erst")!;
    state.addToCollection(me, list.id, "01A");

    expect(state.renameCollection(me, list.id, "Dann")).toBe(true);
    expect(state.snapshot(me).collections[0]).toMatchObject({ name: "Dann", items: ["01A"] });
  });

  test("deleting a list takes its items with it", () => {
    const { state, me } = stateIn();
    const list = state.createCollection(me, "Weg")!;
    state.addToCollection(me, list.id, "01A");

    expect(state.deleteCollection(me, list.id)).toBe(true);
    expect(state.snapshot(me).collections).toEqual([]);
    // The cascade, checked rather than assumed: a stale row here would come
    // back as a phantom item the next time a list took that id.
    expect(state.addToCollection(me, list.id, "01B")).toBe(false);
  });

  test("a list that is not there says so rather than inventing one", () => {
    const { state, me } = stateIn();
    expect(state.renameCollection(me, "nope", "x")).toBe(false);
    expect(state.deleteCollection(me, "nope")).toBe(false);
    expect(state.addToCollection(me, "nope", "01A")).toBe(false);
    expect(state.removeFromCollection(me, "nope", "01A")).toBe(false);
  });
});

describe("a player that cannot remember", () => {
  test("still answers every question, with nothing", () => {
    const state = new WatchState(null);

    expect(state.remembers).toBe(false);
    expect(state.profiles()).toEqual([]);
    expect(state.createProfile("A")).toBeNull();
    expect(state.snapshot("nobody")).toEqual({ progress: [], watchlist: [], collections: [] });
    // None of these may throw: they are called from a request handler.
    state.setProgress("nobody", "01SET", 10, 20);
    state.clearProgress("nobody", "01SET");
    state.setWatchlisted("nobody", "01SET", true);
    expect(state.createCollection("nobody", "x")).toBeNull();
    expect(state.addToCollection("nobody", "x", "01SET")).toBe(false);
  });

  test("a path that cannot be created is that, not a crash", () => {
    // `/proc` exists and refuses new directories on Linux.
    const state = new WatchState("/proc/mediagram-nope/state.db");
    expect(state.remembers).toBe(false);
    expect(state.snapshot("nobody").progress).toEqual([]);
  });
});

describe("across restarts", () => {
  test("a position written by one process is there for the next", () => {
    const dir = mkdtempSync(join(tmpdir(), "mediagram-state-"));
    dirs.push(dir);
    const path = join(dir, "state.db");

    const first = new WatchState(path);
    const me = first.createProfile("André")!.id;
    first.setProgress(me, "01SET", 640, 2400);
    const list = first.createCollection(me, "Bleibt")!;
    first.addToCollection(me, list.id, "01SET");
    first.close();

    const second = new WatchState(path);
    expect(second.profiles().map((profile) => profile.name)).toEqual(["André"]);
    expect(second.snapshot(me).progress[0]).toMatchObject({ setId: "01SET", at: 640 });
    expect(second.snapshot(me).collections[0]!.items).toEqual(["01SET"]);
    second.close();
  });
});

describe("one profile cannot see another", () => {
  test("positions, lists and watchlists are each their own", () => {
    const { state, me } = stateIn();
    const you = state.createProfile("Maja")!.id;

    state.setProgress(me, "01SET", 600, 2400);
    state.setWatchlisted(me, "01SET", true);
    const mine = state.createCollection(me, "Meine Liste")!;
    state.addToCollection(me, mine.id, "01SET");

    const theirs = state.snapshot(you);
    expect(theirs.progress).toEqual([]);
    expect(theirs.watchlist).toEqual([]);
    expect(theirs.collections).toEqual([]);
  });

  test("a list cannot be reached by knowing its id", () => {
    const { state, me } = stateIn();
    const you = state.createProfile("Maja")!.id;
    const mine = state.createCollection(me, "Meine Liste")!;

    expect(state.addToCollection(you, mine.id, "01SET")).toBe(false);
    expect(state.renameCollection(you, mine.id, "Deine")).toBe(false);
    expect(state.deleteCollection(you, mine.id)).toBe(false);
    // Still exactly as its owner left it.
    expect(state.snapshot(me).collections[0]).toMatchObject({ name: "Meine Liste", items: [] });
  });

  test("deleting a profile takes everything that was theirs", () => {
    const { state, me } = stateIn();
    state.setProgress(me, "01SET", 600, 2400);
    state.setWatchlisted(me, "01SET", true);
    const mine = state.createCollection(me, "Weg")!;
    state.addToCollection(me, mine.id, "01SET");

    expect(state.deleteProfile(me)).toBe(true);
    expect(state.profiles()).toEqual([]);
    expect(state.snapshot(me)).toEqual({ progress: [], watchlist: [], collections: [] });
  });

  test("a profile that is not there cannot be written for", () => {
    const { state } = stateIn();
    state.setProgress("nobody", "01SET", 600, 2400);
    expect(state.snapshot("nobody").progress).toEqual([]);
  });
});

describe("titles marked as a child's", () => {
  test("are remembered, and forgotten again", () => {
    const { state } = stateIn();
    state.setKids("01SET0000000000000000001", true);
    expect(state.kids()).toEqual(["01SET0000000000000000001"]);
    state.setKids("01SET0000000000000000001", false);
    expect(state.kids()).toEqual([]);
  });

  test("belong to the library, not to whoever is watching", () => {
    // The one table here with no profile_id. Marking a film as a child's is
    // not a statement about who is watching, so a second profile must see it.
    const { state } = stateIn();
    state.setKids("01SET0000000000000000001", true);
    const second = state.createProfile("Someone else")!;
    expect(state.snapshot(second.id).watchlist).toEqual([]);
    expect(state.kids()).toEqual(["01SET0000000000000000001"]);
  });

  test("survive deleting the profile that marked them", () => {
    const { state, me } = stateIn();
    state.setKids("01SET0000000000000000001", true);
    state.deleteProfile(me);
    expect(state.kids()).toEqual(["01SET0000000000000000001"]);
  });

  test("come back newest first, so the shelf leads with what was just marked", () => {
    const { state } = stateIn();
    state.setKids("01SET0000000000000000001", true);
    Bun.sleepSync(2);
    state.setKids("01SET0000000000000000002", true);
    expect(state.kids()).toEqual(["01SET0000000000000000002", "01SET0000000000000000001"]);
  });

  test("marking twice marks once", () => {
    const { state } = stateIn();
    state.setKids("01SET0000000000000000001", true);
    state.setKids("01SET0000000000000000001", true);
    expect(state.kids()).toEqual(["01SET0000000000000000001"]);
  });

  test("are empty rather than fatal on a player that cannot remember", () => {
    const state = new WatchState(null);
    expect(() => state.setKids("01SET0000000000000000001", true)).not.toThrow();
    expect(state.kids()).toEqual([]);
  });
});
