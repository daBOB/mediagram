/**
 * `SubtitleBundles`: the memory map, the held-titles disk store, integrity,
 * and the reconcile that keeps held sets' bundles on disk.
 */

import { createHash } from "node:crypto";
import { gunzipSync, gzipSync } from "node:zlib";
import { chmod, mkdir, mkdtemp, readFile, readdir, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Database } from "bun:sqlite";
import { beforeEach, describe, expect, spyOn, test } from "bun:test";
import { SubtitleBundles, heldSubtitlesDir } from "../src/catalog/subtitle-bundles";
import type { BundleRef } from "../src/catalog/subtitle-tracks";
import { emptyIndex } from "./index-fixture";
import { deferred } from "./application-fixture";

type Fetcher = (messageId: number, offset: number, length: number) => Promise<Uint8Array>;

function bundleJson(): string {
  return JSON.stringify({
    v: 1, set: "01SET",
    tracks: [{ lang: "de", forced: false, sdh: false, label: "German", source: "embedded", codec: "subrip", vtt: "WEBVTT\n\nhallo" }],
  });
}
const GZ = gzipSync(Buffer.from(bundleJson()));

function refFor(gz: Buffer, over: Partial<BundleRef> = {}): BundleRef {
  return { messageId: 5, bytes: gz.byteLength, sha256: createHash("sha256").update(gz).digest("hex"), ...over };
}

/** Bundles never written to disk unless a test asks: `hold`/`reconcile` do, `vtt` never does. */
async function nothingHeld(root: string) {
  await expect(readdir(heldSubtitlesDir(root))).rejects.toThrow();
}

let root: string;
let heldDir: string;
beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-subs-"));
  heldDir = heldSubtitlesDir(root);
});

function bundles(fetch: Fetcher): SubtitleBundles {
  return new SubtitleBundles({ fetch, background: fetch, heldDir });
}

describe("vtt", () => {
  test("a miss fetches, validates and returns the track", async () => {
    let calls = 0;
    const sb = bundles(async () => { calls++; return GZ; });

    expect(await sb.vtt(refFor(GZ), 0)).toBe("WEBVTT\n\nhallo");
    expect(calls).toBe(1);
  });

  test("a memory hit needs no second fetch", async () => {
    let calls = 0;
    const ref = refFor(GZ);
    const sb = bundles(async () => { calls++; return GZ; });

    await sb.vtt(ref, 0);
    await sb.vtt(ref, 0);
    expect(calls).toBe(1);
  });

  test("concurrent misses share one fetch", async () => {
    let calls = 0;
    const ref = refFor(GZ);
    const gate = deferred<void>();
    const sb = bundles(async () => { calls++; await gate.promise; return GZ; });

    const both = Promise.all([sb.vtt(ref, 0), sb.vtt(ref, 0)]);
    gate.resolve();
    const [a, b] = await both;
    expect(calls).toBe(1);
    expect(a).toBe("WEBVTT\n\nhallo");
    expect(b).toBe("WEBVTT\n\nhallo");
  });

  test("a missing track is null, not a throw", async () => {
    const sb = bundles(async () => GZ);
    expect(await sb.vtt(refFor(GZ), 4)).toBeNull();
  });

  test("a bad sha shape is refused before any fetch", async () => {
    let calls = 0;
    const sb = bundles(async () => { calls++; return GZ; });
    expect(await sb.vtt({ messageId: 5, bytes: 10, sha256: "not-hex" }, 0)).toBeNull();
    expect(calls).toBe(0);
  });

  test("bytes over the compressed cap is refused before any fetch", async () => {
    let calls = 0;
    const sb = bundles(async () => { calls++; return GZ; });
    expect(await sb.vtt(refFor(GZ, { bytes: 17 * 1024 * 1024 }), 0)).toBeNull();
    expect(calls).toBe(0);
  });

  test("a sha mismatch is refused, and nothing is written", async () => {
    const sb = bundles(async () => GZ);
    const ref = refFor(GZ, { sha256: "d".repeat(64) });

    expect(await sb.vtt(ref, 0)).toBeNull();
    await nothingHeld(root);
  });

  test("a gzip bomb is refused", async () => {
    const bomb = gzipSync(Buffer.alloc(70 * 1024 * 1024, 65));
    const sb = bundles(async () => bomb);
    expect(await sb.vtt(refFor(bomb), 0)).toBeNull();
  });

  test("a corrupt disk file is deleted and refetched", async () => {
    const ref = refFor(GZ);
    await mkdir(heldDir, { recursive: true });
    await writeFile(join(heldDir, `${ref.sha256}.json.gz`), "not gzip at all");
    let calls = 0;
    const sb = bundles(async () => { calls++; return GZ; });

    expect(await sb.vtt(ref, 0)).toBe("WEBVTT\n\nhallo");
    expect(calls).toBe(1);
  });

  test("a plain read never writes the disk store", async () => {
    const sb = bundles(async () => GZ);
    await sb.vtt(refFor(GZ), 0);
    await nothingHeld(root);
  });

  test.skipIf(process.getuid?.() === 0)("a corrupt disk file that cannot be removed is still refetched, not a rejection", async () => {
    const ref = refFor(GZ);
    await mkdir(heldDir, { recursive: true });
    await writeFile(join(heldDir, `${ref.sha256}.json.gz`), "not gzip at all");
    await chmod(heldDir, 0o500);
    const warnings = spyOn(console, "warn").mockImplementation(() => {});
    try {
      let calls = 0;
      const sb = bundles(async () => { calls++; return GZ; });

      expect(await sb.vtt(ref, 0)).toBe("WEBVTT\n\nhallo");
      expect(calls).toBe(1);
    } finally {
      warnings.mockRestore();
      await chmod(heldDir, 0o700);
    }
  });

  test("a failed fetch is null, and is logged", async () => {
    const warnings = spyOn(console, "warn").mockImplementation(() => {});
    try {
      const sb = bundles(async () => { throw new Error("no network"); });
      expect(await sb.vtt(refFor(GZ), 0)).toBeNull();
      expect(warnings).toHaveBeenCalledTimes(1);
    } finally {
      warnings.mockRestore();
    }
  });
});

