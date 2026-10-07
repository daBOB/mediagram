/**
 * The Stats answer's `achievements`, through the router that serves it: the
 * kids flag read from the store, the library read from the catalog, the
 * rules from `achievements.ts`, the offset from this server's clock.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import type { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { ByteSource } from "../src/http/stream";
import { createRouter } from "../src/routes";
import type { Achievements } from "../src/state/achievements";
import type { MergedProfile } from "../src/state/merged";
import { utcOffsetMinutes } from "../src/state/stats-recorder";
import { WatchState } from "../src/state/store";
import { emptyIndex } from "./index-fixture";

const NO_BYTES: ByteSource = { stream: () => new ReadableStream({ start: (c) => c.close() }) };
const SHOWS = `CREATE TABLE shows(
    source TEXT NOT NULL, kind TEXT NOT NULL, id INTEGER NOT NULL,
    lang TEXT NOT NULL DEFAULT '',
    overview TEXT, tagline TEXT, genres TEXT, rating REAL,
    network TEXT, status TEXT, first_air TEXT, last_air TEXT,
    total_seasons INTEGER, total_episodes INTEGER,
    PRIMARY KEY(source, kind, id))`;
const FILM = "01SET0000000000000000001";
const FINISHED = Date.parse("2026-09-02T20:00:00Z");

/** One playable film the provider calls Crime and Drama. */
function catalog(): Database {
  const db = emptyIndex();
  db.run(SHOWS);
  db.run(
    `INSERT INTO sets(set_id, kind, title, tmdb, container, total, part_count, status, created_at, spec_version)
     VALUES (?1, 'movie', 'Heat', 949, 'mkv', 0, 0, 'complete', 1, 3)`,
    [FILM],
  );
  db.run("INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'movie', 949, 'Crime, Drama')");
  return db;
}

/** A viewer as another device's document brings them: the film finished, eight days of two hours on the television. */
function viewer(displayName: string, kids: boolean): MergedProfile {
  return {
    name: displayName.toLowerCase(),
    displayName,
    ...(kids ? { kids: true as const } : {}),
    progress: [],
    watched: [{ setId: FILM, updatedAt: FINISHED }],
    dayStats: Array.from({ length: 8 }, (_, i) => ({ day: `2026-09-0${i + 1}`, device: "tv-1", seconds: 7200, updatedAt: FINISHED })),
  };
}

let dir: string;
let state: WatchState;
beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), "mediagram-achievements-route-"));
  state = new WatchState(join(dir, "state.db"));
});
afterEach(() => {
  state.close();
  rmSync(dir, { recursive: true, force: true });
});

async function achievementsOf(name: string): Promise<Achievements> {
  const id = state.profiles().find((profile) => profile.name === name)!.id;
  const route = createRouter({ db: catalog(), source: NO_BYTES, state });
  const response = await route({ method: "GET", path: `/api/profiles/${id}/stats`, range: null });
  expect(response.status).toBe(200);
  return JSON.parse(await new Response(response.body).text()).achievements;
}

const idsOf = ({ earned, next }: Achievements) => [...earned, ...next].map((achievement) => achievement.id);

test("a kids profile is offered no hours, streak or binge, whatever its rows hold", async () => {
  state.importMerged({ profiles: [viewer("Kid", true)] });
  const ids = idsOf(await achievementsOf("Kid"));
  expect(ids).toContain("films-1");
  expect(ids.filter((id) => /^(hours|streak|binge)-/.test(id))).toEqual([]);
});

test("a grown-up's answer earns from the same rows, the film counting its catalog genres", async () => {
  state.importMerged({ profiles: [viewer("Grown", false)] });
  const achievements = await achievementsOf("Grown");
  expect(achievements.earned.map((achievement) => achievement.id)).toEqual(
    expect.arrayContaining(["films-1", "hours-10", "streak-7"]),
  );
  expect(achievements.next).toContainEqual({ id: "genres-5", have: 2, need: 5 });
});

test("the offset is this server's at the moment asked, across both clock changes", () => {
  const before = process.env.TZ;
  try {
    process.env.TZ = "Europe/Berlin";
    expect(utcOffsetMinutes(Date.parse("2026-03-29T00:59:00Z"))).toBe(60);
    expect(utcOffsetMinutes(Date.parse("2026-03-29T01:00:00Z"))).toBe(120);
    expect(utcOffsetMinutes(Date.parse("2026-10-25T00:59:00Z"))).toBe(120);
    expect(utcOffsetMinutes(Date.parse("2026-10-25T01:00:00Z"))).toBe(60);
    process.env.TZ = "UTC";
    expect(utcOffsetMinutes(0)).toBe(0);
  } finally {
    // Bun reads TZ on every date call; leaving it set would move every later test's clock.
    if (before === undefined) delete process.env.TZ;
    else process.env.TZ = before;
  }
});
