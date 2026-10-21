/**
 * The player statistics' download rows on the web: what the server says
 * about a title's download, and how the panel words it — the same three rows,
 * in the same words, as the Android overlay.
 */

import { afterAll, beforeAll, describe, expect, test } from "bun:test";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { downloadFactsResponse } from "../src/cache/download-facts";
import { HeldSets, expectedChunks } from "../src/cache/held";
import { CACHE_CHUNK } from "../src/cache/key";
import type { PreloadItem } from "../src/cache/series-preload";
import { RateWindow, type TrafficSnapshot } from "../src/telegram/fetch-traffic";
import { downloadRows, networkLine, nextLine, onDiskLine } from "../public/lib/playback/download-rows.js";
import { emptyIndex } from "./index-fixture";

const OPEN = "01SETOPEN000000000000001";
const NEXT = "01SETNEXT000000000000002";

let root: string;
let db: ReturnType<typeof emptyIndex>;

function addSet(setId: string, len: number) {
  db.run(
    `INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
     VALUES (?, 'ep', 'mp4', ?, 1, 'complete', 1, 3)`,
    [setId, len],
  );
  db.run(
    `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
     VALUES (?, 0, 0, ?, -1001, 900, 900, ?, 'done')`,
    [setId, len, "a".repeat(64)],
  );
}

async function chunks(setId: string, count: number) {
  const dir = join(root, String(CACHE_CHUNK), setId, "0");
  await mkdir(dir, { recursive: true });
  for (let i = 0; i < count; i++) await writeFile(join(dir, String(i)), "x");
}

const idle: TrafficSnapshot = { foregroundBitsPerSecond: 0, foregroundInFlight: 0, backgroundBitsPerSecond: 0, backgroundWaiting: false };

beforeAll(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-download-"));
  db = emptyIndex();
  addSet(OPEN, CACHE_CHUNK * 10);
  addSet(NEXT, CACHE_CHUNK * 4);
  await chunks(OPEN, 4);
  await chunks(NEXT, 1);
});

afterAll(async () => {
  db.close();
  await rm(root, { recursive: true, force: true });
});

describe("the rate", () => {
  test("is what arrived over the last whole seconds, and nothing once nothing arrives", () => {
    let now = 10_000;
    const rate = new RateWindow(() => now);
    for (let second = 0; second < 4; second++) {
      rate.add(CACHE_CHUNK);
      now += 1000;
    }
    expect(rate.bitsPerSecond()).toBe(CACHE_CHUNK * 8);
    now += 5000;
    expect(rate.bitsPerSecond()).toBe(0);
  });
});

describe("GET /api/sets/:id/download", () => {
  const held = () => new HeldSets(root, expectedChunks(db));

  test("says how much of the title is on disk, the traffic, and the episode preloading", async () => {
    const next: PreloadItem = { setId: NEXT, title: "Pilot", locations: [{ span: { idx: 0, off: 0, len: CACHE_CHUNK * 4 }, chatId: -1001, messageId: 900 }] };
    const traffic = { ...idle, backgroundWaiting: true, foregroundInFlight: 2 };
    const response = await downloadFactsResponse(db, { held: held(), preload: { downloading: () => next }, traffic: () => traffic }, OPEN, false);
    expect(response.status).toBe(200);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(JSON.parse(new TextDecoder().decode(response.body as Uint8Array))).toEqual({
      onDiskBytes: CACHE_CHUNK * 4,
      totalBytes: CACHE_CHUNK * 10,
      traffic,
      next: { title: "Pilot", heldBytes: CACHE_CHUNK, totalBytes: CACHE_CHUNK * 4 },
    });
  });

  test("says nothing is preloading when nothing is, and 404s an unknown title", async () => {
    const sources = { held: held(), preload: { downloading: () => null }, traffic: () => idle };
    const body = JSON.parse(new TextDecoder().decode((await downloadFactsResponse(db, sources, OPEN, false)).body as Uint8Array));
    expect(body.next).toBeNull();
    expect((await downloadFactsResponse(db, sources, "01SETUNKNOWN000000000009", false)).status).toBe(404);
  });
});

describe("the rows", () => {
  test("network is the rate and what is in flight, or idle", () => {
    expect(networkLine({ ...idle, foregroundBitsPerSecond: 14_200_000, foregroundInFlight: 2 })).toBe("14 Mbps · 2 in flight");
    expect(networkLine({ ...idle, foregroundBitsPerSecond: 9_400_000 })).toBe("9.4 Mbps");
    expect(networkLine(idle)).toBe("idle");
  });

  test("on disk is held against the whole, rounded down", () => {
    expect(onDiskLine(512 * 1024 * 1024, 1024 * 1024 * 1024)).toBe("512 MB of 1.0 GB · 50%");
    expect(onDiskLine(1024 * 1024 * 1024 - 1, 1024 * 1024 * 1024)).toEndWith("99%");
    expect(onDiskLine(0, 0)).toBeNull();
  });

  test("next says when the preload is waiting for playback, or its rate", () => {
    const next = { title: "Der Sexmarathon", heldBytes: 34, totalBytes: 100 };
    expect(nextLine(next, { ...idle, backgroundWaiting: true, backgroundBitsPerSecond: 3_000_000 })).toBe("Der Sexmarathon · 34% · waiting for playback");
    expect(nextLine(next, { ...idle, backgroundBitsPerSecond: 11_600_000 })).toBe("Der Sexmarathon · 34% · 12 Mbps");
    expect(nextLine(next, idle)).toBe("Der Sexmarathon · 34%");
    expect(nextLine(null, idle)).toBeNull();
  });

  test("there are none before the first answer", () => {
    expect(downloadRows(null)).toEqual([]);
  });
});
