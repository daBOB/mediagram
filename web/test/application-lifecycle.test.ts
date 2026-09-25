import { afterEach, beforeEach, expect, test } from "bun:test";
import { EventEmitter } from "node:events";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { LibraryUpdates, announcingPulls, installShutdownSignals, shutdownFor, type ApplicationResources } from "../src/application/lifecycle";
import { StateSync } from "../src/state/sync";
import { WatchState } from "../src/state/store";
import { TranscodeRegistry } from "../src/transcode/registry";
import { CatalogEvents } from "../src/catalog-events";
import { startServer } from "../src/server";
import type { LibraryEvent } from "../src/telegram/updates";
import { deferred, library } from "./application-fixture";

let root: string;
beforeEach(async () => { root = await mkdtemp(join(tmpdir(), "application-lifecycle-")); });
afterEach(async () => { await rm(root, { recursive: true, force: true }); });

test("subscription precedes initial sync and attaching a follower always requests catch-up", async () => {
  const order: string[] = [];
  let event!: (kind: LibraryEvent) => void;
  const updates = new LibraryUpdates((callback) => {
    event = callback; order.push("subscribe"); return () => { order.push("unsubscribe"); };
  }, { once: async () => { order.push("sync"); return { pulled: 0, pushed: false }; } });
  await updates.start();
  let followed = 0;
  await updates.followCatalog({ refresh: async () => { followed++; } });
  expect(order).toEqual(["subscribe", "sync"]);
  expect(followed).toBe(1);
  event("index");
  expect(followed).toBe(2);
  updates.stop();
  event("index");
  event("state");
  expect(followed).toBe(2);
  expect(order).toEqual(["subscribe", "sync", "unsubscribe"]);
});

test("a package catalog never follows channel index events", async () => {
  let event!: (kind: LibraryEvent) => void;
  let syncs = 0;
  const updates = new LibraryUpdates((callback) => { event = callback; return () => {}; }, {
    once: async () => { syncs++; return { pulled: 0, pushed: false }; },
  });
  await updates.start();
  event("index");
  await updates.followCatalog(null);
  event("index");
  expect(syncs).toBe(1);
  event("state");
  expect(syncs).toBe(2);
  updates.stop();
});

test("shutdown publishes final state, drains conversions, then closes the listener and connection", async () => {
  const order: string[] = [];
  const put = deferred<void>();
  const finishSync = deferred<void>();
  const processStopping = deferred<void>();
  const finishProcess = deferred<void>();
  let publishedPosition: number | undefined;
  const db = library("Held");
  const state = new WatchState(join(root, "state.db"));
  const viewer = state.createProfile("Viewer")!;
  state.setProgress(viewer.id, "01SET", 12, 100);
  const events = new CatalogEvents();
  const stream = events.subscribe().getReader();
  await stream.read();
  const server = await startServer({ db, events, source: { stream: () => new ReadableStream<Uint8Array>() } });
  const transcodes = new TranscodeRegistry(join(root, "transcode"), {
    start() { return { stop: async () => { order.push("process-stop"); processStopping.resolve(); await finishProcess.promise; } }; },
  });
  const spec = { setId: "01SET", seekSeconds: 0, maxrateBits: 1, audioTrack: 0, copyVideo: false };
  const starting = transcodes.acquireSession(spec);
  const sync = new StateSync(state, {
    list: async () => { order.push("sync-list"); return []; },
    put: async (body) => {
      order.push("sync-put");
      publishedPosition = JSON.parse(body).profiles[0]?.progress[0]?.at;
      put.resolve(); await finishSync.promise; return 1;
    },
  }, state.deviceId());
  const updates = new LibraryUpdates(() => () => { order.push("unsubscribe"); }, null);
  await updates.start();
  const resources: ApplicationResources = {
    timers: [setInterval(() => {}, 60000)], updates, sync, transcodes,
    events: { close() { order.push("events-close"); events.close(); } },
    server: { close: async () => { order.push("server-close"); await server.close(); } },
    state: { close() { order.push("state-close"); state.close(); } },
    telegram: { disconnect: async () => { order.push("disconnect"); } },
    catalog: { close() { order.push("db-close"); db.close(); } },
  };
  const stop = shutdownFor(resources);
  const stopped = stop();
  try {
    expect(stop()).toBe(stopped);
    await put.promise;
    expect(publishedPosition).toBe(12);
    expect(order.slice(0, 4)).toEqual(["unsubscribe", "events-close", "sync-list", "sync-put"]);
    expect(resources.timers).toEqual([]);
    expect((await stream.read()).done).toBe(true);
    finishSync.resolve();
    await processStopping.promise;
    await expect(transcodes.acquireSession(spec)).rejects.toThrow(/shut/);
    expect((await fetch(`http://127.0.0.1:${server.port}/api/sets`)).status).toBe(200);
  } finally {
    finishSync.resolve(); finishProcess.resolve();
    await stopped;
    await starting;
  }
  expect(order).toEqual(["unsubscribe", "events-close", "sync-list", "sync-put", "process-stop", "server-close", "state-close", "disconnect", "db-close"]);
  expect(transcodes.count()).toBe(0);
  expect(() => db.query("SELECT 1").get()).toThrow();
  await expect(fetch(`http://127.0.0.1:${server.port}/api/sets`)).rejects.toThrow();
});

test("one shutdown failure cannot skip later resource cleanup", async () => {
  const order: string[] = [];
  const stop = shutdownFor({
    timers: [],
    sync: { once: async () => { order.push("sync"); throw new Error("sync failed"); } },
    transcodes: { stopAll: async () => { order.push("transcodes"); throw new Error("stop failed"); } },
    server: { close: async () => { order.push("server"); } },
    state: { close: () => { order.push("state"); } },
    telegram: { disconnect: async () => { order.push("telegram"); } },
    catalog: { close: () => { order.push("catalog"); } },
  });
  await expect(stop()).rejects.toThrow();
  expect(order).toEqual(["sync", "transcodes", "server", "state", "telegram", "catalog"]);
});

test("repeated process signals share one shutdown and remove both handlers before exiting", async () => {
  const emitter = new EventEmitter();
  const done = deferred<void>();
  const exited = deferred<number>();
  let stops = 0;
  const target = Object.assign(emitter, { exit: (code: number) => { exited.resolve(code); } });
  const dispose = installShutdownSignals(async () => { stops++; await done.promise; }, target);
  try {
    emitter.emit("SIGINT"); emitter.emit("SIGTERM");
    expect(stops).toBe(1);
    done.resolve();
    expect(await exited.promise).toBe(0);
    expect(emitter.listenerCount("SIGINT") + emitter.listenerCount("SIGTERM")).toBe(0);
  } finally { done.resolve(); dispose(); }
});

test("only a sync round that took something tells open pages", async () => {
  const outcomes = [{ pulled: 0, pushed: true }, { pulled: 2, pushed: false }, { pulled: 0, pushed: false, failed: "offline" }];
  let told = 0;
  const sync = announcingPulls({ once: async () => outcomes.shift()! }, { stateChanged: () => { told++; } })!;
  expect(await sync.once()).toEqual({ pulled: 0, pushed: true });
  expect(told).toBe(0);
  expect(await sync.once()).toEqual({ pulled: 2, pushed: false });
  expect(told).toBe(1);
  await sync.once();
  expect(told).toBe(1);
  expect(announcingPulls(null, { stateChanged: () => { told++; } })).toBeNull();
});
