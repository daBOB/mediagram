/**
 * Gzip/br negotiation and the ETag/304 that lets a repeat request skip a
 * body entirely. Never applied to anything below the size worth the CPU, or
 * to a type that is not text.
 */

import { describe, expect, test } from "bun:test";
import { brotliDecompressSync, gunzipSync } from "node:zlib";
import { negotiatedResponse } from "../src/http/compression";

const large = (byte: number) => new TextEncoder().encode(`{"value":"${"x".repeat(byte)}"}`);

describe("choosing an encoding", () => {
  test("brotli is used when the request accepts it", () => {
    const response = negotiatedResponse({ acceptEncoding: "gzip, deflate, br", ifNoneMatch: null }, large(4000), "application/json");

    expect(response.status).toBe(200);
    expect(response.headers["content-encoding"]).toBe("br");
    expect(response.headers.vary).toBe("accept-encoding");
    // node:zlib's bundled types demand a `Buffer<ArrayBuffer>` specifically; a
    // plain `Uint8Array` decompresses identically at runtime, compared here as
    // one so `toEqual` isn't asked to match Buffer's own extra methods.
    expect(new Uint8Array(brotliDecompressSync(response.body as unknown as Buffer))).toEqual(large(4000));
  });

  test("gzip is used when only gzip is accepted", () => {
    const response = negotiatedResponse({ acceptEncoding: "gzip", ifNoneMatch: null }, large(4000), "application/json");

    expect(response.headers["content-encoding"]).toBe("gzip");
    expect(new Uint8Array(gunzipSync(response.body as unknown as Buffer))).toEqual(large(4000));
  });

  test("no Accept-Encoding leaves the body uncompressed, but still carries an etag", () => {
    const response = negotiatedResponse({ acceptEncoding: null, ifNoneMatch: null }, large(4000), "application/json");

    expect(response.headers["content-encoding"]).toBeUndefined();
    expect(response.body).toEqual(large(4000));
    expect(response.headers.etag).toBeDefined();
  });

  test("a body under the size floor is never compressed", () => {
    const response = negotiatedResponse({ acceptEncoding: "br, gzip", ifNoneMatch: null }, "tiny", "application/json");

    expect(response.headers["content-encoding"]).toBeUndefined();
    expect(response.body).toEqual(new TextEncoder().encode("tiny"));
  });

  test("a non-text type is never compressed even when accepted and large", () => {
    const bytes = new Uint8Array(4000).fill(1);
    const response = negotiatedResponse({ acceptEncoding: "br, gzip", ifNoneMatch: null }, bytes, "font/woff2");

    expect(response.headers["content-encoding"]).toBeUndefined();
    expect(response.body).toEqual(bytes);
    expect(response.headers.etag).toBeDefined();
  });

  test("content-length matches the encoded body actually sent", () => {
    const response = negotiatedResponse({ acceptEncoding: "gzip", ifNoneMatch: null }, large(4000), "application/json");
    expect(response.headers["content-length"]).toBe(String((response.body as Uint8Array).byteLength));
  });
});

describe("revalidation", () => {
  test("a matching If-None-Match answers 304 with no body", () => {
    const body = large(4000);
    const first = negotiatedResponse({ acceptEncoding: "gzip", ifNoneMatch: null }, body, "application/json");
    const etag = first.headers.etag!;

    const second = negotiatedResponse({ acceptEncoding: "gzip", ifNoneMatch: etag }, body, "application/json");

    expect(second.status).toBe(304);
    expect(second.body).toBeNull();
    expect(second.headers["content-length"]).toBe("0");
    expect(second.headers.etag).toBe(etag);
  });

  test("a stale If-None-Match still gets the full response", () => {
    const response = negotiatedResponse({ acceptEncoding: null, ifNoneMatch: '"not-the-real-one"' }, large(4000), "application/json");

    expect(response.status).toBe(200);
    expect(response.body).toEqual(large(4000));
  });

  test("different content gets a different etag", () => {
    const a = negotiatedResponse({ acceptEncoding: null, ifNoneMatch: null }, large(4000), "application/json");
    const b = negotiatedResponse({ acceptEncoding: null, ifNoneMatch: null }, large(4001), "application/json");

    expect(a.headers.etag).not.toBe(b.headers.etag);
  });
});

test("a HEAD request carries the headers without a body", () => {
  const response = negotiatedResponse({ acceptEncoding: "gzip", ifNoneMatch: null }, large(4000), "application/json", { headOnly: true });

  expect(response.body).toBeNull();
  expect(response.headers["content-encoding"]).toBe("gzip");
  expect(response.headers["content-length"]).not.toBe("0");
});
