/**
 * Fetching and caching a set's subtitle bundle: the gzip'd JSON document
 * `mlib_spec::subtitle_bundle` writes, one per set, holding every track.
 *
 * Two caches, for two different questions. A bounded in-memory map answers
 * "was this just asked for" — it de-duplicates the forced and regular track
 * of one playing title, which share a bundle and would otherwise cost two
 * fetches. A disk store beside (not inside) the chunk-cache root answers
 * "does this survive a restart", and is only ever written for a held title —
 * see `hold`/`reconcile` — so an ordinary stream never grows it.
 *
 * Every byte here can be an attacker's: `ref.sha256` and `ref.bytes` are
 * checked before a fetch is even made, the fetched bytes are hashed before
 * anything is written, and decompression is capped through `node:zlib`
 * rather than `Bun.gunzipSync`, which has no such limit.
 */

import { createHash } from "node:crypto";
import { gunzipSync } from "node:zlib";
import { mkdir, open, readFile, rename, rm } from "node:fs/promises";
import { dirname, join } from "node:path";
import type { Database } from "bun:sqlite";
import type { HeldSets } from "../cache/held";
import { failureMessage } from "../failure-message";
import { bundleRef, type BundleRef } from "./subtitle-tracks";

export type { BundleRef } from "./subtitle-tracks";

/** A gzip body over this size is refused before a byte of it is fetched. */
const MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
/** Decompressed JSON over this size is refused mid-read, the gzip-bomb guard. */
const MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024;
/** How many bundles the memory map holds before the least recently used goes. */
const MEMORY_ENTRIES = 32;

interface BundleTrack {
  lang: string;
  forced: boolean;
  sdh: boolean;
  label: string;
  source: string;
  codec: string;
  vtt: string;
}
interface Bundle {
  v: number;
  set: string;
  tracks: BundleTrack[];
}

type Fetcher = (messageId: number, offset: number, length: number) => Promise<Uint8Array>;

/** `<cacheDir>.subtitles`, a sibling of the chunk-cache root, never inside it. */
export function heldSubtitlesDir(cacheDir: string): string {
  return `${cacheDir}.subtitles`;
}

function validRef(ref: BundleRef): boolean {
  return /^[0-9a-f]{64}$/.test(ref.sha256) && ref.bytes > 0 && ref.bytes <= MAX_COMPRESSED_BYTES;
}

function decode(gz: Uint8Array): Bundle | null {
  try {
    const json = gunzipSync(gz, { maxOutputLength: MAX_DECOMPRESSED_BYTES });
    const parsed = JSON.parse(json.toString("utf8")) as Bundle;
    return parsed?.v === 1 && typeof parsed.set === "string" && Array.isArray(parsed.tracks) ? parsed : null;
  } catch {
    return null;
  }
}

/** A resolved bundle, and the compressed bytes to persist — `null` once decoded from disk, since it is already there. */
interface Resolved {
  bundle: Bundle;
  gz: Uint8Array | null;
}

export class SubtitleBundles {
  private readonly memory = new Map<string, Promise<Resolved | null>>();

  constructor(private readonly opts: { fetch: Fetcher; background: Fetcher; heldDir: string }) {}

  /** One track's WebVTT, or `null` for a missing track, a bad ref, or a fetch that failed. */
  async vtt(ref: BundleRef, track: number): Promise<string | null> {
    const resolved = await this.resolve(ref, this.opts.fetch, false);
    return resolved?.bundle.tracks[track]?.vtt ?? null;
  }

  /** Ensures `ref`'s bundle survives a restart. Never deletes; a failure is logged and never rejects. */
  async hold(ref: BundleRef | null): Promise<void> {
    if (ref === null) return;
    await this.resolve(ref, this.opts.background, true).catch((error) =>
      console.warn(`subtitles: hold failed: ${failureMessage(error)}`));
  }

  /**
   * Holds every currently held set's bundle. Called after startup and after
   * each catalog swap; never deletes, including against an index whose
   * uploader has not yet re-published with the v13 tables — a missing bundle
   * then is simply nothing to hold, not a reason to touch what is on disk.
   */
  async reconcile(db: Database, held: Pick<HeldSets, "ids">): Promise<void> {
    await Promise.all(held.ids.map((setId) => this.hold(bundleRef(db, setId))));
  }

  /**
   * Fetches or reuses `ref`'s bundle, keyed by `sha256` so a forced and a
   * regular track sharing a bundle share one fetch.
   *
   * `persist` is decided by *this* call, after the shared task settles —
   * never baked into the task itself — so a plain playback read and a
   * concurrent `hold` of the same bundle each get what they asked for even
   * though they share one fetch. ponytail: two concurrent `hold`s of a fetch
   * neither had on disk yet both write it; harmless (same bytes, unique tmp
   * names), not worth a second lock for.
   */
  private async resolve(ref: BundleRef, fetcher: Fetcher, persist: boolean): Promise<Resolved | null> {
    if (!validRef(ref)) return null;
    let task = this.memory.get(ref.sha256);
    if (task) {
      // Re-inserted so repeated hits keep it away from the eviction end.
      this.memory.delete(ref.sha256);
      this.memory.set(ref.sha256, task);
    } else {
      task = this.fetchOrRead(ref, fetcher);
      this.remember(ref.sha256, task);
    }
    const resolved = await task;
    if (resolved === null) {
      this.memory.delete(ref.sha256);
      return null;
    }
    if (persist && resolved.gz) await writeDiskAtomic(this.pathFor(ref.sha256), resolved.gz);
    return resolved;
  }

  private remember(sha: string, task: Promise<Resolved | null>): void {
    this.memory.set(sha, task);
    if (this.memory.size <= MEMORY_ENTRIES) return;
    const oldest = this.memory.keys().next().value;
    if (oldest !== undefined) this.memory.delete(oldest);
  }

  private pathFor(sha: string): string {
    return join(this.opts.heldDir, `${sha}.json.gz`);
  }

  private async fetchOrRead(ref: BundleRef, fetcher: Fetcher): Promise<Resolved | null> {
    const fromDisk = await readDisk(this.pathFor(ref.sha256));
    if (fromDisk) return { bundle: fromDisk, gz: null };

    let gz: Uint8Array;
    try {
      gz = await fetcher(ref.messageId, 0, ref.bytes);
    } catch (error) {
      console.warn(`subtitles: bundle fetch failed: ${failureMessage(error)}`);
      return null;
    }
    if (createHash("sha256").update(gz).digest("hex") !== ref.sha256) return null;
    const bundle = decode(gz);
    return bundle === null ? null : { bundle, gz };
  }
}

/** A disk hit that fails to decode is corrupt: removed when it can be, never served, so the bundle is refetched. */
async function readDisk(path: string): Promise<Bundle | null> {
  let gz: Buffer;
  try {
    gz = await readFile(path);
  } catch {
    return null;
  }
  const bundle = decode(gz);
  if (bundle === null) await rm(path, { force: true }).catch((error) =>
    console.warn(`subtitles: corrupt bundle not removed: ${failureMessage(error)}`));
  return bundle;
}

async function writeDiskAtomic(path: string, bytes: Uint8Array): Promise<void> {
  await mkdir(dirname(path), { recursive: true });
  const tmp = `${path}.${process.pid}.${Math.random().toString(36).slice(2)}.tmp`;
  const handle = await open(tmp, "w");
  try {
    await handle.writeFile(bytes);
    await handle.sync();
  } finally {
    await handle.close();
  }
  await rename(tmp, path);
}
