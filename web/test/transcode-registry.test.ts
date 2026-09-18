/**
 * One transcode per viewer, and no orphans.
 *
 * An ffmpeg left running holds a hardware encoder session and writes segments
 * to a disk nobody is reading. The registry's whole job is that starting a
 * second transcode of the same thing reuses the first, and that everything
 * stops when it should.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { TranscodeRegistry } from "../src/transcode/registry";

let work: string;
let started: string[];

/** Stands in for ffmpeg: records that it was asked to run, and can be killed. */
function fakeRunner() {
  return {
    start(sessionId: string) {
      started.push(sessionId);
      let stopped = false;
      return {
        stop: async () => {
          stopped = true;
        },
        get stopped() {
          return stopped;
        },
      };
    },
  };
}

beforeEach(async () => {
  work = await mkdtemp(join(tmpdir(), "mediagram-hls-"));
  started = [];
});
afterEach(async () => {
  await rm(work, { recursive: true, force: true });
});

describe("sessions", () => {
  test("a session is identified by what it is transcoding, not by chance", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const a = await registry.sessionFor("01SET", 0, 8_000_000);
    const b = await registry.sessionFor("01SET", 0, 8_000_000);

    expect(a.id).toBe(b.id);
    expect(started).toHaveLength(1);
  });

  test("a different seek is a different session", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const a = await registry.sessionFor("01SET", 0, 8_000_000);
    const b = await registry.sessionFor("01SET", 600, 8_000_000);

    expect(a.id).not.toBe(b.id);
    expect(started).toHaveLength(2);
  });

  test("a different audio track is a different session", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    // Sharing here would hand the second viewer the first one's language.
    const a = await registry.sessionFor("01SET", 0, 8_000_000, 0);
    const b = await registry.sessionFor("01SET", 0, 8_000_000, 2);

    expect(a.id).not.toBe(b.id);
    expect(a.directory).not.toBe(b.directory);
    expect(started).toHaveLength(2);
  });

  test("the same audio track joins the session already running", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const a = await registry.sessionFor("01SET", 0, 8_000_000, 2);
    const b = await registry.sessionFor("01SET", 0, 8_000_000, 2);

    expect(a.id).toBe(b.id);
    expect(started).toHaveLength(1);
  });

  test("an unstated track is the first one, and joins a session that said so", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const a = await registry.sessionFor("01SET", 0, 8_000_000);
    const b = await registry.sessionFor("01SET", 0, 8_000_000, 0);

    expect(a.id).toBe(b.id);
    expect(a.audioTrack).toBe(0);
    expect(started).toHaveLength(1);
  });

  test("a different set is a different session", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    await registry.sessionFor("01SETA", 0, 8_000_000);
    await registry.sessionFor("01SETB", 0, 8_000_000);

    expect(started).toHaveLength(2);
  });

  test("each session gets its own directory", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const a = await registry.sessionFor("01SETA", 0, 8_000_000);
    const b = await registry.sessionFor("01SETB", 0, 8_000_000);

    expect(a.directory).not.toBe(b.directory);
    expect(a.directory.startsWith(work)).toBe(true);
  });
});

describe("stopping", () => {
  test("stopping a session kills its process", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    const session = await registry.sessionFor("01SET", 0, 8_000_000);

    await registry.stop(session.id);

    expect(registry.count()).toBe(0);
  });

  test("stopping everything leaves nothing running", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    await registry.sessionFor("01SETA", 0, 8_000_000);
    await registry.sessionFor("01SETB", 0, 8_000_000);

    await registry.stopAll();

    expect(registry.count()).toBe(0);
  });

  test("stopping a session that is already gone is not an error", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    await registry.stop("nosuchsession");

    expect(registry.count()).toBe(0);
  });

  /** A viewer who closed the tab an hour ago should not still hold an encoder. */
  test("a session idle past its limit is reaped", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner(), { idleMs: 10 });
    const session = await registry.sessionFor("01SET", 0, 8_000_000);

    await new Promise((r) => setTimeout(r, 25));
    await registry.reapIdle();

    expect(registry.count()).toBe(0);
    expect(registry.has(session.id)).toBe(false);
  });

  test("a session still being read is not reaped", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner(), { idleMs: 50 });
    const session = await registry.sessionFor("01SET", 0, 8_000_000);

    await new Promise((r) => setTimeout(r, 30));
    registry.touch(session.id);
    await new Promise((r) => setTimeout(r, 30));
    await registry.reapIdle();

    expect(registry.has(session.id)).toBe(true);
  });
});

describe("session ids", () => {
  /** The id reaches a URL and then a path, so it must be inert. */
  test("are plain and contain nothing path-like", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const session = await registry.sessionFor("01SET/../../etc", 0, 8_000_000);

    expect(session.id).toMatch(/^[a-f0-9]+$/);
  });
});

