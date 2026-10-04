/** Covers merging a profile's role across devices: a kid's limit, a PIN, a parent, the one admin. */

import { describe, expect, test } from "bun:test";
import { mergeStates } from "../src/state/merge";
import type { ProfileState, SyncRecord } from "../src/state/sync-record";

const HASH_A = "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924";
const HASH_B = "48618b45393cde1c8841b73b2ca684f9b39a2efcbf35d4bf602d7f908b5137f8";
const SALT = "00112233445566778899aabbccddeeff";

/** One device's document; every profile is `Mia` unless it says otherwise. */
const doc = (device: string, ...profiles: Partial<ProfileState>[]): SyncRecord => ({
  format: 1,
  device,
  writtenAt: 0,
  profiles: profiles.map((one) => ({ name: "Mia", progress: [], watched: [], ...one })),
});

/** The merged viewer `name`, checked to be the same whichever order the documents came in. */
function merged(records: SyncRecord[], name = "mia") {
  const forward = mergeStates(records).profiles.find((profile) => profile.name === name);
  const backward = mergeStates([...records].reverse()).profiles.find((profile) => profile.name === name);
  expect(backward).toEqual(forward);
  return forward!;
}

/** Who the merge names admin, in both orders. */
function admins(records: SyncRecord[]): string[] {
  const named = (state: ReturnType<typeof mergeStates>) =>
    state.profiles.filter((profile) => profile.admin).map((profile) => profile.name).sort();
  const forward = named(mergeStates(records));
  expect(named(mergeStates([...records].reverse()))).toEqual(forward);
  return forward;
}

describe("a kid's limit", () => {
  test("the newer change wins, whichever device made it", () => {
    expect(merged([
      doc("laptop", { kids: true, kidsAge: { age: 12, updatedAt: 1000 } }),
      doc("tv", { kids: true, kidsAge: { age: 6, updatedAt: 2000 } }),
    ]).kidsAge).toEqual({ age: 6, updatedAt: 2000 });
  });

  test("a tie breaks by device id, the same way on every machine", () => {
    expect(merged([
      doc("laptop", { kids: true, kidsAge: { age: 6, updatedAt: 1000 } }),
      doc("tv", { kids: true, kidsAge: { age: 12, updatedAt: 1000 } }),
    ]).kidsAge).toEqual({ age: 12, updatedAt: 1000 });
  });

  test("an older build's kid, with no limit anywhere, is FSK 12 at time 0", () => {
    expect(merged([doc("old-tv", { kids: true })]).kidsAge).toEqual({ age: 12, updatedAt: 0 });
  });

  test("and loses to a limit a parent set", () => {
    expect(merged([
      doc("old-tv", { kids: true }),
      doc("laptop", { kids: true, kidsAge: { age: 6, updatedAt: 5 } }),
    ]).kidsAge).toEqual({ age: 6, updatedAt: 5 });
  });

  test("is never said of a grown-up", () => {
    expect("kidsAge" in merged([doc("laptop", { kidsAge: { age: 6, updatedAt: 5 } })])).toBe(false);
  });
});

describe("a grown-up's PIN", () => {
  test("the newer one wins", () => {
    expect(merged([
      doc("laptop", { name: "André", pin: { hash: HASH_A, salt: SALT, updatedAt: 1000 } }),
      doc("tv", { name: "André", pin: { hash: HASH_B, salt: SALT, updatedAt: 2000 } }),
    ], "andré").pin).toEqual({ hash: HASH_B, salt: SALT, updatedAt: 2000 });
  });

  test("a tie breaks by device id", () => {
    expect(merged([
      doc("laptop", { name: "André", pin: { hash: HASH_A, salt: SALT, updatedAt: 1000 } }),
      doc("tv", { name: "André", pin: { hash: HASH_B, salt: SALT, updatedAt: 1000 } }),
    ], "andré").pin).toEqual({ hash: HASH_B, salt: SALT, updatedAt: 1000 });
  });

  test("is never carried by a kid", () => {
    expect("pin" in merged([doc("laptop", { kids: true, pin: { hash: HASH_A, salt: SALT, updatedAt: 1 } })])).toBe(false);
  });
});

