/**
 * Profile-scoped state reads and writes: the snapshot, kids marks, the editor's choice, and
 * profiles through `profileRoute`. The browser write guard runs once, in the `src/routes.ts` dispatcher.
 */

import { jsonBody } from "../http/browser-write";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { bodiless } from "../response";
import type { WatchState } from "./store";
import { profileRoute } from "./profiles-routes";
import { json, P } from "./route-shared";

const STATE = new RegExp(`^/api/profiles/${P}/state$`);
const PROGRESS = new RegExp(`^/api/profiles/${P}/progress/([A-Za-z0-9]{1,64})$`);
const WATCHLIST = new RegExp(`^/api/profiles/${P}/watchlist/([A-Za-z0-9]{1,64})$`);
/** Watched to the end — a fact about the viewer, so scoped to one. */
const WATCHED = new RegExp(`^/api/profiles/${P}/watched/([A-Za-z0-9]{1,64})$`);
/**
 * A remembered choice.
 *
 * The scope is in the body, not the path: it is a show's name as often as a
 * key, and a name goes through `encodeURIComponent` into something a route
 * pattern then has to be careful about. The body has no such problem.
 */
const PREFERENCE = new RegExp(`^/api/profiles/${P}/preferences$`);
const COLLECTIONS = new RegExp(`^/api/profiles/${P}/collections$`);
const COLLECTION = new RegExp(`^/api/profiles/${P}/collections/${P}$`);
const COLLECTION_ITEM = new RegExp(
  `^/api/profiles/${P}/collections/${P}/items/([A-Za-z0-9]{1,64})$`,
);

/**
 * Which titles are a child's. Not under a profile, because the mark is not
 * one: see the v3 migration in `schema.ts`.
 */
const KIDS = /^\/api\/kids$/;
const KIDS_ITEM = /^\/api\/kids\/([A-Za-z0-9]{1,64})$/;

/** The household's editor's choice: read as one pick, pinned per title. */
const EDITORS_CHOICE = /^\/api\/editors-choice$/;
const EDITORS_CHOICE_ITEM = /^\/api\/editors-choice\/([A-Za-z0-9]{1,64})$/;

export interface StateRouterOptions {
  state: WatchState;
  /** Whether the catalog will play this set, so state cannot outlive it. */
  isPlayable: (setId: string) => boolean;
}

/**
 * Answers a state request, or `null` when the path is not one of these.
 *
 * `null` rather than a 404 so the caller can go on to its own routes; this
 * module knows about its own paths and nothing else.
 */
