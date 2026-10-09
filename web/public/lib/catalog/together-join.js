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

/** Draws `node` now and on every change, until a redraw has taken it off the page. */
function follow(node, draw) {
  let shown = false;
  draw(togetherNow());
  const stop = onTogether((view) => {
    shown ||= node.isConnected;
    if (shown && !node.isConnected) return stop();
    draw(view);
  });
}

/** The live session and its title, when this profile may see it. */
function joinable(live) {
  const set = live ? setForTogether(live.session.set) : null;
  return set && { live, set };
}

export function togetherNotice() {
  const line = el("p", "together-join");
  follow(line, ({ live }) => {
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
  follow(slot, ({ live }) => {
    const found = joinable(live);
    const shown = found && setIds.includes(found.set.setId) ? playPill("Join", () => openForTogether(found.set.setId, live.id)) : pill;
    slot.replaceChildren(...(shown ? [shown] : []));
  });
  return slot;
}
