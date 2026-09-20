/**
 * Handing a player a playlist it can actually load.
 *
 * hls.js gives a manifest one retry and then reports a fatal error, so a
 * playlist URL returned before ffmpeg has written anything is not "the player
 * will wait" — it is a title that fails to start. `begin` therefore does not
 * answer until there is a segment listed.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { TranscodeRegistry } from "../src/transcode/registry";
import { TranscodeFiles } from "../src/transcode/server";

/** A transcode's identity, so a call site says which number is which. */
const spec = (
  setId: string,
  seekSeconds: number,
  maxrateBits: number,
  audioTrack = 0,
  copyVideo = false,
) => ({ setId, seekSeconds, maxrateBits, audioTrack, copyVideo });


let work: string;

beforeEach(async () => {
  work = await mkdtemp(join(tmpdir(), "mediagram-files-"));
});
afterEach(async () => {
  await rm(work, { recursive: true, force: true });
});

const EMPTY_PLAYLIST = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n";
const ONE_SEGMENT = `${EMPTY_PLAYLIST}#EXTINF:2.000000,\nindex0.ts\n`;

/** Writes what a real ffmpeg would write, after `afterMs`. */
function runnerWriting(text: string | null, afterMs: number) {
  return {
    start(_id: string, directory: string) {
      const timer = setTimeout(() => {
        if (text !== null) void writeFile(join(directory, "index.m3u8"), text);
      }, afterMs);
      return {
        stop: async () => clearTimeout(timer),
      };
    },
  };
}

describe("starting a transcode", () => {
  test("the playlist URL arrives only once a segment is listed", async () => {
    const registry = new TranscodeRegistry(work, runnerWriting(ONE_SEGMENT, 40));
    const files = new TranscodeFiles(registry, { readyTimeoutMs: 2000, pollMs: 10 });

    const playlist = await files.begin(spec("01SET", 0, 8_000_000));

    expect(playlist).toMatch(/^\/hls\/[a-f0-9]{16}\/index\.m3u8$/);
    const session = playlist.split("/")[2]!;
    expect(await files.file(session, "index.m3u8")).not.toBeNull();
    await registry.stopAll();
  });

  /**
   * ffmpeg writes the playlist header before it has encoded anything. A
   * player handed that reads it as a stream with no content.
   */
  test("a playlist with no segments in it does not count as ready", async () => {
    const registry = new TranscodeRegistry(work, runnerWriting(EMPTY_PLAYLIST, 0));
    const files = new TranscodeFiles(registry, { readyTimeoutMs: 150, pollMs: 10 });

    await expect(files.begin(spec("01SET", 0, 8_000_000))).rejects.toThrow(/segment/i);
    await registry.stopAll();
  });

  test("a transcode that never produces anything fails rather than hanging", async () => {
    const registry = new TranscodeRegistry(work, runnerWriting(null, 0));
    const files = new TranscodeFiles(registry, { readyTimeoutMs: 150, pollMs: 10 });

    await expect(files.begin(spec("01SET", 0, 8_000_000))).rejects.toThrow();
    await registry.stopAll();
  });

  /**
   * ffmpeg dies on a bad argument or a missing encoder within milliseconds.
   * Polling the filesystem for a segment it will never write makes the viewer
   * wait out the whole readiness timeout for a failure already known.
   */
  test("an ffmpeg that exits is reported at once, not after the timeout", async () => {
    const registry = new TranscodeRegistry(work, {
      start() {
        return { stop: async () => {}, exited: Promise.resolve(1) };
      },
    });
    const files = new TranscodeFiles(registry, { readyTimeoutMs: 10_000, pollMs: 10 });

    const began = Date.now();
    await expect(files.begin(spec("01SET", 0, 8_000_000))).rejects.toThrow(/stopped|exit/i);

    expect(Date.now() - began).toBeLessThan(2000);
    await registry.stopAll();
  });

  test("a runner that cannot say whether it exited still times out", async () => {
    const registry = new TranscodeRegistry(work, runnerWriting(null, 0));
    const files = new TranscodeFiles(registry, { readyTimeoutMs: 150, pollMs: 10 });

    await expect(files.begin(spec("01SET", 0, 8_000_000))).rejects.toThrow(/no segment/i);
    await registry.stopAll();
  });

  test("a failed start leaves no session behind", async () => {
    const registry = new TranscodeRegistry(work, runnerWriting(null, 0));
    const files = new TranscodeFiles(registry, { readyTimeoutMs: 150, pollMs: 10 });

    await files.begin(spec("01SET", 0, 8_000_000)).catch(() => {});

    expect(registry.count()).toBe(0);
  });
});
