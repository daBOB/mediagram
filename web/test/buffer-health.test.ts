/**
 * Covers `buffer-health`: when a link is judged to be falling behind, and the
 * states in which nothing may be judged at all.
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
  test("a buffer draining while hungry is reported once it is running low", () => {
    const health = new BufferHealth();

    // 30 s of buffer losing half a second each second. With twenty seconds
    // still in hand this is indistinguishable from a browser that has paused
    // its own refill, so nothing is said; under ten it is not.
    expect(play(health, { seconds: 20, fill: 0.5, bufferAhead: 30 }).state).toBe("ok");
    expect(play(new BufferHealth(), { seconds: 46, fill: 0.5, bufferAhead: 30 }).state).toBe(
      "behind",
    );
  });

  /**
   * The misfire this rule exists to prevent. Browsers refill in bursts: Chrome
   * playing a file directly holds twenty-odd seconds and lets it sag before
   * topping it up. Each sag reads as a link delivering nothing, and judging
   * those converted perfectly healthy titles — to the floor bitrate.
   */
  test("a browser refilling in bursts is not a slow link", () => {
    for (const [low, high, burst] of [[20, 28, 3], [15, 25, 1.5], [10, 20, 4]] as const) {
      const health = new BufferHealth();
      let at = 0;
      let end = high;
      let fetching = false;
      for (let tick = 1; tick <= 2400; tick++) {
        at += 0.25;
        if (end - at <= low) fetching = true;
        if (end - at >= high) fetching = false;
        if (fetching) end += burst * 0.25;
        const verdict = health.sample({ now: tick * 250, currentTime: at, bufferedEnd: end, paused: false });
        expect(verdict.state).toBe("ok");
      }
    }
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
    const verdict = play(health, { seconds: 46, fill: 0.5, bufferAhead: 30 });

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

describe("whether a sample measured anything", () => {
  test("says no before there is a previous sample to compare against", () => {
    const health = new BufferHealth();
    const first = health.sample({ now: 0, currentTime: 0, bufferedEnd: 10, paused: false });
    expect(first.measured).toBe(false);
  });

  test("says no while paused, when nothing is being consumed", () => {
    const health = new BufferHealth();
    health.sample({ now: 0, currentTime: 0, bufferedEnd: 10, paused: true });
    const next = health.sample({ now: 1000, currentTime: 0, bufferedEnd: 12, paused: true });
    expect(next.measured).toBe(false);
  });

  test("says no once the buffer is full, when the browser has stopped fetching", () => {
    const health = new BufferHealth();
    health.sample({ now: 0, currentTime: 0, bufferedEnd: 60, paused: false });
    const next = health.sample({ now: 1000, currentTime: 1, bufferedEnd: 60, paused: false });
    expect(next.bufferAhead).toBeGreaterThan(45);
    expect(next.measured).toBe(false);
  });

  test("says yes while playing and still hungry, once there is a window to measure across", () => {
    const health = new BufferHealth();
    let last = health.sample({ now: 0, currentTime: 0, bufferedEnd: 10, paused: false });
    for (let tick = 1; tick <= 8; tick++) {
      last = health.sample({ now: tick * 1000, currentTime: tick, bufferedEnd: 10 + tick * 1.5, paused: false });
      // One second of it is the browser's fetch schedule, not the link.
      if (tick === 1) expect(last.measured).toBe(false);
    }
    expect(last.measured).toBe(true);
    expect(last.ratio).toBeCloseTo(1.5);
  });
});

/**
 * Plays out the end of a title: everything is buffered, nothing more is
 * coming, and the playhead eats into what is left.
 *
 * This is what every title does for its last forty seconds, and what used to
 * be read as a link that had died.
 */
function playToTheEnd(
  health: BufferHealth,
  options: { duration: number; from: number; seconds: number; growsTo?: number },
) {
  let verdict = health.sample({
    now: 0,
    currentTime: options.from,
    bufferedEnd: options.duration,
    paused: false,
    duration: options.duration,
  });

  for (let tick = 1; tick <= options.seconds; tick++) {
    // A playlist still being written keeps growing; a finished file does not.
    const duration = options.growsTo ? options.duration + tick : options.duration;
    verdict = health.sample({
      now: tick * 1000,
      currentTime: options.from + tick,
      bufferedEnd: duration,
      paused: false,
      duration,
    });
  }
  return verdict;
}

describe("the end of a title", () => {
  test("is not a link that has died", () => {
    // The buffer cannot grow because there is nothing left to fetch, and the
    // player is genuinely hungry because it cannot hold 45s of a film with 40
    // left. Judging that converted every title in its last forty seconds.
    const health = new BufferHealth();

    const verdict = playToTheEnd(health, { duration: 252, from: 212, seconds: 35 });

    expect(verdict.state).toBe("ok");
  });

  test("and nothing is measured from it", () => {
    // A rate of nought taken from a file that has simply ended describes
    // nothing, and would drag the smoothed rate down for whatever plays next.
    const health = new BufferHealth();

    expect(playToTheEnd(health, { duration: 252, from: 212, seconds: 35 }).measured).toBe(false);
  });

  test("however long it is left there", () => {
    const health = new BufferHealth();

    expect(playToTheEnd(health, { duration: 3539, from: 3490, seconds: 45 }).state).toBe("ok");
  });
});

describe("a playlist still being written", () => {
  test("is still judged, because more really is coming", () => {
    // A buffer at the end of a *growing* duration means the encoder is the
    // bottleneck, which is worth acting on and must not be suppressed.
    const health = new BufferHealth();

    // The duration grows by a second per second, so the player is exactly
    // keeping pace with the encoder and never gets ahead of it.
    const verdict = playToTheEnd(health, {
      duration: 60,
      from: 40,
      seconds: 30,
      growsTo: 90,
    });

    expect(verdict.measured).toBe(true);
  });
});

describe("a duration nothing knows", () => {
  test("is judged the way it always was", () => {
    // `video.duration` is NaN before metadata arrives, and Infinity for a
    // live stream. Neither may switch the end-of-file rule on.
    const health = new BufferHealth();

    for (const duration of [Number.NaN, Number.POSITIVE_INFINITY, undefined]) {
      const starving = new BufferHealth();
      let verdict = starving.sample({
        now: 0,
        currentTime: 0,
        bufferedEnd: 10,
        paused: false,
        duration: duration as never,
      });
      for (let tick = 1; tick <= 20; tick++) {
        verdict = starving.sample({
          now: tick * 1000,
          currentTime: tick,
          bufferedEnd: 10,
          paused: false,
          duration: duration as never,
        });
      }
      expect(verdict.state).not.toBe("ok");
    }
    void health;
  });
});
