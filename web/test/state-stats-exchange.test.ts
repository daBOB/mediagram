/**
 * Viewing stats between machines, and the summary read from them: exported
 * whole, taken in only when newer, never counted twice, never deleted.
 */

import { afterEach, describe, expect, setSystemTime, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates } from "../src/state/merge";
import type { MergedState } from "../src/state/merged";
import type { DayStatRow, TitleStatRow } from "../src/state/stats-record";
import { parseRecord } from "../src/state/sync-record";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
function machine(device: string) {
  const dir = mkdtempSync(join(tmpdir(), `mediagram-${device}-`));
  dirs.push(dir);
  const state = new WatchState(join(dir, "state.db"));
  const me = state.createProfile("André")!.id;
  return { state, me, device };
}
afterEach(() => {
  setSystemTime();
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

const T0 = new Date(2026, 9, 3, 21, 0, 0).getTime();
const at = (seconds: number) => setSystemTime(new Date(T0 + seconds * 1000));

/** What crosses the channel: text, parsed as a stranger's. */
const publish = (m: ReturnType<typeof machine>) => parseRecord(JSON.stringify(m.state.exportRecord(m.device)))!;
const sync = (m: ReturnType<typeof machine>, channel: ReturnType<typeof publish>[]) =>
  m.state.importMerged(mergeStates([publish(m), ...channel]));

/** Twenty seconds of `setId` watched on `m`, from `from` seconds past T0. */
function watch(m: ReturnType<typeof machine>, setId: string, from: number) {
  for (const step of [0, 10, 20]) {
    at(from + step);
    m.state.setProgress(m.me, setId, 100 + step, 7200);
  }
}

/** A hand-built merge for André that carries only stats rows. */
const merged = (titleStats: TitleStatRow[], dayStats: DayStatRow[] = []): MergedState =>
  ({ profiles: [{ name: "andré", displayName: "André", progress: [], watched: [], titleStats, dayStats }] });

const row = (seconds: number, updatedAt: number, device = "phone"): TitleStatRow =>
  ({ setId: "01FILM", device, startedAt: 1000, lastWatchedAt: updatedAt, seconds, updatedAt });
const day = (seconds: number, updatedAt: number): DayStatRow =>
  ({ day: "2026-10-03", device: "phone", seconds, updatedAt });

describe("the export", () => {
  test("a profile with no stats says exactly what it said before stats existed", () => {
    const laptop = machine("laptop");
    laptop.state.setPreference(laptop.me, "profile", "subtitle", "de");
    const text = JSON.stringify(laptop.state.exportRecord("laptop"));
    expect(text).not.toContain("titleStats");
    expect(text).not.toContain("dayStats");
  });

  test("a title never restarted crosses the wire without an againAt, and is kept", () => {
    const laptop = machine("laptop");
    watch(laptop, "01FILM", 0);
    expect(publish(laptop).profiles[0]!.titleStats).toEqual([{
      setId: "01FILM", device: laptop.state.deviceId(), startedAt: T0, lastWatchedAt: T0 + 20_000, seconds: 20, updatedAt: T0 + 20_000,
    }]);
  });
});

describe("taking rows in", () => {
  test("a newer row replaces the one held; an older or equal one changes nothing", () => {
    const { state, me } = machine("laptop");
    expect(state.importMerged(merged([row(600, 2000)], [day(600, 2000)]))).toBe(2);
    expect(state.importMerged(merged([row(300, 1000)], [day(300, 1000)]))).toBe(0);
    expect(state.importMerged(merged([row(900, 2000)]))).toBe(0);
    expect(state.stats(me).history[0]!.seconds).toBe(600);

    expect(state.importMerged(merged([row(900, 3000)]))).toBe(1);
    expect(state.stats(me).history[0]!.seconds).toBe(900);
  });

  test("a merge that says nothing about stats deletes nothing", () => {
    const laptop = machine("laptop");
    watch(laptop, "01FILM", 0);
    laptop.state.importMerged({ profiles: [{ name: "andré", displayName: "André", progress: [], watched: [] }] });
    expect(laptop.state.stats(laptop.me).allSeconds).toBe(20);
  });

  test("this device's own row comes back when newer — a reinstall that kept its id", () => {
    const { state, me } = machine("laptop");
    expect(state.importMerged(merged([row(1200, 5000, state.deviceId())]))).toBe(1);
    expect(state.stats(me).history).toEqual([{ kind: "started", setId: "01FILM", at: 1000, seconds: 1200 }]);
  });
});

describe("two machines", () => {
  test("minutes counted on one show up on the other, and later rounds add nothing", () => {
    const laptop = machine("laptop");
    const desktop = machine("desktop");
    watch(laptop, "01FILM", 0);

    sync(desktop, [publish(laptop)]);
    expect(desktop.state.stats(desktop.me).allSeconds).toBe(20);
    // The laptop's rows now travel in the desktop's document too.
    sync(desktop, [publish(laptop)]);
    sync(laptop, [publish(desktop)]);
    expect(desktop.state.stats(desktop.me).allSeconds).toBe(20);
    expect(laptop.state.stats(laptop.me).allSeconds).toBe(20);

    watch(desktop, "01FILM", 100);
    sync(laptop, [publish(desktop)]);
    expect(laptop.state.stats(laptop.me)).toMatchObject({
      allSeconds: 40,
      history: [{ kind: "started", setId: "01FILM", at: T0, seconds: 40 }],
    });
  });

  test("a device that went away keeps its minutes, passed on by one that heard them", () => {
    const laptop = machine("laptop");
    const desktop = machine("desktop");
    const phone = machine("phone");
    watch(laptop, "01FILM", 0);
    sync(desktop, [publish(laptop)]);

    sync(phone, [publish(desktop)]); // the laptop's own document is gone
    expect(phone.state.stats(phone.me).allSeconds).toBe(20);
  });
});

describe("the summary", () => {
  test("totals and bars as of today, and a restart as its own history line", () => {
    const { state, me } = machine("laptop");
    at(0); state.setProgress(me, "01FILM", 100, 7200);
    at(10); state.setProgress(me, "01FILM", 110, 7200);
    at(20); state.setWatched(me, "01FILM", true);
    at(60); state.setProgress(me, "01FILM", 0, 7200);
    at(70); state.setProgress(me, "01FILM", 10, 7200);

    const summary = state.stats(me);
    expect(summary).toMatchObject({ weekSeconds: 20, monthSeconds: 20, allSeconds: 20 });
    expect(summary.last30.at(-1)).toEqual({ day: "2026-10-03", seconds: 20 });
    expect(summary.history).toEqual([
      { kind: "again", setId: "01FILM", at: T0 + 60_000, seconds: 20 },
      { kind: "finished", setId: "01FILM", at: T0 + 20_000, seconds: 20 },
      { kind: "started", setId: "01FILM", at: T0, seconds: 20 },
    ]);
  });

  test("a title un-marked as finished is not finished history", () => {
    const { state, me } = machine("laptop");
    state.setWatched(me, "01DONE", true);
    state.setWatched(me, "01DONE", false);
    expect(state.stats(me).history).toEqual([]);
  });

  test("a player that cannot remember answers an empty summary", () => {
    const summary = new WatchState(null).stats("p1");
    expect(summary).toMatchObject({ weekSeconds: 0, monthSeconds: 0, allSeconds: 0, history: [] });
    expect(summary.last30).toHaveLength(30);
  });
});
