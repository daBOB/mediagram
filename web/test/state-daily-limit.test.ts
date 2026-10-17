/**
 * A kid's daily watching limit on the server: who may set it, how it syncs,
 * and the watched-today count the page is told.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { Profile } from "../src/state/profiles";
import { leftToday } from "../src/state/profiles-routes";
import { localDay } from "../src/state/stats-recorder";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

function store(): { state: WatchState; path: string } {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-daily-limit-"));
  dirs.push(dir);
  const path = join(dir, "state.db");
  return { state: new WatchState(path), path };
}

/** A household: an admin with PIN 1111 and a kid of theirs. */
function household(state: WatchState) {
  const admin = state.manage().createFirst("André", "1111") as Profile;
  const kid = state.manage().createKid(admin.id, "1111", "Mia", 6) as Profile;
  return { admin, kid };
}

const limitOf = (state: WatchState, id: string) => state.profiles().find((one) => one.id === id)!.dailyLimit;

describe("setting a daily limit", () => {
  test("a kid starts with none; its grown-up sets and clears it", () => {
    const { state } = store();
    const { admin, kid } = household(state);
    expect(kid.dailyLimit).toBeNull();
    expect(state.manage().setDailyLimit(admin.id, "1111", kid.id, 45)).toBeNull();
    expect(limitOf(state, kid.id)).toBe(45);
    expect(state.manage().setDailyLimit(admin.id, "1111", kid.id, null)).toBeNull();
    expect(limitOf(state, kid.id)).toBeNull();
  });

  test("a kid made with a limit has it, and it is said on the sync record", () => {
    const { state } = store();
    const admin = state.manage().createFirst("André", "1111") as Profile;
    const kid = state.manage().createKid(admin.id, "1111", "Leo", 12, 30) as Profile;
    expect(kid.dailyLimit).toBe(30);
    expect(state.exportRecord("laptop").profiles.find((one) => one.name === "Leo")!.dailyLimit)
      .toEqual({ minutes: 30, updatedAt: expect.any(Number) });
  });

  test("only whole minutes from 1 to 1440, or none", () => {
    const { state } = store();
    const { admin, kid } = household(state);
    for (const bad of [0, 1441, 30.5, "60", undefined, -5]) {
      expect(state.manage().setDailyLimit(admin.id, "1111", kid.id, bad)).toEqual({ reason: "invalid" });
    }
    expect(state.manage().createKid(admin.id, "1111", "Zed", 6, 0)).toEqual({ reason: "invalid" });
  });

  test("the PIN and the age limit's rule apply: a wrong PIN, a kid acting, another grown-up's kid", () => {
    const { state } = store();
    const { admin, kid } = household(state);
    const other = state.manage().createGrownUp(admin.id, "1111", "Maja", "2222") as Profile;
    expect(state.manage().setDailyLimit(admin.id, "9999", kid.id, 30)).toEqual({ reason: "wrong-pin" });
    expect(state.manage().setDailyLimit(kid.id, "1111", kid.id, 30)).toEqual({ reason: "not-allowed" });
    expect(state.manage().setDailyLimit(other.id, "2222", kid.id, 30)).toEqual({ reason: "not-allowed" });
    expect(state.manage().setDailyLimit(admin.id, "1111", admin.id, 30)).toEqual({ reason: "not-allowed" });
    expect(limitOf(state, kid.id)).toBeNull();
  });
});

describe("a daily limit between devices", () => {
  test("a newer 'no limit' beats an older limit when merged in, and a stale limit does not undo it", () => {
    const { state } = store();
    const { admin, kid } = household(state);
    state.manage().setDailyLimit(admin.id, "1111", kid.id, 60);
    const at = state.exportRecord("laptop").profiles.find((one) => one.name === "Mia")!.dailyLimit!.updatedAt;
    const merged = (minutes: number | null, updatedAt: number) =>
      ({ profiles: [{ name: "mia", displayName: "Mia", kids: true as const, dailyLimit: { minutes, updatedAt }, progress: [], watched: [] }] });
    state.importMerged(merged(null, at + 1000));
    expect(limitOf(state, kid.id)).toBeNull();
    state.importMerged(merged(90, at + 500));
    expect(limitOf(state, kid.id)).toBeNull();
  });
});

describe("watched today", () => {
  test("sums every device's seconds for today only, and a limited kid is told", () => {
    const { state, path } = store();
    const { admin, kid } = household(state);
    const raw = new Database(path);
    const row = raw.query("INSERT INTO stats_days(profile_id, day, device, seconds, updated_at) VALUES (?1, ?2, ?3, ?4, 1)");
    row.run(kid.id, localDay(Date.now()), "laptop", 600);
    row.run(kid.id, localDay(Date.now()), "tv", 300.5);
    row.run(kid.id, "2020-01-01", "tv", 9000);
    raw.close();

    expect(state.today(kid.id)).toEqual({ limitMinutes: null, watchedSeconds: 900.5 });
    expect(leftToday(state, kid.id).status).toBe(204);
    // No limit: the progress write does not sum, the plain read still does.
    expect(state.today(kid.id, true)).toEqual({ limitMinutes: null, watchedSeconds: 0 });
    expect(leftToday(state, admin.id).status).toBe(204);
    state.manage().setDailyLimit(admin.id, "1111", kid.id, 30);
    const answer = leftToday(state, kid.id);
    expect(answer.status).toBe(200);
    expect(JSON.parse(new TextDecoder().decode(answer.body as Uint8Array))).toEqual({ limitMinutes: 30, watchedSeconds: 900.5 });
    expect(state.today("nobody")).toBeNull();
    expect(state.today(admin.id)).toEqual({ limitMinutes: null, watchedSeconds: 0 });
  });
});
