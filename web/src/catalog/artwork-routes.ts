/** Catalog artwork and preview frames, independent of media byte delivery. */
import type { Database } from "bun:sqlite";
import { playableSet } from "../catalog";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { posterKeyIsValid, type PosterStore } from "../package/posters";
import { bodiless, withBody } from "../response";
import type { SheetStore } from "../thumbs/sheets";
import { spritePlan } from "../../public/lib/sprite-plan.js";

// Posters, season posters and backdrops; `posterKeyIsValid` re-checks the key.
const POSTER_PATH = /^\/api\/posters\/(tmdb-(?:movie|tv)-\d{1,12}(?:-s\d{1,4}|-bg)?)\.jpg$/;
const THUMBS_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/thumbs\.jpg$/;

export async function artworkResponse(
  db: Database, posters: PosterStore, sheets: SheetStore | undefined, request: PlayerRequest,
): Promise<PlayerResponse | null> {
  const framed = (body: Uint8Array) => withBody(body, "image/jpeg", {
    headOnly: request.method === "HEAD",
    headers: { "cache-control": "public, max-age=86400" },
  });
  const poster = POSTER_PATH.exec(request.path);
  if (poster) {
    const key = poster[1]!;
    const body = posterKeyIsValid(key) ? posters.read(key) : null;
    return body === null ? bodiless(404) : framed(body);
  }

  const thumbs = THUMBS_PATH.exec(request.path);
  if (!thumbs) return null;
  if (!sheets) return bodiless(404);
  const setId = thumbs[1]!;
  const set = playableSet(db, setId);
  if (set === null) return bodiless(404);
  if (await sheets.sizeOf(setId) === null) {
    // Generation only uses held media and publishes when complete. A preview
    // must not hold the request open while the rest of the player works.
    void sheets.ensure(setId, spritePlan(set.duration));
    return bodiless(404);
  }
  return framed(await Bun.file(sheets.path(setId)).bytes());
}
