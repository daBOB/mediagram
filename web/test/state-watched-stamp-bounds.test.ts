/**
 * A `watched` time no clock wrote — past 2^53 − 1 — is refused at the
 * boundary, and an own write on a row an older build already let one into
 * mints a time inside the range rather than stepping further past it. The
 * core holds the same lines (`watched_exchange_tests.rs`).
 */

import { afterEach, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
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
  const dir = mkdtempSync(join(tmpdir(), "mediagram-watched-stamp-"));
  const path = join(dir, "state.db");
  const state = new WatchState(path);
  opened.push({ state, dir });
  return { state, me: state.createProfile("André")!.id, path };
}

/** 2^53, one past it, and the largest 64-bit integer, as a peer's JSON text carries them. */
const PAST_SAFE = ["9007199254740992", "9007199254740993", "9223372036854775807"];

/** One sync round the way `sync.ts` runs it: this device's own document,
 * built fresh, merged with a peer's parsed off the wire, and taken back in. */
function syncIn(state: WatchState, watched: string, unwatched: string): void {
  const peer = parseRecord(
    `{"format":1,"device":"phone","writtenAt":1,"profiles":[{"name":"André","progress":[],` +
      `"watched":[${watched}],"unwatched":[${unwatched}]}]}`,
  );
  state.importMerged(mergeStates([state.exportRecord("laptop"), ...(peer === null ? [] : [peer])]));
}

test.each(PAST_SAFE)("a peer's watched time of %s is not taken in", (stamp) => {
  const { state, me } = machine();
  state.setWatched(me, "01A", true);
  state.setWatched(me, "01A", false);
  state.setProgress(me, "01C", 600, null);

  syncIn(
    state,
    `{"setId":"01A","updatedAt":${stamp}}`,
    `{"setId":"01B","updatedAt":${stamp},"lastFinishedAt":1789000000000},` +
      `{"setId":"01C","updatedAt":1789000000000,"lastFinishedAt":${stamp}}`,
  );

  const { watched, progress } = state.snapshot(me);
  // Taken in, the mark would outrank this device's real removal for good,
  // and the removal finished at it would tombstone every later position.
  expect(watched).toEqual([]);
  expect(progress.map((row) => row.setId)).toEqual(["01C"]);
  const unwatched = state.exportRecord("laptop").profiles[0]!.unwatched ?? [];
  expect(unwatched.map((row) => row.setId)).toEqual(["01A"]);
});

test("an own write on a row already past the safe range mints a time within it", () => {
  const { state, me, path } = machine();
  state.setWatched(me, "01A", true);
  const db = new Database(path);
  try {
    // What importing a peer's largest-integer stamp used to leave behind.
    db.query("UPDATE watched SET finished_at = ?1").run(9223372036854775807n);
  } finally {
    db.close();
  }

  state.setWatched(me, "01A", false);
  const removal = state.exportRecord("laptop").profiles[0]!.unwatched![0]!;
  expect(removal.updatedAt).toBeLessThanOrEqual(Number.MAX_SAFE_INTEGER);

  state.setWatched(me, "01A", true);
  const mark = state.exportRecord("laptop").profiles[0]!.watched[0]!;
  expect(mark.updatedAt).toBeLessThanOrEqual(Number.MAX_SAFE_INTEGER);
  expect(state.snapshot(me).watched.map((row) => row.setId)).toEqual(["01A"]);
});
