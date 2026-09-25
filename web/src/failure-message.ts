/** Describing a rejection must not break a fallback or stop a background queue. */
export function failureMessage(error: unknown): string {
  try { return String(error instanceof Error ? error.message : error); }
  catch { return "unprintable rejection"; }
}
