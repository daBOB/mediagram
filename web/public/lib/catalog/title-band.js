/**
 * A title page's backdrop, as the band across the top a magazine gives a
 * feature: the film's own wide artwork, fading into the page so the heading
 * and facts below can sit over its lower edge.
 *
 * Decoration only — the heading beside it names the title — so it is hidden
 * from a screen reader and takes no pointer events.
 */

import { el } from "../dom.js";
import { artworkUrl } from "./plate.js";

/** The band for `set`, or `null` when it has no backdrop to show. */
export function titleBand(set) {
  if (!set?.backdrop) return null;
  const band = el("div", "title-band");
  band.setAttribute("aria-hidden", "true");
  const image = el("img");
  image.src = artworkUrl(set.backdrop);
  image.alt = "";
  image.decoding = "async";
  image.setAttribute("fetchpriority", "high");
  band.append(image);
  return band;
}
