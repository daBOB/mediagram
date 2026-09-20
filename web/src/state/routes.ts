/**
 * The write half of the API, kept apart from the read half.
 *
 * Apart because the two have different rules. Everything in `routes.ts`
 * answers questions about a library the uploader owns; everything here
 * changes something, on the one surface this project documents as having no
 * authentication of its own. That is a good reason for the checks to live in
 * one place at the front of one module rather than being remembered in nine
 * handlers.
 */

import type { PlayerRequest, PlayerResponse } from "../routes";
import { bodiless, withBody } from "../response";
import type { WatchState } from "./store";

/**
 * The profile is a path segment, not a parameter.
 *
 * `navigator.sendBeacon` is how a position survives the tab closing, and it
 * cannot set a header — so whoever is watching has to travel in the URL. In
 * the path rather than the query because a missing segment is then a route
 * that does not match, and the alternative to a 404 is a write that quietly
 * lands in somebody else's rows.
 */
const P = "([A-Za-z0-9-]{1,64})";
const PROFILES = /^\/api\/profiles$/;
const PROFILE = new RegExp(`^/api/profiles/${P}$`);
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
    if (!path.startsWith("/api/profiles") && !path.startsWith("/api/kids")) return null;
    const reading = method === "GET" || method === "HEAD";

    // Answered before the profile routes, and outside them: a mark on a title
    // belongs to the library, and there is no profile in its path to read.
    if (KIDS.test(path)) {
      if (reading) return json(JSON.stringify({ kids: state.kids() }), method === "HEAD");
      return status(405);
    }


    // Who watches this library, which is the one question askable before
    // anyone has said who they are.
    if (PROFILES.test(path)) {
      if (reading) {
        return json(
          JSON.stringify({ remembers: state.remembers, profiles: state.profiles() }),
          method === "HEAD",
        );
      }
      if (method !== "POST") return status(405);
      const refusal = refuseUnsafe(request);
      if (refusal) return refusal;
      const made = state.createProfile((parse(request.body) as { name?: unknown })?.name);
      return made === null ? status(400) : json(JSON.stringify(made), false, 201);
    }

    const named = PROFILE.exec(path);
    if (named) {
      const refusal = refuseUnsafe(request);
      if (refusal) return refusal;
      if (method === "DELETE") return status(state.deleteProfile(named[1]!) ? 204 : 404);
      if (method !== "PATCH") return status(405);
      const name = (parse(request.body) as { name?: unknown })?.name;
      return status(state.renameProfile(named[1]!, name) ? 204 : 404);
    }

    const snapshot = STATE.exec(path);
    if (snapshot && reading) {
      if (!state.has(snapshot[1]!)) return status(404);
      return json(
        JSON.stringify({ remembers: state.remembers, ...state.snapshot(snapshot[1]!) }),
        method === "HEAD",
      );
    }

    // Past here everything writes, so everything is checked.
    if (reading) return status(405);
    const refusal = refuseUnsafe(request);
    if (refusal) return refusal;

    const progress = PROGRESS.exec(path);
    if (progress) {
      const [, profileId, setId] = progress as unknown as [string, string, string];
      if (!state.has(profileId) || !isPlayable(setId)) return status(404);
      if (method === "DELETE") {
        state.clearProgress(profileId, setId);
        return status(204);
      }
      // `POST` as well as `PUT`, because `navigator.sendBeacon` can only POST
      // and a beacon is how a position survives the tab being closed — which
      // is the moment it matters most. Refusing it lost that write silently:
      // `sendBeacon` reports success on queueing, so the ordinary `PUT`
      // written as its fallback never ran.
      //
      // Safe for the reason the module header cares about. What keeps a
      // cross-site form out is not the method — `POST` is the one method a
      // form can send — but `refuseUnsafe` above, which requires
      // `application/json`. A form may only send urlencoded, multipart or
      // text/plain, and anything that could set a JSON type needs a preflight
      // this server does not answer.
      if (method !== "PUT" && method !== "POST") return status(405);

      const body = parse(request.body);
      const at = Number((body as { at?: unknown })?.at);
      if (!Number.isFinite(at)) return status(400);
      const runtime = Number((body as { duration?: unknown })?.duration);
      state.setProgress(profileId, setId, at, Number.isFinite(runtime) && runtime > 0 ? runtime : null);
      return status(204);
    }

    // Below the check above, with the other writes, rather than carrying its
    // own copy of it: the whole point of the choke point is that a write
    // cannot be added without passing through one.
    const kid = KIDS_ITEM.exec(path);
    if (kid) {
      if (!isPlayable(kid[1]!)) return status(404);
      if (method !== "PUT" && method !== "DELETE") return status(405);
      state.setKids(kid[1]!, method === "PUT");
      return status(204);
    }

    const watchlist = WATCHLIST.exec(path);
    if (watchlist) {
      const [, profileId, setId] = watchlist as unknown as [string, string, string];
      if (!state.has(profileId) || !isPlayable(setId)) return status(404);
      if (method !== "PUT" && method !== "DELETE") return status(405);
      state.setWatchlisted(profileId, setId, method === "PUT");
      return status(204);
    }

    const preference = PREFERENCE.exec(path);
    if (preference) {
      const profileId = preference[1]!;
      if (!state.has(profileId)) return status(404);
      if (method !== "PUT" && method !== "POST") return status(405);

      const body = parse(request.body) as
        | { scope?: unknown; name?: unknown; value?: unknown }
        | null;
      // `setPreference` decides what is storable — length, type, and that an
      // empty value means forget. A 400 here is the request being unusable,
      // not the choice being unwelcome.
      return status(
        state.setPreference(profileId, body?.scope, body?.name, body?.value) ? 204 : 400,
      );
    }

    const watched = WATCHED.exec(path);
    if (watched) {
      const [, profileId, setId] = watched as unknown as [string, string, string];
      if (!state.has(profileId) || !isPlayable(setId)) return status(404);
      if (method !== "PUT" && method !== "DELETE") return status(405);
      state.setWatched(profileId, setId, method === "PUT");
      return status(204);
    }

    const lists = COLLECTIONS.exec(path);
    if (lists) {
      if (!state.has(lists[1]!)) return status(404);
      if (method !== "POST") return status(405);
      const made = state.createCollection(
        lists[1]!,
        (parse(request.body) as { name?: unknown })?.name,
      );
      return made === null ? status(400) : json(JSON.stringify(made), false, 201);
    }

    const collection = COLLECTION.exec(path);
    if (collection) {
      const [, profileId, id] = collection as unknown as [string, string, string];
      if (method === "DELETE") return status(state.deleteCollection(profileId, id) ? 204 : 404);
      if (method !== "PATCH") return status(405);
      const name = (parse(request.body) as { name?: unknown })?.name;
      return status(state.renameCollection(profileId, id, name) ? 204 : 404);
    }

    const item = COLLECTION_ITEM.exec(path);
    if (item) {
      const [, profileId, id, setId] = item as unknown as [string, string, string, string];
      if (!isPlayable(setId)) return status(404);
      if (method === "PUT") return status(state.addToCollection(profileId, id, setId) ? 204 : 404);
      if (method === "DELETE") {
        return status(state.removeFromCollection(profileId, id, setId) ? 204 : 404);
      }
      return status(405);
    }

    return status(404);
  };
}

