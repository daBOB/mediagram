/**
 * Gunzipping and untarring a package that has already been authenticated.
 *
 * Written by hand rather than taken from a library, for two reasons. The
 * validation *is* the feature — every member name reaches a filesystem path,
 * and the refusals below are the whole point — and tar is four fields of a
 * 512-byte header, which is less code than vetting a dependency that unpacks
 * things by default.
 *
 * Nothing is written until every member has been read and checked. A package
 * refused halfway would otherwise leave a directory holding some of an
 * archive the reader has decided not to trust.
 */

import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join, normalize, resolve, sep } from "node:path";
import { gunzipSync } from "node:zlib";

const BLOCK = 512;
/** Regular files only. `\0` is the old spelling of `0`. */
const REGULAR = new Set(["0", "\0"]);

interface Member {
  name: string;
  body: Uint8Array;
}

/**
 * Unpacks `packed` into `dest`, returning the member names in archive order.
 *
 * Refuses any member whose path is absolute or climbs out of `dest`, and
 * anything that is not a regular file — a symlink in particular, which is how
 * an archive reaches a path it never names.
 */
export async function unpackTo(packed: Uint8Array, dest: string): Promise<string[]> {
  const members = readMembers(gunzip(packed));

  // Checked in full first: see the file comment.
  const root = resolve(dest);
  for (const entry of members) {
    // The name is judged on its own first. `join` silently treats an absolute
    // member name as relative to the target, so a resolved path alone would
    // accept `/etc/passwd` by quietly rewriting it.
    if (!memberNameIsSafe(entry.name)) {
      throw new Error(`archive member ${entry.name} escapes the target directory`);
    }
    const target = resolve(join(root, entry.name));
    if (!target.startsWith(root + sep)) {
      throw new Error(`archive member ${entry.name} escapes the target directory`);
    }
  }

  for (const entry of members) {
    const target = join(root, entry.name);
    await mkdir(dirname(target), { recursive: true });
    await writeFile(target, entry.body);
  }
  return members.map((entry) => entry.name);
}

function gunzip(packed: Uint8Array): Uint8Array {
  try {
    return new Uint8Array(gunzipSync(packed));
  } catch {
    throw new Error("the package is not a gzip stream");
  }
}

/** Reads every member of a tar, or throws saying why it could not. */
function readMembers(tar: Uint8Array): Member[] {
  const members: Member[] = [];
  let at = 0;

  while (at + BLOCK <= tar.length) {
    const header = tar.subarray(at, at + BLOCK);
    // Two consecutive zero blocks end an archive; one is enough to stop on.
    if (header.every((byte) => byte === 0)) break;
    at += BLOCK;

    const name = field(header, 0, 100);
    const typeflag = String.fromCharCode(header[156] ?? 0);
    if (!REGULAR.has(typeflag)) {
      // Long names and sparse files are GNU extensions the exporter never
      // writes, so they are refused rather than half-supported.
      throw new Error(`archive member ${name} is not a regular file`);
    }

    const size = octal(field(header, 124, 12));
    if (size === null) throw new Error(`archive member ${name} has an unreadable size`);
    if (at + size > tar.length) {
      throw new Error(`archive member ${name} is truncated`);
    }

    members.push({ name, body: tar.slice(at, at + size) });
    at += Math.ceil(size / BLOCK) * BLOCK;
  }

  if (members.length === 0) throw new Error("the archive holds no members");
  return members;
}

/** A NUL-terminated header field as text. */
function field(header: Uint8Array, offset: number, length: number): string {
  const raw = header.subarray(offset, offset + length);
  const end = raw.indexOf(0);
  return new TextDecoder().decode(end === -1 ? raw : raw.subarray(0, end)).trim();
}

function octal(text: string): number | null {
  if (!/^[0-7]+$/.test(text)) return null;
  const value = Number.parseInt(text, 8);
  return Number.isSafeInteger(value) && value >= 0 ? value : null;
}

/**
 * Whether a member name is one this reader will write.
 *
 * Exported because the refusal is worth stating on its own: a name is a
 * relative path of ordinary segments, and anything else — absolute, `..`,
 * a drive letter, a backslash someone will treat as a separator — is not.
 */
export function memberNameIsSafe(name: string): boolean {
  if (name === "" || name.startsWith("/") || name.includes("\\")) return false;
  if (/^[A-Za-z]:/.test(name)) return false;
  return !normalize(name)
    .split("/")
    .some((segment) => segment === ".." || segment === "");
}
