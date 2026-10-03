/**
 * What the achievement rules need from the catalog: each playable set's
 * kind, genres and collection, and each show's episodes and course's
 * lessons. Read once per catalog, by the router that serves it, the way the
 * catalog router reads its own provider facts.
 *
 * `crates/mediagram-core/src/catalog_achievements.rs` builds the same from
 * the same index, so both surfaces count one library alike.
 */

import type { Database } from "bun:sqlite";
import { listPlayable } from "../catalog";
import { providerFactsByShow } from "../catalog/shows";
import { posterKeyFor } from "../package/posters";
import type { AchievementLibrary, LibraryTitle, TitleKind } from "./achievements";

export function achievementLibrary(db: Database): AchievementLibrary {
  const provider = providerFactsByShow(db);
  const library: LibraryTitle[] = [];
  const members = new Map<string, string[]>();
  for (const set of listPlayable(db)) {
    // The key the catalog route files a set's provider facts under, so a
    // title counts the genres its own page shows.
    const key = posterKeyFor(set.kind, set.tmdb, set.show ?? set.title);
    const collection = collectionOf(set.kind, set.show);
    // The index holds the five kinds `mlib_spec` defines and no other.
    library.push({ setId: set.setId, kind: set.kind as TitleKind, genres: (key && provider.get(key)?.genres) || [], collection });
    if (collection === null) continue;
    const list = members.get(collection);
    if (list) list.push(set.setId);
    else members.set(collection, [set.setId]);
  }
  return { library, collections: [...members].map(([id, setIds]) => ({ id, setIds })) };
}

/**
 * A show's episodes or a course's lessons, by name, the way the shelves
 * group them (`collections` in `public/lib/library.js`). A course's
 * documents are not lessons, and a set with no show belongs to none: the
 * shelves' "Unknown show" is a place to file it, not a series to finish.
 */
export function collectionOf(kind: string, show: string | null): string | null {
  return (kind === "ep" || kind === "tut") && show ? `${kind}:${show}` : null;
}
