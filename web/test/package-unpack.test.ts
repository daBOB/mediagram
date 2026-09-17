/**
 * Unpacking, which is where authenticated bytes meet the filesystem.
 *
 * Authentic is not the same as well-formed. A package is written by our own
 * exporter, but a reader that trusts a member name because the tag verified
 * is one bug in the exporter away from writing wherever the archive says.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { gzipSync } from "node:zlib";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { unpackTo } from "../src/package/unpack";

let dest: string;

beforeEach(async () => {
  dest = await mkdtemp(join(tmpdir(), "mediagram-unpack-"));
});
afterEach(async () => {
  await rm(dest, { recursive: true, force: true });
});

const BLOCK = 512;

/** One tar member, as the exporter writes them: ustar, mode 644, mtime 0. */
function member(name: string, body: Uint8Array, typeflag = "0"): Uint8Array {
  const header = Buffer.alloc(BLOCK);
  header.write(name, 0, 100, "utf8");
  header.write("000644 \0", 100, 8, "utf8");
  header.write("000000 \0", 108, 8, "utf8");
  header.write("000000 \0", 116, 8, "utf8");
  header.write(body.length.toString(8).padStart(11, "0") + " ", 124, 12, "utf8");
  header.write("00000000000 ", 136, 12, "utf8");
  header.write(typeflag, 156, 1, "utf8");
  header.write("ustar\0", 257, 6, "utf8");
  header.write("00", 263, 2, "utf8");

  // The checksum is computed with its own field read as spaces.
  header.write(" ".repeat(8), 148, 8, "utf8");
  let sum = 0;
  for (const byte of header) sum += byte;
  header.write(sum.toString(8).padStart(6, "0") + "\0 ", 148, 8, "utf8");

  const padded = Buffer.alloc(Math.ceil(body.length / BLOCK) * BLOCK);
  Buffer.from(body).copy(padded);
  return new Uint8Array(Buffer.concat([header, padded]));
}

/** A gzipped tar of the given members, with the two-block end marker. */
function archive(...members: Uint8Array[]): Uint8Array {
  const end = Buffer.alloc(BLOCK * 2);
  return new Uint8Array(gzipSync(Buffer.concat([...members.map(Buffer.from), end])));
}

const text = (s: string) => new TextEncoder().encode(s);

describe("unpacking an archive", () => {
  test("members are written where they say", async () => {
    const packed = archive(
      member("manifest.json", text('{"format":1}')),
      member("library.db", text("SQLite format 3")),
      member("posters/tmdb-movie-36648.jpg", text("jpeg bytes")),
    );

    const names = await unpackTo(packed, dest);

    expect(names).toEqual(["manifest.json", "library.db", "posters/tmdb-movie-36648.jpg"]);
    expect(await readFile(join(dest, "manifest.json"), "utf8")).toBe('{"format":1}');
    expect(await readFile(join(dest, "posters/tmdb-movie-36648.jpg"), "utf8")).toBe("jpeg bytes");
  });

  test("a member that climbs out of the target is refused", async () => {
    for (const name of ["../escaped", "a/../../escaped", "/etc/passwd", "./../x"]) {
      const packed = archive(member(name, text("no")));

      await expect(unpackTo(packed, dest)).rejects.toThrow(/escape|outside|absolute/i);
    }
  });

  test("nothing is left behind by a refused archive", async () => {
    // The good member comes first, so a reader that writes as it goes would
    // have created it before reaching the bad one.
    const packed = archive(member("library.db", text("ok")), member("../escaped", text("no")));

    await expect(unpackTo(packed, dest)).rejects.toThrow();
    await expect(readFile(join(dest, "library.db"), "utf8")).rejects.toThrow();
  });

  test("a member that is not a regular file is refused", async () => {
    // '2' is a symlink, '5' a directory, '3' a character device.
    for (const flag of ["2", "5", "3", "L"]) {
      const packed = archive(member("evil", text(""), flag));

      await expect(unpackTo(packed, dest)).rejects.toThrow(/regular file|not supported/i);
    }
  });

  test("a truncated archive is refused rather than half-unpacked", async () => {
    const whole = Buffer.from(archive(member("library.db", text("x".repeat(2000)))));
    const packed = new Uint8Array(whole.subarray(0, whole.length - 40));

    await expect(unpackTo(packed, dest)).rejects.toThrow();
  });

  test("a member whose size runs past the end of the archive is refused", async () => {
    const one = Buffer.from(member("library.db", text("short")));
    // Claim 8 KB of body where 512 bytes were written.
    one.write((8192).toString(8).padStart(11, "0") + " ", 124, 12, "utf8");
    const end = Buffer.alloc(BLOCK * 2);
    const packed = new Uint8Array(gzipSync(Buffer.concat([one, end])));

    await expect(unpackTo(packed, dest)).rejects.toThrow(/truncat|short|incomplete/i);
  });

  test("bytes that are not a gzip stream are refused", async () => {
    await expect(unpackTo(text("plain text"), dest)).rejects.toThrow();
  });
});
