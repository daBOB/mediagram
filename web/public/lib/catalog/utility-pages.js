/**
 * The rail's utility pages: Latest (everything by arrival), Genres (an index
 * of the library's departments by subject) and one genre's shelf.
 *
 * Reference pages rather than editorial ones, so they are plain shelves; the
 * departments (Movies, Series) are where the library is presented.
 */

import { el } from "../dom.js";
import { countOf } from "../format.js";
import { firstItemOf } from "../library.js";
import { collectionGrid, heading, movieGrid, SECTIONS } from "./shelf-view.js";
import { GRID, LIST } from "./shelf-mode.js";
import { genreHash, genreShelf, genresOf } from "./genres.js";
import { homeShelves } from "./home-shelves.js";
import { artworkUrl } from "./plate.js";

const LATEST_LIMIT = 48;

/** @typedef {{ openFilm: (set: any) => void, openShow: (section: string, name: string) => void }} Openers */

/** Films, shows and courses, newest arrival first. @param {Openers} on */
export function renderLatest(main, library, byId, { openFilm, openShow }) {
  const rows = homeShelves({ library, byId, posterLimit: LATEST_LIMIT, limit: LATEST_LIMIT });
  heading(main, "Latest", "Newest arrivals first");
  const part = (title, node) => main.append(el("h2", "shelf-sub", title), node);
  if (rows.latestMovies.length > 0) part(SECTIONS.movies.label, movieGrid(rows.latestMovies, openFilm, { mode: GRID }));
  if (rows.latestSeries.length > 0) {
    part(SECTIONS.series.label, collectionGrid("series", rows.latestSeries, (name) => openShow("series", name), { mode: GRID }));
  }
  if (rows.latestCourses.length > 0) {
    part(SECTIONS.tutorials.label, collectionGrid("tutorials", rows.latestCourses, (name) => openShow("tutorials", name), { mode: LIST }));
  }
}

/**
 * Every genre the library holds, most titles first, each with the backdrop of
 * its most popular title that a bigger genre has not already used — a page of
 * tiles showing the same film three times reads as a mistake. Pure, so the
 * Movies page's genre row can share it.
 *
 * @returns {{ name: string, count: number, art: string|null }[]}
 */
export function genreIndex(titles) {
  const byName = new Map();
  for (const title of titles) {
    for (const name of genresOf(title)) {
      if (!byName.has(name)) byName.set(name, []);
      byName.get(name).push(title);
    }
  }
  const used = new Set();
  return [...byName.entries()]
    .sort((a, b) => b[1].length - a[1].length || a[0].localeCompare(b[0]))
    .map(([name, members]) => {
      const pictured = members
        .filter((title) => title.backdrop || title.poster)
        .sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0));
      const lead = pictured.find((title) => !used.has(title.setId)) ?? pictured[0] ?? null;
      if (lead) used.add(lead.setId);
      return { name, count: members.length, art: lead?.backdrop ?? lead?.poster ?? null };
    });
}

/** The whole library's titles: films, and each show by its first episode. */
export function titlesOf(library) {
  return [...library.movies, ...library.series.map((show) => firstItemOf(show.divisions)).filter(Boolean)];
}

/** Genres as image tiles, each a link to its shelf. */
export function genreTiles(genres) {
  const grid = el("div", "genre-tiles");
  for (const { name, count, art } of genres) {
    const tile = el("a", "genre-tile");
    tile.href = genreHash(name);
    if (art) {
      const image = el("img");
      image.src = artworkUrl(art);
      image.alt = "";
      image.loading = "lazy";
      image.decoding = "async";
      tile.append(image);
    }
    tile.append(el("span", "genre-tile-name", name), el("span", "genre-tile-count", countOf(count, "title")));
    grid.append(tile);
  }
  return grid;
}

export function renderGenres(main, library) {
  const genres = genreIndex(titlesOf(library));
  heading(main, "Genres", countOf(genres.length, "genre"));
  if (genres.length === 0) return main.append(el("p", "empty", "Nothing in the library has a genre recorded."));
  main.append(genreTiles(genres));
}

/** Everything tagged with one genre: films first, then series. @param {Openers} on */
export function renderGenre(main, library, name, { openFilm, openShow }) {
  const { films, series } = genreShelf(library, name);
  heading(main, name, countOf(films.length + series.length, "title"));
  if (films.length + series.length === 0) {
    main.append(el("p", "empty", "Nothing in the library is tagged with this genre."));
    return;
  }
  // Labelled only when both are there; a shelf of one kind says what it is.
  const both = films.length > 0 && series.length > 0;
  if (films.length > 0) {
    if (both) main.append(el("h2", "shelf-sub", SECTIONS.movies.label));
    main.append(movieGrid(films, openFilm, { mode: GRID }));
  }
  if (series.length > 0) {
    if (both) main.append(el("h2", "shelf-sub", SECTIONS.series.label));
    main.append(collectionGrid("series", series, (title) => openShow("series", title), { mode: GRID }));
  }
}
