/**
 * How much disk a directory is using.
 *
 * The transcode directory has no budget and no eviction: a conversion writes
 * about a megabyte per second of film, so one feature is some seven
 * gigabytes, and the only thing that removes them is the idle reaper. Nothing
 * reported that, which makes a player that quietly filled a disk look like a
 * player that stopped working for no reason.
 *
 * Separate from `ChunkCache.sizeOnDisk`, which needs each file's mtime to
 * order eviction by. This needs only the sum, and pays for only the sum.
 */

import { readdir, stat } from "node:fs/promises";
import { join } from "node:path";

/**
 * Bytes under `root`, recursively. `0` for a directory that is not there.
 *
 * A directory being measured is also being written to and reaped, so a file
 * that vanishes between the listing and the `stat` is ordinary rather than an
 * error: it is skipped, and the total is a reading taken at a moment.
 * Other IO failures reject the reading so callers can retain a prior total.
 */
export async function dirBytes(root: string): Promise<number> {
  let total = 0;
  let entries;
  try {
    entries = await readdir(root, { withFileTypes: true });
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") return 0;
    throw error;
  }

  // Together rather than one after another: a two-hour conversion at two
  // seconds a segment is some three and a half thousand files, and one
  // serialised `stat` each is thousands of round trips for a figure printed
  // to one decimal place.
  const sizes = await Promise.all(
    entries.map(async (entry) => {
      const path = join(root, entry.name);
      if (entry.isDirectory()) return dirBytes(path);
      try {
        return (await stat(path)).size;
      } catch (error) {
        // Reaped between the listing and the stat.
        if ((error as NodeJS.ErrnoException).code === "ENOENT") return 0;
        throw error;
      }
    }),
  );
  for (const size of sizes) total += size;
  return total;
}

/** Segment files (`.ts`, `.m4s`) directly under `root`. `0` for a missing directory. */
export async function countSegments(root: string): Promise<number> {
  try {
    const entries = await readdir(root);
    return entries.filter((name) => /\.(ts|m4s)$/.test(name)).length;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") return 0;
    throw error;
  }
}
