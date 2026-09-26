/** Catalog artwork and preview frames, independent of media byte delivery. */
import type { Database } from "bun:sqlite";
import { playableSet } from "../catalog";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { posterKeyIsValid, type PosterStore } from "../package/posters";
import { bodiless, withBody } from "../response";
import type { SheetStore } from "../thumbs/sheets";
import { spritePlan } from "../../public/lib/sprite-plan.js";

// Posters, season posters, backdrops, portraits and title-slug artwork share
// this path; `posterKeyIsValid` is the real gate on the shape of the key, so
// the capture here only needs to keep the path free of anything a file name
// or a SQL parameter should never see.
const POSTER_PATH = /^\/api\/posters\/([a-z0-9-]{1,80})\.jpg$/;
const THUMBS_PATH = /^\/api\/sets\/([A-Za-z0-9]{1,64})\/thumbs\.jpg$/;

/** Image types `mediagram artwork` may have written into the table. */
const ARTWORK_MIME = new Set(["image/jpeg", "image/png", "image/webp"]);

/**
 * One row of user-supplied artwork, or `null` when there is none — a key
 * nobody has set, an index written before the table existed, or a mime type
 * this build refuses to relay as a `Content-Type` header.
 */
function artworkRow(db: Database, key: string): { mime: string; bytes: Uint8Array } | null {
  try {
    const row = db.query("SELECT mime, bytes FROM artwork WHERE key = ?1").get(key) as
      | { mime: string; bytes: Uint8Array }
      | null;
    return row && ARTWORK_MIME.has(row.mime) ? row : null;
  } catch (error) {
    if (error instanceof Error && error.message === "no such table: artwork") return null;
    throw error;
  }
}

/**
 * Every key the `artwork` table carries, read once per catalog so the
 * catalog route can ask "is there art for this?" without a query per title.
 * The bytes themselves are read only when a request actually asks for one, in
 * {@link artworkResponse}.
 */
export function artworkKeys(db: Database): Set<string> {
  try {
    return new Set((db.query("SELECT key FROM artwork").all() as { key: string }[]).map((row) => row.key));
  } catch (error) {
    if (error instanceof Error && error.message === "no such table: artwork") return new Set();
    throw error;
  }
}

export async function artworkResponse(
  db: Database, posters: PosterStore, sheets: SheetStore | undefined, request: PlayerRequest,
): Promise<PlayerResponse | null> {
  const respond = (body: Uint8Array, contentType: string) => withBody(body, contentType, {
    headOnly: request.method === "HEAD",
    headers: { "cache-control": "public, max-age=86400" },
  });
  const poster = POSTER_PATH.exec(request.path);
  if (poster) {
    const key = poster[1]!;
    if (!posterKeyIsValid(key)) return bodiless(404);
    // A custom image overrides whatever the package or TMDB fetch carries.
    const custom = artworkRow(db, key);
    if (custom) return respond(custom.bytes, custom.mime);
    const body = posters.read(key);
    return body === null ? bodiless(404) : respond(body, "image/jpeg");
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
  return respond(await Bun.file(sheets.path(setId)).bytes(), "image/jpeg");
}
