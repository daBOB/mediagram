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
import { renderSearch } from "./lib/catalog/search-view.js";
import {
  groupLibrary,
  nextAfter,
  nextInQueue,
} from "./lib/library.js";
import { catalogOf, loadLink } from "./lib/link.js";
import { colophonLine } from "./lib/colophon.js";
import { watchStatus } from "./lib/status/status-view.js";
import { initializePlayer, openPlayer } from "./lib/playback/player.js";
import { renderCollection } from "./lib/catalog/course-view.js";
import { SECTIONS, collectionGrid, emptyState, heading, movieGrid, setGrid } from "./lib/catalog/shelf-view.js";
import { GRID, LIST, setShelfMode, shelfMode } from "./lib/catalog/shelf-mode.js";
import * as state from "./lib/watch-state.js";
import { resumeAt } from "./lib/resume-point.js";
import { renderLists, renderList } from "./lib/catalog/collections-view.js";
import { chooseProfile } from "./lib/profile-picker.js";
import { homeShelves } from "./lib/catalog/home-shelves.js";
import { renderHome } from "./lib/catalog/home-view.js";
import { describeFilm, filmPage } from "./lib/catalog/film-page.js";
import { genreShelf } from "./lib/catalog/genres.js";
import { kidsShelf } from "./lib/age-rating.js";

const main = document.getElementById("main");
const player = document.getElementById("player");
const searchBox = document.getElementById("search");
initializePlayer();

/** @type {import("./lib/library.js").Library} */
let library = { movies: [], series: [], tutorials: [] };

/**
 * Every set by id, for the shelves built from watch state.
 *
 * Those hold ids rather than titles — a position is recorded against a set,
 * not against a copy of one — so turning a list of ids back into things to
 * show needs one lookup rather than a search of three shelves.
 */
let byId = new Map();

/** Views that are neither a catalog shelf nor one built from watch state. */
const PAGES = new Set(["home", "search", "system", "film", "genre"]);

/** The shelves that come from what has been watched rather than the catalog. */
const KEPT = {
  continue: { label: "Continue", empty: "Nothing started yet." },
  watchlist: { label: "Watchlist", empty: "Nothing on the list." },
  collections: { label: "Collections", empty: "No lists yet." },
  kids: {
    label: "Kids",
    empty: "Nothing rated FSK 12 or younger, and nothing marked. An unrated title can be marked with Kids in the player.",
  },
};

/** Ids to sets, quietly dropping any the catalog no longer holds. */
const setsFor = (ids) => ids.map((id) => byId.get(id)).filter(Boolean);

/**
 * List or plates, for the two shelves that have artwork worth showing.
 *
 * Two buttons rather than a select: there are two states, and a menu that
 * opens to offer a choice of two is a menu that should have been the choice.
 * Re-renders through `route()` so the shelf is rebuilt the same way it is
 * built on arrival — the mode is read at render time, not passed around.
 */
function shelfToggle() {
  const current = shelfMode();
  const control = el("div", "shelf-modes");
  control.setAttribute("role", "group");
  control.setAttribute("aria-label", "How to show this shelf");

  for (const [mode, label] of [
    [LIST, "List"],
    [GRID, "Grid"],
  ]) {
    const button = el("button", "mode", label);
    button.type = "button";
    if (mode === current) {
      button.classList.add("on");
      // The pressed state rather than `disabled`: a viewer reading with a
      // screen reader is told which they are on, and the control does not
      // lose focus when the shelf rebuilds under it.
      button.setAttribute("aria-pressed", "true");
    } else {
      button.setAttribute("aria-pressed", "false");
      button.addEventListener("click", () => {
        setShelfMode(mode);
        route();
      });
    }
    control.append(button);
  }
  return control;
}

/**
 * Where the player opens: what is underway, and what arrived.
 *
 * The rules are all in `home-shelves.js` and the drawing is all in
 * `home-view.js`; what is left here is handing one the state and the other
 * the two things only this module can do — play a title, and open a shelf.
 *
 * A library with nothing in it has no rows to draw at all, and falls back to
 * the one empty state that says how to fill it.
 */
function viewHome() {
  const shelves = homeShelves({
    library,
    byId,
    progress: state.inProgress(),
    watchedAt: state.watchedAt,
  });

  const empty = Object.values(shelves).every((row) => row.length === 0);
  if (empty) {
    heading(main, SECTIONS.movies.label, countOf(0, SECTIONS.movies.extent));
    return main.append(emptyState("movies"));
  }

  renderHome(main, shelves, {
    play: (set) => play(set),
    openFilm,
    open: (section, name) => {
      location.hash = `#/${section}/${encodeURIComponent(name)}`;
    },
  });
}

