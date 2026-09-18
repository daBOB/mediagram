/**
 * The library page: three shelves, a drill-down, and a player.
 *
 * Talks only to this server's own API. It never learns which channel or
 * message a set's bytes live in, which is the point of keeping those on the
 * server side.
 *
 * Routing is the URL hash, so the back button works and a view can be linked:
 *   #/movies                     #/series                #/tutorials
 *   #/series/Widow%27s%20Bay     #/tutorials/Geldhochschule
 */

import { el } from "./lib/dom.js";
import { countOf } from "./lib/format.js";
import { renderSearch } from "./lib/search-view.js";
import { divisionAt, firstItemOf, groupLibrary, lessonsUnder, nextAfter } from "./lib/library.js";
import { loadLink } from "./lib/link.js";
import { openPlayer } from "./lib/player.js";
import { divisionBlock, levelBlock } from "./lib/course-view.js";
import { describeSeries, seriesHeader } from "./lib/series-header.js";
import { SECTIONS, collectionGrid, emptyState, movieGrid, setGrid } from "./lib/shelf-view.js";
import * as state from "./lib/watch-state.js";
import { resumeAt } from "./lib/resume-point.js";
import { listControls, listsView, listView } from "./lib/collections-view.js";
import { chooseProfile } from "./lib/profile-picker.js";

const main = document.getElementById("main");
const searchBox = document.getElementById("search");

/** @type {{movies: any[], series: any[], tutorials: any[]}} */
let library = { movies: [], series: [], tutorials: [] };

/**
 * Every set by id, for the shelves built from watch state.
 *
 * Those hold ids rather than titles — a position is recorded against a set,
 * not against a copy of one — so turning a list of ids back into things to
 * show needs one lookup rather than a search of three shelves.
 */
let byId = new Map();

/** The shelves that come from what has been watched rather than the catalog. */
const KEPT = {
  continue: { label: "Continue", empty: "Nothing started yet." },
  watchlist: { label: "Watchlist", empty: "Nothing on the list." },
  collections: { label: "Collections", empty: "No lists yet." },
};

/** Ids to sets, quietly dropping any the catalog no longer holds. */
const setsFor = (ids) => ids.map((id) => byId.get(id)).filter(Boolean);

/**
 * A shelf's title and how much is on it, as one block.
 *
 * Together rather than as two siblings because the stylesheet sets them on a
 * shared baseline with the extent flush right, which two separate children
 * of `main` could not do.
 */
function heading(title, subtitle) {
  const head = el("header", "shelf-head");
  head.append(el("h1", null, title));
  if (subtitle) head.append(el("p", "sub", subtitle));
  main.append(head);
}

/** Films: a flat grid, since a film is one thing. */
function viewMovies() {
  heading(SECTIONS.movies.label, countOf(library.movies.length, SECTIONS.movies.extent));
  if (library.movies.length === 0) return main.append(emptyState("movies"));
  main.append(movieGrid(library.movies, play));
}

/** Shows and courses: a grid of collections, each opening its own view. */
function viewCollections(section) {
  const collections = library[section];
  heading(SECTIONS[section].label, countOf(collections.length, SECTIONS[section].extent));
  if (collections.length === 0) return main.append(emptyState(section));

  main.append(
    collectionGrid(section, collections, (name) => {
      location.hash = `#/${section}/${encodeURIComponent(name)}`;
    }),
  );
}

/**
 * The way back up, as far as here.
 *
 * Ancestors only: where you are is the heading directly below, and printing
 * it twice says nothing the second time. Real links rather than buttons,
 * because every one of these is a URL that works on its own.
 */
function crumbs(section, collectionName, folders) {
  const nav = el("nav", "crumbs");
  let hash = `#/${section}`;
  const trail = [{ label: (SECTIONS[section] ?? KEPT[section]).label, hash }];

  if (collectionName !== null) {
    hash += `/${encodeURIComponent(collectionName)}`;
    trail.push({ label: collectionName, hash });
  }
  for (const folder of folders) {
    hash += `/${encodeURIComponent(folder)}`;
    trail.push({ label: folder, hash });
  }
  // The last entry is where the viewer already is — unless it is the only
  // one, in which case it is the shelf above and the way back out.
  if (trail.length > 1) trail.pop();

  for (const [index, step] of trail.entries()) {
    if (index > 0) nav.append(el("span", "sep", "\u203a"));
    const link = el("a", null, step.label);
    link.href = step.hash;
    nav.append(link);
  }
  return nav;
}

