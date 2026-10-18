/**
 * Drawing the remote: the title and its backdrop, the seek bar at the TV's
 * position, the play button, speed and the menu of choices. It only reads a
 * session and the catalog; what the buttons do is in `cast-remote.js`.
 */

import { el } from "../dom.js";
import { clockTime, episodeLabel } from "../format.js";
import { speedLabel } from "../playback/transport.js";
import { expectedPosition } from "../together/follow.js";
import { CAST_WORDS } from "../together/cast-words.js";
import { nextForTogether, setForTogether } from "../together-now.js";

export const part = (name) => document.getElementById(name === "root" ? "cast-remote" : `cast-remote-${name}`);

const titleOf = (set) => [set.show, episodeLabel(set), set.title].filter(Boolean).join(" · ");

/** The words and the picture, once per title. */
export function drawTitle(s) {
  const set = setForTogether(s.set);
  const art = part("art");
  const src = set?.backdrop ?? set?.poster ?? "";
  art.hidden = !src;
  if (src) art.src = src;
  part("title").textContent = set ? titleOf(set) : "";
  part("label").textContent = CAST_WORDS.playingOnTv;
  part("stop").textContent = CAST_WORDS.stop;
  part("next").hidden = nextForTogether(s.set) === null;
}

/** The parts that move: the clock, the play button and the speed. */
export function drawCard(s, now) {
  const pos = expectedPosition(s, now);
  const length = setForTogether(s.set)?.duration ?? 0;
  const seek = part("seek");
  seek.max = String(Math.max(length, pos));
  seek.value = String(Math.floor(pos));
  part("at").textContent = clockTime(pos);
  part("end").textContent = length ? clockTime(length) : "";
  const play = part("play");
  play.textContent = s.playing ? "Pause" : "Play";
  play.setAttribute("aria-label", play.textContent);
  part("speed").textContent = speedLabel(s.rate);
}

/** The menu's rows: `[{ label, current, pick }]`. An empty list closes it. */
export function drawMenu(rows) {
  const menu = part("menu");
  menu.hidden = rows.length === 0;
  menu.replaceChildren(...rows.map(({ label, current, pick }) => {
    const row = el("button", "menu-item", label);
    row.type = "button";
    row.setAttribute("aria-pressed", String(current === true));
    row.addEventListener("click", () => { menu.hidden = true; pick(); });
    return row;
  }));
}
