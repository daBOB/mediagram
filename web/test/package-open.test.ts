/**
 * Opening a package.
 *
 * One shot over the whole file, never a streaming wrapper: plaintext is
 * returned only after the tag verifies, so a caller cannot act on bytes that
 * were never authenticated. The format document records why — on Android a
 * streaming cipher swallows the tag failure and hands back truncated
 * plaintext instead of failing.
 */

import { describe, expect, test } from "bun:test";
import { createCipheriv, randomBytes } from "node:crypto";

import { NONCE_LEN, TAG_LEN, openPackage, parseKey } from "../src/package/open";

const KEY = Buffer.alloc(32, 7);
const AAD = new TextEncoder().encode(
  '{"format":1,"created_at":1,"key_id":"aabbccdd","schema":4,"spec":4}',
);

/** Seals the way the exporter does: nonce || ciphertext || tag. */
function seal(key: Buffer, plaintext: Uint8Array, aad: Uint8Array): Uint8Array {
  const nonce = randomBytes(NONCE_LEN);
  const cipher = createCipheriv("aes-256-gcm", key, nonce);
  cipher.setAAD(aad);
  const body = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  return new Uint8Array(Buffer.concat([nonce, body, cipher.getAuthTag()]));
}

const PLAINTEXT = new TextEncoder().encode("the archive would be here");

describe("the package key", () => {
  test("32 bytes of base64 is a key", () => {
    expect(parseKey(Buffer.alloc(32, 1).toString("base64"))).toHaveLength(32);
  });

  test("anything else is refused, and never quoted back", () => {
    const secretish = Buffer.alloc(31, 9).toString("base64");
    for (const bad of ["", "not base64!!", secretish]) {
      let message = "";
      try {
        parseKey(bad);
      } catch (error) {
        message = (error as Error).message;
      }
      expect(message).not.toBe("");
      // A malformed key is still key material; it must not reach a log.
      if (bad !== "") expect(message).not.toContain(bad);
    }
  });
});

describe("opening a sealed package", () => {
  test("a package sealed with the key opens to its plaintext", () => {
    const sealed = seal(KEY, PLAINTEXT, AAD);

    expect(openPackage(KEY, sealed, AAD)).toEqual(PLAINTEXT);
  });

  test("a flipped ciphertext byte fails rather than returning anything", () => {
    const sealed = seal(KEY, PLAINTEXT, AAD);
    sealed[NONCE_LEN + 3]! ^= 1;

    expect(() => openPackage(KEY, sealed, AAD)).toThrow(/authentication|tag|key/i);
  });

  test("a flipped tag byte fails", () => {
    const sealed = seal(KEY, PLAINTEXT, AAD);
    sealed[sealed.length - 1]! ^= 1;

    expect(() => openPackage(KEY, sealed, AAD)).toThrow();
  });

  test("associated data that does not match fails", () => {
    // This is what stops an old package being served under a new pointer.
    const sealed = seal(KEY, PLAINTEXT, AAD);
    const altered = new TextEncoder().encode(
      '{"format":1,"created_at":2,"key_id":"aabbccdd","schema":4,"spec":4}',
    );

    expect(() => openPackage(KEY, sealed, altered)).toThrow();
  });

  test("the wrong key fails", () => {
    const sealed = seal(KEY, PLAINTEXT, AAD);

    expect(() => openPackage(Buffer.alloc(32, 8), sealed, AAD)).toThrow();
  });

  test("a file too short to hold a nonce and a tag is refused", () => {
    for (const length of [0, NONCE_LEN, NONCE_LEN + TAG_LEN - 1]) {
      expect(() => openPackage(KEY, new Uint8Array(length), AAD)).toThrow(/short/i);
    }
  });

  test("an empty archive still round-trips", () => {
    const sealed = seal(KEY, new Uint8Array(0), AAD);

    expect(openPackage(KEY, sealed, AAD)).toHaveLength(0);
  });
});
