/**
 * Taking the next episodes into the cache while one plays.
 *
 * What matters: a whole title ends up on disk, nothing already there is
 * fetched again, a viewer who moves on replaces what was still waiting, and
 * the route passes on only episodes — never a film someone named instead.
 */

import { collectRead } from "./support/cache-reader";
import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { CACHE_CHUNK } from "../src/cache/key";
import { CachedReader } from "../src/cache/reader";
import { SeriesPreload, type PreloadItem } from "../src/cache/series-preload";
import { ChunkCache } from "../src/cache/store";
import { createRouter } from "../src/routes";
import type { ByteSource } from "../src/http/stream";
import type { PlayerRequest } from "../src/http/contracts";
import { emptyIndex } from "./index-fixture";

const SET = "01SET0000000000000000001";
let root: string;

beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-preload-"));
});
afterEach(async () => {
  await rm(root, { recursive: true, force: true });
});

/** The bytes of a part, and a record of what was asked of it. */
function upstream(partLength: number) {
  const bytes = new Uint8Array(partLength);
  for (let i = 0; i < partLength; i++) bytes[i] = i % 251;
  const asked: { offset: number; length: number }[] = [];
  return {
    bytes,
    asked,
    fetch: async (offset: number, length: number) => {
      asked.push({ offset, length });
      return bytes.subarray(offset, Math.min(offset + length, partLength));
    },
  };
}

describe("CachedReader.fill", () => {
  test("takes a whole part, after which reading it asks upstream for nothing", async () => {
    const part = upstream(CACHE_CHUNK * 3 + 100);
    const reader = new CachedReader(new ChunkCache(root, 100_000_000), 4);

    await reader.fill(SET, 0, part.bytes.length, part.fetch);
    const asked = part.asked.length;
    const whole = await collectRead(reader, { setId: SET, partIdx: 0, start: 0, length: part.bytes.length, partLength: part.bytes.length, fetch: part.fetch });

    expect(whole).toEqual(part.bytes);
    expect(asked).toBeGreaterThan(0);
    expect(part.asked.length).toBe(asked);
  });

  test("fetches only the chunks that are missing", async () => {
    const part = upstream(CACHE_CHUNK * 4);
    const reader = new CachedReader(new ChunkCache(root, 100_000_000));
    await collectRead(reader, { setId: SET, partIdx: 0, start: 0, length: CACHE_CHUNK, partLength: part.bytes.length, fetch: part.fetch });
    part.asked.length = 0;

    await reader.fill(SET, 0, part.bytes.length, part.fetch);

    expect(part.asked).toEqual([{ offset: CACHE_CHUNK, length: CACHE_CHUNK * 3 }]);
  });
});

/** A preload over fake parts, recording which sets were filled in what order. */
function preloadWith(held: Set<string> = new Set(), failing: Set<string> = new Set()) {
  const filled: string[] = [];
  const became: string[] = [];
  let release: (() => void) | null = null;
  let gate: Promise<void> | null = null;
  const preload = new SeriesPreload({
    fill: async (setId, partIdx) => {
      if (gate) await gate;
      if (failing.has(setId)) throw new Error("FLOOD_WAIT");
      filled.push(`${setId}/${partIdx}`);
    },
    fetcherFor: () => async () => new Uint8Array(),
    isHeld: async (setId) => held.has(setId),
    onHeld: (setId) => { became.push(setId); },
  });
  return {
    preload,
    filled,
    became,
    /** Holds every fill until `open` is called. */
    hold: () => {
      gate = new Promise((resolve) => (release = resolve));
    },
    open: () => {
      gate = null;
      release?.();
    },
  };
}

function item(setId: string, parts = 1): PreloadItem {
  return {
    setId,
    title: setId,
    locations: Array.from({ length: parts }, (_, idx) => ({
      span: { idx, off: 0, len: CACHE_CHUNK },
      chatId: -1001,
      messageId: 900 + idx,
    })),
  };
}

describe("SeriesPreload", () => {
  for (const [description, rejection, message] of [
    ["null", null, "null"],
    ["undefined", undefined, "undefined"],
    ["string", "offline", "offline"],
    ["unprintable object", { toString() { throw new Error("cannot print"); } }, "unprintable rejection"],
  ] as const) {
    test(`${description} rejection is reported and the next queued title is still filled`, async () => {
      const filled: string[] = [];
      const messages: string[] = [];
      const preload = new SeriesPreload({
        isHeld: async (id) => { if (id === "bad") throw rejection; return false; },
        fill: async (id) => { filled.push(id); },
        fetcherFor: () => async () => new Uint8Array(),
        log: (message) => messages.push(message),
      });
      preload.want([item("bad"), item("good")]);
      await preload.settle();
      expect(messages).toContain(`preload: bad stopped: ${message}`);
      expect(filled).toEqual(["good"]);
      preload.want([item("later")]);
      await preload.settle();
      expect(filled).toEqual(["good", "later"]);
      await preload.stop();
    });
  }

  test("fills every part of each set in turn, and says when each is held", async () => {
    const run = preloadWith();
    run.preload.want([item("E2", 2), item("E3")]);
    await run.preload.settle();

    expect(run.filled).toEqual(["E2/0", "E2/1", "E3/0"]);
    expect(run.became).toEqual(["E2", "E3"]);
  });

  test("skips a set already on disk", async () => {
    const run = preloadWith(new Set(["E2"]));
    run.preload.want([item("E2"), item("E3")]);
    await run.preload.settle();

    expect(run.filled).toEqual(["E3/0"]);
  });

  test("a new request replaces what was waiting, and lets the current one finish", async () => {
    const run = preloadWith();
    run.hold();
    run.preload.want([item("E2"), item("E3")]);
    // The viewer moved on to E3 while E2 was downloading.
    run.preload.want([item("E4"), item("E5")]);
    run.open();
    await run.preload.settle();

    expect(run.filled).toEqual(["E2/0", "E4/0", "E5/0"]);
  });

  test("a set that fails does not stop the ones after it", async () => {
    const run = preloadWith(new Set(), new Set(["E2"]));
    run.preload.want([item("E2"), item("E3")]);
    await run.preload.settle();

    expect(run.filled).toEqual(["E3/0"]);
    expect(run.became).toEqual(["E3"]);
  });

  test("a request after the queue ran dry starts it again", async () => {
    const run = preloadWith();
    run.preload.want([item("E2")]);
    await run.preload.settle();
    run.preload.want([item("E3")]);
    await run.preload.settle();

    expect(run.filled).toEqual(["E2/0", "E3/0"]);
  });
});

