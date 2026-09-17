/**
 * Building the archives the package reader is pointed at.
 *
 * The exporter writes tars; the tests need to hand the reader one without
 * running the exporter, including shapes the exporter would never produce.
 * Written once here because two suites need it and a second copy had already
 * drifted: the fields a header carries are not the fields the reader looks
 * at, so a copy that omits one stays green until the day the reader grows
 * an opinion about it.
 */

import { gzipSync } from "node:zlib";

export const BLOCK = 512;

/** One tar member, as the exporter writes them: ustar, mode 644, mtime 0. */
export function member(name: string, body: Uint8Array, typeflag = "0"): Uint8Array {
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

/** The given members followed by the two-block end marker, uncompressed. */
export function tar(...members: Uint8Array[]): Uint8Array {
  const end = Buffer.alloc(BLOCK * 2);
  return new Uint8Array(Buffer.concat([...members.map(Buffer.from), end]));
}

/** A gzipped tar of the given members, which is what a package carries. */
export function archive(...members: Uint8Array[]): Uint8Array {
  return new Uint8Array(gzipSync(Buffer.from(tar(...members))));
}

export const text = (s: string) => new TextEncoder().encode(s);
