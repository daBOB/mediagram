/**
 * The rules behind each achievement, one ladder each — split out of
 * `achievements.ts`, which gathers them. Every function answers the same
 * shape, a {@link Rung} per id: when it was earned, or `null`, and the
 * progress towards it.
 */

import type { LibraryCollection, LibraryTitle } from "./achievements";

export const DAY_MS = 86_400_000;

/** One achievement of a ladder: earned at `earnedAt`, or not yet (`null`). */
export interface Rung {
  id: string;
  earnedAt: number | null;
  have: number;
  need: number;
}

/** A finish the library still holds, oldest first. */
export interface Finish {
  title: LibraryTitle;
  at: number;
}

/** One local day's seconds across every device, as days since 1970-01-01. */
export interface DayTotal {
  day: number;
  seconds: number;
}

/** A finish's local day at `offsetMs`, as days since 1970-01-01. */
const finishDay = (at: number, offsetMs: number) => Math.floor((at + offsetMs) / DAY_MS);

const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Days since 1970-01-01 for a `YYYY-MM-DD` date, or `null` for one naming no
 * month or day — the core's `calendar::day_number`, which rolls a 31
 * February into March exactly as `Date.UTC` does.
 */
function epochDay(date: string): number | null {
  const parts = DATE.exec(date);
  if (!parts) return null;
  const [year, month, day] = [Number(parts[1]), Number(parts[2]), Number(parts[3])];
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  return Date.UTC(year, month - 1, day) / DAY_MS;
}

/**
 * Every device's rows summed per local day, oldest day first. A row whose
 * day names no date counts toward no day-based achievement.
 */
export function dayTotals(rows: { day: string; seconds: number }[]): DayTotal[] {
  const byDay = new Map<number, number>();
  for (const row of rows) {
    const day = epochDay(row.day);
    if (day !== null) byDay.set(day, (byDay.get(day) ?? 0) + row.seconds);
  }
  return [...byDay].map(([day, seconds]) => ({ day, seconds })).sort((a, b) => a.day - b.day);
}

/** Rung N of a counting ladder is earned by the Nth time in `times`, which is ascending. */
export function counted(name: string, rungs: number[], times: number[]): Rung[] {
  return rungs.map((need) => ({ id: `${name}-${need}`, earnedAt: times[need - 1] ?? null, have: times.length, need }));
}

/** When each new distinct genre arrived: entry K is the finish that brought the (K+1)th. */
export function genreArrivals(finishes: Finish[]): number[] {
  const seen = new Set<string>();
  const arrivals: number[] = [];
  for (const { title, at } of finishes) {
    for (const genre of title.genres) seen.add(genre);
    while (arrivals.length < seen.size) arrivals.push(at);
  }
  return arrivals;
}

/** Cumulative watching across days; a rung is earned on the day the total reaches it. */
export function hours(rungs: number[], days: DayTotal[], midnight: (day: number) => number): Rung[] {
  let total = 0;
  const reached = new Map<number, number>();
  for (const { day, seconds } of days) {
    total += seconds;
    for (const need of rungs) if (!reached.has(need) && total >= need * 3600) reached.set(need, midnight(day));
  }
  const have = Math.floor(total / 3600);
  return rungs.map((need) => ({ id: `hours-${need}`, earnedAt: reached.get(need) ?? null, have, need }));
}

/** Consecutive days with any watching; a rung is earned on the day completing the first such run. */
export function streak(rungs: number[], days: DayTotal[], midnight: (day: number) => number): Rung[] {
  let run = 0;
  let longest = 0;
  let previous = Number.NaN;
  const reached = new Map<number, number>();
  for (const { day, seconds } of days) {
    if (seconds <= 0) continue;
    run = day === previous + 1 ? run + 1 : 1;
    previous = day;
    longest = Math.max(longest, run);
    for (const need of rungs) if (run === need && !reached.has(need)) reached.set(need, midnight(day));
  }
  return rungs.map((need) => ({ id: `streak-${need}`, earnedAt: reached.get(need) ?? null, have: longest, need }));
}

/** Episodes finished on one local day; earned on the first day reaching `need`. */
export function binge(need: number, finishes: Finish[], offsetMs: number, midnight: (day: number) => number): Rung {
  const perDay = new Map<number, number>();
  let most = 0;
  let first: number | null = null;
  for (const { title, at } of finishes) {
    if (title.kind !== "ep") continue;
    const day = finishDay(at, offsetMs);
    const count = (perDay.get(day) ?? 0) + 1;
    perDay.set(day, count);
    most = Math.max(most, count);
    if (count === need && first === null) first = day;
  }
  return { id: `binge-${need}`, earnedAt: first === null ? null : midnight(first), have: most, need };
}

/**
 * Every set of one collection finished. Earned when the first collection was
 * completed; until then, the progress of the one closest to it. Nothing at
 * all for a library with no collections.
 */
export function wholeShow(collections: LibraryCollection[], finishedAt: Map<string, number>): Rung[] {
  let earnedAt: number | null = null;
  let best: { have: number; need: number } | null = null;
  for (const { setIds } of collections) {
    const need = setIds.length;
    if (need === 0) continue;
    const times = setIds.flatMap((setId) => finishedAt.get(setId) ?? []);
    const have = times.length;
    if (have === need) earnedAt = Math.min(earnedAt ?? Infinity, Math.max(...times));
    // Closest first; equal shares with equal counts are equal answers.
    if (best === null || have * best.need > best.have * need || (have * best.need === best.have * need && have > best.have)) {
      best = { have, need };
    }
  }
  if (best === null) return [];
  return [{ id: "whole-show", earnedAt, ...best }];
}
