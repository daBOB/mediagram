/**
 * Which encoder this machine can actually use.
 *
 * `ffmpeg -encoders` lists what was compiled in, not what works: this machine
 * lists `h264_nvenc` and fails to initialise it, because the card is AMD and
 * there is no libcuda. So each candidate is tried for real, once, at startup —
 * discovering it at first play would mean a viewer pressing play and getting
 * nothing.
 */

import { readdirSync } from "node:fs";
import type { Encoder } from "./args";

interface EncoderIo {
  readDirectory(path: string): string[];
  spawn(command: string[]): { exited: Promise<number> };
}

const encoderIo: EncoderIo = {
  readDirectory: (path) => readdirSync(path),
  spawn: (command) => Bun.spawn(command, { stdout: "ignore", stderr: "ignore" }),
};

/** The DRM render nodes this machine has, in a stable order. */
function renderNodes(io: EncoderIo): string[] {
  try {
    return io.readDirectory("/dev/dri")
      .filter((name) => name.startsWith("renderD"))
      .sort()
      .map((name) => `/dev/dri/${name}`);
  } catch {
    return [];
  }
}

/** A one-second encode of a test pattern: enough to prove initialisation. */
async function works(io: EncoderIo, args: string[]): Promise<boolean> {
  const proc = io.spawn(["ffmpeg", "-hide_banner", "-v", "error", ...args]);
  return (await proc.exited) === 0;
}

const TEST_SOURCE = ["-f", "lavfi", "-i", "testsrc=duration=1:size=640x480:rate=10"];

/**
 * The best encoder available, hardware first.
 *
 * Hardware encoding matters less here than it would elsewhere — libx264
 * manages many times realtime on a modern desktop — but it leaves the CPU for
 * everything else the machine is doing.
 */
export async function detectEncoder(io: EncoderIo = encoderIo): Promise<Encoder> {
  // `readdir` rather than a glob: render nodes are character devices, which
  // Bun's Glob does not match, so a glob silently finds nothing and every
  // machine looks like it has no GPU.
  for (const device of renderNodes(io)) {
    const path = device;
    const usable = await works(io, [
      "-vaapi_device",
      path,
      ...TEST_SOURCE,
      "-vf",
      "format=nv12,hwupload",
      "-c:v",
      "h264_vaapi",
      "-f",
      "null",
      "-",
    ]);
    if (usable) return { kind: "vaapi", name: "h264_vaapi", device: path };
  }

  if (await works(io, [...TEST_SOURCE, "-c:v", "libx264", "-f", "null", "-"])) {
    return { kind: "software", name: "libx264" };
  }

  throw new Error(
    "no usable H.264 encoder: ffmpeg has neither a working VAAPI device nor libx264",
  );
}
