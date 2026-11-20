/**
 * What the OS shows for a title that is playing: the lock screen, the media
 * keys and the picture-in-picture window.
 *
 * A book is the reason this exists — an audio-only file has no picture, so
 * its cover on a locked phone is the only thing that says what is playing.
 */

import { artworkUrl } from "../catalog/plate.js";

/** What one skip button moves, as the player's own skip does. */
const SKIP = 15;

/**
 * @param {{ poster?: string | null }} set
 * @param {HTMLVideoElement} video
 * @param {string} titleLine
 * @param {{ toggle?: (action: "play" | "pause") => void, at?: () => number, seekTo?: (seconds: number) => void }} [drive]
 *   how the player's own transport does these, for a title whose clock is not
 *   the element's — a converted film starts again at 0 from wherever it was
 *   seeked. Left out, the element is driven directly.
 */
export function showOnLockScreen(set, video, titleLine, drive = {}) {
  const session = globalThis.navigator?.mediaSession;
  if (!session) return;
  const at = drive.at ?? (() => video.currentTime);
  const seekTo = drive.seekTo ?? ((seconds) => { video.currentTime = seconds; });
  const toggle = drive.toggle ?? ((action) => void (action === "play" ? video.play() : video.pause()));

  if (typeof MediaMetadata !== "undefined") {
    session.metadata = new MediaMetadata({
      title: titleLine,
      artwork: set.poster ? [{ src: artworkUrl(set.poster), type: "image/jpeg" }] : [],
    });
  }
  session.setActionHandler("play", () => toggle("play"));
  session.setActionHandler("pause", () => toggle("pause"));
  session.setActionHandler("seekbackward", (details) => seekTo(Math.max(0, at() - (details?.seekOffset ?? SKIP))));
  session.setActionHandler("seekforward", (details) => seekTo(at() + (details?.seekOffset ?? SKIP)));
  session.setActionHandler("seekto", (details) => seekTo(details.seekTime));
}

/** Takes the title off the lock screen; the next open puts its own up. */
export function clearLockScreen() {
  const session = globalThis.navigator?.mediaSession;
  if (session) session.metadata = null;
}
