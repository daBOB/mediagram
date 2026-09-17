/**
 * The reader algorithm end to end.
 *
 * Two properties matter more than the happy path. A refresh that fails for
 * any reason must leave the player with the catalog it already had — a stale
 * catalog is useful, a forged one is not. And "I already have this" is
 * decided from the authenticated fields, never from `sha256`, which anyone
 * who can rewrite the pointer can set to whatever the reader is holding.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { createCipheriv, createHash, randomBytes } from "node:crypto";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { gzipSync } from "node:zlib";

import { refreshCatalog, type Pointer } from "../src/package/refresh";
import { NONCE_LEN } from "../src/package/open";

const KEY = Buffer.alloc(32, 3);
const KEY_ID = createHash("sha256").update(KEY).digest("hex").slice(0, 8);
const BLOCK = 512;

let root: string;

beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-catalog-"));
});
afterEach(async () => {
  await rm(root, { recursive: true, force: true });
});

function member(name: string, body: Uint8Array): Uint8Array {
  const header = Buffer.alloc(BLOCK);
  header.write(name, 0, 100, "utf8");
  header.write("000644 \0", 100, 8, "utf8");
  header.write(body.length.toString(8).padStart(11, "0") + " ", 124, 12, "utf8");
  header.write("00000000000 ", 136, 12, "utf8");
  header.write("0", 156, 1, "utf8");
  header.write("ustar\0", 257, 6, "utf8");
  header.write("00", 263, 2, "utf8");
  header.write(" ".repeat(8), 148, 8, "utf8");
  let sum = 0;
  for (const byte of header) sum += byte;
  header.write(sum.toString(8).padStart(6, "0") + "\0 ", 148, 8, "utf8");
  const padded = Buffer.alloc(Math.ceil(body.length / BLOCK) * BLOCK);
  Buffer.from(body).copy(padded);
  return new Uint8Array(Buffer.concat([header, padded]));
}

const text = (s: string) => new TextEncoder().encode(s);

/** A package as the exporter would write it, and the pointer naming it. */
function build(options: { createdAt: number; dbBody?: string; manifestCreatedAt?: number }) {
  const createdAt = options.createdAt;
  const manifest = JSON.stringify({
    format: 1,
    created_at: options.manifestCreatedAt ?? createdAt,
    schema: 4,
    spec: 4,
    sets: 1,
    parts: 1,
    posters: [{ key: "tmdb-movie-36648", file: "posters/tmdb-movie-36648.jpg" }],
  });
  const tar = Buffer.concat([
    Buffer.from(member("manifest.json", text(manifest))),
    Buffer.from(member("library.db", text(options.dbBody ?? "SQLite format 3"))),
    Buffer.from(member("posters/tmdb-movie-36648.jpg", text("jpeg"))),
    Buffer.alloc(BLOCK * 2),
  ]);

  const pointer: Pointer = {
    format: 1,
    created_at: createdAt,
    file: `prebuilt_mediagram_db_x-${createdAt}.tar.gz.enc`,
    url: `https://example.test/prebuilt_mediagram_db_x-${createdAt}.tar.gz.enc`,
    bytes: 0,
    sha256: "",
    cipher: "aes-256-gcm",
    key_id: KEY_ID,
    schema: 4,
    spec: 4,
  };

  const aad = new TextEncoder().encode(
    JSON.stringify({
      format: pointer.format,
      created_at: pointer.created_at,
      key_id: pointer.key_id,
      schema: pointer.schema,
      spec: pointer.spec,
    }),
  );
  const nonce = randomBytes(NONCE_LEN);
  const cipher = createCipheriv("aes-256-gcm", KEY, nonce);
  cipher.setAAD(aad);
  const packed = gzipSync(tar);
  const body = Buffer.concat([cipher.update(packed), cipher.final()]);
  const sealed = Buffer.concat([nonce, body, cipher.getAuthTag()]);

  pointer.bytes = sealed.length;
  pointer.sha256 = createHash("sha256").update(sealed).digest("hex");
  return { pointer, sealed: new Uint8Array(sealed) };
}

