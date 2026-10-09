/**
 * Runs the JSON fixtures under `fixtures/together/` against the watch-together
 * session rules. The Kotlin port in `android/core/model` reads the same files.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { parseSession, writeSession, type Session } from "../public/lib/together/session.js";

const FIXTURES = join(import.meta.dir, "fixtures", "together");

function load<T>(file: string): T {
  return JSON.parse(readFileSync(join(FIXTURES, file), "utf8")) as T;
}

describe("format fixtures", () => {
  for (const one of load<{ name: string; text: string; expect: Session | null }[]>("format.json")) {
    test(one.name, () => {
      const parsed = parseSession(one.text);
      expect(parsed).toEqual(one.expect);
      if (parsed !== null) expect(parseSession(writeSession(parsed))).toEqual(parsed);
    });
  }
});
