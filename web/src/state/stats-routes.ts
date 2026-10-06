/**
 * `GET /api/profiles/{profileId}/stats`: one profile's viewing stats, as of
 * today on this server's calendar — the household's.
 *
 * Dispatched from `src/routes.ts`'s `createRouter`, the one router that holds
 * both the state and the catalog the achievements are counted against —
 * ahead of the state router. Each profile asks for its own; nothing here
 * lists anyone else's.
 */

import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import type { AchievementLibrary } from "./achievements";
import { json, P, status } from "./route-shared";
import type { WatchState } from "./store";

const STATS = new RegExp(`^/api/profiles/${P}/stats$`);

/** The answer for a stats path, or `null` when the path is not this one. */
export function statsRoute(
  state: WatchState,
  request: PlayerRequest,
  library: () => AchievementLibrary,
): PlayerResponse | null {
  const matched = STATS.exec(request.path);
  if (!matched) return null;
  if (request.method !== "GET" && request.method !== "HEAD") return status(405);
  const profileId = matched[1]!;
  if (!state.has(profileId)) return status(404);
  return json(JSON.stringify(state.stats(profileId, library())), request.method === "HEAD");
}
