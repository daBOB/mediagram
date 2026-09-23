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
 * slots, so the title actually playing always has the rest.
 */

import type { PartLocation } from "../catalog";
import type { FetchRange } from "./reader";

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
  onHeld?: (setId: string) => void;
  log?: (line: string) => void;
}

export class SeriesPreload {
  private waiting: PreloadItem[] = [];
  private current: string | null = null;
  private worker: Promise<void> | null = null;
  private running = false;

  constructor(private readonly options: SeriesPreloadOptions) {}

  /**
   * What to take next, replacing whatever was still waiting.
   *
   * Replacing rather than appending: a viewer who opened another episode has
   * moved on, and the ones after the old episode are no longer the next ones.
   * The set already downloading is let finish — half of it is on disk, and it
   * is usually still one of the wanted ones.
   */
  want(items: PreloadItem[]): void {
    this.waiting = items.filter((item) => item.setId !== this.current);
    if (this.running) return;
    this.running = true;
    this.worker = this.drain();
  }

  /** Resolves once nothing is waiting or downloading. For tests. */
  async settle(): Promise<void> {
    while (this.running) await this.worker;
  }

  private async drain(): Promise<void> {
    for (let item = this.waiting.shift(); item; item = this.waiting.shift()) {
      this.current = item.setId;
      try {
        if (await this.options.isHeld(item.setId)) continue;
        for (const location of item.locations) {
          await this.options.fill(
            item.setId,
            location.span.idx,
            location.span.len,
            this.options.fetcherFor(location.messageId),
          );
        }
        this.options.onHeld?.(item.setId);
        this.options.log?.(`preload: ${item.title} held`);
      } catch (error) {
        // A preload that fails costs nothing but the wait it was meant to
        // save; the episode still streams when it is opened. Said once, and
        // the next one is tried.
        this.options.log?.(`preload: ${item.title} stopped: ${(error as Error).message}`);
      } finally {
        this.current = null;
      }
    }
    // Cleared in the same step that found the queue empty, so a `want` that
    // lands after this starts a new worker rather than finding this one
    // still marked as running and leaving its items unread.
    this.running = false;
  }
}
