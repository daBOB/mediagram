/**
 * Redrawing the page already showing without making it flicker.
 *
 * The router rebuilds `main` from scratch, which is simple and always right —
 * but when it runs again for the same address (another device's watch state
 * arrived, a pin, a catalog refresh), rebuilding throws away every decoded
 * image and replays every entrance animation, and the page blinks at a viewer
 * who did nothing. So a redraw keeps the images already loaded, handing each
 * to the new node that shows the same picture, and marks the page `settled`
 * so the stylesheet skips entrance animations. Arriving at a page is not a
 * redraw, and animates as before.
 */

let drawnAddress = null;

/** Draws through `draw()`; if `address` is the one already showing, as a redraw. */
export function drawAt(main, address, draw) {
  const redraw = address === drawnAddress;
  drawnAddress = address;
  document.documentElement.classList.toggle("settled", redraw);
  const kept = redraw ? loadedImages(main) : null;
  draw();
  if (kept) adoptImages(main, kept);
}

/** The loaded images under `root`, by the address they show. */
function loadedImages(root) {
  const bySrc = new Map();
  if (typeof root.querySelectorAll !== "function") return bySrc;
  for (const image of root.querySelectorAll("img")) {
    if (!image.complete || image.naturalWidth === 0) continue;
    const src = image.getAttribute("src");
    bySrc.set(src, [...(bySrc.get(src) ?? []), image]);
  }
  return bySrc;
}

/** Swaps each new image for a kept one showing the same picture. */
function adoptImages(root, kept) {
  if (kept.size === 0 || typeof root.querySelectorAll !== "function") return;
  for (const image of root.querySelectorAll("img")) {
    const old = kept.get(image.getAttribute("src"))?.pop();
    if (!old) continue;
    // The new node's attributes are the current truth; the old one keeps only
    // what it already has, the decoded picture.
    old.className = image.className;
    old.alt = image.alt;
    image.replaceWith(old);
  }
}
