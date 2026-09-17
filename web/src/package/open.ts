/**
 * The package cipher: AES-256-GCM over the whole file.
 *
 * `nonce (12 bytes) || ciphertext || tag (16 bytes)`, with the pointer's five
 * identifying fields as associated data. An old package served under an
 * edited pointer fails its tag rather than being accepted as current.
 *
 * One shot, never a streaming wrapper. That is not a style preference: the
 * format document records that Android's `CipherInputStream` swallows the tag
 * failure and returns truncated plaintext, so a reader built that way
 * silently accepts tampered data. Returning plaintext only after the tag
 * verifies is the one shape that cannot be misused.
 */

import { createDecipheriv } from "node:crypto";

/** Bytes of the random nonce prefixed to every package. */
export const NONCE_LEN = 12;

/** Bytes of the authentication tag GCM appends. */
export const TAG_LEN = 16;

const KEY_LEN = 32;

/**
 * Decodes a configured key.
 *
 * Errors never quote the input. A key that failed to parse is still key
 * material, and the one place it must not end up is a log.
 */
export function parseKey(encoded: string): Buffer {
  const trimmed = encoded.trim();
  if (trimmed === "") throw new Error("the package key is empty");

  const bytes = Buffer.from(trimmed, "base64");
  // Buffer.from ignores what it cannot decode rather than failing, so a
  // round trip is the only way to know the input was really base64.
  if (bytes.toString("base64").replace(/=+$/, "") !== trimmed.replace(/=+$/, "")) {
    throw new Error("the package key is not valid base64");
  }
  if (bytes.length !== KEY_LEN) {
    throw new Error(`the package key decodes to ${bytes.length} bytes, expected ${KEY_LEN}`);
  }
  return bytes;
}

/**
 * Verifies and decrypts a package, returning plaintext only once the tag has
 * checked out.
 *
 * A failure here means a wrong key, a corrupt download, or an edited pointer.
 * It is reported, never retried: retrying a tag failure is how a reader ends
 * up hammering a host that is feeding it something it must not accept.
 */
export function openPackage(key: Buffer, sealed: Uint8Array, aad: Uint8Array): Uint8Array {
  if (sealed.length < NONCE_LEN + TAG_LEN) {
    throw new Error("the package is too short to hold a nonce and a tag");
  }

  const nonce = sealed.subarray(0, NONCE_LEN);
  const body = sealed.subarray(NONCE_LEN, sealed.length - TAG_LEN);
  const tag = sealed.subarray(sealed.length - TAG_LEN);

  const decipher = createDecipheriv("aes-256-gcm", key, nonce);
  decipher.setAAD(aad);
  decipher.setAuthTag(tag);
  try {
    // `final()` is what checks the tag, so both halves are needed and the
    // result is only used after it returns.
    const head = decipher.update(body);
    const rest = decipher.final();
    return new Uint8Array(Buffer.concat([head, rest]));
  } catch {
    throw new Error(
      "the package failed authentication: wrong key, corrupt file, or edited pointer",
    );
  }
}
