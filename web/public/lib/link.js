/**
 * How this page reached the server, and what that means for a title.
 *
 * The browser cannot tell a LAN from the internet; the server can, and says
 * so once at startup. Everything that asks "can this be played as it is"
 * goes through here so the answer is the same on a shelf badge and in the
 * player.
 */

import { decidePlayback } from "./playable.js";

/** Assumed local until the server says otherwise: the common case, and the
 *  one where being wrong only costs a conversion nobody needed. */
let link = { remote: false, maxBitrate: 0 };

export async function loadLink() {
  try {
    const response = await fetch("/api/player");
    if (!response.ok) return;
    const said = await response.json();
    link = { remote: said.remote === true, maxBitrate: Number(said.maxBitrate) || 0 };
  } catch {
    // A player that cannot ask still works; it just offers direct play to a
    // remote viewer who may not be able to keep up with it.
  }
}

/** Whether `set` plays as it is over this link, or has to be converted. */
export function playbackFor(set) {
  return decidePlayback(set, link);
}

/** True when the viewer is not on this machine's own network. */
export function isRemote() {
  return link.remote;
}