/** Serves one pointer and one package, and counts what was asked for. */
function host(built: { pointer: Pointer; sealed: Uint8Array }) {
  const asked: string[] = [];
  const fetcher = async (input: string | URL | Request) => {
    const url = String(input);
    asked.push(url);
    if (url.endsWith("latest.json")) {
      return new Response(JSON.stringify(built.pointer), { status: 200 });
    }
    if (url === `https://example.test/${built.pointer.file}`) {
      return new Response(built.sealed, { status: 200 });
    }
    return new Response(null, { status: 404 });
  };
  return { asked, fetcher: fetcher as unknown as typeof globalThis.fetch };
}

const NOW = 1_800_000_000;
const refresh = (built: ReturnType<typeof build>, over: Record<string, unknown> = {}) => {
  const served = host(built);
  return {
    served,
    run: () =>
      refreshCatalog({
        baseUrl: "https://example.test",
        key: KEY,
        root,
        supportedSchema: [4],
        now: () => NOW,
        fetch: served.fetcher,
        ...over,
      }),
  };
};

describe("a first refresh", () => {
  test("downloads, opens, unpacks and reports where the catalog is", async () => {
    const { run } = refresh(build({ createdAt: NOW - 100 }));

    const result = await run();

    expect(result.status).toBe("updated");
    expect(await readFile(join(result.dir!, "library.db"), "utf8")).toBe("SQLite format 3");
    expect(await readFile(join(result.dir!, "posters/tmdb-movie-36648.jpg"), "utf8")).toBe("jpeg");
  });

  test("the manifest must agree with the pointer about when it was built", async () => {
    // Both are authenticated, so disagreement means the exporter is wrong,
    // not that someone edited something. Either way it is not usable.
    const { run } = refresh(build({ createdAt: NOW - 100, manifestCreatedAt: NOW - 500 }));

    const result = await run();

    expect(result.status).toBe("kept");
    expect(result.reason).toMatch(/manifest/i);
  });
});

describe("a second refresh", () => {
  test("the same package is not downloaded again", async () => {
    const built = build({ createdAt: NOW - 100 });
    await refresh(built).run();

    const again = refresh(built);
    const result = await again.run();

    expect(result.status).toBe("unchanged");
    expect(again.served.asked).toEqual(["https://example.test/latest.json"]);
  });

  /**
   * The rule the format document states outright. `sha256` is not
   * authenticated, so a host that wants to suppress an update can set it to
   * the digest of the copy the reader holds. A reader that skips on that
   * match never runs the cipher and never notices.
   */
  test("a matching sha256 does not short-circuit anything", async () => {
    const held = build({ createdAt: NOW - 100 });
    await refresh(held).run();

    const newer = build({ createdAt: NOW - 50 });
    newer.pointer.sha256 = held.pointer.sha256; // what an attacker would do
    const again = refresh(newer);
    const result = await again.run();

    // Downloaded despite the matching digest, then refused for the digest
    // that does not match the bytes.
    expect(again.served.asked).toContain(`https://example.test/${newer.pointer.file}`);
    expect(result.status).toBe("kept");
    expect(result.reason).toMatch(/sha256|digest/i);
  });

  test("a newer package replaces the held one", async () => {
    await refresh(build({ createdAt: NOW - 100 })).run();

    const result = await refresh(build({ createdAt: NOW - 50, dbBody: "newer db" })).run();

    expect(result.status).toBe("updated");
    expect(await readFile(join(result.dir!, "library.db"), "utf8")).toBe("newer db");
  });

  test("an older package is refused and the held one kept", async () => {
    await refresh(build({ createdAt: NOW - 50, dbBody: "current db" })).run();

    const result = await refresh(build({ createdAt: NOW - 500 })).run();

    expect(result.status).toBe("kept");
    expect(result.reason).toMatch(/older|newer|stale/i);
    expect(await readFile(join(result.dir!, "library.db"), "utf8")).toBe("current db");
  });

  test("one built implausibly far in the future is refused", async () => {
    const result = await refresh(build({ createdAt: NOW + 90 * 86400 })).run();

    expect(result.status).toBe("kept");
    expect(result.reason).toMatch(/future/i);
  });
});

