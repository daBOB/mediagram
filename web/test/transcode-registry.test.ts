/**
 * One transcode per viewer, and no orphans.
 *
 * An ffmpeg left running holds a hardware encoder session and writes segments
 * to a disk nobody is reading. The registry's whole job is that starting a
 * second transcode of the same thing reuses the first, and that everything
 * stops when it should.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdtemp, rm, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { TranscodeRegistry, type SessionSpec } from "../src/transcode/registry";

/** A transcode's identity, so a call site says which number is which. */
const spec = (
  setId: string,
  seekSeconds: number,
  maxrateBits: number,
  audioTrack = 0,
  copyVideo = false,
) => ({ setId, seekSeconds, maxrateBits, audioTrack, copyVideo });


let work: string;
let started: string[];
let stopped: string[];

/** Stands in for ffmpeg: records that it was asked to run, and can be killed. */
function fakeRunner() {
  return {
    start(sessionId: string) {
      started.push(sessionId);
      return {
        stop: async () => {
          stopped.push(sessionId);
        },
      };
    },
  };
}

beforeEach(async () => {
  work = await mkdtemp(join(tmpdir(), "mediagram-hls-"));
  started = [];
  stopped = [];
});
afterEach(async () => {
  await rm(work, { recursive: true, force: true });
});

describe("sessions", () => {
  test("a session is identified by what it is transcoding, not by chance", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const a = await registry.acquireSession(spec("01SET", 0, 8_000_000));
    const b = await registry.acquireSession(spec("01SET", 0, 8_000_000));

    expect(a.id).toBe(b.id);
    expect(started).toHaveLength(1);
  });

  test("a different seek is a different session", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const a = await registry.acquireSession(spec("01SET", 0, 8_000_000));
    const b = await registry.acquireSession(spec("01SET", 600, 8_000_000));

    expect(a.id).not.toBe(b.id);
    expect(started).toHaveLength(2);
  });

  test("a different audio track is a different session", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    // Sharing here would hand the second viewer the first one's language.
    const a = await registry.acquireSession(spec("01SET", 0, 8_000_000, 0));
    const b = await registry.acquireSession(spec("01SET", 0, 8_000_000, 2));

    expect(a.id).not.toBe(b.id);
    expect(a.directory).not.toBe(b.directory);
    expect(started).toHaveLength(2);
  });

  test("the same audio track joins the session already running", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const a = await registry.acquireSession(spec("01SET", 0, 8_000_000, 2));
    const b = await registry.acquireSession(spec("01SET", 0, 8_000_000, 2));

    expect(a.id).toBe(b.id);
    expect(started).toHaveLength(1);
  });

  test("an unstated track is the first one, and joins a session that said so", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const a = await registry.acquireSession(spec("01SET", 0, 8_000_000));
    const b = await registry.acquireSession(spec("01SET", 0, 8_000_000, 0));

    expect(a.id).toBe(b.id);
    expect(a.audioTrack).toBe(0);
    expect(started).toHaveLength(1);
  });

  test("a different set is a different session", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    await registry.acquireSession(spec("01SETA", 0, 8_000_000));
    await registry.acquireSession(spec("01SETB", 0, 8_000_000));

    expect(started).toHaveLength(2);
  });

  test("each session gets its own directory", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const a = await registry.acquireSession(spec("01SETA", 0, 8_000_000));
    const b = await registry.acquireSession(spec("01SETB", 0, 8_000_000));

    expect(a.directory).not.toBe(b.directory);
    expect(a.directory.startsWith(work)).toBe(true);
  });
});

