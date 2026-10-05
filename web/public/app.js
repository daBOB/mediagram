/**
 * The library page: three shelves, a drill-down, and a player.
 *
 * Talks only to this server's own API. It never learns which channel or
 * message a set's bytes live in, which is the point of keeping those on the
 * server side.
 *
 * Routing is the URL hash, so the back button works and a view can be linked:
 *   #/movies  #/movies/page/2   #/series                #/tutorials
 *   #/series/Widow%27s%20Bay     #/tutorials/Geldhochschule
 */

import { el } from "./lib/dom.js";
import { countOf } from "./lib/format.js";
import { renderSearch } from "./lib/catalog/search-view.js";
import { countDocumentaries } from "./lib/documentaries.js";
import { catalogOf, loadLink } from "./lib/link.js";
import { colophonLine } from "./lib/colophon.js";
import { watchStatus } from "./lib/status/status-view.js";
import { viewSettings as renderSettingsPage } from "./lib/settings-view.js";
import { probeSettings } from "./lib/settings-api.js";
import { renderCollection } from "./lib/catalog/course-view.js";
import { SECTIONS, emptyState, heading, movieGrid, setGrid } from "./lib/catalog/shelf-view.js";
import { shelfMode, shelfToggle } from "./lib/catalog/shelf-mode.js";
import { pageOf, pager } from "./lib/catalog/pager.js";
import { pickFeatured } from "./lib/catalog/featured-picks.js";
import { openFeatured } from "./lib/catalog/featured-reel.js";
import * as state from "./lib/watch-state.js";
import { kidsLimitOf } from "./lib/age-rating.js";
import { resumeAt } from "./lib/resume-point.js";
import { renderList } from "./lib/catalog/collections-view.js";
import { franchisesIn, renderCollectionsPage, renderFranchise } from "./lib/catalog/collections-page.js";
import { chooseProfile, initialOf } from "./lib/profile-picker.js";
import { homeShelves } from "./lib/catalog/home-shelves.js";
import { renderHome } from "./lib/catalog/home-view.js";
import { homeEditorial } from "./lib/catalog/editorial-picks.js";
import { editorsChoice, loadEditorsChoice, onEditorsChoice } from "./lib/editors-choice.js";
import { describeFilm, filmPage } from "./lib/catalog/film-page.js";
import { renderSeries } from "./lib/catalog/series-page.js";
import { renderDocumentariesDept, renderMoviesDept, renderShowsDept } from "./lib/catalog/department-pages.js";
import { renderAnimeDept } from "./lib/catalog/anime-department.js";
import { countAnime, everyFilm, everyShow, groupDepartments, sectionForShow } from "./lib/departments.js";
import { renderSettings } from "./lib/catalog/settings-page.js";
import { renderPerson, titlesByKey, visiblePeople } from "./lib/catalog/cast.js";
import { similarTo } from "./lib/catalog/similar.js";
import { drawAt } from "./lib/redraw.js";
import { turnPage } from "./lib/page-turn.js";
import { renderGenre, renderGenres, renderLatest } from "./lib/catalog/utility-pages.js";
import { renderStats } from "./lib/catalog/stats-page.js";
import { watchStatsDot } from "./lib/catalog/stats-dot.js";
import { createLibrarySession } from "./lib/library-session.js";
import { browserLibraryPort } from "./lib/library-session-port.js";
import { go, href, parse, sectionOf } from "./lib/address.js";
import { markCurrent } from "./lib/nav-current.js";
import { collectionOf, playsNext, requestPreload } from "./lib/playback/plays-next.js";
import { loadPlayer } from "./lib/playback/player-loader.js";

const main = document.getElementById("main");
const player = document.getElementById("player");
const searchBox = document.getElementById("search");
// A fragment link would be consumed as an application route. Move focus
// directly so keyboard users can skip navigation without leaving their shelf.
document.getElementById("skip-library")?.addEventListener("click", () => main.focus());

