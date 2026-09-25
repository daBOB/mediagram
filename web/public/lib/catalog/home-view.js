/**
 * The start page: what is underway, and what arrived.
 *
 * Draws the rows `home-shelves.js` decided on and nothing else — no rule
 * about what belongs on a row lives here. Every card is one the shelves
 * already draw, so a badge or a progress rule added to `shelf-view.js`
 * reaches this page without being remembered twice.
 *
 * A row with nothing in it is not drawn at all. A start page that says
 * "nothing here yet" four times is worse than the shelf it replaced.
 */

import { el } from "../dom.js";
import { resumeLine } from "../format.js";
import { collectionGrid, movieGrid, setGrid } from "./shelf-view.js";
import { GRID, LIST } from "./shelf-mode.js";
import { progressOf } from "../watch-state.js";

/**
 * A row's header: its name, and the way to the whole shelf.
 *
 * An `h2` and not the `h1` a shelf page uses. Five page titles down one page
 * is five pages, to a reader and to anything reading the outline aloud; the
 * page's own title is the hidden one below.
 */
function rowHead(title, total, hash) {
  const head = el("header", "row-head");
  const h2 = el("h2", null, title);
  // The same " · n" a course's folder heading carries, and for the same
  // reason: the name says what, the figure says how much.
  h2.append(el("span", "count", ` \u00b7 ${total}`));
  head.append(h2);
  const link = el("a", "see-all", "See all");
  link.href = hash;
  head.append(link);
  return head;
}

/**
 * Draws the page.
 *
 * @param {HTMLElement} main
 * @param {import("./home-shelves.js").HomeShelves} shelves
 * @param {{play: (set: import("../library.js").CatalogSet) => void,
 *   open: (kind: "series"|"tutorials", name: string) => void,
 *   openFilm?: (set: import("../library.js").CatalogSet) => void}} on
 */
export function renderHome(main, shelves, { play, open, openFilm }) {
  // Every page needs one, and this one has no visible title: the rows name
  // themselves and a heading saying "Home" above the word "mediagram" would
  // be saying it twice.
  main.append(el("h1", "page-title", "Home"));

  const row = (key, title, hash, contents) => {
    main.append(rowHead(title, shelves.totals[key], hash), contents);
  };

  if (shelves.continues.length > 0) {
    row("continues", "Continue", "#/continue", setGrid(shelves.continues, play, { mode: GRID }));
  }

  if (shelves.nextUp.length > 0) {
    // The captions differ within the row, which is the whole point of it:
    // one card is where the viewer stopped, the next is what follows an
    // episode they finished, and a row that read the same for both would
    // not be worth having.
    const resume = new Map(shelves.nextUp.map((entry) => [entry.set.setId, entry.resume]));
    row(
      "nextUp",
      "Next up",
      "#/series",
      setGrid(
        shelves.nextUp.map((entry) => entry.set),
        play,
        {
          mode: GRID,
          caption: (set) =>
            resume.get(set.setId) ? resumeLine(progressOf(set.setId)) : "Next up",
        },
      ),
    );
  }

  if (shelves.latestMovies.length > 0) {
    row("latestMovies", "Latest films", "#/movies", movieGrid(shelves.latestMovies, openFilm ?? play, { mode: GRID }));
  }

  if (shelves.latestSeries.length > 0) {
    row(
      "latestSeries",
      "Latest series",
      "#/series",
      collectionGrid("series", shelves.latestSeries, (name) => open("series", name), { mode: GRID }),
    );
  }

  if (shelves.latestCourses.length > 0) {
    // An index, not plates, for the reason the Tutorials shelf is one: a
    // course has no artwork — `posterKeyFor` files everything under a TMDB
    // id and a course has none — so a plate is a poster-shaped blank with
    // an initial in it. Six of those is worse than a row that reads.
    row(
      "latestCourses",
      "Latest courses",
      "#/tutorials",
      collectionGrid("tutorials", shelves.latestCourses, (name) => open("tutorials", name), { mode: LIST }),
    );
  }
}
