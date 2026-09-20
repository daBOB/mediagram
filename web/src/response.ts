/**
 * Deciding the status and headers of a response.
 *
 * Kept apart from the router because two of these decisions are easy to get
 * subtly wrong and expensive to debug through a video player: an absent
 * `Content-Length` makes ffmpeg read from byte zero instead of seeking, and a
 * `Content-Range` that disagrees with the body makes Safari refuse to play.
 */

import { parseRange, rangeLength, type ByteRange } from "./range";
import type { PlayerResponse } from "./routes";

/** The answer to a stream request, decided before a single byte is fetched. */
export interface ResponsePlan {
  status: number;
  /** The bytes to send, or `null` when there is no body to send. */
  range: ByteRange | null;
  /** Always stated, including on a 206 and on an error. */
  contentLength: number;
  contentRange: string | null;
}

/**
 * Plans the response to a `Range` header (or its absence) against a set's
 * total size.
 */
export function planResponse(rangeHeader: string | null, total: number): ResponsePlan {
  if (rangeHeader === null) {
    return {
      status: 200,
      range: total > 0 ? { start: 0, end: total - 1 } : null,
      contentLength: total,
      contentRange: null,
    };
  }

  const result = parseRange(rangeHeader, total);
  if (result.ok) {
    return {
      status: 206,
      range: result.range,
      contentLength: rangeLength(result.range),
      contentRange: `bytes ${result.range.start}-${result.range.end}/${total}`,
    };
  }

  // Understood but outside the file, or several ranges at once, which this
  // server refuses rather than answering with a multipart body.
  if (result.error === "unsatisfiable" || result.error === "multi-range") {
    return { status: 416, range: null, contentLength: 0, contentRange: `bytes */${total}` };
  }

  // Anything we could not parse is ignored, as RFC 9110 14.2 requires of a
  // unit we do not understand — which is to say, answered exactly as a
  // request carrying no Range at all. The client asked for less than we sent,
  // which every client copes with; refusing outright would break playback
  // over a header it did not need us to honour.
  return planResponse(null, total);
}

/**
 * The virtual file is the original file's bytes, so its type is the original
 * container's. A browser that gets this wrong will not even attempt direct
 * play, and falls back to a transcode it did not need.
 */
export function contentType(container: string): string {
  switch (container) {
    case "mkv":
      return "video/x-matroska";
    case "mp4":
    case "m4v":
      return "video/mp4";
    case "webm":
      return "video/webm";
    case "avi":
      return "video/x-msvideo";
    case "ts":
      return "video/mp2t";
    // A course document. Named here so a browser opens it in its own viewer
    // rather than downloading `stream` with no extension on it.
    case "pdf":
      return "application/pdf";
    default:
      return "application/octet-stream";
  }
}

/**
 * A response carrying a body, with its length stated.
 *
 * Stated, never left to the runtime: `Bun.serve` replaces a manually set
 * `Content-Length` with chunked encoding for anything it cannot buffer, and
 * ffmpeg cannot seek an HTTP source without one — it reads from byte zero
 * instead, which would quietly make every conversion start at the beginning
 * of the film. Every route in this project states its own length, and this is
 * the one place that does it, so the three routers cannot drift on the
 * property the rest of the system rests on.
 *
 * `headOnly` keeps the headers and drops the body, which is what a `HEAD`
 * answer is: the same framing, none of the bytes.
 */
export function withBody(
  body: string,
  contentType: string,
  options: { status?: number; headOnly?: boolean; headers?: Record<string, string> } = {},
): PlayerResponse {
  const bytes = new TextEncoder().encode(body);
  return {
    status: options.status ?? 200,
    headers: {
      "content-type": contentType,
      "content-length": String(bytes.byteLength),
      ...options.headers,
    },
    body: options.headOnly === true ? null : bytes,
  };
}

/**
 * A response carrying none — a refusal, or a write that answers 204.
 *
 * `content-length: 0` for the same reason: a bodiless response that does not
 * say so is one a client may sit waiting on.
 */
export function bodiless(status: number): PlayerResponse {
  return { status, headers: { "content-length": "0" }, body: null };
}
