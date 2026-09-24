/**
 * Which of a channel's messages is the newest library index.
 *
 * A port of core's `pick_index` (`crates/mediagram-core/src/api/channel/index.rs`),
 * pinned to it by the fixtures in `test/fixtures/pick-index/`, which both
 * languages read. The Android app and this server choose between the same
 * snapshots, and two surfaces disagreeing about what the library is would be
 * worse than either being slow.
 *
 * Newest by the `pushed_at` in the caption, not by which message a pin points
 * at: a publish whose unpin failed, or a second machine publishing to the same
 * channel, leaves the pin on an older snapshot, and to a reader that looks
 * exactly like the library shrinking. The timestamp travels with the snapshot
 * and cannot drift that way.
 */

import { FUTURE_TOLERANCE_SECONDS } from "../package/catalog-versions";
import { INDEX_MARKER } from "../telegram/channel-captions";

/** One message that might be a snapshot: its caption and its id. */
export interface Candidate {
  text: string;
  id: number;
}

/** Why no candidate is an index — the two states a reader must tell apart. */
export type NoIndex = "nothing-pinned" | "not-an-index";

/** Whether a caption marks an index snapshot, of this version or a later one. */
export function isIndexCaption(caption: string): boolean {
  return caption.startsWith(INDEX_MARKER);
}

/**
 * When a snapshot was pushed, or `null` when its caption says nothing this
 * build can read. Only `pushed_at` is required, so a later caption with more
 * fields still reads.
 */
export function pushedAt(caption: string): number | null {
  const newline = caption.indexOf("\n");
  if (newline < 0) return null;
  try {
    const stamp = (JSON.parse(caption.slice(newline + 1).trim()) as { pushed_at?: unknown }).pushed_at;
    return typeof stamp === "number" && Number.isInteger(stamp) && stamp > 0 ? stamp : null;
  } catch {
    return null;
  }
}

/**
 * The timestamp a choice may rank by. A stamp further ahead of `now` than
 * clocks disagree by is not believed: it would win every later choice.
 */
function rankableStamp(caption: string, now: number): number | null {
  const stamp = pushedAt(caption);
  return stamp !== null && stamp <= now + FUTURE_TOLERANCE_SECONDS ? stamp : null;
}

/**
 * The seconds a snapshot is installed under: its own stamp when that can be
 * believed, `now` otherwise. Core's `pushed_at` (`index.rs`). A stamp from
 * next year must not name the version either — installed as `v-<next year>`,
 * it would make every real snapshot after it look older.
 */
export function versionStamp(caption: string, now: number): number {
  return rankableStamp(caption, now) ?? now;
}

/**
 * The position of the newest index among `candidates`, or why there is none.
 *
 * An undated snapshot loses to any dated one, and the higher message id breaks
 * a tie, so the choice is total and the same on every device.
 */
export function pickNewestIndex(candidates: Candidate[], now: number): number | NoIndex {
  let best = -1;
  let bestStamp: number | null = null;
  for (const [position, candidate] of candidates.entries()) {
    if (!isIndexCaption(candidate.text)) continue;
    const stamp = rankableStamp(candidate.text, now);
    if (best >= 0 && !outranks(stamp, candidate.id, bestStamp, candidates[best]!.id)) continue;
    best = position;
    bestStamp = stamp;
  }
  if (best >= 0) return best;
  return candidates.length === 0 ? "nothing-pinned" : "not-an-index";
}

/** Rust's `Option` ordering: `None` below every `Some`, then the id. */
function outranks(stamp: number | null, id: number, than: number | null, thanId: number): boolean {
  if (stamp !== than) {
    if (stamp === null) return false;
    if (than === null) return true;
    return stamp > than;
  }
  // `max_by_key` keeps the last of equal keys, so an equal id also wins —
  // which only matters for the same message listed twice.
  return id >= thanId;
}
