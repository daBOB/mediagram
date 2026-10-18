/**
 * Drawing the remote: the title and its backdrop, the seek bar at the TV's
 * position, the transport and the speed. It only reads a session and the
 * catalog; what the buttons do is in `cast-remote.js`. It draws with the
 * player's own bar model, icons and words, so the two cards read alike.
 */

import { artworkUrl } from "../catalog/plate.js";
import { titleLine } from "../format.js";
import { ICONS, playLabel, setIcon, skipButton, speedLabel } from "../playback/transport.js";
import { seekModel } from "../playback/seek-model.js";
import { expectedPosition } from "../together/follow.js";
import { CAST_WORDS } from "../together/cast-words.js";
import { nextForTogether, setForTogether } from "../together-now.js";

/** How far the remote's skip buttons step. */
export const JUMP_SECONDS = 10;

export const part = (name) => document.getElementById(name === "root" ? "cast-remote" : `cast-remote-${name}`);

/** What never changes while the remote is open: the skip buttons and the Stop word. */
export function drawFixed() {
  skipButton(part("back"), ICONS.back, JUMP_SECONDS, "Back");
  skipButton(part("forward"), ICONS.forward, JUMP_SECONDS, "Forward");
  part("stop").textContent = CAST_WORDS.stop;
  part("label").textContent = CAST_WORDS.playingOnTv;
}

/** The title, its picture and whether a next title follows: once per title. */
export function drawTitle(s) {
  const set = setForTogether(s.set);
  const art = part("art");
  const key = set?.backdrop ?? set?.poster ?? null;
  art.hidden = !key;
  if (key) art.src = artworkUrl(key);
  part("title").textContent = set ? titleLine(set) : "";
  part("next").hidden = nextForTogether(s.set) === null;
}

/** The parts that move: the clock, the play button, the speed and whether the TV has an audio choice. */
export function drawCard(s, now) {
  const pos = expectedPosition(s, now);
  const bar = seekModel({ runtime: setForTogether(s.set)?.duration ?? 0, at: pos });
  const seek = part("seek");
  seek.max = String(bar.max);
  part("audio").hidden = (s.cast?.tracks.audio.length ?? 0) <= 1;
  part("speed").textContent = speedLabel(s.rate);
  const play = part("play");
  const state = s.playing ? "playing" : "paused";
  if (play.dataset.state !== state) {
    play.dataset.state = state;
    setIcon(play, s.playing ? ICONS.pause : ICONS.play);
  }
  play.setAttribute("aria-label", playLabel(!s.playing));
  // A bar the viewer is holding is theirs, as in the player.
  if (document.activeElement === seek || seek.matches(":active")) return;
  seek.value = String(bar.value);
  part("at").textContent = bar.elapsed;
  part("end").textContent = bar.total;
  seek.setAttribute("aria-valuetext", bar.label);
  seek.style.setProperty("--played", `${(bar.played * 100).toFixed(3)}%`);
}
