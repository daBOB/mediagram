/**
 * Installing a channel's index snapshot: stage it, prove it is a library, and
 * only then make it the live catalog.
 *
 * Mirrors core's `install.rs`, which the Android app has run against real
 * channels since it shipped. A snapshot is a plain `library.db` sent as a
 * document. Proving it before the swap is what keeps a channel with the wrong
 * thing pinned — or a snapshot still arriving — from replacing a catalog that
 * works.
 */

import { Database } from "bun:sqlite";
import { readlink, rename, rm, mkdir } from "node:fs/promises";
import { join } from "node:path";
import { assertSchema, listPlayable } from "../catalog";
import { failureMessage } from "../failure-message";
import { CURRENT, availableVersionName, cleanupCatalogDirectory, removeOtherVersions, swapCurrent } from "../package/catalog-versions";

/**
 * A ceiling on the snapshot. A real index for a few hundred sets is a few
 * megabytes; this only stops a wrong or hostile document filling the disk
 * before anything looks at it. Core's number, for the same reason.
 */
export const MAX_INDEX_BYTES = 256 * 1024 * 1024;

const INDEX_FILE = "library.db";

export type InstallOutcome =
  | { status: "updated"; dir: string; pushedAt: number; sets: number }
  | { status: "unchanged"; dir: string; pushedAt: number }
  | { status: "kept"; reason: string };

/** When the installed snapshot was pushed, or `null` when none is installed. */
export async function installedPushedAt(root: string): Promise<number | null> {
  try {
    const target = await readlink(join(root, CURRENT));
    const seconds = Number(/^v-(\d+)(?:-\d+)?$/.exec(target)?.[1]);
    return Number.isSafeInteger(seconds) ? seconds : null;
  } catch {
    return null;
  }
}

/** The live catalog directory, whichever version it names. */
export function currentDir(root: string): string {
  return join(root, CURRENT);
}

/**
 * Installs the snapshot pushed at `pushedAt`, read from `chunks`.
 *
 * `chunks` is only started when the snapshot is newer than the installed one,
 * so an index event for the catalog already live costs no download. It is a
 * function rather than an iterable for that reason.
 * Download, validation, and installation failures return `kept` and preserve
 * the installed catalog. Cleanup failures are logged after publication.
 */
export async function installChannelIndex(
  root: string,
  pushedAt: number,
  chunks: () => AsyncIterable<Uint8Array>,
): Promise<InstallOutcome> {
  const installed = await installedPushedAt(root);
  if (installed !== null && installed >= pushedAt) {
    return { status: "unchanged", dir: currentDir(root), pushedAt: installed };
  }

  const incoming = join(root, `incoming-${pushedAt}-${process.pid}`);
  let version: string;
  let staged = incoming;

  let sets: number;
  try {
    await mkdir(root, { recursive: true });
    await rm(incoming, { recursive: true, force: true });
    await mkdir(incoming);
    await download(join(incoming, INDEX_FILE), chunks());
    sets = validateAndCountPlayableSets(join(incoming, INDEX_FILE));
    version = await availableVersionName(root, pushedAt);
    await rename(incoming, join(root, version));
    staged = join(root, version);
    await swapCurrent(root, version);
  } catch (error) {
    await cleanupCatalogDirectory(staged);
    return { status: "kept", reason: failureMessage(error) };
  }

  // An open handle on the version just replaced keeps reading it: unlinking a
  // file SQLite has open leaves the inode alive until the handle closes.
  await removeOtherVersions(root, version);
  return { status: "updated", dir: currentDir(root), pushedAt, sets };
}

async function download(path: string, chunks: AsyncIterable<Uint8Array>): Promise<void> {
  const file = Bun.file(path).writer();
  let written = 0;
  try {
    for await (const chunk of chunks) {
      written += chunk.length;
      if (written > MAX_INDEX_BYTES) {
        throw new Error(`the pinned index is larger than ${MAX_INDEX_BYTES} bytes`);
      }
      file.write(chunk);
    }
  } finally {
    await file.end();
  }
}

/**
 * Counts what the staged snapshot can play, which is also the proof: a file
 * that is not a catalog of this schema cannot be counted.
 */
function validateAndCountPlayableSets(path: string): number {
  let db: Database;
  try {
    db = new Database(path, { readonly: true });
  } catch (error) {
    throw new Error(`the pinned index could not be opened as a library: ${failureMessage(error)}`, { cause: error });
  }
  try {
    assertSchema(db);
    return listPlayable(db).length;
  } catch (error) {
    throw new Error(`the pinned index could not be read as a library: ${failureMessage(error)}`, { cause: error });
  } finally {
    db.close();
  }
}
