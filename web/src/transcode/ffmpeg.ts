/**
 * Running ffmpeg, and stopping it properly.
 *
 * The supervision matters more than the spawning. An ffmpeg that is not
 * killed holds a hardware encoder session and keeps writing segments, and a
 * player that leaks those stops working after a handful of plays with no
 * obvious cause.
 */

import { appendFileSync } from "node:fs";
import { join } from "node:path";

import { transcodeArgs, type Encoder } from "./args";
import type { Runner, Running } from "./registry";

export interface FfmpegOptions {
  encoder: Encoder;
  /** Where this server answers its own Range requests. */
  baseUrl: string;
  segmentSeconds: number;
}

export class FfmpegRunner implements Runner {
  constructor(private readonly options: FfmpegOptions) {}

  start(
    _sessionId: string,
    directory: string,
    setId: string,
    seekSeconds: number,
    maxrateBits: number,
  ): Running {
    const args = transcodeArgs({
      input: `${this.options.baseUrl}/api/sets/${encodeURIComponent(setId)}/stream`,
      output: join(directory, "index.m3u8"),
      encoder: this.options.encoder,
      seekSeconds,
      maxrateBits,
      segmentSeconds: this.options.segmentSeconds,
      // Left to ffmpeg: it reads the real rate from the source, and
      // `-force_key_frames` holds the segment boundaries regardless.
      frameRate: null,
    });

    const log = join(directory, "ffmpeg.log");
    const proc = Bun.spawn(["ffmpeg", ...args], {
      stdout: "ignore",
      stderr: "pipe",
      // Detached would outlive us; this process must die with the server.
    });

    // Appended as it arrives rather than collected and written at exit: the
    // failure worth catching is an ffmpeg that stops making progress and
    // never exits, and a log that only appears afterwards says nothing about
    // one of those.
    void (async () => {
      try {
        const decoder = new TextDecoder();
        for await (const chunk of proc.stderr as ReadableStream<Uint8Array>) {
          const text = decoder.decode(chunk, { stream: true });
          if (text) appendFileSync(log, text);
        }
      } catch {
        // A lost log is not worth failing a playback for.
      }
    })();

    return {
      // Surfaced so a wait for a first segment can give up the moment ffmpeg
      // dies, rather than polling for output that is never coming.
      exited: proc.exited,
      stop: async () => {
        try {
          // SIGTERM lets ffmpeg finish the segment it is writing and close
          // the playlist; SIGKILL would leave a truncated segment behind.
          proc.kill("SIGTERM");
          await Promise.race([proc.exited, Bun.sleep(3000)]);
          proc.kill("SIGKILL");
        } catch {
          // Already gone.
        }
      },
    };
  }
}
