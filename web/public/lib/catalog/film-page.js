/**
 * A film's own page: what it is, and a way to play it.
 *
 * Opened by a film's card, as the Android app's title page is. Laid out like
 * a series header — the artwork beside the facts rather than above them — so
 * the two kinds of title read alike, and styled by the same rules.
 *
 * What the provider says (score, genres, description) arrives separately and
 * is added by `describeFilm`, exactly as `describeSeries` adds it to a show:
 * the facts already on screen are not redrawn under the viewer.
 */

import { el } from "../dom.js";
import { clockTime, humanDuration, technicalLine } from "../format.js";
import { genreHash, genresOf, scoreLabel } from "./genres.js";
import { ageLabel } from "../age-rating.js";

/**
 * The page for `set`. `resume` is the second to carry on from, or `null`;
 * `onPlay` starts it — resuming is the player's job, as from any card.
 */
export function filmPage(set, { resume, onPlay }) {
  const header = el("header", "series-header film-page");

  if (set.poster) {
    const art = el("div", "series-art");
    const image = el("img");
    image.src = `/api/posters/${encodeURIComponent(set.poster)}.jpg`;
    // The title is the heading beside it; the artwork is decoration.
    image.alt = "";
    art.append(image);
    header.append(art);
  }

  const body = el("div", "series-facts");
  const facts = [set.year, humanDuration(set.duration), ageLabel(set), set.quality].filter(Boolean).join(" · ");
  if (facts) body.append(el("p", "series-scale", facts));
  body.append(el("p", "series-picture", technicalLine(set)));

  const links = genreLinks(genresOf(set));
  if (links) body.append(links);

  const play = el("button", "film-play", resume ? `Resume from ${clockTime(resume)}` : "Play");
  play.type = "button";
  play.addEventListener("click", () => onPlay(set));
  body.append(play);

  header.append(body);
  return header;
}

/** Adds the provider's score, tagline and description, once. */
export function describeFilm(header, meta) {
  if (!meta) return;
  const body = header.querySelector(".series-facts");
  if (!body || body.querySelector(".series-overview, .film-score")) return;

  const play = body.querySelector(".film-play");
  // Just above the genres, so the provider's view of the film sits together.
  const score = scoreLabel(meta.rating);
  if (score) (body.querySelector(".genre-links") ?? play)?.before(el("p", "film-score", score));
  for (const [text, className] of [
    [meta.tagline ?? null, "series-tagline"],
    [meta.overview ?? null, "series-overview"],
  ]) {
    // A long synopsis must not push the primary action off the phone screen.
    if (text) body.append(el("p", className, text));
  }
}

/**
 * Each genre as a link to its shelf, or `null` when there are none.
 *
 * Shared with the series header, so a genre is followed the same way from a
 * film and from a show.
 */
export function genreLinks(names) {
  if (names.length === 0) return null;
  const line = el("p", "genre-links");
  for (const name of names) {
    const link = el("a", "genre", name);
    link.href = genreHash(name);
    line.append(link);
  }
  return line;
}
