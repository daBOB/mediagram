/**
 * Counting the finished chunks of one set on disk — the whole question
 * `HeldSets` asks, and how much of a title the player's statistics say is here.
 */

import { readdir } from "node:fs/promises";
import { join } from "node:path";
import { CACHE_CHUNK } from "./key";

/**
 * How many bytes of a set's parts are on disk under `root`: each part's
 * chunks taken whole and capped at the part's length, so its short last
 * chunk counts exactly and any chunk missing keeps the total under 100%.
 */
export async function heldBytesOf(root: string, setId: string, parts: { idx: number; len: number }[]): Promise<number> {
  let held = 0;
  for (const part of parts) {
    const chunks = await countFiles(join(root, setId, String(part.idx)));
    held += Math.min(chunks * CACHE_CHUNK, part.len);
  }
  return held;
}

/** The finished chunk files in one directory; none if it is missing. */
async function countFiles(dir: string): Promise<number> {
  try {
    return (await readdir(dir)).filter((name) => !name.endsWith(".tmp")).length;
  } catch {
    return 0;
  }
}

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
