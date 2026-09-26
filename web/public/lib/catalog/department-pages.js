/**
 * The three departments — Movies, Series, Tutorials — each opened like a
 * magazine section rather than as a wall of posters: a hero, then a few
 * curated rows, then the way into the whole shelf.
 *
 * Movies and Series share the hero and the row, and differ in what the rows
 * are: films are browsed by what is popular, acclaimed and new and by genre;
 * shows by what is underway and what is new. The whole film shelf is paged
 * at `#/movies/page/N`; shows and courses are few enough to list in full at
 * the foot of their page.
 */

import { el } from "../dom.js";
import { countOf } from "../format.js";
import { firstItemOf, flattenCollection } from "../library.js";
import { collectionGrid, emptyState, movieGrid, SECTIONS } from "./shelf-view.js";
import { GRID, LIST } from "./shelf-mode.js";
import { departmentHero, deptRow } from "./department-hero.js";
import { genreIndex, genreTiles } from "./utility-pages.js";
import { homeShelves } from "./home-shelves.js";
import { resumeCards } from "./home-resume.js";
import { revealWithin } from "../reveal.js";

const ROW = 12;
const ACCLAIMED = 7.5;
const popular = (a, b) => (b.popularity ?? 0) - (a.popularity ?? 0);

/**
 * @typedef {{ library: any, byId: Map<string, any>, progress: any[], watchedAt: (id: string) => number|null,
 *   isWatched: (id: string) => boolean, kids: boolean, play: (set: any) => void,
 *   openFilm: (set: any) => void, openShow: (section: string, name: string) => void,
 *   reel: HTMLElement|null }} Context
 */

/** @param {HTMLElement} main @param {Context} cx */
export function renderMoviesDept(main, cx) {
  const films = cx.library.movies;
  if (films.length === 0) return main.append(emptyState("movies", { kids: cx.kids }));
  const unwatched = films.filter((set) => !cx.isWatched(set.setId));
  const byPopularity = [...unwatched].sort(popular);
  const lead = byPopularity.find((set) => set.backdrop) ?? null;
  const hours = Math.round(films.reduce((sum, set) => sum + (Number(set.duration) || 0), 0) / 3600);

  main.append(departmentHero({
    kicker: "Only in your library",
    title: SECTIONS.movies.label,
    line: [countOf(films.length, "film"), hours > 0 ? `${hours.toLocaleString()} hours` : null].filter(Boolean).join(" · "),
    lead,
    leadName: lead?.title ?? null,
    leadHref: lead ? `#/film/${encodeURIComponent(lead.setId)}` : null,
  }));

  const shelf = (sets) => movieGrid(sets, cx.openFilm, { mode: GRID, strip: true });
  const all = { href: "#/movies/page/1", label: `All ${films.length} films` };
  const featured = byPopularity.filter((set) => set !== lead).slice(0, ROW);
  if (featured.length > 0) {
    main.append(deptRow("Featured Movies", shelf(featured), all, cx.reel));
  }
  const genres = genreIndex(films).slice(0, 12);
  if (genres.length > 0) main.append(deptRow("Genres", genreTiles(genres), { href: "#/genres", label: "Every genre" }));

  const acclaimed = unwatched.filter((set) => (set.rating ?? 0) >= ACCLAIMED)
    .sort((a, b) => b.rating - a.rating).slice(0, ROW);
  if (acclaimed.length > 0) main.append(deptRow("Acclaimed, not yet seen", shelf(acclaimed)));

  const latest = homeShelves({ library: cx.library, byId: cx.byId, posterLimit: ROW }).latestMovies;
  if (latest.length > 0) main.append(deptRow("Recently added", shelf(latest), { href: "#/latest", label: "Latest" }));

  main.append(allLink(all));
  revealWithin(main);
}

/**
 * Documentaries: no popularity or genre to browse by, since none of these
 * came from TMDB — a hero, what is underway, what arrived, then a row for
 * every collected folder and one more for whatever was uploaded on its own.
 * @param {HTMLElement} main @param {Context} cx
 */
