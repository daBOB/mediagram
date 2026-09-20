/**
 * Which titles a browser can play as they are.
 *
 * The library holds two profiles: mp4/h264/aac for the 162 course lessons,
 * which every browser plays, and mkv/hevc/ac3 for films, which none will.
 * Deciding this from the catalog means the page can say so before the viewer
 * clicks, rather than showing a black rectangle.
 */

import { describe, expect, test } from "bun:test";
import { conversionNote, decidePlayback } from "../public/lib/playable.js";

const set = (container: string, vcodec: string | null, acodec: string | null) => ({
  container,
  vcodec,
  acodec,
});

/** Narrows the union so a test can assert on the reason it was refused. */
function reasonOf(decision: ReturnType<typeof decidePlayback>): string {
  if (decision.kind !== "transcode") throw new Error("expected a transcode decision");
  return decision.reason;
}

describe("direct play", () => {
  test("accepts the course profile: mp4, h264, aac", () => {
    expect(decidePlayback(set("mp4", "h264", "aac"))).toEqual({
      kind: "direct",
      blocking: { container: false, video: false, audio: false, bitrate: false },
    });
  });

  test("accepts codec strings Telegram and ffprobe spell differently", () => {
    expect(decidePlayback(set("mp4", "avc1", "mp4a")).kind).toBe("direct");
    expect(decidePlayback(set("m4v", "h264", "aac")).kind).toBe("direct");
    expect(decidePlayback(set("webm", "vp9", "opus")).kind).toBe("direct");
  });
});

describe("what browsers refuse", () => {
  test("Matroska, whatever is inside it", () => {
    const decision = decidePlayback(set("mkv", "h264", "aac"));
    expect(decision).toMatchObject({ kind: "transcode" });
    expect(reasonOf(decision)).toContain("Matroska");
  });

  test("AC3 and its relatives, which no browser decodes", () => {
    for (const codec of ["ac3", "eac3", "dts", "truehd"]) {
      const decision = decidePlayback(set("mp4", "h264", codec));
      expect(decision.kind).toBe("transcode");
      expect(reasonOf(decision).toLowerCase()).toContain(codec);
    }
  });

  test("HEVC, which is patchy enough not to promise", () => {
    const decision = decidePlayback(set("mp4", "hevc", "aac"));
    expect(decision.kind).toBe("transcode");
    expect(reasonOf(decision)).toContain("HEVC");
  });

  /** The films in this library: every reason at once. */
  test("the film profile is refused and says why", () => {
    const decision = decidePlayback(set("mkv", "hevc", "ac3"));
    expect(decision.kind).toBe("transcode");
    expect(reasonOf(decision).length).toBeGreaterThan(0);
  });
});

describe("what the index does not say", () => {
  test("an unknown codec is not promised as playable", () => {
    expect(decidePlayback(set("mp4", null, null)).kind).toBe("transcode");
    expect(decidePlayback(set("avi", "mpeg4", "mp3")).kind).toBe("transcode");
  });
});

describe("playing over a link that has to carry it", () => {
  /** An mp4 a browser would play directly, at ~14 Mbit/s. */
  const film = {
    container: "mp4",
    vcodec: "h264",
    acodec: "aac",
    total: 12_800_000_000,
    duration: 7_200,
  };

  test("on the local network a big file is still played as it is", () => {
    expect(decidePlayback(film, { remote: false, maxBitrate: 8_000_000 }).kind).toBe("direct");
  });

  test("from outside, a file that will not fit the uplink is converted", () => {
    const decision = decidePlayback(film, { remote: true, maxBitrate: 8_000_000 });

    expect(decision.kind).toBe("transcode");
    if (decision.kind !== "transcode") throw new Error("unreachable");
    expect(decision.reason).toMatch(/connection|uplink|Mbit/i);
  });

  test("from outside, a file that does fit is still played as it is", () => {
    const lesson = { ...film, total: 500_000_000, duration: 1_800 };

    expect(decidePlayback(lesson, { remote: true, maxBitrate: 8_000_000 }).kind).toBe("direct");
  });

  test("a set with no duration cannot be measured, so it is not refused for size", () => {
    const unknown = { ...film, duration: null };

    expect(decidePlayback(unknown, { remote: true, maxBitrate: 8_000_000 }).kind).toBe("direct");
  });

  test("said nothing about the link, the decision is the codec one", () => {
    expect(decidePlayback(film).kind).toBe("direct");
  });
});

describe("which reason is at fault", () => {
  test("a Matroska h264 blames the container and nothing else", () => {
    // The 146 sets in this library that are h264 in an mkv: the picture is
    // already what a browser wants and only the box around it is wrong.
    expect(decidePlayback(set("mkv", "h264", "aac")).blocking).toEqual({
      container: true,
      video: false,
      audio: false,
      bitrate: false,
    });
  });

  test("HEVC blames the video, whatever else is wrong with it", () => {
    expect(decidePlayback(set("mkv", "hevc", "ac3")).blocking).toMatchObject({
      video: true,
      container: true,
      audio: true,
    });
  });

  test("a bitrate over the link blames the bitrate", () => {
    const heavy = { ...set("mp4", "h264", "aac"), total: 20e9, duration: 3600 };
    const decision = decidePlayback(heavy, { remote: true, maxBitrate: 8e6 });
    expect(decision.blocking).toMatchObject({ bitrate: true, video: false });
  });

  test("and on the LAN the same file blames nothing", () => {
    const heavy = { ...set("mp4", "h264", "aac"), total: 20e9, duration: 3600 };
    expect(decidePlayback(heavy, { remote: false, maxBitrate: 8e6 }).kind).toBe("direct");
  });
});

describe("what the page says while it waits", () => {
  test("an encode is a conversion, and says so", () => {
    expect(conversionNote("Matroska container, HEVC video.")).toBe(
      "Converting as you watch: Matroska container, HEVC video.",
    );
  });

  test("a copy is not, and says that instead", () => {
    // The two take wildly different amounts of time, and one of them does not
    // touch the picture at all. Calling both "converting" told a viewer to
    // expect the slow one.
    expect(conversionNote("Matroska container.", true)).toBe(
      "Repackaging as you watch: Matroska container. The picture is untouched.",
    );
  });

  test("a title that needs nothing has nothing to explain", () => {
    expect(conversionNote(null)).toBeNull();
    expect(conversionNote("", true)).toBeNull();
  });
});
