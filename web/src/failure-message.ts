/** Describing a rejection must not break a fallback or stop a background queue. */
export function failureMessage(error: unknown): string {
  try { return String(error instanceof Error ? error.message : error); }
  catch { return "unprintable rejection"; }
}

/**
 * The string `code` an fs or SQLite error carries (`ENOENT`,
 * `SQLITE_CONSTRAINT_FOREIGNKEY`), else `undefined`. Total for the same reason
 * as `failureMessage`: a rejection need not be an Error, and reading `.code`
 * off `null` would throw from inside the catch meant to handle it.
 */
export function errorCode(error: unknown): string | undefined {
  const code = typeof error === "object" && error !== null && "code" in error ? error.code : undefined;
  return typeof code === "string" ? code : undefined;
}