/** Films: a flat grid, since a film is one thing. */
function viewMovies() {
  const mode = shelfMode();
  heading(main,
    SECTIONS.movies.label,
    countOf(library.movies.length, SECTIONS.movies.extent),
    // No control over an empty shelf: there is nothing to lay out either way,
    // and offering the choice would be offering it about nothing.
    library.movies.length > 0 ? shelfToggle() : null,
  );
  if (library.movies.length === 0) return main.append(emptyState("movies"));
  main.append(movieGrid(library.movies, openFilm, { mode }));
}

/** A film's card opens its page; the page's button plays it. */
function openFilm(set) {
  location.hash = `#/film/${encodeURIComponent(set.setId)}`;
}

/**
 * One film's page. The provider's description, score and genres are asked
 * for after the facts are drawn, as a series header's are.
 */
function viewFilm(setId) {
  const set = byId.get(setId);
  if (!set || set.kind !== "movie") {
    heading(main, SECTIONS.movies.label);
    main.append(el("p", "error", "That film is not in the library any more."));
    return;
  }
  heading(main, set.title ?? set.setId);
  const page = filmPage(set, {
    resume: resumeAt(state.progressOf(set.setId)),
    onPlay: (film) => play(film),
  });
  main.append(page);
  if (set.showKey) {
    fetch(`/api/shows/${encodeURIComponent(set.showKey)}`)
      .then((res) => (res.ok ? res.json() : null))
      .then((meta) => describeFilm(page, meta))
      .catch(() => {});
  }
}

/** Everything tagged with one genre: films first, then series. */
function viewGenre(name) {
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
    main.append(
      collectionGrid("series", series, (title) => {
        location.hash = `#/series/${encodeURIComponent(title)}`;
      }, { mode: GRID }),
    );
  }
}

/** Shows and courses: a grid of collections, each opening its own view. */
function viewCollections(section) {
  const collections = library[section];
  // Series only. A course has no artwork — `posterKeyFor` files everything
  // under a TMDB id and a course has none — so a wall of plates would be a
  // wall of initials, and a hundred and seventy lessons are a list anyway.
  const offersModes = section === "series" && collections.length > 0;
  const mode = offersModes ? shelfMode() : LIST;
  heading(main,
    SECTIONS[section].label,
    countOf(collections.length, SECTIONS[section].extent),
    offersModes ? shelfToggle() : null,
  );
  if (collections.length === 0) return main.append(emptyState(section));

  main.append(
    collectionGrid(
      section,
      collections,
      (name) => {
        location.hash = `#/${section}/${encodeURIComponent(name)}`;
      },
      { mode },
    ),
  );
}

/** Only the latest request may open, and closing the player withdraws it. */
let pendingOpen = 0;

/**
 * Opens a title, and says what follows it.
 *
 * `queue` is a hand-built list being played through, in which case what
 * follows is the next thing on it. Without one, what follows is the next
 * episode or lesson of whatever the title belongs to, which is the ordinary
 * case of pressing play on a shelf.
 *
 * A snapshot, deliberately: a title removed from the list halfway through a
 * run does not change the run. Re-reading the list under a viewer who is
 * watching it would be the stranger behaviour.
 */
function play(set, queue = null, options = {}) {
  const request = ++pendingOpen;
  // The position to resume from is read fresh: this tab may have been open
  // while the same viewer watched further on another device.
  void state.refreshState().finally(() => {
    if (request === pendingOpen) openTitle(set, queue, options);
  });
}

function openTitle(set, queue, options) {
  // `autoplay` is set only by the player handing over to what follows, and
  // says which kind of start that is. Opening a title from a shelf never
  // carries one, so it loads and waits for the viewer as it always has.
  const autoplay = options.autoplay ?? null;

  if (queue) {
    openPlayer(set, {
      next: nextInQueue(queue, set.setId),
      onOpenNext: (following, how) => play(following, queue, how),
      autoplay,
    });
    return;
  }
  const collection = [...library.series, ...library.tutorials].find((entry) =>
    entry.name === set.show,
  );
  if (collection && set.kind === "ep") preloadAfter(collection, set.setId);
  openPlayer(set, {
    next: collection ? nextAfter(collection, set.setId) : null,
    onOpenNext: (following, how) => play(following, null, how),
    autoplay,
  });
}

