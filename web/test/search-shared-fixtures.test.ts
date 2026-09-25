/**
 * Runs `fixtures/search/cases.json` against this player's own `SearchIndex`.
 *
 * The fixture is what pins this file together with
 * `shared_search_fixtures.rs`, the Rust port's copy of the same run: a case
 * that only passes after a change here does not belong in the fixture — see
 * `tasks/lessons.md`, 2026-09-18. Editing an expected order here is editing
 * it out from under the Rust test too.
 */

import { describe, expect, test } from "bun:test";

import { SearchIndex, type Searchable } from "../src/search/index";
import cases from "./fixtures/search/cases.json";

const full = (set: Partial<Searchable> & { setId: string }): Searchable => ({
  kind: "tut",
  title: null,
  show: null,
  chap: null,
  path: null,
  summary: null,
  ...set,
});

describe("search matches the shared fixture", () => {
  for (const c of cases.cases) {
    test(c.name, () => {
      const index = new SearchIndex(c.sets.map(full));
      const hits = index.search(c.query).map(({ setId, matched, excerpt }) => ({ setId, matched, excerpt }));
      // The fixture is JSON, so its `matched` is any string; it names the same
      // fields the index reports, which is what the comparison checks.
      expect(hits).toEqual(c.expect as typeof hits);
    });
  }
});
