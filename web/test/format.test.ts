import { describe, expect, test } from "bun:test";
import { codecLine, episodeLabel, humanDuration, humanSize } from "../public/lib/format.js";

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
