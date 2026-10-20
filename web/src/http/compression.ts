/**
 * Gzip/br for text responses, with a strong ETag that lets a repeat request
 * skip the body entirely.
 *
 * Never applied to a media stream, a byte range, or an already-compressed
 * format such as `woff2`: a browser plays video by seeking into the
 * `Content-Length` it was promised, and encoding a byte range would answer a
 * length that no longer matches what is sent.
 *
 * Compressed bodies are kept in memory keyed by the body's own hash rather
 * than by where it came from: a static file reread from disk, or a catalog
 * JSON body rebuilt after a swap, gets the same cached compression the
 * moment its bytes are unchanged, and a change is simply a different key
 * rather than something to invalidate. Bounded by bytes so a long-running
 * process serving many catalog swaps does not grow this forever.
 */

import { createHash } from "node:crypto";
import { brotliCompressSync, constants, gzipSync } from "node:zlib";
import type { PlayerRequest, PlayerResponse } from "./contracts";
import { withBody } from "../response";

/** Below this, gzip/br's own framing overhead outweighs what a body this small can save. */
const MIN_COMPRESSIBLE_BYTES = 1024;

/** Text types worth spending CPU compressing. Fonts, images and media are already compressed formats. */
const COMPRESSIBLE_PREFIXES = ["text/html", "text/javascript", "text/css", "application/json", "image/svg+xml"];

/**
 * Bytes kept compressed at once: every static file (well under a megabyte
 * together) plus a handful of catalog generations (about 2 MB each). Bytes,
 * not entries: a page load asks for well over a hundred static files, and an
 * entry cap that falls below that evicts the catalog on every load.
 */
const MAX_CACHED_BYTES = 32 * 1024 * 1024;

/** What an entry costs beyond its compressed bodies, so etag-only entries stay bounded too. */
const ENTRY_OVERHEAD = 256;

/**
 * Brotli's default quality, 11, took 9.4 s on the 16 MB catalog, all of it
 * synchronous, so no request or stream was answered meanwhile. Quality 5 took
 * 85 ms for a body 14% larger.
 */
const BROTLI_QUALITY = 5;

interface Encoded {
  etag: string;
  gzip?: Uint8Array;
  br?: Uint8Array;
}

const cache = new Map<string, Encoded>();
let cachedBytes = 0;

const costOf = (encoded: Encoded) => ENTRY_OVERHEAD + (encoded.br?.byteLength ?? 0) + (encoded.gzip?.byteLength ?? 0);

function isCompressibleType(contentType: string): boolean {
  return COMPRESSIBLE_PREFIXES.some((prefix) => contentType.startsWith(prefix));
}

function encodedFor(body: Uint8Array, contentType: string): Encoded {
  const digest = createHash("sha1").update(body).digest("hex");
  const found = cache.get(digest);
  if (found) {
    // Moved to the back, so the Map's order is recency: a static file asked
    // on every page load outlives a catalog generation nobody asks for again.
    cache.delete(digest);
    cache.set(digest, found);
    return found;
  }

  const etag = `"${digest}"`;
  const encoded: Encoded = !isCompressibleType(contentType) || body.byteLength < MIN_COMPRESSIBLE_BYTES
    ? { etag }
    : { etag, br: brotliCompressSync(body, { params: { [constants.BROTLI_PARAM_QUALITY]: BROTLI_QUALITY } }), gzip: gzipSync(body) };

  cache.set(digest, encoded);
  cachedBytes += costOf(encoded);
  for (const [key, oldest] of cache) {
    if (cachedBytes <= MAX_CACHED_BYTES) break;
    cache.delete(key);
    cachedBytes -= costOf(oldest);
  }
  return encoded;
}

function pick(acceptEncoding: string | null | undefined, encoded: Encoded): { encoding: string; bytes: Uint8Array } | null {
  const accepted = (acceptEncoding ?? "").toLowerCase();
  // Brotli first: usually the smaller of the two, and both cost the same
  // — one compression, cached — so there is no reason to prefer gzip.
  if (encoded.br && accepted.includes("br")) return { encoding: "br", bytes: encoded.br };
  if (encoded.gzip && accepted.includes("gzip")) return { encoding: "gzip", bytes: encoded.gzip };
  return null;
}

/**
 * `body`, compressed when the request allows it and the type is worth it,
 * carrying a strong ETag either way. A matching `If-None-Match` short-
 * circuits to a bodiless 304 before anything is encoded or sent.
 */
export function negotiatedResponse(
  request: Pick<PlayerRequest, "acceptEncoding" | "ifNoneMatch">,
  body: string | Uint8Array,
  contentType: string,
  options: { headOnly?: boolean; headers?: Record<string, string> } = {},
): PlayerResponse {
  const bytes = typeof body === "string" ? new TextEncoder().encode(body) : body;
  const encoded = encodedFor(bytes, contentType);
  const headers = { ...options.headers, etag: encoded.etag, vary: "accept-encoding" };

  if (request.ifNoneMatch === encoded.etag) {
    return { status: 304, headers: { ...headers, "content-length": "0" }, body: null };
  }

  const picked = pick(request.acceptEncoding, encoded);
  if (picked) {
    return withBody(picked.bytes, contentType, {
      headOnly: options.headOnly,
      headers: { ...headers, "content-encoding": picked.encoding },
    });
  }
  return withBody(bytes, contentType, { headOnly: options.headOnly, headers });
}
