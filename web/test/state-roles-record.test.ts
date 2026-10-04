/** Covers reading a profile's role keys, and a Kids mark's age, off another device's document. */

import { describe, expect, test } from "bun:test";
import { parseRecord } from "../src/state/sync-record";

const HASH = "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924";
const SALT = "00112233445566778899aabbccddeeff";

/** A document with one profile, `Mia`, carrying `extra`, and `top` beside it. */
const withProfile = (extra: Record<string, unknown>, top: Record<string, unknown> = {}) =>
  parseRecord(JSON.stringify({
    format: 1,
    device: "laptop",
    writtenAt: 1,
    profiles: [{ name: "Mia", progress: [], watched: [], ...extra }],
    ...top,
  }))!;

describe("a profile's role keys", () => {
  test("read back as written", () => {
    const [profile] = withProfile({
      admin: { claimedAt: 1000 },
      kids: true,
      kidsAge: { age: 6, updatedAt: 0 },
      parent: "André",
      pin: { hash: HASH, salt: SALT, updatedAt: 2000 },
    }).profiles;
    expect(profile).toMatchObject({
      admin: { claimedAt: 1000 },
      kids: true,
      kidsAge: { age: 6, updatedAt: 0 },
      parent: "André",
      pin: { hash: HASH, salt: SALT, updatedAt: 2000 },
    });
  });

  test("an absent key is nothing said, not an empty one", () => {
    const [profile] = withProfile({}).profiles;
    for (const key of ["admin", "kids", "kidsAge", "parent", "pin"]) expect(key in profile!).toBe(false);
  });

  test("a malformed key is dropped on its own; the profile and its rows survive", () => {
    const bad: Record<string, unknown>[] = [
      { admin: { claimedAt: 0 } },
      { admin: { claimedAt: "soon" } },
      { admin: true },
      { kids: "yes" },
      { kidsAge: { age: 9, updatedAt: 1 } },
      { kidsAge: { age: "6", updatedAt: 1 } },
      { kidsAge: { age: 6, updatedAt: -1 } },
      { kidsAge: { age: 6 } },
      { parent: "" },
      { parent: "   " },
      { parent: 7 },
      { pin: { hash: HASH.toUpperCase(), salt: SALT, updatedAt: 1 } },
      { pin: { hash: HASH, salt: "abc", updatedAt: 1 } },
      { pin: { hash: HASH, salt: SALT, updatedAt: 0 } },
      { pin: { hash: HASH, salt: SALT } },
    ];
    for (const extra of bad) {
      const [profile] = withProfile({ ...extra, progress: [{ setId: "01A", at: 5, updatedAt: 1 }] }).profiles;
      expect(profile!.name).toBe("Mia");
      expect(profile!.progress).toHaveLength(1);
      for (const key of Object.keys(extra)) expect(key in profile!).toBe(false);
    }
  });

  test("one bad key does not cost a good one beside it", () => {
    const [profile] = withProfile({ admin: { claimedAt: -5 }, kidsAge: { age: 12, updatedAt: 3 } }).profiles;
    expect("admin" in profile!).toBe(false);
    expect(profile!.kidsAge).toEqual({ age: 12, updatedAt: 3 });
  });

  test("a time past 2^53 - 1 drops the key, as it drops any synced row; 2^53 - 1 itself is kept", () => {
    const LAST = Number.MAX_SAFE_INTEGER;
    // Built as text: a JS number cannot carry 2^53 + 1 and a 64-bit integer exactly.
    const parse = (keys: string) =>
      parseRecord(`{"format":1,"device":"laptop","writtenAt":1,"profiles":[{"name":"Mia","progress":[],"watched":[],${keys}}]}`)!
        .profiles[0]!;
    for (const past of ["9007199254740992", "9223372036854775807", "1e300"]) {
      const profile = parse(
        `"admin":{"claimedAt":${past}},"kidsAge":{"age":6,"updatedAt":${past}},` +
          `"pin":{"hash":"${HASH}","salt":"${SALT}","updatedAt":${past}}`,
      );
      for (const key of ["admin", "kidsAge", "pin"]) expect(key in profile).toBe(false);
    }
    expect(parse(
      `"admin":{"claimedAt":${LAST}},"kidsAge":{"age":6,"updatedAt":${LAST}},` +
        `"pin":{"hash":"${HASH}","salt":"${SALT}","updatedAt":${LAST}}`,
    )).toMatchObject({
      admin: { claimedAt: LAST },
      kidsAge: { age: 6, updatedAt: LAST },
      pin: { hash: HASH, salt: SALT, updatedAt: LAST },
    });
  });
});

describe("a Kids mark's age", () => {
  test("only the literal 6 on a live mark is kept; anything else reads as from 12", () => {
    const record = withProfile({}, {
      kids: [
        { setId: "K6", updatedAt: 1, age: 6 },
        { setId: "K12", updatedAt: 1, age: 12 },
        { setId: "KS", updatedAt: 1, age: "6" },
        { setId: "KN", updatedAt: 1 },
        { setId: "KR", updatedAt: 1, removed: true, age: 6 },
      ],
    });
    expect(record.kids).toEqual([
      { setId: "K6", updatedAt: 1, age: 6 },
      { setId: "K12", updatedAt: 1 },
      { setId: "KS", updatedAt: 1 },
      { setId: "KN", updatedAt: 1 },
      { setId: "KR", updatedAt: 1, removed: true },
    ]);
  });

  test("an age does not save a mark whose time is past 2^53 - 1", () => {
    const record = parseRecord(
      `{"format":1,"device":"laptop","writtenAt":1,"profiles":[],"kids":[{"setId":"K6","updatedAt":9007199254740992,"age":6}]}`,
    )!;
    expect(record.kids).toEqual([]);
  });

  test("a watchlist row and an editor's choice never carry one", () => {
    const record = withProfile(
      { watchlist: [{ setId: "W", updatedAt: 1, age: 6 }] },
      { editorsChoice: [{ setId: "E", updatedAt: 1, age: 6 }] },
    );
    expect(record.profiles[0]!.watchlist).toEqual([{ setId: "W", updatedAt: 1 }]);
    expect(record.editorsChoice).toEqual([{ setId: "E", updatedAt: 1 }]);
  });
});
