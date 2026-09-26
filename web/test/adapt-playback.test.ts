/**
 * The loop that watches a playing video and converts down when it must.
 *
 * Two timings matter and they are not the same. A newly attached source needs
 * a moment for its buffer to establish before anything is concluded — but
 * only a moment, because every second of that is a second of the stalling the
 * viewer is already suffering. Between switches the wait is much longer:
 * restarting costs seconds of black, and two restarts chasing each other cost
 * more than the problem.
 */

import { describe, expect, test } from "bun:test";

import { sourceBitrate, watchPlayback } from "../public/lib/playback/streaming/adapt-playback.js";

/** A video element as far as this module is concerned. */
function fakeVideo() {
  const listeners: Array<() => void> = [];
  // Absolute, not relative to the playhead: a buffer expressed as "seconds
  // ahead" never drains, because the playhead moving takes the end with it.
  let bufferedEnd = 0;
  const element = {
    readyState: 4,
    currentTime: 0,
    paused: false,
    buffered: { length: 1, end: () => bufferedEnd },
    addEventListener: (_name: string, fn: () => void) => void listeners.push(fn),
    removeEventListener: () => {},
  };

  return {
    element,
    /** Plays `seconds`, having buffered `fill` seconds of video per second. */
    tick(seconds: number, fill: number) {
      // A player cannot play past its own buffer.
      element.currentTime += Math.max(0, Math.min(seconds, bufferedEnd - element.currentTime));
      bufferedEnd += fill * seconds;
      for (const fire of listeners) fire();
    },
    set ahead(seconds: number) {
      bufferedEnd = element.currentTime + seconds;
    },
  };
}

function run(options: { fill: number; seconds: number; capBits: number | null }) {
  const video = fakeVideo();
  const switches: number[] = [];
  let exhausted = 0;
  let clock = 0;

  const watch = watchPlayback({
    video: video.element as never,
    // What the player does: a switch restarts the conversion, and the watch
    // is told what it is now capped at. Without that it would keep judging
    // against the cap that has already been replaced.
    onSwitch: (bits: number) => {
      switches.push(bits);
      video.ahead = 30;
      watch.begin({ capBits: bits, sourceBits: 13_900_000 });
    },
    onExhausted: () => void (exhausted += 1),
    now: () => clock,
  });
  video.ahead = 30;
  watch.begin({ capBits: options.capBits, sourceBits: 13_900_000 });

  for (let tick = 0; tick < options.seconds; tick++) {
    clock += 1000;
    video.tick(1, options.fill);
  }
  return { switches, exhausted, watch };
}

describe("a link that keeps up", () => {
  test("nothing is switched", () => {
    expect(run({ fill: 1.3, seconds: 120, capBits: null }).switches).toEqual([]);
  });
});

describe("a link that does not", () => {
  test("a direct play is converted, at a rate the link managed", () => {
    const { switches } = run({ fill: 0.5, seconds: 60, capBits: null });

    expect(switches.length).toBeGreaterThanOrEqual(1);
    // Half of realtime on a 13.9 Mbit/s source, with headroom under it.
    expect(switches[0]).toBeGreaterThan(3_000_000);
    expect(switches[0]).toBeLessThan(7_000_000);
  });

  /**
   * The first switch should not wait out the between-switches cooldown. Every
   * second of that is a second of the stalling the viewer already has.
   */
  test("the first switch comes within a few seconds, not a cooldown later", () => {
    const video = fakeVideo();
    const switches: number[] = [];
    let clock = 0;
    const watch = watchPlayback({
      video: video.element as never,
      onSwitch: (bits: number) => void switches.push(bits),
      onExhausted: () => {},
      now: () => clock,
    });
    // Already low: a viewer this close to a stall should not wait out a
    // cooldown for the first switch.
    video.ahead = 8;
    watch.begin({ capBits: null, sourceBits: 13_900_000 });

    for (let tick = 0; tick < 15 && switches.length === 0; tick++) {
      clock += 1000;
      video.tick(1, 0.4);
    }

    expect(switches).toHaveLength(1);
    expect(clock).toBeLessThanOrEqual(15_000);
  });

  test("a second switch waits much longer than the first", () => {
    // 120 s at a fill that never recovers: a loop without a cooldown would
    // restart the encode on every one of them.
    const { switches } = run({ fill: 0.3, seconds: 120, capBits: 8_000_000 });

    expect(switches.length).toBeGreaterThanOrEqual(1);
    expect(switches.length).toBeLessThanOrEqual(4);
  });

  test("each switch asks for less than the one before", () => {
    const { switches } = run({ fill: 0.3, seconds: 120, capBits: 8_000_000 });

    for (let at = 1; at < switches.length; at++) {
      expect(switches[at]!).toBeLessThan(switches[at - 1]!);
    }
  });
});

describe("nothing left to try", () => {
  test("at the floor and still losing, it says so once and stops", () => {
    const { switches, exhausted } = run({ fill: 0.2, seconds: 300, capBits: 600_000 });

    expect(switches).toEqual([]);
    expect(exhausted).toBe(1);
  });
});

describe("health(), for a report that cannot see the decision itself", () => {
  test("starts ok, before anything has been judged", () => {
    const { watch } = run({ fill: 1.3, seconds: 0, capBits: null });
    expect(watch.health()).toBe("ok");
  });

  test("stays ok while the link keeps up", () => {
    const { watch } = run({ fill: 1.3, seconds: 60, capBits: null });
    expect(watch.health()).toBe("ok");
  });

  test("reports the verdict's own state once a switch has been asked for", () => {
    const { watch, switches } = run({ fill: 0.3, seconds: 120, capBits: 8_000_000 });
    expect(switches.length).toBeGreaterThan(0);
    expect(["behind", "starving", "ok"]).toContain(watch.health());
  });

  test("resets to ok on the next begin, not carried over from the last title", () => {
    const video = fakeVideo();
    let clock = 0;
    let healthAtSwitch: string | null = null;
    const watch = watchPlayback({
      video: video.element as never,
      // Read inline: `fakeVideo` fires every registered listener for every
      // event name it does not distinguish, so a state read after `tick()`
      // returns can already reflect a second, redundant call `begin` itself
      // never made — a fixture quirk, not something a real `timeupdate`,
      // `progress` and `waiting` firing at genuinely different times has.
      onSwitch: () => { healthAtSwitch = watch.health(); },
      onExhausted: () => {},
      now: () => clock,
    });
    // The same setup as "the first switch comes within a few seconds": a
    // buffer already close to a stall, which the watch judges as not ok.
    video.ahead = 8;
    watch.begin({ capBits: null, sourceBits: 13_900_000 });
    for (let tick = 0; tick < 15 && healthAtSwitch === null; tick++) {
      clock += 1000;
      video.tick(1, 0.4);
    }
    expect(healthAtSwitch).not.toBe("ok");

    watch.begin({ capBits: null, sourceBits: null });
    expect(watch.health()).toBe("ok");
  });
});

describe("what the original demands", () => {
  test("bitrate comes from size over duration", () => {
    expect(sourceBitrate({ total: 1_000_000, duration: 8 })).toBe(1_000_000);
  });

  test("a set that cannot be measured has no bitrate", () => {
    expect(sourceBitrate({ total: 1_000_000, duration: 0 })).toBeNull();
    expect(sourceBitrate({ total: 1_000_000, duration: null })).toBeNull();
    expect(sourceBitrate({})).toBeNull();
  });
});
