/**
 * Watchlist, Kids and collections on the sync record: the v6 migration, the
 * tombstone each of the three now carries, and the two-machine round trip
 * that is the whole point of carrying one.
 *
 * `shared-watch-state-fixtures.test.ts` covers `mergeStates` itself against
 * `lists-merge.json`; this file covers the store around it — schema,
 * export, import, and hostile parsing of what another device claimed.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { GROUPS } from "../src/state/schema";
import { mergeStates } from "../src/state/merge";
import { parseRecord } from "../src/state/sync-record";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
const tempPath = () => {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-lists-sync-"));
  dirs.push(dir);
  return join(dir, "state.db");
};
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

describe("v5 to v6", () => {
  /** A database at version 5: a watchlist, a kids mark and a collection,
   * none of them removable yet. */
  function v5With(path: string, rows: () => string[]): void {
    const db = new Database(path, { create: true });
    for (const group of GROUPS.slice(0, 5)) for (const statement of group) db.exec(statement);
    db.query("INSERT OR REPLACE INTO state_meta(key, value) VALUES ('schema_version', '5')").run();
    for (const statement of rows()) db.exec(statement);
    db.close();
  }

  test("carries what a v5 player recorded across untouched", () => {
    const path = tempPath();
    v5With(path, () => [
      "INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 1)",
      "INSERT INTO watchlist(profile_id, set_id, added_at) VALUES ('p1', '01A', 1)",
      "INSERT INTO kids(set_id, marked_at) VALUES ('01K', 1)",
      "INSERT INTO collections(id, profile_id, name, created_at) VALUES ('c1', 'p1', 'Sunday', 1)",
      "INSERT INTO collection_items(collection_id, set_id, position) VALUES ('c1', '01A', 0)",
    ]);

    const state = new WatchState(path);
    const held = state.snapshot("p1");
    expect(held.watchlist).toEqual(["01A"]);
    expect(state.kids()).toEqual(["01K"]);
    expect(held.collections[0]).toMatchObject({ name: "Sunday", items: ["01A"] });
    state.close();
  });

  test("a collection's new updated_at starts from created_at, not zero", () => {
    const path = tempPath();
    v5With(path, () => [
      "INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 1)",
      "INSERT INTO collections(id, profile_id, name, created_at) VALUES ('c1', 'p1', 'Sunday', 424242)",
    ]);

    const state = new WatchState(path);
    // Backfilled from `created_at`, checked the only way available from
    // outside: a rename right after open must not read as older than the
    // list already was, which it would if `updated_at` had been left at 0.
    expect(state.renameCollection("p1", "c1", "Sunday night")).toBe(true);
    state.close();
  });

  test("opening again changes nothing at all", () => {
    const path = tempPath();
    v5With(path, () => ["INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 1)"]);

    const first = new WatchState(path);
    first.setWatchlisted("p1", "01A", true);
    first.close();

    const second = new WatchState(path);
    expect(second.snapshot("p1").watchlist).toEqual(["01A"]);
    second.close();
  });
});

describe("a removal is a tombstone, not a gap", () => {
  test("the watchlist: removing clears the shelf but keeps the row on the wire", () => {
    const state = new WatchState(tempPath());
    const me = state.createProfile("André")!.id;

    state.setWatchlisted(me, "01A", true);
    state.setWatchlisted(me, "01A", false);

    expect(state.snapshot(me).watchlist).toEqual([]);
    const row = state.exportRecord("laptop").profiles[0]!.watchlist!.find((r) => r.setId === "01A");
    expect(row).toMatchObject({ setId: "01A", removed: true });
    state.close();
  });

  test("re-adding clears the tombstone rather than piling up a second row", () => {
    const state = new WatchState(tempPath());
    const me = state.createProfile("André")!.id;

    state.setWatchlisted(me, "01A", true);
    state.setWatchlisted(me, "01A", false);
    state.setWatchlisted(me, "01A", true);

    expect(state.snapshot(me).watchlist).toEqual(["01A"]);
    const rows = state.exportRecord("laptop").profiles[0]!.watchlist!.filter((r) => r.setId === "01A");
    expect(rows).toHaveLength(1);
    expect(rows[0]!.removed).toBeUndefined();
    state.close();
  });

  test("kids: the same shape, at the top level", () => {
    const state = new WatchState(tempPath());
    state.setKids("01A", true);
    state.setKids("01A", false);

    expect(state.kids()).toEqual([]);
    const row = state.exportRecord("laptop").kids!.find((r) => r.setId === "01A");
    expect(row).toMatchObject({ setId: "01A", removed: true });
    state.close();
  });

  test("a deleted collection cannot be added to again — the tombstone check `addToCollection` makes", () => {
    const state = new WatchState(tempPath());
    const me = state.createProfile("André")!.id;
    const list = state.createCollection(me, "Weg")!;
    state.deleteCollection(me, list.id);

    expect(state.addToCollection(me, list.id, "01A")).toBe(false);
    const row = state.exportRecord("laptop").profiles[0]!.collections!.find((c) => c.id === list.id);
    expect(row).toMatchObject({ id: list.id, removed: true });
    state.close();
  });

  test("adding an item bumps the list's own clock", () => {
    const state = new WatchState(tempPath());
    const me = state.createProfile("André")!.id;
    const list = state.createCollection(me, "Kurs")!;
    const before = state.exportRecord("laptop").profiles[0]!.collections!.find((c) => c.id === list.id)!.updatedAt;

    Bun.sleepSync(2);
    state.addToCollection(me, list.id, "01A");
    const after = state.exportRecord("laptop").profiles[0]!.collections!.find((c) => c.id === list.id)!.updatedAt;
    expect(after).toBeGreaterThan(before);
  });
});

