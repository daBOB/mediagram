/** Bursts share a drain, including news that arrived after its current read. */

import { afterEach, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { StateSync, type ChannelDocument, type StateChannel } from "../src/state/sync";
import { WatchState } from "../src/state/store";

const machines: { state: WatchState; dir: string }[] = [];
afterEach(() => {
  for (const { state, dir } of machines.splice(0)) {
    state.close();
    rmSync(dir, { recursive: true, force: true });
  }
});

function machine() {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-sync-burst-"));
  const state = new WatchState(join(dir, "state.db"));
  machines.push({ state, dir });
  return state;
}

function gate<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

test("a burst schedules one follow-up and every caller waits for the drain", async () => {
  const state = machine();
  const firstRead = gate<ChannelDocument[]>();
  const secondRead = gate<ChannelDocument[]>();
  const firstStarted = gate<void>();
  const secondStarted = gate<void>();
  let lists = 0;
  let puts = 0;
  const channel: StateChannel = {
    list: async () => {
      lists += 1;
      if (lists === 1) {
        firstStarted.resolve();
        return firstRead.promise;
      }
      if (lists === 2) {
        secondStarted.resolve();
        return secondRead.promise;
      }
      return [];
    },
    put: async () => { puts += 1; return 1; },
  };
  const sync = new StateSync(state, channel, "a");
  let settled = 0;
  const calls = [sync.once().then((outcome) => { settled += 1; return outcome; })];
  await firstStarted.promise;
  for (let index = 0; index < 100; index += 1) {
    calls.push(sync.once().then((outcome) => { settled += 1; return outcome; }));
  }
  firstRead.resolve([]);
  await secondStarted.promise;
  // Let already-settled caller continuations run before checking the barrier.
  await Promise.resolve();
  const settledBeforeDrain = settled;
  secondRead.resolve([]);
  const outcomes = await Promise.all(calls);

  expect(settledBeforeDrain).toBe(0);
  expect(lists).toBe(2);
  expect(puts).toBe(1);
  expect(outcomes).toEqual(Array.from({ length: 101 }, () => ({ pulled: 0, pushed: true })));
  expect(await sync.once()).toEqual({ pulled: 0, pushed: false });
  expect(lists).toBe(3);
});

test("news arriving during a follow-up read is retained in one more round", async () => {
  const state = machine();
  const reads = [gate<ChannelDocument[]>(), gate<ChannelDocument[]>()];
  const started = [gate<void>(), gate<void>()];
  let documents: ChannelDocument[] = [];
  let lists = 0;
  const sent: string[] = [];
  const channel: StateChannel = {
    list: async () => {
      const index = lists++;
      if (index < reads.length) {
        started[index]!.resolve();
        return reads[index]!.promise;
      }
      return documents;
    },
    put: async (body) => { sent.push(body); return 1; },
  };
  const sync = new StateSync(state, channel, "a");
  const first = sync.once();
  await started[0]!.promise;
  const second = sync.once();
  reads[0]!.resolve([]);
  await started[1]!.promise;
  documents = [{ messageId: 2, device: "z", text: JSON.stringify({
    format: 1, device: "z", writtenAt: 100,
    profiles: [{ name: "André", progress: [{ setId: "01A", at: 42, updatedAt: 100 }], watched: [] }],
  }) }];
  const burst = Array.from({ length: 50 }, () => sync.once());
  reads[1]!.resolve([]);

  const outcomes = await Promise.all([first, second, ...burst]);

  expect(lists).toBe(3);
  expect(outcomes.every((outcome) => outcome.pulled === 1 && outcome.pushed)).toBe(true);
  expect(state.snapshot(state.profiles()[0]!.id).progress[0]!.at).toBe(42);
  expect(JSON.parse(sent.at(-1)!).profiles[0].progress[0].at).toBe(42);
});

test("a failed round does not discard its queued follow-up or reject its callers", async () => {
  const state = machine();
  const release = gate<void>();
  const started = gate<void>();
  let lists = 0;
  const channel: StateChannel = {
    list: async () => {
      lists += 1;
      if (lists === 1) {
        started.resolve();
        await release.promise;
        throw new Error("temporary refusal");
      }
      return [];
    },
    put: async () => 1,
  };
  const sync = new StateSync(state, channel, "a");
  const first = sync.once();
  await started.promise;
  const second = sync.once();
  release.resolve();

  expect(await Promise.all([first, second])).toEqual([
    { pulled: 0, pushed: true, failed: "temporary refusal" },
    { pulled: 0, pushed: true, failed: "temporary refusal" },
  ]);
  expect(lists).toBe(2);
  expect(await sync.once()).toEqual({ pulled: 0, pushed: false });
});
