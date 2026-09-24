/**
 * Everything the scrub bar needs to know, worked out away from the DOM.
 *
 * The player has two ways of playing a title and they disagree about what
 * time it is. A file played directly has a `currentTime` that is the film's
 * time. A converted one is encoded as it plays, so its `currentTime` counts
 * from wherever that encode began and its `duration` is only as long as
 * ffmpeg has written so far — both true about the conversion and both untrue
 * about the film.
 *
 * So the bar is never given the media element's numbers. It is given the
 * film's runtime from the catalog and the film's position, and this decides
 * the rest. Pure, because the alternative is asserting on `TimeRanges` and a
 * live `<video>`, which `preload-readout.js` and `up-next.js` also declined
 * to do for the same reason.
 */

import { clockTime } from "../format.js";

/**
 * The bar's whole state, from the four facts that determine it.
 *
 * Described rather than positional: `seekModel(1204, 359, 61, false)` says
 * nothing at its call site, and three of the four are seconds.
 *
 * `runtime` absent or nought means nothing knows how long the title is —
 * a live stream, or a set the catalog has no duration for. There is no bar to
 * draw in that case and `usable` says so, rather than a bar scaled to a
 * length someone guessed.
 *
 * @param {{runtime?: number|null, at?: number|null, ahead?: number|null,
 *          converting?: boolean, held?: boolean}} facts
 *
 * `held` is a title on the server's disk in full. The browser still only
 * buffers a minute or so ahead of itself, but nothing past that is waiting on
 * the network, so the whole track is painted.
 */
export function seekModel(facts = {}) {
  // `Number(null)` is 0, and 0 is a real position — the start of the film.
  // Absent and nought are separated here rather than left to coercion, which
  // cannot tell them apart and has been the bug in this file three times.
  const runtime = Number(facts.runtime ?? Number.NaN);
  if (!Number.isFinite(runtime) || runtime <= 0) {
    return {
      usable: false,
      max: 0,
      value: 0,
      elapsed: "",
      total: "",
      label: "",
      played: 0,
      buffered: 0,
      seeksWhileDragging: false,
    };
  }

  const at = clamp(Number(facts.at ?? Number.NaN), runtime);
  const aheadSeconds = Number(facts.ahead ?? Number.NaN);
  const ahead = Number.isFinite(aheadSeconds) && aheadSeconds > 0 ? aheadSeconds : 0;

  return {
    usable: true,
    max: Math.floor(runtime),
    value: Math.floor(at),
    // Given apart as well as together: the bar prints them at either end of
    // the transport, the way the phone does, and reads them out as one line
    // to anyone who cannot see where they were printed.
    elapsed: clockTime(at),
    total: clockTime(runtime),
    label: `${clockTime(at)} / ${clockTime(runtime)}`,
    played: at / runtime,
    // What is played is also buffered; the paint is one band from nought, not
    // a band floating beyond the thumb.
    buffered:
      facts.held === true ? 1 : clamp(at + ahead, runtime) / runtime,
    /**
     * Whether dragging should move the picture, or only the readout.
     *
     * Free on a file the server can seek within. Ruinous on a conversion,
     * where landing somewhere else means starting ffmpeg again there — every
     * pixel of a drag would start an encode and finish none. So a converting
     * title moves when the viewer lets go, and not before.
     */
    seeksWhileDragging: facts.converting !== true,
  };
}

/** Into the film, from nought to its last second. */
function clamp(seconds, runtime) {
  if (!Number.isFinite(seconds) || seconds < 0) return 0;
  return Math.min(seconds, runtime);
}

/**
 * Where a skip lands, given where it started.
 *
 * Its own function because the buttons and the keyboard both do this, and
 * because the clamping is the whole of it: a skip back from four seconds in
 * goes to nought rather than to minus six, and a skip forward near the end
 * stops at the end rather than past it, where a conversion would be asked to
 * encode from beyond the file.
 */
export function skipTo(at, by, runtime) {
  const from = Number(at ?? Number.NaN);
  const length = Number(runtime ?? Number.NaN);
  const start = Number.isFinite(from) && from > 0 ? from : 0;
  const target = start + Number(by ?? 0);
  if (!Number.isFinite(target)) return start;
  if (target < 0) return 0;
  if (Number.isFinite(length) && length > 0) return Math.min(target, length);
  return target;
}