/**
 * The catalog this profile currently sees, and every set by id within it.
 *
 * Mirror `librarySession.current()`, kept in step by its `onData`
 * notification below — almost every view here reads both, so they are read
 * as plain locals rather than through the session on every call. Ids rather
 * than titles in `byId` — a position is recorded against a set, not against
 * a copy of one — so turning a list of ids back into things to show needs
 * one lookup rather than a search of three shelves.
 * @type {import("./lib/library.js").Library}
 */
let library = groupDepartments([]);
let byId = new Map();

/**
 * The catalog this profile sees, kept current — fetch, diff, filter, the
 * update stream, and holding a redraw back while the player or a list
 * picker is open all live in `library-session.js`; see its module doc.
 */
const librarySession = createLibrarySession({
  port: browserLibraryPort(),
  state,
  // Another device's positions or marks, pulled by the server, on the same
  // notification and deferred-redraw path as a local mutation.
  remoteState: () => {
    void state.refreshState();
    void loadEditorsChoice();
  },
});

// Counts and the colophon reach the page at once, even while a redraw waits;
// safe to subscribe before a profile is chosen — `librarySession.start()`'s
// own commit fires this once, against the whole catalog, and `reapply()`
// fires it again moments later against whoever was actually chosen.
librarySession.onData((change) => {
  ({ library, byId } = librarySession.current());
  document.getElementById("n-movies").textContent = String(library.movies.length);
  document.getElementById("n-series").textContent = String(library.series.length);
  document.getElementById("n-tutorials").textContent = String(library.tutorials.length);
  document.getElementById("n-documentaries").textContent = String(countDocumentaries(library.documentaries));
  const animeCount = countAnime(library.anime);
  document.getElementById("n-anime").textContent = String(animeCount);
  document.getElementById("nav-anime").hidden = animeCount === 0;
  document.getElementById("rail-masthead").textContent = ["movies", "series", "tutorials"]
    .map((section) => countOf(library[section].length, SECTIONS[section].extent))
    .join("\n");
  renderColophon(librarySession.current().visibleSets);
  // A new catalogue has a new publish date, which the colophon states and the
  // link's answer carries: asked again for that, not for every state change.
  if (change.catalog) void loadLink().then(() => renderColophon(librarySession.current().visibleSets));
  // Reaches this at once too, even with the player open — a count is owed now.
  refreshShelfCounts();
});

/** The shelves that come from what has been watched rather than the catalog. */
const KEPT = {
  continue: { label: "Continue", empty: "Nothing started yet." },
  watchlist: { label: "My List", empty: "Nothing on your list." },
};

/** Ids to sets, quietly dropping any the catalog no longer holds. */
const setsFor = (ids) => ids.map((id) => byId.get(id)).filter(Boolean);

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

  // The rows only: `totals` is an object of counts, not a row, and reading
  // its `length` would call a library with nothing in it non-empty.
  const empty = Object.values(shelves).every((row) => !Array.isArray(row) || row.length === 0);
  if (empty) {
    heading(main, SECTIONS.movies.label, countOf(0, SECTIONS.movies.extent));
    return main.append(emptyState("movies", { kidsLimit: kidsLimitOf(state.profile()) }));
  }

  const editorial = homeEditorial({
    movies: library.movies,
    byId,
    isWatched: state.isWatched,
    editorsChoice: editorsChoice(),
    now: Date.now(),
    onRow: new Set(shelves.latestMovies.map((set) => set.setId)),
  });
  renderHome(main, shelves, editorial, {
    play: (set) => play(set),
    openFilm,
    open: openShow,
  });
}

/**
 * Opens a show or course's own page — the one show-opener every caller
 * shares; `sectionForShow` resolves a name onto Anime when it lives there.
 */
function openShow(section, name, folders = []) {
  go({ page: "show", section: sectionForShow(library, section, name), name, folders });
}

// A multiple of two, three, four, six and eight, so a wall of plates ends
// on a full row at any width.
const FILMS_PER_PAGE = 48;

