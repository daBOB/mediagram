/**
 * Watching a playing video and converting down when the link cannot keep up.
 *
 * Left alone, a link too slow for what it is being sent stalls, resumes,
 * plays a few seconds and stalls again — nothing having changed in between,
 * so nothing stops it happening again. The viewer gets a film delivered in
 * ten-second instalments. This notices the first time and moves to a stream
 * the link can actually carry.
 *
 * It only ever converts *down*. Falling behind is not the moment to ask for
 * more, and a link that recovers is not worth a second interruption to find
 * out.
 */

import { BufferHealth } from "./buffer-health.js";
import { FLOOR_BITS, decideSwitch } from "./adapt-bitrate.js";

/**
 * How long after a switch before another may be considered.
 *
 * A switch costs seconds of black, so two chasing each other cost more than
 * the problem they are chasing.
 */
const COOLDOWN_MS = 25_000;

/**
 * How long a newly attached source gets before it is judged at all.
 *
 * Deliberately much shorter than the cooldown. A buffer needs a moment to
 * establish, but every second beyond that is a second of the stalling the
 * viewer is already suffering — making them wait out a full cooldown for the
 * *first* switch would be the bug this module exists to fix, with extra
 * steps.
 */
const GRACE_MS = 6000;

/** What the original file demands, in bits per second, or `null`. */
export function sourceBitrate(set) {
  const bytes = Number(set.total);
  const seconds = Number(set.duration);
  if (!Number.isFinite(bytes) || !Number.isFinite(seconds) || seconds <= 0) return null;
  return (bytes * 8) / seconds;
}

/**
 * Attaches the watch to a video element.
 *
 * `onSwitch(targetBits)` is called when it decides a conversion at that rate
 * would fit; `onExhausted()` when there is nothing lower left to try, which
 * is a thing the viewer should be told rather than a thing to keep retrying.
 *
 * @param {{video: HTMLVideoElement, onSwitch: (bits: number) => void,
 *          onExhausted: () => void, now?: () => number}} options
 */
export function watchPlayback(options) {
  const { video, onSwitch, onExhausted } = options;
  const now = options.now ?? (() => Date.now());

  const health = new BufferHealth();
  /** What this source is capped at, or `null` while playing the original. */
  let capBits = null;
  let sourceBits = null;
  /** The earliest a switch may be made. */
  let eligibleAt = 0;
  let exhausted = false;
  /** Whether the source about to be attached is one this watch asked for. */
  let ours = false;

  /**
   * Starts measuring a newly attached source.
   *
   * A restart this watch asked for keeps the cooldown it just set; anything
   * else — a title opened, a position jumped to — is the viewer's doing and
   * gets the short grace. Without that distinction every automatic switch
   * would reset its own cooldown and the next one would follow six seconds
   * later, which is the thrash the cooldown exists to prevent.
   */
  function begin(attached) {
    health.reset();
    capBits = attached.capBits ?? null;
    sourceBits = attached.sourceBits ?? null;
    exhausted = false;
    if (ours) ours = false;
    else eligibleAt = now() + GRACE_MS;
  }

  function look() {
    if (sourceBits === null && capBits === null) return;
    if (video.readyState === 0) return;

    const buffered = video.buffered;
    if (buffered.length === 0) return;

    const verdict = health.sample({
      now: now(),
      currentTime: video.currentTime,
      bufferedEnd: buffered.end(buffered.length - 1),
      paused: video.paused,
    });
    if (verdict.state === "ok") return;
    if (now() < eligibleAt) return;

    const decision = decideSwitch({
      state: verdict.state,
      fitting: health.fittingBitrate(capBits ?? sourceBits),
      currentCapBits: capBits,
    });

    if (decision === null) {
      // Nothing lower left. Said once, not every half minute.
      if (capBits !== null && capBits <= FLOOR_BITS && !exhausted) {
        exhausted = true;
        onExhausted();
      }
      return;
    }

    eligibleAt = now() + COOLDOWN_MS;
    // The callback restarts playback, which calls `begin`; this tells it the
    // restart was ours, so it keeps the cooldown rather than replacing it
    // with the much shorter grace.
    ours = true;
    onSwitch(decision.targetBits);
  }

  // `timeupdate` fires only while playback advances, which is exactly when
  // the buffer means something — but it stops during a stall, which is
  // exactly when this needs to be looking. `progress` fires as bytes arrive
  // regardless, and `waiting` is the stall itself.
  const EVENTS = ["timeupdate", "progress", "waiting"];
  for (const name of EVENTS) video.addEventListener(name, look);

  return {
    begin,
    stop() {
      for (const name of EVENTS) video.removeEventListener(name, look);
    },
  };
}
