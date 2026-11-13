/**
 * ffmpeg's two pipes during a clip export, each drained for as long as it
 * runs: an unread pipe fills and blocks the process.
 */

import { parseProgress } from "./progress";

/** Hands each `-progress` reading of seconds written to `heard`. */
export async function readProgress(stdout: ReadableStream<Uint8Array>, heard: (outSeconds: number) => void): Promise<void> {
  let carry = "";
  const decoder = new TextDecoder();
  try {
    for await (const chunk of stdout) {
      const parsed = parseProgress(decoder.decode(chunk, { stream: true }), carry);
      carry = parsed.carry;
      for (const block of parsed.blocks) if (block.outSeconds !== null) heard(block.outSeconds);
    }
  } catch { /* a lost reading only makes the bar stand still */ }
}

/** Keeps the last `count` lines of stderr in `tail`: for the server's log, never for the client. */
export async function keepTail(stderr: ReadableStream<Uint8Array>, tail: string[], count: number): Promise<void> {
  let rest = "";
  const decoder = new TextDecoder();
  try {
    for await (const chunk of stderr) {
      const lines = (rest + decoder.decode(chunk, { stream: true })).split("\n");
      rest = lines.pop() ?? "";
      tail.push(...lines);
      tail.splice(0, Math.max(0, tail.length - count));
    }
  } catch { /* diagnosis only */ }
}
