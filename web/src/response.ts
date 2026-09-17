/**
 * Deciding the status and headers of a stream response.
 *
 * Kept apart from the router because two of these decisions are easy to get
 * subtly wrong and expensive to debug through a video player: an absent
 * `Content-Length` makes ffmpeg read from byte zero instead of seeking, and a
 * `Content-Range` that disagrees with the body makes Safari refuse to play.
 */

import { parseRange, rangeLength, type ByteRange } from "./range";

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
    default:
      return "application/octet-stream";
  }
}