/**
 * Asks the server to take the next two episodes into its cache.
 *
 * Named here, with the `nextAfter` that decides Play next, so what is fetched
 * ahead is exactly what would play next. Fire and forget: a player with
 * preload turned off answers 404, and either way this episode plays the same.
 */
function preloadAfter(collection, setId) {
  const first = nextAfter(collection, setId);
  const second = first ? nextAfter(collection, first.setId) : null;
  const setIds = [first, second].filter(Boolean).map((next) => next.setId);
  if (setIds.length === 0) return;
  fetch("/api/preload", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ setIds }),
  }).catch(() => {});
}

/**
 * State changes update counts immediately; the application chooses when to
 * rebuild the shelf. Playback and list editing retain their current nodes
 * until the dialog or picker closes, preserving the viewer's place.
 *
 * Startup renders once after catalog and profile selection are complete.
 */
let shelfStale = false;
let shelfEditing = false;
let pageReady = false;

function invalidateShelf() {
  refreshKept();
  if (player.open || shelfEditing) {
    shelfStale = true;
    return;
  }
  shelfStale = false;
  route();
}

state.subscribeChanges(() => {
  if (pageReady) invalidateShelf();
});

player.addEventListener("close", () => {
  pendingOpen++;
  if (shelfStale) invalidateShelf();
});
window.addEventListener("pagehide", () => { pendingOpen++; });

function setShelfEditing(editing) {
  shelfEditing = editing;
  if (!editing && shelfStale) invalidateShelf();
}

function refreshKept() {
  const started = state.inProgress().filter((row) => resumeAt(row) !== null && byId.has(row.setId));
  document.getElementById("n-continue").textContent = String(started.length);
  document.getElementById("n-watchlist").textContent = String(setsFor(state.watchlist()).length);
  document.getElementById("n-collections").textContent = String(state.collections().length);
  const kids = kidsShelf(library, setsFor(state.kids()));
  document.getElementById("n-kids").textContent = String(kids.films.length + kids.series.length + kids.byHand.length);
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

  heading(main, KEPT.continue.label, countOf(started.length, "title"));
  if (started.length === 0) return main.append(el("p", "empty", KEPT.continue.empty));
  main.append(setGrid(started, play));
}

/** Titles marked to come back to. */
function viewWatchlist() {
  const listed = setsFor(state.watchlist());
  heading(main, KEPT.watchlist.label, countOf(listed.length, "title"));
  if (listed.length === 0) return main.append(el("p", "empty", KEPT.watchlist.empty));
  main.append(setGrid(listed, play));
}

/**
 * What has been marked as a child's.
 *
 * Played as a run, the way a list is: a child handed a tablet should not have
 * to come back to the shelf between one film and the next.
 */
/**
 * What a child may watch: everything rated FSK 12 or younger, and anything
 * unrated someone marked by hand. The rules are `age-rating.js`'s.
 */
function viewKids() {
  const { films, series, byHand } = kidsShelf(library, setsFor(state.kids()));
  heading(main, KEPT.kids.label, countOf(films.length + series.length + byHand.length, "title"));
  if (films.length + series.length + byHand.length === 0) {
    return main.append(el("p", "empty", KEPT.kids.empty));
  }
  const parts = [
    [films, SECTIONS.movies.label, () => movieGrid(films, openFilm, { mode: GRID })],
    [
      series,
      SECTIONS.series.label,
      () =>
        collectionGrid("series", series, (title) => {
          location.hash = `#/series/${encodeURIComponent(title)}`;
        }, { mode: GRID }),
    ],
    [byHand, "Marked by hand", () => setGrid(byHand, (set) => play(set, byHand))],
  ].filter(([items]) => items.length > 0);
  for (const [, label, grid] of parts) {
    if (parts.length > 1) main.append(el("h2", "shelf-sub", label));
    main.append(grid());
  }
}

/** Asks the server, because summaries live there and are not in the catalog. */
async function viewSearch(query, generation) {
  main.append(el("p", "empty", "Searching…"));
  try {
    const response = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
    if (!response.ok) throw new Error(`the server answered ${response.status}`);
    const { hits } = await response.json();
    if (generation !== routeGeneration) return;
    main.textContent = "";
    renderSearch(main, query, hits, play);
  } catch (error) {
    if (generation !== routeGeneration) return;
    main.textContent = "";
    main.append(el("p", "error", `Search failed: ${error.message}`));
  }
}

/**
 * The catalog exactly as the server last sent it.
 *
 * Kept as text so a refresh can tell "the same again" from "something
 * arrived" with one comparison — including a title that finished caching,
 * whose only change is its offline badge.
 */
