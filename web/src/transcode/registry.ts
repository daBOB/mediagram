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

/**
 * What one transcode is of.
 *
 * Described rather than positional. There are five of these now, two of them
 * numbers and one a boolean, and `start(id, dir, setId, 900, 8000000, 2,
 * true)` says nothing at its call site about which `true` that is — the same
 * reason `preloadReadout` stopped taking its five in a row.
 *
 * It is also the session's identity: two viewers of the same spec share one
 * encode, and any field added here has to be part of that identity or the
 * second viewer silently gets the first one's stream.
 */
export interface SessionSpec {
  setId: string;
  seekSeconds: number;
  /** Ceiling for the output, in bits per second. Ignored while copying. */
  maxrateBits: number;
  /** `0:a:N`. The first stream unless the viewer chose another. */
  audioTrack: number;
  /** Carry the picture across rather than encode it. See `video-copy.ts`. */
  copyVideo: boolean;
  /**
   * The picture being carried across is HEVC, for a browser that said it
   * decodes it. Written as fMP4 tagged `hvc1`: hls.js plays HEVC from fMP4
   * only, and Safari and Chrome refuse the `hev1` tag Matroska sources carry.
   * Absent means MPEG-TS, which is what every other session still is.
   */
  hevcCopy?: boolean;
}

/** A running transcode, however it is actually run. */
export interface Runner {
  start(sessionId: string, directory: string, spec: SessionSpec): Running;
}

export interface Running {
  stop(): Promise<void>;
  /**
   * Resolves with the exit code when the process ends, where the runner can
   * say. Optional because a runner need not be a process at all.
   */
  exited?: Promise<number>;
}

export interface Session extends SessionSpec {
  id: string;
  directory: string;
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
  private readonly stopping = new Set<Promise<void>>();
  private readonly idleMs: number;
  private readonly maxSessions: number;
  private readonly now: () => number;
  private closed = false;
  private shutdown: Promise<void> | null = null;

  constructor(
    private readonly workDir: string,
    private readonly runner: Runner,
    options: { idleMs?: number; maxSessions?: number; now?: () => number } = {},
  ) {
    this.idleMs = options.idleMs ?? DEFAULT_IDLE_MS;
    this.maxSessions = options.maxSessions ?? DEFAULT_MAX_SESSIONS;
    this.now = options.now ?? Date.now;
  }

  /**
   * Acquires one viewer's share of a session, starting it if necessary.
   *
   * Identified by what it transcodes rather than by a random id, so two
   * viewers of the same thing share one encode instead of racing.
   */
  async acquireSession(spec: SessionSpec): Promise<Session> {
    if (this.closed) throw new Error("the conversion registry is shutting down");
    const id = sessionId(spec);
    const existing = this.sessions.get(id);
    if (existing) {
      existing.lastUsed = this.now();
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
        tracked.lastUsed = this.now();
        tracked.watchers += 1;
      }
      return session;
    }

    // Counted before starting, and only for a session that is genuinely new:
    // joining one already running costs nothing and must never be refused.
    if (this.sessions.size + this.starting.size >= this.maxSessions) {
      throw new Error(`too many conversions at once (${this.maxSessions}); try again shortly`);
    }

