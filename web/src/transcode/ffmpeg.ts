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
import type { Runner, Running, SessionSpec } from "./registry";
import { cpuPercentSince, parseProgress, readCpuTicks, type TranscodeProgress } from "./progress";

export interface FfmpegOptions {
  encoder: Encoder;
  /** Where this server answers its own Range requests. */
  baseUrl: string;
  segmentSeconds: number;
}

interface ChildProcess {
  pid: number;
  stdout: ReadableStream<Uint8Array>;
  stderr: ReadableStream<Uint8Array>;
  exited: Promise<number>;
  kill(signal: "SIGTERM" | "SIGKILL"): void;
}

/** Raw process, log, timer and `/proc` IO; supervision remains in the runner. */
interface ProcessIo {
  spawn(command: string[]): ChildProcess;
  appendLog(path: string, text: string): void;
  schedule(callback: () => void, milliseconds: number): () => void;
  readCpuTicks(pid: number): Promise<number | null>;
  now(): number;
}

const processIo: ProcessIo = {
  spawn: (command) => Bun.spawn(command, { stdout: "pipe", stderr: "pipe" }),
  appendLog: appendFileSync,
  schedule(callback, milliseconds) {
    const timer = setTimeout(callback, milliseconds);
    return () => clearTimeout(timer);
  },
  readCpuTicks,
  now: () => performance.now(),
};

export class FfmpegRunner implements Runner {
  constructor(private readonly options: FfmpegOptions, private readonly io: ProcessIo = processIo) {}

  start(_sessionId: string, directory: string, spec: SessionSpec): Running {
    const args = transcodeArgs({
      input: `${this.options.baseUrl}/api/sets/${encodeURIComponent(spec.setId)}/stream`,
      output: join(directory, "index.m3u8"),
      encoder: this.options.encoder,
      seekSeconds: spec.seekSeconds,
      maxrateBits: spec.maxrateBits,
      audioTrack: spec.audioTrack,
      copyVideo: spec.copyVideo,
      hevcCopy: spec.hevcCopy,
      segmentSeconds: this.options.segmentSeconds,
      // Left to ffmpeg: it reads the real rate from the source, and
      // `-force_key_frames` holds the segment boundaries regardless.
      frameRate: null,
    });

    const log = join(directory, "ffmpeg.log");
    const proc = this.io.spawn(["ffmpeg", ...args]);

    // Appended as it arrives rather than collected and written at exit: the
    // failure worth catching is an ffmpeg that stops making progress and
    // never exits, and a log that only appears afterwards says nothing about
    // one of those.
    void (async () => {
      try {
        const decoder = new TextDecoder();
        for await (const chunk of proc.stderr) {
          const text = decoder.decode(chunk, { stream: true });
          if (text) this.io.appendLog(log, text);
        }
      } catch {
        // A lost log is not worth failing a playback for.
      }
    })();

    let last: TranscodeProgress | null = null;
    void this.readProgress(proc, (progress) => {
      last = progress;
    });

    return {
      // Surfaced so a wait for a first segment can give up the moment ffmpeg
      // dies, rather than polling for output that is never coming.
      exited: proc.exited,
      progress: () => last,
      stop: async () => {
        let cancel = () => {};
        try {
          // SIGTERM lets ffmpeg finish the segment it is writing and close
          // the playlist; SIGKILL would leave a truncated segment behind.
          proc.kill("SIGTERM");
          const exited = await Promise.race([
            proc.exited.then(() => true),
            new Promise<boolean>((resolve) => {
              cancel = this.io.schedule(() => resolve(false), 3000);
            }),
          ]);
          if (!exited) proc.kill("SIGKILL");
        } catch {
          // Already gone; still wait for its exit notification below.
        } finally {
          cancel();
        }
        await proc.exited;
      },
    };
  }

  /**
   * Drains `-progress pipe:1`, the same way `stderr` is drained above: an
   * unread pipe fills and blocks ffmpeg, so this runs for as long as the
   * process does regardless of whether anyone is currently asking for a
   * reading.
   */
  private async readProgress(proc: ChildProcess, onBlock: (progress: TranscodeProgress) => void): Promise<void> {
    let carry = "";
    let cpu: { ticks: number; atMs: number } | null = null;
    try {
      const decoder = new TextDecoder();
      for await (const chunk of proc.stdout) {
        const parsed = parseProgress(decoder.decode(chunk, { stream: true }), carry);
        carry = parsed.carry;
        for (const block of parsed.blocks) {
          const ticks = await this.io.readCpuTicks(proc.pid);
          const cpuReading = cpuPercentSince(cpu, ticks, this.io.now());
          cpu = cpuReading.next;
          onBlock({ ...block, cpuPercent: cpuReading.percent });
        }
      }
    } catch {
      // A lost progress reading is not worth failing a playback for.
    }
  }
}
