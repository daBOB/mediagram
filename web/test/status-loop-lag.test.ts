import { describe, expect, test } from "bun:test";
import { startLoopLag, type EventLoopHistogram } from "../src/status/loop-lag";

function fakeHistogram(percentiles: Record<number, number>, max: number): EventLoopHistogram {
  return {
    enable: () => true,
    disable: () => true,
    reset: () => {},
    percentile: (p) => percentiles[p] ?? 0,
    max,
  };
}

describe("loop lag", () => {
  test("reports nothing before the first window completes", () => {
    const lag = startLoopLag({
      createHistogram: () => fakeHistogram({ 50: 2_000_000, 99: 18_000_000 }, 40_000_000),
      schedule: () => 1,
      cancel: () => {},
    });
    expect(lag.reading()).toBe(null);
  });

  test("converts nanoseconds to milliseconds on each window rotation", () => {
    let rotate: (() => void) | null = null;
    const lag = startLoopLag({
      createHistogram: () => fakeHistogram({ 50: 2_000_000, 99: 18_000_000 }, 40_000_000),
      schedule: (fn) => {
        rotate = fn;
        return 1;
      },
      cancel: () => {},
    });

    rotate!();
    expect(lag.reading()).toEqual({ p50: 2, p99: 18, max: 40 });
  });

  test("keeps the last complete window rather than an average since startup", () => {
    let rotate: (() => void) | null = null;
    const histogram = fakeHistogram({ 50: 1_000_000, 99: 1_000_000 }, 1_000_000);
    const lag = startLoopLag({
      createHistogram: () => histogram,
      schedule: (fn) => {
        rotate = fn;
        return 1;
      },
      cancel: () => {},
    });

    rotate!();
    expect(lag.reading()).toEqual({ p50: 1, p99: 1, max: 1 });

    // A later window, as if a hiccup at startup had long since rotated away.
    // `reset()` is a no-op here, so the histogram is mutated directly to
    // stand in for a fresh window's numbers.
    histogram.percentile = (p) => (p === 50 ? 3_000_000 : 20_000_000);
    histogram.max = 20_000_000;
    rotate!();
    expect(lag.reading()).toEqual({ p50: 3, p99: 20, max: 20 });
  });

  test("reports no reading when the histogram cannot be enabled, rather than throwing", () => {
    const lag = startLoopLag({
      createHistogram: () => {
        throw new Error("monitorEventLoopDelay is not available");
      },
    });
    expect(lag.reading()).toBe(null);
    expect(() => lag.stop()).not.toThrow();
  });

  test("stop cancels the scheduled rotation and disables the histogram", () => {
    let cancelled: unknown = null;
    const histogram = fakeHistogram({ 50: 1, 99: 1 }, 1);
    let disabled = false;
    histogram.disable = () => {
      disabled = true;
      return true;
    };
    const lag = startLoopLag({
      createHistogram: () => histogram,
      schedule: () => "handle",
      cancel: (handle) => {
        cancelled = handle;
      },
    });

    lag.stop();
    expect(cancelled).toBe("handle");
    expect(disabled).toBe(true);
  });
});
