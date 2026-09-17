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
import { renderSearch } from "./lib/search-view.js";
import { groupLibrary } from "./lib/library.js";
import { loadLink } from "./lib/link.js";
import { openPlayer } from "./lib/player.js";
import { divisionBlock } from "./lib/course-view.js";
import { SECTIONS, collectionGrid, emptyState, movieGrid } from "./lib/shelf-view.js";

const main = document.getElementById("main");
const searchBox = document.getElementById("search");

/** @type {{movies: any[], series: any[], tutorials: any[]}} */
let library = { movies: [], series: [], tutorials: [] };

function heading(title, subtitle) {
  main.append(el("h1", null, title));
  if (subtitle) main.append(el("p", "sub", subtitle));
}

/** Films: a flat grid, since a film is one thing. */
function viewMovies() {
  heading("Movies", `${library.movies.length} in the library`);
  if (library.movies.length === 0) return main.append(emptyState("movies"));
  main.append(movieGrid(library.movies, openPlayer));
}

/** Shows and courses: a grid of collections, each opening its own view. */
function viewCollections(section) {
  const collections = library[section];
  heading(SECTIONS[section].label, `${collections.length} in the library`);
  if (collections.length === 0) return main.append(emptyState(section));

  main.append(
    collectionGrid(section, collections, (name) => {
      location.hash = `#/${section}/${encodeURIComponent(name)}`;
    }),
  );
}

/** One show or course: its divisions, each a list of numbered items. */
function viewCollection(section, name) {
  const collection = library[section].find((entry) => entry.name === name);
  if (!collection) {
    main.append(el("p", "error", `No ${section === "series" ? "show" : "course"} called "${name}".`));
    return;
  }

  const back = el("button", "back", `← ${SECTIONS[section].label}`);
  back.addEventListener("click", () => {
    location.hash = `#/${section}`;
  });
  main.append(back);
  heading(collection.name, `${collection.count} ${section === "series" ? "episodes" : "lessons"}`);

  for (const division of collection.divisions) main.append(divisionBlock(division, 0, openPlayer));
}

/** Asks the server, because summaries live there and are not in the catalog. */
async function viewSearch(query) {
  main.append(el("p", "empty", "Searching…"));
  try {
    const response = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
    if (!response.ok) throw new Error(`the server answered ${response.status}`);
    const { hits } = await response.json();
    main.textContent = "";
    renderSearch(main, query, hits, openPlayer);
  } catch (error) {
    main.textContent = "";
    main.append(el("p", "error", `Search failed: ${error.message}`));
  }
}

function route() {
  const [section = "movies", name] = location.hash.replace(/^#\/?/, "").split("/");
  const known = SECTIONS[section] ? section : section === "search" ? "search" : "movies";

  for (const link of document.querySelectorAll("nav a")) {
    link.classList.toggle("active", link.dataset.section === known);
  }

  main.textContent = "";
  if (known === "search") {
    const query = decodeURIComponent(name ?? "");
    searchBox.value = query;
    void viewSearch(query);
    return;
  }
  // Leaving a search clears the box, so the shelf and the field agree.
  if (searchBox.value !== "") searchBox.value = "";
  if (name) viewCollection(known, decodeURIComponent(name));
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
  const response = await fetch("/api/sets");
  if (!response.ok) throw new Error(`the catalog answered ${response.status}`);
  const sets = await response.json();
  library = groupLibrary(sets);

  document.getElementById("n-movies").textContent = String(library.movies.length);
  document.getElementById("n-series").textContent = String(library.series.length);
  document.getElementById("n-tutorials").textContent = String(library.tutorials.length);
  document.getElementById("foot").textContent = `${sets.length} playable sets`;

  if (!location.hash) {
    // Open on a shelf that has something in it.
    const first = ["movies", "series", "tutorials"].find((s) => library[s].length > 0) ?? "movies";
    location.hash = `#/${first}`;
  }
  route();
} catch (error) {
  main.textContent = "";
  main.append(el("p", "error", `Could not load the catalog: ${error.message}`));
}
