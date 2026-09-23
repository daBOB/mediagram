import { describe, expect, test } from "bun:test";

import { DownloadGate } from "../src/telegram/download-gate";

/** A task that runs until `release` is called, and records that it started. */
function held(started: number[], id: number) {
  let release!: () => void;
  const done = new Promise<void>((resolve) => (release = resolve));
  return {
    task: async () => {
      started.push(id);
      await done;
      return id;
    },
    release: () => release(),
  };
}

const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

describe("DownloadGate", () => {
  test("holds downloads past the limit until one finishes", async () => {
    const gate = new DownloadGate(2);
    const started: number[] = [];
    const tasks = [0, 1, 2, 3].map((id) => held(started, id));
    const results = tasks.map((t) => gate.run(t.task));

    await tick();
    expect(started).toEqual([0, 1]);
    expect(gate.inFlight()).toBe(2);

    tasks[0]!.release();
    await tick();
    expect(started).toEqual([0, 1, 2]);

    for (const t of tasks) t.release();
    expect(await Promise.all(results)).toEqual([0, 1, 2, 3]);
    expect(gate.inFlight()).toBe(0);
  });

  test("a download that fails still frees its slot", async () => {
    const gate = new DownloadGate(1);
    const failed = gate.run(async () => {
      throw new Error("FLOOD_WAIT");
    });
    const next = gate.run(async () => "after");

    await expect(failed).rejects.toThrow("FLOOD_WAIT");
    expect(await next).toBe("after");
    expect(gate.inFlight()).toBe(0);
  });

  test("the queue is served in the order it formed", async () => {
    const gate = new DownloadGate(1);
    const started: number[] = [];
    const first = held(started, 0);
    const running = gate.run(first.task);
    const queued = [1, 2, 3].map((id) => gate.run(async () => void started.push(id)));

    await tick();
    first.release();
    await Promise.all([running, ...queued]);
    expect(started).toEqual([0, 1, 2, 3]);
  });
});