/** Films: a flat grid, since a film is one thing, one page of it at a time. */
function viewMovies(requested) {
  const mode = shelfMode();
  const { items, page, pages } = pageOf(library.movies, requested, FILMS_PER_PAGE);
  const extent = countOf(library.movies.length, SECTIONS.movies.extent);
  heading(main,
    SECTIONS.movies.label,
    pages > 1 ? `${extent} \u00b7 page ${page} of ${pages}` : extent,
    // No control over an empty shelf: there is nothing to lay out either way,
    // and offering the choice would be offering it about nothing.
    library.movies.length > 0 ? movieControls() : null,
  );
  if (library.movies.length === 0) return main.append(emptyState("movies", { kidsLimit: kidsLimitOf(state.profile()) }));
  main.append(movieGrid(items, openFilm, { mode }));
  const links = pager(page, pages);
  if (links) main.append(links);
}

/**
 * The Movies heading's controls: the Featured reel, when there is a film
 * left to suggest, beside the list-or-grid choice.
 */
function movieControls() {
  const controls = el("div", "shelf-modes");
  const featured = reelButton();
  if (featured) controls.append(featured);
  controls.append(shelfToggle(route));
  return controls;
}

/** The Featured reel's button, when there is a film left to suggest. */
function reelButton() {
  const picks = () => pickFeatured(library.movies, state.isWatched, Math.random);
  if (picks().length === 0) return null;
  const featured = el("button", "mode featured-open", "Featured");
  featured.type = "button";
  featured.addEventListener("click", () => openFeatured(picks(), { play: (set) => play(set), openFilm }));
  return featured;
}

/** What a department's front page draws from. */
const deptContext = () => ({
  library, byId, progress: state.inProgress(), watchedAt: state.watchedAt, isWatched: state.isWatched,
  kidsLimit: kidsLimitOf(state.profile()), play: (set) => play(set), openFilm, reel: reelButton(),
  openShow,
});

