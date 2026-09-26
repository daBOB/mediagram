/**
 * `slug` is a byte-for-byte port of `mlib_spec::slug::slug`: the uploader
 * derives a course's collection id from it, and `posterKeyFor` derives a
 * title-keyed artwork slot from it, and the two must agree on every title or
 * `mediagram artwork "<title>"` never finds the key the browser asks for.
 *
 * Cases below are the Rust function's own tests
 * (`crates/mlib-spec/src/slug.rs`), plus umlauts, which that suite does not
 * cover explicitly.
 */

import { describe, expect, test } from "bun:test";
import { slug } from "../src/package/posters";

describe("slug", () => {
  test("a title becomes a readable slug", () => {
    expect(slug("Rust Course 2024")).toBe("rust-course-2024");
  });

  test("punctuation collapses rather than doubling dashes", () => {
    expect(slug("Rust -- The  Book!!")).toBe("rust-the-book");
    expect(slug("  leading and trailing  ")).toBe("leading-and-trailing");
  });

  test("hostile input is safe to put in a path", () => {
    for (const hostile of ["../../etc/passwd", "a/b\\c", "..", "with\nnewline"]) {
      const s = slug(hostile);
      expect(s.includes("/")).toBe(false);
      expect(s.includes("\\")).toBe(false);
      expect(s.includes("..")).toBe(false);
    }
  });

  test("a title with no ASCII yields an empty slug", () => {
    expect(slug("日本語")).toBe("");
  });

  test("an umlaut is dropped, not transliterated, exactly as the Rust side drops it", () => {
    // 'Ü' is not ASCII-alphanumeric, so it is dropped like any other
    // punctuation — it never opens a leading dash, because `out` is still
    // empty when it is reached.
    expect(slug("Über uns")).toBe("ber-uns");
    expect(slug("Geldhochschule")).toBe("geldhochschule");
  });
});
