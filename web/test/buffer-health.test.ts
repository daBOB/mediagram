/**
 * Noticing that the download cannot keep up, before the stalls start.
 *
 * The signal is the buffer: how many seconds of video are ready ahead of the
 * playhead, and whether that number is growing or shrinking. Shrinking while
 * playing means every second of watching costs more than a second of
 * downloading, and the only question left is when it runs out.
 *
 * The trap this module exists to avoid: **a full buffer looks exactly like a
 * slow download.** A browser that has buffered as much as it wants stops
 * fetching, so the buffer stops growing — identical, from outside, to a link
 * that cannot keep up. Judging that as "falling behind" would convert every
 * title that was playing perfectly. So nothing is judged unless the player is
 * still hungry.
 */

import { describe, expect, test } from "bun:test";

import { BufferHealth } from "../public/lib/buffer-health.js";

/**
 * Plays for `seconds` of wall clock with the buffer gaining `fill` seconds of
 * video per second.
 *
 * A player cannot play past its own buffer, so when the buffer empties the
 * playhead stops with it — which is the stall this whole module exists to see
 * coming, and leaving it out would let the tests describe something no
 * browser does.
 */
function play(
  health: BufferHealth,
  options: { seconds: number; fill: number; bufferAhead?: number },
) {
  let at = 0;
  let ahead = options.bufferAhead ?? 10;
  let verdict = health.sample({ now: 0, currentTime: at, bufferedEnd: at + ahead, paused: false });

  for (let tick = 1; tick <= options.seconds; tick++) {
    const playable = Math.min(1, ahead);
    at += playable;
    ahead = Math.max(0, ahead + options.fill - playable);
    verdict = health.sample({
      now: tick * 1000,
      currentTime: at,
      bufferedEnd: at + ahead,
      paused: false,
    });
  }
  return verdict;
}

describe("a link that keeps up", () => {
  test("a buffer growing faster than playback is healthy", () => {
    const health = new BufferHealth();

    expect(play(health, { seconds: 20, fill: 1.4 }).state).toBe("ok");
  });

  test("a buffer holding steady is healthy", () => {
    // Exactly realtime: not comfortable, but not losing either.
    const health = new BufferHealth();

    expect(play(health, { seconds: 20, fill: 1.0 }).state).toBe("ok");
  });

  /**
   * The case that must not be misread. A browser stops fetching once it has
   * buffered enough, so a satisfied player looks like a starving one.
   */
  test("a full buffer that has stopped growing is not falling behind", () => {
    const health = new BufferHealth();

    // 120 s of buffer, gaining nothing, because there is nothing to gain.
    const verdict = play(health, { seconds: 30, fill: 0, bufferAhead: 120 });

    expect(verdict.state).toBe("ok");
  });
});

describe("a link that cannot keep up", () => {
  test("a buffer draining while hungry is reported", () => {
    const health = new BufferHealth();

    // 30 s of buffer losing half a second each second: still 20 s in hand
    // after twenty seconds, so this is the early warning, not the emergency.
    const verdict = play(health, { seconds: 20, fill: 0.5, bufferAhead: 30 });

    expect(verdict.state).toBe("behind");
  });

  test("it says how much of realtime the link is managing", () => {
    const health = new BufferHealth();

    const verdict = play(health, { seconds: 20, fill: 0.5, bufferAhead: 30 });

    expect(verdict.ratio).toBeGreaterThan(0.4);
    expect(verdict.ratio).toBeLessThan(0.6);
  });

  test("a brief dip is not enough to call it", () => {
    // Two seconds of a slow patch happens on any link; converting the title
    // over it would be worse than the dip.
    const health = new BufferHealth();

    expect(play(health, { seconds: 2, fill: 0.2, bufferAhead: 30 }).state).toBe("ok");
  });

  test("an empty buffer is urgent rather than merely behind", () => {
    const health = new BufferHealth();

    const verdict = play(health, { seconds: 10, fill: 0.5, bufferAhead: 9 });

    expect(verdict.state).toBe("starving");
  });
});

describe("a player that has already stalled", () => {
  /**
   * The case a live run found and the synthetic ones missed. Once the buffer
   * is empty the playhead advances only as fast as bytes arrive, so a rate
   * measured against *playback* reads exactly 1.0 — "keeping up" — while the
   * viewer watches a spinner. Measuring against the wall clock instead is the
   * only formulation true in both regimes.
   */
  test("a stalled player is not reported as keeping up", () => {
    const health = new BufferHealth();

    // Buffer already empty, gaining a third of realtime: a spinner every few
    // seconds, and playback creeping forward in between.
    const verdict = play(health, { seconds: 30, fill: 0.33, bufferAhead: 0 });

    expect(verdict.state).toBe("starving");
  });

  test("the rate it reports is what the link delivers, not what plays", () => {
    const health = new BufferHealth();

    play(health, { seconds: 30, fill: 0.33, bufferAhead: 0 });

    expect(health.ratio).toBeGreaterThan(0.2);
    expect(health.ratio).toBeLessThan(0.5);
  });
});

describe("what must not be measured", () => {
  test("a paused player consumes nothing, so nothing is concluded", () => {
    const health = new BufferHealth();
    for (let tick = 0; tick <= 30; tick++) {
      health.sample({ now: tick * 1000, currentTime: 100, bufferedEnd: 105, paused: true });
    }

    expect(health.sample({ now: 31_000, currentTime: 100, bufferedEnd: 105, paused: true }).state)
      .toBe("ok");
  });

  test("a seek starts the measurement again", () => {
    // The buffer after a seek is a different buffer; comparing across the
    // jump would read the discontinuity as a collapse.
    const health = new BufferHealth();
    play(health, { seconds: 20, fill: 0.5, bufferAhead: 30 });

    const afterSeek = health.sample({
      now: 21_000,
      currentTime: 4000,
      bufferedEnd: 4002,
      paused: false,
    });

    expect(afterSeek.state).toBe("ok");
  });

  test("a switch of source starts it again too", () => {
    const health = new BufferHealth();
    play(health, { seconds: 20, fill: 0.5, bufferAhead: 30 });

    health.reset();

    expect(
      health.sample({ now: 22_000, currentTime: 0, bufferedEnd: 5, paused: false }).state,
    ).toBe("ok");
  });
});

describe("what to do about it", () => {
  test("a bitrate that would have fitted is derived from the measurement", () => {
    const health = new BufferHealth();
    const verdict = play(health, { seconds: 20, fill: 0.5, bufferAhead: 30 });

    // Half of realtime on a 13.9 Mbit/s source is a link carrying about
    // 7 Mbit/s; the target leaves room under it rather than aiming at it.
    const target = health.fittingBitrate(13_900_000);

    expect(target).toBeGreaterThan(4_000_000);
    expect(target).toBeLessThan(7_000_000);
    expect(verdict.state).toBe("behind");
  });

  test("with nothing measured there is nothing to suggest", () => {
    expect(new BufferHealth().fittingBitrate(13_900_000)).toBeNull();
  });
});
