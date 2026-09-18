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
import { mkdir, rename, rm } from "node:fs/promises";
import { join } from "node:path";

/** A running transcode, however it is actually run. */
export interface Runner {
  start(
    sessionId: string,
    directory: string,
    setId: string,
    seekSeconds: number,
    maxrateBits: number,
    /** `0:a:N`. Absent means the first stream, which is the default. */
    audioTrack?: number,
  ): Running;
}

export interface Running {
  stop(): Promise<void>;
  /**
   * Resolves with the exit code when the process ends, where the runner can
   * say. Optional because a runner need not be a process at all.
   */
  exited?: Promise<number>;
}

export interface Session {
  id: string;
  directory: string;
  setId: string;
  seekSeconds: number;
  /** What this encode was told to stay under, in bits per second. */
  maxrateBits: number;
  /** Which audio stream this encode carries, as `0:a:N`. */
  audioTrack: number;
  /** Resolves if the transcode stops, so a wait for output can give up. */
  exited?: Promise<number>;
}

interface Tracked extends Session {
  process: Running;
  lastUsed: number;
  /** How many viewers are watching. A session is shared, so the first one to
   *  leave must not stop the encode the others are still reading. */
  watchers: number;
}

/** How long a session may go unread before it is reaped. */
const DEFAULT_IDLE_MS = 5 * 60 * 1000;

/**
 * How many transcodes may run at once.
 *
 * Each holds an encoder and, at the default cap, writes about 1 MB per second
 * of film to disk — some 7 GB for a feature. Without a ceiling, a caller asking for a different
 * offset every time starts one per request.
 */
const DEFAULT_MAX_SESSIONS = 4;

export class TranscodeRegistry {
  private readonly sessions = new Map<string, Tracked>();
  /** Starts in flight, so two viewers arriving together share one ffmpeg. */
  private readonly starting = new Map<string, Promise<Session>>();
  private readonly idleMs: number;
  private readonly maxSessions: number;

  constructor(
    private readonly workDir: string,
    private readonly runner: Runner,
    options: { idleMs?: number; maxSessions?: number } = {},
  ) {
    this.idleMs = options.idleMs ?? DEFAULT_IDLE_MS;
    this.maxSessions = options.maxSessions ?? DEFAULT_MAX_SESSIONS;
  }

  /**
   * The session for this title at this offset, started if it is not running.
   *
   * Identified by what it transcodes rather than by a random id, so two
   * viewers of the same thing share one encode instead of racing.
   */
  async sessionFor(
    setId: string,
    seekSeconds: number,
    maxrateBits: number,
    audioTrack = 0,
  ): Promise<Session> {
    const id = sessionId(setId, seekSeconds, maxrateBits, audioTrack);
    const existing = this.sessions.get(id);
    if (existing) {
      existing.lastUsed = Date.now();
      existing.watchers += 1;
      return existing;
    }

    // The starting promise goes in the map before anything is awaited. Two
    // viewers pressing play at the same moment used to get past the check
    // above while the directory was being made, and the second ffmpeg — over-
    // writing the same segments — was never tracked, so it survived stop,
    // reapIdle and shutdown still holding the encoder.
    const pending = this.starting.get(id);
    if (pending) {
      const session = await pending;
      const tracked = this.sessions.get(session.id);
      if (tracked) {
        tracked.lastUsed = Date.now();
        tracked.watchers += 1;
      }
      return session;
    }

    // Counted before starting, and only for a session that is genuinely new:
    // joining one already running costs nothing and must never be refused.
    if (this.sessions.size + this.starting.size >= this.maxSessions) {
      throw new Error(`too many conversions at once (${this.maxSessions}); try again shortly`);
    }

    const starting = this.start(id, setId, seekSeconds, maxrateBits, audioTrack).finally(() =>
      this.starting.delete(id),
    );
    this.starting.set(id, starting);
    return starting;
  }

  private async start(
    id: string,
    setId: string,
    seekSeconds: number,
    maxrateBits: number,
    audioTrack: number,
  ): Promise<Session> {
    const directory = join(this.workDir, id);
    // Emptied rather than reused. A directory left by a killed server holds
    // that run's playlist, and a new session would be reported ready
    // immediately and serve segments of an encode that is no longer running.
    await rm(directory, { recursive: true, force: true });
    await mkdir(directory, { recursive: true });

    const process = this.runner.start(id, directory, setId, seekSeconds, maxrateBits, audioTrack);
    const tracked: Tracked = {
      id,
      directory,
      setId,
      seekSeconds,
      maxrateBits,
      audioTrack,
      process,
      exited: process.exited,
      lastUsed: Date.now(),
      watchers: 1,
    };
    this.sessions.set(id, tracked);
    return tracked;
  }

  /**
   * One viewer is finished with a session; stops it when the last one is.
   *
   * Absent, or held by someone else, is not an error: a browser saying
   * goodbye to a session already reaped is the normal case.
   */
  async release(id: string): Promise<void> {
    const session = this.sessions.get(id);
    if (!session) return;
    session.watchers -= 1;
    if (session.watchers > 0) return;
    await this.stop(id);
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

    // Moved aside before ffmpeg is waited on, because ids are deterministic:
    // a session restarted during the three seconds ffmpeg gets to exit would
    // otherwise have its fresh directory deleted out from under it, and the
    // viewer would wait out the whole ready timeout for a 503.
    const discarded = `${session.directory}.stopping.${process.pid}.${discardCount++}`;
    const moved = await rename(session.directory, discarded).then(
      () => true,
      () => false,
    );

    await session.process.stop();
    await rm(moved ? discarded : session.directory, { recursive: true, force: true });
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

/** Makes each discarded directory's name unique within a run. */
let discardCount = 0;

/**
 * A stable id for one title, at one offset, at one bitrate.
 *
 * The bitrate is part of the identity because it is part of what the session
 * *is*: a viewer whose link cannot carry the default needs a different
 * encode, and an id that ignored the cap would hand them the one already
 * failing.
 *
 * Hashed rather than composed from the set id: this reaches a URL and then a
 * path, and a set id comes from a caption, so it must not be able to carry a
 * separator or a `..` into either.
 */
function sessionId(
  setId: string,
  seekSeconds: number,
  maxrateBits: number,
  audioTrack: number,
): string {
  return createHash("sha256")
    .update(setId)
    .update(new Uint8Array([0]))
    .update(String(seekSeconds))
    .update(new Uint8Array([0]))
    .update(String(maxrateBits))
    .update(new Uint8Array([0]))
    // Part of the identity, not a detail of it: two viewers watching the same
    // film in different languages want different encodes, and sharing one
    // would hand the second viewer the first one's audio.
    .update(String(audioTrack))
    .digest("hex")
    .slice(0, 16);
}
