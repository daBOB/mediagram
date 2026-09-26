/**
 * The trailing debounce a local write arms, proved against a fake clock so
 * the delay never has to actually elapse.
 */

import { describe, expect, test } from "bun:test";
import { WriteDebounce, type DebounceClock } from "../src/application/write-debounce";

/** A clock with exactly one timer live at a time, which is all `touch()`
 * ever needs: it always clears the outstanding one before setting a new. */
function fakeClock() {
  let next = 1;
  const pending = new Map<number, () => void>();
  const clock: DebounceClock = {
    setTimeout: (run) => {
      const id = next++;
      pending.set(id, run);
      return id;
    },
    clearTimeout: (timer) => {
      pending.delete(timer as number);
    },
  };
  return {
    clock,
    /** Runs every timer currently armed, as if its delay had elapsed. */
    fire: () => {
      for (const run of pending.values()) run();
      pending.clear();
    },
    live: () => pending.size,
  };
}

describe("WriteDebounce", () => {
  test("fires once, at the delay it was given", () => {
    const { clock, fire } = fakeClock();
    let runs = 0;
    const debounce = new WriteDebounce(() => { runs++; }, 5000, clock);

    debounce.touch();
    expect(runs).toBe(0);
    fire();
    expect(runs).toBe(1);
  });

  test("a burst of writes collapses into one round, timed from the last of them", () => {
    const { clock, fire, live } = fakeClock();
    let runs = 0;
    const debounce = new WriteDebounce(() => { runs++; }, 5000, clock);

    debounce.touch();
    debounce.touch();
    debounce.touch();
    // Three touches, one still-pending timer: each re-arm cancelled the last.
    expect(live()).toBe(1);
    fire();
    expect(runs).toBe(1);
  });

  test("stop cancels a pending round without running it", () => {
    const { clock, fire, live } = fakeClock();
    let runs = 0;
    const debounce = new WriteDebounce(() => { runs++; }, 5000, clock);

    debounce.touch();
    debounce.stop();
    expect(live()).toBe(0);
    fire();
    expect(runs).toBe(0);
  });

  test("stop with nothing pending is a no-op", () => {
    const { clock } = fakeClock();
    expect(() => new WriteDebounce(() => {}, 5000, clock).stop()).not.toThrow();
  });

  test("stop stays stopped: a write arriving after it does not re-arm", () => {
    // The gap this closes: a write landing between `writeDebounce.stop()`
    // and the resources it guarded actually closing must not schedule a
    // round against them mid- or post-close.
    const { clock, fire, live } = fakeClock();
    let runs = 0;
    const debounce = new WriteDebounce(() => { runs++; }, 5000, clock);

    debounce.stop();
    debounce.touch();
    expect(live()).toBe(0);
    fire();
    expect(runs).toBe(0);
  });

  test("stop after a pending touch, then another touch, stays stopped", () => {
    const { clock, fire, live } = fakeClock();
    let runs = 0;
    const debounce = new WriteDebounce(() => { runs++; }, 5000, clock);

    debounce.touch();
    debounce.stop();
    debounce.touch();
    debounce.touch();
    expect(live()).toBe(0);
    fire();
    expect(runs).toBe(0);
  });

  test("touching again after a round fires arms a fresh one", () => {
    const { clock, fire } = fakeClock();
    let runs = 0;
    const debounce = new WriteDebounce(() => { runs++; }, 5000, clock);

    debounce.touch();
    fire();
    expect(runs).toBe(1);

    debounce.touch();
    fire();
    expect(runs).toBe(2);
  });
});
