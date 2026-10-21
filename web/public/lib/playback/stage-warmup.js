/**
 * The stage's warm-up line: the film leader stays up, past the first frame,
 * with how full the buffer is under it, while a title that started itself
 * waits for its buffer — the Android player's warm-up, said the same way.
 */

import { warmUpLine } from "./autoplay.js";

const line = () => document.getElementById("stage-warmup");

/** Shows the leader and the line for `ahead` seconds buffered. */
export function showWarmUp(ahead) {
  const warmUp = line();
  warmUp.textContent = warmUpLine(ahead);
  warmUp.hidden = false;
  document.getElementById("stage-leader").hidden = false;
}

/**
 * Takes the line down, and the leader with it once there is a frame to show
 * — the leader's own script left both up for as long as the warm-up ran.
 * @param {HTMLVideoElement} video
 */
export function hideWarmUp(video) {
  const warmUp = line();
  if (warmUp.hidden) return;
  warmUp.hidden = true;
  warmUp.textContent = "";
  if (video.readyState >= 2) { // HAVE_CURRENT_DATA: a frame to show
    document.getElementById("stage-leader").hidden = true;
    document.getElementById("stage-status").textContent = "";
  }
}
