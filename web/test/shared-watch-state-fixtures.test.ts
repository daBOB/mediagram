/**
 * Runs the JSON fixtures under `fixtures/watch-state/` against the web's own
 * record parsing, merge, resume, Next up and viewing-stats logic.
 *
 * These fixtures are read by other languages too, so a case here is a rule
 * both engines must agree on. The sync, resume and Next up cases describe
 * behaviour the web already has, and a case that only passes after a change
 * to `src/state` or `public/lib` does not belong with them. The stats
 * fixtures are different: they pin the new counting rules first, and the
 * web's modules under `src/state/stats-*` are written to satisfy them.
 */

import { describe, expect, test } from "bun:test";
import { catalogSet } from "./support/catalog-set";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { mergeStates, type MergedState } from "../src/state/merge";
import { parseRecord, type SyncRecord } from "../src/state/sync-record";
import { againNow, stepSeconds } from "../src/state/stats-step";
import type { CatalogSet } from "../public/lib/library.js";
import { groupDepartments } from "../public/lib/departments.js";
import { homeShelves } from "../public/lib/catalog/home-shelves.js";
import {
  isFinished,
  resumeAt,
  trustedRuntime,
  watchedFraction,
} from "../public/lib/resume-point.js";

const FIXTURES = join(import.meta.dir, "fixtures", "watch-state");

function load<T>(file: string): T {
  return JSON.parse(readFileSync(join(FIXTURES, file), "utf8")) as T;
}

describe("record-parse fixtures", () => {
  interface Case {
    name: string;
    input: string;
    expect: SyncRecord | null;
  }

  for (const one of load<Case[]>("record-parse.json")) {
    test(one.name, () => {
      expect(parseRecord(one.input)).toEqual(one.expect);
    });
  }
});

describe("merge fixtures", () => {
  interface Case {
    name: string;
    records: SyncRecord[];
    expect: MergedState;
  }

  // Profiles and rows compared as sets, not by the order a Map iterates them
  // in, so a runner does not have to reproduce that order to agree with one.
  //
  // Picked explicitly, not spread: `merge.json` predates watchlist, Kids and
  // collections and its `expect` objects say nothing about them, so this
  // must not surface fields `mergeStates` now always fills in — that is
  // `lists-merge.json`'s job, below, with its own comparison.
  function canonical(state: MergedState): MergedState {
    return {
      profiles: state.profiles
        .map((profile) => ({
          name: profile.name,
          displayName: profile.displayName,
          ...(profile.kids ? { kids: profile.kids } : {}),
          progress: [...profile.progress].sort((a, b) => a.setId.localeCompare(b.setId)),
          watched: [...profile.watched].sort((a, b) => a.setId.localeCompare(b.setId)),
          ...(profile.unwatched?.length
            ? { unwatched: [...profile.unwatched].sort((a, b) => a.setId.localeCompare(b.setId)) }
            : {}),
          ...(profile.preferences?.length
            ? { preferences: [...profile.preferences].sort((a, b) =>
                `${a.scope}\u0000${a.name}`.localeCompare(`${b.scope}\u0000${b.name}`)) }
            : {}),
        }))
        .sort((a, b) => a.name.localeCompare(b.name)),
    };
  }

  for (const one of load<Case[]>("merge.json")) {
    test(one.name, () => {
      expect(canonical(mergeStates(one.records))).toEqual(one.expect);
      // Order-independent, spelling included: devices see each other's
      // documents in whatever order Telegram hands them over.
      expect(canonical(mergeStates([...one.records].reverse()))).toEqual(one.expect);
    });
  }
});

