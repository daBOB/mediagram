import { describe, expect, test } from "bun:test";
import {
  bitrateLabel,
  clockTime,
  codecLine,
  countOf,
  endsAt,
  episodeLabel,
  humanDuration,
  humanSize,
  spellCount,
  technicalLine,
} from "../public/lib/format.js";

describe("sizes", () => {
  test("scale to the unit that reads best", () => {
    expect(humanSize(512)).toBe("512 B");
    expect(humanSize(5_872_026)).toBe("5.6 MB");
    expect(humanSize(7_011_563_463)).toBe("6.5 GB");
  });

  test("drop the decimal once the number is big enough not to need it", () => {
    expect(humanSize(11_534_336)).toBe("11 MB");
  });

  test("refuse to invent a size", () => {
    expect(humanSize(Number.NaN)).toBe("");
    expect(humanSize(-1)).toBe("");
  });
});

describe("durations", () => {
  test("read as hours and minutes", () => {
    expect(humanDuration(7342)).toBe("2h 2m");
    expect(humanDuration(360)).toBe("6m");
    expect(humanDuration(7200)).toBe("2h");
  });

  test("never claim zero minutes for something that has a length", () => {
    expect(humanDuration(20)).toBe("1m");
    expect(humanDuration(0)).toBe("");
    expect(humanDuration(null)).toBe("");
  });
});

describe("labels", () => {
  test("an episode carries its season, a lesson does not", () => {
    expect(episodeLabel({ kind: "ep", season: 1, episode: "4" })).toBe("S1E4");
    expect(episodeLabel({ kind: "tut", season: 1, episode: "4" })).toBe("4");
    expect(episodeLabel({ kind: "movie", season: null, episode: null })).toBe("");
  });

  test("the codec line omits what the index does not know", () => {
    expect(codecLine({ container: "mkv", vcodec: "hevc", acodec: "ac3" })).toBe("mkv · hevc · ac3");
    expect(codecLine({ container: "mp4", vcodec: null, acodec: "aac" })).toBe("mp4 · aac");
  });
});

describe("a position on a scrub bar", () => {
  test("under an hour, minutes and seconds", () => {
    expect(clockTime(0)).toBe("0:00");
    expect(clockTime(9)).toBe("0:09");
    expect(clockTime(83)).toBe("1:23");
    expect(clockTime(599)).toBe("9:59");
  });

  test("an hour or more gains an hours field", () => {
    expect(clockTime(3600)).toBe("1:00:00");
    expect(clockTime(5025)).toBe("1:23:45");
  });

  test("seconds are truncated, not rounded", () => {
    // Rounding up shows a position the viewer has not reached yet, which on a
    // running clock reads as the time jumping.
    expect(clockTime(59.9)).toBe("0:59");
  });

  test("nothing sensible still reads as a time", () => {
    expect(clockTime(null)).toBe("0:00");
    expect(clockTime(-5)).toBe("0:00");
    expect(clockTime(Number.NaN)).toBe("0:00");
  });
});

describe("extents", () => {
  test("spell a count while it is short enough to read as a word", () => {
    expect(spellCount(1)).toBe("one");
    expect(spellCount(12)).toBe("twelve");
    expect(spellCount(20)).toBe("twenty");
  });

  test("give up on words once the figure is quicker", () => {
    expect(spellCount(21)).toBe("21");
    expect(spellCount(170)).toBe("170");
  });

  test("refuse to spell something that is not a count", () => {
    expect(spellCount(-1)).toBe("");
    expect(spellCount(1.5)).toBe("");
    expect(spellCount(Number.NaN)).toBe("");
  });

  test("agree with the noun they count", () => {
    expect(countOf(1, "show")).toBe("one show");
    expect(countOf(3, "show")).toBe("three shows");
    expect(countOf(0, "film")).toBe("zero films");
    expect(countOf(170, "lesson")).toBe("170 lessons");
  });
});

describe("when it ends", () => {
  const at = (hour: number, minute: number) => new Date(2026, 8, 18, hour, minute, 0);

  test("adds what is left to the clock", () => {
    // 1h 59m of Blade left at 20:42.
    expect(endsAt(7142, at(20, 42))).toBe("22:41");
    expect(endsAt(0, at(20, 42))).toBe("20:42");
  });

  test("rolls over midnight rather than counting past it", () => {
    expect(endsAt(3600, at(23, 30))).toBe("00:30");
    expect(endsAt(7200, at(23, 10))).toBe("01:10");
  });

  test("pads, so the figures line up with the ones beside them", () => {
    expect(endsAt(60, at(9, 4))).toBe("09:05");
  });

  test("says nothing rather than guessing when the runtime is unknown", () => {
    expect(endsAt(Number.NaN, at(20, 0))).toBe("");
    expect(endsAt(-1, at(20, 0))).toBe("");
    expect(endsAt(Number.POSITIVE_INFINITY, at(20, 0))).toBe("");
  });
});

describe("average bitrate", () => {
  test("is the whole file over its running time", () => {
    // 14.2 GB of Blade Runner 2049 over 2h 44m.
    expect(bitrateLabel({ total: 15_247_000_000, duration: 9_840 })).toBe("12 Mbps");
  });

  test("keeps a decimal while the first one still decides anything", () => {
    expect(bitrateLabel({ total: 1_175_000_000, duration: 1_000 })).toBe("9.4 Mbps");
  });

  test("says nothing rather than NaN when either number is missing", () => {
    expect(bitrateLabel({ total: 1_000_000 })).toBe("");
    expect(bitrateLabel({ duration: 100 })).toBe("");
    expect(bitrateLabel({ total: 1_000_000, duration: 0 })).toBe("");
    expect(bitrateLabel({})).toBe("");
  });
});

describe("the technical line", () => {
  const blade = {
    quality: "1080p",
    hdr: "HDR10",
    container: "mkv",
    vcodec: "hevc",
    acodec: "eac3",
    total: 15_247_000_000,
    partCount: 5,
    duration: 9_840,
  };

  test("says everything the index knows, in reading order", () => {
    expect(technicalLine(blade)).toBe(
      "1080p · HDR10 · mkv · hevc · eac3 · 14 GB · 5 parts · 12 Mbps",
    );
  });

  test("leaves SDR out: it is the absence of a fact, not a fact", () => {
    expect(technicalLine({ ...blade, hdr: "SDR" })).not.toContain("SDR");
    expect(technicalLine({ ...blade, hdr: null })).toBe(technicalLine({ ...blade, hdr: "SDR" }));
  });

  test("says nothing about a single part, which is the ordinary case", () => {
    expect(technicalLine({ ...blade, partCount: 1 })).not.toContain("part");
  });

  test("drops whatever is missing rather than printing a gap", () => {
    expect(technicalLine({ container: "mp4", vcodec: "h264" })).toBe("mp4 · h264");
    expect(technicalLine({})).toBe("");
  });

  test("leaves codecLine alone, which the compact views still use", () => {
    expect(codecLine(blade)).toBe("mkv · hevc · eac3");
  });
});
