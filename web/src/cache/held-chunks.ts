/**
 * Counting the finished chunks of one set on disk — the whole question
 * `HeldSets` asks, and how much of a title the player's statistics say is here.
 */

import { readdir } from "node:fs/promises";
import { join } from "node:path";

/**
 * The finished chunks of `setId` under `root`, counting no further than
 * `enough`: a held film is thirteen thousand filenames, and a caller that
 * only asks whether all are there can stop at the number it needs.
 * Nothing cached, or nothing readable, is 0.
 */
export async function countChunks(root: string, setId: string, enough = Number.POSITIVE_INFINITY): Promise<number> {
  let have = 0;
  try {
    for (const part of await readdir(join(root, setId), { withFileTypes: true })) {
      if (!part.isDirectory()) continue;
      for (const chunk of await readdir(join(root, setId, part.name))) {
        // `.tmp` is a chunk being written, which `ChunkCache` renames into
        // place only once it is whole. Counting one would claim a title plays
        // offline while a piece of it was still arriving.
        if (!chunk.endsWith(".tmp")) have += 1;
      }
      if (have >= enough) return have;
    }
  } catch {
    return 0;
  }
  return have;
}
