/**
 * The versioned catalog directory both catalog sources install into.
 *
 * `root/v-<seconds>[-<suffix>]/` per catalog and a `current` symlink naming it.
 * A published package and a channel snapshot arrive differently but land the
 * same way, so the swap that makes one live is written once.
 */

import { lstat, readdir, rename, rm, symlink } from "node:fs/promises";
import { join } from "node:path";

/** The symlink that names the live version. */
export const CURRENT = "current";

/**
 * How far ahead of this clock a catalog's own date may be and still be
 * believed. Dated next year, a catalog would win every later comparison and
 * make each real one look stale.
 */
export const FUTURE_TOLERANCE_SECONDS = 24 * 60 * 60;

/** Preserve existing versions until the new catalog has been published. */
export async function availableVersionName(root: string, seconds: number): Promise<string> {
  const base = `v-${seconds}`;
  for (let suffix = 0; ; suffix++) {
    const name = suffix === 0 ? base : `${base}-${suffix}`;
    try {
      await lstat(join(root, name));
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === "ENOENT") return name;
      throw error;
    }
  }
}

/**
 * Points `current` at `version`, atomically.
 *
 * A symlink renamed over another is a single operation, so a reader that dies
 * mid-refresh is looking at one whole catalog or the other, never at half of
 * each. An already-open SQLite handle keeps the version it opened, which is
 * what should happen: a refresh must not pull the database out from under a
 * query in flight.
 */
export async function swapCurrent(root: string, version: string): Promise<void> {
  const staged = join(root, `.current-${process.pid}`);
  await rm(staged, { force: true });
  await symlink(version, staged);
  try {
    await rename(staged, join(root, CURRENT));
  } catch (error) {
    await rm(staged, { force: true }).catch(reportCleanupFailure);
    throw error;
  }
}

/** Cleanup cannot undo a successful publication or hide an earlier failure. */
export async function cleanupCatalogDirectory(path: string): Promise<void> {
  await rm(path, { recursive: true, force: true }).catch(reportCleanupFailure);
}

function reportCleanupFailure(error: unknown): void {
  console.warn("catalog cleanup failed:", error);
}

/** Obsolete files may remain if cleanup fails; `current` is already published. */
export async function removeOtherVersions(root: string, keepName: string): Promise<void> {
  const entries = await readdir(root).catch((error) => {
    reportCleanupFailure(error);
    return [] as string[];
  });
  for (const name of entries) {
    if (name === keepName || name === CURRENT) continue;
    if (!name.startsWith("v-") && !name.startsWith("incoming-")) continue;
    await cleanupCatalogDirectory(join(root, name));
  }
}
