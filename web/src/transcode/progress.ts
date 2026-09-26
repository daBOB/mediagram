/**
 * Reading ffmpeg's own progress reports and its process's CPU time.
 *
 * `-progress pipe:1 -stats_period 2` writes `key=value` blocks to stdout,
 * independent of `-loglevel error` on stderr. That is the interface ffmpeg
 * offers for programs to read, and it does not need parsing a human log line.
 */

import { readFile } from "node:fs/promises";

/** One `-progress` block, the fields this panel cares about. */
export interface ProgressBlock {
  /** Encoding speed as a multiple of realtime, or `null` for `N/A`. */
  speed: number | null;
  fps: number | null;
  /** How far into the output ffmpeg has written, in seconds. */
  outSeconds: number | null;
}

/** A progress block plus the CPU reading taken alongside it: what `Running.progress()` returns. */
export interface TranscodeProgress extends ProgressBlock {
  /** Since the previous reading; `null` off Linux or before there is one. */
  cpuPercent: number | null;
}

export interface ParsedProgress {
  /** Every complete block found in this call. */
  blocks: ProgressBlock[];
  /** The partial line after the last block, to prefix onto the next chunk. */
  carry: string;
}

function numberOrNull(value: string | undefined): number | null {
  if (value === undefined) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function blockFrom(fields: Map<string, string>): ProgressBlock {
  const speedRaw = fields.get("speed");
  return {
    speed: speedRaw === undefined ? null : numberOrNull(speedRaw.replace(/x$/, "")),
    fps: numberOrNull(fields.get("fps")),
    outSeconds: (() => {
      const microseconds = numberOrNull(fields.get("out_time_us"));
      return microseconds === null ? null : microseconds / 1e6;
    })(),
  };
}

/**
 * Splits `carry + chunk` into whole `-progress` blocks and what is left.
 *
 * A block ends at its `progress=continue` or `progress=end` line. Everything
 * since the last one — whole lines a chunk boundary happened to land after,
 * as well as a final partial line — is returned as `carry` and re-parsed
 * with the next chunk, rather than only ever carrying a lone unfinished
 * line: a block is ten-odd lines, and a read can end anywhere among them.
 */
export function parseProgress(chunk: string, carry: string): ParsedProgress {
  const lines = (carry + chunk).split("\n");
  const pending = lines.pop() ?? "";

  const blocks: ProgressBlock[] = [];
  let fields = new Map<string, string>();
  let sinceLastBlock: string[] = [];
  for (const line of lines) {
    sinceLastBlock.push(line);
    const at = line.indexOf("=");
    if (at === -1) continue;
    const key = line.slice(0, at);
    if (key === "progress") {
      blocks.push(blockFrom(fields));
      fields = new Map();
      sinceLastBlock = [];
      continue;
    }
    fields.set(key, line.slice(at + 1));
  }
  return { blocks, carry: [...sinceLastBlock, pending].join("\n") };
}

/** Clock ticks per second, fixed on Linux regardless of `HZ` at kernel build time. */
const CLK_TCK = 100;

/** `utime + stime`, in clock ticks, or `null` when the layout is not what Linux writes. */
export function cpuTicksFromStat(text: string): number | null {
  // The process name can itself contain spaces and closing parentheses, so
  // the fields before it are skipped by taking everything after the *last* one.
  const nameEnd = text.lastIndexOf(")");
  if (nameEnd === -1) return null;
  const fields = text.slice(nameEnd + 1).trim().split(/\s+/);
  // 0-indexed from the field after the name: state, ppid, pgrp, session,
  // tty_nr, tpgid, flags, minflt, cminflt, majflt, cmajflt, utime, stime.
  const utime = Number(fields[11]);
  const stime = Number(fields[12]);
  return Number.isFinite(utime) && Number.isFinite(stime) ? utime + stime : null;
}

/** `null` for a process that has exited or a `/proc` this platform does not have. */
export async function readCpuTicks(pid: number): Promise<number | null> {
  try {
    return cpuTicksFromStat(await readFile(`/proc/${pid}/stat`, "utf8"));
  } catch {
    return null;
  }
}

/** CPU percent since `previous`, and the reading to pass as `previous` next time. */
export function cpuPercentSince(
  previous: { ticks: number; atMs: number } | null,
  ticks: number | null,
  atMs: number,
): { percent: number | null; next: { ticks: number; atMs: number } | null } {
  if (ticks === null) return { percent: null, next: previous };
  if (previous === null) return { percent: null, next: { ticks, atMs } };
  const deltaSeconds = (atMs - previous.atMs) / 1000;
  const percent = deltaSeconds > 0 ? ((ticks - previous.ticks) / (CLK_TCK * deltaSeconds)) * 100 : null;
  return { percent, next: { ticks, atMs } };
}
