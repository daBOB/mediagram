/**
 * What every state route module shares: the profile path segment, reading a
 * body, and the two shapes of answer.
 *
 * Its own module so `routes.ts` and `stats-routes.ts` both import it rather
 * than one importing the other.
 */

import type { PlayerResponse } from "../http/contracts";
import { bodiless, withBody } from "../response";

/**
 * The profile is a path segment, not a parameter.
 *
 * `navigator.sendBeacon` is how a position survives the tab closing, and it
 * cannot set a header — so whoever is watching has to travel in the URL. In
 * the path rather than the query because a missing segment is then a route
 * that does not match, and the alternative to a 404 is a write that quietly
 * lands in somebody else's rows.
 */
export const P = "([A-Za-z0-9-]{1,64})";

export function parse(body: string | null | undefined): unknown {
  if (typeof body !== "string" || body === "") return null;
  try {
    return JSON.parse(body);
  } catch {
    return null;
  }
}

export const json = (body: string, headOnly: boolean, code = 200): PlayerResponse =>
  withBody(body, "application/json", { headOnly, status: code });

export const status = bodiless;
