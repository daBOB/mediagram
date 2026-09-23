/**
 * How this page reached the server, and what that means for a title.
 *
 * The browser cannot tell a LAN from the internet; the server can, and says
 * so once at startup. Everything that asks "can this be played as it is"
 * goes through here so the answer is the same on a shelf badge and in the
 * player.
 */

import { browserDecodes } from "./codec-support.js";
import { decidePlayback } from "./playable.js";

/** Assumed local until the server says otherwise: the common case, and the
 *  one where being wrong only costs a conversion nobody needed. */
let link = { remote: false, maxBitrate: 0, decodes: browserDecodes() };

/**
 * What the server said about the catalogue, or `null` before it answered.
 *
 * Kept beside the link because it arrives in the same answer: the route is
 * "facts about this session", and which catalogue is open is one of them.
 */
let catalog = null;

export async function loadLink() {
  try {
    const response = await fetch("/api/player");
    if (!response.ok) return;
    const said = await response.json();
    link = { ...link, remote: said.remote === true, maxBitrate: Number(said.maxBitrate) || 0 };
    catalog = {
      origin: ["package", "channel"].includes(said.catalog?.origin) ? said.catalog.origin : "local",
      publishedAt: Number.isFinite(said.catalog?.publishedAt) ? said.catalog.publishedAt : null,
      schema: Number(said.schema) || null,
    };
  } catch {
    // A player that cannot ask still works; it just offers direct play to a
    // remote viewer who may not be able to keep up with it.
  }
}

/** Which catalogue is open, or `null` if the server was never reached. */
export function catalogOf() {
  return catalog;
}

/** Whether `set` plays as it is over this link, or has to be converted. */
export function playbackFor(set) {
  return decidePlayback(set, link);
}

/**
 * What this browser decodes beyond the everywhere-list, as a transcode
 * request says it. The server copies the picture rather than re-encoding it
 * when the title's codec is on it.
 */
export function decodesParam() {
  return link.decodes.length > 0 ? `&vcodecs=${link.decodes.join(",")}` : "";
}
