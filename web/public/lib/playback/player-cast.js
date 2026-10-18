/**
 * The player's Play on TV button. An accepted cast closes the player, which
 * saves its progress as closing always does, and then opens the remote; a
 * refused one leaves the player playing.
 */

import { profileId } from "../watch-state.js";
import { askTv } from "../cast/cast-ask.js";
import { openRemote } from "../cast/cast-remote.js";
import { onLeaveTogether } from "../together-now.js";
import { grey, greyWhileUnavailable } from "../cast/play-on-tv.js";

/**
 * @param {{ button: HTMLButtonElement, video: HTMLVideoElement, filmTime: () => number,
 *   setId: () => string | null, close: () => void }} parts
 */
export function mountPlayerCast({ button, video, filmTime, setId, close }) {
  greyWhileUnavailable(button);
  // Leaving a session tells only these callbacks, not the listeners above.
  onLeaveTogether(() => grey(button));
  button.addEventListener("click", async () => {
    const set = setId();
    if (set === null) return;
    const live = await askTv({ set, playing: !video.paused, pos: filmTime(), rate: video.playbackRate }, profileId());
    if (!live) return;
    close();
    openRemote(live);
  });
}