describe("two viewers arriving at once", () => {
  /**
   * `sessionFor` awaits a mkdir between reading the map and writing to it. Two
   * callers that overlap in that window both started an ffmpeg, and only one
   * of them was ever tracked: the other wrote over the same segments and
   * survived stop, reapIdle and shutdown, holding the encoder for good.
   */
  test("concurrent starts of the same title produce one transcode", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const [a, b] = await Promise.all([
      registry.sessionFor("01SET", 0, 8_000_000),
      registry.sessionFor("01SET", 0, 8_000_000),
    ]);

    expect(a.id).toBe(b.id);
    expect(started).toHaveLength(1);
    expect(registry.count()).toBe(1);
  });

  test("everything started is stopped by stopAll", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    await Promise.all([registry.sessionFor("01SET", 0, 8_000_000), registry.sessionFor("01SET", 0, 8_000_000)]);

    await registry.stopAll();

    expect(registry.count()).toBe(0);
  });

  test("a start that fails is not left in the map", async () => {
    const registry = new TranscodeRegistry(work, {
      start() {
        throw new Error("ffmpeg is not installed");
      },
    });

    await expect(registry.sessionFor("01SET", 0, 8_000_000)).rejects.toThrow(/ffmpeg/);
    expect(registry.count()).toBe(0);
  });
});

describe("a session that is watched by more than one viewer", () => {
  /**
   * Sessions are shared on purpose, so the first viewer to close the dialog
   * must not stop the encode the other one is watching.
   */
  test("one viewer leaving does not stop the other's playback", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    await registry.sessionFor("01SET", 0, 8_000_000);
    await registry.sessionFor("01SET", 0, 8_000_000);

    await registry.release(sessionIdOf(registry));

    expect(registry.count()).toBe(1);
  });

  test("the last viewer leaving stops it", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    await registry.sessionFor("01SET", 0, 8_000_000);
    await registry.sessionFor("01SET", 0, 8_000_000);
    const id = sessionIdOf(registry);

    await registry.release(id);
    await registry.release(id);

    expect(registry.count()).toBe(0);
  });

  test("releasing a session nobody holds is not an error", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    await registry.release("0".repeat(16));

    expect(registry.count()).toBe(0);
  });

  test("a rejoined session is watched again, so an earlier release is not fatal", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    await registry.sessionFor("01SET", 0, 8_000_000);
    const id = sessionIdOf(registry);

    await registry.release(id);
    // Someone else opens the same title: a fresh session, watched by one.
    await registry.sessionFor("01SET", 0, 8_000_000);

    expect(registry.count()).toBe(1);
  });
});

/** The id of the one session in the registry. */
function sessionIdOf(registry: TranscodeRegistry): string {
  const session = registry.get(started[0]!);
  if (!session) throw new Error("no session");
  return session.id;
}

describe("how many transcodes may run at once", () => {
  /**
   * Each session holds an encoder and writes about 2 MB per second of film to
   * disk — some 7 GB for a feature. Without a ceiling, a caller asking for a
   * different offset each time starts one per request.
   */
  test("a new session past the limit is refused rather than started", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner(), { maxSessions: 2 });
    await registry.sessionFor("01SET", 0, 8_000_000);
    await registry.sessionFor("01SET", 60, 8_000_000);

    await expect(registry.sessionFor("01SET", 120, 8_000_000)).rejects.toThrow(/too many|at once|limit/i);
    expect(registry.count()).toBe(2);
  });

  test("joining a session that is already running is never refused", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner(), { maxSessions: 1 });
    await registry.sessionFor("01SET", 0, 8_000_000);

    const again = await registry.sessionFor("01SET", 0, 8_000_000);

    expect(again.seekSeconds).toBe(0);
    expect(started).toHaveLength(1);
  });

  test("room freed by a release can be used again", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner(), { maxSessions: 1 });
    const first = await registry.sessionFor("01SET", 0, 8_000_000);
    await registry.release(first.id);

    await registry.sessionFor("01SET", 60, 8_000_000);

    expect(registry.count()).toBe(1);
  });
});

describe("a session at a particular bitrate", () => {
  /**
   * The bitrate is part of what a session is, not a setting of the server.
   * A viewer whose link cannot carry 8 Mbit/s needs a different encode, and
   * one keyed only by title and offset would hand them the one that is
   * already failing.
   */
  test("the same title at the same offset but a different cap is a different session", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const fast = await registry.sessionFor("01SET", 0, 8_000_000);
    const slow = await registry.sessionFor("01SET", 0, 3_000_000);

    expect(fast.id).not.toBe(slow.id);
    expect(started).toHaveLength(2);
  });

  test("the same cap joins the session already running", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const first = await registry.sessionFor("01SET", 0, 3_000_000);
    const second = await registry.sessionFor("01SET", 0, 3_000_000);

    expect(second.id).toBe(first.id);
    expect(started).toHaveLength(1);
  });

  test("the cap reaches the runner", async () => {
    const caps: number[] = [];
    const registry = new TranscodeRegistry(work, {
      start(id: string, _dir: string, _setId: string, _seek: number, maxrateBits: number) {
        started.push(id);
        caps.push(maxrateBits);
        return { stop: async () => {} };
      },
    });

    await registry.sessionFor("01SET", 0, 2_500_000);

    expect(caps).toEqual([2_500_000]);
  });
});
