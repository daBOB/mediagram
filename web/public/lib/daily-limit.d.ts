export const WARN_SECONDS: number;
export const DAILY_LIMIT_WORDS: { label: string; warning: string; out: string };
export const DAILY_LIMIT_CHOICES: { minutes: number | null; label: string }[];
export function dailyLimitState(
  limitMinutes: number | null | undefined,
  watchedSeconds: number,
): { state: "free" | "ok" | "warn" | "out"; leftSeconds: number | null };