/** A film's card opens its page; the page's button plays it. */
function openFilm(set) {
  go({ page: "film", setId: set.setId });
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
  const page = filmPage(set, {
    resume: resumeAt(state.progressOf(set.setId)),
    onPlay: (film) => play(film),
    similar: () => similarTo(set, everyFilm(library), (film) => state.isWatched(film.setId)),
    openFilm,
    hasFranchise: franchisesIn(everyFilm(library)).some((franchise) => franchise.id === set.collectionId),
  });
  main.append(page);
  if (set.showKey) {
    fetch(`/api/shows/${encodeURIComponent(set.showKey)}`)
      .then((res) => (res.ok ? res.json() : null))
      .then((meta) => describeFilm(page, meta))
      .catch(() => {});
  }
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
 *
 * Opens without waiting for either the player's own modules or a fresh read
 * of this profile's state — the dialog would otherwise sit blank for a round
 * trip. The state read still happens, and lands its position on the title
 * that asked for it, for as long as that is still the latest request and
 * playback has not really moved on; see `player.js`'s `RESUME_DRIFT_SECONDS`.
 */
function play(set, queue = null, options = {}) {
  const request = ++pendingOpen;
  // This tab may have been open while the same viewer watched further on
  // another device. Started here, not awaited: a superseded request resolves
  // to `null` rather than a stale position.
  const freshResume = state.refreshState().then(() =>
    request === pendingOpen ? resumeAt(state.progressOf(set.setId)) : null,
  );
  void openTitle(set, queue, { ...options, freshResume }, request);
}

async function openTitle(set, queue, options, request) {
  // Warmed in the background since the first shelf drew; a click that beat
  // it here simply waits its turn, and one superseded before it arrives
  // never opens at all.
  let openPlayer;
  try {
    ({ openPlayer } = await loadPlayer());
  } catch (error) {
    if (request === pendingOpen) main.append(el("p", "error", `Could not start the player: ${error.message}`));
    return;
  }
  if (request !== pendingOpen) return;

  // `autoplay` is set only by the player handing over to what follows, and
  // says which kind of start that is. Opening a title from a shelf never
  // carries one, so it loads and waits for the viewer as it always has.
  const autoplay = options.autoplay ?? null;
  const { freshResume } = options;

  const { next, previous, inRun, preload } = playsNext(library, set, queue);
  requestPreload(preload);
  openPlayer(set, {
    next, previous, inRun, collection: queue ? null : collectionOf(library, set),
    onOpenNext: (following, how) => play(following, queue, how),
    autoplay,
    freshResume,
  });
}

/**
 * State changes update counts immediately; `librarySession` decides when the
 * shelf itself may rebuild, held back while the player or a list picker is
 * open — see `library-session.js`'s `hold()`/`onRedraw()`. Subscribed here
 * regardless of startup's own progress: nothing reaches `route()` before
 * `librarySession.onRedraw` is itself subscribed, near the end of startup.
 */
function onShelfAffectingChange() {
  refreshShelfCounts();
  librarySession.invalidate();
}
state.subscribeChanges(onShelfAffectingChange);
watchStatsDot(state);
// The pin lives outside watch state (it belongs to no profile), but a change
// to it is redrawn the same way, deferred while a title plays.
onEditorsChoice(onShelfAffectingChange);

/**
 * Holds a redraw back for as long as the player is open.
 *
 * There is no event for a `<dialog>` opening itself, only for its closing, so
 * this is discovered reactively rather than armed on open: the redraw handler
 * below calls this the first time a change arrives while `player.open` is
 * true, which is also the first moment there is anything to hold back for.
 * The close listener releases it.
 */
let playerHeld = null;
function ensurePlayerHold() {
  if (player.open && !playerHeld) playerHeld = librarySession.hold();
}

player.addEventListener("close", () => {
  pendingOpen++;
  playerHeld?.();
  playerHeld = null;
});
window.addEventListener("pagehide", () => { pendingOpen++; });

/** A list picker's own hold, for as long as it is adding titles. */
let editHeld = null;
function setShelfEditing(editing) {
  if (editing) editHeld ??= librarySession.hold();
  else {
    editHeld?.();
    editHeld = null;
  }
}

function refreshShelfCounts() {
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
  // Drawn as an initial in a circle; the name stays the button's text.
  button.dataset.initial = current ? initialOf(current.name) : "?";
  button.hidden = false;
}

// The header's name opens the picker: to become somebody else (a grown-up's PIN
// is asked there) or to manage profiles — which can change this very profile too.
async function switchProfile() {
  const before = JSON.stringify(state.profile());
  await chooseProfile(document.body, { canCancel: true });
  if (JSON.stringify(state.profile()) === before) return;
  showProfile();
  // Counts follow from the reapply itself, through `onData`.
  librarySession.reapply();
  route();
}
document.getElementById("who").addEventListener("click", switchProfile);

/** What was started and not finished, most recent first. */
function viewContinue() {
  const started = state
    .inProgress()
    .filter((row) => resumeAt(row) !== null)
    .map((row) => byId.get(row.setId))
    .filter(Boolean);

  heading(main, KEPT.continue.label, countOf(started.length, "title"));
  if (started.length === 0) return main.append(el("p", "empty", KEPT.continue.empty));
  // The shelf redraws itself from the change, so the title simply leaves it.
  main.append(setGrid(started, play, { finish: (set) => state.markFinished(set.setId) }));
}

/** Titles marked to come back to. */
function viewWatchlist() {
  const listed = setsFor(state.watchlist());
  heading(main, KEPT.watchlist.label, countOf(listed.length, "title"));
  if (listed.length === 0) return main.append(el("p", "empty", KEPT.watchlist.empty));
  main.append(setGrid(listed, play));
}

/** Asks the server, because summaries live there and are not in the catalog. */
async function viewSearch(query, generation) {
  main.append(el("p", "empty", "Searching…"));
  try {
    const response = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
    if (!response.ok) throw new Error(`the server answered ${response.status}`);
    const { hits, people } = await response.json();
    if (generation !== routeGeneration) return;
    main.textContent = "";
    // The server's search knows no profile; keep only what this one can see.
    renderSearch(main, query, hits.filter((hit) => byId.has(hit.setId)), {
      ...deptContext(), shows: library.series, animeShows: library.anime.collections,
      people: visiblePeople(people ?? [], titlesByKey(library)), franchises: franchisesIn(everyFilm(library)), lists: state.collections(),
    });
  } catch (error) {
    if (generation !== routeGeneration) return;
    main.textContent = "";
    main.append(el("p", "error", `Search failed: ${error.message}`));
  }
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
 * Shows the Settings entry to a viewer `/api/settings` will answer at all —
 * 200 unlocked, 401 locked, either way there is a page to open. 404 (off
 * this household's network) hides it, the same rule `offerSystem` follows.
 */
async function offerSettings() {
  try {
    const response = await probeSettings();
    if (response.status !== 200 && response.status !== 401) return;
  } catch {
    return;
  }
  adminSettings = true;
  // Settings may already be open: redraw so the tab appears.
  if (location.hash.startsWith(href({ page: "settings" }))) route();
}
/** Whether this viewer is offered the admin tab; set by `offerSettings`. */
let adminSettings = false;

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

/** Stops the Settings page's async work, if any is in flight. */
let stopSettings = null;

// A route visit survives redraws, but not leaving and returning to its hash.
let navigationGeneration = 0;
let routeGeneration = 0;
function route() { drawAt(main, location.hash, drawRoute); } // redraws keep images: redraw.js
function drawRoute() {
  const navigation = navigationGeneration;
  const visitedHash = location.hash;
  const generation = ++routeGeneration;
  // Settled before a hold releases below — releasing first would let an owed
  // redraw fire back in through `onRedraw`, re-entering this very draw.
  librarySession.drawn();
  // A list-editing session left open when the viewer navigates away by
  // another route: this draw is the redraw its hold was withholding, so the
  // hold ends here rather than sitting open for the rest of the session.
  if (editHeld) {
    const release = editHeld;
    editHeld = null;
    release();
  }
  // A panel left polling after the viewer has gone is the failure mode of
  // every panel like this. Stopped on the way out of *any* route, so there is
  // one place it can happen rather than one per way of leaving.
  if (stopStatus) {
    stopStatus();
    stopStatus = null;
  }
  if (stopSettings) {
    stopSettings();
    stopSettings = null;
  }

  const address = parse(location.hash);
  const known = sectionOf(address);

  // Which page is showing, for the stylesheet: the home page's masthead sits
  // dark over the cover before it settles into the page's own colour.
  document.body.dataset.page = known;
  // Set again by the home view only when it draws a cover.
  delete document.body.dataset.cover;
  markCurrent(document.querySelectorAll("nav a"), known);

  main.textContent = "";
  refreshShelfCounts();
  if (address.page === "search") {
    searchBox.value = address.query;
    void viewSearch(address.query, generation);
    return;
  }
  // Leaving a search clears the box, so the shelf and the field agree.
  if (searchBox.value !== "") searchBox.value = "";

  if (address.page === "home") return viewHome();
  if (address.page === "film") return viewFilm(address.setId);
  const openers = { openFilm, openShow };
  if (address.page === "genre") return renderGenre(main, library, address.name, openers);
  if (address.page === "genres") return renderGenres(main, library);
  if (address.page === "latest") return renderLatest(main, library, byId, openers);
  if (address.page === "person") return renderPerson(main, address.id, { ...openers, byKey: titlesByKey(library) }, () => generation === routeGeneration);
  if (address.page === "settings") {
    stopSettings = renderSettings(main, {
      profile: state.profile(), switchProfile, systemVisible: !document.getElementById("nav-system").hidden,
      admin: adminSettings ? renderSettingsPage : null,
    });
    return;
  }
  if (address.page === "stats") return renderStats(main, { byId }, () => generation === routeGeneration);
  if (address.page === "system") return viewSystem();
  if (address.page === "continue") return viewContinue();
  if (address.page === "watchlist") return viewWatchlist();
  if (address.page === "franchise") return renderFranchise(main, address.id, { library, openFilm }, () => generation === routeGeneration);
  if (address.page === "list") {
    const list = state.collections().find((entry) => entry.id === address.id);
    return renderList(main, list, list ? setsFor(list.items) : [], {
      play, onEditing: setShelfEditing, onGone: () => {
        if (navigation === navigationGeneration && location.hash === visitedHash) go({ page: "collections" });
      },
    });
  }
  if (address.page === "collections") return renderCollectionsPage(main, { library, lists: state.collections(), setsFor });

  if (address.page === "moviesPage") return viewMovies(address.n);
  if (address.page === "department" && address.section === "movies") return renderMoviesDept(main, deptContext());
  if (address.page === "department" && address.section === "documentaries") return renderDocumentariesDept(main, deptContext());
  if (address.page === "department" && address.section === "anime") return renderAnimeDept(main, deptContext());
  if (address.page === "department") return renderShowsDept(main, address.section, deptContext());

  // address.page === "show"
  const collections = { documentaries: library.documentaries.collections, anime: library.anime.collections }[address.section]
    ?? library[address.section];
  (address.section === "series" || address.section === "anime" ? renderSeries : renderCollection)(
    main, address.section, collections.find((entry) => entry.name === address.name), address.name, address.folders,
    { play, pool: everyShow(library), open: openShow },
  );
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
      go({ page: "department", section: "movies" });
      return;
    }
    go({ page: "search", query });
  }, 200);
});

