/**
 * The pointer is plaintext on a public URL, so everything in it is hostile
 * until proven otherwise. It is validated in the order the format document
 * gives, and every refusal happens *before* anything is downloaded.
 */

import { describe, expect, test } from "bun:test";

import {
  MAX_PACKAGE_BYTES,
  associatedData,
  parsePointer,
  pointerIsReadable,
} from "../src/package/pointer";

const GOOD = {
  format: 1,
  created_at: 1781568000,
  file: "prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc",
  url: "https://example.com/prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc",
  bytes: 8127744,
  sha256: "a".repeat(64),
  cipher: "aes-256-gcm",
  key_id: "630dcd29",
  schema: 4,
  spec: 4,
};

const readable = (over: Record<string, unknown> = {}) =>
  pointerIsReadable(parsePointer(JSON.stringify({ ...GOOD, ...over })), [4]);

describe("reading a pointer", () => {
  test("a well-formed one is accepted", () => {
    expect(readable()).toBeNull();
  });

  test("anything that is not an object of the right shape is refused", () => {
    for (const text of ["[]", '"hello"', "null", "7", "{}", '{"format":1}', "not json"]) {
      expect(() => parsePointer(text)).toThrow();
    }
  });

  test("a field of the wrong type is refused rather than coerced", () => {
    // `"1"` must not become 1: the associated data would then differ from
    // what the exporter authenticated, and the failure would look like an
    // attack instead of a malformed file.
    expect(() => parsePointer(JSON.stringify({ ...GOOD, format: "1" }))).toThrow();
    expect(() => parsePointer(JSON.stringify({ ...GOOD, created_at: "1781568000" }))).toThrow();
    expect(() => parsePointer(JSON.stringify({ ...GOOD, bytes: "10" }))).toThrow();
  });

  test("a format this reader does not know is refused", () => {
    expect(readable({ format: 2 })?.reason).toMatch(/format/i);
  });

  test("a cipher this format does not define is refused", () => {
    expect(readable({ cipher: "chacha20-poly1305" })?.reason).toMatch(/cipher/i);
  });

  test("a schema the player cannot query is refused", () => {
    expect(readable({ schema: 99 })?.reason).toMatch(/schema/i);
  });

  test("key_id and sha256 must be lowercase hex of the right length", () => {
    // Lowercase hex is what makes the associated data reproducible by a
    // different JSON writer: there is no character to escape differently.
    for (const key_id of ["630DCD29", "630dcd2", "630dcd299", "630dcd2g", ""]) {
      expect(readable({ key_id })?.reason).toMatch(/key_id/i);
    }
    for (const sha256 of ["A".repeat(64), "a".repeat(63), "z".repeat(64)]) {
      expect(readable({ sha256 })?.reason).toMatch(/sha256/i);
    }
  });

  test("a size over the ceiling is refused before the download", () => {
    expect(readable({ bytes: MAX_PACKAGE_BYTES + 1 })?.reason).toMatch(/large|limit|ceiling/i);
    expect(readable({ bytes: 0 })?.reason).toMatch(/bytes/i);
    expect(readable({ bytes: -1 })?.reason).toMatch(/bytes/i);
  });

  test("a negative created_at is refused", () => {
    expect(readable({ created_at: -1 })?.reason).toMatch(/created_at/i);
  });

  test("the format is checked before the cipher, and both before the hex fields", () => {
    // The document gives an order, and a reader that reports the last
    // problem it happens to find sends whoever is debugging the wrong way.
    const wrong = readable({ format: 2, cipher: "nope", key_id: "zz" });
    expect(wrong?.reason).toMatch(/format/i);
    expect(readable({ cipher: "nope", key_id: "zz" })?.reason).toMatch(/cipher/i);
  });
});

describe("the bytes the cipher authenticates", () => {
  test("exactly five fields, in the documented order", () => {
    const aad = new TextDecoder().decode(associatedData(parsePointer(JSON.stringify(GOOD))));

    expect(aad).toBe(
      '{"format":1,"created_at":1781568000,"key_id":"630dcd29","schema":4,"spec":4}',
    );
  });

  test("the download fields are not in it", () => {
    const aad = new TextDecoder().decode(associatedData(parsePointer(JSON.stringify(GOOD))));

    for (const excluded of ["file", "url", "bytes", "sha256", "cipher"]) {
      expect(aad).not.toContain(`"${excluded}"`);
    }
  });

  test("changing an authenticated field changes the bytes", () => {
    const one = associatedData(parsePointer(JSON.stringify(GOOD)));
    const two = associatedData(parsePointer(JSON.stringify({ ...GOOD, created_at: 1781568001 })));

    expect(one).not.toEqual(two);
  });

  test("changing a download field does not", () => {
    const one = associatedData(parsePointer(JSON.stringify(GOOD)));
    const two = associatedData(
      parsePointer(JSON.stringify({ ...GOOD, url: "https://elsewhere/x", bytes: 99 })),
    );

    expect(one).toEqual(two);
  });
});
