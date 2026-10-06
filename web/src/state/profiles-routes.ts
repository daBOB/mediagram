/**
 * The profile routes: who watches this library, and managing them.
 *
 * Every write has passed the dispatcher's `refuseUnsafeBrowserWrite` check
 * before it gets here; whether the PIN is right and the rule agrees is
 * `profiles-manage.ts`'s to decide, and a refusal answers with its reason so
 * the page can say what went wrong.
 */

import { jsonBody } from "../http/browser-write";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { bodiless, withBody } from "../response";
import type { Profile } from "./profiles";
import type { Refusal, Refused } from "./profiles-manage";
import { json, P } from "./route-shared";
import type { WatchState } from "./store";

const PROFILES = /^\/api\/profiles$/;
const PROFILE = new RegExp(`^/api/profiles/${P}$`);
const ACTION = new RegExp(`^/api/profiles/${P}/(unlock|claim-admin|pin|kids-age)$`);

/** A wrong PIN and a refused role are both 403; a taken name, a missing PIN and an unheard household all 409; the body says which. */
const CODES: Record<Refusal, number> = {
  invalid: 400,
  "name-taken": 409,
  "not-found": 404,
  wait: 429,
  "no-pin": 409,
  "wrong-pin": 403,
  "not-allowed": 403,
  "not-synced": 409,
};

/** Answers a profile route, or `null` when the path is not one. */
export function profileRoute(request: PlayerRequest, state: WatchState): PlayerResponse | null {
  const { method, path } = request;
  const list = PROFILES.test(path);
  const named = PROFILE.exec(path);
  const acting = ACTION.exec(path);
  if (!list && named === null && acting === null) return null;

  // Who watches this library: the one question askable before anyone has
  // said who they are. It says whether a profile has a PIN, never what, and
  // whether the household has been heard — until then no first profile is made.
  if (method === "GET" || method === "HEAD") {
    if (!list) return bodiless(405);
    const said = { remembers: state.remembers, heard: state.household.heard(), profiles: state.profiles() };
    return json(JSON.stringify(said), method === "HEAD");
  }

  const said = jsonBody(request.body) ?? {};
  const actorId = typeof said.actorId === "string" ? said.actorId : "";
  const manage = state.manage();

  if (list) {
    if (method !== "POST") return bodiless(405);
    // Nobody asking is the first profile, on a player with no grown-up yet.
    if (said.actorId === undefined) return answer(manage.createFirst(said.name, said.newPin));
    // Only a literal true: a restricting flag is not switched on by accident.
    return answer(said.kids === true
      ? manage.createKid(actorId, said.pin, said.name, said.kidsAge)
      : manage.createGrownUp(actorId, said.pin, said.name, said.newPin));
  }
  if (named !== null) {
    return method === "DELETE" ? answer(manage.remove(actorId, said.pin, named[1]!)) : bodiless(405);
  }

  const id = acting![1]!;
  switch (`${method} ${acting![2]}`) {
    case "POST unlock":
      return answer(manage.unlock(id, said.pin));
    case "POST claim-admin":
      return answer(manage.claimAdmin(id, said.pin));
    case "PUT pin":
      return answer(manage.setPin(actorId, said.pin, id, said.newPin));
    case "PUT kids-age":
      return answer(manage.setKidsAge(actorId, said.pin, id, said.age));
    default:
      return bodiless(405);
  }
}

/** 204 for done, 201 and the profile for one made, a reason for a refusal. */
function answer(result: Refused | Profile | null): PlayerResponse {
  if (result === null) return bodiless(204);
  if (!("reason" in result)) return json(JSON.stringify(result), false, 201);
  const headers: Record<string, string> =
    result.retryAfter === undefined ? {} : { "retry-after": String(result.retryAfter) };
  return withBody(JSON.stringify(result), "application/json", { status: CODES[result.reason], headers });
}
