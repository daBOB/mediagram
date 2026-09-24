/** Covers `preference-scope`: what a remembered choice is filed under. */

import { describe, expect, test } from "bun:test";
import { scopeOf } from "../public/lib/playback/preference-scope.js";

describe("an identified series", () => {
  test("files every episode under the show", () => {
    // The whole point: choosing English on episode 1 is choosing it for 22.
    const one = scopeOf({ setId: "01A", showKey: "tmdb-tv-1399", show: "30 Rock" });
    const two = scopeOf({ setId: "01B", showKey: "tmdb-tv-1399", show: "30 Rock" });
    expect(one).toBe("key:tmdb-tv-1399");
    expect(two).toBe(one);
  });

  test("and survives the show being renamed, because the key is not the name", () => {
    expect(scopeOf({ setId: "01A", showKey: "tmdb-tv-1399", show: "30 Rock (2006)" })).toBe(
      "key:tmdb-tv-1399",
    );
  });
});

describe("a course nothing could identify", () => {
  test("falls back to the name the shelves group by", () => {
    // 170 lessons with no TMDB id between them. Filing per lesson would mean
    // choosing a playback speed 170 times.
    const a = scopeOf({ setId: "01A", showKey: null, show: "Geldhochschule" });
    const b = scopeOf({ setId: "01B", showKey: null, show: "Geldhochschule" });
    expect(a).toBe("show:Geldhochschule");
    expect(b).toBe(a);
  });

  test("a name that is only whitespace is not a name", () => {
    expect(scopeOf({ setId: "01A", show: "   " })).toBe("set:01A");
  });
});

describe("a one-off", () => {
  test("is filed under itself", () => {
    expect(scopeOf({ setId: "01FILM", showKey: null, show: null })).toBe("set:01FILM");
  });

  test("and does not leak its choice to another one-off", () => {
    expect(scopeOf({ setId: "01A" })).not.toBe(scopeOf({ setId: "01B" }));
  });
});

describe("the namespaces cannot collide", () => {
  test("a course named like a TMDB key is still a course", () => {
    // Without the prefix this would be filed with whatever tv-1399 is.
    expect(scopeOf({ setId: "01A", show: "tmdb-tv-1399" })).toBe("show:tmdb-tv-1399");
    expect(scopeOf({ setId: "01A", showKey: "tmdb-tv-1399" })).toBe("key:tmdb-tv-1399");
  });

  test("and a set id that looks like a show name is still a set", () => {
    expect(scopeOf({ setId: "Geldhochschule" })).toBe("set:Geldhochschule");
  });
});

describe("nothing to file it under", () => {
  test("is nothing, not an empty key everything shares", () => {
    expect(scopeOf(null)).toBeNull();
    expect(scopeOf({})).toBeNull();
    expect(scopeOf({ setId: 5 as never })).toBeNull();
  });
});
