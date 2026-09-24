import { describe, expect, test } from "bun:test";
import { FfmpegRunner } from "../src/transcode/ffmpeg";
import { detectEncoder } from "../src/transcode/encoders";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

const OPTIONS = {
  encoder: { kind: "software", name: "libx264" } as const,
  baseUrl: "http://127.0.0.1:8770",
  segmentSeconds: 4,
};
const SPEC = { setId: "01SET /?", seekSeconds: 600, maxrateBits: 3_000_000, audioTrack: 2, copyVideo: false };

/** Controls raw process IO; argument building, logging, and supervision run unchanged. */
function processFixture() {
  const exit = deferred<number>();
  const logged = deferred<void>();
  const killed = deferred<void>();
  let stderr!: ReadableStreamDefaultController<Uint8Array>;
  let streamClosed = false;
  const signals: string[] = [];
  const commands: string[][] = [];
  const logs: Array<{ path: string; text: string }> = [];
  const timers: Array<{ milliseconds: number; fire: () => void; cancelled: boolean }> = [];
  let onSignal = (_signal: string) => {};
  const io = {
    spawn(command: string[]) {
      commands.push(command);
      return {
        stderr: new ReadableStream<Uint8Array>({ start(controller) { stderr = controller; } }),
        exited: exit.promise,
        kill(signal: "SIGTERM" | "SIGKILL") {
          signals.push(signal);
          onSignal(signal);
          if (signal === "SIGKILL") killed.resolve();
        },
      };
    },
    appendLog(path: string, text: string) {
      logs.push({ path, text });
      if (text.includes("done")) logged.resolve();
    },
    schedule(fire: () => void, milliseconds: number) {
      const timer = { milliseconds, fire, cancelled: false };
      timers.push(timer);
      return () => { timer.cancelled = true; };
    },
  };
  return {
    io, signals, commands, logs, timers, logged: logged.promise, killed: killed.promise,
    onSignal(handler: (signal: string) => void) { onSignal = handler; },
    write(bytes: Uint8Array) { stderr.enqueue(bytes); },
    finish(code = 0) {
      exit.resolve(code);
      if (!streamClosed) { stderr.close(); streamClosed = true; }
    },
  };
}

function valueOf(command: string[], flag: string): string | undefined {
  return command[command.indexOf(flag) + 1];
}

describe("the production ffmpeg runner", () => {
  test("the session spec reaches the actual command, including HEVC copy and audio selection", async () => {
    const fixture = processFixture();
    const runner = new FfmpegRunner(OPTIONS, fixture.io);
    const running = runner.start("session", "/catalog/session", { ...SPEC, copyVideo: true, hevcCopy: true });
    const command = fixture.commands[0]!;
    expect(command[0]).toBe("ffmpeg");
    expect(valueOf(command, "-i")).toBe("http://127.0.0.1:8770/api/sets/01SET%20%2F%3F/stream");
    expect(valueOf(command, "-ss")).toBe("600");
    expect(command).toContain("0:a:2");
    expect(valueOf(command, "-c:v")).toBe("copy");
    expect(valueOf(command, "-tag:v")).toBe("hvc1");
    expect(valueOf(command, "-hls_segment_type")).toBe("fmp4");
    expect(valueOf(command, "-hls_time")).toBe("4");
    expect(command.at(-1)).toBe("/catalog/session/index.m3u8");
    fixture.finish(7);
    expect(await running.exited).toBe(7);
  });

  test("stderr is decoded across chunk boundaries and written while the process is still running", async () => {
    const fixture = processFixture();
    const running = new FfmpegRunner(OPTIONS, fixture.io).start("session", "/catalog/session", SPEC);
    const bytes = new TextEncoder().encode("frame=é done\n");
    const split = bytes.indexOf(0xc3) + 1;
    fixture.write(bytes.subarray(0, split));
    fixture.write(bytes.subarray(split));
    await fixture.logged;
    expect(fixture.logs.map((entry) => entry.text).join("")).toBe("frame=é done\n");
    expect(fixture.logs.every((entry) => entry.path === "/catalog/session/ffmpeg.log")).toBe(true);
    fixture.finish();
    expect(await running.exited).toBe(0);
  });

  test("a graceful stop sends only SIGTERM and cancels its escalation timer", async () => {
    const fixture = processFixture();
    fixture.onSignal((signal) => { if (signal === "SIGTERM") fixture.finish(); });
    const running = new FfmpegRunner(OPTIONS, fixture.io).start("session", "/catalog/session", SPEC);

    await running.stop();

    expect(fixture.signals).toEqual(["SIGTERM"]);
    expect(fixture.timers).toMatchObject([{ milliseconds: 3000, cancelled: true }]);
  });

  test("forced termination waits for the child exit before stop returns", async () => {
    const fixture = processFixture();
    const running = new FfmpegRunner(OPTIONS, fixture.io).start("session", "/catalog/session", SPEC);
    let finished = false;
    const stopping = running.stop().then(() => { finished = true; });
    try {
      expect(fixture.signals).toEqual(["SIGTERM"]);
      fixture.timers[0]!.fire();
      await fixture.killed;
      await new Promise<void>((resolve) => setImmediate(resolve));
      expect(finished).toBe(false);
      expect(fixture.signals).toEqual(["SIGTERM", "SIGKILL"]);
    } finally {
      fixture.finish(137);
      await stopping;
    }
    expect(await running.exited).toBe(137);
    expect(fixture.timers[0]!.cancelled).toBe(true);
  });

  test("a failed spawn is reported to the caller", () => {
    const fixture = processFixture();
    fixture.io.spawn = () => { throw new Error("ffmpeg could not start"); };
    expect(() => new FfmpegRunner(OPTIONS, fixture.io).start("session", "/catalog/session", SPEC)).toThrow(/could not start/);
  });
});

