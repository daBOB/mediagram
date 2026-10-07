/**
 * Free and total space under a handful of directories.
 *
 * The cache and the transcode directory are usually the same filesystem, and
 * a player that reported "118 GB free" twice under two different labels
 * would read as twice as much room as there actually is. Directories that
 * share a device (the same `stat().dev`) are merged into one row.
 */

import { stat, statfs } from "node:fs/promises";

export interface DiskFree {
  dirs: string[];
  freeBytes: number;
  totalBytes: number;
}

/**
 * One row per distinct device among `dirs`, in the order first seen.
 *
 * A directory that cannot be `stat`'d or `statfs`'d, for any reason — not
 * created yet, removed, unreadable — is left out rather than failing the
 * whole status reading it is polled with. `dirBytes` differs: it forgives
 * only a missing directory and rejects the rest, which its caller absorbs by
 * keeping the total it had.
 */
export async function diskFree(dirs: string[]): Promise<DiskFree[]> {
  const rows = await Promise.all(
    dirs.map(async (dir) => {
      try {
        const [info, usage] = await Promise.all([stat(dir), statfs(dir)]);
        return { dir, dev: info.dev, freeBytes: usage.bavail * usage.bsize, totalBytes: usage.blocks * usage.bsize };
      } catch {
        return null;
      }
    }),
  );

  const byDevice = new Map<number, DiskFree>();
  for (const row of rows) {
    if (!row) continue;
    const existing = byDevice.get(row.dev);
    if (!existing) {
      byDevice.set(row.dev, { dirs: [row.dir], freeBytes: row.freeBytes, totalBytes: row.totalBytes });
    } else if (!existing.dirs.includes(row.dir)) {
      existing.dirs.push(row.dir);
    }
  }
  return [...byDevice.values()];
}
