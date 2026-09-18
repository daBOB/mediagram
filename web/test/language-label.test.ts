/** Covers `language-label`: turning a track's language code into a menu row. */

import { describe, expect, test } from "bun:test";

import { languageLabel } from "../public/lib/language-label.js";

describe("naming a subtitle track", () => {
  test("a language code becomes the language's name", () => {
    expect(languageLabel("de", "Subtitles")).toBe("German");
    expect(languageLabel("en", "Subtitles")).toBe("English");
    expect(languageLabel("ja", "Subtitles")).toBe("Japanese");
  });

  test("an untagged track is called what it is, not what it is tagged", () => {
    // Every subtitle in this library is `und`: the sidecars beside the videos
    // carry no language, so the uploader had nothing to record. "Subtitles"
    // is honest and useful; "und" is neither.
    expect(languageLabel("und", "Subtitles")).toBe("Subtitles");
  });

  test("a region keeps its distinction, because it is one a viewer can see", () => {
    expect(languageLabel("de-AT", "Subtitles")).toBe("Austrian German");
  });

  test("a code nobody recognises is shown as itself rather than hidden", () => {
    expect(languageLabel("zz", "Subtitles")).toBe("zz");
  });

  test("a missing code never reaches the menu as a blank row", () => {
    expect(languageLabel("", "Subtitles")).toBe("Subtitles");
    expect(languageLabel(null, "Subtitles")).toBe("Subtitles");
    expect(languageLabel(undefined, "Subtitles")).toBe("Subtitles");
  });

  test("a malformed tag is survived rather than thrown", () => {
    // The server only ever sends `[A-Za-z]{2,8}`, but a label is not worth a
    // broken player if that ever changes.
    expect(languageLabel("a", "Subtitles")).toBe("a");
    expect(languageLabel("!!", "Subtitles")).toBe("!!");
  });
});
