/** Covers `playback-report.js`: what one open player tells the server, and the loop that sends it. */

import { describe, expect, test } from "bun:test";
import { playbackFields, playbackMeta, playbackReport, reportPlayback } from "../public/lib/playback/playback-report.js";

function fakeVideo(over = {}) {
  return {
    readyState: 4,
    currentTime: 10,
    paused: false,
    buffered: { length: 1, start: () => 0, end: () => 40 },
    getVideoPlaybackQuality: () => ({ droppedVideoFrames: 2, totalVideoFrames: 1000 }),
    ...over,
  };
}

const watch = { fillRate: () => 1.1, health: () => "ok" };

describe("reading the video element's own state", () => {
  test("carries the readout fields and the watch's own readings", () => {
    const fields = playbackFields(fakeVideo(), { starved: false, waitingToStart: null, held: false, watch });
    expect(fields).toMatchObject({ readyState: 4, ahead: 30, starved: false, fillRate: 1.1, health: "ok", dropped: 2, frames: 1000, paused: false, held: false });
  });

  test("says it is awaiting a buffered start only in that mode", () => {
    const held = playbackFields(fakeVideo(), { starved: false, waitingToStart: { mode: "buffered" }, held: false, watch });
    expect(held.awaitingStart).toBe(true);
    const asap = playbackFields(fakeVideo(), { starved: false, waitingToStart: { mode: "asap" }, held: false, watch });
    expect(asap.awaitingStart).toBe(false);
  });

  test("tolerates an element with no getVideoPlaybackQuality at all", () => {
    const fields = playbackFields(fakeVideo({ getVideoPlaybackQuality: undefined }), {
      starved: false,
      waitingToStart: null,
      held: false,
      watch,
    });
    expect(fields.dropped).toBeUndefined();
  });
});

describe("what this title is", () => {
  const set = { setId: "01SET", vcodec: "h264", acodec: "aac", total: 1_000_000, duration: 100 };

  test("is direct when nothing is converting", () => {
    expect(playbackMeta(set, { converting: false, copiedOutput: false, capBits: null }).mode).toBe("direct");
  });

  test("is transcode when converting without a copy", () => {
    expect(playbackMeta(set, { converting: true, copiedOutput: false, capBits: null }).mode).toBe("transcode");
  });

  test("is copy for a non-HEVC picture carried across", () => {
    expect(playbackMeta(set, { converting: true, copiedOutput: true, capBits: null }).mode).toBe("copy");
  });

  test("is hevc-copy when the source picture is HEVC", () => {
    const hevc = { ...set, vcodec: "hevc" };
    expect(playbackMeta(hevc, { converting: true, copiedOutput: true, capBits: null }).mode).toBe("hevc-copy");
    const h265 = { ...set, vcodec: "h265" };
    expect(playbackMeta(h265, { converting: true, copiedOutput: true, capBits: null }).mode).toBe("hevc-copy");
  });

  test("bitrate is the cap while one is active, the source rate otherwise", () => {
    expect(playbackMeta(set, { converting: false, copiedOutput: false, capBits: null }).bitrateBits).toBe(80_000);
    expect(playbackMeta(set, { converting: true, copiedOutput: false, capBits: 3_000_000 }).bitrateBits).toBe(3_000_000);
  });

  test("handles no set at all, which is the state between titles", () => {
    expect(playbackMeta(null, { converting: false, copiedOutput: false, capBits: null })).toMatchObject({
      setId: "",
      mode: "direct",
      videoCodec: null,
      audioCodec: null,
    });
  });
});

const RAW = {
  viewer: "v1",
  setId: "01SET",
  title: "A Film",
  mode: "direct",
  videoCodec: "h264",
  audioCodec: "aac",
  bitrateBits: 8_000_000,
  ahead: 30,
  health: "ok",
  fillRate: 1.1,
  dropped: 2,
  frames: 1000,
  paused: false,
  held: false,
};

