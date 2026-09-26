/**
 * A file reference expiring mid-download used to end playback outright — the
 * documented trade of fetching `partMedia` fresh every time. Now that answer
 * is cached (`telegram/media-cache.ts`), a stale one is expected occasionally
 * and is worth one retry rather than a failed stream.
 */

import { describe, expect, test } from "bun:test";
import { partFetcher, TelegramSource } from "../src/telegram/source";
import type { PartLocation } from "../src/catalog";
import type { Step } from "../src/range";

/** Shaped like teleproto's RPCError: `.errorMessage` carries the server string. */
function fileReferenceExpired(): Error & { errorMessage: string } {
  return Object.assign(new Error("FILE_REFERENCE_EXPIRED"), { errorMessage: "FILE_REFERENCE_EXPIRED" });
}

async function drain(stream: ReadableStream<Uint8Array>): Promise<Uint8Array> {
  const chunks: Uint8Array[] = [];
  let length = 0;
  for await (const chunk of stream as unknown as AsyncIterable<Uint8Array>) {
    chunks.push(chunk);
    length += chunk.length;
  }
  const out = new Uint8Array(length);
  let at = 0;
  for (const chunk of chunks) { out.set(chunk, at); at += chunk.length; }
  return out;
}

const LOCATIONS: PartLocation[] = [{ span: { idx: 0, off: 0, len: 10 }, chatId: 1, messageId: 42 }];
const STEP: Step = { partIdx: 0, offset: 0, headDrop: 0, take: 10 };

describe("the uncached streaming path", () => {
  test("a reference that expires before any byte is sent is retried once", async () => {
    let mediaFetches = 0;
    let forgotten = 0;
    let attempt = 0;
    const telegram = {
      partMedia: async () => { mediaFetches += 1; return {} as never; },
      forgetPartMedia: () => { forgotten += 1; },
      client: {
        iterDownload: (async function* () {
          attempt += 1;
          if (attempt === 1) throw fileReferenceExpired();
          yield new Uint8Array(10).fill(9);
        }) as never,
      },
    };

    const source = new TelegramSource(telegram as never);
    const got = await drain(source.stream(LOCATIONS, [STEP], "SET"));

    expect(got).toEqual(new Uint8Array(10).fill(9));
    expect(mediaFetches).toBe(2);
    expect(forgotten).toBe(1);
  });

  test("a second expiry in a row is not retried again", async () => {
    const telegram = {
      partMedia: async () => ({}) as never,
      forgetPartMedia: () => {},
      client: { iterDownload: (async function* () { throw fileReferenceExpired(); }) as never },
    };

    const source = new TelegramSource(telegram as never);
    await expect(drain(source.stream(LOCATIONS, [STEP], "SET"))).rejects.toThrow("FILE_REFERENCE_EXPIRED");
  });

  test("an expiry after bytes were already sent is not retried", async () => {
    let attempts = 0;
    const telegram = {
      partMedia: async () => ({}) as never,
      forgetPartMedia: () => {},
      client: {
        iterDownload: (async function* () {
          attempts += 1;
          yield new Uint8Array(5).fill(1);
          throw fileReferenceExpired();
        }) as never,
      },
    };

    const source = new TelegramSource(telegram as never);
    await expect(drain(source.stream(LOCATIONS, [STEP], "SET"))).rejects.toThrow("FILE_REFERENCE_EXPIRED");
    expect(attempts).toBe(1);
  });

  test("an unrelated failure is never retried", async () => {
    const telegram = {
      partMedia: async () => ({}) as never,
      forgetPartMedia: () => {},
      client: { iterDownload: (async function* () { throw new Error("boom"); }) as never },
    };

    const source = new TelegramSource(telegram as never);
    await expect(drain(source.stream(LOCATIONS, [STEP], "SET"))).rejects.toThrow("boom");
  });
});

describe("the cached path's fetcher", () => {
  test("a reference that expires is dropped and refetched once", async () => {
    let mediaFetches = 0;
    let forgotten = 0;
    let attempt = 0;
    const telegram = {
      partMedia: async () => { mediaFetches += 1; return {} as never; },
      forgetPartMedia: () => { forgotten += 1; },
      client: {
        iterDownload: (async function* () {
          attempt += 1;
          if (attempt === 1) throw fileReferenceExpired();
          yield new Uint8Array(10).fill(3);
        }) as never,
      },
    };

    const fetch = partFetcher(telegram as never, 42);
    const got = await fetch(0, 10);

    expect(got).toEqual(new Uint8Array(10).fill(3));
    expect(mediaFetches).toBe(2);
    expect(forgotten).toBe(1);
  });

  test("a non-expiry failure surfaces without a retry", async () => {
    const telegram = {
      partMedia: async () => ({}) as never,
      forgetPartMedia: () => {},
      client: { iterDownload: (async function* () { throw new Error("boom"); }) as never },
    };

    const fetch = partFetcher(telegram as never, 42);
    await expect(fetch(0, 10)).rejects.toThrow("boom");
  });
});