    const starting = this.start(id, spec).finally(() => this.starting.delete(id));
    this.starting.set(id, starting);
    return starting;
  }

  private async start(id: string, spec: SessionSpec): Promise<Session> {
    const directory = join(this.workDir, id);
    // Emptied rather than reused. A directory left by a killed server holds
    // that run's playlist, and a new session would be reported ready
    // immediately and serve segments of an encode that is no longer running.
    await rm(directory, { recursive: true, force: true });
    await mkdir(directory, { recursive: true });

    const process = this.runner.start(id, directory, spec);
    const tracked: Tracked = {
      ...spec,
      id,
      directory,
      process,
      exited: process.exited,
      lastUsed: this.now(),
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
   * Each acquisition must be released once; repeated releases consume shares.
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
    if (session) session.lastUsed = this.now();
  }

  has(id: string): boolean {
    return this.sessions.has(id);
  }

  get(id: string): Session | undefined {
    return this.sessions.get(id);
  }

  /**
   * Every session running now, with how many viewers each has.
   *
   * A copy rather than the tracked objects: a caller reading this must not be
   * able to reach the process handle or change the watcher count by holding
   * the row it was told about.
   */
  list(): Array<Session & { watchers: number }> {
    return [...this.sessions.values()].map((tracked) => ({
      id: tracked.id,
      directory: tracked.directory,
      setId: tracked.setId,
      seekSeconds: tracked.seekSeconds,
      maxrateBits: tracked.maxrateBits,
      audioTrack: tracked.audioTrack,
      copyVideo: tracked.copyVideo,
      hevcCopy: tracked.hevcCopy,
      watchers: tracked.watchers,
    }));
  }

  /** How many sessions may run at once. */
  get capacity(): number {
    return this.maxSessions;
  }

  count(): number {
    return this.sessions.size;
  }

  /** Stops a session and removes its segments. Absent is not an error. */
  stop(id: string): Promise<void> {
    const session = this.sessions.get(id);
    if (!session) return Promise.resolve();
    this.sessions.delete(id);
    const stopped = this.stopSession(session).finally(() => this.stopping.delete(stopped));
    this.stopping.add(stopped);
    return stopped;
  }

  private async stopSession(session: Tracked): Promise<void> {
    // Moved aside before ffmpeg is waited on, because ids are deterministic:
    // a session restarted during the three seconds ffmpeg gets to exit would
    // otherwise have its fresh directory deleted out from under it, and the
    // viewer would wait out the whole ready timeout for a 503.
    const discarded = `${session.directory}.stopping.${process.pid}.${discardCount++}`;
    const failures: unknown[] = [];
    let moved = false;
    try {
      await rename(session.directory, discarded);
      moved = true;
    } catch (error) {
      if (!(error instanceof Error && "code" in error && error.code === "ENOENT")) failures.push(error);
    }

    try { await session.process.stop(); }
    catch (error) { failures.push(error); }
    // The original path can already belong to a replacement session. Only
    // successful isolation establishes ownership of a directory to remove.
    if (moved) {
      try { await rm(discarded, { recursive: true, force: true }); }
      catch (error) { failures.push(error); }
    }
    if (failures.length === 1) throw failures[0];
    if (failures.length > 1) throw new AggregateError(failures, "could not clean up the conversion");
  }

  /** Stops every session that has not been read within the idle limit. */
  async reapIdle(): Promise<number> {
    const deadline = this.now() - this.idleMs;
    const stale = [...this.sessions.values()].filter((s) => s.lastUsed <= deadline);
    let reaped = 0;
    for (const session of stale) {
      // Earlier cleanup awaited process exit. A queued candidate may have
      // been read, acquired again, or replaced under the same deterministic id.
      if (this.sessions.get(session.id) !== session || session.lastUsed > deadline) continue;
      await this.stop(session.id);
      reaped += 1;
    }
    return reaped;
  }

  /** Used on shutdown: an orphaned ffmpeg outlives the server otherwise. */
  stopAll(): Promise<void> {
    if (this.shutdown) return this.shutdown;
    this.closed = true;
    this.shutdown = this.finishShutdown();
    return this.shutdown;
  }

  private async finishShutdown(): Promise<void> {
    // Admitted starts may still be making directories. Drain them before
    // collecting processes, including releases already waiting for exit.
    await Promise.allSettled(this.starting.values());
    const stops = [...this.stopping, ...[...this.sessions.keys()].map((id) => this.stop(id))];
    const results = await Promise.allSettled(stops);
    const failures = results.filter((result) => result.status === "rejected");
    if (failures.length) throw new AggregateError(failures.map((result) => result.reason), "could not stop every conversion");
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
function sessionId(spec: SessionSpec): string {
  return createHash("sha256")
    .update(spec.setId)
    .update(new Uint8Array([0]))
    .update(String(spec.seekSeconds))
    .update(new Uint8Array([0]))
    .update(String(spec.maxrateBits))
    .update(new Uint8Array([0]))
    // Part of the identity, not a detail of it: two viewers watching the same
    // film in different languages want different encodes, and sharing one
    // would hand the second viewer the first one's audio.
    .update(String(spec.audioTrack))
    .update(new Uint8Array([0]))
    // And likewise a copy: the same title, offset and track, copied for one
    // viewer and encoded for a capped one, are two different streams. An id
    // that ignored this would hand the capped viewer the uncapped bytes their
    // cap exists to prevent.
    // An HEVC copy is fMP4 rather than TS: a different stream again.
    .update(spec.copyVideo ? (spec.hevcCopy ? "copy-fmp4" : "copy") : "encode")
    .digest("hex")
    .slice(0, 16);
}