export function renderDocumentariesDept(main, cx) {
  const { collections: groups, singles } = cx.library.documentaries;
  const items = [...singles, ...groups.flatMap((group) => flattenCollection(group))];
  if (items.length === 0) return main.append(emptyState("documentaries", { kids: cx.kids }));

  const byRecent = [...items].sort((a, b) => (Number(b.addedAt) || 0) - (Number(a.addedAt) || 0));
  const lead = byRecent.find((set) => set.backdrop) ?? null;

  main.append(departmentHero({
    kicker: "Only in your library",
    title: SECTIONS.documentaries.label,
    line: countOf(items.length, SECTIONS.documentaries.extent),
    lead,
    leadName: lead?.title ?? null,
    leadHref: null,
  }));

  const shelf = (sets) => movieGrid(sets, cx.play, { mode: GRID, strip: true });
  const shelves = homeShelves({ library: cx.library, byId: cx.byId, progress: cx.progress, watchedAt: cx.watchedAt });
  const underway = resumeCards(
    { continues: shelves.continues.filter((set) => set.kind === "docu"), nextUp: [] },
    cx.play,
  );
  if (underway.length > 0) {
    const strip = el("div", "resume-strip");
    strip.append(...underway);
    main.append(deptRow("Continue watching", strip));
  }

  if (byRecent.length > 0) main.append(deptRow("Recently added", shelf(byRecent.slice(0, ROW))));

  for (const group of groups) {
    const groupItems = flattenCollection(group);
    if (groupItems.length === 0) continue;
    const more = groupItems.length > ROW
      ? { href: `#/documentaries/${encodeURIComponent(group.name)}`, label: `All ${groupItems.length}` }
      : null;
    main.append(deptRow(group.name, shelf(groupItems.slice(0, ROW)), more));
  }

  // ponytail: caps a single row rather than paging it — add a "singles" page
  // if a library ever holds more standalone documentaries than one row shows.
  if (singles.length > 0) main.append(deptRow("Standalone documentaries", shelf(singles.slice(0, ROW))));
  revealWithin(main);
}

/** @param {HTMLElement} main @param {"series"|"tutorials"} section @param {Context} cx */
export function renderShowsDept(main, section, cx) {
  const shows = cx.library[section];
  if (shows.length === 0) return main.append(emptyState(section, { kids: cx.kids }));
  const series = section === "series";
  const leads = shows.map((show) => firstItemOf(show.divisions)).filter(Boolean);
  const lead = [...leads].filter((set) => set.backdrop).sort(popular)[0] ?? null;
  const items = shows.reduce((sum, show) => sum + show.count, 0);

  main.append(departmentHero({
    kicker: "Only in your library",
    title: SECTIONS[section].label,
    line: [countOf(shows.length, SECTIONS[section].extent), countOf(items, series ? "episode" : "lesson")].join(" · "),
    lead,
    leadName: lead?.show ?? null,
    leadHref: lead?.show ? `#/${section}/${encodeURIComponent(lead.show)}` : null,
  }));

  const kind = series ? "ep" : "tut";
  const shelves = homeShelves({ library: cx.library, byId: cx.byId, progress: cx.progress, watchedAt: cx.watchedAt });
  const underway = resumeCards({
    continues: shelves.continues.filter((set) => set.kind === kind),
    nextUp: shelves.nextUp.filter((entry) => entry.set.kind === kind),
  }, cx.play);
  if (underway.length > 0) {
    const strip = el("div", "resume-strip");
    strip.append(...underway);
    main.append(deptRow(series ? "Continue your series" : "Continue your courses", strip));
  }

  const open = (name) => cx.openShow(section, name);
  const mode = series ? GRID : LIST;
  // Rows only once there are enough shows for a row to be a selection; below
  // that, the full shelf underneath already is every row at once.
  if (series && shows.length > ROW) {
    const byLead = (rank) => [...shows].sort((a, b) => rank(firstItemOf(a.divisions), firstItemOf(b.divisions))).slice(0, ROW);
    main.append(deptRow("Popular series", collectionGrid(section, byLead(popular), open, { mode, strip: true })));
    main.append(deptRow("New episodes", collectionGrid(section, shelves.latestSeries, open, { mode, strip: true })));
  }
  main.append(deptRow(series ? "All shows" : "All courses", collectionGrid(section, shows, open, { mode })));
  revealWithin(main);
}

function allLink({ href, label }) {
  const link = el("a", "dept-all", `${label} →`);
  link.href = href;
  return link;
}
