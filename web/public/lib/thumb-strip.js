/**
 * The frame you would land on, shown above the scrub bar.
 *
 * Called `peek`, not `thumb`: `.thumb` is already the poster plate on every
 * card and row, and the first version of this inherited its `display: grid`
 * and its hover transform — a preview laid out as a two-row grid, stretched,
 * with the time floating eighty pixels below the picture.
 *
 * One element, moved and re-pointed rather than rebuilt: a hover fires many
 * times a second, and building a node per event is how a smooth gesture turns
 * into a stuttering one.
 *
 * **No preview is not a broken preview.** A title whose sheet has not been
 * made — or cannot be, because it is not on this disk in full — leaves the bar
 * exactly as it was before any of this existed. That is the common case for a
 * library larger than its cache, so it has to be the quiet one.
 */

import { spritePlan, tileAt, TILE_HEIGHT, TILE_WIDTH } from "./sprite-plan.js";
import { el } from "./dom.js";
import { clockTime } from "./format.js";

/**
 * Attaches the preview to a scrub bar.
 *
 * `positionAt(fraction)` turns a place along the bar into a film position;
 * the player owns that because only it knows whether a title's clock is the
 * film's or an encode's.
 */
export function thumbStrip({ bar, slider, positionAt }) {
  const strip = el("div", "peek");
  strip.hidden = true;
  const frame = el("div", "peek-frame");
  const at = el("div", "peek-at");
  strip.append(frame, at);
  bar.append(strip);

  /** The open title's layout, or `null` when it has none to show. */
  let plan = null;
  /** Whether the sheet is known to exist. Asked once per title, not per hover. */
  let sheet = null;

  /**
   * Points at a title's sheet, and asks for one if it is not there.
   *
   * The request that comes back 404 is also what starts the sheet being made,
   * so a first viewing costs a wasted request and every later one gets
   * previews. Nothing waits for it.
   */
  async function open(set) {
    plan = null;
    sheet = null;
    strip.hidden = true;
    const runtime = Number(set?.duration ?? Number.NaN);
    if (!Number.isFinite(runtime) || runtime <= 0) return;

    const url = `/api/sets/${encodeURIComponent(set.setId)}/thumbs.jpg`;
    try {
      // `HEAD`, because the answer wanted is whether it exists — the image
      // itself arrives through the stylesheet when a tile is shown.
      const response = await fetch(url, { method: "HEAD" });
      if (!response.ok) return;
      plan = spritePlan(runtime);
      sheet = url;
    } catch {
      /* A player that cannot ask still scrubs. */
    }
  }

  /** Shows the frame for a place along the bar, or nothing if there is none. */
  function show(fraction) {
    if (plan === null || sheet === null) return;
    const seconds = positionAt(fraction);
    const tile = tileAt(plan, seconds);
    if (tile === null) return;

    frame.style.backgroundImage = `url("${sheet}")`;
    // The sheet is one image and the tile is a window onto it, so the whole
    // sheet is fetched once by the browser and every later hover is free.
    frame.style.backgroundPosition = `-${tile.x}px -${tile.y}px`;
    at.textContent = clockTime(seconds);

    // Held inside the bar, so a preview near either end does not hang off the
    // side of the picture.
    const width = bar.clientWidth;
    const half = TILE_WIDTH / 2;
    const left = Math.min(width - half, Math.max(half, fraction * width));
    strip.style.left = `${left}px`;
    strip.hidden = false;
  }

  function hide() {
    strip.hidden = true;
  }

  /** Where along the bar a pointer is, as nought to one. */
  const fractionOf = (event) => {
    const box = slider.getBoundingClientRect();
    if (box.width === 0) return 0;
    return Math.min(1, Math.max(0, (event.clientX - box.left) / box.width));
  };

  slider.addEventListener("pointermove", (event) => show(fractionOf(event)));
  slider.addEventListener("pointerleave", hide);
  // A touch drag is a scrub, and the preview is most useful during one.
  slider.addEventListener("pointerdown", (event) => show(fractionOf(event)));
  slider.addEventListener("pointerup", hide);

  return { open, hide, tileSize: { width: TILE_WIDTH, height: TILE_HEIGHT } };
}
