/**
 * The block above a show's seasons: what it is, in one look.
 *
 * Separate from `course-view`, which is about containers that nest. This is
 * flat by nature — a show's facts, not its shape — and the two would only
 * blur each other.
 *
 * Every row is omitted when it has nothing to say. A header of empty labels
 * would claim the library looked and found nothing, when the truth is that
 * nobody recorded it.
 */

import { el } from "../dom.js";
import {
  detailRows,
  pictureLine,
  provenance,
  scaleLine,
  summarize,
  yearLine,
} from "./series-summary.js";
import { genreLinks } from "./film-page.js";
import { ageLabel } from "../age-rating.js";
import { firstItemOf } from "../library.js";

/**
 * @param {object} collection a grouped show, as `groupLibrary` builds one
 * @param {string|null} poster the key its episodes share, if any
 * @param {object|null} meta what the provider says, when the index has it
 */
export function seriesHeader(collection, poster, meta = null) {
  const facts = summarize(collection);
  const header = el("header", "series-header");

  if (poster) {
    const art = el("div", "series-art");
    const image = el("img");
    image.src = `/api/posters/${encodeURIComponent(poster)}.jpg`;
    // The name is already the heading beside it, so the artwork is
    // decoration: announcing it again only makes a screen reader repeat.
    image.alt = "";
    art.append(image);
    header.append(art);
  }

  // What to print is decided in `series-summary`, which has no DOM and can be
  // tested; this places what that returns and nothing more.
  const body = el("div", "series-facts");
  const line = (text, className) => {
    if (text) body.append(el("p", className, text));
  };
  line(scaleLine(facts, meta), "series-scale");
  // A show is rated once, so any episode answers for all of them.
  line(ageLabel(firstItemOf(collection.divisions)), "series-age");
  line(yearLine(facts, meta), "series-years");
  line(pictureLine(facts), "series-picture");

  const rows = detailRows(facts);
  if (rows.length > 0) {
    const list = el("dl", "series-details");
    for (const { label, value } of rows) {
      const row = el("div", "detail");
      row.append(el("dt", null, label), el("dd", null, value));
      list.append(row);
    }
    body.append(list);
  }

  // What the provider says goes last: the facts above are about this copy of
  // the show, and are true whether or not anyone has described it.
  header.append(body);
  describeSeries(header, meta);
  return header;
}

/**
 * Adds what the provider says to a header already on screen.
 *
 * Separate from building the header because the two arrive at different
 * times: the facts come from the catalog the page already has, the
 * description from a request. Rebuilding the header when it lands would
 * recount every episode and swap the poster element under the viewer.
 *
 * @param {HTMLElement} header a header from `seriesHeader`
 * @param {object|null} meta what the provider says, or nothing
 */
export function describeSeries(header, meta) {
  if (!meta) return;
  const body = header.querySelector(".series-facts");
  if (!body) return;
  // Called once when the description arrives; a second call would repeat it.
  if (body.querySelector(".series-overview, .series-provenance, .series-tagline")) return;

  for (const [text, className] of [
    [meta.tagline ?? null, "series-tagline"],
    // The genres leave this line to become links of their own, below.
    [provenance({ ...meta, genres: null }), "series-provenance"],
  ]) {
    if (text) body.append(el("p", className, text));
  }
  const links = genreLinks(splitGenres(meta.genres));
  if (links) body.append(links);
  if (meta.overview) body.append(el("p", "series-overview", meta.overview));
}

/** The provider's comma-separated genres, as names. */
function splitGenres(genres) {
  return String(genres ?? "")
    .split(",")
    .map((name) => name.trim())
    .filter((name) => name !== "");
}
