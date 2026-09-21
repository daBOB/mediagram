/**
 * Noticing that the download cannot keep up, before the stalls start.
 *
 * The signal is the buffer: how many seconds of video are ready ahead of the
 * playhead, and whether that number is growing or shrinking. Shrinking while
 * playing means every second of watching costs more than a second of
 * downloading, and the only question left is when it runs out. A player left
 * to discover that for itself discovers it as a stall, then another, then
 * another, because nothing about the situation has changed in between.
 *
 * Two traps, both found the hard way.
 *
 * **A full buffer looks exactly like a slow download.** A browser that has
 * buffered as much as it wants stops fetching, so the buffer stops growing —
 * from outside, identical to a link that cannot keep up. Judging that as
 * falling behind would convert titles that were playing perfectly. So nothing
 * is judged unless the player is still hungry, which is `HUNGRY_SECONDS`.
 *
 * **The end of a file looks exactly like a slow download.** The trap above
 * again, with a different cause and no `HUNGRY_SECONDS` to catch it: in the
 * last `HUNGRY_SECONDS` of any title the player is genuinely hungry — it
 * cannot hold that much ahead of itself, because there is not that much left
 * — so measurement begins, and the buffer cannot grow because there is
 * nothing to fetch. The rate falls to nought and the watch concludes the link
 * has died. Every title, every time, for its last forty seconds. So a buffer
 * that has reached the end of a media whose duration has stopped moving is
 * not judged at all; a duration still growing is a playlist still being
 * written, which is a different situation and still worth watching.
 *
 * **A stalled player looks exactly like a healthy one**, if you measure the
 * wrong thing. Once the buffer is empty the playhead advances only as fast as
 * bytes arrive, so buffered-seconds-gained per second-played is exactly 1.0 —
 * "keeping up" — while the viewer watches a spinner. The rate is therefore
 * measured against the wall clock, which is the only formulation true both
 * while playing smoothly and while stalling.
 */

/** Above this much buffered, the player has stopped asking for more. */
const HUNGRY_SECONDS = 45;

/** Below this much, a stall is seconds away rather than minutes. */
const STARVING_SECONDS = 5;

/** How long a shortfall must last before it counts. Dips happen. */
const SUSTAINED_MS = 6000;

/**
 * Buffered seconds gained per second of wall clock, below which the link
 * cannot sustain playback at 1×.
 */
const KEEPING_UP = 0.97;

/** A jump larger than this is a seek, not playback. */
const SEEK_SECONDS = 3;

/**
 * How near the end counts as at it.
 *
 * A buffered range ends on a frame or a segment boundary rather than exactly
 * on the duration, and the two disagree by a little in every container.
 */
const END_TOLERANCE = 0.5;

/** What the measurement leaves spare when it suggests a bitrate. */
const HEADROOM = 0.8;

/**
 * @typedef {object} Verdict
 * @property {"ok"|"behind"|"starving"} state
 * @property {number|null} ratio media-seconds delivered per second of wall clock
 * @property {number} bufferAhead seconds of video ready beyond the playhead
 * @property {boolean} measured whether *this* sample recomputed `ratio`
 *
 * `measured` is what separates a live rate from a remembered one. `ratio` is
 * carried through every verdict so a caller never sees it flicker to null on
 * one paused frame — but a full buffer stops the browser fetching, and a rate
 * from before that happened describes a situation that has since ended.
 * Anything *showing* the rate rather than acting on it wants this.
 */

export class BufferHealth {
  constructor(options = {}) {
    this.hungrySeconds = options.hungrySeconds ?? HUNGRY_SECONDS;
    this.starvingSeconds = options.starvingSeconds ?? STARVING_SECONDS;
    this.sustainedMs = options.sustainedMs ?? SUSTAINED_MS;
    this.reset();
  }