describe("building the POST body", () => {
  test("passes a well-formed reading straight through", () => {
    expect(playbackReport(RAW)).toEqual(RAW);
  });

  test("clamps an oversized title and codec name rather than sending them whole", () => {
    const report = playbackReport({ ...RAW, title: "x".repeat(500), videoCodec: "y".repeat(50) });
    expect(report.title).toHaveLength(200);
    expect(report.videoCodec).toHaveLength(16);
  });

  test("turns a non-finite or negative number into null", () => {
    const report = playbackReport({ ...RAW, bitrateBits: Number.NaN, dropped: -1 });
    expect(report.bitrateBits).toBe(null);
    expect(report.dropped).toBe(null);
  });

  test("coerces paused and held to real booleans", () => {
    const report = playbackReport({ ...RAW, paused: undefined, held: 1 });
    expect(report.paused).toBe(false);
    expect(report.held).toBe(false);
  });
});

describe("the reporting loop", () => {
  function loopFixture() {
    const posted: unknown[] = [];
    let status = 204;
    const timers: Array<{ fn: () => void; ms: number }> = [];
    return {
      posted,
      setStatus: (s: number) => { status = s; },
      schedule: (fn: () => void, ms: number) => {
        timers.push({ fn, ms });
        return timers.length - 1;
      },
      cancel: (handle: number) => { timers[handle] = null as never; },
      fireNext: () => timers.filter(Boolean).forEach((t) => t.fn()),
      post: async (body: unknown) => {
        posted.push(body);
        return { status } as Response;
      },
    };
  }

  test("reports immediately, without waiting for the first interval", async () => {
    const fixture = loopFixture();
    reportPlayback({ read: () => RAW, post: fixture.post, schedule: fixture.schedule, cancel: fixture.cancel });
    await Promise.resolve();
    expect(fixture.posted).toHaveLength(1);
  });

  test("reports again on each scheduled tick", async () => {
    const fixture = loopFixture();
    reportPlayback({ read: () => RAW, post: fixture.post, schedule: fixture.schedule, cancel: fixture.cancel });
    await Promise.resolve();
    fixture.fireNext();
    await Promise.resolve();
    expect(fixture.posted.length).toBeGreaterThanOrEqual(2);
  });

  test("stops after the server says this page will never be accepted", async () => {
    const fixture = loopFixture();
    fixture.setStatus(404);
    reportPlayback({ read: () => RAW, post: fixture.post, schedule: fixture.schedule, cancel: fixture.cancel });
    await Promise.resolve();
    const postedAfterFirst = fixture.posted.length;
    fixture.fireNext();
    await Promise.resolve();
    expect(fixture.posted.length).toBe(postedAfterFirst);
  });

  test("keeps reporting through a network error, until the next tick", async () => {
    const fixture = loopFixture();
    let failNext = true;
    const flaky = async (body: unknown) => {
      if (failNext) {
        failNext = false;
        throw new Error("offline");
      }
      return fixture.post(body);
    };
    reportPlayback({ read: () => RAW, post: flaky, schedule: fixture.schedule, cancel: fixture.cancel });
    await Promise.resolve();
    fixture.fireNext();
    await Promise.resolve();
    expect(fixture.posted).toHaveLength(1);
  });

  test("stop() ends the loop and cancels the scheduled tick", async () => {
    const fixture = loopFixture();
    const stop = reportPlayback({ read: () => RAW, post: fixture.post, schedule: fixture.schedule, cancel: fixture.cancel });
    await Promise.resolve();
    stop();
    fixture.fireNext();
    await Promise.resolve();
    expect(fixture.posted).toHaveLength(1);
  });

  test("stop() is safe to call twice", async () => {
    const fixture = loopFixture();
    const stop = reportPlayback({ read: () => RAW, post: fixture.post, schedule: fixture.schedule, cancel: fixture.cancel });
    await Promise.resolve();
    expect(() => { stop(); stop(); }).not.toThrow();
  });
});
