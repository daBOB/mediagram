/**
 * What one device says about where things were left off.
 *
 * A device writes one of these and reads everyone else's. Deliberately not one
 * shared document that every device edits: Telegram has no compare-and-swap,
 * so two players saving at the same moment would clobber each other with no
 * way to notice. Each writer owning its own document is what makes the merge
 * in `merge.ts` deterministic rather than a race.
 *
 * **Every row carries its own `updatedAt`.** Without one a merge could only
 * prefer whole documents, and whichever device pushed last would overwrite a
 * position it had never heard of.
 *
 * **Scope: positions and completions, and nothing else yet.** Those are what
 * the Continue shelf is made of, and they are also the two whose removals
 * survive a merge — see `merge.ts` on why `watched` is the tombstone for
 * `progress`. A watchlist entry is deleted outright, leaving nothing to
 * carry the fact that it *was* deleted, so syncing it would resurrect on
 * every merge whatever another device had not yet heard was gone. That needs
 * tombstones in the schema, and it is not what was asked for.
 */

/** Bumped when a reader could no longer make sense of an older document. */
export const SYNC_FORMAT = 1;

export interface ProgressRow {
  setId: string;
  at: number;
  duration: number | null;
  updatedAt: number;
}

export interface WatchedRow {
  setId: string;
  updatedAt: number;
}

export interface ProfileState {
  /** The viewer. See the plan's Identity section: the name, not the id. */
  name: string;
  /** The writing device's own id for this profile — provenance, not identity. */
  localId?: string;
  progress: ProgressRow[];
  watched: WatchedRow[];
}

export interface SyncRecord {
  format: number;
  /** Stable per install, so a device can recognise and replace its own. */
  device: string;
  writtenAt: number;
  profiles: ProfileState[];
}

/**
 * How a viewer is the same person on two machines.
 *
 * Case and surrounding space are not part of who someone is; a name typed
 * "andré" on the phone and "André " on the desktop is one viewer. Normalised
 * to NFC first, because the same name can be typed as a composed `é` or as an
 * `e` with a combining accent and the two are not otherwise equal.
 */
export function normalName(name: unknown): string | null {
  if (typeof name !== "string") return null;
  const clean = name.normalize("NFC").trim().toLowerCase();
  return clean === "" ? null : clean;
}

/**
 * Reads a document from the channel, or `null` if it cannot be trusted.
 *
 * Written as if the input were hostile, because in the useful sense it is: it
 * came off the network, it was written by another machine, and it may have
 * been written by a *newer* version of this program. Every row is checked and
 * a bad one is dropped rather than taking the document down with it — one
 * unparseable position should not cost a viewer the rest of their history.
 */
export function parseRecord(text: string): SyncRecord | null {
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch {
    return null;
  }
  if (raw === null || typeof raw !== "object") return null;

  const held = raw as Record<string, unknown>;
  const format = Number(held.format);
  // A document from the future is ignored rather than guessed at. Reading it
  // half-right would merge a half-right answer into a database that is the
  // source of truth for this machine.
  if (!Number.isInteger(format) || format < 1 || format > SYNC_FORMAT) return null;

  const device = text_(held.device);
  if (device === null) return null;

  const profiles: ProfileState[] = [];
  for (const one of asArray(held.profiles)) {
    const row = one as Record<string, unknown>;
    const name = text_(row.name);
    if (name === null) continue;
    profiles.push({
      name,
      localId: text_(row.localId) ?? undefined,
      progress: asArray(row.progress).flatMap((entry) => {
        const at = progressRow(entry as Record<string, unknown>);
        return at === null ? [] : [at];
      }),
      watched: asArray(row.watched).flatMap((entry) => {
        const at = watchedRow(entry as Record<string, unknown>);
        return at === null ? [] : [at];
      }),
    });
  }

  return { format, device, writtenAt: Number(held.writtenAt) || 0, profiles };
}

function progressRow(raw: Record<string, unknown>): ProgressRow | null {
  const setId = text_(raw.setId);
  const at = Number(raw.at);
  const updatedAt = Number(raw.updatedAt);
  // `at` may legitimately be 0 — the start of a film — so it is checked for
  // finiteness rather than truthiness.
  if (setId === null || !Number.isFinite(at) || at < 0) return null;
  if (!Number.isFinite(updatedAt) || updatedAt <= 0) return null;
  const runtime = Number(raw.duration);
  return {
    setId,
    at,
    duration: Number.isFinite(runtime) && runtime > 0 ? runtime : null,
    updatedAt,
  };
}

function watchedRow(raw: Record<string, unknown>): WatchedRow | null {
  const setId = text_(raw.setId);
  const updatedAt = Number(raw.updatedAt);
  if (setId === null || !Number.isFinite(updatedAt) || updatedAt <= 0) return null;
  return { setId, updatedAt };
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

/** A non-empty string, trimmed — the only kind of text worth keeping here. */
function text_(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const clean = value.trim();
  return clean === "" ? null : clean;
}
