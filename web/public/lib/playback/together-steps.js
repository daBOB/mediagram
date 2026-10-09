/**
 * Moving the player to where a watch-together session is, by `decide` in
 * `../together/follow.js`. Nothing here is sent anywhere: these are the
 * changes a screen makes because it is following.
 */

import { decide } from "../together/follow.js";
import { serverNow } from "../together-now.js";

/** Below this the picture is loading or buffering. Nobody waits for it, and the next check catches it up. */
const HAVE_FUTURE_DATA = 3;

/** `seekTo` and `setRate` move the player without telling anyone; `converting` is whether a seek restarts ffmpeg. */
export function playerSteps({ video, filmTime, seekTo, setRate, converting }) {
  const own = (setId) => ({ set: setId, playing: !video.paused, pos: filmTime(), rate: video.playbackRate });

  /**
   * Brings the player, on `setId`, to `session`; or names the title to open
   * first. A drift check passes over a screen still loading or buffering, and
   * never seeks a conversion: that starts ffmpeg again, which lands it behind
   * by as long as starting takes, every time. An element at its end waits for
   * the next title, which Up next or the viewer opens: a play there would start
   * this one again from nothing.
   * @returns {string | null}
   */
  function bring(session, setId, drifting) {
    if (drifting && (video.ended || video.seeking || video.readyState < HAVE_FUTURE_DATA)) return null;
    let atEnd = video.ended;
    for (const step of decide(own(setId), session, serverNow())) {
      if (step.kind === "open") return step.set;
      if (step.kind === "rate") setRate(step.rate);
      if (step.kind === "seek" && !(drifting && converting())) {
        seekTo(step.pos);
        atEnd = false;
      }
      if (step.kind === "play" && !atEnd) void video.play().catch(() => {});
      if (step.kind === "pause") video.pause();
    }
    return null;
  }

  return { own, bring };
}
