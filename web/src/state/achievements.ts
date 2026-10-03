/**
 * Achievements, worked out on every read from rows both engines already
 * keep — every device's day rows, the live watched marks and the library —
 * and never stored or synced: an achievement is a fact about those rows, so
 * two devices holding the same rows cannot disagree about one.
 *
 * `crates/mediagram-core/src/state/stats/achievements.rs` is a port of this
 * file; `test/fixtures/watch-state/achievements.json` pins the two together.
 */

import { binge, counted, DAY_MS, dayTotals, genreArrivals, hours, streak, wholeShow, type Finish, type Rung } from "./achievement-rungs";
import type { DayStatRow } from "./stats-record";

export type TitleKind = "movie" | "ep" | "tut" | "doc" | "docu";

/** One set the library holds, as the rules read it. */
export interface LibraryTitle {
  setId: string;
  kind: TitleKind;
  /** The provider's genres; an episode carries its show's. */
  genres: string[];
  /** The show or course it belongs to, or `null`. */
  collection: string | null;
}

/** A show's episodes or a course's lessons, as the library holds them now. */
export interface LibraryCollection {
  id: string;
  setIds: string[];
}

export interface AchievementLibrary {
  library: LibraryTitle[];
  collections: LibraryCollection[];
}

export interface AchievementInput extends AchievementLibrary {
  /** The reading engine's local date, `YYYY-MM-DD`. */
  today: string;
  /** The reading engine's offset from UTC now, in minutes: `120` in CEST. */
  utcOffsetMinutes: number;
  /** A kids profile is offered finishing and exploring achievements only. */
  kids: boolean;
  /** Every device's day rows. */
  days: DayStatRow[];
  /** Live watched rows only. */
  watched: { setId: string; finishedAt: number }[];
}

export interface Earned {
  id: string;
  /** Epoch milliseconds. */
  earnedAt: number;
}

export interface Next {
  id: string;
  have: number;
  need: number;
}

export interface Achievements {
  earned: Earned[];
  next: Next[];
}

const FILMS = [1, 10, 50, 100];
const GENRES = [5, 10];
const DOCS = [10];
const HOURS = [10, 100, 500];
const STREAKS = [7, 30];
const BINGE = 5;
/** How many of the closest unearned achievements a page shows. */
const NEXT_SHOWN = 3;

/** Plain code-unit order, the same as Rust's `str` ordering — never the locale's. */
const byId = (a: { id: string }, b: { id: string }) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);

export function achievements(input: AchievementInput): Achievements {
  const offsetMs = input.utcOffsetMinutes * 60_000;
  // A day's local midnight, at the offset the reader is at now — applied to
  // every day alike, so a day from before a clock change reads an hour out.
  const midnight = (day: number) => day * DAY_MS - offsetMs;
  const bySet = new Map(input.library.map((title) => [title.setId, title]));
  // A finish of a set the library no longer holds counts for nothing.
  const finishes: Finish[] = input.watched
    .flatMap((row) => {
      const title = bySet.get(row.setId);
      return title ? [{ title, at: row.finishedAt }] : [];
    })
    .sort((a, b) => a.at - b.at);
  const timesOf = (kind: TitleKind) => finishes.filter((finish) => finish.title.kind === kind).map((finish) => finish.at);
  const finishedAt = new Map(finishes.map((finish) => [finish.title.setId, finish.at]));

  const ladders: Rung[][] = [
    counted("films", FILMS, timesOf("movie")),
    counted("genres", GENRES, genreArrivals(finishes)),
    counted("docs", DOCS, timesOf("docu")),
    wholeShow(input.collections, finishedAt),
  ];
  if (!input.kids) {
    const days = dayTotals(input.days);
    ladders.push(hours(HOURS, days, midnight), streak(STREAKS, days, midnight), [binge(BINGE, finishes, offsetMs, midnight)]);
  }

  const earned: Earned[] = [];
  const next: Next[] = [];
  for (const ladder of ladders) {
    for (const { id, earnedAt } of ladder) if (earnedAt !== null) earned.push({ id, earnedAt });
    const open = ladder.find((rung) => rung.earnedAt === null);
    if (open) next.push({ id: open.id, have: open.have, need: open.need });
  }
  earned.sort((a, b) => b.earnedAt - a.earnedAt || byId(a, b));
  // Closest first: have/need compared without dividing.
  next.sort((a, b) => b.have * a.need - a.have * b.need || byId(a, b));
  return { earned, next: next.slice(0, NEXT_SHOWN) };
}
