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
 */
export async function dirBytes(root: string): Promise<number> {
  let total = 0;
  let entries;
  try {
    entries = await readdir(root, { withFileTypes: true });
  } catch {
    return 0;
  }

  for (const entry of entries) {
    const path = join(root, entry.name);
    if (entry.isDirectory()) {
      total += await dirBytes(path);
      continue;
    }
    try {
      total += (await stat(path)).size;
    } catch {
      // Reaped between the listing and the stat.
    }
  }
  return total;
}
