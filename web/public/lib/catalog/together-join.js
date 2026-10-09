/**
 * Join, where a viewer looks for something to watch: one notice line at the
 * top of Home, and Join in place of Play on the page of the title a session
 * is playing. Both update in place as the session changes, because Home
 * doesn't redraw while it is shown for nothing else. Only a title in this
 * profile's catalog is offered: a kids profile is not shown one past its limit.
 */

import { el } from "../dom.js";
import { onTogether, openForTogether, setForTogether, togetherNow } from "../together-now.js";
import { playPill } from "./title-spread.js";
import { episodeShort } from "./series-resume.js";

/** The listener each kind draws through. Each is in one place at a time, so the next one drawn replaces it. */
const following = new Map();

/** Draws now and on every change, until the page that holds it is drawn again. */
function follow(kind, draw) {
  following.get(kind)?.();
  draw(togetherNow());
  following.set(kind, onTogether(draw));
}

/**
 * The live session and its title, when this profile may see it. Hidden, not
 * greyed, while Telegram is out of reach: the server can't know of a session
 * then, so there is none to offer. The player's own button says why.
 */
function joinable(live) {
  const set = live ? setForTogether(live.session.set) : null;
  return set && { live, set };
}

export function togetherNotice() {
  const line = el("p", "together-join");
  follow("notice", ({ live }) => {
    const found = joinable(live);
    line.hidden = !found;
    line.replaceChildren();
    if (!found) return;
    const { set } = found;
    const title = set.show ? `${set.show} ${episodeShort(set)}` : set.title ?? set.setId;
    const who = live.session.members.map((m) => m.label).join(", ");
    const join = el("button", "together-join-button", "Join");
    join.type = "button";
    join.addEventListener("click", () => openForTogether(set.setId, live.id));
    line.append(el("span", null, `Watching together: ${title} · ${who}`), join);
  });
  return line;
}

/** `pill`, or Join while a live session plays one of `setIds`; nothing for no `pill` and no session. */
export function joinOr(setIds, pill) {
  const slot = el("span", "together-slot");
  follow("slot", ({ live }) => {
    const found = joinable(live);
    const shown = found && setIds.includes(found.set.setId) ? playPill("Join", () => openForTogether(found.set.setId, live.id)) : pill;
    slot.replaceChildren(...(shown ? [shown] : []));
  });
  return slot;
}