export function createStateRouter(options: StateRouterOptions) {
  const { state, isPlayable } = options;

  return function stateRoute(request: PlayerRequest): PlayerResponse | null {
    const { method, path } = request;
    if (!path.startsWith("/api/profiles") && !path.startsWith("/api/kids") &&
      !path.startsWith("/api/editors-choice")) return null;
    const reading = method === "GET" || method === "HEAD";

    // Answered before the profile routes, and outside them: a mark on a title
    // belongs to the library, and there is no profile in its path to read.
    if (KIDS.test(path)) {
      // Every live mark, and which of them are "from 6": a subset, so a page
      // that reads `kids` alone still sees every mark.
      if (reading) return json(JSON.stringify({ kids: state.kids(), fromSix: state.kidsFromSix() }), method === "HEAD");
      return bodiless(405);
    }
    if (EDITORS_CHOICE.test(path)) {
      if (!reading) return bodiless(405);
      // A pick the catalog no longer holds is no pick; the next live one is.
      const pick = state.editorsChoices().find((setId) => isPlayable(setId)) ?? null;
      return json(JSON.stringify({ setId: pick }), method === "HEAD");
    }

    // Who watches this library, and managing them, PIN and rule checked.
    const profile = profileRoute(request, state);
    if (profile) return profile;

    const snapshot = STATE.exec(path);
    if (snapshot && reading) {
      if (!state.has(snapshot[1]!)) return bodiless(404);
      return json(
        JSON.stringify({ remembers: state.remembers, ...state.snapshot(snapshot[1]!) }),
        method === "HEAD",
      );
    }

    // Past here everything writes, and the dispatcher has checked every write.
    if (reading) return bodiless(405);

    const progress = PROGRESS.exec(path);
    if (progress) {
      const [, profileId, setId] = progress as unknown as [string, string, string];
      if (!state.has(profileId) || !isPlayable(setId)) return bodiless(404);
      if (method === "DELETE") {
        state.clearProgress(profileId, setId);
        return bodiless(204);
      }
      // `POST` as well as `PUT`, because `navigator.sendBeacon` can only POST
      // and a beacon is how a position survives the tab being closed — which
      // is the moment it matters most. Refusing it lost that write silently:
      // `sendBeacon` reports success on queueing, so the ordinary `PUT`
      // written as its fallback never ran.
      //
      // Safe all the same. What keeps a cross-site form out is not the
      // method — `POST` is the one method a form can send — but the
      // dispatcher's write guard, which requires `application/json`. A form
      // may only send urlencoded, multipart or text/plain, and anything that
      // could set a JSON type needs a preflight this server does not answer.
      if (method !== "PUT" && method !== "POST") return bodiless(405);

      const body = jsonBody(request.body);
      const at = Number(body?.at);
      if (!Number.isFinite(at)) return bodiless(400);
      const runtime = Number(body?.duration);
      state.setProgress(profileId, setId, at, Number.isFinite(runtime) && runtime > 0 ? runtime : null);
      return bodiless(204);
    }

    // No write here carries its own copy of the guard: the dispatcher runs
    // it once for every write, so none can be added that skips it.
    const kid = KIDS_ITEM.exec(path);
    if (kid) {
      if (!isPlayable(kid[1]!)) return bodiless(404);
      if (method !== "PUT" && method !== "DELETE") return bodiless(405);
      // No age is from 12, which is what every mark meant before there were
      // two; anything but 6 or 12 is not an age.
      const age = jsonBody(request.body)?.age;
      if (method === "PUT" && age !== undefined && age !== 6 && age !== 12) return bodiless(400);
      state.setKids(kid[1]!, method === "PUT", age === 6 ? 6 : 12);
      return bodiless(204);
    }

    const pinned = EDITORS_CHOICE_ITEM.exec(path);
    if (pinned) {
      if (!isPlayable(pinned[1]!)) return bodiless(404);
      if (method !== "PUT" && method !== "DELETE") return bodiless(405);
      state.setEditorsChoice(pinned[1]!, method === "PUT");
      return bodiless(204);
    }

    const watchlist = WATCHLIST.exec(path);
    if (watchlist) {
      const [, profileId, setId] = watchlist as unknown as [string, string, string];
      if (!state.has(profileId) || !isPlayable(setId)) return bodiless(404);
      if (method !== "PUT" && method !== "DELETE") return bodiless(405);
      state.setWatchlisted(profileId, setId, method === "PUT");
      return bodiless(204);
    }

    const preference = PREFERENCE.exec(path);
    if (preference) {
      const profileId = preference[1]!;
      if (!state.has(profileId)) return bodiless(404);
      if (method !== "PUT" && method !== "POST") return bodiless(405);

      const body = jsonBody(request.body);
      // `setPreference` decides what is storable — length, type, and that an
      // empty value forgets a device-only name and is refused for a synced one.
      // A 400 here is the request being unusable, not the choice being unwelcome.
      return bodiless(
        state.setPreference(profileId, body?.scope, body?.name, body?.value) ? 204 : 400,
      );
    }

    const watched = WATCHED.exec(path);
    if (watched) {
      const [, profileId, setId] = watched as unknown as [string, string, string];
      if (!state.has(profileId) || !isPlayable(setId)) return bodiless(404);
      if (method !== "PUT" && method !== "DELETE") return bodiless(405);
      state.setWatched(profileId, setId, method === "PUT");
      return bodiless(204);
    }

    const lists = COLLECTIONS.exec(path);
    if (lists) {
      if (!state.has(lists[1]!)) return bodiless(404);
      if (method !== "POST") return bodiless(405);
      const made = state.createCollection(lists[1]!, jsonBody(request.body)?.name);
      return made === null ? bodiless(400) : json(JSON.stringify(made), false, 201);
    }

    const collection = COLLECTION.exec(path);
    if (collection) {
      const [, profileId, id] = collection as unknown as [string, string, string];
      if (method === "DELETE") return bodiless(state.deleteCollection(profileId, id) ? 204 : 404);
      if (method !== "PATCH") return bodiless(405);
      const name = jsonBody(request.body)?.name;
      return bodiless(state.renameCollection(profileId, id, name) ? 204 : 404);
    }

    const item = COLLECTION_ITEM.exec(path);
    if (item) {
      const [, profileId, id, setId] = item as unknown as [string, string, string, string];
      if (!isPlayable(setId)) return bodiless(404);
      if (method === "PUT") return bodiless(state.addToCollection(profileId, id, setId) ? 204 : 404);
      if (method === "DELETE") {
        return bodiless(state.removeFromCollection(profileId, id, setId) ? 204 : 404);
      }
      return bodiless(405);
    }

    return bodiless(404);
  };
}
