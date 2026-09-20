/** Covers `sync-record`: reading a document another machine wrote. */

import { describe, expect, test } from "bun:test";
import { normalName, parseRecord, SYNC_FORMAT } from "../src/state/sync-record";

const document_ = (over: Record<string, unknown> = {}) =>
  JSON.stringify({
    format: 1,
    device: "laptop",
    writtenAt: 1789000000000,
    profiles: [
      {
        name: "André",
        localId: "b398013d",
        progress: [{ setId: "01A", at: 742, duration: 1204, updatedAt: 1789000000000 }],
        watched: [{ setId: "01B", updatedAt: 1789000000000 }],
      },
    ],
    ...over,
  });

describe("a document this player wrote itself", () => {
  test("reads back as it was written", () => {
    const record = parseRecord(document_())!;
    expect(record.device).toBe("laptop");
    expect(record.profiles[0]!.name).toBe("André");
    expect(record.profiles[0]!.progress[0]).toEqual({
      setId: "01A",
      at: 742,
      duration: 1204,
      updatedAt: 1789000000000,
    });
  });
});

describe("a document that cannot be trusted", () => {
  test("nothing that is not JSON", () => {
    for (const text of ["", "{{{", "null", "[]", '"a string"', "7"]) {
      expect(parseRecord(text)).toBeNull();
    }
  });

  test("a format from the future is ignored, not guessed at", () => {
    // Reading it half-right would merge a half-right answer into a database
    // that is the source of truth for this machine.
    expect(parseRecord(document_({ format: SYNC_FORMAT + 1 }))).toBeNull();
  });

  test("and so is one with no format, or a nonsense one", () => {
    for (const format of [undefined, 0, -1, "one", 1.5]) {
      expect(parseRecord(document_({ format }))).toBeNull();
    }
  });

  test("a document with no device cannot be attributed, so it is dropped", () => {
    // The device id is what a tie breaks on and what a writer recognises as
    // its own. A document without one cannot take part in either.
    expect(parseRecord(document_({ device: "  " }))).toBeNull();
  });
});

describe("one bad row does not cost the rest", () => {
  test("an unreadable position is dropped and its neighbours survive", () => {
    const record = parseRecord(
      document_({
        profiles: [
          {
            name: "André",
            progress: [
              { setId: "01A", at: 742, updatedAt: 1000 },
              { setId: "01B", at: "halfway", updatedAt: 1000 },
              { setId: "", at: 5, updatedAt: 1000 },
              { setId: "01C", at: 5, updatedAt: 0 },
              { setId: "01D", at: 60, updatedAt: 2000 },
            ],
            watched: [],
          },
        ],
      }),
    )!;
    expect(record.profiles[0]!.progress.map((r) => r.setId)).toEqual(["01A", "01D"]);
  });

  test("nought is a position and is kept", () => {
    // The start of a film. `Number(0)` is falsy and this has bitten the
    // player's other readers three times.
    const record = parseRecord(
      document_({
        profiles: [{ name: "A", progress: [{ setId: "01A", at: 0, updatedAt: 1 }], watched: [] }],
      }),
    )!;
    expect(record.profiles[0]!.progress[0]!.at).toBe(0);
  });

  test("a runtime nobody knows is null, not nought", () => {
    const record = parseRecord(
      document_({
        profiles: [{ name: "A", progress: [{ setId: "01A", at: 5, updatedAt: 1 }], watched: [] }],
      }),
    )!;
    expect(record.profiles[0]!.progress[0]!.duration).toBeNull();
  });

  test("a profile with no name is dropped", () => {
    const record = parseRecord(document_({ profiles: [{ name: 5, progress: [], watched: [] }] }))!;
    expect(record.profiles).toEqual([]);
  });

  test("and missing arrays are empty ones", () => {
    const record = parseRecord(document_({ profiles: [{ name: "A" }] }))!;
    expect(record.profiles[0]).toEqual({ name: "A", localId: undefined, progress: [], watched: [] });
  });
});

describe("who a viewer is", () => {
  test("case and space are not part of a name", () => {
    expect(normalName(" André ")).toBe("andré");
    expect(normalName("ANDRÉ")).toBe("andré");
  });

  test("and neither is how the accent was typed", () => {
    // Composed vs. an `e` with a combining accent: the same name, two byte
    // sequences, and not otherwise equal.
    expect(normalName("André")).toBe(normalName("André"));
  });

  test("a name that is not one is nobody", () => {
    for (const name of [undefined, null, "", "   ", 5]) {
      expect(normalName(name)).toBeNull();
    }
  });
});
