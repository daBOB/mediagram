/**
 * The pointer: a plaintext file on a public URL that says where the package
 * is and what it claims to be.
 *
 * Nothing in it is trustworthy on its own. Five of its ten fields are fed to
 * the cipher as associated data and so cannot be altered without the package
 * failing to open; the other five describe where the bytes were fetched from
 * and are hints, checked only to avoid a download that cannot work.
 *
 * Which is why `sha256` may never be used to decide "I already have this".
 * Anyone who can rewrite the pointer can set it to the digest of the copy the
 * reader holds, and a reader that skips on that match never runs the cipher —
 * so an update can be suppressed without touching the package at all.
 * Freshness comes from the authenticated fields. See `refresh.ts`.
 *
 * Normative description: `docs/mlib-package-v1.md`.
 */

/** The only package layout this reader understands. */
export const PACKAGE_FORMAT = 1;

/** The only cipher format 1 defines. */
export const CIPHER = "aes-256-gcm";

/**
 * The most a package may be.
 *
 * The tag covers the whole file, so a reader holds the ciphertext and its
 * plaintext at once and has to be able to afford both. The exporter refuses
 * to produce anything over 48 MB.
 */
export const MAX_PACKAGE_BYTES = 64 * 1024 * 1024;

const KEY_ID_HEX = 8;
const SHA256_HEX = 64;
/**
 * A package file name and nothing else.
 *
 * The name is joined to the configured base to build the download URL, so it
 * must not be able to carry a path, a query, or a host into it.
 */
const FILE_NAME = /^[A-Za-z0-9._-]{1,128}$/;

export interface Pointer {
  format: number;
  created_at: number;
  file: string;
  url: string;
  bytes: number;
  sha256: string;
  cipher: string;
  key_id: string;
  schema: number;
  spec: number;
}

/** Why a pointer cannot be used. Carries a reason a person can act on. */
export interface Refusal {
  reason: string;
}

/**
 * The five fields the cipher authenticates, in the order it authenticates
 * them.
 *
 * `key_id` is constrained to lowercase hex precisely so these bytes contain
 * nothing two JSON writers could escape differently: serde_json on one side
 * and `JSON.stringify` on the other have to agree byte for byte or every
 * package fails to open.
 */
export function associatedData(pointer: Pointer): Uint8Array {
  return new TextEncoder().encode(
    JSON.stringify({
      format: pointer.format,
      created_at: pointer.created_at,
      key_id: pointer.key_id,
      schema: pointer.schema,
      spec: pointer.spec,
    }),
  );
}

/**
 * Parses pointer JSON, refusing anything of the wrong shape.
 *
 * Types are checked rather than coerced. A `"1"` quietly becoming `1` would
 * change the associated data this reader rebuilds, and the package would then
 * fail its tag — a malformed file reported as what looks exactly like an
 * attack.
 */
export function parsePointer(text: string): Pointer {
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    throw new Error("the pointer is not JSON");
  }
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error("the pointer is not a JSON object");
  }

  const raw = value as Record<string, unknown>;
  for (const name of ["format", "created_at", "bytes", "schema", "spec"]) {
    if (!Number.isInteger(raw[name])) throw new Error(`pointer field \`${name}\` is not an integer`);
  }
  for (const name of ["file", "url", "sha256", "cipher", "key_id"]) {
    if (typeof raw[name] !== "string") throw new Error(`pointer field \`${name}\` is not a string`);
  }

  return {
    format: raw.format as number,
    created_at: raw.created_at as number,
    file: raw.file as string,
    url: raw.url as string,
    bytes: raw.bytes as number,
    sha256: raw.sha256 as string,
    cipher: raw.cipher as string,
    key_id: raw.key_id as string,
    schema: raw.schema as number,
    spec: raw.spec as number,
  };
}

/** Lowercase hex of an exact length, and nothing else. */
function isLowerHex(text: string, length: number): boolean {
  return text.length === length && /^[0-9a-f]+$/.test(text);
}

/**
 * Whether the package a pointer names can be used, decided before anything is
 * fetched. `null` when it can.
 *
 * The order is the document's, and it is deliberate: reporting the last
 * problem found rather than the first sends whoever is debugging a failed
 * refresh after the wrong one.
 */
export function pointerIsReadable(pointer: Pointer, supportedSchema: number[]): Refusal | null {
  if (pointer.format !== PACKAGE_FORMAT) {
    return { reason: `package format ${pointer.format} is newer than this reader understands` };
  }
  if (pointer.cipher !== CIPHER) {
    return { reason: `package cipher \`${pointer.cipher}\` is not recognised` };
  }
  if (!supportedSchema.includes(pointer.schema)) {
    return { reason: `package holds schema ${pointer.schema}, which this player cannot query` };
  }
  if (!isLowerHex(pointer.key_id, KEY_ID_HEX)) {
    return { reason: "pointer field `key_id` is malformed" };
  }
  if (!isLowerHex(pointer.sha256, SHA256_HEX)) {
    return { reason: "pointer field `sha256` is malformed" };
  }
  if (!FILE_NAME.test(pointer.file)) {
    // It is joined to the configured base and becomes a URL, so it is a plain
    // file name or it is nothing.
    return { reason: "pointer field `file` is malformed" };
  }
  if (pointer.created_at < 0) {
    return { reason: "pointer field `created_at` is malformed" };
  }
  if (pointer.bytes <= 0) {
    return { reason: "pointer field `bytes` is malformed" };
  }
  if (pointer.bytes > MAX_PACKAGE_BYTES) {
    return {
      reason: `the package claims ${pointer.bytes} bytes, over the ${MAX_PACKAGE_BYTES} byte limit`,
    };
  }
  return null;
}
