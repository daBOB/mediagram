/**
 * Collections as destinations: the film franchises the library holds (from
 * TMDB's `belongs_to_collection`, schema v9, plus the MCU from `mcu.js`) and
 * the viewer's own lists, both as large cards — then, for a franchise, a page
 * that introduces it and lays its films out in its order: release order, or
 * the MCU's story order.
 *
 * A franchise needs two held films to count: one film is a title, not a
 * collection. The introduction is TMDB's own overview of the collection
 * when it has one; otherwise the page says only what the library knows.
 */

import { el } from "../dom.js";
import { countOf } from "../format.js";
import { everyFilm } from "../departments.js";
import { artworkUrl } from "./plate.js";
import { departmentHero, deptRow } from "./department-hero.js";
import { movieGrid } from "./shelf-view.js";
import { GRID } from "./shelf-mode.js";
import { newListButton } from "./collections-view.js";
import { revealWithin } from "../reveal.js";
import { href } from "../address.js";
import { mcuIn } from "./mcu.js";

/**
 * The franchises with at least two films held, largest first; each with its
 * films in release order and the most popular one's backdrop. The MCU is one
 * of them, in story order (`mcu.js`).
 * @returns {{ id: number, name: string, order: string, films: any[], art: string|null }[]}
 */
export function franchisesIn(movies) {
  const byId = new Map();
  for (const film of movies) {
    if (!film.collectionId) continue;
    if (!byId.has(film.collectionId)) byId.set(film.collectionId, { id: film.collectionId, name: film.collectionName ?? "", films: [] });
    byId.get(film.collectionId).films.push(film);
  }
  const tmdb = [...byId.values()]
    .filter((franchise) => franchise.films.length >= 2)
    .map((franchise) => {
      const films = [...franchise.films].sort((a, b) => (a.year ?? 9999) - (b.year ?? 9999));
      const lead = [...films].filter((f) => f.backdrop || f.poster).sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0))[0];
      return { ...franchise, order: "In release order", films, art: lead?.backdrop ?? lead?.poster ?? null };
    });
  const mcu = mcuIn(movies);
  return (mcu ? [...tmdb, mcu] : tmdb)
    .sort((a, b) => b.films.length - a.films.length || a.name.localeCompare(b.name));
}

/** A large card: artwork, the name set large, and how much is inside. */
export function destination(name, meta, art, href) {
  const card = el("a", "destination");
  card.href = href;
  if (art) {
    const image = el("img");
    image.src = artworkUrl(art);
    image.alt = "";
    image.loading = "lazy";
    image.decoding = "async";
    card.append(image);
  }
  card.append(el("span", "destination-name", name), el("span", "destination-meta", meta));
  return card;
}

/**
 * @param {{ library: any, lists: {id: string, name: string, items: string[]}[],
 *   setsFor: (ids: string[]) => any[] }} on
 */
export function renderCollectionsPage(main, { library, lists, setsFor }) {
  const franchises = franchisesIn(everyFilm(library));
  const lead = franchises[0]?.films.find((film) => film.backdrop) ?? null;
  main.append(departmentHero({
    title: "Collections",
    line: [franchises.length > 0 ? countOf(franchises.length, "franchise") : null, countOf(lists.length, "list")]
      .filter(Boolean).join(" · "),
    lead,
    leadName: lead?.title ?? null,
    leadHref: lead ? href({ page: "film", setId: lead.setId }) : null,
  }));
  if (franchises.length > 0) {
    const grid = el("div", "destinations");
    grid.append(...franchises.map((f) => destination(f.name, countOf(f.films.length, "film"), f.art, href({ page: "franchise", id: f.id }))));
    main.append(deptRow("Franchises", grid));
  }
  const yours = el("div", "destinations");
  for (const list of lists) {
    const first = setsFor(list.items).find((set) => set.backdrop || set.poster);
    yours.append(destination(list.name, countOf(list.items.length, "title"),
      first?.backdrop ?? first?.poster ?? null, href({ page: "list", id: list.id })));
  }
  const section = deptRow("Your lists", yours);
  section.append(newListButton());
  main.append(section);
  revealWithin(main);
}

/** TMDB's franchise overviews, once fetched: a redraw draws them at once. */
let overviews = null;

/** One franchise: its introduction, then its films in order, playable as one run. */
export function renderFranchise(main, id, { library, openFilm, play }, stillHere) {
  const franchise = franchisesIn(everyFilm(library)).find((f) => String(f.id) === String(id));
  if (!franchise) {
    main.append(el("p", "error", "That collection is not in the library."));
    return;
  }
  const years = franchise.films.map((f) => f.year).filter(Boolean);
  const span = years.length > 0 ? `${Math.min(...years)}–${Math.max(...years)}` : null;
  const lead = franchise.films.find((film) => film.backdrop) ?? null;
  const hero = departmentHero({
    title: franchise.name,
    line: [countOf(franchise.films.length, "film"), span].filter(Boolean).join(" · "),
    lead,
    leadName: lead?.title ?? null,
    leadHref: lead ? href({ page: "film", setId: lead.setId }) : null,
  });
  hero.classList.add("franchise-hero");
  main.append(hero);
  // The list page's own "Play all": the films play on in the page's order.
  const controls = el("div", "list-controls");
  const all = el("button", "quiet play-all", "Play all");
  all.addEventListener("click", () => play(franchise.films[0], franchise.films));
  controls.append(all);
  main.append(controls, deptRow(franchise.order, movieGrid(franchise.films, openFilm, { mode: GRID })));
  revealWithin(main);
  const introduce = (all) => {
    const overview = all.find((f) => f.id === franchise.id)?.overview;
    if (overview && stillHere()) hero.querySelector(".dept-copy")?.append(el("p", "franchise-overview", overview));
  };
  if (overviews) return introduce(overviews);
  fetch("/api/franchises")
    .then((res) => (res.ok ? res.json() : []))
    .then((all) => { overviews = all; introduce(all); })
    .catch(() => {});
}