/** What actually crosses the channel: text, parsed as if it were a stranger's. */
function machine(device: string) {
  const dir = mkdtempSync(join(tmpdir(), `mediagram-lists-${device}-`));
  dirs.push(dir);
  const state = new WatchState(join(dir, "state.db"));
  const me = state.createProfile("André")!.id;
  return { state, me, device };
}
const publish = (m: ReturnType<typeof machine>) => parseRecord(JSON.stringify(m.state.exportRecord(m.device)))!;
const sync = (m: ReturnType<typeof machine>, channel: ReturnType<typeof publish>[]) =>
  m.state.importMerged(mergeStates([publish(m), ...channel]));

describe("two machines, a watchlist entry removed on one", () => {
  test("the other converges and a later round does not resurrect it", () => {
    const laptop = machine("laptop");
    const desktop = machine("desktop");

    laptop.state.setWatchlisted(laptop.me, "01A", true);
    sync(desktop, [publish(laptop)]);
    expect(desktop.state.snapshot(desktop.me).watchlist).toEqual(["01A"]);

    // A minute later, in the sense that matters: the remove must carry a
    // later millisecond than the add, or the tie is indistinguishable from
    // "nothing changed" to the `updatedAt` a merge reconciles by.
    Bun.sleepSync(2);
    laptop.state.setWatchlisted(laptop.me, "01A", false);
    sync(desktop, [publish(laptop)]);
    expect(desktop.state.snapshot(desktop.me).watchlist).toEqual([]);

    // A later round, laptop's document unchanged: desktop must not read its
    // own now-tombstoned row as a fresh addition and bring it back.
    sync(desktop, [publish(laptop)]);
    expect(desktop.state.snapshot(desktop.me).watchlist).toEqual([]);
  });

  test("a stale document — an old-format one, with none of these keys — brings nothing back", () => {
    const desktop = machine("desktop");
    desktop.state.setWatchlisted(desktop.me, "01A", true);
    desktop.state.setWatchlisted(desktop.me, "01A", false);

    // A device on the old format: no `watchlist`, no `kids`, no
    // `collections` at all — parseRecord leaves them `undefined`, and the
    // merge must not treat that as "nothing to say" turning into "put it
    // back".
    const stale = parseRecord(
      JSON.stringify({ format: 1, device: "phone", writtenAt: 0, profiles: [{ name: "André", progress: [], watched: [] }] }),
    )!;
    sync(desktop, [stale]);
    expect(desktop.state.snapshot(desktop.me).watchlist).toEqual([]);
  });
});

describe("a collection carried whole", () => {
  test("a rename and an added title from two machines: the later edit wins outright", () => {
    const laptop = machine("laptop");
    const desktop = machine("desktop");
    const list = laptop.state.createCollection(laptop.me, "Sunday")!;
    laptop.state.addToCollection(laptop.me, list.id, "01A");
    sync(desktop, [publish(laptop)]);

    Bun.sleepSync(2);
    laptop.state.renameCollection(laptop.me, list.id, "Sunday night");
    laptop.state.addToCollection(laptop.me, list.id, "01B");
    sync(desktop, [publish(laptop)]);

    expect(desktop.state.snapshot(desktop.me).collections[0]).toMatchObject({
      name: "Sunday night",
      items: ["01A", "01B"],
    });
  });
});

describe("hostile input", () => {
  const doc = (over: Record<string, unknown>) =>
    JSON.stringify({
      format: 1,
      device: "laptop",
      writtenAt: 1,
      profiles: [{ name: "André", progress: [], watched: [], ...over }],
    });

  test("a collection name is trimmed and capped at 200, not trusted whole", () => {
    const long = "x".repeat(500);
    const record = parseRecord(
      doc({ collections: [{ id: "c1", name: `  ${long}  `, items: ["01A"], updatedAt: 1 }] }),
    )!;
    expect(record.profiles[0]!.collections![0]!.name).toHaveLength(200);
  });

  test("a collection with only whitespace for a name is dropped, its neighbour kept", () => {
    const record = parseRecord(
      doc({
        collections: [
          { id: "c1", name: "   ", items: [], updatedAt: 1 },
          { id: "c2", name: "Kept", items: [], updatedAt: 1 },
        ],
      }),
    )!;
    expect(record.profiles[0]!.collections!.map((c) => c.id)).toEqual(["c2"]);
  });

  test("a list row with no id, or a zero timestamp, is dropped", () => {
    const record = parseRecord(doc({ watchlist: [{ updatedAt: 1 }, { setId: "01A", updatedAt: 0 }] }))!;
    expect(record.profiles[0]!.watchlist).toEqual([]);
  });

  test("a document written before these existed parses with them absent, not empty", () => {
    const record = parseRecord(
      JSON.stringify({ format: 1, device: "laptop", writtenAt: 1, profiles: [{ name: "André", progress: [], watched: [] }] }),
    )!;
    expect(record.kids).toBeUndefined();
    expect(record.profiles[0]!.watchlist).toBeUndefined();
    expect(record.profiles[0]!.collections).toBeUndefined();
  });
});
