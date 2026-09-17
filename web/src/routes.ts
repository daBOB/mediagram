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
import { createRequire } from "node:module";
import { join, normalize } from "node:path";
import { subtitle, subtitleLanguages, summary } from "./assets";
import { listPlayable, partLocations, playableSet, type PartLocation } from "./catalog";
import { planReads, totalSize, type PartSpan, type Step } from "./range";
import { isLocalAddress } from "./client-reach";
import { PosterStore, posterKeyFor, posterKeyIsValid } from "./package/posters";
import { DEFAULT_MAX_BITRATE } from "./config";
import { contentType, planResponse } from "./response";

/** Where a stream's bytes come from. */
export interface ByteSource {
  /** The bytes of `steps`, in order, for a set whose parts are `locations`. */
  stream(locations: PartLocation[], steps: Step[], setId: string): ReadableStream<Uint8Array>;
}

export interface PlayerRequest {
  method: string;
  path: string;
  range: string | null;
  /** `?seek=` on a transcode request, in seconds. */
  seek?: string | null;
  /** The address the request came from, already resolved through any proxy. */
  client?: string;
}

/** Serves HLS playlists and segments for a transcode in progress. */
export interface HlsServer {
  /**
   * Starts (or joins) a transcode and returns its playlist URL.
   *
   * Joining rather than always starting is what stops two viewers of the same
   * title running two encoders.
   */
  begin(setId: string, seekSeconds: number): Promise<string>;

  /**
   * The file for a session, or `null` when it is not ready.
   *
   * Not-ready is a real answer rather than an error: the playlist does not
   * exist until ffmpeg has written a first segment, and a player handed an
   * empty playlist treats it as a failure instead of waiting.
   */
  file(sessionId: string, name: string): Promise<{ body: Uint8Array; type: string } | null>;

  /**
   * Stops a session and forgets it. A session that is not running is not an
   * error: the viewer's browser may be saying goodbye to one already reaped.
   */
  end(sessionId: string): Promise<void>;
}

export interface PlayerResponse {
  status: number;
  headers: Record<string, string>;
  /** `null` for a bodiless response; the byte count is always in the headers. */
  body: ReadableStream<Uint8Array> | Uint8Array | null;
}

const STREAM_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/stream$/;
const SUMMARY_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/summary$/;
// Spelled out for the same reason as everything else that reaches a file
// name: the key comes from a caption, and `posterKeyIsValid` checks it again.
const POSTER_PATH = /^\/api\/posters\/(tmdb-(?:movie|tv)-\d{1,12})\.jpg$/;
// The language is spelled out rather than captured loosely: it ends up in no
// path, but a route that accepts `../` invites someone to make it one.
const SUBTITLE_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/subtitles\/([A-Za-z]{2,8})\.vtt$/;
// A session id is a hex digest and a segment is what ffmpeg names them. Both
// reach a filesystem path, so both are spelled out rather than captured
// loosely: a route that accepts `../` invites someone to use it.
const HLS_PATH = /^\/hls\/([a-f0-9]{16})\/([A-Za-z0-9_-]{1,64}\.(?:m3u8|ts|m4s))$/;
/** The session itself, which `DELETE` stops. */
const HLS_SESSION_PATH = /^\/hls\/([a-f0-9]{16})\/?$/;

/** The page and its script, served from `web/public`. */
const PUBLIC_DIR = new URL("../public/", import.meta.url).pathname;

/**
 * hls.js, served out of the installed package rather than copied into
 * `public`.
 *
 * Only Safari plays an HLS playlist from a plain `<video src>`; in Chrome and
 * Firefox the transcode route would be unreachable without this. Reading it
 * from the dependency keeps one copy of it and ties its version to the
 * lockfile instead of to whenever someone last re-copied the file.
 */
const HLS_LIBRARY_PATH = "/lib/hls.mjs";

let hlsLibrary: Uint8Array | null = null;

