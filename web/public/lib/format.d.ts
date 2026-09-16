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
