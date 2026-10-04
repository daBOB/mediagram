/**
 * The readers every value in another device's document goes through.
 *
 * Kept apart from `sync-record.ts` so that it and the files that read part
 * of the same document beside it — `stats-record.ts`, `roles-record.ts` —
 * read a value the same way: one idea of what a number, a piece of text or a
 * time is, not several that could drift apart.
 */

export function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

export function objectRow(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

/** Older documents allow numeric strings and other primitives. Objects and
 * arrays are not numbers; coercing a JSON object's own `toString` can throw. */
export function numberFromScalar(value: unknown): number {
  return value !== null && typeof value === "object" ? Number.NaN : Number(value);
}

/** A non-empty string, trimmed — the only kind of text worth keeping here. */
export function text_(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const clean = value.trim();
  return clean === "" ? null : clean;
}

/**
 * A time a synced row may carry: positive, no later than `Number.MAX_SAFE_INTEGER`.
 * Rows are last-writer-wins, so one stamped past it would outrank every later
 * edit of that row on every device it reached (dropped, not clamped: clamped,
 * it would too), and the core's own `+ 1` on it could leave its integer range.
 */
export function isStamp(at: number): boolean {
  return at > 0 && at <= Number.MAX_SAFE_INTEGER;
}
