/**
 * Clip exports as background jobs.
 *
 * One at a time: an export is a CPU encode of up to three minutes, and the
 * player's own transcodes share the machine. Finished files are kept for an
 * hour so a viewer who left the page can come back for them, then swept.
 */

import { randomBytes } from "node:crypto";
import { mkdirSync, rmSync } from "node:fs";
import { rm } from "node:fs/promises";
import { join } from "node:path";

import { clipArgs, type ClipHdr } from "./clip-args";
import { parseProgress } from "./progress";

export interface ClipProcess {
  stdout: ReadableStream<Uint8Array>;
  stderr: ReadableStream<Uint8Array>;
  exited: Promise<number>;
  kill(): void;
}

export interface ClipSpec {
  profileId: string;
  setId: string;
  title: string;
  from: number;
  to: number;
  audioTrack: number;
  hdr: ClipHdr;
}

export interface ClipStatus {
  id: string;
  setId: string;
  from: number;
  to: number;
  state: "running" | "done" | "failed";
  /** 0..1 */
  progress: number;
  error?: string;
  name: string;
}

export interface ClipJobsOptions {
  baseUrl: string;
  pacedReads: boolean;
  directory: string;
  spawn?: (command: string[]) => ClipProcess;
  now?: () => number;
}

export const CLIP_BUSY = "Another clip is being made. Try again in a moment.";
const DV_FAILED = "This title's Dolby Vision colours can't be converted yet.";
const FAILED = "The clip could not be made.";
const KEEP_MS = 3_600_000;
const STDERR_LINES = 20;

interface Job {
  status: ClipStatus;
  profileId: string;
  path: string;
  endedAt: number | null;
  /** Last lines of ffmpeg's stderr: for the server's log, never for the client. */
  stderr: string[];
  process: ClipProcess | null;
}

const defaultSpawn = (command: string[]): ClipProcess => Bun.spawn(command, { stdout: "pipe", stderr: "pipe" });
/** `Title – 1h02m00s.mp4`: what the viewer's Files app will show. */
export function clipFileName(title: string, from: number): string {
  const safe = title.replace(/[/\\:*?"<>|\u0000-\u001f\u007f]/g, "").trim().slice(0, 80).trim() || "clip";
  const s = Math.floor(from);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${safe} – ${Math.floor(s / 3600)}h${pad(Math.floor(s / 60) % 60)}m${pad(s % 60)}s.mp4`;
}

export class ClipJobs {
  private readonly jobs = new Map<string, Job>();
  private readonly spawn: (command: string[]) => ClipProcess;
  private readonly now: () => number;

  constructor(private readonly options: ClipJobsOptions) {
    this.spawn = options.spawn ?? defaultSpawn;
    this.now = options.now ?? Date.now;
  }

  start(spec: ClipSpec): { id: string } | "busy" {
    if ([...this.jobs.values()].some((j) => j.status.state === "running")) return "busy";
    const id = randomBytes(8).toString("hex");
    const path = join(this.options.directory, `${id}.mp4`);
    const job: Job = {
      profileId: spec.profileId, path, endedAt: null, stderr: [], process: null,
      status: {
        id, setId: spec.setId, from: spec.from, to: spec.to, state: "running", progress: 0,
        name: clipFileName(spec.title, spec.from),
      },
    };
    this.jobs.set(id, job);
    // Global options first: ffmpeg ignores any that follow the output path.
    const command = ["nice", "-n", "10", "ffmpeg", "-progress", "pipe:1", "-nostats", ...clipArgs({
      input: `${this.options.baseUrl}/api/sets/${encodeURIComponent(spec.setId)}/stream`,
      output: path, from: spec.from, to: spec.to, audioTrack: spec.audioTrack,
      hdr: spec.hdr, pacedReads: this.options.pacedReads,
    })];
    try {
      mkdirSync(this.options.directory, { recursive: true });
      job.process = this.spawn(command);
    } catch (error) {
      console.warn("clip: could not start", error);
      this.end(job, false, spec.hdr);
      return { id };
    }
    void this.supervise(job, job.process, spec);
    return { id };
  }

  status(id: string): ClipStatus | null {
    return this.jobs.get(id)?.status ?? null;
  }

  file(id: string): { path: string; name: string; profileId: string } | null {
    const job = this.jobs.get(id);
    return job?.status.state === "done" ? { path: job.path, name: job.status.name, profileId: job.profileId } : null;
  }

  ready(profileId: string, setId: string): ClipStatus[] {
    return [...this.jobs.values()]
      .filter((j) => j.profileId === profileId && j.status.setId === setId && j.status.state !== "failed")
      .map((j) => j.status);
  }

  sweep(): void {
    for (const [id, job] of this.jobs) {
      if (job.endedAt !== null && this.now() - job.endedAt > KEEP_MS) {
        this.jobs.delete(id);
        rmSync(job.path, { force: true });
      }
    }
  }

  /** On shutdown: an orphaned ffmpeg outlives the server, and the files are not worth keeping. */
  async stopAll(): Promise<void> {
    for (const job of this.jobs.values()) job.process?.kill();
    this.jobs.clear();
    await rm(this.options.directory, { recursive: true, force: true });
  }

  private async supervise(job: Job, proc: ClipProcess, spec: ClipSpec): Promise<void> {
    // Both pipes are drained for as long as ffmpeg runs: an unread pipe fills and blocks it.
    const progress = (async () => {
      let carry = "";
      const decoder = new TextDecoder();
      try {
        for await (const chunk of proc.stdout) {
          const parsed = parseProgress(decoder.decode(chunk, { stream: true }), carry);
          carry = parsed.carry;
          for (const block of parsed.blocks) {
            if (block.outSeconds === null) continue;
            const fraction = block.outSeconds / (spec.to - spec.from);
            job.status.progress = Math.min(1, Math.max(job.status.progress, fraction));
          }
        }
      } catch { /* a lost reading only makes the bar stand still */ }
    })();
    const errors = (async () => {
      let rest = "";
      const decoder = new TextDecoder();
      try {
        for await (const chunk of proc.stderr) {
          const lines = (rest + decoder.decode(chunk, { stream: true })).split("\n");
          rest = lines.pop() ?? "";
          job.stderr.push(...lines);
          job.stderr.splice(0, Math.max(0, job.stderr.length - STDERR_LINES));
        }
      } catch { /* diagnosis only */ }
    })();
    const code = await proc.exited.catch(() => 1);
    await Promise.all([progress, errors]);
    // Dropped by stopAll: a killed process at shutdown is not a failure.
    if (this.jobs.get(job.status.id) !== job) return;
    if (code !== 0) console.warn(`clip: ffmpeg exited ${code}\n${job.stderr.join("\n")}`);
    this.end(job, code === 0, spec.hdr);
  }

  private end(job: Job, ok: boolean, hdr: ClipHdr): void {
    // A job ends once, whichever of the exit and a failed spawn reports first.
    if (job.status.state !== "running") return;
    job.endedAt = this.now();
    if (ok) {
      job.status.state = "done";
      job.status.progress = 1;
      return;
    }
    job.status.state = "failed";
    job.status.error = hdr === "DV" ? DV_FAILED : FAILED;
    void rm(job.path, { force: true });
  }
}
