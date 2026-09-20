/** Covers `video-copy`: when a conversion may carry the picture across. */

import { describe, expect, test } from "bun:test";
import { canCopyVideo } from "../src/transcode/video-copy";

const set = (container: string, vcodec: string, acodec: string, extra = {}) => ({
  container,
  vcodec,
  acodec,
  total: 1_000_000_000,
  duration: 3600,
  ...extra,
});

describe("the picture is fine and the box is not", () => {
  test("Matroska h264 copies — the largest case in this library", () => {
    expect(canCopyVideo(set("mkv", "h264", "aac"))).toBe(true);
  });

  test("an AC3 track is a reason to re-encode sound, not picture", () => {
    expect(canCopyVideo(set("mkv", "h264", "ac3"))).toBe(true);
    expect(canCopyVideo(set("mp4", "h264", "dts"))).toBe(true);
  });

  test("the codec names ffprobe and Telegram spell differently", () => {
    expect(canCopyVideo(set("mkv", "avc1", "ac3"))).toBe(true);
  });

  test("an mp4 that needs nothing at all could still be copied", () => {
    // Which is what switching audio language on a directly-played title asks
    // for: a new audio stream beside the video it already had.
    expect(canCopyVideo(set("mp4", "h264", "aac"))).toBe(true);
  });
});

describe("the picture is the problem", () => {
  test("HEVC is re-encoded, container notwithstanding", () => {
    expect(canCopyVideo(set("mkv", "hevc", "aac"))).toBe(false);
    expect(canCopyVideo(set("mp4", "hevc", "aac"))).toBe(false);
  });

  test("so is anything this player has never heard of", () => {
    for (const codec of ["vc1", "mpeg2video", "mpeg4", "", "prores"]) {
      expect(canCopyVideo(set("mkv", codec, "aac"))).toBe(false);
    }
  });
});

describe("the link cannot carry the original", () => {
  const heavy = set("mkv", "h264", "ac3", { total: 20e9, duration: 3600 });

  test("a capped remote viewer gets an encode, not a copy", () => {
    // The cap exists because the connection measurably could not carry it.
    // Copying ignores the only thing the cap was for.
    expect(canCopyVideo(heavy, { remote: true, maxBitrate: 8e6 })).toBe(false);
  });

  test("the same file on the LAN copies", () => {
    expect(canCopyVideo(heavy, { remote: false, maxBitrate: 8e6 })).toBe(true);
  });

  test("a set nobody can measure is not assumed to be small", () => {
    const unmeasurable = set("mkv", "h264", "ac3", { duration: null });
    // Unmeasurable means the bitrate rule cannot fire, so the copy is allowed
    // on the same reasoning that lets `decidePlayback` offer direct play.
    expect(canCopyVideo(unmeasurable, { remote: true, maxBitrate: 8e6 })).toBe(true);
  });
});

describe("the page said the link is falling behind", () => {
  test("an explicit cap refuses the copy, whatever the file is", () => {
    // The page only asks for a ceiling after watching playback stall, which
    // is better evidence than the index's average bitrate: an average says
    // nothing about the scene that caused it.
    expect(canCopyVideo(set("mkv", "h264", "ac3"), { capAsked: true })).toBe(false);
    expect(canCopyVideo(set("mp4", "h264", "aac"), { capAsked: true })).toBe(false);
  });

  test("and without one the same file copies", () => {
    expect(canCopyVideo(set("mkv", "h264", "ac3"), { capAsked: false })).toBe(true);
    expect(canCopyVideo(set("mkv", "h264", "ac3"), {})).toBe(true);
  });
});