  /** Forgets everything measured. Call when the source changes. */
  reset() {
    this.last = null;
    /** When the shortfall began, or `null` while it is keeping up. */
    this.shortfallSince = null;
    this.ratio = null;
  }

  /**
   * One observation. Returns the verdict as of now.
   *
   * @param {{now: number, currentTime: number, bufferedEnd: number,
   *          paused: boolean}} at
   * @returns {Verdict}
   */
  sample(at) {
    const bufferAhead = Math.max(0, at.bufferedEnd - at.currentTime);
    const previous = this.last;
    this.last = at;

    // A seek lands in a different buffer, and comparing across the jump would
    // read the discontinuity as a collapse.
    const elapsed = previous ? (at.now - previous.now) / 1000 : 0;
    const played = previous ? at.currentTime - previous.currentTime : 0;
    if (played < 0 || played > SEEK_SECONDS) {
      this.reset();
      return { state: "ok", ratio: null, bufferAhead, measured: false };
    }
    // A paused player consumes nothing, so there is nothing to fall behind.
    // Note this is not the same as a *stalled* one, which is trying to play
    // and failing — that is the case this whole module is about.
    if (at.paused || previous === null || elapsed <= 0) {
      return { state: "ok", ratio: this.ratio, bufferAhead, measured: false };
    }

    // Only while the player still wants more. Above the mark it has what it
    // asked for and has stopped fetching, which is not a shortfall.
    if (bufferAhead >= this.hungrySeconds) {
      this.shortfallSince = null;
      return { state: "ok", ratio: this.ratio, bufferAhead, measured: false };
    }

    /**
     * Nor is having everything there is.
     *
     * Both halves matter. Reaching the end of what is buffered means there is
     * nothing left to ask for; the duration standing still means nothing more
     * is coming. A growing duration is a playlist still being written, where
     * a buffer at the end really does mean the encoder is the bottleneck and
     * is worth acting on.
     */
    const finished =
      Number.isFinite(at.duration) &&
      at.bufferedEnd >= at.duration - END_TOLERANCE &&
      at.duration === previous.duration;
    if (finished) {
      this.shortfallSince = null;
      return { state: "ok", ratio: this.ratio, bufferAhead, measured: false };
    }

    // Against the wall clock, not against playback. See the file comment:
    // dividing by seconds played reads a stalled player as a healthy one,
    // because a stalled player plays exactly as fast as it downloads.
    const gained = at.bufferedEnd - previous.bufferedEnd;
    const ratio = gained / elapsed;
    // Smoothed, because one sample is mostly the browser's fetch schedule.
    this.ratio = this.ratio === null ? ratio : this.ratio * 0.7 + ratio * 0.3;

    if (this.ratio >= KEEPING_UP) {
      this.shortfallSince = null;
      return { state: "ok", ratio: this.ratio, bufferAhead, measured: true };
    }

    if (this.shortfallSince === null) this.shortfallSince = at.now;
    const sustained = at.now - this.shortfallSince >= this.sustainedMs;
    if (!sustained) return { state: "ok", ratio: this.ratio, bufferAhead, measured: true };

    return {
      state: bufferAhead <= this.starvingSeconds ? "starving" : "behind",
      ratio: this.ratio,
      bufferAhead,
      measured: true,
    };
  }

  /**
   * A bitrate the link looked able to carry, given what the source was.
   *
   * `ratio` is media-seconds delivered per second of wall clock, so a link
   * managing half of realtime on a stream of a given bitrate is carrying half
   * that bitrate. The suggestion sits under that rather than on it: aiming
   * exactly at the measured rate leaves a stream that stalls on the first bad
   * minute.
   *
   * `null` when nothing has been measured, because a guess dressed as a
   * measurement is worse than admitting there is none.
   */
  fittingBitrate(sourceBitrate) {
    if (this.ratio === null || !Number.isFinite(sourceBitrate) || sourceBitrate <= 0) return null;
    return Math.round(sourceBitrate * this.ratio * HEADROOM);
  }
}
