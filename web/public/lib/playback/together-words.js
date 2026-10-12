/**
 * What watch together says in the player. The web is the reference: the
 * Android surfaces copy these word for word.
 */

import { clockTime } from "../format.js";
import { speedLabel } from "./transport.js";

export const ENDED = "Watch together ended";
export const COULD_NOT_START = "Watch together couldn't start";
export const NOT_HERE = "That title isn't on this profile";
export const LEFT_NOT_HERE = "Left watch together: that title isn't on this profile";

/** The two-second notice for another screen's edit `session`, as `changeOf` named it. */
export function say({ change, label }, session) {
  const by = label ?? "Another screen";
  if (change === "joined") return `${by} joined`;
  if (change === "left") return `${by} left`;
  if (change === "sought") return `Skipped to ${clockTime(session.pos)} by ${by}`;
  if (change === "rate") return `${speedLabel(session.rate)} by ${by}`;
  if (change === "title") return `Next episode by ${by}`;
  return `${change === "paused" ? "Paused" : "Resumed"} by ${by}`;
}