describe("a kid's parent", () => {
  test("is the parent's normalised name", () => {
    expect(merged([doc("laptop", { kids: true, parent: " André " })]).parent).toBe("andré");
  });

  test("two documents disagreeing settle by device id", () => {
    expect(merged([
      doc("laptop", { kids: true, parent: "André" }),
      doc("tv", { kids: true, parent: "Maja" }),
    ]).parent).toBe("maja");
  });

  test("a document that says nothing does not unsay it", () => {
    expect(merged([doc("zz-old", { kids: true }), doc("laptop", { kids: true, parent: "André" })]).parent).toBe("andré");
  });

  test("is never said of a grown-up", () => {
    expect("parent" in merged([doc("laptop", { name: "André", parent: "Maja" })], "andré")).toBe(false);
  });
});

describe("a Kids mark's age", () => {
  /** The top-level Kids marks two devices hold for one title, merged both ways round. */
  function mark(...held: [device: string, row: { updatedAt: number; age?: 6; removed?: true }][]) {
    const records = held.map(([device, row]): SyncRecord => ({
      format: 1, device, writtenAt: 0, profiles: [], kids: [{ setId: "K1", ...row }],
    }));
    const forward = mergeStates(records).kids;
    expect(mergeStates([...records].reverse()).kids).toEqual(forward);
    return forward;
  }

  test("an older build's echo of a mark from 6, at the same time, does not strip the age — whichever id sorts higher", () => {
    // That build drops `age` on import and writes the mark back unchanged.
    expect(mark(["laptop", { updatedAt: 1000, age: 6 }], ["zz-old-tv", { updatedAt: 1000 }]))
      .toEqual([{ setId: "K1", updatedAt: 1000, age: 6 }]);
    expect(mark(["zz-laptop", { updatedAt: 1000, age: 6 }], ["old-tv", { updatedAt: 1000 }]))
      .toEqual([{ setId: "K1", updatedAt: 1000, age: 6 }]);
  });

  test("a newer mark from 12 still wins, and so does a newer removal", () => {
    expect(mark(["laptop", { updatedAt: 1000, age: 6 }], ["tv", { updatedAt: 2000 }]))
      .toEqual([{ setId: "K1", updatedAt: 2000 }]);
    expect(mark(["laptop", { updatedAt: 1000, age: 6 }], ["tv", { updatedAt: 2000, removed: true }]))
      .toEqual([{ setId: "K1", updatedAt: 2000, removed: true }]);
  });

  test("two marks alike in age still settle by device id", () => {
    expect(mark(["laptop", { updatedAt: 1000 }], ["tv", { updatedAt: 1000, removed: true }]))
      .toEqual([{ setId: "K1", updatedAt: 1000, removed: true }]);
  });
});

describe("the household's admin", () => {
  test("two devices claiming before they meet: the earliest claim is the only admin", () => {
    const records = [
      doc("laptop", { name: "André", admin: { claimedAt: 2000 } }),
      doc("tv", { name: "Maja", admin: { claimedAt: 1000 } }),
    ];
    expect(admins(records)).toEqual(["maja"]);
    expect(merged(records, "maja").admin).toEqual({ claimedAt: 1000 });
    expect("admin" in merged(records, "andré")).toBe(false);
  });

  test("a tie goes to the smaller name", () => {
    expect(admins([
      doc("laptop", { name: "Maja", admin: { claimedAt: 1000 } }),
      doc("tv", { name: "André", admin: { claimedAt: 1000 } }),
    ])).toEqual(["andré"]);
  });

  test("one viewer claimed on two devices keeps its earliest claim", () => {
    expect(merged([
      doc("laptop", { name: "André", admin: { claimedAt: 3000 } }),
      doc("tv", { name: "André", admin: { claimedAt: 1000 } }),
    ], "andré").admin).toEqual({ claimedAt: 1000 });
  });

  test("nobody claimed is no admin at all", () => {
    expect(admins([doc("laptop", { name: "André" })])).toEqual([]);
  });

  test("a claim on a viewer any device calls a kid is ignored, however early", () => {
    expect(admins([
      doc("laptop", { name: "Mia", admin: { claimedAt: 500 } }),
      doc("tv", { name: "Mia", kids: true }, { name: "André", admin: { claimedAt: 1000 } }),
    ])).toEqual(["andré"]);
  });
});
