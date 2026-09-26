import { describe, expect, test } from "bun:test";
import { countingLogger } from "../src/telegram/measured-client";

/** Captures what would have reached the terminal, without touching it. */
function captureConsole<T>(run: () => T): { result: T; lines: string[]; errors: unknown[] } {
  const lines: string[] = [];
  const errors: unknown[] = [];
  const originalLog = console.log;
  const originalError = console.error;
  console.log = (...args: unknown[]) => lines.push(args.join(" "));
  console.error = (...args: unknown[]) => errors.push(args[0]);
  try {
    return { result: run(), lines, errors };
  } finally {
    console.log = originalLog;
    console.error = originalError;
  }
}

describe("the counting logger", () => {
  test("counts a flood wait carried only in the sleeping log line", () => {
    const floodedSeconds: number[] = [];
    const logger = countingLogger({ flood: (seconds) => floodedSeconds.push(seconds) });
    captureConsole(() => logger.info("Sleeping for 12s on flood wait (Caused by upload.GetFile)"));
    expect(floodedSeconds).toEqual([12]);
  });

  test("prints an ordinary line without counting it as a flood", () => {
    let floods = 0;
    const logger = countingLogger({ flood: () => { floods += 1; } });
    const { lines } = captureConsole(() => logger.info("connecting to 149.154.167.51:443/TcpFull"));
    expect(floods).toBe(0);
    expect(lines).toHaveLength(1);
    expect(lines[0]).toContain("connecting to 149.154.167.51:443/TcpFull");
  });

  test("still prints the flood line itself, the same as any other", () => {
    const logger = countingLogger({ flood: () => {} });
    const { lines } = captureConsole(() => logger.info("Sleeping for 3s on flood wait (Caused by X)"));
    expect(lines[0]).toContain("Sleeping for 3s on flood wait");
  });

  test("prints an error's own line to console.error, as the default handler does", () => {
    const logger = countingLogger({ flood: () => {} });
    const failure = new Error("disconnected");
    const { errors } = captureConsole(() => logger.error("session dropped", failure));
    expect(errors).toEqual([failure]);
  });
});
