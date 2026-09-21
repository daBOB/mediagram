/**
 * Two machines, one channel.
 *
 * The whole point, proved without Telegram: two players with their own
 * databases exchange documents, merge, and agree. Everything a real sync adds
 * on top of this is transport.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates } from "../src/state/merge";
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
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

/** What actually crosses the channel: text, parsed as if it were a stranger's. */
const publish = (m: ReturnType<typeof machine>) =>
  parseRecord(JSON.stringify(m.state.exportRecord(m.device)))!;

/** One round of sync on `m`, given everything on the channel. */
const sync = (m: ReturnType<typeof machine>, channel: ReturnType<typeof publish>[]) =>
  m.state.importMerged(mergeStates([publish(m), ...channel]));

describe("a position set on one machine", () => {
  test("shows up on the other", () => {
    const laptop = machine("laptop");
    const desktop = machine("desktop");

    laptop.state.setProgress(laptop.me, "01FILM", 742, 1204);
    sync(desktop, [publish(laptop)]);

    expect(desktop.state.snapshot(desktop.me).progress[0]).toMatchObject({
      setId: "01FILM",
      at: 742,
    });
  });

  test("and carrying on there moves it back the other way", () => {
    const laptop = machine("laptop");
    const desktop = machine("desktop");

    laptop.state.setProgress(laptop.me, "01FILM", 742, 1204);
    sync(desktop, [publish(laptop)]);

    // A minute later, in the sense that matters. Both writes land in the same
    // millisecond otherwise, and the merge then breaks the tie on the device
    // id — correctly, and on the wrong intention. Real watching is minutes
    // apart; see `merge.ts` on clock skew for the case that is not.
    const later = publish(desktop);
    desktop.state.setProgress(desktop.me, "01FILM", 900, 1204);
    const carried = publish(desktop);
    carried.profiles[0]!.progress[0]!.updatedAt += 60_000;
    void later;
    sync(laptop, [carried]);

    expect(laptop.state.snapshot(laptop.me).progress[0]!.at).toBe(900);
  });
});

describe("two titles on two machines", () => {
  test("both survive, on both", () => {
    const laptop = machine("laptop");
    const phone = machine("phone");

    laptop.state.setProgress(laptop.me, "01E9", 700, 1204);
    phone.state.setProgress(phone.me, "01E4", 300, 1204);

    const channel = [publish(laptop), publish(phone)];
    sync(laptop, channel);
    sync(phone, channel);

    const seen = (m: ReturnType<typeof machine>) =>
      Object.fromEntries(m.state.snapshot(m.me).progress.map((r) => [r.setId, r.at]));
    expect(seen(laptop)).toEqual({ "01E4": 300, "01E9": 700 });
    expect(seen(phone)).toEqual(seen(laptop));
  });
});

describe("finishing on one machine", () => {
  test("takes it off the other's Continue shelf", () => {
    const laptop = machine("laptop");
    const desktop = machine("desktop");

    laptop.state.setProgress(laptop.me, "01FILM", 900, 1204);
    sync(desktop, [publish(laptop)]);
    expect(desktop.state.snapshot(desktop.me).progress).toHaveLength(1);

    // What the player does at the end of a title: both, together.
    laptop.state.clearProgress(laptop.me, "01FILM");
    laptop.state.setWatched(laptop.me, "01FILM", true);
    sync(desktop, [publish(laptop)]);

    expect(desktop.state.snapshot(desktop.me).progress).toEqual([]);
    expect(desktop.state.snapshot(desktop.me).watched.map((row) => row.setId)).toEqual(["01FILM"]);
  });
});

describe("syncing twice", () => {
  test("is syncing once", () => {
    const laptop = machine("laptop");
    const desktop = machine("desktop");
    laptop.state.setProgress(laptop.me, "01FILM", 742, 1204);

    expect(sync(desktop, [publish(laptop)])).toBeGreaterThan(0);
    expect(sync(desktop, [publish(laptop)])).toBe(0);
  });

  test("and a machine that hears nothing keeps what it had", () => {
    // A channel that cannot be reached must cost nothing.
    const laptop = machine("laptop");
    laptop.state.setProgress(laptop.me, "01FILM", 742, 1204);

    expect(sync(laptop, [])).toBe(0);
    expect(laptop.state.snapshot(laptop.me).progress[0]!.at).toBe(742);
  });
});

describe("a machine joining late", () => {
  test("gets the history it never had, profile and all", () => {
    const laptop = machine("laptop");
    laptop.state.setProgress(laptop.me, "01A", 100, 1204);
    laptop.state.setWatched(laptop.me, "01B", true);

    // A fresh install: no profiles, nothing.
    const dir = mkdtempSync(join(tmpdir(), "mediagram-new-"));
    dirs.push(dir);
    const fresh = new WatchState(join(dir, "state.db"));
    expect(fresh.profiles()).toEqual([]);

    fresh.importMerged(mergeStates([publish(laptop)]));

    const them = fresh.profiles()[0]!;
    expect(them.name).toBe("André");
    expect(fresh.snapshot(them.id).progress[0]!.setId).toBe("01A");
    expect(fresh.snapshot(them.id).watched.map((row) => row.setId)).toEqual(["01B"]);
  });
});
