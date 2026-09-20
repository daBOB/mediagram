/**
 * When a conversion does not have to touch the picture.
 *
 * A transcode exists for a reason, and `decidePlayback` already names it:
 * the container, the video codec, the audio codec, or a bitrate the link
 * cannot carry. Only two of those four are about the video stream.
 *
 * Which means a Matroska file holding h264 — 146 of this library's 566 sets —
 * has been re-encoded end to end, on a GPU, at a bitrate cap and a quality
 * cost, to solve a problem with the box the picture came in. The picture was
 * always exactly what the browser wanted.
 *
 * `-c:v copy` moves those bytes instead. No decode, no encode, no loss, and
 * far faster than realtime, so the wait becomes muxing rather than encoding.
 *
 * The same applies to changing audio language, which is why this exists now:
 * the player's only way to choose an audio stream is to build a new file, and
 * on a title that was playing perfectly it was building a *worse* one.
 */

import { decidePlayback } from "../../public/lib/playable.js";

/** What the index knows about a set, as far as this decision needs it. */
export interface CopyProfile {
  container: string;
  vcodec: string | null;
  acodec: string | null;
  total?: number;
  duration?: number | null;
}

/** How the file will travel, and what the page has already said about it. */
export interface CopyLink {
  remote?: boolean;
  maxBitrate?: number;
  /**
   * The page asked for a specific ceiling.
   *
   * It only does that after measuring playback falling behind, so it is a
   * statement that this link is already struggling — the strongest evidence
   * available that the original must not be sent. Stronger than the index's
   * average bitrate, which is an average and says nothing about the scene
   * that caused the stall.
   */
  capAsked?: boolean;
}

/**
 * Whether the video stream can be carried across untouched.
 *
 * Reads `decidePlayback`'s flags and nothing else, so there is still one copy
 * of the policy: teaching the player about a new codec teaches this at the
 * same time, and the two cannot drift into disagreeing about what is
 * playable.
 *
 * **The bitrate is a refusal, not a preference.** A cap exists because the
 * link measurably could not carry the original; copying the original ignores
 * the only thing the cap was for, and the viewer's connection — not their
 * patience — is what pays for it.
 *
 * @param link how the file will travel, the same shape the browser uses
 */
export function canCopyVideo(profile: CopyProfile, link: CopyLink = {}): boolean {
  if (link.capAsked === true) return false;
  const { blocking } = decidePlayback(profile, link);
  if (blocking.video) return false;
  if (blocking.bitrate) return false;
  // Nothing about the video is wrong, so whatever else is wrong — a Matroska
  // box, an AC3 track, both — is fixed by re-wrapping and re-encoding sound.
  return true;
}
