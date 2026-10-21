/**
 * What the player's statistics say about downloading a title: how much of it
 * is on this server's disk, what the part downloads are moving, and the
 * episode the series preload is taking in behind it — the web's half of the
 * Android overlay's network, on disk and next rows, in the same shape.
 */

import type { Database } from "bun:sqlite";
import { partLocations, playableSet } from "../catalog";
import type { PlayerResponse } from "../http/contracts";
import { bodiless, withBody } from "../response";
import type { TrafficSnapshot } from "../telegram/fetch-traffic";
import type { HeldSets } from "./held";
import type { PreloadItem, SeriesPreload } from "./series-preload";

export interface DownloadFactsSources {
  /** Absent without a cache: nothing is ever on disk. */
  held?: Pick<HeldSets, "heldBytes">;
  preload?: Pick<SeriesPreload, "downloading">;
  traffic: () => TrafficSnapshot;
}

/**
 * `GET /api/sets/:id/download`. Not cached by the browser: the panel asks
 * every couple of seconds, and a remembered answer is a rate that stopped.
 *
 * shortcut: the rates are the whole server's, so two viewers streaming at
 * once each see the sum; per-set rates if a household ever does that often.
 */
/**
 * The preload worth naming to a viewer of `set`: an episode of the same show,
 * which they can see already. The preloader is the whole server's, and the
 * episode it takes in may be another household's, or one a profile's hidden
 * sections keep off its shelves; nor is the title open its own "next".
 */
function nextFor(db: Database, set: { setId: string; show: string | null }, next: PreloadItem | null): PreloadItem | null {
  if (!next || next.setId === set.setId || set.show === null) return null;
  return playableSet(db, next.setId)?.show === set.show ? next : null;
}

export async function downloadFactsResponse(
  db: Database,
  sources: DownloadFactsSources,
  setId: string,
  headOnly: boolean,
): Promise<PlayerResponse> {
  const set = playableSet(db, setId);
  if (!set) return bodiless(404);
  const next = nextFor(db, set, sources.preload?.downloading() ?? null);
  const nextParts = next?.locations.map((location) => location.span) ?? [];
  const body = {
    onDiskBytes: (await sources.held?.heldBytes(setId, partLocations(db, setId).map((location) => location.span))) ?? 0,
    totalBytes: set.total,
    traffic: sources.traffic(),
    next: next && {
      title: next.title,
      heldBytes: (await sources.held?.heldBytes(next.setId, nextParts)) ?? 0,
      totalBytes: nextParts.reduce((sum, part) => sum + part.len, 0),
    },
  };
  return withBody(JSON.stringify(body), "application/json", { headOnly, headers: { "cache-control": "no-store" } });
}
