/** Types for the display helpers in `format.js`. */

export function humanSize(bytes: number): string;
export function humanDuration(seconds: number | null): string;
export function episodeLabel(set: {
  kind?: string;
  season?: number | null;
  episode?: string | null;
}): string;
export function codecLine(set: {
  container?: string | null;
  vcodec?: string | null;
  acodec?: string | null;
}): string;

/** The average bitrate over a whole set, as `9.4 Mbps`. Empty without both. */
export function bitrateLabel(set: {
  total?: number | null;
  duration?: number | null;
}): string;

/** What a title's dynamic range is worth saying, or `null` for SDR. */
export function hdrLabel(set: { hdr?: string | null }): string | null;

/** Everything the index knows about a file, for a card or the player HUD. */
export function technicalLine(set: {
  quality?: string | null;
  hdr?: string | null;
  container?: string | null;
  vcodec?: string | null;
  acodec?: string | null;
  total?: number | null;
  partCount?: number | null;
  duration?: number | null;
}): string;

/** Where someone got to: `42% · 12:30`, or the position alone if unmeasured. */
export function resumeLine(progress: { at?: number; duration?: number | null } | null): string;

/** A position on a scrub bar: `1:23`, or `1:23:45` once past an hour. */
export function clockTime(seconds: number | null): string;

/** A count as a word while it is small enough to be one, else as figures. */
export function spellCount(count: number): string;

/** An extent: `three shows`, `one show`, `170 lessons`. */
export function countOf(count: number, noun: string): string;

/** The wall-clock time something with `remainingSeconds` left will finish. */
export function endsAt(remainingSeconds: number, now: Date): string;
