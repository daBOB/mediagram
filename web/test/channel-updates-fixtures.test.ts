/**
 * Runs the JSON fixtures under `fixtures/channel-updates/` against the web's
 * update classifier and debouncer. The Android core's port reads the same
 * files (`crates/mediagram-core/tests/shared_channel_update_fixtures.rs`).
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import {
  Debouncer,
  classify,
  type ChannelUpdate,
  type ClassifyContext,
  type LibraryEvent,
} from "../src/telegram/updates";

const FIXTURES = join(import.meta.dir, "fixtures", "channel-updates");

function load<T>(file: string): T {
  return JSON.parse(readFileSync(join(FIXTURES, file), "utf8")) as T;
}

describe("classify fixtures", () => {
  interface Case {
    name: string;
    context: ClassifyContext;
    update: ChannelUpdate;
    expect: LibraryEvent | null;
  }

  for (const one of load<Case[]>("classify.json")) {
    test(one.name, () => {
      expect(classify(one.update, one.context)).toBe(one.expect);
    });
  }
});

describe("debounce fixtures", () => {
  type Step = { offer: LibraryEvent; at: number } | { take: number; expect: LibraryEvent[] };
  interface Case {
    name: string;
    window: number;
    steps: Step[];
  }

  for (const one of load<Case[]>("debounce.json")) {
    test(one.name, () => {
      const debouncer = new Debouncer(one.window);
      for (const step of one.steps) {
        if ("offer" in step) debouncer.offer(step.offer, step.at);
        else expect(debouncer.take(step.take)).toEqual(step.expect);
      }
    });
  }
});

describe("nextDue", () => {
  test("is idle with nothing pending, and the earliest window otherwise", () => {
    const debouncer = new Debouncer(5000);
    expect(debouncer.nextDue()).toBeNull();
    debouncer.offer("index", 2000);
    debouncer.offer("state", 1000);
    expect(debouncer.nextDue()).toBe(6000);
    debouncer.take(6000);
    expect(debouncer.nextDue()).toBe(7000);
  });
});
