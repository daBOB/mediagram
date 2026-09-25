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
import { mergeStates } from "../src/state/merge";

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
    expect(state.setPreference("nobody", "show:X", "audio", "en")).toBe(false);
    expect(state.snapshot("nobody")).toEqual({
      progress: [],
      watchlist: [],
      collections: [],
      watched: [],
      preferences: [],
    });
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

    state.setWatched(me, "01SET", true);
    state.setPreference(me, "key:tmdb-tv-1399", "audio", "en");

    expect(state.deleteProfile(me)).toBe(true);
    expect(state.profiles()).toEqual([]);
    // Everything of theirs, which now includes what they finished and what
    // they chose: both cascade from `profiles` like the rest of it.
    expect(state.snapshot(me)).toEqual({
      progress: [],
      watchlist: [],
      collections: [],
      watched: [],
      preferences: [],
    });
  });

  test("a profile that is not there cannot be written for", () => {
    const { state } = stateIn();
    state.setProgress("nobody", "01SET", 600, 2400);
    expect(state.snapshot("nobody").progress).toEqual([]);
  });
});

describe("the household's editor's choice", () => {
  const A = "01SET0000000000000000001";
  const B = "01SET0000000000000000002";

  test("is one pick: pinning another retires the last", () => {
    const { state } = stateIn();
    expect(state.editorsChoice()).toBeNull();
    state.setEditorsChoice(A, true);
    expect(state.editorsChoice()).toBe(A);
    Bun.sleepSync(2);
    state.setEditorsChoice(B, true);
    expect(state.editorsChoice()).toBe(B);
    // Retired, not deleted: the tombstone is what tells another device.
    const wire = state.exportRecord("here").editorsChoice ?? [];
    expect(wire.find((row) => row.setId === A)?.removed).toBe(true);
  });

  test("unpinning leaves no pick, and belongs to no profile", () => {
    const { state, me } = stateIn();
    state.setEditorsChoice(A, true);
    state.deleteProfile(me);
    expect(state.editorsChoice()).toBe(A);
    state.setEditorsChoice(A, false);
    expect(state.editorsChoice()).toBeNull();
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

describe("titles watched to the end", () => {
  test("are remembered, because finishing clears the position", () => {
    const { state, me } = stateIn();
    state.setWatched(me, "01SET0000000000000000001", true);
    expect(state.snapshot(me).watched.map((row) => row.setId)).toEqual(["01SET0000000000000000001"]);
  });

  test("belong to the viewer, not to the library", () => {
    // The opposite call to `kids`, deliberately: a children's film is a fact
    // about the title, having watched something is a fact about the person.
    const { state, me } = stateIn();
    const other = state.createProfile("Someone else")!;
    state.setWatched(me, "01SET0000000000000000001", true);
    expect(state.snapshot(me).watched.map((row) => row.setId)).toEqual(["01SET0000000000000000001"]);
    expect(state.snapshot(other.id).watched.map((row) => row.setId)).toEqual([]);
  });

  test("can be taken back", () => {
    const { state, me } = stateIn();
    state.setWatched(me, "01SET0000000000000000001", true);
    state.setWatched(me, "01SET0000000000000000001", false);
    expect(state.snapshot(me).watched.map((row) => row.setId)).toEqual([]);
  });

  test("finishing twice records once, not twice", () => {
    // Re-watching an episode and reaching the end again must not duplicate it.
    const { state, me } = stateIn();
    state.setWatched(me, "01SET0000000000000000001", true);
    state.setWatched(me, "01SET0000000000000000001", true);
    expect(state.snapshot(me).watched.map((row) => row.setId)).toEqual(["01SET0000000000000000001"]);
  });

  test("survive keeping a position again, which is what re-watching does", () => {
    const { state, me } = stateIn();
    state.setWatched(me, "01SET0000000000000000001", true);
    state.setProgress(me, "01SET0000000000000000001", 30, 1800);
    const held = state.snapshot(me);
    expect(held.watched.map((row) => row.setId)).toEqual(["01SET0000000000000000001"]);
    expect(held.progress.map((p) => p.setId)).toEqual(["01SET0000000000000000001"]);
  });

  test("are nothing rather than fatal on a player that cannot remember", () => {
    const state = new WatchState(null);
    expect(() => state.setWatched("p", "01SET0000000000000000001", true)).not.toThrow();
    expect(state.snapshot("p").watched.map((row) => row.setId)).toEqual([]);
  });
});

describe("what a viewer chose", () => {
  test("is remembered against a scope, not a title", () => {
    const { state, me } = stateIn();

    state.setPreference(me, "key:tmdb-tv-1399", "audio", "en");

    expect(state.snapshot(me).preferences).toEqual([
      { scope: "key:tmdb-tv-1399", name: "audio", value: "en" },
    ]);
  });

  test("choosing again replaces rather than accumulates", () => {
    const { state, me } = stateIn();

    state.setPreference(me, "show:Geldhochschule", "speed", "1.25");
    state.setPreference(me, "show:Geldhochschule", "speed", "1.5");

    expect(state.snapshot(me).preferences).toEqual([
      { scope: "show:Geldhochschule", name: "speed", value: "1.5" },
    ]);
  });

  test("an empty value forgets it", () => {
    // Rather than storing "" for every reader to recognise as meaning nothing.
    const { state, me } = stateIn();

    state.setPreference(me, "show:X", "subtitle", "de");
    state.setPreference(me, "show:X", "subtitle", "");

    expect(state.snapshot(me).preferences).toEqual([]);
  });

  test("one profile's choice is not another's", () => {
    const { state, me } = stateIn();
    const you = state.createProfile("B")!.id;

    state.setPreference(me, "key:tmdb-tv-1399", "audio", "en");

    expect(state.snapshot(you).preferences).toEqual([]);
  });

  test("a scope or a name that is not a string is refused", () => {
    const { state, me } = stateIn();

    expect(state.setPreference(me, null, "audio", "en")).toBe(false);
    expect(state.setPreference(me, "show:X", 5, "en")).toBe(false);
    expect(state.setPreference(me, "  ", "audio", "en")).toBe(false);
    expect(state.snapshot(me).preferences).toEqual([]);
  });

  test("and all three are capped, so one row cannot be a database", () => {
    const { state, me } = stateIn();

    state.setPreference(me, "x".repeat(5000), "y".repeat(5000), "z".repeat(5000));

    const [held] = state.snapshot(me).preferences;
    expect(held!.scope.length).toBe(200);
    expect(held!.name.length).toBe(200);
    expect(held!.value.length).toBe(200);
  });
});

describe("what this player tells other devices", () => {
  test("exports every profile, with the times a merge needs", () => {
    const { state, me } = stateIn();
    state.setProgress(me, "01SET", 742, 1204);
    state.setWatched(me, "01DONE", true);

    const record = state.exportRecord("laptop");

    expect(record.device).toBe("laptop");
    expect(record.profiles[0]!.progress[0]).toMatchObject({ setId: "01SET", at: 742 });
    expect(record.profiles[0]!.progress[0]!.updatedAt).toBeGreaterThan(0);
    expect(record.profiles[0]!.watched[0]!.setId).toBe("01DONE");
  });

  test("and a second profile is in the same document", () => {
    // A document belongs to a device, not to whoever happens to be watching.
    const { state } = stateIn();
    state.createProfile("Sam");
    expect(state.exportRecord("laptop").profiles).toHaveLength(2);
  });
});

describe("what this player takes back", () => {
  test("an imported empty profile counts as a change only when it is created", () => {
    const { state } = stateIn();
    const merged = { profiles: [{ name: "new viewer", displayName: "New viewer", progress: [], watched: [] }] };
    expect(state.importMerged(merged)).toBe(1);
    expect(state.profiles().some((profile) => profile.name === "New viewer")).toBe(true);
    expect(state.importMerged(merged)).toBe(0);
    expect(state.importMerged({ profiles: [{ ...merged.profiles[0]!, name: " NEW VIEWER " }] })).toBe(0);
  });
  test("a position it has never seen arrives", () => {
    const { state, me } = stateIn();
    const name = state.profiles().find((p) => p.id === me)!.name;

    state.importMerged({
      profiles: [
        { name, displayName: name, progress: [{ setId: "01NEW", at: 500, duration: 1204, updatedAt: 9000 }], watched: [] },
      ],
    });

    expect(state.snapshot(me).progress[0]).toMatchObject({ setId: "01NEW", at: 500 });
  });

  test("a newer local position is not overwritten by an older one", () => {
    const { state, me } = stateIn();
    const name = state.profiles().find((p) => p.id === me)!.name;
    state.setProgress(me, "01SET", 900, 1204);

    state.importMerged({
      profiles: [
        { name, displayName: name, progress: [{ setId: "01SET", at: 10, duration: 1204, updatedAt: 1 }], watched: [] },
      ],
    });

    expect(state.snapshot(me).progress[0]!.at).toBe(900);
  });

  test("a completion elsewhere clears the position here", () => {
    // The laptop finished it; this machine still held where it had got to.
    const { state, me } = stateIn();
    const name = state.profiles().find((p) => p.id === me)!.name;
    state.setProgress(me, "01SET", 900, 1204);

    state.importMerged({
      profiles: [{ name, displayName: name, progress: [], watched: [{ setId: "01SET", updatedAt: Date.now() + 5000 }] }],
    });

    expect(state.snapshot(me).progress).toEqual([]);
    expect(state.snapshot(me).watched.map((row) => row.setId)).toEqual(["01SET"]);
  });

  test("a position this player has and the merge does not is left alone", () => {
    // Corrective, never wholesale: a sync that reached only some devices must
    // not erase what the missing ones knew.
    const { state, me } = stateIn();
    const name = state.profiles().find((p) => p.id === me)!.name;
    state.setProgress(me, "01MINE", 300, 1204);

    state.importMerged({ profiles: [{ name, displayName: name, progress: [], watched: [] }] });

    expect(state.snapshot(me).progress[0]!.setId).toBe("01MINE");
  });

  test("a viewer this player has never met gets a profile", () => {
    // The first thing a second machine knows about someone is a document the
    // first one wrote; refusing to create would make the sync one-way.
    const { state } = stateIn();
    const before = state.profiles().length;

    state.importMerged({
      profiles: [
        { name: "Sam", displayName: "Sam", progress: [{ setId: "01A", at: 1, duration: null, updatedAt: 9000 }], watched: [] },
      ],
    });

    const sam = state.profiles().find((p) => p.name === "Sam");
    expect(state.profiles()).toHaveLength(before + 1);
    expect(state.snapshot(sam!.id).progress[0]!.setId).toBe("01A");
  });

  test("and the same viewer typed differently is not a second profile", () => {
    const { state, me } = stateIn();
    const name = state.profiles().find((p) => p.id === me)!.name;
    const before = state.profiles().length;

    state.importMerged({
      profiles: [
        {
          name: ` ${name.toUpperCase()} `,
          displayName: name,
          progress: [{ setId: "01A", at: 1, duration: null, updatedAt: 9000 }],
          watched: [],
        },
      ],
    });

    expect(state.profiles()).toHaveLength(before);
    expect(state.snapshot(me).progress[0]!.setId).toBe("01A");
  });

  test("importing a merge of this player's own document changes nothing", () => {
    const { state, me } = stateIn();
    state.setProgress(me, "01SET", 742, 1204);
    const before = state.snapshot(me);

    expect(state.importMerged(mergeStates([state.exportRecord("self")]))).toBe(0);
    expect(state.snapshot(me)).toEqual(before);
  });
});

describe("kids profiles", () => {
  test("a profile is created as a kids profile only when asked", () => {
    const { state } = stateIn();
    const mia = state.createProfile("Mia", true)!;
    expect(mia.kids).toBe(true);
    expect(state.profiles().map((p) => [p.name, p.kids])).toEqual([["André", false], ["Mia", true]]);
  });

  test("the record carries kids only on the kids profile", () => {
    const { state } = stateIn();
    state.createProfile("Mia", true);
    const record = state.exportRecord("laptop");
    const byName = new Map(record.profiles.map((p) => [p.name, p]));
    expect(byName.get("Mia")!.kids).toBe(true);
    expect("kids" in byName.get("André")!).toBe(false);
  });

  test("an import makes an existing ordinary profile of that name a kids profile", () => {
    const { state } = stateIn();
    state.createProfile("Mia");
    const changed = state.importMerged({
      profiles: [{ name: "mia", displayName: "Mia", kids: true, progress: [], watched: [] }],
    });
    expect(changed).toBe(1);
    expect(state.profiles().find((p) => p.name === "Mia")!.kids).toBe(true);
  });

  test("an import creates a profile it has never met with the flag", () => {
    const { state } = stateIn();
    state.importMerged({ profiles: [{ name: "ben", displayName: "Ben", kids: true, progress: [], watched: [] }] });
    expect(state.profiles().find((p) => p.name === "Ben")!.kids).toBe(true);
  });

  test("an import never turns a kids profile back into an ordinary one", () => {
    const { state } = stateIn();
    state.createProfile("Mia", true);
    const changed = state.importMerged({ profiles: [{ name: "mia", displayName: "Mia", progress: [], watched: [] }] });
    expect(changed).toBe(0);
    expect(state.profiles().find((p) => p.name === "Mia")!.kids).toBe(true);
  });
});
