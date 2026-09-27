/**
 * Takes the next episodes of a show into the cache while the viewer watches
 * this one.
 *
 * Which episodes is the page's call, made with the same `nextAfter` that
 * drives Play next — so what is fetched ahead is exactly what would play
 * next, and the server keeps no second idea of episode order.
 *
 * One set at a time, one run at a time. That is what keeps it out of the
 * viewer's way: a preload never holds more than one of the download gate's
 * slots, so the title actually playing always has the rest — and its
 * fetcher is expected to run the gate's `background` lane, so even that one
 * slot yields the moment a foreground read wants it (`download-gate.ts`).
 *
 * A whole episode fetched flat-out, unpaced, is still enough requests per
 * second on its own to trip Telegram's flood limit even with the viewer
 * idle, so every fetch this preload makes is also split and paced (below):
 * the gate keeps it from crowding out playback, pacing keeps it from
 * flooding by itself.
 */

import type { PartLocation } from "../catalog";
import { failureMessage } from "../failure-message";
import { CACHE_CHUNK } from "./key";
import type { FetchRange } from "./reader";

/**
 * How long to wait before each request the preload makes.
 *
 * One 512 KiB chunk per second is about 4 Mbit/s — comfortably under
 * whatever rate trips Telegram's `upload.getFile` flood limit, and still
 * fast enough that the next episode is cached in roughly the time it takes
 * to watch this one.
 */
export const PRELOAD_REQUEST_INTERVAL_MS = 1000;

/** The real wait, for production; tests inject an instant one. */
async function realPause(ms: number): Promise<void> {
  await new Promise<void>((resolve) => setTimeout(resolve, ms));
}

/** A set to take, with where its parts live — resolved by the caller, who holds the catalog. */
export interface PreloadItem {
  setId: string;
  /** What the log calls it. */
  title: string;
  locations: PartLocation[];
}

export interface SeriesPreloadOptions {
  fill: (setId: string, partIdx: number, partLength: number, fetch: FetchRange) => Promise<void>;
  fetcherFor: (messageId: number) => FetchRange;
  /** Whether a set is already on disk in full; skipped if so. */
  isHeld: (setId: string) => Promise<boolean>;
  /** Told when a set has been taken in full, so the offline badge can follow. */
  onHeld?: (setId: string) => void | Promise<void>;
  log?: (line: string) => void;
  /** The wait between requests. Overridable so tests run instantly. */
  pause?: (ms: number) => Promise<void>;
}

export class SeriesPreload {
  private waiting: PreloadItem[] = [];
  private current: string | null = null;
  private worker: Promise<void> | null = null;
  private running = false;
  private stopped = false;
  private readonly pause: (ms: number) => Promise<void>;

  constructor(private readonly options: SeriesPreloadOptions) {
    this.pause = options.pause ?? realPause;
  }

  /**
   * What to take next, replacing whatever was still waiting.
   *
   * Replacing rather than appending: a viewer who opened another episode has
   * moved on, and the ones after the old episode are no longer the next ones.
   * The set already downloading is let finish — half of it is on disk, and it
   * is usually still one of the wanted ones.
   */
  want(items: PreloadItem[]): void {
    if (this.stopped) return;
    this.waiting = items.filter((item) => item.setId !== this.current);
    if (this.running) return;
    this.running = true;
    this.worker = this.drain();
  }

  /** Resolves once nothing is waiting or downloading. For tests. */
  async settle(): Promise<void> {
    while (this.running) await this.worker;
  }

  /** Close admission and drain the range already downloading; discard later work. */
  async stop(): Promise<void> {
    this.stopped = true;
    this.waiting = [];
    await this.worker;
  }

  private async drain(): Promise<void> {
    for (let item = this.waiting.shift(); item; item = this.waiting.shift()) {
      this.current = item.setId;
      try {
        if (await this.options.isHeld(item.setId)) continue;
        for (const location of item.locations) {
          if (this.stopped) return;
          const fetch = this.pacedFetch(this.options.fetcherFor(location.messageId));
          await this.options.fill(item.setId, location.span.idx, location.span.len, fetch);
        }
        if (this.stopped) return;
        await this.options.onHeld?.(item.setId);
        this.options.log?.(`preload: ${item.title} held`);
      } catch (error) {
        // A preload that fails costs nothing but the wait it was meant to
        // save; the episode still streams when it is opened. Said once, and
        // the next one is tried.
        this.options.log?.(`preload: ${item.title} stopped: ${failureMessage(error)}`);
      } finally {
        this.current = null;
        if (this.stopped) this.running = false;
      }
    }
    // Cleared in the same step that found the queue empty, so a `want` that
    // lands after this starts a new worker rather than finding this one
    // still marked as running and leaving its items unread.
    this.running = false;
  }

  /**
   * Wraps a fetcher so it never asks for more than one cache chunk, or more
   * often than once per `PRELOAD_REQUEST_INTERVAL_MS`.
   *
   * `fillRun` (`reader.ts`) hands this a whole run — up to 4 MiB — expecting
   * it back in one piece; splitting and reassembling here keeps that
   * unaware it is talking to anything paced. Paused before every slice,
   * including the first of each call: two calls back to back (one run
   * ending, the next beginning) must not fire two requests together, or the
   * pacing only holds inside a run and not across the whole preload.
   */
  private pacedFetch(fetch: FetchRange): FetchRange {
    return async (offset, length) => {
      const out = new Uint8Array(length);
      let at = 0;
      while (at < length) {
        if (this.stopped) throw new Error("preload stopped");
        await this.pause(PRELOAD_REQUEST_INTERVAL_MS);
        // Stopped during the wait: no new request once a stop is asked for.
        if (this.stopped) throw new Error("preload stopped");
        const take = Math.min(CACHE_CHUNK, length - at);
        const slice = await fetch(offset + at, take);
        out.set(slice, at);
        at += slice.length;
        if (slice.length < take) break; // short read; let the caller's own length check report it
      }
      return out.subarray(0, at);
    };
  }
}
