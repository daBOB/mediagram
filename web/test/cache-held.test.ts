import { afterAll, beforeAll, describe, expect, test } from "bun:test";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { HeldSets, expectedChunks } from "../src/cache/held";
import { CACHE_CHUNK } from "../src/cache/key";
import { emptyIndex } from "./index-fixture";

const WHOLE = "01SETWHOLE00000000000001";
const HALF = "01SETHALF000000000000002";
const NONE = "01SETNONE000000000000003";
const PENDING = "01SETPEND000000000000004";

let root: string;
let db: ReturnType<typeof emptyIndex>;

/** Writes `count` chunk files for one part, as the cache names them. */
async function chunks(setId: string, partIdx: number, count: number) {
  const dir = join(root, String(CACHE_CHUNK), setId, String(partIdx));
  await mkdir(dir, { recursive: true });
  for (let i = 0; i < count; i++) await writeFile(join(dir, String(i)), "x");
}

function addSet(setId: string, parts: Array<{ len: number; status?: string }>) {
  db.run(
    `INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
     VALUES (?, 'movie', 'mp4', ?, ?, 'complete', 1, 3)`,
    [setId, parts.reduce((t, p) => t + p.len, 0), parts.length],
  );
  parts.forEach((part, idx) => {
    db.run(
      `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
       VALUES (?, ?, 0, ?, -1001, ?, ?, ?, ?)`,
      [setId, idx, part.len, 900 + idx, 900 + idx, "a".repeat(64), part.status ?? "done"],
    );
  });
}

beforeAll(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-held-"));
  db = emptyIndex();
  // Two parts, deliberately not chunk multiples: the last chunk of a part is
  // legitimately short and still counts as one.
  addSet(WHOLE, [{ len: CACHE_CHUNK * 2 + 10 }, { len: CACHE_CHUNK - 1 }]);
  addSet(HALF, [{ len: CACHE_CHUNK * 4 }]);
  addSet(NONE, [{ len: CACHE_CHUNK }]);
  addSet(PENDING, [{ len: CACHE_CHUNK, status: "pending" }]);

  await chunks(WHOLE, 0, 3);
  await chunks(WHOLE, 1, 1);
  await chunks(HALF, 0, 2);
});

afterAll(async () => {
  db.close();
  await rm(root, { recursive: true, force: true });
});

describe("what a set needs", () => {
  test("rounds a short last chunk up, because it is still a chunk", () => {
    const expected = expectedChunks(db);
    // 2 chunks and 10 bytes is 3; a byte short of one chunk is still 1.
    expect(expected.get(WHOLE)).toBe(4);
    expect(expected.get(HALF)).toBe(4);
  });

  test("ignores parts that are not uploaded, which cannot be cached either", () => {
    expect(expectedChunks(db).has(PENDING)).toBe(false);
  });
});

describe("which sets are held", () => {
  test("counts a set with every chunk on disk as held", async () => {
    const held = new HeldSets(root, expectedChunks(db));
    await held.refresh();
    expect(held.has(WHOLE)).toBe(true);
    expect(held.count).toBe(1);
  });

  test("does not count a set that is only partly there", async () => {
    const held = new HeldSets(root, expectedChunks(db));
    await held.refresh();
    // Two chunks of four is exactly the case the badge must not claim.
    expect(held.has(HALF)).toBe(false);
  });

  test("does not count a set with nothing cached", async () => {
    const held = new HeldSets(root, expectedChunks(db));
    await held.refresh();
    expect(held.has(NONE)).toBe(false);
  });

  test("answers false before the first scan rather than throwing", () => {
    const held = new HeldSets(root, expectedChunks(db));
    expect(held.has(WHOLE)).toBe(false);
  });

  test("answers nothing held when the cache directory is missing", async () => {
    const held = new HeldSets(join(root, "gone"), expectedChunks(db));
    await held.refresh();
    expect(held.count).toBe(0);
  });
});

describe("rescanning", () => {
  test("keeps the reading until the interval has passed", async () => {
    let clock = 0;
    const held = new HeldSets(root, expectedChunks(db), () => clock);
    await held.refresh();

    // Everything arrives for HALF, but the reading is still fresh.
    await chunks(HALF, 0, 4);
    clock = 10_000;
    held.refreshIfStale();
    await held.refresh();
    expect(held.has(HALF)).toBe(true);
  });

  test("shares one scan between callers rather than starting several", async () => {
    const held = new HeldSets(root, expectedChunks(db));
    const [a, b] = [held.refresh(), held.refresh()];
    expect(a).toBe(b);
    await a;
  });
});
