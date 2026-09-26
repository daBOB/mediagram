import { describe, expect, test } from "bun:test";
import { cpuPercentSince, cpuTicksFromStat, parseProgress } from "../src/transcode/progress";

describe("parsing ffmpeg's -progress output", () => {
  test("reads speed, fps and out_time_us from one complete block", () => {
    const chunk = ["frame=48", "fps=24.00", "out_time_us=2000000", "speed=1.50x", "progress=continue", ""].join("\n");
    const { blocks, carry } = parseProgress(chunk, "");
    expect(blocks).toEqual([{ speed: 1.5, fps: 24, outSeconds: 2 }]);
    expect(carry).toBe("");
  });

  test("reads every block in a chunk carrying several", () => {
    const chunk =
      ["fps=24.00", "out_time_us=1000000", "speed=1.00x", "progress=continue", "fps=24.00", "out_time_us=3000000", "speed=1.20x", "progress=continue", ""].join(
        "\n",
      );
    const { blocks } = parseProgress(chunk, "");
    expect(blocks).toHaveLength(2);
    expect(blocks[0]?.outSeconds).toBe(1);
    expect(blocks[1]?.outSeconds).toBe(3);
  });

  test("holds a line split across two chunks until it is whole", () => {
    const first = parseProgress("fps=24.00\nout_time_us=200", "");
    expect(first.blocks).toEqual([]);
    const second = parseProgress("0000\nspeed=1.00x\nprogress=continue\n", first.carry);
    expect(second.blocks).toEqual([{ speed: 1, fps: 24, outSeconds: 2 }]);
  });

  test("holds whole lines a chunk boundary landed after, not just a partial one", () => {
    // The boundary falls cleanly between two complete lines, well before the
    // block's terminating `progress=` line.
    const first = parseProgress("fps=24.00\nout_time_us=2000000\n", "");
    expect(first.blocks).toEqual([]);
    const second = parseProgress("speed=1.00x\nprogress=continue\n", first.carry);
    expect(second.blocks).toEqual([{ speed: 1, fps: 24, outSeconds: 2 }]);
  });

  test("reports N/A as no reading rather than NaN", () => {
    const chunk = ["fps=N/A", "out_time_us=N/A", "speed=N/A", "progress=continue", ""].join("\n");
    const { blocks } = parseProgress(chunk, "");
    expect(blocks).toEqual([{ speed: null, fps: null, outSeconds: null }]);
  });

  test("a session that just ended is still a valid block", () => {
    const chunk = ["fps=24.00", "out_time_us=9000000", "speed=1.00x", "progress=end", ""].join("\n");
    const { blocks } = parseProgress(chunk, "");
    expect(blocks[0]?.outSeconds).toBe(9);
  });
});

describe("reading CPU time from /proc/<pid>/stat", () => {
  function statLine(comm: string, utime: number, stime: number): string {
    const fields = ["1", `(${comm})`, "S", "1", "1", "1", "0", "-1", "0", "0", "0", "0", "0", String(utime), String(stime)];
    return `${fields.join(" ")} 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0\n`;
  }

  test("sums utime and stime, in clock ticks", () => {
    expect(cpuTicksFromStat(statLine("ffmpeg", 120, 30))).toBe(150);
  });

  test("parses a process name containing spaces and a closing paren", () => {
    expect(cpuTicksFromStat(statLine("ffmpeg (transcode)", 50, 10))).toBe(60);
  });

  test("reports nothing for a line that is not this layout", () => {
    expect(cpuTicksFromStat("not a stat line")).toBe(null);
  });
});

describe("CPU percent between two readings", () => {
  test("reports nothing until there is a previous reading to compare against", () => {
    expect(cpuPercentSince(null, 100, 1000)).toEqual({ percent: null, next: { ticks: 100, atMs: 1000 } });
  });

  test("computes percent CPU from ticks gained over wall time elapsed", () => {
    // 200 ticks over 2s of wall clock, at 100 ticks/s: 100% CPU.
    const { percent } = cpuPercentSince({ ticks: 100, atMs: 0 }, 300, 2000);
    expect(percent).toBeCloseTo(100);
  });

  test("reports nothing when the process could not be read", () => {
    const { percent, next } = cpuPercentSince({ ticks: 100, atMs: 0 }, null, 2000);
    expect(percent).toBe(null);
    expect(next).toEqual({ ticks: 100, atMs: 0 });
  });
});