describe("stopping", () => {
  test("stopping a session kills its process", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    const session = await registry.acquireSession(spec("01SET", 0, 8_000_000));

    await registry.stop(session.id);

    expect(registry.count()).toBe(0);
    expect(stopped).toEqual([session.id]);
  });

  test("stopping everything leaves nothing running", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    await registry.acquireSession(spec("01SETA", 0, 8_000_000));
    await registry.acquireSession(spec("01SETB", 0, 8_000_000));

    await registry.stopAll();

    expect(registry.count()).toBe(0);
    expect(stopped.sort()).toEqual(started.sort());
  });

  test("stopping a session that is already gone is not an error", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    await registry.stop("nosuchsession");

    expect(registry.count()).toBe(0);
  });

  /** A viewer who closed the tab an hour ago should not still hold an encoder. */
  test("a session idle past its limit is reaped", async () => {
    let now = 1_000;
    const registry = new TranscodeRegistry(work, fakeRunner(), { idleMs: 10, now: () => now });
    const session = await registry.acquireSession(spec("01SET", 0, 8_000_000));

    now = 1_009;
    expect(await registry.reapIdle()).toBe(0);
    expect(stopped).toEqual([]);
    now = 1_010;
    expect(await registry.reapIdle()).toBe(1);

    expect(registry.count()).toBe(0);
    expect(registry.has(session.id)).toBe(false);
    expect(stopped).toEqual([session.id]);
  });

  test("a session still being read is not reaped", async () => {
    let now = 1_000;
    const registry = new TranscodeRegistry(work, fakeRunner(), { idleMs: 50, now: () => now });
    const session = await registry.acquireSession(spec("01SET", 0, 8_000_000));

    now = 1_030;
    registry.touch(session.id);
    now = 1_079;
    expect(await registry.reapIdle()).toBe(0);

    expect(registry.has(session.id)).toBe(true);
    expect(stopped).toEqual([]);
    now = 1_080;
    expect(await registry.reapIdle()).toBe(1);
    expect(stopped).toEqual([session.id]);
  });

  test.each(["touch", "acquire", "replace"])("idle reaping rechecks a queued session after %s during earlier cleanup", async (activity) => {
    let now = 1_000;
    const stopping = deferred<void>();
    const release = deferred<void>();
    const registry = new TranscodeRegistry(work, {
      start(id, _directory, asked) {
        return { stop: async () => {
          stopped.push(id);
          if (asked.setId === "FIRST") { stopping.resolve(); await release.promise; }
        } };
      },
    }, { idleMs: 50, now: () => now });
    const first = await registry.acquireSession(spec("FIRST", 0, 8_000_000));
    const secondSpec = spec("SECOND", 0, 8_000_000);
    const second = await registry.acquireSession(secondSpec);
    now = 1_100;
    const reaping = registry.reapIdle();
    try {
      await stopping.promise;
      if (activity === "touch") registry.touch(second.id);
      else {
        if (activity === "replace") {
          await registry.stop(second.id);
          // Keep the replacement itself old enough to be eligible, so only
          // tracked-object identity can distinguish it from the stale candidate.
          now = 1_000;
        }
        await registry.acquireSession(secondSpec);
      }
      release.resolve();
      expect(await reaping).toBe(1);
      expect(registry.has(first.id)).toBe(false);
      expect(registry.has(second.id)).toBe(true);
      expect(stopped.filter((id) => id === second.id)).toHaveLength(activity === "replace" ? 1 : 0);
    } finally {
      release.resolve();
      await reaping;
      await registry.stopAll();
    }
  });
});

describe("session ids", () => {
  /** The id reaches a URL and then a path, so it must be inert. */
  test("are plain and contain nothing path-like", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const session = await registry.acquireSession(spec("01SET/../../etc", 0, 8_000_000));

    expect(session.id).toMatch(/^[a-f0-9]+$/);
  });
});