describe("where the package is fetched from", () => {
  /**
   * `url` is not authenticated, so following it is following whoever last
   * wrote the pointer. The package would still have to open, but the fetch
   * itself is the problem: a player on a home network told to request an
   * arbitrary address is a probe with a browser's credentials.
   *
   * The base URL is configured, `file` names the package beside the pointer,
   * and that is all a reader needs. The exporter builds `url` as exactly that
   * join, so nothing published normally is affected.
   */
  test("the package comes from the configured base, not from the pointer's url", async () => {
    const built = build({ createdAt: NOW - 100 });
    built.pointer.url = "http://169.254.169.254/latest/meta-data/";
    const served = host(built);
    // The stand-in host answers the package only at its own path, so a reader
    // that followed `url` would get a 404 and keep nothing.
    const result = await refreshCatalog({
      baseUrl: "https://example.test",
      key: KEY,
      root,
      supportedSchema: [4],
      now: () => NOW,
      fetch: served.fetcher,
    });

    expect(result.status).toBe("updated");
    expect(served.asked).not.toContain("http://169.254.169.254/latest/meta-data/");
  });

  test("a file name that is a path is refused", async () => {
    for (const file of ["../secrets", "a/b.tar.gz.enc", "/etc/passwd", "", "x?y"]) {
      const built = build({ createdAt: NOW - 100 });
      built.pointer.file = file;

      const result = await refresh(built).run();

      expect(result.status).toBe("kept");
      expect(result.reason).toMatch(/file/i);
    }
  });
});

describe("a refresh that fails", () => {
  const causes: [string, (built: ReturnType<typeof build>) => void, RegExp][] = [
    ["a tampered ciphertext byte", (b) => void (b.sealed[NONCE_LEN + 5]! ^= 1), /sha256|digest/i],
    ["a pointer for another key", (b) => void (b.pointer.key_id = "00112233"), /key/i],
    ["a size over the ceiling", (b) => void (b.pointer.bytes = 999_999_999), /limit|large/i],
    ["a format from the future", (b) => void (b.pointer.format = 2), /format/i],
  ];

  for (const [name, spoil, reason] of causes) {
    test(`${name} leaves the held catalog in place`, async () => {
      await refresh(build({ createdAt: NOW - 100, dbBody: "held db" })).run();

      const bad = build({ createdAt: NOW - 50 });
      spoil(bad);
      const result = await refresh(bad).run();

      expect(result.status).toBe("kept");
      expect(result.reason).toMatch(reason);
      expect(await readFile(join(result.dir!, "library.db"), "utf8")).toBe("held db");
    });
  }

  test("an altered authenticated field fails the tag, not a shape check", async () => {
    await refresh(build({ createdAt: NOW - 100, dbBody: "held db" })).run();

    const bad = build({ createdAt: NOW - 50 });
    // Still well-formed and still newer; only the cipher can catch this.
    bad.pointer.spec = 3;
    const result = await refresh(bad).run();

    expect(result.status).toBe("kept");
    expect(result.reason).toMatch(/authentication/i);
    expect(await readFile(join(result.dir!, "library.db"), "utf8")).toBe("held db");
  });

  test("with nothing held yet, a failure reports no catalog at all", async () => {
    const bad = build({ createdAt: NOW - 50 });
    bad.pointer.format = 2;

    const result = await refresh(bad).run();

    expect(result.status).toBe("kept");
    expect(result.dir).toBeNull();
  });

  test("a host that cannot be reached is a refusal, not a crash", async () => {
    const result = await refreshCatalog({
      baseUrl: "https://example.test",
      key: KEY,
      root,
      supportedSchema: [4],
      now: () => NOW,
      fetch: (async () => {
        throw new Error("getaddrinfo ENOTFOUND");
      }) as unknown as typeof globalThis.fetch,
    });

    expect(result.status).toBe("kept");
    expect(result.reason).toMatch(/ENOTFOUND|pointer/i);
  });

  test("a body longer than the pointer promised is refused", async () => {
    // The ceiling is worth nothing if the reader trusts the pointer's own
    // number and then reads whatever arrives.
    const built = build({ createdAt: NOW - 100 });
    const lying = {
      pointer: { ...built.pointer, bytes: 10 },
      sealed: built.sealed,
    };
    const result = await refresh(lying).run();

    expect(result.status).toBe("kept");
    expect(result.reason).toMatch(/bytes|larger|size/i);
  });
});
