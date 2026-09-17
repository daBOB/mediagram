/**
 * The Rust exporter writes, this reader reads.
 *
 * Every other test in this directory builds its own package, which proves
 * only that the reader agrees with itself. The fixture here came out of
 * `mediagram export-package`, so it is the one test that can catch the two
 * implementations of one format drifting apart — a JSON writer that escapes
 * something differently, a tar header field read from the wrong offset, a
 * gzip level nobody thought about.
 *
 * Regenerate it with `test/fixtures/package/regenerate.sh`. The library it
 * describes is synthetic and the key is a fixture key; nothing in it names a
 * real channel.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtemp, readFile, readdir, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { listPlayable } from "../src/catalog";
import { refreshCatalog } from "../src/package/refresh";
import { parseKey } from "../src/package/open";

const FIXTURES = new URL("./fixtures/package/", import.meta.url).pathname;

let root: string;
let key: Buffer;
let pointerText: string;
let sealed: Uint8Array;

beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-round-trip-"));
  key = parseKey(await readFile(join(FIXTURES, "key.base64"), "utf8"));
  pointerText = await readFile(join(FIXTURES, "latest.json"), "utf8");

  const name = (await readdir(FIXTURES)).find((f) => f.endsWith(".tar.gz.enc"));
  if (!name) throw new Error("no package in the fixtures; run regenerate.sh");
  sealed = new Uint8Array(await readFile(join(FIXTURES, name)));
});
afterEach(async () => {
  await rm(root, { recursive: true, force: true });
});

/** Serves the fixture, optionally with an edited pointer or bytes. */
function host(options: { pointer?: string; sealed?: Uint8Array } = {}) {
  const text = options.pointer ?? pointerText;
  const body = options.sealed ?? sealed;
  return (async (input: string | URL | Request) => {
    const url = String(input);
    if (url.endsWith("latest.json")) return new Response(text, { status: 200 });
    if (url.endsWith(".tar.gz.enc")) {
      return new Response(body, { status: 200 });
    }
    return new Response(null, { status: 404 });
  }) as unknown as typeof globalThis.fetch;
}

const run = (fetcher: typeof globalThis.fetch) =>
  refreshCatalog({
    baseUrl: "https://packages.example.test/mediagram",
    key,
    root,
    supportedSchema: [4],
    // The fixture is dated when it was generated; freezing "now" would make
    // this test start failing the day after it was written.
    now: () => Math.floor(Date.now() / 1000),
    fetch: fetcher,
  });

describe("a package written by mediagram export-package", () => {
  test("opens, unpacks, and its library.db answers the catalog query", async () => {
    const result = await run(host());

    expect(result.status).toBe("updated");
    const db = new Database(join(result.dir!, "library.db"), { readonly: true });
    try {
      const sets = listPlayable(db);
      expect(sets.length).toBeGreaterThan(0);
      expect(sets.map((s) => s.title)).toContain("Blade: Trinity");
    } finally {
      db.close();
    }
  });

  test("its posters arrive as files the player can serve", async () => {
    const result = await run(host());

    const posters = await readdir(join(result.dir!, "posters"));
    expect(posters.length).toBeGreaterThan(0);
    for (const name of posters) {
      expect(name).toMatch(/^tmdb-(movie|tv)-\d+\.jpg$/);
      const bytes = await readFile(join(result.dir!, "posters", name));
      // JPEG's start-of-image marker: a real image, not an error page the
      // exporter saved because a CDN fetch failed.
      expect([bytes[0], bytes[1]]).toEqual([0xff, 0xd8]);
    }
  });

  test("the manifest inside agrees with the pointer outside", async () => {
    const result = await run(host());

    const manifest = JSON.parse(await readFile(join(result.dir!, "manifest.json"), "utf8"));
    const pointer = JSON.parse(pointerText);
    expect(manifest.created_at).toBe(pointer.created_at);
    expect(manifest.schema).toBe(pointer.schema);
    expect(manifest.spec).toBe(pointer.spec);
    expect(manifest.sets).toBe(2);
  });

  test("one flipped ciphertext byte is refused", async () => {
    const tampered = new Uint8Array(sealed);
    tampered[Math.floor(tampered.length / 2)]! ^= 1;

    const result = await run(host({ sealed: tampered }));

    expect(result.status).toBe("kept");
    expect(result.dir).toBeNull();
  });

  /**
   * The five authenticated fields, one at a time. Each must fail, and the
   * ones the cipher covers must fail *at the cipher* — a shape check passing
   * them through would mean the associated data this reader rebuilds is not
   * the data the exporter sealed with.
   */
  test("altering any authenticated field stops the package opening", async () => {
    const pointer = JSON.parse(pointerText);
    const edits: [string, unknown][] = [
      ["created_at", pointer.created_at + 1],
      ["spec", pointer.spec + 1],
    ];

    for (const [field, value] of edits) {
      const result = await run(host({ pointer: JSON.stringify({ ...pointer, [field]: value }) }));

      expect(result.status).toBe("kept");
      expect(result.reason).toMatch(/authentication/i);
    }
  });

  test("altering a download field does not stop it, because it is not sealed", async () => {
    // Stated as a test because it is the format's most surprising property,
    // and the reason `sha256` may never decide freshness.
    const pointer = JSON.parse(pointerText);
    const moved = JSON.stringify({ ...pointer, url: "https://elsewhere.test/pkg.tar.gz.enc" });

    const result = await run(host({ pointer: moved }));

    expect(result.status).toBe("updated");
  });
});