/**
 * One show, or one level of one course.
 *
 * The two part company here because the containers do. A show's seasons are
 * one flat level holding episodes, so the whole of it goes on the page. A
 * course is four levels and 162 lessons, so one floor goes on the page and
 * the folders are doors.
 */
function viewCollection(section, name, folders) {
  const collection = library[section].find((entry) => entry.name === name);
  if (!collection) {
    main.append(el("p", "error", `No ${section === "series" ? "show" : "course"} called "${name}".`));
    return;
  }

  if (section === "tutorials") return viewCourseLevel(collection, folders);

  main.append(crumbs(section, collection.name, []));
  heading(collection.name, countOf(collection.count, "episode"));
  // The artwork a show's episodes share is the show's own; `firstItemOf`
  // is what the shelf card already uses to find it.
  const first = firstItemOf(collection.divisions);
  const header = seriesHeader(collection, first?.poster ?? null);
  main.append(header);
  // Asked for separately, and late: the description is one page's worth of
  // text, and putting it on every catalog row would send all of it to build
  // a shelf that shows none of it. The facts are already on screen; what the
  // provider says is added to them rather than rebuilding the header, which
  // would recount every episode and swap the poster under the viewer.
  if (first?.showKey) {
    fetch(`/api/shows/${encodeURIComponent(first.showKey)}`)
      .then((res) => (res.ok ? res.json() : null))
      .then((meta) => describeSeries(header, meta))
      .catch(() => {});
  }
  for (const division of collection.divisions) main.append(divisionBlock(division, 0, play));
}

/** One floor of a course: the lessons in this folder, and the doors below. */
function viewCourseLevel(collection, folders) {
  const level = divisionAt(collection.divisions, folders);
  if (level === null) {
    // A stale or hand-typed URL. Says which folder, because "not found" about
    // a four-deep trail leaves the viewer to work out which part was wrong.
    main.append(crumbs("tutorials", collection.name, []));
    main.append(
      el("p", "error", `"${collection.name}" has no folder called "${folders.join(" › ")}".`),
    );
    return;
  }

  main.append(crumbs("tutorials", collection.name, folders));
  // `title` is null only for the stand-in at the top, where the course's own
  // name is the heading.
  heading(level.title ?? collection.name, countOf(lessonsUnder(level), "lesson"));

  main.append(
    levelBlock(
      level,
      (folder) => {
        location.hash = `#/tutorials/${[collection.name, ...folders, folder]
          .map(encodeURIComponent)
          .join("/")}`;
      },
      play,
    ),
  );
}

/**
 * Opens a title, and tells the player what follows it.
 *
 * Worked out here rather than in the player because the collection a title
 * came from is what says — and the page holds the collections. Passing the
 * opener along too means the title after *that* one is found the same way,
 * however many the viewer sits through.
 */
function play(set) {
  const collection = [...library.series, ...library.tutorials].find((entry) =>
    entry.name === set.show,
  );
  openPlayer(set, {
    next: collection ? nextAfter(collection, set.setId) : null,
    onOpenNext: play,
  });
}

/** The counts beside the shelves that come from watch state. */
function refreshKept() {
  const started = state.inProgress().filter((row) => resumeAt(row) !== null && byId.has(row.setId));
  document.getElementById("n-continue").textContent = String(started.length);
  document.getElementById("n-watchlist").textContent = String(setsFor(state.watchlist()).length);
  document.getElementById("n-collections").textContent = String(state.collections().length);
}

/** Whose shelves these are, and the way to become somebody else. */
function showProfile() {
  const button = document.getElementById("who");
  const current = state.profile();
  button.textContent = current ? current.name : "Who\u2019s watching?";
  button.hidden = false;
}

/** What was started and not finished, most recent first. */
function viewContinue() {
  const started = state
    .inProgress()
    .filter((row) => resumeAt(row) !== null)
    .map((row) => byId.get(row.setId))
    .filter(Boolean);

  heading(KEPT.continue.label, countOf(started.length, "title"));
  if (started.length === 0) return main.append(el("p", "empty", KEPT.continue.empty));
  main.append(setGrid(started, play));
}

/** Titles marked to come back to. */
function viewWatchlist() {
  const listed = setsFor(state.watchlist());
  heading(KEPT.watchlist.label, countOf(listed.length, "title"));
  if (listed.length === 0) return main.append(el("p", "empty", KEPT.watchlist.empty));
  main.append(setGrid(listed, play));
}

/** The lists themselves. */
function viewLists() {
  heading(KEPT.collections.label, countOf(state.collections().length, "list"));
  main.append(listsView((id) => (location.hash = `#/collections/${encodeURIComponent(id)}`), route));
}

