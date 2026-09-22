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
      picture: "everywhere",
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

  /** What the probe in `codec-support.js` actually vouches for. */
  const sdr = (container: string, vcodec: string, quality = "1080p", hdr = "SDR") => ({
    ...set(container, vcodec, "aac"),
    quality,
    hdr,
  });

  test("HEVC is carried across untouched for a browser that said it decodes it", () => {
    const decision = decidePlayback(sdr("mkv", "hevc"), { decodes: ["hevc"] });
    // Still a new box — Matroska — but not a new picture.
    expect(decision.kind).toBe("transcode");
    expect(decision.blocking).toMatchObject({ container: true, video: false });
    expect(decision.picture).toBe("negotiated");
    // Spelled the other way in the index, still the same codec.
    expect(decidePlayback(sdr("mkv", "h265"), { decodes: ["hevc"] }).blocking.video).toBe(false);
  });

  test("but never handed over directly, even in an mp4", () => {
    // The index cannot say whether the file is tagged hvc1 or hev1, and the
    // wrong one is a black picture. Repackaging retags it.
    const decision = decidePlayback(sdr("mp4", "hevc"), { decodes: ["hevc"] });
    expect(decision.kind).toBe("transcode");
    expect(decision.blocking).toMatchObject({ container: true, video: false });
    expect(reasonOf(decision)).toContain("HEVC");
  });

  test("HDR, Dolby Vision and 2160p stay converted: the probe says nothing about them", () => {
    for (const profile of [
      sdr("mkv", "hevc", "1080p", "HDR10"),
      sdr("mkv", "hevc", "1080p", "DV"),
      sdr("mkv", "hevc", "2160p", "SDR"),
      set("mkv", "hevc", "aac"),
    ]) {
      expect(decidePlayback(profile, { decodes: ["hevc"] }).blocking.video).toBe(true);
    }
  });

  test("a browser's list cannot widen the policy past what it negotiates", () => {
    // Nothing on it is known to the policy, so nothing is promised.
    const decision = decidePlayback(set("mp4", "prores", "aac"), { decodes: ["prores"] });
    expect(decision.kind).toBe("transcode");
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