describe("two viewers arriving at once", () => {
  /**
   * `acquireSession` awaits a mkdir between reading the map and writing to it. Two
   * callers that overlap in that window both started an ffmpeg, and only one
   * of them was ever tracked: the other wrote over the same segments and
   * survived stop, reapIdle and shutdown, holding the encoder for good.
   */
  test("concurrent starts of the same title produce one transcode", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const [a, b] = await Promise.all([
      registry.acquireSession(spec("01SET", 0, 8_000_000)),
      registry.acquireSession(spec("01SET", 0, 8_000_000)),
    ]);

    expect(a.id).toBe(b.id);
    expect(started).toHaveLength(1);
    expect(registry.count()).toBe(1);
  });

  test("everything started is stopped by stopAll", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    await Promise.all([registry.acquireSession(spec("01SET", 0, 8_000_000)), registry.acquireSession(spec("01SET", 0, 8_000_000))]);

    await registry.stopAll();

    expect(registry.count()).toBe(0);
    expect(stopped).toEqual(started);
  });

  test("a start that fails is not left in the map", async () => {
    const registry = new TranscodeRegistry(work, {
      start() {
        throw new Error("ffmpeg is not installed");
      },
    });

    await expect(registry.acquireSession(spec("01SET", 0, 8_000_000))).rejects.toThrow(/ffmpeg/);
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
    await registry.acquireSession(spec("01SET", 0, 8_000_000));
    await registry.acquireSession(spec("01SET", 0, 8_000_000));

    await registry.release(sessionIdOf(registry));

    expect(registry.count()).toBe(1);
    expect(stopped).toEqual([]);
  });

  test("the last viewer leaving stops it", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    await registry.acquireSession(spec("01SET", 0, 8_000_000));
    await registry.acquireSession(spec("01SET", 0, 8_000_000));
    const id = sessionIdOf(registry);

    await registry.release(id);
    await registry.release(id);

    expect(registry.count()).toBe(0);
    expect(stopped).toEqual([id]);
  });

  test("releasing a session nobody holds is not an error", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    await registry.release("0".repeat(16));

    expect(registry.count()).toBe(0);
  });

  test("a rejoined session is watched again, so an earlier release is not fatal", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    await registry.acquireSession(spec("01SET", 0, 8_000_000));
    const id = sessionIdOf(registry);

    await registry.release(id);
    // Someone else opens the same title: a fresh session, watched by one.
    await registry.acquireSession(spec("01SET", 0, 8_000_000));

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
    await registry.acquireSession(spec("01SET", 0, 8_000_000));
    await registry.acquireSession(spec("01SET", 60, 8_000_000));

    await expect(registry.acquireSession(spec("01SET", 120, 8_000_000))).rejects.toThrow(/too many|at once|limit/i);
    expect(registry.count()).toBe(2);
  });

  test("joining a session that is already running is never refused", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner(), { maxSessions: 1 });
    await registry.acquireSession(spec("01SET", 0, 8_000_000));

    const again = await registry.acquireSession(spec("01SET", 0, 8_000_000));

    expect(again.seekSeconds).toBe(0);
    expect(started).toHaveLength(1);
  });

  test("room freed by a release can be used again", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner(), { maxSessions: 1 });
    const first = await registry.acquireSession(spec("01SET", 0, 8_000_000));
    await registry.release(first.id);

    await registry.acquireSession(spec("01SET", 60, 8_000_000));

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

    const fast = await registry.acquireSession(spec("01SET", 0, 8_000_000));
    const slow = await registry.acquireSession(spec("01SET", 0, 3_000_000));

    expect(fast.id).not.toBe(slow.id);
    expect(started).toHaveLength(2);
  });

  test("the same cap joins the session already running", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());

    const first = await registry.acquireSession(spec("01SET", 0, 3_000_000));
    const second = await registry.acquireSession(spec("01SET", 0, 3_000_000));

    expect(second.id).toBe(first.id);
    expect(started).toHaveLength(1);
  });

  test("the cap reaches the runner", async () => {
    const seen: SessionSpec[] = [];
    const registry = new TranscodeRegistry(work, {
      start(id: string, _dir: string, asked: SessionSpec) {
        started.push(id);
        seen.push(asked);
        return { stop: async () => {} };
      },
    });

    await registry.acquireSession(spec("01SET", 0, 2_500_000));

    expect(seen.map((one) => one.maxrateBits)).toEqual([2_500_000]);
  });
});

describe("carrying the picture across", () => {
  test("the decision reaches the runner", async () => {
    const seen: SessionSpec[] = [];
    const registry = new TranscodeRegistry(work, {
      start(id: string, _dir: string, asked: SessionSpec) {
        started.push(id);
        seen.push(asked);
        return { stop: async () => {} };
      },
    });

    await registry.acquireSession(spec("01SET", 0, 8_000_000, 0, true));

    expect(seen[0]?.copyVideo).toBe(true);
  });

  test("a copy and an encode of the same thing are two sessions", async () => {
    // Otherwise a viewer whose link needed capping would join the session
    // already running with the original bytes, which is the one thing their
    // cap exists to prevent.
    const registry = new TranscodeRegistry(work, fakeRunner());
    const copied = await registry.acquireSession(spec("01SET", 0, 8_000_000, 0, true));
    const encoded = await registry.acquireSession(spec("01SET", 0, 8_000_000, 0, false));

    expect(copied.id).not.toBe(encoded.id);
  });

  test("and two viewers of the same copy still share one", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    const a = await registry.acquireSession(spec("01SET", 0, 8_000_000, 0, true));
    const b = await registry.acquireSession(spec("01SET", 0, 8_000_000, 0, true));

    expect(b.id).toBe(a.id);
  });
});

