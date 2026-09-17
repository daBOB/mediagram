/**
 * Range maths for a set's virtual file.
 *
 * A set's parts are raw byte ranges of one original file, so the virtual file
 * is simply their concatenation in `off` order: no container parsing, no
 * index, just arithmetic. This module is pure, because a mistake here is not
 * an error message but a corrupt video.
 *
 * The Rust server in `crates/mediagram/src/serve/range.rs` computes the same
 * thing and is the oracle this is checked against. The one difference is the
 * seek unit: grammers skips whole 512 KiB chunks, while Telegram's
 * `upload.getFile` takes a byte offset it requires to be 4 KiB aligned.
 */

/**
 * Telegram refuses an offset that is not a multiple of this, with
 * `OFFSET_INVALID`. Verified against the live API; teleproto's own type
 * documentation claims offsets are handled precisely, and they are not.
 */
export const ALIGN = 4096;

/**
 * Legal values for a request's `limit`: 4 KiB multiples that divide 1 MiB.
 * Anything else answers `LIMIT_INVALID`.
 */
export const REQUEST_SIZES = [4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288];

/** One part's place in the virtual file. */
export interface PartSpan {
  idx: number;
  off: number;
  len: number;
}

/** An inclusive byte range, as HTTP defines it. */
export interface ByteRange {
  start: number;
  end: number;
}

export type RangeFailure =
  /** Not a byte range this server understands, so it is ignored entirely. */
  | "malformed"
  /** Understood, but outside the file: 416. */
  | "unsatisfiable"
  /**
   * Several ranges at once. Allowed by HTTP, refused here: answering needs a
   * multipart body, and browsers fall back to single ranges.
   */
  | "multi-range";

export type RangeResult =
  | { ok: true; range: ByteRange }
  | { ok: false; error: RangeFailure };

/** One read against one part: start here, discard this much, emit this many. */
export interface Step {
  partIdx: number;
  /** Absolute offset within the part's document. Always `ALIGN`-aligned. */
  offset: number;
  /** Bytes to discard after `offset` before the range begins. */
  headDrop: number;
  /** Bytes to emit. */
  take: number;
}

export function totalSize(parts: PartSpan[]): number {
  return parts.reduce((n, p) => n + p.len, 0);
}

export function rangeLength(range: ByteRange): number {
  return range.end - range.start + 1;
}

/** The smallest legal request size that covers `bytes`. */
export function requestSizeFor(bytes: number): number {
  return REQUEST_SIZES.find((size) => size >= bytes) ?? 524288;
}

/** Parses an integer that is entirely digits, or `null`. */
function digits(text: string): number | null {
  return /^\d+$/.test(text) ? Number(text) : null;
}

/**
 * Parses a `Range` header against a known total size.
 *
 * Handles the three forms browsers and ffmpeg actually send: `bytes=a-b`,
 * `bytes=a-`, and `bytes=-n` (the last `n` bytes). An end past the file is
 * clamped rather than refused, which is what RFC 9110 requires.
 */
export function parseRange(header: string, total: number): RangeResult {
  const spec = header.trim();
  if (!spec.startsWith("bytes=")) return { ok: false, error: "malformed" };
  const body = spec.slice("bytes=".length);
  if (body.includes(",")) return { ok: false, error: "multi-range" };

  const dash = body.indexOf("-");
  if (dash < 0) return { ok: false, error: "malformed" };
  const rawStart = body.slice(0, dash).trim();
  const rawEnd = body.slice(dash + 1).trim();

  if (rawStart === "") {
    // Suffix form: the last n bytes.
    const n = digits(rawEnd);
    if (n === null) return { ok: false, error: "malformed" };
    if (n === 0 || total === 0) return { ok: false, error: "unsatisfiable" };
    return { ok: true, range: { start: Math.max(0, total - n), end: total - 1 } };
  }

  const start = digits(rawStart);
  if (start === null) return { ok: false, error: "malformed" };

  let end: number;
  if (rawEnd === "") {
    if (total === 0) return { ok: false, error: "unsatisfiable" };
    end = total - 1;
  } else {
    const parsed = digits(rawEnd);
    if (parsed === null) return { ok: false, error: "malformed" };
    end = Math.min(parsed, Math.max(0, total - 1));
  }

  if (total === 0 || start >= total || start > end) {
    return { ok: false, error: "unsatisfiable" };
  }
  return { ok: true, range: { start, end } };
}

/** The reads that together cover `range` exactly, in order. */
export function planReads(parts: PartSpan[], range: ByteRange): Step[] {
  const steps: Step[] = [];
  for (const part of parts) {
    const partEnd = part.off + part.len; // exclusive
    if (partEnd <= range.start || part.off > range.end) continue;

    // Where this read starts and ends inside this part.
    const from = Math.max(0, range.start - part.off);
    const to = Math.min(range.end - part.off + 1, part.len); // exclusive
    if (to <= from) continue;

    const offset = Math.floor(from / ALIGN) * ALIGN;
    steps.push({
      partIdx: part.idx,
      offset,
      headDrop: from - offset,
      take: to - from,
    });
  }
  return steps;
}
