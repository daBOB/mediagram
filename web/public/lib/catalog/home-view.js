/**
 * The start page, laid out as a magazine's front section:
 *
 *   cover story → features → Continue beside a pull-quote →
 *   what arrived, beside "This month" → the rest of the library
 *
 * Draws what `home-shelves.js` and `editorial-picks.js` decided and nothing
 * else — no rule about what belongs on the page lives here. The poster rows
 * are the cards the shelves already draw, so a badge or a progress rule added
 * to `shelf-view.js` reaches this page without being remembered twice.
 *
 * A part with nothing in it is not drawn at all. A start page that says
 * "nothing here yet" four times is worse than the shelf it replaced.
 */

import { el } from "../dom.js";
import { collectionGrid, movieGrid } from "./shelf-view.js";
import { GRID, LIST } from "./shelf-mode.js";
import { coverStory } from "./home-cover.js";
import { featureStrip } from "./home-features.js";
import { resumeCards } from "./home-resume.js";
import { pullQuote, thisMonth } from "./home-break.js";
import { revealWithin } from "../reveal.js";

/**
 * A row's header: its name, and the way to the whole shelf.
 *
 * An `h2` and not the `h1` a shelf page uses. Five page titles down one page
 * is five pages, to a reader and to anything reading the outline aloud.
 * `total` is left off when the row holds more kinds than one shelf counts.
 */
function rowHead(title, total, hash, id) {
  const head = el("header", "row-head");
  const h2 = el("h2", null, title);
  h2.id = id;
  if (total !== null) h2.append(el("span", "count", ` ${total}`));
  head.append(h2);
  const link = el("a", "see-all", "See all");
  link.href = hash;
  link.setAttribute("aria-label", `See all: ${title}`);
  head.append(link);
  return head;
}

function section(className, key, title, total, hash, ...contents) {
  const node = el("section", `home-section ${className} reveal`);
  const id = `home-${key}`;
  node.setAttribute("aria-labelledby", id);
  node.append(rowHead(title, total, hash, id), ...contents);
  return node;
}

/**
 * Draws the page.
 *
 * @param {HTMLElement} main
 * @param {import("./home-shelves.js").HomeShelves} shelves
 * @param {ReturnType<typeof import("./editorial-picks.js").homeEditorial>} editorial
 * @param {{play: (set: import("../library.js").CatalogSet) => void,
 *   open: (kind: "series"|"tutorials", name: string) => void,
 *   openFilm: (set: import("../library.js").CatalogSet) => void}} on
 */
export function renderHome(main, shelves, editorial, { play, open, openFilm }) {
  // The page's one `h1`, for the outline; the cover's headline is a film's.
  main.append(el("h1", "sr-only", "Home"));
  if (editorial.cover.length > 0) {
    main.append(coverStory(editorial.cover, { play }));
    // Tells the stylesheet the masthead is over artwork, not paper.
    document.body.dataset.cover = "";
  }
  if (editorial.features.length > 0) main.append(featureStrip(editorial.features));

  // Resuming beside a line of type: the quiet tool, and the pause after the
  // features, share one band so neither fills a screen alone.
  const cards = resumeCards(shelves, play);
  const band = el("div", "home-band");
  if (cards.length > 0) {
    const strip = el("div", "resume-strip");
    strip.append(...cards);
    band.append(section("resume", "continue", "Continue watching", null, "#/continue", strip));
  }
  if (editorial.quote) {
    const quote = pullQuote(editorial.quote);
    quote.classList.add("reveal");
    band.append(quote);
  }
  if (band.childElementCount > 0) main.append(band);

  const library = el("div", "home-band home-library");
  if (shelves.latestMovies.length > 0) {
    library.append(section("latest", "latestMovies", "Recently added", shelves.totals.latestMovies, "#/movies",
      movieGrid(shelves.latestMovies, openFilm, { mode: GRID })));
  }
  if (editorial.thisMonth.length > 0) {
    const column = thisMonth(editorial.thisMonth);
    column.classList.add("reveal");
    library.append(column);
  }
  if (library.childElementCount > 0) main.append(library);

  if (shelves.latestSeries.length > 0) {
    main.append(section("series-row", "latestSeries", "Latest series", shelves.totals.latestSeries, "#/series",
      collectionGrid("series", shelves.latestSeries, (name) => open("series", name), { mode: GRID })));
  }
  if (shelves.latestCourses.length > 0) {
    // An index, not plates, for the reason the Tutorials shelf is one: a
    // course has no artwork, so a plate would be a poster-shaped blank.
    main.append(section("courses-row", "latestCourses", "Latest courses", shelves.totals.latestCourses, "#/tutorials",
      collectionGrid("tutorials", shelves.latestCourses, (name) => open("tutorials", name), { mode: LIST })));
  }
  revealWithin(main);
}
