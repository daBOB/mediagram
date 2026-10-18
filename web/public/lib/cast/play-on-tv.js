/**
 * Play on TV on a title page: a pill beside Play or Resume that offers the
 * title to the TV from the point Play would start at. It is greyed, with the
 * reason, while Telegram is out of reach, as the player's button is.
 */

import { el } from "../dom.js";
import { inTogether, onTogether, togetherNow, UNREACHABLE } from "../together-now.js";
import { profileId } from "../watch-state.js";
import { CAST_WORDS } from "../together/cast-words.js";
import { askTv } from "./cast-ask.js";
import { openRemote } from "./cast-remote.js";

/** Why Play on TV can't be pressed now, or `null` when it can. */
export function castReason() {
  if (inTogether()) return CAST_WORDS.leaveFirst;
  const { available, reason } = togetherNow();
  return available ? null : reason ?? UNREACHABLE;
}

/** Greys `button` with its reason, now and on every change, until `stop` is called. */
export function greyWhileUnavailable(button) {
  const draw = () => {
    const reason = castReason();
    button.disabled = reason !== null;
    button.title = reason ?? "";
    if (reason) button.setAttribute("aria-label", `${CAST_WORDS.playOnTv}: ${reason}`);
    else button.removeAttribute("aria-label");
  };
  draw();
  return onTogether(draw);
}

/** The listener of the pill drawn last: a page is drawn again before the next one is shown. */
let stopFollowing = null;

/**
 * @param {string} setId the title to cast
 * @param {number} pos the second to start from
 * @returns {HTMLElement}
 */
export function castPill(setId, pos) {
  const button = el("button", "pill pill-line", CAST_WORDS.playOnTv);
  button.type = "button";
  stopFollowing?.();
  stopFollowing = greyWhileUnavailable(button);
  button.addEventListener("click", async () => {
    const live = await askTv({ set: setId, playing: true, pos, rate: 1 }, profileId());
    if (live) openRemote(live);
  });
  return button;
}
