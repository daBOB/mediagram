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
  const invalidRows = [null, [], 7, "not a row", true];

  test("non-object profiles are dropped while valid siblings survive", () => {
    const record = parseRecord(document_({
      profiles: [{ name: "Before" }, ...invalidRows, { name: "After" }],
    }))!;
    expect(record.profiles.map((profile) => profile.name)).toEqual(["Before", "After"]);
  });

  test.each(["progress", "watched", "watchlist", "collections", "kids"] as const)(
    "non-object %s rows are dropped while valid siblings survive",
    (kind) => {
      const row = (id: string) => kind === "collections"
        ? { id, name: id, items: [], updatedAt: 1 }
        : { setId: id, at: 5, updatedAt: 1 };
      const rows = [row("before"), ...invalidRows, row("after")];
      const input = kind === "kids" ? { kids: rows } : { profiles: [{ name: "André", [kind]: rows }] };
      const record = parseRecord(document_(input))!;
      const kept = kind === "kids" ? record.kids : record.profiles[0]![kind];
      expect(kept).toHaveLength(2);
      expect(kept).toMatchObject(kind === "collections"
        ? [{ id: "before" }, { id: "after" }]
        : [{ setId: "before" }, { setId: "after" }]);
    },
  );

  test.each([
    ["progress", "at"], ["progress", "updatedAt"], ["watched", "updatedAt"],
    ["watchlist", "updatedAt"], ["collections", "updatedAt"], ["kids", "updatedAt"],
  ] as const)("non-scalar %s.%s is dropped without losing valid siblings", (kind, field) => {
    for (const invalid of [{ toString: 0 }, {}, [1]]) {
      const row = (id: string) => kind === "collections"
        ? { id, name: id, items: [], updatedAt: 1 }
        : { setId: id, at: 5, updatedAt: 1 };
      const rows = [row("before"), { ...row("bad"), [field]: invalid }, row("after")];
      const input = kind === "kids" ? { kids: rows } : { profiles: [{ name: "André", [kind]: rows }] };
      const record = parseRecord(document_(input))!;
      const kept = kind === "kids" ? record.kids : record.profiles[0]![kind];
      expect(kept).toHaveLength(2);
      expect(kept).toMatchObject(kind === "collections"
        ? [{ id: "before" }, { id: "after" }]
        : [{ setId: "before" }, { setId: "after" }]);
    }
  });

  test("a non-scalar optional duration becomes unknown without losing any position", () => {
    for (const duration of [{ toString: 0 }, {}, [100]]) {
      const record = parseRecord(document_({ profiles: [{ name: "André", progress: [
        { setId: "before", at: 1, updatedAt: 1 },
        { setId: "bad-duration", at: 2, updatedAt: 1, duration },
        { setId: "after", at: 3, updatedAt: 1 },
      ] }] }))!;
      expect(record.profiles[0]!.progress).toMatchObject([
        { setId: "before" }, { setId: "bad-duration", duration: null }, { setId: "after" },
      ]);
    }
  });

  test("non-scalar document numbers are refused or defaulted without coercion", () => {
    for (const value of [{ toString: 0 }, {}, [1]]) {
      expect(parseRecord(document_({ format: value }))).toBeNull();
      const record = parseRecord(document_({ writtenAt: value }))!;
      expect(record.writtenAt).toBe(0);
      expect(record.profiles[0]!.progress[0]!.at).toBe(742);
    }
  });

  test("numeric primitives accepted by older documents keep their meaning", () => {
    const record = parseRecord(document_({ format: "1", writtenAt: "12", profiles: [{
      name: "André", progress: [
        { setId: "strings", at: "42", duration: "100", updatedAt: "2" },
        { setId: "null", at: null, duration: null, updatedAt: true },
        { setId: "boolean", at: false, duration: true, updatedAt: "3" },
      ],
    }] }))!;
    expect(record.writtenAt).toBe(12);
    expect(record.profiles[0]!.progress).toEqual([
      { setId: "strings", at: 42, duration: 100, updatedAt: 2 },
      { setId: "null", at: 0, duration: null, updatedAt: 1 },
      { setId: "boolean", at: 0, duration: 1, updatedAt: 3 },
    ]);
  });

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
