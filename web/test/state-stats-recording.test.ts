/**
 * Counting watch time inside the position write.
 *
 * What one step counts is pinned by `stats-step.json`; this is the store
 * around it: that a step lands on the title and on the right day, that only
 * this device's own writes count, that a restart and a finish forget what
 * they should, and that two writers cannot count more than the wall clock.
 */

import { afterEach, describe, expect, setSystemTime, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates } from "../src/state/merge";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
/** A store with André in it — at `path` again, to reopen one. */
function stateAt(path?: string) {
  let file = path;
  if (file === undefined) {
    const dir = mkdtempSync(join(tmpdir(), "mediagram-stats-"));
    dirs.push(dir);
    file = join(dir, "state.db");
  }
  const state = new WatchState(file);
  const me = state.profiles()[0]?.id ?? state.createProfile("André")!.id;
  return { state, me, path: file };
}
afterEach(() => {
  setSystemTime();
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

/** 21:00 local on 2026-10-03, and `seconds` after it. */
const T0 = new Date(2026, 9, 3, 21, 0, 0).getTime();
const at = (seconds: number) => setSystemTime(new Date(T0 + seconds * 1000));

/** Every stats row in the file, read beside the store's own connection. */
function counted(path: string) {
  const db = new Database(path, { readonly: true });
  try {
    const titles = db.query(
      `SELECT set_id AS setId, device, started_at AS startedAt, last_watched_at AS lastWatchedAt,
              seconds, again_at AS againAt, updated_at AS updatedAt FROM stats_titles ORDER BY set_id, device`,
    ).all() as {
      setId: string; device: string; startedAt: number; lastWatchedAt: number;
      seconds: number; againAt: number | null; updatedAt: number;
    }[];
    const days = db.query("SELECT day, device, seconds, updated_at AS updatedAt FROM stats_days ORDER BY day, device").all();
    return { titles, days };
  } finally {
    db.close();
  }
}

describe("a position write", () => {
  test("ten-second ticks add up on the title and on today", () => {
    const { state, me, path } = stateAt();
    at(0); state.setProgress(me, "01FILM", 100, 7200);
    at(10); state.setProgress(me, "01FILM", 110, 7200);
    at(20); state.setProgress(me, "01FILM", 120, 7200);

    const device = state.deviceId();
    expect(counted(path)).toEqual({
      titles: [{ setId: "01FILM", device, startedAt: T0, lastWatchedAt: T0 + 20_000, seconds: 20, againAt: null, updatedAt: T0 + 20_000 }],
      days: [{ day: "2026-10-03", device, seconds: 20, updatedAt: T0 + 20_000 }],
    });
    // The position itself is written as it always was.
    expect(state.snapshot(me).progress).toEqual([{ setId: "01FILM", at: 120, duration: 7200, updatedAt: T0 + 20_000 }]);
  });

  test("a pause, a seek forward and a seek back count only the time spent watching", () => {
    const { state, me, path } = stateAt();
    at(0); state.setProgress(me, "01FILM", 100, 7200);
    at(3); state.setProgress(me, "01FILM", 103, 7200); // paused here
    at(613); state.setProgress(me, "01FILM", 113, 7200); // ten seconds after resuming
    at(623); state.setProgress(me, "01FILM", 1900, 7200); // seeked forward
    at(633); state.setProgress(me, "01FILM", 50, 7200); // seeked back
    expect(counted(path).titles[0]!.seconds).toBe(23);
  });

  test("a step across midnight counts on the day it ends", () => {
    const { state, me, path } = stateAt();
    setSystemTime(new Date(2026, 9, 3, 23, 59, 55));
    state.setProgress(me, "01FILM", 100, 7200);
    setSystemTime(new Date(2026, 9, 4, 0, 0, 5));
    state.setProgress(me, "01FILM", 110, 7200);
    expect(counted(path).days).toMatchObject([{ day: "2026-10-04", seconds: 10 }]);
  });

  test("a restarted server counts nothing for its first write of each title", () => {
    const first = stateAt();
    at(0); first.state.setProgress(first.me, "01FILM", 100, 7200);
    at(10); first.state.setProgress(first.me, "01FILM", 110, 7200);
    first.state.close();

    const second = stateAt(first.path);
    at(20); second.state.setProgress(second.me, "01FILM", 120, 7200);
    expect(counted(first.path).titles[0]).toMatchObject({ seconds: 10, startedAt: T0 });
    at(30); second.state.setProgress(second.me, "01FILM", 130, 7200);
    expect(counted(first.path).titles[0]!.seconds).toBe(20);
  });

  test("two tabs on one title never count more than the wall clock", () => {
    const { state, me, path } = stateAt();
    // Tab A near the start, tab B further on, saving in turn every 5 s.
    for (const [second, position] of [[0, 100], [5, 500], [10, 110], [15, 510], [20, 120]] as const) {
      at(second); state.setProgress(me, "01FILM", position, 7200);
    }
    const { seconds } = counted(path).titles[0]!;
    expect(seconds).toBeLessThanOrEqual(20);
    expect(seconds).toBe(10);
  });

  test("a clock that stepped back still moves each row's stamp forward", () => {
    const { state, me, path } = stateAt();
    at(100); state.setProgress(me, "01FILM", 100, 7200);
    at(110); state.setProgress(me, "01FILM", 110, 7200);
    // The clock goes back a minute; playing carries on.
    at(50); state.setProgress(me, "01FILM", 120, 7200);
    expect(counted(path).titles[0]!.updatedAt).toBe(T0 + 110_001);
    at(60); state.setProgress(me, "01FILM", 130, 7200);
    const { titles, days } = counted(path);
    expect(titles[0]).toMatchObject({ seconds: 20, lastWatchedAt: T0 + 60_000, updatedAt: T0 + 110_002 });
    expect(days).toMatchObject([{ seconds: 20, updatedAt: T0 + 110_001 }]);
  });

  test("finishing forgets the last write, so playing on counts from a fresh first write", () => {
    const { state, me, path } = stateAt();
    at(0); state.setProgress(me, "01FILM", 100, 7200);
    at(10); state.setProgress(me, "01FILM", 110, 7200);
    at(12); state.setWatched(me, "01FILM", true);
    at(20); state.setProgress(me, "01FILM", 115, 7200);
    expect(counted(path).titles[0]!.seconds).toBe(10);
  });

  test("starting a finished title over marks it watched again, once, and counts on top", () => {
    const { state, me, path } = stateAt();
    at(0); state.setProgress(me, "01FILM", 100, 7200);
    at(10); state.setProgress(me, "01FILM", 110, 7200);
    at(20); state.setWatched(me, "01FILM", true);
    at(60); state.setProgress(me, "01FILM", 0, 7200);
    at(70); state.setProgress(me, "01FILM", 10, 7200);
    at(80); state.setProgress(me, "01FILM", 20, 7200);
    expect(counted(path).titles[0]).toMatchObject({ startedAt: T0, againAt: T0 + 60_000, seconds: 30 });
  });

  test("a position merged in from another device counts nothing here", () => {
    const { state, me, path } = stateAt();
    at(0);
    state.importMerged(mergeStates([{
      format: 1, device: "phone", writtenAt: 0,
      profiles: [{ name: "André", progress: [{ setId: "01FILM", at: 900, duration: 7200, updatedAt: T0 }], watched: [] }],
    }]));
    expect(counted(path)).toEqual({ titles: [], days: [] });

    // Nor does it leave a last write behind for this device's next one to count from.
    at(10); state.setProgress(me, "01FILM", 910, 7200);
    expect(counted(path).titles).toMatchObject([{ seconds: 0, againAt: null }]);
  });

  test("a write for a profile another device deleted is refused quietly and counts nothing", () => {
    const { state, path } = stateAt();
    const gone = state.createProfile("Ben")!.id;
    state.deleteProfile(gone);
    at(0); expect(() => state.setProgress(gone, "01FILM", 100, 7200)).not.toThrow();
    at(10); expect(() => state.setProgress(gone, "01FILM", 110, 7200)).not.toThrow();
    expect(counted(path)).toEqual({ titles: [], days: [] });
  });

  test("a player that cannot remember counts nothing and does not throw", () => {
    expect(() => new WatchState(null).setProgress("p1", "01FILM", 100, 7200)).not.toThrow();
  });
});
