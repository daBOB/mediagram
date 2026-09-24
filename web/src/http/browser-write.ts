import type { PlayerRequest, PlayerResponse } from "./contracts";
import { bodiless } from "../response";

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
export function refuseUnsafeBrowserWrite(request: PlayerRequest): PlayerResponse | null {
  if (request.origin != null && request.host != null) {
    let sameHost = false;
    try {
      sameHost = new URL(request.origin).host === request.host;
    } catch {
      sameHost = false;
    }
    if (!sameHost) return bodiless(403);
  }
  // `DELETE` carries no body, so nothing to declare a type for.
  if (request.method !== "DELETE" && request.contentType !== "application/json") {
    return bodiless(415);
  }
  return null;
}
