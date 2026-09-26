import { describe, expect, test } from "bun:test";
import {
  healthWord,
  modeLabel,
  playbackRows,
  startedLine,
  transcodeRows,
} from "../public/lib/status/status-session-lines.js";

describe("mode words", () => {
  test("names every mode the panel can show", () => {
    expect(modeLabel("direct")).toBe("direct");
    expect(modeLabel("copy")).toBe("copy");
    expect(modeLabel("hevc-copy")).toBe("HEVC copy");
    expect(modeLabel("transcode")).toBe("transcode");
  });
});

describe("health words", () => {
  test("says keeping up by default, and by name for the other two", () => {
    expect(healthWord("ok")).toBe("keeping up");
    expect(healthWord("behind")).toBe("falling behind");
    expect(healthWord("starving")).toBe("starving");
  });
});

describe("what has started since this process began", () => {
  test("says none rather than an empty list", () => {
    expect(startedLine({ encode: 0, copy: 0, hevcCopy: 0 })).toBe("none");
  });

  test("counts each mode, singular for one", () => {
    expect(startedLine({ encode: 5, copy: 3, hevcCopy: 2 })).toBe("5 re-encodes, 3 copies, 2 HEVC copies");
    expect(startedLine({ encode: 1, copy: 1, hevcCopy: 1 })).toBe("1 re-encode, 1 copy, 1 HEVC copy");
  });

  test("leaves out a mode nothing has run in", () => {
    expect(startedLine({ encode: 2, copy: 0, hevcCopy: 0 })).toBe("2 re-encodes");
  });
});

describe("the conversion rows", () => {
  test("say the capacity even when nothing is running", () => {
    expect(transcodeRows({ running: 0, capacity: 4, started: { encode: 0, copy: 0, hevcCopy: 0 }, sessions: [] }, "libx264")).toContainEqual([
      "Running",
      "none, of 4 allowed",
    ]);
  });

  test("names a re-encode by the encoder actually in use", () => {
    const rows = transcodeRows(
      {
        running: 1,
        capacity: 4,
        started: { encode: 1, copy: 0, hevcCopy: 0 },
        sessions: [{ mode: "encode", speed: 1.1, segments: 38, cpuPercent: 12, watchers: 1 }],
      },
      "h264_vaapi",
    );
    expect(rows[0]).toEqual(["Running", "1 of 4"]);
    expect(rows[1]).toEqual(["Conversion 1", "re-encode with h264_vaapi · 1.1× realtime · 38 segments · 12% CPU, 1 watching"]);
  });

  test("names a copy by its mode instead, with no encoder in the sentence", () => {
    const rows = transcodeRows(
      {
        running: 1,
        capacity: 4,
        started: { encode: 0, copy: 0, hevcCopy: 1 },
        sessions: [{ mode: "hevc-copy", speed: 14.2, segments: 38, cpuPercent: 12, watchers: 1 }],
      },
      "h264_vaapi",
    );
    expect(rows[1]?.[1]).toBe("HEVC copy · 14.2× realtime · 38 segments · 12% CPU, 1 watching");
  });

  test("leaves out speed and CPU when ffmpeg has not reported them yet", () => {
    const rows = transcodeRows(
      {
        running: 1,
        capacity: 4,
        started: { encode: 1, copy: 0, hevcCopy: 0 },
        sessions: [{ mode: "encode", speed: null, segments: 0, cpuPercent: null, watchers: 1 }],
      },
      "libx264",
    );
    expect(rows[1]?.[1]).toBe("re-encode with libx264 · 0 segments, 1 watching");
  });

  test("reports how many of each mode have run since starting", () => {
    const rows = transcodeRows({ running: 0, capacity: 4, started: { encode: 3, copy: 1, hevcCopy: 0 }, sessions: [] }, "libx264");
    expect(rows).toContainEqual(["Since starting", "3 re-encodes, 1 copy"]);
  });

  test("say what is on disk even when nothing is running", () => {
    const rows = transcodeRows(
      { running: 0, capacity: 4, started: { encode: 0, copy: 0, hevcCopy: 0 }, sessions: [], heldBytes: 7 * 1024 ** 3, dir: "/var/tmp/t" },
      "libx264",
    );
    expect(rows).toContainEqual(["On disk", "7.0 GB"]);
    expect(rows).toContainEqual(["Directory", "/var/tmp/t"]);
  });

  test("say nothing about disk when it could not be measured", () => {
    const rows = transcodeRows({ running: 0, capacity: 4, started: { encode: 0, copy: 0, hevcCopy: 0 }, sessions: [], heldBytes: null }, "libx264");
    expect(rows).toContainEqual(["On disk", null]);
  });
});

const VIEWER = {
  setId: "01SET",
  title: "A Film",
  mode: "direct",
  videoCodec: "h264",
  audioCodec: "aac",
  bitrateBits: 8_200_000,
  ahead: 72,
  health: "ok",
  fillRate: 1.1,
  dropped: 0,
  frames: 41_200,
  paused: false,
  held: false,
  from: "192.168.1.23",
  ageSeconds: 2,
};

describe("watching now", () => {
  test("says nobody when no player is open", () => {
    expect(playbackRows([])).toEqual([["Watching", "nobody"]]);
  });

  test("describes a viewer by codecs, mode, rate, buffer, health and where from", () => {
    const [row] = playbackRows([VIEWER]);
    expect(row?.[0]).toBe("A Film");
    expect(row?.[1]).toBe("h264 / aac · direct · 8.2 Mbps · 1:12 ahead · keeping up · 0 dropped of 41,200 · from 192.168.1.23");
  });

  test("names falling behind and starving in place of keeping up", () => {
    const [row] = playbackRows([{ ...VIEWER, health: "behind" }]);
    expect(row?.[1]).toContain("falling behind");
    const [starving] = playbackRows([{ ...VIEWER, health: "starving" }]);
    expect(starving?.[1]).toContain("starving");
  });

  test("adds paused and cached only when they apply", () => {
    const [row] = playbackRows([{ ...VIEWER, paused: true, held: true }]);
    expect(row?.[1]).toContain("paused");
    expect(row?.[1]).toContain("cached");
    const [plain] = playbackRows([VIEWER]);
    expect(plain?.[1]).not.toContain("paused");
    expect(plain?.[1]).not.toContain("cached");
  });

  test("falls back to the set id when a title was not sent", () => {
    const [row] = playbackRows([{ ...VIEWER, title: "" }]);
    expect(row?.[0]).toBe("01SET");
  });

  test("leaves out an unmeasured bitrate or buffer rather than showing a blank", () => {
    const [row] = playbackRows([{ ...VIEWER, bitrateBits: null, ahead: null }]);
    expect(row?.[1]).not.toContain("Mbps");
    expect(row?.[1]).not.toContain("ahead");
  });
});