let catalogText = "";

/**
 * Reads the catalog and rebuilds the shelves' data from it.
 *
 * Answers whether anything changed, so a caller can leave the page alone when
 * nothing did: redrawing an unchanged shelf would throw away where the viewer
 * had scrolled to.
 */
async function loadCatalog() {
  const response = await fetch("/api/sets");
  if (!response.ok) throw new Error(`the catalog answered ${response.status}`);
  const text = await response.text();
  if (text === catalogText) return false;
  const sets = JSON.parse(text);
  const nextLibrary = groupLibrary(sets);
  const nextById = new Map(sets.map((set) => [set.setId, set]));
  library = nextLibrary;
  byId = nextById;
  catalogText = text;
  document.getElementById("n-movies").textContent = String(library.movies.length);
  document.getElementById("n-series").textContent = String(library.series.length);
  document.getElementById("n-tutorials").textContent = String(library.tutorials.length);
  renderColophon(sets);
  return true;
}

/** What the whole library adds up to, across the foot of the page. */
function renderColophon(sets) {
  document.getElementById("foot").textContent = colophonLine(sets, catalogOf());
}

/**
 * Shows the System entry, but only to a viewer `/api/status` will answer.
 *
 * A `HEAD` is enough to find out and costs nothing. A remote viewer gets a
 * 404 and the entry stays hidden — which is the point: a menu item leading to
 * a page they cannot open would advertise that the page is there.
 */
async function offerSystem() {
  try {
    const response = await fetch("/api/status", { method: "HEAD" });
    if (!response.ok) return;
  } catch {
    return;
  }
  document.getElementById("nav-system").hidden = false;
}

/**
 * Stops the status panel polling, if one is open.
 *
 * Held here rather than inside the view because only the router knows the
 * panel has been left: `hashchange` is the event, and the panel cannot see it.
 */
let stopStatus = null;

/**
 * What this player is doing. Local viewers only — `/api/status` is a 404
 * from anywhere else, and the poller renders that as the error it is.
 */
function viewSystem() {
  heading(main, "System", "What this player is doing, refreshed as it happens");
  const panel = el("div", "status-panel");
  main.append(panel);
  stopStatus = watchStatus(panel);
}