function encoderFixture(nodes: string[] | Error, codes: number[]) {
  const commands: string[][] = [];
  const reads: string[] = [];
  return {
    commands, reads,
    io: {
      readDirectory(path: string) {
        reads.push(path);
        if (nodes instanceof Error) throw nodes;
        return nodes;
      },
      spawn(command: string[]) {
        commands.push(command);
        if (!codes.length) throw new Error("an unexpected extra probe");
        return { exited: Promise.resolve(codes.shift()!) };
      },
    },
  };
}

describe("encoder detection through the production probes", () => {
  test("render nodes are filtered and tried in order until hardware initializes", async () => {
    const fixture = encoderFixture(["renderD130", "card0", "renderD128", "renderD129"], [1, 0]);

    expect(await detectEncoder(fixture.io)).toEqual({ kind: "vaapi", name: "h264_vaapi", device: "/dev/dri/renderD129" });

    expect(fixture.reads).toEqual(["/dev/dri"]);
    expect(fixture.commands.map((command) => valueOf(command, "-vaapi_device"))).toEqual(["/dev/dri/renderD128", "/dev/dri/renderD129"]);
    for (const command of fixture.commands) {
      expect(command.slice(0, 4)).toEqual(["ffmpeg", "-hide_banner", "-v", "error"]);
      expect(valueOf(command, "-i")).toBe("testsrc=duration=1:size=640x480:rate=10");
      expect(valueOf(command, "-vf")).toBe("format=nv12,hwupload");
      expect(valueOf(command, "-c:v")).toBe("h264_vaapi");
      expect(command.slice(-3)).toEqual(["-f", "null", "-"]);
    }
  });

  test("compiled hardware that fails initialization falls back to a proven software encoder", async () => {
    const fixture = encoderFixture(["renderD128"], [1, 0]);
    expect(await detectEncoder(fixture.io)).toEqual({ kind: "software", name: "libx264" });
    expect(fixture.commands.map((command) => valueOf(command, "-c:v"))).toEqual(["h264_vaapi", "libx264"]);
  });

  test("a missing device directory still probes software", async () => {
    const fixture = encoderFixture(new Error("ENOENT"), [0]);
    expect(await detectEncoder(fixture.io)).toEqual({ kind: "software", name: "libx264" });
    expect(fixture.commands).toHaveLength(1);
    expect(valueOf(fixture.commands[0]!, "-c:v")).toBe("libx264");
  });

  test("no successful encode produces the actionable startup failure", async () => {
    const fixture = encoderFixture(["renderD128"], [1, 1]);
    await expect(detectEncoder(fixture.io)).rejects.toThrow(/neither a working VAAPI device nor libx264/);
    expect(fixture.commands).toHaveLength(2);
  });

  test("a process that cannot be started propagates its cause", async () => {
    const fixture = encoderFixture([], []);
    fixture.io.spawn = () => { throw new Error("ffmpeg ENOENT"); };
    await expect(detectEncoder(fixture.io)).rejects.toThrow("ffmpeg ENOENT");
  });
});