/**
 * Turns the page when the viewer goes somewhere, and only then.
 *
 * A catalog refresh redraws through `route()` directly and stays still: the
 * viewer did not move, so the page should not either. Typing a search moves
 * the hash every pause, and animating each one would make the results swim
 * under the cursor.
 */
let shownHash = location.hash;
window.addEventListener("hashchange", () => {
  navigationGeneration++;
  const refining = [shownHash, location.hash].every((hash) => hash.startsWith(href({ page: "search", query: "" })));
  shownHash = location.hash;
  turnPage(main, route, refining);
});

try {
  // None of these depends on another: the link, the kids marks, the editor's
  // choice and the catalog are all facts about the library itself, and the
  // profile list is asked here only to learn who there is to choose from.
  // Only choosing one waits on that list; everything else runs alongside it.
  const [, , , , profilesLoaded] = await Promise.all([
    loadLink(),
    state.loadKids(),
    loadEditorsChoice(),
    librarySession.start(),
    state.loadProfiles(),
  ]);

  // Remembered on this device, if that profile is still one of them; asked
  // otherwise, which is also the first run on a new player.
  const known = profilesLoaded ? state.rememberedProfile() : null;
  const selected = known ? await state.useProfile(known) : false;
  if (!selected) await chooseProfile(document.body, {
    discoveryFailed: !profilesLoaded,
    stateFailed: known !== null,
  });
  showProfile();
  // Read before anyone was chosen; a kids profile sees less of it. Counts
  // follow from the reapply itself, through `onData`.
  librarySession.reapply();

  void offerSystem();
  void offerSettings();

  // The start page, which answers both halves of what used to be decided
  // here: what was left unfinished, and — for a viewer who finished
  // everything — what has arrived since.
  //
  // Replaced, not assigned: assigning fires `hashchange`, which would draw the
  // page twice, the second rising in again, which reads as a flicker.
  if (!location.hash) {
    history.replaceState(history.state, "", href({ page: "home" }));
    shownHash = location.hash;
  }
  // Only from here does a redraw-worthy change actually redraw: a state or
  // editor's-choice notification arriving during the work above (loading the
  // Kids marks, choosing a remembered profile) had nothing yet to reach.
  librarySession.onRedraw(() => {
    // A title is still open: take the hold this is the first sign of, and
    // leave the redraw owed rather than losing the viewer's place under it.
    if (player.open) { ensurePlayerHold(); librarySession.invalidate(); return; }
    route();
  });
  // Kicked off once the first shelf is about to draw, not awaited: Play
  // usually finds it already there by the time anyone presses it. A failure
  // here is reported when Play actually asks for it, not against a page the
  // viewer has not touched yet.
  void loadPlayer().catch(() => {});
  route();
} catch (error) {
  main.textContent = "";
  main.append(el("p", "error", `Could not load the catalog: ${error.message}`));
}