/**
 * Why a write might be refused before it is looked at.
 *
 * Neither check is authentication and neither pretends to be. What they stop
 * is the realistic drive-by against a service on loopback: a page on another
 * origin submitting a form at it. A form can only send a handful of content
 * types, none of them JSON, and a browser attaches `Origin` to every request
 * that is not a same-origin read. A caller that is not a browser is not
 * inconvenienced by either — but a caller that is not a browser could already
 * stream the whole library, which is the problem a proxy in front solves.
 */
function refuseUnsafe(request: PlayerRequest): PlayerResponse | null {
  if (request.origin != null && request.host != null) {
    let sameHost = false;
    try {
      sameHost = new URL(request.origin).host === request.host;
    } catch {
      sameHost = false;
    }
    if (!sameHost) return status(403);
  }
  // `DELETE` carries no body, so nothing to declare a type for.
  if (request.method !== "DELETE" && request.contentType !== "application/json") {
    return status(415);
  }
  return null;
}

function parse(body: string | null | undefined): unknown {
  if (typeof body !== "string" || body === "") return null;
  try {
    return JSON.parse(body);
  } catch {
    return null;
  }
}

const json = (body: string, headOnly: boolean, code = 200): PlayerResponse =>
  withBody(body, "application/json", { headOnly, status: code });

const status = bodiless;
