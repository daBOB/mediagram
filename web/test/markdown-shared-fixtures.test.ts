/**
 * The notes parser's contract with the Android app.
 *
 * `cases.json` holds inputs and the exact tree `parseMarkdown` makes of each.
 * The phone's port (`android/core/model/.../markdown`) runs the same file, so
 * a lesson's notes read the same on both surfaces — including the quirks, which
 * are pinned here rather than fixed on one side only.
 */

import { describe, expect, test } from "bun:test";
import cases from "./fixtures/markdown/cases.json";
import { parseMarkdown } from "../public/lib/playback/notes/markdown.js";

describe("shared markdown fixtures", () => {
  for (const { name, input, blocks } of cases) {
    test(name, () => {
      expect(parseMarkdown(input)).toEqual(blocks);
    });
  }
});
