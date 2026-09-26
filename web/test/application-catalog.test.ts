import { afterEach, beforeEach, expect, spyOn, test } from "bun:test";
import type { Database } from "bun:sqlite";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { CatalogFollower } from "../src/application/catalog-follow";
import { CatalogEvents } from "../src/catalog-events";
import { HeldSets, expectedChunks } from "../src/cache/held";
import { CACHE_CHUNK } from "../src/cache/key";
import { EXPECTED_SCHEMA } from "../src/catalog";
import { startServer, type RunningServer } from "../src/server";
import type { CatalogFacts } from "../src/status/facts";
import { library, snapshot } from "./application-fixture";

let root: string;
let server: RunningServer | undefined;
let follower: CatalogFollower | undefined;
const databases: Database[] = [];
const restoreDiagnostics: Array<() => void> = [];
const events = new CatalogEvents();
beforeEach(async () => { root = await mkdtemp(join(tmpdir(), "application-catalog-")); });
afterEach(async () => {
  for (const restore of restoreDiagnostics.splice(0)) restore();
  await follower?.stopFollowing();
  events.close();
  await server?.close();
  follower?.close();
  for (const db of databases.splice(0)) db.close();
  follower = undefined;
  server = undefined;
  await rm(root, { recursive: true, force: true });
});

function facts(): { catalog: CatalogFacts } {
  return { catalog: { origin: "local", publishedAt: null, refresh: null, reason: null, schema: EXPECTED_SCHEMA, sets: 1, posters: 0 } };
}

async function titles(): Promise<string[]> {
  const response = await fetch(`http://127.0.0.1:${server!.port}/api/sets`);
  return ((await response.json()) as Array<{ title: string }>).map((set) => set.title);
}

test.each([
  ["Error", new Error("router build refused"), "router build refused"],
  ["null", null, "null"],
  ["undefined", undefined, "undefined"],
  ["string", "router build refused", "router build refused"],
  ["unprintable value", Object.create(null) as unknown, "unprintable rejection"],
])("%s router rejections keep the served catalog and retry the installed timestamp", async (_, rejection, message) => {
  const errors = spyOn(console, "error").mockImplementation(() => {});
  restoreDiagnostics.push(() => errors.mockRestore());
  const before = library("Before", "01OLD");
  databases.push(before);
  server = await startServer({ db: before, source: { stream: () => new ReadableStream<Uint8Array>() }, events });
  const held = new HeldSets(join(root, "cache"), expectedChunks(before));
  const cached = join(root, "cache", String(CACHE_CHUNK), "01NEW", "0");
  await mkdir(cached, { recursive: true });
  await writeFile(join(cached, "0"), "cached bytes");
  const changes: number[] = [];
  const replacements: Map<string, number>[] = [];
  const retirements: Array<{ run: () => void; milliseconds: number; cancelled: boolean }> = [];
  const attempts: Database[] = [];
  const status = facts();
  let downloads = 0;
  const found = snapshot("After", 200, "01NEW");
  follower = new CatalogFollower({
    db: before, catalog: { dir: null, origin: "local", publishedAt: null, refresh: null, reason: null },
    root: join(root, "channel"), find: async () => ({ ...found, chunks: async function* () { downloads++; yield* found.chunks(); } }),
    server: { replaceCatalog(next) {
      attempts.push(next.db);
      databases.push(next.db);
      if (attempts.length === 1) throw rejection;
      server!.replaceCatalog(next);
    } },
    facts: status,
    held: { replaceExpected: async (expected) => { replacements.push(expected); await held.replaceExpected(expected); } },
    events: { catalogChanged(at) { changes.push(at!); events.catalogChanged(at); } },
    fetchPosters: async () => ({ ok: false, reason: "art unavailable" }), posterCount: () => 0,
    schedule(run, milliseconds) {
      const timer = { run, milliseconds, cancelled: false }; retirements.push(timer);
      return () => { timer.cancelled = true; };
    },
  });

  await follower.refresh();
  expect(await titles()).toEqual(["Before"]);
  expect(status.catalog).toMatchObject({ origin: "local", publishedAt: null });
  expect(replacements).toEqual([]);
  expect(changes).toEqual([]);
  expect(await held.check("01NEW")).toBe(false);
  expect(errors.mock.calls).toEqual([[`catalog: the installed channel index could not be served: ${message}`]]);

  const stream = events.subscribe().getReader();
  await stream.read();
  await follower.refresh();
  expect(await titles()).toEqual(["After"]);
  expect(downloads).toBe(1);
  expect(attempts).toHaveLength(2);
  expect(replacements.map((entry) => [...entry])).toEqual([[["01NEW", 1]]]);
  expect(await held.check("01NEW")).toBe(true);
  expect(status.catalog).toMatchObject({ origin: "channel", publishedAt: 200000, refresh: "updated", sets: 1 });
  expect(changes).toEqual([200000]);
  expect(new TextDecoder().decode((await stream.read()).value)).toContain('"publishedAt":200000');
  await stream.cancel();
  expect(retirements[0]?.milliseconds).toBe(300000);
  expect(before.query("SELECT count(*) AS n FROM sets").get()).toEqual({ n: 1 });
  expect(() => attempts[0]!.query("SELECT 1").get()).toThrow();
  await follower.stopFollowing();
  await server.close();
  follower.close();
  expect(retirements[0]?.cancelled).toBe(true);
  expect(() => before.query("SELECT 1").get()).toThrow();
  expect(() => attempts[1]!.query("SELECT 1").get()).toThrow();
});