function hlsLibraryBytes(): Uint8Array {
  if (hlsLibrary === null) {
    const resolve = createRequire(import.meta.url).resolve;
    hlsLibrary = new Uint8Array(readFileSync(resolve("hls.js/dist/hls.min.mjs")));
  }
  return hlsLibrary;
}

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
  try {
    // Decoding is inside the try because a malformed escape throws, and a
    // path that cannot be decoded is one that does not exist — not a fault of
    // this server's worth a 500 and a stack in the log.
    const relative = urlPath === "/" ? "index.html" : decodeURIComponent(urlPath).replace(/^\/+/, "");
    const resolved = normalize(join(PUBLIC_DIR, relative));
    if (!resolved.startsWith(PUBLIC_DIR)) return null;

    const dot = resolved.lastIndexOf(".");
    const type = CONTENT_TYPES[resolved.slice(dot)] ?? "application/octet-stream";
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

export interface RouterOptions {
  db: Database;
  source: ByteSource;
  hls?: HlsServer;
  /** The artwork the current catalog carries, if any. */
  posters?: PosterStore;
  /**
   * What a remote link is assumed to carry, in bits per second.
   *
   * The page needs it to decide whether a title can be played as it is: the
   * browser cannot know how it reached this server, and the server can.
   */
  maxBitrate?: number;
}

export function createRouter(options: RouterOptions) {
  const { db, source, hls } = options;
  const maxBitrate = options.maxBitrate ?? DEFAULT_MAX_BITRATE;
  const posters = options.posters ?? new PosterStore(null);

  return async function route(request: PlayerRequest): Promise<PlayerResponse> {
    // The one thing a viewer may change: a transcode they no longer want.
    // It holds the hardware encoder, and waiting for the idle reaper means a
    // second one starts while the abandoned one is still running.
    if (request.method === "DELETE") {
      // Anything else under `/hls/` is simply not a session, which is a 404;
      // `DELETE` anywhere else is a method this server does not have.
      if (!request.path.startsWith("/hls/")) return empty(405);
      const session = HLS_SESSION_PATH.exec(request.path);
      if (!session || !hls) return empty(404);
      await hls.end(session[1]!);
      return empty(204);
    }

    const readOnlyMethod = request.method === "GET" || request.method === "HEAD";
    if (!readOnlyMethod) return empty(405);

    if (request.path === "/api/player") {
      // Which link this viewer is on, which the page cannot work out for
      // itself. Deliberately says nothing about the host or the proxy.
      return text(
        JSON.stringify({ remote: !isLocalAddress(request.client ?? ""), maxBitrate }),
        "application/json",
      );
    }

    if (request.path === "/api/sets") {
      // What a set has, so the page can offer a summary or a subtitle track
      // without asking per title.
      const sets = listPlayable(db).map(({ tmdb, ...set }) => {
        // The key rather than the id: the page asks for artwork this server
        // has, instead of guessing a URL and collecting 404s for every title
        // whose poster the package did not carry.
        const key = posterKeyFor(set.kind, tmdb);
        return {
          ...set,
          poster: posters.has(key) ? key : null,
          hasSummary: summary(db, set.setId) !== null,
          subtitles: subtitleLanguages(db, set.setId),
        };
      });
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

    const wantsPoster = POSTER_PATH.exec(request.path);
    if (wantsPoster) {
      const key = wantsPoster[1]!;
      const body = posterKeyIsValid(key) ? posters.read(key) : null;
      if (body === null) return empty(404);
      return {
        status: 200,
        headers: {
          "content-type": "image/jpeg",
          "content-length": String(body.byteLength),
          // Named after the title it shows, and replaced only when a whole
          // new catalog arrives, so a day is comfortably safe.
          "cache-control": "public, max-age=86400",
        },
        body: request.method === "HEAD" ? null : body,
      };
    }

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

    const beginMatch = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/transcode$/.exec(request.path);
    if (beginMatch) {
      if (!hls) return empty(501);
      if (playableSet(db, beginMatch[1]!) === null) return empty(404);
      // `Number.isFinite`, not a NaN check: `Infinity` survives one of those
      // and reaches the command line as `-ss Infinity`, which ffmpeg exits on
      // at once while the request waits out the whole readiness timeout.
      const asked = Number(request.seek ?? 0);
      const seek = Number.isFinite(asked) ? Math.max(0, Math.floor(asked)) : 0;

      // A conversion that produces nothing is a 503 carrying the reason
      // rather than a 500: it is a title that could not be started now, and
      // the page has somewhere to show why.
      let playlist: string;
      try {
        playlist = await hls.begin(beginMatch[1]!, seek);
      } catch (error) {
        const reason = error instanceof Error ? error.message : "the conversion did not start";
        return { ...text(JSON.stringify({ error: reason }), "application/json"), status: 503 };
      }
      const body = JSON.stringify({ playlist });
      return {
        status: 200,
        headers: {
          "content-type": "application/json",
          "content-length": String(Buffer.byteLength(body)),
        },
        body: request.method === "HEAD" ? null : new TextEncoder().encode(body),
      };
    }

    const hlsMatch = HLS_PATH.exec(request.path);
    if (hlsMatch) {
      if (!hls) return empty(404);
      return hlsResponse(hls, hlsMatch[1]!, hlsMatch[2]!, request.method);
    }
    // Anything under /hls that did not match the shape above is refused
    // rather than falling through to the static files below.
    if (request.path.startsWith("/hls/")) return empty(404);

    if (request.path === HLS_LIBRARY_PATH) {
      const body = hlsLibraryBytes();
      return {
        status: 200,
        headers: {
          "content-type": "text/javascript; charset=utf-8",
          "content-length": String(body.byteLength),
          // Half a megabyte that changes only when the dependency does.
          "cache-control": "public, max-age=86400",
        },
        body: request.method === "HEAD" ? null : body,
      };
    }

    if (!request.path.startsWith("/api/")) {
      const file = staticFile(request.path);
      if (file !== null) {
        return {
          status: 200,
          headers: {
            "content-type": file.type,
            "content-length": String(file.body.byteLength),
            // The page and its scripts are small and change whenever the
            // server is updated. A browser holding yesterday's copy of one of
            // them against today's API is a bug with no visible cause.
            "cache-control": "no-cache",
          },
          body: request.method === "HEAD" ? null : file.body,
        };
      }
    }

    return empty(404);
  };
}

const HLS_TYPES: Record<string, string> = {
  m3u8: "application/vnd.apple.mpegurl",
  ts: "video/mp2t",
  m4s: "video/iso.segment",
};

async function hlsResponse(
  hls: HlsServer,
  sessionId: string,
  name: string,
  method: string,
): Promise<PlayerResponse> {
  const found = await hls.file(sessionId, name);
  // 503: the transcode exists but has not produced this yet. A player retries
  // a 503 and gives up on a 404.
  if (found === null) return empty(503);

  return {
    status: 200,
    headers: {
      "content-type": found.type,
      "content-length": String(found.body.byteLength),
      // A playlist grows while encoding; a cached one stops at whatever
      // length it had when it was first read.
      "cache-control": "no-store",
    },
    body: method === "HEAD" ? null : found.body,
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
      ? source.stream(locations, planReads(spans, plan.range), setId)
      : null;

  return { status: plan.status, headers, body };
}
