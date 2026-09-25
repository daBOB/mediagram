/** Covers `merge`: reconciling what several devices say about one viewing. */

import { describe, expect, test } from "bun:test";
import { mergeStates } from "../src/state/merge";
import type { SyncRecord } from "../src/state/sync-record";

const from = (
  device: string,
  name: string,
  progress: Array<[string, number, number]> = [],
  watched: Array<[string, number]> = [],
): SyncRecord => ({
  format: 1,
  device,
  writtenAt: 0,
  profiles: [
    {
      name,
      progress: progress.map(([setId, at, updatedAt]) => ({
        setId,
        at,
        duration: 1204,
        updatedAt,
      })),
      watched: watched.map(([setId, updatedAt]) => ({ setId, updatedAt })),
    },
  ],
});

const positions = (merged: ReturnType<typeof mergeStates>, name = "andré") =>
  Object.fromEntries(
    (merged.profiles.find((p) => p.name === name)?.progress ?? []).map((r) => [r.setId, r.at]),
  );

describe("last writer wins, per title", () => {
  test("the later position for a title is the one that counts", () => {
    const merged = mergeStates([
      from("laptop", "André", [["01A", 100, 1000]]),
      from("desktop", "André", [["01A", 900, 2000]]),
    ]);
    expect(positions(merged)).toEqual({ "01A": 900 });
  });

  test("whichever order the documents arrive in", () => {
    // Telegram hands them over in whatever order it likes.
    const a = from("laptop", "André", [["01A", 100, 1000]]);
    const b = from("desktop", "André", [["01A", 900, 2000]]);
    expect(positions(mergeStates([a, b]))).toEqual(positions(mergeStates([b, a])));
  });

  test("two titles watched on two machines both survive", () => {
    // The case the whole design is for: S1E4 on the phone, S1E9 on the laptop.
    const merged = mergeStates([
      from("phone", "André", [["01E4", 300, 1000]]),
      from("laptop", "André", [["01E9", 700, 1000]]),
    ]);
    expect(positions(merged)).toEqual({ "01E4": 300, "01E9": 700 });
  });

  test("merging a document with itself changes nothing", () => {
    const one = from("laptop", "André", [["01A", 100, 1000]]);
    expect(mergeStates([one, one])).toEqual(mergeStates([one]));
  });
});

describe("a tie", () => {
  test("breaks the same way on every machine", () => {
    // Arbitrary, but consistently arbitrary: two devices that disagreed here
    // would push their disagreement back and forth for ever.
    const a = from("aaa", "André", [["01A", 111, 5000]]);
    const b = from("zzz", "André", [["01A", 222, 5000]]);
    expect(positions(mergeStates([a, b]))).toEqual({ "01A": 222 });
    expect(positions(mergeStates([b, a]))).toEqual({ "01A": 222 });
  });
});

describe("a finished title stays finished", () => {
  test("a completion beats a position a device had not heard about", () => {
    // The laptop finished it; the phone still holds where it had got to.
    const merged = mergeStates([
      from("phone", "André", [["01A", 900, 1000]]),
      from("laptop", "André", [], [["01A", 2000]]),
    ]);
    expect(positions(merged)).toEqual({});
    expect(merged.profiles[0]!.watched).toHaveLength(1);
  });

  test("even when both carry the same millisecond", () => {
    // `clearProgress` and `setWatched` are called in one moment, so they can.
    // In a tie the completion is the later intention.
    const merged = mergeStates([
      from("phone", "André", [["01A", 900, 1000]]),
      from("laptop", "André", [], [["01A", 1000]]),
    ]);
    expect(positions(merged)).toEqual({});
  });

  test("but starting it again after finishing does come back", () => {
    const merged = mergeStates([
      from("laptop", "André", [], [["01A", 1000]]),
      from("phone", "André", [["01A", 30, 2000]]),
    ]);
    expect(positions(merged)).toEqual({ "01A": 30 });
  });
});

describe("who the viewer is", () => {
  test("the same name typed differently is the same person", () => {
    const merged = mergeStates([
      from("laptop", "André", [["01A", 100, 1000]]),
      from("desktop", " andré ", [["01B", 200, 1000]]),
    ]);
    expect(merged.profiles).toHaveLength(1);
    expect(positions(merged)).toEqual({ "01A": 100, "01B": 200 });
  });

  test("and two different people stay apart", () => {
    const merged = mergeStates([
      from("laptop", "André", [["01A", 100, 1000]]),
      from("laptop", "Sam", [["01A", 900, 2000]]),
    ]);
    expect(merged.profiles).toHaveLength(2);
    expect(positions(merged, "andré")).toEqual({ "01A": 100 });
    expect(positions(merged, "sam")).toEqual({ "01A": 900 });
  });

  test("a profile with no usable name is dropped, not merged into one bucket", () => {
    const merged = mergeStates([
      { format: 1, device: "d", writtenAt: 0, profiles: [{ name: "  ", progress: [], watched: [] }] },
    ]);
    expect(merged.profiles).toEqual([]);
  });
});

describe("nothing to merge", () => {
  test("is not an error", () => {
    expect(mergeStates([])).toEqual({ profiles: [], kids: [], editorsChoice: [] });
    expect(mergeStates([from("laptop", "André")])).toEqual({
      profiles: [
        { name: "andré", displayName: "André", progress: [], watched: [], watchlist: [], collections: [] },
      ],
      kids: [],
      editorsChoice: [],
    });
  });
});

describe("the name a new machine will greet you by", () => {
  test("is the one somebody typed, not the identity it is matched on", () => {
    // Creating from the normalised key would introduce you as "andré".
    const merged = mergeStates([from("laptop", " André ")]);
    expect(merged.profiles[0]!.name).toBe("andré");
    expect(merged.profiles[0]!.displayName).toBe("André");
  });
});