test("a cache refresh failure does not hide a catalog already committed to the listener", async () => {
  const before = library("Before");
  databases.push(before);
  server = await startServer({ db: before, source: { stream: () => new ReadableStream<Uint8Array>() } });
  const status = facts();
  const changes: number[] = [];
  follower = new CatalogFollower({
    db: before, catalog: { dir: null, origin: "local", publishedAt: null, refresh: null, reason: null },
    root: join(root, "channel"), find: async () => snapshot("After", 200), server,
    facts: status, held: { replaceExpected: async () => { throw new Error("cache scan refused"); } },
    events: { catalogChanged(at) { changes.push(at!); } },
    fetchPosters: async () => ({ ok: false, reason: "art unavailable" }), posterCount: () => 0,
  });
  await follower.refresh();
  expect(await titles()).toEqual(["After"]);
  expect(status.catalog).toMatchObject({ origin: "channel", publishedAt: 200000 });
  expect(changes).toEqual([200000]);
});

test("shutdown drains an artwork process already started and admits no more", async () => {
  const db = library("Held");
  databases.push(db);
  let complete!: (value: { ok: true; summary: string }) => void;
  const artwork = new Promise<{ ok: true; summary: string }>((resolve) => { complete = resolve; });
  let starts = 0;
  follower = new CatalogFollower({
    db, catalog: { dir: root, origin: "channel", publishedAt: 100000, refresh: "unchanged", reason: null },
    root, find: async () => { throw new Error("no refresh expected"); },
    server: { replaceCatalog() { throw new Error("no swap expected"); } }, facts: facts(),
    events, fetchPosters: async () => { starts++; return artwork; }, posterCount: () => 0,
  });
  const fetchingPosters = follower.refreshPosters(root);
  let finished = false;
  const stopped = follower.stopFollowing().then(() => { finished = true; });
  try {
    await new Promise<void>((resolve) => setImmediate(resolve));
    expect(finished).toBe(false);
  } finally {
    complete({ ok: true, summary: "art fetched" });
    await Promise.all([fetchingPosters, stopped]);
  }
  await follower.refreshPosters(root);
  expect(starts).toBe(1);
});

test("retarget forgets what was served, so a different channel's index is not compared against it", async () => {
  const before = library("Before");
  databases.push(before);
  server = await startServer({ db: before, source: { stream: () => new ReadableStream<Uint8Array>() } });
  const status = facts();
  follower = new CatalogFollower({
    db: before, catalog: { dir: null, origin: "channel", publishedAt: 999_000, refresh: "updated", reason: null },
    root: join(root, "channel-a"), find: async () => snapshot("From B", 5, "01FROMB"), server,
    facts: status, events: { catalogChanged: () => {} },
    fetchPosters: async () => ({ ok: false, reason: "art unavailable" }), posterCount: () => 0,
  });
  // A lower pushedAt than what this follower already believes it is serving
  // (999) would be refused as "unchanged" without retarget resetting that.
  follower.retarget(join(root, "channel-b"));
  await follower.refresh();
  expect(await titles()).toEqual(["From B"]);
  expect(status.catalog).toMatchObject({ publishedAt: 5000, refresh: "updated" });
});
