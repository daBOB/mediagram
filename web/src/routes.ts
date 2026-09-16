/**
 * The HTTP surface: what is playable, and the bytes of a set.
 *
 * Routing is a pure function from a request description to a response
 * description. It deliberately does not build a `Response`: `Bun.serve`
 * replaces a manually set `Content-Length` with `Transfer-Encoding: chunked`
 * for any streamed body it cannot buffer, and ffmpeg cannot seek an HTTP
 * source without `Content-Length` — it reads from byte zero instead. Framing
 * is therefore stated here and written verbatim by `server.ts`.
 *
 * The router is also generic over where bytes come from, so the whole
 * contract — statuses, headers, part-boundary crossings — is testable without
 * Telegram.
 *
 * Nothing here ever puts a channel id or a message id in a response. The
 * browser is told what it may play, not where it lives.
 */

import type { Database } from "bun:sqlite";
import { readFileSync } from "node:fs";
import { join, normalize } from "node:path";
import { subtitle, subtitleLanguages, summary } from "./assets";
import { listPlayable, partLocations, playableSet, type PartLocation } from "./catalog";
import { planReads, totalSize, type PartSpan, type Step } from "./range";
import { contentType, planResponse } from "./response";

/** Where a stream's bytes come from. */
export interface ByteSource {
  /** The bytes of `steps`, in order, for a set whose parts are `locations`. */
  stream(locations: PartLocation[], steps: Step[]): ReadableStream<Uint8Array>;
}

export interface PlayerRequest {
  method: string;
  path: string;
  range: string | null;
}

export interface PlayerResponse {
  status: number;
  headers: Record<string, string>;
  /** `null` for a bodiless response; the byte count is always in the headers. */
  body: ReadableStream<Uint8Array> | Uint8Array | null;
}

const STREAM_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/stream$/;
const SUMMARY_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/summary$/;
// The language is spelled out rather than captured loosely: it ends up in no
// path, but a route that accepts `../` invites someone to make it one.
const SUBTITLE_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/subtitles\/([A-Za-z]{2,8})\.vtt$/;

/** The page and its script, served from `web/public`. */
const PUBLIC_DIR = new URL("../public/", import.meta.url).pathname;

const CONTENT_TYPES: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
};

/**
 * Reads a file from the public directory, or `null`.
 *
 * The path is normalized and then checked to still start with the public
 * directory, so `..` cannot walk out of it and serve, say, the session file
 * sitting two levels up.
 */
function staticFile(urlPath: string): { body: Uint8Array; type: string } | null {
  const relative = urlPath === "/" ? "index.html" : decodeURIComponent(urlPath).replace(/^\/+/, "");
  const resolved = normalize(join(PUBLIC_DIR, relative));
  if (!resolved.startsWith(PUBLIC_DIR)) return null;

  const dot = resolved.lastIndexOf(".");
  const type = CONTENT_TYPES[resolved.slice(dot)] ?? "application/octet-stream";
  try {
    return { body: new Uint8Array(readFileSync(resolved)), type };
  } catch {
    return null;
  }
}

/** A text response, with its length stated as every response states one. */
function text(body: string, contentType: string): PlayerResponse {
  const bytes = new TextEncoder().encode(body);
  return {
    status: 200,
    headers: {
      "content-type": contentType,
      "content-length": String(bytes.byteLength),
    },
    body: bytes,
  };
}

/**
 * A response with the status and nothing else. `Content-Length: 0` is stated
 * rather than left to the runtime, because every response carrying one is the
 * property the rest of the system relies on.
 */
function empty(status: number): PlayerResponse {
  return { status, headers: { "content-length": "0" }, body: null };
}

export function createRouter(db: Database, source: ByteSource) {
  return function route(request: PlayerRequest): PlayerResponse {
    const readOnlyMethod = request.method === "GET" || request.method === "HEAD";
    if (!readOnlyMethod) return empty(405);

    if (request.path === "/api/sets") {
      // What a set has, so the page can offer a summary or a subtitle track
      // without asking per title.
      const sets = listPlayable(db).map((set) => ({
        ...set,
        hasSummary: summary(db, set.setId) !== null,
        subtitles: subtitleLanguages(db, set.setId),
      }));
      const body = new TextEncoder().encode(JSON.stringify(sets));
      return {
        status: 200,
        headers: {
          "content-type": "application/json",
          "content-length": String(body.byteLength),
        },
        body: request.method === "HEAD" ? null : body,
      };
    }

    const streaming = STREAM_PATH.exec(request.path);
    if (streaming) return streamSet(db, source, request, streaming[1]!);

    const wantsSummary = SUMMARY_PATH.exec(request.path);
    if (wantsSummary) {
      const body = summary(db, wantsSummary[1]!);
      if (body === null) return empty(404);
      const response = text(body, "text/plain; charset=utf-8");
      return request.method === "HEAD" ? { ...response, body: null } : response;
    }

    const wantsSubtitle = SUBTITLE_PATH.exec(request.path);
    if (wantsSubtitle) {
      const body = subtitle(db, wantsSubtitle[1]!, wantsSubtitle[2]!);
      if (body === null) return empty(404);
      const response = text(body, "text/vtt; charset=utf-8");
      return request.method === "HEAD" ? { ...response, body: null } : response;
    }

    if (!request.path.startsWith("/api/")) {
      const file = staticFile(request.path);
      if (file !== null) {
        return {
          status: 200,
          headers: {
            "content-type": file.type,
            "content-length": String(file.body.byteLength),
          },
          body: request.method === "HEAD" ? null : file.body,
        };
      }
    }

    return empty(404);
  };
}

function streamSet(
  db: Database,
  source: ByteSource,
  request: PlayerRequest,
  setId: string,
): PlayerResponse {
  const set = playableSet(db, setId);
  // Not there, incomplete, or inconsistent: all the same to a player, and
  // none of them worth telling a caller apart.
  if (set === null) return empty(404);

  const locations = partLocations(db, setId);
  const spans: PartSpan[] = locations.map((l) => l.span);
  const total = totalSize(spans);

  const plan = planResponse(request.range, total);
  const headers: Record<string, string> = {
    "accept-ranges": "bytes",
    "content-length": String(plan.contentLength),
    "content-type": contentType(set.container),
  };
  if (plan.contentRange !== null) headers["content-range"] = plan.contentRange;

  // HEAD asks for the headers only: resolving documents and opening a
  // download for a body nobody reads would cost a Telegram round trip per
  // probe, and players probe often.
  const body =
    plan.range !== null && request.method !== "HEAD"
      ? source.stream(locations, planReads(spans, plan.range))
      : null;

  return { status: plan.status, headers, body };
}