/** Inside one list. */
function viewList(id) {
  const list = state.collections().find((entry) => entry.id === id);
  if (!list) {
    main.append(crumbs("collections", null, []));
    main.append(el("p", "error", "That list is not here any more."));
    return;
  }

  main.append(crumbs("collections", null, []));
  heading(list.name, countOf(list.items.length, "title"));
  main.append(listControls(list, route, () => (location.hash = "#/collections")));

  const sets = setsFor(list.items);
  if (sets.length === 0) {
    main.append(el("p", "empty", "Nothing in this list yet. Add titles from the player."));
    return;
  }
  main.append(listView(list, sets, play, route));
}

/** Asks the server, because summaries live there and are not in the catalog. */
async function viewSearch(query) {
  main.append(el("p", "empty", "Searching…"));
  try {
    const response = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
    if (!response.ok) throw new Error(`the server answered ${response.status}`);
    const { hits } = await response.json();
    main.textContent = "";
    renderSearch(main, query, hits, play);
  } catch (error) {
    main.textContent = "";
    main.append(el("p", "error", `Search failed: ${error.message}`));
  }
}

function route() {
  // Everything after the collection is the trail of folders into a course.
  // Each segment is encoded on the way out, so a folder whose name contains a
  // slash survives the split rather than becoming two folders.
  const parts = location.hash
    .replace(/^#\/?/, "")
    .split("/")
    .filter((part) => part !== "");
  const [section = "movies", name, ...folders] = parts;
  const known =
    SECTIONS[section] || KEPT[section] ? section : section === "search" ? "search" : "movies";

  for (const link of document.querySelectorAll("nav a")) {
    link.classList.toggle("active", link.dataset.section === known);
  }

  main.textContent = "";
  refreshKept();
  if (known === "search") {
    const query = decodeURIComponent(name ?? "");
    searchBox.value = query;
    void viewSearch(query);
    return;
  }
  // Leaving a search clears the box, so the shelf and the field agree.
  if (searchBox.value !== "") searchBox.value = "";

  if (known === "continue") return viewContinue();
  if (known === "watchlist") return viewWatchlist();
  if (known === "collections") {
    return name ? viewList(decodeURIComponent(name)) : viewLists();
  }

  if (name) viewCollection(known, decodeURIComponent(name), folders.map(decodeURIComponent));
  else if (known === "movies") viewMovies();
  else viewCollections(known);
}

/**
 * Typing searches, with a pause.
 *
 * The hash carries the query so a result list can be linked and the back
 * button leaves it, but writing the hash on every keystroke would fill the
 * history with half-typed words — so the address is replaced while typing and
 * only pushed when the typing stops.
 */
let typing = null;
searchBox.addEventListener("input", () => {
  clearTimeout(typing);
  typing = setTimeout(() => {
    const query = searchBox.value.trim();
    if (query === "") {
      location.hash = "#/movies";
      return;
    }
    location.hash = `#/search/${encodeURIComponent(query)}`;
  }, 200);
});

window.addEventListener("hashchange", route);

try {
  // Asked for first: every shelf badge depends on whether this page is being
  // watched from the sofa or from somewhere with an uplink in between.
  await loadLink();
  // Who, before anything else: every shelf below is one profile's, and the
  // first render already draws progress rules.
  const [response] = await Promise.all([fetch("/api/sets"), state.loadProfiles()]);
  if (!response.ok) throw new Error(`the catalog answered ${response.status}`);
  const sets = await response.json();
  library = groupLibrary(sets);
  byId = new Map(sets.map((set) => [set.setId, set]));

  // Remembered on this device, if that profile is still one of them; asked
  // otherwise, which is also the first run on a new player.
  const known = state.rememberedProfile();
  if (known) await state.useProfile(known);
  else await chooseProfile(document.body);
  showProfile();

  document.getElementById("n-movies").textContent = String(library.movies.length);
  document.getElementById("n-series").textContent = String(library.series.length);
  document.getElementById("n-tutorials").textContent = String(library.tutorials.length);
  document.getElementById("foot").textContent = `${sets.length} playable sets`;
  refreshKept();

  if (!location.hash) {
    // Somewhere half-watched beats a shelf: the reason to open this page at
    // all is usually the thing that was not finished last time.
    const unfinished = state.inProgress().some((row) => resumeAt(row) !== null && byId.has(row.setId));
    const first = ["movies", "series", "tutorials"].find((s) => library[s].length > 0) ?? "movies";
    location.hash = `#/${unfinished ? "continue" : first}`;
  }
  route();
} catch (error) {
  main.textContent = "";
  main.append(el("p", "error", `Could not load the catalog: ${error.message}`));
}
