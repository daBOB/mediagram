/** Clip jobs over a fake process: progress, busy, failure copy, listing, expiry. */

import { afterEach, describe, expect, test } from "bun:test";
import { existsSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { ClipJobs, type ClipProcess, type ClipSpec } from "../src/transcode/clip-jobs";

const dirs: string[] = [];
afterEach(() => { for (const d of dirs.splice(0)) rmSync(d, { recursive: true, force: true }); });

/** A process the test drives by hand. */
function fakeProcess() {
  const enc = new TextEncoder();
  let out!: ReadableStreamDefaultController<Uint8Array>;
  let err!: ReadableStreamDefaultController<Uint8Array>;
  let exit!: (code: number) => void;
  const proc: ClipProcess = {
    stdout: new ReadableStream({ start: (c) => { out = c; } }),
    stderr: new ReadableStream({ start: (c) => { err = c; } }),
    exited: new Promise<number>((r) => { exit = r; }),
    kill: () => exit(143),
  };
  return {
    proc,
    progress: (us: number) => out.enqueue(enc.encode(`out_time_us=${us}\nprogress=continue\n`)),
    stderr: (text: string) => err.enqueue(enc.encode(text)),
    finish: (code: number, write?: string) => { if (write) writeFileSync(write, "mp4"); out.close(); err.close(); exit(code); },
  };
}

const spec: ClipSpec = { profileId: "p1", setId: "s1", title: "A Film", from: 3720, to: 3780, audioTrack: 0, hdr: null };
const tick = () => new Promise((r) => setTimeout(r, 5));

function harness(clock = { t: 0 }) {
  const directory = mkdtempSync(join(tmpdir(), "clips-"));
  dirs.push(directory);
  const spawned: { command: string[]; fake: ReturnType<typeof fakeProcess> }[] = [];
  /** Timers the jobs asked for, run by hand; `cancelled` once called off. */
  const timers: { run: () => void; ms: number; cancelled: boolean }[] = [];
  const jobs = new ClipJobs({
    baseUrl: "http://127.0.0.1:1", pacedReads: false, directory, now: () => clock.t,
    schedule: (run, ms) => {
      const timer = { run, ms, cancelled: false };
      timers.push(timer);
      return () => { timer.cancelled = true; };
    },
    spawn: (command) => {
      const fake = fakeProcess();
      spawned.push({ command, fake });
      return fake.proc;
    },
  });
  return { jobs, spawned, directory, clock, timers };
}

describe("ClipJobs", () => {
  test("a job runs, reports progress, and ends done with its file", async () => {
    const { jobs, spawned } = harness();
    const started = jobs.start(spec);
    if (started === "busy") throw new Error("busy");
    const { command, fake } = spawned[0]!;
    expect(command.slice(0, 7)).toEqual(["nice", "-n", "10", "ffmpeg", "-progress", "pipe:1", "-nostats"]);
    // Global options must precede the inputs; ffmpeg ignores them after the last output.
    expect(command.indexOf("-progress")).toBeLessThan(command.indexOf("-i"));
    expect(command).toContain("http://127.0.0.1:1/api/sets/s1/stream");

    fake.progress(30_000_000);
    await tick();
    expect(jobs.status(started.id)).toMatchObject({ state: "running", progress: 0.5, from: 3720, to: 3780 });
    expect(jobs.file(started.id)).toBeNull();

    fake.finish(0, command.at(-1));
    await tick();
    expect(jobs.status(started.id)).toMatchObject({ state: "done", progress: 1, name: "A Film – 1h02m00s.mp4" });
    expect(jobs.file(started.id)).toEqual({ path: command.at(-1)!, name: "A Film – 1h02m00s.mp4", profileId: "p1" });
  });

  test("a second start while one runs is busy", () => {
    const { jobs } = harness();
    expect(jobs.start(spec)).not.toBe("busy");
    expect(jobs.start(spec)).toBe("busy");
  });

  test("a failed DV job says why, any other failure is generic", async () => {
    const { jobs, spawned } = harness();
    const dv = jobs.start({ ...spec, hdr: "DV" }) as { id: string };
    spawned[0]!.fake.stderr("libplacebo broke\n");
    spawned[0]!.fake.finish(1);
    await tick();
    expect(jobs.status(dv.id)).toMatchObject({ state: "failed", error: "This title's Dolby Vision colours can't be converted yet." });

    const hdr = jobs.start({ ...spec, hdr: "HDR10" }) as { id: string };
    spawned[1]!.fake.finish(1);
    await tick();
    expect(jobs.status(hdr.id)).toMatchObject({ state: "failed", error: "The clip could not be made." });
    expect(JSON.stringify(jobs.status(hdr.id))).not.toContain("libplacebo");
  });

  test("ready lists only that profile's jobs for that set", async () => {
    const { jobs, spawned } = harness();
    jobs.start(spec);
    spawned[0]!.fake.finish(0);
    await tick();
    jobs.start({ ...spec, profileId: "p2" });
    spawned[1]!.fake.finish(0);
    await tick();
    jobs.start({ ...spec, setId: "other" });
    expect(jobs.ready("p1", "s1")).toHaveLength(1);
    expect(jobs.ready("p1", "other")).toHaveLength(1);
    expect(jobs.ready("p3", "s1")).toHaveLength(0);
  });

  test("sweep forgets a job an hour after it ended and deletes its file", async () => {
    const { jobs, spawned, clock } = harness();
    const { id } = jobs.start(spec) as { id: string };
    const path = spawned[0]!.command.at(-1)!;
    spawned[0]!.fake.finish(0, path);
    await tick();
    clock.t = 3_600_000;
    jobs.sweep();
    expect(jobs.status(id)).not.toBeNull();
    clock.t = 3_600_001;
    jobs.sweep();
    expect(jobs.status(id)).toBeNull();
    expect(existsSync(path)).toBe(false);
  });

  test("a directory that cannot be made ends the job failed without throwing", () => {
    const { directory } = harness();
    const blocker = join(directory, "file");
    writeFileSync(blocker, "x");
    const jobs = new ClipJobs({ baseUrl: "http://x", pacedReads: false, directory: join(blocker, "clips"), spawn: () => fakeProcess().proc });
    const started = jobs.start(spec) as { id: string };
    expect(jobs.status(started.id)).toMatchObject({ state: "failed", error: "The clip could not be made." });
    // Failed jobs do not hold the slot.
    expect(jobs.start(spec)).not.toBe("busy");
  });

  test("an export still running after twenty minutes is stopped and fails with the generic copy", async () => {
    const { jobs, spawned, timers } = harness();
    const stuck = jobs.start({ ...spec, hdr: "DV" }) as { id: string };
    let killed = false;
    const proc = spawned[0]!.fake.proc;
    const kill = proc.kill.bind(proc);
    proc.kill = () => { killed = true; kill(); };
    expect(timers).toHaveLength(1);
    expect(timers[0]!.ms).toBe(20 * 60_000);

    timers[0]!.run();
    expect(killed).toBe(true);
    expect(jobs.status(stuck.id)).toMatchObject({ state: "failed", error: "The clip could not be made." });
    // The slot is free at once, even if the process never exits.
    expect(jobs.start(spec)).not.toBe("busy");
  });

  test("an export that ends in time calls its timer off", async () => {
    const { jobs, spawned, timers } = harness();
    const { id } = jobs.start(spec) as { id: string };
    spawned[0]!.fake.finish(0);
    await tick();
    expect(timers[0]!.cancelled).toBe(true);
    timers[0]!.run(); // a timer that fires anyway changes nothing
    expect(jobs.status(id)).toMatchObject({ state: "done" });
  });

  test("the file name strips unsafe characters", () => {
    const { jobs, spawned } = harness();
    const { id } = jobs.start({ ...spec, title: "AC/DC: Live?" }) as { id: string };
    expect(jobs.status(id)?.name).toBe("ACDC Live – 1h02m00s.mp4");
    spawned[0]!.fake.finish(1);
  });
});