let routeGeneration = 0;
function route() {
  const generation = ++routeGeneration;
  shelfEditing = false;
  shelfStale = false;
  // A panel left polling after the viewer has gone is the failure mode of
  // every panel like this. Stopped on the way out of *any* route, so there is
  // one place it can happen rather than one per way of leaving.
  if (stopStatus) {
    stopStatus();
    stopStatus = null;
  }

  // Everything after the collection is the trail of folders into a course.
  // Each segment is encoded on the way out, so a folder whose name contains a
  // slash survives the split rather than becoming two folders.
  const parts = location.hash
    .replace(/^#\/?/, "")
    .split("/")
    .filter((part) => part !== "");
  const [section = "movies", name, ...folders] = parts;
  const known = SECTIONS[section] || KEPT[section] || PAGES.has(section) ? section : "movies";

  for (const link of document.querySelectorAll("nav a")) {
    link.classList.toggle("active", link.dataset.section === known);
  }

  main.textContent = "";
  refreshKept();
  if (known === "search") {
    const query = decodeURIComponent(name ?? "");
    searchBox.value = query;
    void viewSearch(query, generation);
    return;
  }
  // Leaving a search clears the box, so the shelf and the field agree.
  if (searchBox.value !== "") searchBox.value = "";

  if (known === "home") return viewHome();
  if (known === "film") return viewFilm(decodeURIComponent(name ?? ""));
  if (known === "genre") return viewGenre(decodeURIComponent(name ?? ""));
  if (known === "system") return viewSystem();
  if (known === "continue") return viewContinue();
  if (known === "watchlist") return viewWatchlist();
  if (known === "kids") return viewKids();
  if (known === "collections") {
    const list = state.collections().find((entry) => entry.id === decodeURIComponent(name ?? ""));
    return name
      ? renderList(main, list, list ? setsFor(list.items) : [], {
          play, onEditing: setShelfEditing, onGone: () => { location.hash = "#/collections"; },
        })
      : renderLists(main, (id) => { location.hash = `#/collections/${encodeURIComponent(id)}`; });
  }

  if (name) {
    const decoded = decodeURIComponent(name);
    renderCollection(main, known, library[known].find((entry) => entry.name === decoded), decoded,
      folders.map(decodeURIComponent), { play, open: (section, collection, path) => {
        location.hash = `#/${section}/${[collection, ...path].map(encodeURIComponent).join("/")}`;
      } });
  }
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

/**
 * Turns the page when the viewer goes somewhere, and only then.
 *
 * A catalog refresh redraws through `route()` directly and stays still: the
 * viewer did not move, so the page should not either. Typing a search moves
 * the hash every pause, and animating each one would make the results swim
 * under the cursor. A browser without view transitions, or a viewer who asked
 * for less motion, gets the plain swap.
 */
const lessMotion = matchMedia("(prefers-reduced-motion: reduce)");
let shownHash = location.hash;
window.addEventListener("hashchange", () => {
  const refining = shownHash.startsWith("#/search/") && location.hash.startsWith("#/search/");
  shownHash = location.hash;
  if (refining || lessMotion.matches || !document.startViewTransition) {
    route();
    return;
  }
  // Not `startViewTransition(route)`: a view that returns a promise would
  // hold the old page frozen until it settled.
  document.startViewTransition(() => {
    route();
  });
});

/**
 * Reads the catalog again and redraws what changed, or defers the redraw
 * while a title plays — rebuilding the shelf behind the dialog would lose the
 * viewer's place for a change they cannot see yet.
 *
 * One read at a time, but never a notice dropped: one that arrives while a
 * read is running may be about a catalog that read has already missed, so it
 * earns exactly one more.
 */
let refreshing = false;
let askedAgain = false;
async function refreshCatalog() {
  // Not before the first load has finished: that one draws the page itself.
  if (catalogText === "") return;
  if (refreshing) {
    askedAgain = true;
    return;
  }
  refreshing = true;
  try {
    do {
      askedAgain = false;
      await readCatalogOnce();
    } while (askedAgain);
  } finally {
    refreshing = false;
  }
}

async function readCatalogOnce() {
  try {
    if (!(await loadCatalog())) return;
    // The colophon says how old the catalogue is, and that just changed.
    await loadLink();
    renderColophon(JSON.parse(catalogText));
    invalidateShelf();
  } catch {
    // A server that is restarting answers nothing for a moment. The shelves
    // already showing are still true, and the next event asks again.
  }
}

/**
 * The server says when the library changes; the page never polls for it.
 *
 * It hears a new index from Telegram the moment it is pinned and tells every
 * open page here. Every (re)connect is treated as news too — the server
 * restarted, the laptop woke up — because an event sent while the page was
 * away is not sent again. `EventSource` reconnects by itself.
 *
 * Open only while the tab is visible. Without HTTP/2 a browser allows about
 * six connections to one host, and a stream held by every background tab
 * would leave the tab being watched none for its video. A tab brought back
 * opens its stream again, and that open reads the catalog.
 */
let libraryEvents = null;
function listenForLibrary() {
  if (libraryEvents !== null) return;
  libraryEvents = new EventSource("/api/events");
  libraryEvents.addEventListener("catalog", () => void refreshCatalog());
  libraryEvents.addEventListener("open", () => void refreshCatalog());
}
function stopListeningForLibrary() {
  libraryEvents?.close();
  libraryEvents = null;
}
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") {
    listenForLibrary();
    // State saved on another device while this tab was away follows the same
    // notification and deferred-redraw path as a local mutation.
    void state.refreshState();
  } else stopListeningForLibrary();
});
if (document.visibilityState === "visible") listenForLibrary();

try {
  // Asked for first: every shelf badge depends on whether this page is being
  // watched from the sofa or from somewhere with an uplink in between.
  await loadLink();
  // Shared by everyone on this player, so it is read once rather than per
  // profile — see `loadKids`.
  await state.loadKids();
  // Who, before anything else: every shelf below is one profile's, and the
  // first render already draws progress rules.
  await Promise.all([loadCatalog(), state.loadProfiles()]);

  // Remembered on this device, if that profile is still one of them; asked
  // otherwise, which is also the first run on a new player.
  const known = state.rememberedProfile();
  if (known) await state.useProfile(known);
  else await chooseProfile(document.body);
  showProfile();

  void offerSystem();
  refreshKept();

  // The start page, which answers both halves of what used to be decided
  // here: what was left unfinished, and — for a viewer who finished
  // everything — what has arrived since.
  if (!location.hash) location.hash = "#/home";
  pageReady = true;
  route();
} catch (error) {
  main.textContent = "";
  main.append(el("p", "error", `Could not load the catalog: ${error.message}`));
}
