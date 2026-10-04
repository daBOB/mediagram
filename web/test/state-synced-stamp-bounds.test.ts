/**
 * A position, watchlist mark, collection, Kids mark or editor's choice that a
 * peer stamped past 2^53 − 1 is refused at the boundary. Taken in, it would
 * outrank every later edit of that row, on every device it reached. The
 * core holds the same lines (`exchange_tests.rs`); `state-watched-stamp-bounds`
 * covers `watched`/`unwatched`.
 */

import { afterEach, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates } from "../src/state/merge";
import { WatchState } from "../src/state/store";
import { parseRecord } from "../src/state/sync-record";

const opened: { state: WatchState; dir: string }[] = [];
afterEach(() => {
  for (const { state, dir } of opened.splice(0)) {
    state.close();
    rmSync(dir, { recursive: true, force: true });
  }
});

function machine() {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-synced-stamp-"));
  const state = new WatchState(join(dir, "state.db"));
  opened.push({ state, dir });
  return { state, me: state.createProfile("André")!.id };
}

/** 2^53, the largest 64-bit integer and a double far past both, as a peer's JSON text carries them. */
const PAST_SAFE = ["9007199254740992", "9223372036854775807", "1e300"];

/** One sync round the way `sync.ts` runs it: this device's own document, built
 * fresh, merged with a peer's parsed off the wire, and taken back in. `rows`
 * is the peer document's members after its header. */
function syncIn(state: WatchState, rows: string): void {
  const peer = parseRecord(`{"format":1,"device":"phone","writtenAt":1,${rows}}`);
  state.importMerged(mergeStates([state.exportRecord("laptop"), ...(peer === null ? [] : [peer])]));
}

/** The peer's rows for André. */
const andre = (rows: string) => `"profiles":[{"name":"André",${rows}}]`;

/** The peer's row synced in before and after an edit this device makes. */
function editBetweenRounds(state: WatchState, peer: string, edit: () => void): void {
  syncIn(state, peer);
  edit();
  syncIn(state, peer);
}

test.each(PAST_SAFE)("a peer's position at %s cannot outrank a later one", (stamp) => {
  const { state, me } = machine();
  editBetweenRounds(state, andre(`"progress":[{"setId":"01A","at":5,"updatedAt":${stamp}}]`), () =>
    state.setProgress(me, "01A", 900, null));
  expect(state.snapshot(me).progress.map((row) => row.at)).toEqual([900]);
});

test.each(PAST_SAFE)("a peer's watchlist mark at %s cannot outrank a later removal", (stamp) => {
  const { state, me } = machine();
  state.setWatchlisted(me, "01A", true);
  editBetweenRounds(state, andre(`"watchlist":[{"setId":"01A","updatedAt":${stamp}}]`), () =>
    state.setWatchlisted(me, "01A", false));
  expect(state.snapshot(me).watchlist).toEqual([]);
});

test.each(PAST_SAFE)("a peer's collection at %s cannot outrank a later rename", (stamp) => {
  const { state, me } = machine();
  const list = state.createCollection(me, "Films")!;
  const peer = andre(`"collections":[{"id":"${list.id}","name":"Stolen","items":["01A"],"updatedAt":${stamp}}]`);
  editBetweenRounds(state, peer, () => state.renameCollection(me, list.id, "Mine"));
  // The peer's items would have come in with its name: the list is one row.
  expect(state.snapshot(me).collections.map(({ name, items }) => ({ name, items })))
    .toEqual([{ name: "Mine", items: [] }]);
});

test.each(PAST_SAFE)("a peer's Kids mark at %s cannot outrank a later removal", (stamp) => {
  const { state } = machine();
  state.setKids("01A", true);
  editBetweenRounds(state, `"kids":[{"setId":"01A","updatedAt":${stamp}}]`, () => state.setKids("01A", false));
  expect(state.kids()).toEqual([]);
});

test.each(PAST_SAFE)("a peer's editor's choice at %s cannot outrank a later pick", (stamp) => {
  const { state } = machine();
  editBetweenRounds(state, `"editorsChoice":[{"setId":"01B","updatedAt":${stamp}}]`, () =>
    state.setEditorsChoice("01A", true));
  expect(state.editorsChoice()).toBe("01A");
});
