/**
 * Making a subtitle legible, and making it on time.
 *
 * 206 of this library's sets carry subtitles and the only thing a viewer could
 * do with them was turn them off. Too small across a room, a white line lost
 * against a snowy shot, or half a second early for two hours — none of it was
 * fixable, and the last one makes a film unwatchable.
 *
 * The three split in a way worth knowing about, because it decides where each
 * one is implemented:
 *
 * - **Size and backing are CSS.** `::cue` styles what the browser already
 *   renders, so they cost a stylesheet rule and nothing else.
 * - **Sync is not.** `::cue` cannot move a cue along the clock; that lives on
 *   the cue objects, as `startTime`/`endTime`, and has to be written onto
 *   each one.
 *
 * Which is why an offset is held as an **absolute** and applied against the
 * times a cue was parsed with. Nudging the current times by a delta drifts:
 * ask for −0.4s twice and a cue that should have moved 0.4s has moved 0.8s,
 * and there is no way back to where it started.
 *
 * **There is no position control, and the attempt is worth recording.** Moving
 * subtitles clear of the transport bar needs the cue box's *bottom* anchored,
 * which in WebVTT means `snapToLines: false` and a percentage `line`. Chromium
 * then stops wrapping the text: a two-hundred-character cue renders as one
 * line off the right edge of the picture, whatever `size`, `position` and
 * `align` are set to. With `snapToLines: true` it wraps, but a negative line
 * anchors the cue's *first* row and the rest grow downward, so a tall cue
 * covers the bar regardless. Neither does what the control would have claimed,
 * so the browser's own placement is left alone — it puts cues at the bottom,
 * grows them upward, and wraps. The bar rests after a few seconds anyway.
 */

/**
 * How far a cue may be scaled, as a percentage of the browser's own size.
 *
 * The browser's own is already sized for the video, and generously: at 140%
 * a long German line ran to four rows and covered the bottom half of the
 * picture along with the whole transport bar. The ceiling is what a viewer
 * across a room can ask for without the subtitle becoming the programme.
 */
const SMALLEST = 50;
const LARGEST = 200;

/**
 * The stylesheet rule for a set of choices.
 *
 * `background` is always stated. A browser's default `::cue` carries a black
 * box, so "none" has to say so explicitly — leaving it out does not remove
 * the box, it keeps it.
 */
export function cueStyle({ size = 100, backing = "shadow" } = {}) {
  const scale = clamp(Number(size ?? Number.NaN), SMALLEST, LARGEST, 100);
  const parts = [`font-size: ${scale}%`];

  parts.push(backing === "box" ? "background: rgba(0, 0, 0, 0.75)" : "background: transparent");
  if (backing === "shadow") {
    // Two shadows: a soft drop for depth and a tight one that holds the
    // letterform together against a bright shot, which a single blur does not.
    parts.push("text-shadow: 0 2px 4px rgba(0, 0, 0, 0.95), 0 0 2px rgba(0, 0, 0, 0.9)");
  } else {
    parts.push("text-shadow: none");
  }

  return `video::cue { ${parts.join("; ")}; }`;
}

/**
 * Where a cue sits once `offset` is applied to the times it was parsed with.
 *
 * Both ends are clamped at nought, because a cue cannot start before the film
 * does. That can squash a cue near the beginning — an offset of −5s applied
 * to one at 2s leaves it starting at 0 — and squashed is the honest outcome:
 * the alternative is a cue shown at a time it was never meant for.
 */
export function shiftedTimes(original, offset) {
  const by = Number(offset ?? Number.NaN);
  const moved = Number.isFinite(by) ? by : 0;
  const start = Math.max(0, original.start + moved);
  // Never before its own start, however far back the offset reaches.
  const end = Math.max(start, original.end + moved);
  return { start, end };
}

/**
 * The times a cue was parsed with, kept so an offset is never applied twice.
 *
 * A `WeakMap` rather than a property on the cue: these are the browser's
 * objects, they go away with the track, and nothing here should be what keeps
 * one alive.
 */
const asParsed = new WeakMap();

/**
 * Puts `track`'s cues where the viewer asked for them.
 *
 * Safe to call as often as it is convenient, and it needs to be: cues do not
 * exist until the file has been fetched and parsed, which only happens once a
 * track stops being `disabled`, and a conversion re-attaches every track from
 * scratch. Calling it twice with the same offset is not calling it twice.
 *
 * Returns how many cues it actually moved, which is what a test can assert on
 * without a media element.
 */
export function placeCues(track, { offset = 0 } = {}) {
  const cues = track?.cues;
  if (!cues || cues.length === 0) return 0;

  let moved = 0;
  for (const cue of cues) {
    let original = asParsed.get(cue);
    if (original === undefined) {
      original = { start: cue.startTime, end: cue.endTime };
      asParsed.set(cue, original);
    }

    const { start, end } = shiftedTimes(original, offset);
    // `line` and friends are deliberately untouched — see the header.
    if (cue.startTime !== start || cue.endTime !== end) {
      cue.startTime = start;
      cue.endTime = end;
      moved += 1;
    }
  }
  return moved;
}

function clamp(value, low, high, fallback) {
  if (!Number.isFinite(value)) return fallback;
  return Math.min(high, Math.max(low, value));
}
