/**
 * What a profile's stats page shows, from the rows every device counted.
 *
 * Pure, and pinned by `test/fixtures/watch-state/stats-summary.json`, which
 * the Android core runs too. Days are `YYYY-MM-DD` strings throughout and
 * compared as strings, which orders them correctly; arithmetic on them is
 * done in UTC so a daylight-saving change cannot skip or repeat a day.
 */

import type { DayStatRow, TitleStatRow } from "./stats-record";

export interface SummaryInput {
  /** The reading engine's local date. */
  today: string;
  titles: TitleStatRow[];
  days: DayStatRow[];
  /** Live `watched` rows only — a title un-marked is not finished. */
  watched: { setId: string; finishedAt: number }[];
}

export interface HistoryEntry {
  kind: "started" | "finished" | "again";
  setId: string;
  /** Epoch ms. */
  at: number;
  /** The title's total across devices; 0 when it has no title row. */
  seconds: number;
}

export interface StatsSummary {
  /** Monday of today's ISO week through today. */
  weekSeconds: number;
  /** Today's calendar month, through today. */
  monthSeconds: number;
  /** Every day row, a future one included. */
  allSeconds: number;
  /** Thirty days, oldest first, ending today; a day nobody watched is 0. */
  last30: { day: string; seconds: number }[];
  /** Newest first. */
  history: HistoryEntry[];
}

/** Equal times sort a finish first, then a restart, then a first play. */
const KIND_ORDER = { finished: 0, again: 1, started: 2 } as const;

/** `day` moved by `by` days. */
function shiftDay(day: string, by: number): string {
  const [year, month, date] = day.split("-").map(Number) as [number, number, number];
  return new Date(Date.UTC(year, month - 1, date + by)).toISOString().slice(0, 10);
}

/** 0 for Monday through 6 for Sunday. */
function isoWeekday(day: string): number {
  return (new Date(`${day}T00:00:00Z`).getUTCDay() + 6) % 7;
}

/** Code-unit order, the same on every engine — not `localeCompare`. */
const byText = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

export function summarize(input: SummaryInput): StatsSummary {
  const { today } = input;
  const byDay = new Map<string, number>();
  let allSeconds = 0;
  for (const row of input.days) {
    byDay.set(row.day, (byDay.get(row.day) ?? 0) + row.seconds);
    allSeconds += row.seconds;
  }
  const weekStart = shiftDay(today, -isoWeekday(today));
  const monthStart = `${today.slice(0, 7)}-01`;
  let weekSeconds = 0;
  let monthSeconds = 0;
  for (const [day, seconds] of byDay) {
    if (day > today) continue;
    if (day >= weekStart) weekSeconds += seconds;
    if (day >= monthStart) monthSeconds += seconds;
  }
  const last30 = Array.from({ length: 30 }, (_, index) => {
    const day = shiftDay(today, index - 29);
    return { day, seconds: byDay.get(day) ?? 0 };
  });
  return { weekSeconds, monthSeconds, allSeconds, last30, history: historyOf(input) };
}

function historyOf(input: SummaryInput): HistoryEntry[] {
  const titles = new Map<string, { seconds: number; started: number; again: number | null }>();
  for (const row of input.titles) {
    const held = titles.get(row.setId);
    const again = row.againAt ?? null;
    if (held === undefined) {
      titles.set(row.setId, { seconds: row.seconds, started: row.startedAt, again });
      continue;
    }
    held.seconds += row.seconds;
    held.started = Math.min(held.started, row.startedAt);
    if (again !== null) held.again = held.again === null ? again : Math.max(held.again, again);
  }
  const history: HistoryEntry[] = [];
  for (const [setId, held] of titles) {
    history.push({ kind: "started", setId, at: held.started, seconds: held.seconds });
    if (held.again !== null) history.push({ kind: "again", setId, at: held.again, seconds: held.seconds });
  }
  // Finishes from before stats existed have no title row and are history all the same.
  for (const row of input.watched) {
    history.push({ kind: "finished", setId: row.setId, at: row.finishedAt, seconds: titles.get(row.setId)?.seconds ?? 0 });
  }
  return history.sort((a, b) => b.at - a.at || byText(a.setId, b.setId) || KIND_ORDER[a.kind] - KIND_ORDER[b.kind]);
}
