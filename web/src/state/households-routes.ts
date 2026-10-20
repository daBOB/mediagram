/**
 * The household routes: the owner's actions, and a device joining one.
 *
 * Listing households is `GET /api/profiles`'s, beside the profiles in them.
 * Every write has passed the dispatcher's `refuseUnsafeBrowserWrite` check
 * before it gets here; the PIN and the rule are `households-manage.ts`'s, and
 * a refusal answers with its reason, as a profile route's does.
 */

import { jsonBody } from "../http/browser-write";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { bodiless } from "../response";
import { answer } from "./profiles-routes";
import { P } from "./route-shared";
import type { WatchState } from "./store";

const HOUSEHOLDS = /^\/api\/households$/;
const HOUSEHOLD = new RegExp(`^/api/households/${P}$`);
const ACTION = new RegExp(`^/api/households/${P}/(name|admin|move|join)$`);

/** Answers a household route, or `null` when the path is not one. */
export function householdRoute(request: PlayerRequest, state: WatchState): PlayerResponse | null {
  const { method, path } = request;
  const list = HOUSEHOLDS.test(path);
  const named = HOUSEHOLD.exec(path);
  const acting = ACTION.exec(path);
  if (!list && named === null && acting === null) return null;

  const said = jsonBody(request.body) ?? {};
  const text = (value: unknown) => (typeof value === "string" ? value : "");
  const actorId = text(said.actorId);
  const manage = state.manage();

  if (list) {
    if (method !== "POST") return bodiless(405);
    // An existing grown-up by id, else a new one by name.
    const admin = typeof said.adminId === "string"
      ? { id: said.adminId, newPin: said.newPin }
      : { name: said.adminName, newPin: said.newPin };
    return answer(manage.createHousehold(actorId, said.pin, said.name, admin));
  }
  if (named !== null) {
    return method === "DELETE" ? answer(manage.deleteHousehold(actorId, said.pin, named[1]!)) : bodiless(405);
  }

  const id = acting![1]!;
  switch (`${method} ${acting![2]}`) {
    case "PUT name":
      return answer(manage.renameHousehold(actorId, said.pin, id, said.name));
    case "PUT admin":
      return answer(manage.setHouseholdAdmin(actorId, said.pin, id, text(said.adminId), said.newPin));
    case "POST move":
      return answer(manage.moveToHousehold(actorId, said.pin, id, text(said.profileId)));
    case "POST join":
      return answer(manage.join(id, said.pin));
    default:
      return bodiless(405);
  }
}
