/**
 * How long the event loop is kept waiting, over rolling windows.
 *
 * A single average since startup would let one hiccup at boot inflate the
 * figure forever. Instead a histogram is read and reset on a fixed interval,
 * so what is shown is only ever the last complete window.
 */

import { monitorEventLoopDelay } from "node:perf_hooks";

/** p50, p99 and max, all in whole milliseconds. */
export interface LoopLagReading {
  p50: number;
  p99: number;
  max: number;
}

/** The slice of `perf_hooks`' event loop histogram this module needs. */
export interface EventLoopHistogram {
  enable(): boolean;
  disable(): boolean;
  reset(): void;
  percentile(percentile: number): number;
  max: number;
}

export interface LoopLagOptions {
  /** How long a window covers before it rotates. */
  windowMs?: number;
  /** Replaces the real histogram; a test supplies one with known numbers. */
  createHistogram?: () => EventLoopHistogram;
  /** Replaces `setInterval`, so a test can trigger a rotation by hand. */
  schedule?: (rotate: () => void, ms: number) => unknown;
  /** Replaces `clearInterval`, paired with `schedule`. */
  cancel?: (handle: unknown) => void;
}

export interface LoopLag {
  /** The last complete window, or `null` before one has finished. */
  reading(): LoopLagReading | null;
  stop(): void;
}

const NS_PER_MS = 1_000_000;

function defaultHistogram(): EventLoopHistogram {
  return monitorEventLoopDelay({ resolution: 20 });
}

/**
 * Starts measuring. Never throws: a runtime that refuses to enable the
 * histogram reports no reading rather than failing the caller.
 */
export function startLoopLag(options: LoopLagOptions = {}): LoopLag {
  const windowMs = options.windowMs ?? 10_000;
  const schedule =
    options.schedule ??
    ((rotate: () => void, ms: number) => {
      const timer = setInterval(rotate, ms);
      timer.unref();
      return timer;
    });
  const cancel = options.cancel ?? ((handle) => clearInterval(handle as NodeJS.Timeout));

  let last: LoopLagReading | null = null;
  let histogram: EventLoopHistogram | null = null;
  try {
    histogram = (options.createHistogram ?? defaultHistogram)();
    histogram.enable();
  } catch {
    histogram = null;
  }

  const handle = histogram
    ? schedule(() => {
        last = {
          p50: histogram!.percentile(50) / NS_PER_MS,
          p99: histogram!.percentile(99) / NS_PER_MS,
          max: histogram!.max / NS_PER_MS,
        };
        histogram!.reset();
      }, windowMs)
    : null;

  return {
    reading: () => last,
    stop: () => {
      if (handle !== null) cancel(handle);
      histogram?.disable();
    },
  };
}