describe("hold", () => {
  test("writes the bundle to disk", async () => {
    const ref = refFor(GZ);
    const sb = bundles(async () => GZ);

    await sb.hold(ref);
    const written = await readFile(join(heldDir, `${ref.sha256}.json.gz`));
    expect(JSON.parse(gunzipSync(written).toString())).toMatchObject({ v: 1 });
  });

  test("a null ref is a no-op", async () => {
    const sb = bundles(async () => GZ);
    await sb.hold(null);
    await nothingHeld(root);
  });

  test("a failed fetch leaves nothing behind", async () => {
    const sb = bundles(async () => { throw new Error("no network"); });
    await sb.hold(refFor(GZ));
    await nothingHeld(root);
  });

  test("a disk store that cannot be written is logged, not a rejection", async () => {
    const blocker = join(root, "not-a-dir");
    await writeFile(blocker, "");
    heldDir = join(blocker, "held");
    const warnings = spyOn(console, "warn").mockImplementation(() => {});
    try {
      await bundles(async () => GZ).hold(refFor(GZ));
      expect(warnings).toHaveBeenCalledTimes(1);
    } finally {
      warnings.mockRestore();
    }
  });

  test("already on disk needs no second fetch", async () => {
    let calls = 0;
    const ref = refFor(GZ);
    const sb = bundles(async () => { calls++; return GZ; });

    await sb.hold(ref);
    await sb.hold(ref);
    expect(calls).toBe(1);
  });

  test("a plain read that already warmed the memory cache still reaches disk", async () => {
    // The gap this guards: a title played but not held must not silently
    // skip the write once it *is* held, just because its bundle is already
    // in the in-process memory cache.
    const ref = refFor(GZ);
    const sb = bundles(async () => GZ);

    await sb.vtt(ref, 0);
    await nothingHeld(root);
    await sb.hold(ref);
    const written = await readFile(join(heldDir, `${ref.sha256}.json.gz`));
    expect(written.byteLength).toBeGreaterThan(0);
  });
});

function putBundleFile(db: Database, setId: string, ref: BundleRef): void {
  db.run("INSERT INTO subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at) VALUES (?, -1001, ?, ?, ?, 1)", [
    setId, ref.messageId, ref.bytes, ref.sha256,
  ]);
}

describe("reconcile", () => {
  test("holds every held set's bundle, and only those", async () => {
    const db = emptyIndex();
    putBundleFile(db, "held-set", refFor(GZ));
    putBundleFile(db, "other-set", refFor(GZ));
    let calls = 0;
    const sb = bundles(async () => { calls++; return GZ; });

    await sb.reconcile(db, { ids: ["held-set"] });

    expect(calls).toBe(1);
    expect(await readdir(heldDir)).toHaveLength(1);
  });

  test("a held set with no bundle yet is nothing to hold, not an error", async () => {
    const db = emptyIndex();
    const sb = bundles(async () => GZ);
    await sb.reconcile(db, { ids: ["no-bundle-set"] });
    await nothingHeld(root);
  });

  test("against an index without the v13 tables, nothing is held and nothing throws", async () => {
    const db = new Database(":memory:");
    const sb = bundles(async () => GZ);
    await sb.reconcile(db, { ids: ["some-set"] });
    await nothingHeld(root);
  });

  test("a re-reconcile of an already-held set fetches nothing new", async () => {
    const db = emptyIndex();
    const ref = refFor(GZ);
    putBundleFile(db, "held-set", ref);
    let calls = 0;
    const sb = bundles(async () => { calls++; return GZ; });

    await sb.reconcile(db, { ids: ["held-set"] });
    await sb.reconcile(db, { ids: ["held-set"] });

    expect(calls).toBe(1);
    expect(await readdir(heldDir)).toHaveLength(1);
  });
});
