/**
 * Fetching a part's bytes from Telegram.
 *
 * The interesting property is not the first request but the second: anything
 * larger than one request size is downloaded in several, and the client
 * advances the offset itself. It does that with big-integer arithmetic, so a
 * native `bigint` passed in looks right, serves the first request, and then
 * throws — which presents as a video that stops after half a megabyte.
 */

import { describe, expect, test } from "bun:test";

import { partFetcher } from "../src/telegram/source";

/** What teleproto does to the offset it is given, and nothing else. */
interface BigIntish {
  add(other: number): BigIntish;
  toJSNumber(): number;
}

/**
 * A client that downloads the way teleproto does: one request at a time,
 * advancing the offset with `.add` between them.
 */
function fakeTelegram(bytes: Uint8Array) {
  const asked: { offset: number; requestSize: number }[] = [];
  const client = {
    async *iterDownload(_media: unknown, options: { offset: unknown; requestSize: number }) {
      let offset = options.offset as BigIntish;
      for (;;) {
        const at = offset.toJSNumber();
        asked.push({ offset: at, requestSize: options.requestSize });
        const piece = bytes.subarray(at, Math.min(at + options.requestSize, bytes.length));
        if (piece.length === 0) return;
        yield piece;
        offset = offset.add(piece.length);
      }
    },
  };
  return { asked, telegram: { partMedia: async () => ({}), client } };
}

function pattern(length: number): Uint8Array {
  const bytes = new Uint8Array(length);
  for (let i = 0; i < length; i++) bytes[i] = i % 251;
  return bytes;
}

describe("fetching a range of a part", () => {
  test("a range larger than one request is downloaded in several", async () => {
    const bytes = pattern(3_000_000);
    const fake = fakeTelegram(bytes);

    const got = await partFetcher(fake.telegram as never, 1)(0, 2_000_000);

    expect(got.length).toBe(2_000_000);
    expect(got).toEqual(bytes.subarray(0, 2_000_000));
    expect(fake.asked.length).toBeGreaterThan(1);
  });

  test("a range that starts partway through a part starts there", async () => {
    const bytes = pattern(3_000_000);
    const fake = fakeTelegram(bytes);

    const got = await partFetcher(fake.telegram as never, 1)(1_048_576, 1_000_000);

    expect(got).toEqual(bytes.subarray(1_048_576, 2_048_576));
    expect(fake.asked[0]?.offset).toBe(1_048_576);
  });

  test("a range running past the end of the part returns what there is", async () => {
    const bytes = pattern(1_500_000);
    const fake = fakeTelegram(bytes);

    const got = await partFetcher(fake.telegram as never, 1)(1_000_000, 1_000_000);

    expect(got).toEqual(bytes.subarray(1_000_000));
  });
});