describe("session snapshots", () => {
  test.each([
    { copyVideo: false },
    { copyVideo: true, hevcCopy: false },
    { copyVideo: true, hevcCopy: true },
  ])("preserves the video mode in a detached listing: %j", async (mode) => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    const asked = { ...spec("01SET", 10, 8_000_000, 2), ...mode };
    const session = await registry.acquireSession(asked);
    const listed = registry.list()[0]!;

    expect(listed).toMatchObject({ ...asked, id: session.id, watchers: 1 });
    expect(listed).not.toHaveProperty("process");
    listed.watchers = 20;
    listed.copyVideo = !asked.copyVideo;
    expect(registry.list()[0]).toMatchObject({ ...asked, watchers: 1 });
  });
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

describe("shutdown admission", () => {
  test("shutdown waits for starts already admitted and stops each resulting process", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    const first = registry.acquireSession(spec("01SETA", 0, 8_000_000));
    const second = registry.acquireSession(spec("01SETB", 0, 8_000_000));

    await registry.stopAll();
    await Promise.all([first, second]);

    expect(registry.count()).toBe(0);
    expect(stopped.sort()).toEqual(started.sort());
    expect(stopped).toHaveLength(2);
  });

  test("shutdown refuses both joins and new sessions as soon as it begins", async () => {
    const stopping = deferred<void>();
    const release = deferred<void>();
    const registry = new TranscodeRegistry(work, {
      start() {
        return { stop: async () => { stopping.resolve(); await release.promise; } };
      },
    });
    const same = spec("01SET", 0, 8_000_000);
    await registry.acquireSession(same);
    const shutdown = registry.stopAll();
    try {
      await stopping.promise;
      await expect(registry.acquireSession(same)).rejects.toThrow(/shut|clos/i);
      await expect(registry.acquireSession(spec("01OTHER", 0, 8_000_000))).rejects.toThrow(/shut|clos/i);
    } finally {
      release.resolve();
      await shutdown;
    }
    await expect(registry.acquireSession(same)).rejects.toThrow(/shut|clos/i);
  });

  test("repeated shutdown calls wait for the same process cleanup", async () => {
    const stopping = deferred<void>();
    const release = deferred<void>();
    const registry = new TranscodeRegistry(work, {
      start() {
        return { stop: async () => { stopping.resolve(); await release.promise; } };
      },
    });
    await registry.acquireSession(spec("01SET", 0, 8_000_000));
    const first = registry.stopAll();
    await stopping.promise;
    let finished = false;
    const second = registry.stopAll().then(() => { finished = true; });
    try {
      await Promise.resolve();
      expect(finished).toBe(false);
    } finally {
      release.resolve();
      await Promise.all([first, second]);
    }
  });
});

describe("shutdown cleanup", () => {
  test.each([false, true])("old cleanup preserves a replacement when its directory was missing: %s", async (missing) => {
    const stopping = deferred<void>();
    const release = deferred<void>();
    let starts = 0;
    const registry = new TranscodeRegistry(work, {
      start() {
        const first = starts++ === 0;
        return { stop: async () => { if (first) { stopping.resolve(); await release.promise; } } };
      },
    });
    const asked = spec("01SET", 0, 8_000_000);
    const old = await registry.acquireSession(asked);
    if (missing) await rm(old.directory, { recursive: true });
    const stopped = registry.release(old.id);
    try {
      await stopping.promise;
      const current = await registry.acquireSession(asked);
      const marker = join(current.directory, "new-segment.ts");
      await writeFile(marker, "replacement bytes");
      release.resolve();
      await stopped;
      expect(registry.has(current.id)).toBe(true);
      expect(await Bun.file(marker).text()).toBe("replacement bytes");
    } finally {
      release.resolve();
      await stopped;
      await registry.stopAll();
    }
  });

  test("directory isolation failure is reported after the process is stopped", async () => {
    const registry = new TranscodeRegistry(work, fakeRunner());
    const session = await registry.acquireSession(spec("01SET", 0, 8_000_000));
    await rm(work, { recursive: true });
    await writeFile(work, "directory was replaced by a file");
    await expect(registry.release(session.id)).rejects.toMatchObject({ code: "ENOTDIR", syscall: "rename" });
    expect(stopped).toEqual([session.id]);
    expect((await stat(work)).isFile()).toBe(true);
  });

  test("shutdown waits for a release already stopping a process", async () => {
    const stopping = deferred<void>();
    const release = deferred<void>();
    const registry = new TranscodeRegistry(work, {
      start() { return { stop: async () => { stopping.resolve(); await release.promise; } }; },
    });
    const session = await registry.acquireSession(spec("01SET", 0, 8_000_000));
    const released = registry.release(session.id);
    await stopping.promise;
    let finished = false;
    const shutdown = registry.stopAll().then(() => { finished = true; });
    try {
      await Promise.resolve();
      expect(finished).toBe(false);
    } finally {
      release.resolve();
      await Promise.all([released, shutdown]);
    }
  });

  test("one failed stop does not leave other processes running", async () => {
    const stoppedSets: string[] = [];
    const registry = new TranscodeRegistry(work, {
      start(_id, _dir, asked) {
        return {
          stop: async () => {
            stoppedSets.push(asked.setId);
            if (asked.setId === "01BAD") throw new Error("stop refused");
          },
        };
      },
    });
    await registry.acquireSession(spec("01BAD", 0, 8_000_000));
    await registry.acquireSession(spec("01GOOD", 0, 8_000_000));

    await expect(registry.stopAll()).rejects.toThrow();

    expect(stoppedSets.sort()).toEqual(["01BAD", "01GOOD"]);
    expect(registry.count()).toBe(0);
  });
});
