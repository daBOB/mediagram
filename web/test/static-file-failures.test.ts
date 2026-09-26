import { expect, test } from "bun:test";
import { mkdtempSync, rmSync, symlinkSync } from "node:fs";
import { gunzipSync } from "node:zlib";
import { join, basename } from "node:path";
import { staticResponse } from "../src/http/static-files";
import type { PlayerRequest } from "../src/http/contracts";

const get = (path: string, extra: Partial<PlayerRequest> = {}) =>
  staticResponse({ method: "GET", path, range: null, ...extra });

test("missing files and malformed paths remain ordinary 404 responses", () => {
  for (const path of ["/missing-file.txt", "/%zz", "/%00", "/app.js/child"]) {
    expect(get(path).status).toBe(404);
  }
});

test("unexpected filesystem errors reach the request error boundary", () => {
  const directory = mkdtempSync(new URL("../public/static-test-", import.meta.url).pathname);
  try {
    symlinkSync("loop", join(directory, "loop"));
    expect(() => get(`/${basename(directory)}/loop`)).toThrow(/ELOOP/);
  } finally { rmSync(directory, { recursive: true, force: true }); }
});

test("a compressible static file is gzipped when the request accepts it", async () => {
  const plain = get("/app.js");
  const gzipped = get("/app.js", { acceptEncoding: "gzip" });

  expect(gzipped.headers["content-encoding"]).toBe("gzip");
  expect(gzipped.headers.vary).toBe("accept-encoding");
  // node:zlib's bundled types demand a `Buffer<ArrayBuffer>` specifically; a
  // plain `Uint8Array` decompresses identically at runtime, compared here as
  // one so `toEqual` isn't asked to match Buffer's own extra methods.
  expect(new Uint8Array(gunzipSync(gzipped.body as unknown as Buffer))).toEqual(new Uint8Array(plain.body as Uint8Array));
  expect(Number(gzipped.headers["content-length"])).toBeLessThan(Number(plain.headers["content-length"]));
});

test("a static file answers 304 once its own etag comes back as If-None-Match", () => {
  const first = get("/app.js");
  const etag = first.headers.etag!;

  const revalidated = get("/app.js", { ifNoneMatch: etag });

  expect(revalidated.status).toBe(304);
  expect(revalidated.body).toBeNull();
});

test("a font is never compressed, even when the request accepts it", () => {
  const plain = get("/font/geist-latin.woff2");
  const response = get("/font/geist-latin.woff2", { acceptEncoding: "gzip, br" });

  expect(response.headers["content-type"]).toBe("font/woff2");
  expect(response.headers["content-encoding"]).toBeUndefined();
  expect(response.body).toEqual(plain.body as Uint8Array);
  // Still revalidatable, even though it is never compressed.
  expect(response.headers.etag).toBeDefined();
});
