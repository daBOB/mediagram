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
 * **Positions and completions carry their removal for free.** See `merge.ts`
 * on why `watched` is the tombstone for `progress` — no separate row needed.
 *
 * **Watchlist, Kids and collections carry an explicit one.** A watchlist
 * entry used to be deleted outright, leaving nothing to say it *was*
 * deleted, so a merge would resurrect it from whichever device had not yet
 * heard. `removed` is that missing fact, on the row itself, with its own
 * `updatedAt` — the same last-writer-wins rule as everything else here, kept
 * to a row rather than a whole document so one removal cannot cost another
 * device's unrelated addition.
 *
 * **`kids?` and its optional siblings are new keys, not a format bump.** A
 * format-1 reader older than this drops a key it does not recognise
 * (`parseRecord` below) and keeps merging positions — see `SYNC_FORMAT`.
 * They are optional here for the same reason `localId` is: a document from
 * before they existed has none, and that must parse as "nothing said" rather
 * than "nothing there".
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

/** A watchlist entry or a Kids mark: a title, when it last changed, and
 * whether that change was taking it off rather than putting it on. */
export interface ListRow {
  setId: string;
  updatedAt: number;
  /** Present, and `true`, only for a tombstone — absent means still on. */
  removed?: true;
}

/** A hand-built list, whole: merged as one row, not title by title — see
 * `merge.ts` on why. */
export interface CollectionRow {
  id: string;
  name: string;
  items: string[];
  updatedAt: number;
  removed?: true;
}

export interface ProfileState {
  /** The viewer. See the plan's Identity section: the name, not the id. */
  name: string;
  /** The writing device's own id for this profile — provenance, not identity. */
  localId?: string;
  /**
   * Present only on a kids profile. Written only when true, so an ordinary
   * profile's entry reads exactly as it did before the flag existed.
   */
  kids?: true;
  progress: ProgressRow[];
  watched: WatchedRow[];
  /** Absent on a document from before this existed — not the same as empty. */
  watchlist?: ListRow[];
  collections?: CollectionRow[];
}

export interface SyncRecord {
  format: number;
  /** Stable per install, so a device can recognise and replace its own. */
  device: string;
  writtenAt: number;
  profiles: ProfileState[];
  /** Not scoped to a profile — see `schema.ts` on why `kids` alone has none. */
  kids?: ListRow[];
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
  const held = objectRow(raw);
  if (held === null) return null;
  const format = numberFromScalar(held.format);
  // A document from the future is ignored rather than guessed at. Reading it
  // half-right would merge a half-right answer into a database that is the
  // source of truth for this machine.
  if (!Number.isInteger(format) || format < 1 || format > SYNC_FORMAT) return null;

  const device = text_(held.device);
  if (device === null) return null;

  const profiles: ProfileState[] = [];
  for (const one of asArray(held.profiles)) {
    const row = objectRow(one);
    if (row === null) continue;
    const name = text_(row.name);
    if (name === null) continue;
    profiles.push({
      name,
      localId: text_(row.localId) ?? undefined,
      // Only a literal `true`: a flag that restricts what a child sees must
      // not be switched on by a string that merely looks truthy.
      ...(row.kids === true ? { kids: true as const } : {}),
      progress: asArray(row.progress).flatMap((entry) => {
        const at = progressRow(entry);
        return at === null ? [] : [at];
      }),
      watched: asArray(row.watched).flatMap((entry) => {
        const at = watchedRow(entry);
        return at === null ? [] : [at];
      }),
      watchlist: row.watchlist === undefined ? undefined : parseListRows(row.watchlist),
      collections: row.collections === undefined ? undefined : parseCollectionRows(row.collections),
    });
  }

  return {
    format,
    device,
    writtenAt: numberFromScalar(held.writtenAt) || 0,
    profiles,
    kids: held.kids === undefined ? undefined : parseListRows(held.kids),
  };
}

function progressRow(value: unknown): ProgressRow | null {
  const raw = objectRow(value);
  if (raw === null) return null;
  const setId = text_(raw.setId);
  const at = numberFromScalar(raw.at);
  const updatedAt = numberFromScalar(raw.updatedAt);
  // `at` may legitimately be 0 — the start of a film — so it is checked for
  // finiteness rather than truthiness.
  if (setId === null || !Number.isFinite(at) || at < 0) return null;
  if (!Number.isFinite(updatedAt) || updatedAt <= 0) return null;
  const runtime = numberFromScalar(raw.duration);
  return {
    setId,
    at,
    duration: Number.isFinite(runtime) && runtime > 0 ? runtime : null,
    updatedAt,
  };
}

function watchedRow(value: unknown): WatchedRow | null {
  const raw = objectRow(value);
  if (raw === null) return null;
  const setId = text_(raw.setId);
  const updatedAt = numberFromScalar(raw.updatedAt);
  if (setId === null || !Number.isFinite(updatedAt) || updatedAt <= 0) return null;
  return { setId, updatedAt };
}

function parseListRows(value: unknown): ListRow[] {
  return asArray(value).flatMap((entry) => {
    const row = listRow(entry);
    return row === null ? [] : [row];
  });
}

function listRow(value: unknown): ListRow | null {
  const raw = objectRow(value);
  if (raw === null) return null;
  const setId = text_(raw.setId);
  const updatedAt = numberFromScalar(raw.updatedAt);
  if (setId === null || !Number.isFinite(updatedAt) || updatedAt <= 0) return null;
  return raw.removed === true ? { setId, updatedAt, removed: true } : { setId, updatedAt };
}

function parseCollectionRows(value: unknown): CollectionRow[] {
  return asArray(value).flatMap((entry) => {
    const row = collectionRow(entry);
    return row === null ? [] : [row];
  });
}

/** How long a list's name from another device's document may be — not
 * `store.ts`'s `MAX_NAME`: that caps what this player lets someone type,
 * this caps what a stranger's document is allowed to claim. */
const MAX_LIST_NAME = 200;

function collectionRow(value: unknown): CollectionRow | null {
  const raw = objectRow(value);
  if (raw === null) return null;
  const id = text_(raw.id);
  const updatedAt = numberFromScalar(raw.updatedAt);
  if (id === null || !Number.isFinite(updatedAt) || updatedAt <= 0) return null;
  const name = text_(raw.name)?.slice(0, MAX_LIST_NAME);
  if (name === undefined || name === "") return null;
  const items = asArray(raw.items).flatMap((entry) => {
    const setId = text_(entry);
    return setId === null ? [] : [setId];
  });
  return raw.removed === true ? { id, name, items, updatedAt, removed: true } : { id, name, items, updatedAt };
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function objectRow(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

/** Older documents allow numeric strings and other primitives. Objects and
 * arrays are not numbers; coercing a JSON object's own `toString` can throw. */
function numberFromScalar(value: unknown): number {
  return value !== null && typeof value === "object" ? Number.NaN : Number(value);
}

/** A non-empty string, trimmed — the only kind of text worth keeping here. */
function text_(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const clean = value.trim();
  return clean === "" ? null : clean;
}
