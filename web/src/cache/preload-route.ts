/** Validate a preload write and select only playable upcoming episodes. */
import type { Database } from "bun:sqlite";
import { partLocations, playableSet } from "../catalog";
import { refuseUnsafeBrowserWrite } from "../http/browser-write";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { bodiless } from "../response";
import type { SeriesPreload } from "./series-preload";

const MAX_PRELOAD = 2;

export function preloadResponse(
  db: Database, preload: SeriesPreload | undefined, request: PlayerRequest,
): PlayerResponse {
  if (!preload) return bodiless(404);
  if (request.method !== "POST") return bodiless(405);
  const refusal = refuseUnsafeBrowserWrite(request);
  if (refusal) return refusal;
  const items = preloadIds(request.body).slice(0, MAX_PRELOAD).flatMap((setId) => {
    const set = playableSet(db, setId);
    if (set === null || set.kind !== "ep") return [];
    return [{ setId, title: set.title ?? setId, locations: partLocations(db, setId) }];
  });
  preload.want(items);
  return bodiless(202);
}

function preloadIds(body: string | null | undefined): string[] {
  try {
    const ids = (JSON.parse(body ?? "") as { setIds?: unknown })?.setIds;
    return Array.isArray(ids) ? ids.filter((id): id is string => typeof id === "string") : [];
  } catch {
    return [];
  }
}
