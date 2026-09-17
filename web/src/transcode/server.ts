/**
 * Serving what a transcode has produced so far.
 *
 * Reading from the session's directory rather than from ffmpeg's output
 * stream: HLS is files, and a segment is only readable once it is complete.
 * A request for something not yet written is answered as not-ready rather
 * than missing, because a player retries the first and abandons the second.
 */

import { readFile } from "node:fs/promises";
import { join, resolve } from "node:path";

import type { HlsServer } from "../routes";
import type { TranscodeRegistry } from "./registry";

const TYPES: Record<string, string> = {
  ".m3u8": "application/vnd.apple.mpegurl",
  ".ts": "video/mp2t",
  ".m4s": "video/iso.segment",
};

export interface TranscodeFilesOptions {
  /** How long a first segment may take before the title is called failed. */
  readyTimeoutMs?: number;
  pollMs?: number;
}

/**
 * Long enough for a cold set at a large offset: the bytes have to come from
 * Telegram before ffmpeg can encode a frame of them.
 */
const DEFAULT_READY_TIMEOUT_MS = 45_000;
const DEFAULT_POLL_MS = 100;

export class TranscodeFiles implements HlsServer {
  private readonly readyTimeoutMs: number;
  private readonly pollMs: number;

  constructor(
    private readonly registry: TranscodeRegistry,
    options: TranscodeFilesOptions = {},
  ) {
    this.readyTimeoutMs = options.readyTimeoutMs ?? DEFAULT_READY_TIMEOUT_MS;
    this.pollMs = options.pollMs ?? DEFAULT_POLL_MS;
  }

  /**
   * Starts, or joins, the transcode for this title at this offset.
   *
   * Joining is the point: two viewers of the same thing share one encoder
   * session rather than racing for the hardware.
   */
  async begin(setId: string, seekSeconds: number): Promise<string> {
    const session = await this.registry.sessionFor(setId, seekSeconds);

    // Not returned until there is something to play. hls.js gives a manifest
    // one retry and then reports a fatal error, so a URL handed over early is
    // not "the player waits" — it is a title that fails to start.
    try {
      await this.waitForFirstSegment(session.directory, session.exited);
    } catch (error) {
      // A session that never produced anything is an ffmpeg holding the
      // encoder for nothing. Released rather than stopped: another viewer may
      // be watching the same one and getting segments perfectly well.
      await this.registry.release(session.id);
      throw error;
    }
    return `/hls/${session.id}/index.m3u8`;
  }

  /** Resolves when the playlist lists a segment, or throws having waited. */
  private async waitForFirstSegment(directory: string, exited?: Promise<number>): Promise<void> {
    const playlist = join(directory, "index.m3u8");
    const deadline = Date.now() + this.readyTimeoutMs;

    // ffmpeg dies on a bad argument within milliseconds. Watching for that is
    // the difference between saying so at once and polling for output that is
    // never coming until the timeout runs out.
    let stopped = false;
    void exited?.then(() => {
      stopped = true;
    });

    for (;;) {
      const text = await readFile(playlist, "utf8").catch(() => null);
      // The header alone is written before anything is encoded; a segment
      // line is the first evidence that there is a picture.
      if (text !== null && /^[^#\r\n]+\.(?:ts|m4s)\s*$/m.test(text)) return;
      if (stopped) {
        throw new Error(`the conversion stopped before it produced anything; see ffmpeg.log`);
      }
      if (Date.now() >= deadline) {
        throw new Error(
          `the conversion produced no segment within ${Math.round(this.readyTimeoutMs / 1000)}s`,
        );
      }
      await Bun.sleep(this.pollMs);
    }
  }

  /**
   * One viewer is finished with a session.
   *
   * A release, not a stop: sessions are shared, so the first viewer to close
   * the dialog must not end the encode the other one is watching. Idempotent,
   * because a browser saying goodbye to a session already reaped is normal.
   */
  async end(sessionId: string): Promise<void> {
    await this.registry.release(sessionId);
  }

  async file(sessionId: string, name: string): Promise<{ body: Uint8Array; type: string } | null> {
    const session = this.registry.get(sessionId);
    if (!session) return null;

    const path = join(session.directory, name);
    // The route already constrains the name, but this is the last point
    // before a filesystem read, and the cost of checking twice is nothing.
    if (!resolve(path).startsWith(resolve(session.directory))) return null;

    // Read straight out, rather than asking whether it exists first: between
    // the two, a session being stopped takes the directory away, and the read
    // then fails out of the route as a 500 instead of the not-ready this is.
    const body = await Bun.file(path)
      .arrayBuffer()
      .then((bytes) => new Uint8Array(bytes))
      .catch(() => null);
    if (body === null) return null;

    // Someone is watching; do not reap this session out from under them.
    this.registry.touch(sessionId);

    const dot = name.lastIndexOf(".");
    return { body, type: TYPES[name.slice(dot)] ?? "application/octet-stream" };
  }
}
