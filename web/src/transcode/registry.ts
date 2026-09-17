/**
 * One transcode per viewer, and no orphans.
 *
 * An ffmpeg nobody is watching still holds a hardware encoder session and
 * still writes segments to disk. Two viewers of the same title at the same
 * offset should share one, and everything should stop when the last reader
 * goes away — a player that leaks encoder sessions stops working after a few
 * plays, and the cause is never obvious.
 */

import { createHash } from "node:crypto";
import { mkdir, rm } from "node:fs/promises";
import { join } from "node:path";

/** A running transcode, however it is actually run. */
export interface Runner {
  start(sessionId: string, directory: string, setId: string, seekSeconds: number): Running;
}

export interface Running {
  stop(): Promise<void>;
}

export interface Session {
  id: string;
  directory: string;
  setId: string;
  seekSeconds: number;
}

interface Tracked extends Session {
  process: Running;
  lastUsed: number;
}

/** How long a session may go unread before it is reaped. */
const DEFAULT_IDLE_MS = 5 * 60 * 1000;

export class TranscodeRegistry {
  private readonly sessions = new Map<string, Tracked>();
  private readonly idleMs: number;

  constructor(
    private readonly workDir: string,
    private readonly runner: Runner,
    options: { idleMs?: number } = {},
  ) {
    this.idleMs = options.idleMs ?? DEFAULT_IDLE_MS;
  }

  /**
   * The session for this title at this offset, started if it is not running.
   *
   * Identified by what it transcodes rather than by a random id, so two
   * viewers of the same thing share one encode instead of racing.
   */
  async sessionFor(setId: string, seekSeconds: number): Promise<Session> {
    const id = sessionId(setId, seekSeconds);
    const existing = this.sessions.get(id);
    if (existing) {
      existing.lastUsed = Date.now();
      return existing;
    }

    const directory = join(this.workDir, id);
    await mkdir(directory, { recursive: true });
    const process = this.runner.start(id, directory, setId, seekSeconds);

    const tracked: Tracked = { id, directory, setId, seekSeconds, process, lastUsed: Date.now() };
    this.sessions.set(id, tracked);
    return tracked;
  }

  /** Marks a session as still wanted, so `reapIdle` leaves it alone. */
  touch(id: string): void {
    const session = this.sessions.get(id);
    if (session) session.lastUsed = Date.now();
  }

  has(id: string): boolean {
    return this.sessions.has(id);
  }

  get(id: string): Session | undefined {
    return this.sessions.get(id);
  }

  count(): number {
    return this.sessions.size;
  }

  /** Stops a session and removes its segments. Absent is not an error. */
  async stop(id: string): Promise<void> {
    const session = this.sessions.get(id);
    if (!session) return;
    this.sessions.delete(id);
    await session.process.stop();
    await rm(session.directory, { recursive: true, force: true });
  }

  /** Stops every session that has not been read within the idle limit. */
  async reapIdle(): Promise<number> {
    const deadline = Date.now() - this.idleMs;
    const stale = [...this.sessions.values()].filter((s) => s.lastUsed <= deadline);
    for (const session of stale) await this.stop(session.id);
    return stale.length;
  }

  /** Used on shutdown: an orphaned ffmpeg outlives the server otherwise. */
  async stopAll(): Promise<void> {
    for (const id of [...this.sessions.keys()]) await this.stop(id);
  }
}

/**
 * A stable id for one title at one offset.
 *
 * Hashed rather than composed from the set id: this reaches a URL and then a
 * path, and a set id comes from a caption, so it must not be able to carry a
 * separator or a `..` into either.
 */
function sessionId(setId: string, seekSeconds: number): string {
  return createHash("sha256")
    .update(setId)
    .update(new Uint8Array([0]))
    .update(String(seekSeconds))
    .digest("hex")
    .slice(0, 16);
}
