/**
 * What to do once the buffer says the link cannot keep up.
 *
 * Deciding is separate from measuring because the two go wrong differently: a
 * measurement is wrong when it misreads a full buffer, a decision is wrong
 * when it restarts playback for a gain nobody would notice. Restarting costs
 * the viewer several seconds of black, so it has to buy more than that.
 */

import { describe, expect, test } from "bun:test";

import { FLOOR_BITS, decideSwitch } from "../public/lib/playback/streaming/adapt-bitrate.js";

const behind = (over: Record<string, unknown> = {}) =>
  decideSwitch({ state: "behind", fitting: 4_000_000, currentCapBits: null, ...over });

describe("when nothing should happen", () => {
  test("a healthy player is left alone", () => {
    expect(behind({ state: "ok" })).toBeNull();
  });

  test("nothing measured means nothing decided", () => {
    expect(behind({ fitting: null })).toBeNull();
  });
});

describe("a title playing directly", () => {
  /**
   * `currentCapBits: null` means the browser is being handed the original
   * file. Any conversion is an improvement, because the original is whatever
   * bitrate it was mastered at and nothing can lower it.
   */
  test("converting is always worth it, at what the link managed", () => {
    expect(behind({ fitting: 4_000_000 })?.targetBits).toBe(4_000_000);
  });

  test("even a small measured capacity is worth converting to", () => {
    expect(behind({ fitting: 900_000 })?.targetBits).toBe(900_000);
  });
});

describe("a conversion that is already running", () => {
  test("a meaningfully lower target restarts it", () => {
    const decision = decideSwitch({
      state: "behind",
      fitting: 3_000_000,
      currentCapBits: 8_000_000,
    });

    expect(decision?.targetBits).toBe(3_000_000);
  });

  test("a target barely below the current one is not worth the interruption", () => {
    // Restarting costs seconds of black for a 6% gain nobody would notice.
    const decision = decideSwitch({
      state: "behind",
      fitting: 7_500_000,
      currentCapBits: 8_000_000,
    });

    expect(decision).toBeNull();
  });

  test("a target above the current one never raises it", () => {
    // Falling behind is not the moment to ask for more.
    const decision = decideSwitch({
      state: "behind",
      fitting: 12_000_000,
      currentCapBits: 8_000_000,
    });

    expect(decision).toBeNull();
  });
});

describe("the bottom of the range", () => {
  test("a measurement below the floor still tries the floor once", () => {
    const decision = decideSwitch({
      state: "starving",
      fitting: 50_000,
      currentCapBits: 3_000_000,
    });

    expect(decision?.targetBits).toBe(FLOOR_BITS);
    expect(decision?.atFloor).toBe(true);
  });

  test("at the floor already, there is nothing left to try", () => {
    // Saying so beats restarting the same encode forever.
    const decision = decideSwitch({
      state: "starving",
      fitting: 50_000,
      currentCapBits: FLOOR_BITS,
    });

    expect(decision).toBeNull();
  });
});