describe("POST /api/preload", () => {
  const NO_BYTES: ByteSource = { stream: () => new ReadableStream() } as never;

  function catalog() {
    const db = emptyIndex();
    const add = (setId: string, kind: string) => {
      db.run(
        `INSERT INTO sets(set_id, kind, title, container, total, part_count, status, created_at, spec_version)
         VALUES (?, ?, ?, 'mkv', ?, 1, 'complete', 1, 3)`,
        [setId, kind, `title ${setId}`, CACHE_CHUNK],
      );
      db.run(
        `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
         VALUES (?, 0, 0, ?, -1001, 901, 901, ?, 'done')`,
        [setId, CACHE_CHUNK, "a".repeat(64)],
      );
    };
    add("01EP00000000000000000002", "ep");
    add("01EP00000000000000000003", "ep");
    add("01EP00000000000000000004", "ep");
    add("01MOVIE00000000000000001", "movie");
    return db;
  }

  function post(body: unknown): PlayerRequest {
    return { method: "POST", path: "/api/preload", range: null, body: JSON.stringify(body), contentType: "application/json" };
  }

  test.each([
    { origin: "https://unrelated.example", contentType: "application/json", status: 403 },
    { origin: "not an origin", contentType: "application/json", status: 403 },
    { origin: "http://127.0.0.1:8770", contentType: "text/plain", status: 415 },
    { origin: null, contentType: null, status: 415 },
  ])("refuses unsafe browser writes before changing the preload queue: %j", async (request) => {
    const asked: PreloadItem[][] = [];
    const preload = { want: (items: PreloadItem[]) => asked.push(items) } as never;
    const db = catalog();
    try {
      const route = createRouter({ db, source: NO_BYTES, preload });
      const response = await route({ ...post({ setIds: [] }), ...request, host: "127.0.0.1:8770" });
      expect(response.status).toBe(request.status);
      expect(asked).toEqual([]);
    } finally {
      db.close();
    }
  });

  test("accepts a same-origin JSON write", async () => {
    const asked: PreloadItem[][] = [];
    const preload = { want: (items: PreloadItem[]) => asked.push(items) } as never;
    const db = catalog();
    try {
      const route = createRouter({ db, source: NO_BYTES, preload });
      const response = await route({
        ...post({ setIds: ["01EP00000000000000000002"] }),
        origin: "http://127.0.0.1:8770",
        host: "127.0.0.1:8770",
      });
      expect(response.status).toBe(202);
      expect(asked[0]?.map((item) => item.setId)).toEqual(["01EP00000000000000000002"]);
    } finally {
      db.close();
    }
  });

  test("passes on the episodes named, as many as two, and nothing else", async () => {
    const asked: PreloadItem[][] = [];
    const preload = { want: (items: PreloadItem[]) => asked.push(items) } as never;
    const route = createRouter({ db: catalog(), source: NO_BYTES, preload });

    const onlyEpisodes = await route(post({ setIds: ["01MOVIE00000000000000001", "nope"] }));
    const capped = await route(
      post({
        setIds: ["01EP00000000000000000002", "01EP00000000000000000003", "01EP00000000000000000004"],
      }),
    );

    expect(onlyEpisodes.status).toBe(202);
    expect(capped.status).toBe(202);
    expect(asked[0]!.map((i) => i.setId)).toEqual([]);
    expect(asked[1]!.map((i) => i.setId)).toEqual(["01EP00000000000000000002", "01EP00000000000000000003"]);
    expect(asked[1]![0]!.locations).toHaveLength(1);
  });

  test("is not a route when preload is off", async () => {
    const route = createRouter({ db: catalog(), source: NO_BYTES });
    const res = await route(post({ setIds: ["01EP00000000000000000002"] }));
    expect(res.status).toBe(404);
  });

  test("answers a read with 405", async () => {
    const preload = { want: () => {} } as never;
    const route = createRouter({ db: catalog(), source: NO_BYTES, preload });
    const res = await route({ method: "GET", path: "/api/preload", range: null });
    expect(res.status).toBe(405);
  });
});
