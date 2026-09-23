/**
 * Runs `fixtures/pick-index/cases.json` against the web's port of core's
 * `pick_index`. Core reads the same file (`index_tests.rs`), and core is the
 * authority here: the Android app chose between snapshots first.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { pickNewestIndex, pushedAt, versionStamp, type Candidate } from "../src/channel-index/pick-newest-index";

const { now, cases } = JSON.parse(
  readFileSync(join(import.meta.dir, "fixtures", "pick-index", "cases.json"), "utf8"),
) as { now: number; cases: { name: string; candidates: Candidate[]; expect: number | string }[] };

describe("pick-index fixtures", () => {
  for (const c of cases) {
    test(c.name, () => {
      expect(pickNewestIndex(c.candidates, now)).toBe(c.expect as never);
    });
  }
});

describe("the version a snapshot installs as", () => {
  // Core's `a_stamp_from_the_future_is_not_believed` asserts the same three.
  const caption = (at: number) => `#mlib-index v=2\n{"pushed_at":${at},"schema":6,"sets":1}`;

  test("a believable stamp names it", () => {
    expect(versionStamp(caption(now - 60), now)).toBe(now - 60);
    expect(versionStamp(caption(now + 60), now)).toBe(now + 60);
  });

  test("a stamp from next year does not, or it would outrank every real one", () => {
    expect(versionStamp(caption(now + 400 * 24 * 60 * 60), now)).toBe(now);
  });

  test("an unreadable caption installs as now rather than not at all", () => {
    expect(versionStamp("#mlib-index v=9", now)).toBe(now);
  });
});

describe("reading a caption's timestamp", () => {
  test("a caption without a JSON line has none", () => {
    expect(pushedAt("#mlib-index v=2")).toBeNull();
  });

  test("a zero or fractional stamp is not a time", () => {
    expect(pushedAt('#mlib-index v=2\n{"pushed_at":0}')).toBeNull();
    expect(pushedAt('#mlib-index v=2\n{"pushed_at":1.5}')).toBeNull();
  });

  test("fields a later version adds do not stop it reading", () => {
    expect(pushedAt('#mlib-index v=3\n{"pushed_at":1789946371,"extra":true}')).toBe(1789946371);
  });
});
