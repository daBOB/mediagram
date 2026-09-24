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

/**
 * Above this much in hand, nothing is judged to be falling behind.
 *
 * Not because a link cannot be slow with more buffered, but because above it a
 * slow link and a browser that has paused its own refill look identical, and
 * the second is far more common. Chrome playing a file directly holds twenty
 * to thirty seconds and lets it sag by several before topping it up; a replay
 * of five real minutes of that, and of harsher synthetic refill patterns, read
 * as "behind" again and again at any threshold above this. No browser lets
 * its buffer fall this low on a link that is keeping up — so reaching it is
 * the evidence, and what is left still covers a restart.
 */
const BEHIND_SECONDS = 10;

/**
 * Below this rate a nearly empty buffer is urgent, and skips `SUSTAINED_MS`.
 * Well under `KEEPING_UP`, so a player that starts on two seconds in hand and
 * holds them exactly — which Chrome does — is left alone.
 */
const LOSING = 0.8;

/** How long a shortfall must last before it counts. Dips happen. */
const SUSTAINED_MS = 6000;

/**
 * Buffered seconds gained per second of wall clock, below which the link
 * cannot sustain playback at 1×.
 */
const KEEPING_UP = 0.97;

/**
 * How much wall clock the rate is measured across, and the least that counts.
 *
 * Browsers do not fetch smoothly. Chrome playing a file directly keeps twenty
 * to thirty seconds in hand — well under `HUNGRY_SECONDS` — and tops it up in
 * bursts, with pauses of several seconds between them in which the buffer
 * gains nothing. A rate taken sample by sample reads each pause as a link
 * delivering nought, and the first pause longer than `SUSTAINED_MS` converted
 * a title that was playing perfectly — straight to the floor, because the
 * suggested bitrate is the source's times that nought. Measured across a
 * window longer than a fetch cycle, the pauses and the bursts average out to
 * what the link actually carries; a slow link still shows, because it stays
 * slow across the whole window.
 */
const WINDOW_MS = 20_000;
const MIN_SPAN_MS = 6_000;

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
    /** Samples inside `WINDOW_MS`, oldest first, that the rate is taken across. */
    this.window = [];
    /** When the shortfall began, or `null` while it is keeping up. */
    this.shortfallSince = null;
    this.ratio = null;
  }

  /**
   * Nothing is being fetched on purpose — the buffer is full, or there is no
   * more — so neither a shortfall nor a window measured across it means
   * anything once fetching starts again.
   */
  forgetShortfall() {
    this.shortfallSince = null;
    this.window = [];
  }

  /**
   * One observation. Returns the verdict as of now.
   * An absent or non-finite duration disables the end-of-media check.
   *
   * @param {{now: number, currentTime: number, bufferedEnd: number,
   *          paused: boolean, duration?: number}} at
   * @returns {Verdict}
   */
  sample(at) {
    const bufferAhead = Math.max(0, at.bufferedEnd - at.currentTime);
    const previous = this.last;
    this.last = at;

    // A seek lands in a different buffer, and comparing across the jump would
    // read the discontinuity as a collapse.
    const played = previous ? at.currentTime - previous.currentTime : 0;
    if (played < 0 || played > SEEK_SECONDS) {
      this.reset();
      return { state: "ok", ratio: null, bufferAhead, measured: false };
    }
    // A paused player consumes nothing, so there is nothing to fall behind.
    // Note this is not the same as a *stalled* one, which is trying to play
    // and failing — that is the case this whole module is about.
    if (at.paused || previous === null || at.now <= previous.now) {
      // A window spanning a pause would count the pause as a link delivering
      // nothing, so measuring starts again when playback does.
      if (at.paused) this.window = [];
      return { state: "ok", ratio: this.ratio, bufferAhead, measured: false };
    }

    // Only while the player still wants more. Above the mark it has what it
    // asked for and has stopped fetching, which is not a shortfall.
    if (bufferAhead >= this.hungrySeconds) {
      this.forgetShortfall();
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
      this.forgetShortfall();
      return { state: "ok", ratio: this.ratio, bufferAhead, measured: false };
    }

    // Against the wall clock, not against playback. See the file comment:
    // dividing by seconds played reads a stalled player as a healthy one,
    // because a stalled player plays exactly as fast as it downloads. And
    // across a window rather than between two samples: see `WINDOW_MS`.
    this.window.push(at);
    while (at.now - this.window[0].now > WINDOW_MS) this.window.shift();
    const oldest = this.window[0];
    const span = at.now - oldest.now;
    if (span < MIN_SPAN_MS) {
      return { state: "ok", ratio: this.ratio, bufferAhead, measured: false };
    }
    this.ratio = (at.bufferedEnd - oldest.bufferedEnd) / (span / 1000);

    if (this.ratio >= KEEPING_UP) {
      this.shortfallSince = null;
      return { state: "ok", ratio: this.ratio, bufferAhead, measured: true };
    }

    if (this.shortfallSince === null) this.shortfallSince = at.now;
    // A shortfall has to last before it counts — unless the buffer is nearly
    // gone, when waiting out the rule would only mean waiting for the stall.
    // The window already stands between one bad second and a verdict.
    const starving = bufferAhead <= this.starvingSeconds;
    const sustained = at.now - this.shortfallSince >= this.sustainedMs;
    const urgent = starving && this.ratio < LOSING;
    // Timed from the first short window even above `BEHIND_SECONDS`, so a
    // shortfall that was already sustained is acted on the moment it gets
    // there rather than six seconds later.
    if (bufferAhead > BEHIND_SECONDS || (!sustained && !urgent)) {
      return { state: "ok", ratio: this.ratio, bufferAhead, measured: true };
    }

    return {
      state: starving ? "starving" : "behind",
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
