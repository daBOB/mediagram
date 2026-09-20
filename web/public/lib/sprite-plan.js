/**
 * How a title's preview frames are laid out in one image.
 *
 * Shared by the two ends on purpose. The server uses it to tell ffmpeg what to
 * make; the browser uses it to work out which tile to show for a position. If
 * they disagreed by one the previews would be subtly wrong — a frame from the
 * scene before, everywhere, which looks like a broken player rather than a
 * rounding error.
 *
 * Deterministic from the runtime alone, so the browser needs no extra request
 * to learn the layout. That matters more than it sounds: a hover fires many
 * times a second, and a design that had to ask the server what shape the sheet
 * was would either block the first preview or need a second round trip before
 * the bar could respond at all.
 */

/** Frames to aim for across the whole title. */
const TARGET_TILES = 120;

/** The coarsest and finest a preview interval may be, in seconds. */
const CLOSEST = 2;
const FURTHEST = 60;

/** One tile. Small enough that a whole sheet is a few hundred kilobytes. */
export const TILE_WIDTH = 160;
export const TILE_HEIGHT = 90;

/** How many tiles to a row. Ten keeps a sheet roughly as wide as it is tall. */
export const COLUMNS = 10;

/**
 * The layout for a title of `duration` seconds, or `null` when there is none.
 *
 * `null` for a runtime nobody knows: a sheet needs to be divided into tiles
 * before it can be made, and a length that is a guess would put every preview
 * in the wrong place rather than merely in an imprecise one.
 */
export function spritePlan(duration) {
  // `?? NaN` before the coercion: `Number(null)` is 0, and a zero-length title
  // would otherwise ask for a division by nothing.
  const runtime = Number(duration ?? Number.NaN);
  if (!Number.isFinite(runtime) || runtime <= 0) return null;

  const interval = Math.min(FURTHEST, Math.max(CLOSEST, Math.ceil(runtime / TARGET_TILES)));
  // The last tile is the one containing the final second, so this counts the
  // intervals a position can land in rather than the boundaries between them.
  const tiles = Math.max(1, Math.ceil(runtime / interval));
  return {
    interval,
    tiles,
    columns: Math.min(COLUMNS, tiles),
    rows: Math.ceil(tiles / COLUMNS),
    tileWidth: TILE_WIDTH,
    tileHeight: TILE_HEIGHT,
  };
}

/**
 * Where in the sheet the frame for `at` sits, as pixels to offset by.
 *
 * Returned as a background position rather than a tile index, because that is
 * what the one caller needs and working it out here keeps the arithmetic in
 * the file that is tested.
 */
export function tileAt(plan, at) {
  if (plan === null) return null;
  const seconds = Number(at ?? Number.NaN);
  const safe = Number.isFinite(seconds) && seconds > 0 ? seconds : 0;
  // Clamped to the last tile: a position at the very end of a film divides to
  // exactly `tiles`, which is one past the end of a zero-based grid.
  const index = Math.min(plan.tiles - 1, Math.floor(safe / plan.interval));
  return {
    index,
    x: (index % COLUMNS) * plan.tileWidth,
    y: Math.floor(index / COLUMNS) * plan.tileHeight,
  };
}