describe("lists-merge fixtures", () => {
  interface Case {
    name: string;
    records: SyncRecord[];
    expect: ReturnType<typeof canonicalLists>;
  }

  /** The list-carrying fields only — progress and watched are `merge.json`'s
   * concern, not this fixture's. */
  function canonicalLists(state: MergedState) {
    return {
      kids: [...(state.kids ?? [])].sort((a, b) => a.setId.localeCompare(b.setId)),
      editorsChoice: [...(state.editorsChoice ?? [])].sort((a, b) => a.setId.localeCompare(b.setId)),
      profiles: (state.profiles ?? [])
        .map((profile) => ({
          name: profile.name,
          displayName: profile.displayName,
          watchlist: [...(profile.watchlist ?? [])].sort((a, b) => a.setId.localeCompare(b.setId)),
          collections: [...(profile.collections ?? [])].sort((a, b) => a.id.localeCompare(b.id)),
        }))
        .sort((a, b) => a.name.localeCompare(b.name)),
    };
  }

  for (const one of load<Case[]>("lists-merge.json")) {
    test(one.name, () => {
      expect(canonicalLists(mergeStates(one.records))).toEqual(one.expect);
      expect(canonicalLists(mergeStates([...one.records].reverse()))).toEqual(one.expect);
    });
  }
});

describe("resume-point fixtures", () => {
  const fns = { resumeAt, isFinished, watchedFraction, trustedRuntime } as const;

  interface Case {
    name: string;
    fn: keyof typeof fns;
    args: unknown[];
    expect: unknown;
  }

  for (const one of load<Case[]>("resume-point.json")) {
    test(`${one.fn}: ${one.name}`, () => {
      const run = fns[one.fn] as (...args: unknown[]) => unknown;
      expect(run(...one.args)).toEqual(one.expect);
    });
  }
});

describe("next-up fixtures", () => {
  interface Case {
    name: string;
    /** A flat play order: one show, its episodes numbered as they appear here. */
    order: string[];
    progress: Array<{ setId: string; at: number; duration: number; updatedAt: number }>;
    watched: Record<string, number>;
    expect: {
      nextUp: { setId: string; resume: boolean } | null;
      continues: string[];
      totals: { continues: number; nextUp: number };
    };
  }

  /** One episode of a single synthetic show, numbered by its place in `order`. */
  function episodeOf(setId: string, number: number): CatalogSet {
    return catalogSet({
      setId,
      kind: "ep",
      title: `Show ${number}`,
      show: "Show",
      chap: null,
      path: null,
      season: 1,
      episode: String(number),
      year: null,
      container: "mp4",
      vcodec: "h264",
      acodec: "aac",
      duration: 3600,
      total: 1000,
      partCount: 1,
      addedAt: 1000,
    });
  }

  for (const one of load<Case[]>("next-up.json")) {
    test(one.name, () => {
      const sets = one.order.map((setId, index) => episodeOf(setId, index + 1));
      const shelves = homeShelves({
        library: groupDepartments(sets),
        byId: new Map(sets.map((set) => [set.setId, set])),
        // The store hands these over newest first; so does this.
        progress: [...one.progress].sort((a, b) => b.updatedAt - a.updatedAt),
        watchedAt: (setId: string) => one.watched[setId] ?? null,
      });

      const nextUp = shelves.nextUp[0]
        ? { setId: shelves.nextUp[0].set.setId, resume: shelves.nextUp[0].resume }
        : null;

      expect(nextUp).toEqual(one.expect.nextUp);
      expect(shelves.continues.map((set) => set.setId)).toEqual(one.expect.continues);
      expect(shelves.totals.continues).toBe(one.expect.totals.continues);
      expect(shelves.totals.nextUp).toBe(one.expect.totals.nextUp);
    });
  }
});

describe("stats-step fixtures", () => {
  const fns = { stepSeconds, againNow } as const;

  interface Case {
    name: string;
    fn: keyof typeof fns;
    args: unknown[];
    expect: unknown;
  }

  for (const one of load<Case[]>("stats-step.json")) {
    test(`${one.fn}: ${one.name}`, () => {
      const run = fns[one.fn] as (...args: unknown[]) => unknown;
      expect(run(...one.args)).toEqual(one.expect);
    });
  }
});
