/**
 * Which cache chunks a byte range touches, and where they live.
 *
 * The cache unit is a fixed aligned block rather than the request. A viewer's
 * ranges are arbitrary and overlapping — a scrub bar produces dozens of them —
 * so caching them as asked would store the same bytes many times over and
 * almost never hit. Fixed blocks make any range a mix of hits and misses with
 * no partial-overlap arithmetic.
 */

import { join } from "node:path";

/**
 * The block size, and also the size fetched on a miss.
 *
 * 512 KiB is Telegram's own maximum request size, so a miss costs exactly one
 * upstream request and nothing is wasted rounding up.
 */
export const CACHE_CHUNK = 512 * 1024;

/** One chunk's part in answering a range. */
export interface ChunkSlice {
  /** Index of the chunk within the part. */
  index: number;
  /** Byte offset of the chunk itself, always chunk-aligned. */
  offset: number;
  /** Bytes to skip inside the chunk before the wanted range begins. */
  skip: number;
  /** Bytes to take from this chunk. */
  take: number;
}

/** The chunks covering `[start, start + length)`, in order. */
export function chunksCovering(start: number, length: number): ChunkSlice[] {
  const slices: ChunkSlice[] = [];
  let position = start;
  let remaining = length;

  while (remaining > 0) {
    const index = Math.floor(position / CACHE_CHUNK);
    const offset = index * CACHE_CHUNK;
    const skip = position - offset;
    const take = Math.min(CACHE_CHUNK - skip, remaining);
    slices.push({ index, offset, skip, take });
    position += take;
    remaining -= take;
  }
  return slices;
}

/**
 * Where a chunk's file lives.
 *
 * The chunk size is part of the path so that changing it retires the old
 * entries instead of reading them as though they were the new size. A set id
 * comes from a caption, which anyone with channel access can write, so it is
 * checked here rather than trusted into a path.
 */
export function chunkPath(root: string, setId: string, partIdx: number, index: number): string {
  if (!/^[A-Za-z0-9]{1,64}$/.test(setId)) {
    throw new Error(`refusing a set id that is not plain alphanumeric: ${setId}`);
  }
  return join(root, String(CACHE_CHUNK), setId, String(partIdx), String(index));
}
