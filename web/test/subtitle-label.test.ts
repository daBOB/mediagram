/** Covers `subtitle-label`: turning a track's language code into a menu row. */

import { describe, expect, test } from "bun:test";

import { subtitleLabel } from "../public/lib/subtitle-label.js";

describe("naming a subtitle track", () => {
  test("a language code becomes the language's name", () => {
    expect(subtitleLabel("de")).toBe("German");
    expect(subtitleLabel("en")).toBe("English");
    expect(subtitleLabel("ja")).toBe("Japanese");
  });

  test("an untagged track is called what it is, not what it is tagged", () => {
    // Every subtitle in this library is `und`: the sidecars beside the videos
    // carry no language, so the uploader had nothing to record. "Subtitles"
    // is honest and useful; "und" is neither.
    expect(subtitleLabel("und")).toBe("Subtitles");
  });

  test("a region keeps its distinction, because it is one a viewer can see", () => {
    expect(subtitleLabel("de-AT")).toBe("Austrian German");
  });

  test("a code nobody recognises is shown as itself rather than hidden", () => {
    expect(subtitleLabel("zz")).toBe("zz");
  });

  test("a missing code never reaches the menu as a blank row", () => {
    expect(subtitleLabel("")).toBe("Subtitles");
    expect(subtitleLabel(null)).toBe("Subtitles");
    expect(subtitleLabel(undefined)).toBe("Subtitles");
  });

  test("a malformed tag is survived rather than thrown", () => {
    // The server only ever sends `[A-Za-z]{2,8}`, but a label is not worth a
    // broken player if that ever changes.
    expect(subtitleLabel("a")).toBe("a");
    expect(subtitleLabel("!!")).toBe("!!");
  });
});
