/**
 * Search as a discovery page: films as posters, shows as show cards (the
 * episodes that matched are grouped into their show, as every shelf groups
 * them), then the matching episodes and lessons as rows that say *where*
 * each sits and *why* it matched — a lesson title alone rarely tells one
 * from the next.
 *
 * Filter pills narrow the page to one kind without asking the server again.
 * People come from the index's credits (schema v9) and collections are the
 * franchises and lists whose name holds every word of the query.
 */

import { el } from "../dom.js";
import { codecLine, countOf, episodeLabel, humanDuration, humanSize } from "../format.js";
import { offlineBadge, progressRuleFor, watchedTick } from "./set-badge.js";
import { collectionGrid, movieGrid } from "./shelf-view.js";
import { GRID } from "./shelf-mode.js";
import { personCard } from "./cast.js";
import { destination } from "./collections-page.js";

/** How a hit earned its place, in words rather than a field name. */
const WHY = {
  title: null,
  show: "matched the series or course",
  chap: "matched the chapter",
  path: "matched the folder",
  summary: "found in the summary",
};

/** Where a hit sits, as a person would say it. */
function locationOf(hit) {
  if (hit.kind === "ep") {
    return [hit.show, episodeLabel(hit)].filter(Boolean).join(" · ");
  }
  if (hit.kind === "tut") {
    // The folder path reads better than the generated chapter label, and is
    // what the shelves show too.
    const where = hit.path ?? hit.chap;
    return [hit.show, where].filter(Boolean).join(" · ");
  }
  return [hit.year].filter(Boolean).join(" · ");
}

/** The filter the viewer chose, kept while they refine the same search. */
let chosen = "all";

/**
 * Renders `hits` into `main`.
 *
 * @param {{ play: (set: any) => void, openFilm: (set: any) => void,
 *   shows: import("../library.js").Collection[], openShow: (section: string, name: string) => void,
 *   people: any[], franchises: {id: number, name: string, films: any[], art: string|null}[],
 *   lists: {id: string, name: string, items: string[]}[] }} on
 */
export function renderSearch(main, query, hits, { play, openFilm, shows, openShow, people = [], franchises = [], lists = [] }) {
  const words = query.toLocaleLowerCase().split(/\s+/).filter(Boolean);
  const named = (name) => words.length > 0 && words.every((word) => name.toLocaleLowerCase().includes(word));
  const places = [
    ...franchises.filter((f) => named(f.name))
      .map((f) => destination(f.name, countOf(f.films.length, "film"), f.art, `#/collections/tmdb-${f.id}`)),
    ...lists.filter((list) => named(list.name))
      .map((list) => destination(list.name, countOf(list.items.length, "title"), null, `#/collections/${encodeURIComponent(list.id)}`)),
  ];
  const head = el("header", "shelf-head");
  head.append(el("h1", null, `“${query}”`));
  // The same block the shelves use, so a result list is a page of the
  // catalogue rather than a different screen.
  const total = hits.length + people.length + places.length;
  head.append(el("p", "sub", total === 0 ? "nothing found" : countOf(total, "result")));
  main.append(head);
  if (hits.length + people.length + places.length === 0) {
    main.append(el("p", "empty", "No title, person, folder or summary in the library mentions that."));
    return;
  }

  const films = hits.filter((hit) => hit.kind === "movie");
  const episodes = hits.filter((hit) => hit.kind === "ep");
  const matchedShows = [...new Set(episodes.map((hit) => hit.show))]
    .map((name) => shows.find((show) => show.name === name)).filter(Boolean);
  const docus = hits.filter((hit) => hit.kind === "docu");
  const lessons = hits.filter((hit) => !["movie", "ep", "docu"].includes(hit.kind));
  const kinds = [
    ["all", "All", total],
    ["movies", "Movies", films.length],
    ["series", "Series", episodes.length],
    ["documentaries", "Documentaries", docus.length],
    ["tutorials", "Tutorials", lessons.length],
    ["people", "People", people.length],
    ["collections", "Collections", places.length],
  ].filter(([, , count]) => count > 0);
  if (!kinds.some(([key]) => key === chosen)) chosen = "all";

  const results = el("div", "search-results");
  const draw = () => {
    results.replaceChildren();
    const part = (key, title, node) => {
      if (chosen !== "all" && chosen !== key) return;
      const section = el("section", "search-part");
      section.append(el("h2", "shelf-sub", title), node);
      results.append(section);
    };
    if (films.length > 0) part("movies", "Movies", movieGrid(films, openFilm, { mode: GRID }));
    if (matchedShows.length > 0) {
      part("series", "Series", collectionGrid("series", matchedShows, (name) => openShow("series", name), { mode: GRID }));
    }
    if (episodes.length > 0) part("series", "Episodes", rows(episodes, play));
    if (docus.length > 0) part("documentaries", "Documentaries", rows(docus, play));
    if (lessons.length > 0) part("tutorials", "Lessons", rows(lessons, play));
    if (people.length > 0) {
      const faces = el("div", "people");
      faces.append(...people.map((person) => personCard({ ...person, role: countOf(person.titles, "title") })));
      part("people", "People", faces);
    }
    if (places.length > 0) {
      const cards = el("div", "destinations");
      cards.append(...places);
      part("collections", "Collections", cards);
    }
  };

  if (kinds.length > 2) {
    const filters = el("div", "search-filters");
    filters.setAttribute("role", "group");
    filters.setAttribute("aria-label", "Show only");
    for (const [key, label, count] of kinds) {
      const pill = el("button", "search-filter");
      pill.type = "button";
      pill.append(el("span", null, label), el("span", "n", String(count)));
      pill.setAttribute("aria-pressed", String(key === chosen));
      pill.addEventListener("click", () => {
        chosen = key;
        for (const other of filters.children) other.setAttribute("aria-pressed", String(other === pill));
        draw();
      });
      filters.append(pill);
    }
    main.append(filters);
  }
  draw();
  main.append(results);
}

/** Episodes and lessons: one row each, saying where it sits and why it matched. */
function rows(hits, onPlay) {
  const block = el("section", "season");
  for (const hit of hits) {
    const row = el("button", "row");
    row.append(el("div", "num", episodeLabel(hit) || ""));

    const title = el("div", "title");
    const name = el("b", null, hit.title ?? hit.setId);
    const tick = watchedTick(hit);
    if (tick) name.prepend(tick);
    title.append(name);
    const where = locationOf(hit);
    if (where) title.append(el("span", null, where));
    // The excerpt is the reason a summary hit is worth showing at all.
    if (hit.excerpt) title.append(el("span", "excerpt", hit.excerpt));
    row.append(title);

    const why = WHY[hit.matched];
    if (why) row.append(el("span", "badge", why));
    // After the reason it matched, which is what the viewer came here for.
    const held = offlineBadge(hit);
    if (held) row.append(held);

    row.append(
      el(
        "div",
        "meta",
        [codecLine(hit), humanDuration(hit.duration), humanSize(hit.total)].filter(Boolean).join(" · "),
      ),
    );
    const progress = progressRuleFor(hit);
    if (progress) row.append(progress);

    row.addEventListener("click", () => onPlay(hit));
    block.append(row);
  }
  return block;
}
