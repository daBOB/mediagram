/**
 * The catalog this profile sees, kept current.
 *
 * Everything a shelf, search, reel or Play next reads about the library goes
 * through the snapshot this returns from `current()`: the last body the
 * server sent, filtered for whoever is watching, refreshed the moment the
 * server says it changed. A redraw is held back while the player or a list
 * picker is open — rebuilding the shelf behind either would lose the
 * viewer's place for a change they cannot see yet — but the counts and the
 * colophon are not; `onData` reaches them at once, `onRedraw` waits.
 *
 * DOM-free: everything the outside world touches is behind `port`, so this
 * is tested without a browser. The one guarantee `loadCatalog` always made
 * survives the move unchanged — nothing is recorded until `groupDepartments`
 * has made sense of a body, so one it cannot parse leaves the current
 * catalog exactly as it was.
 */

import { groupDepartments } from "./departments.js";
import { forKidsProfile, kidsLimitOf } from "./age-rating.js";

/**
 * @param {{ port: import("./library-session.js").LibraryPort,
 *   state: import("./library-session.js").LibrarySessionState,
 *   remoteState: () => void }} deps
 * @returns {import("./library-session.js").LibrarySession}
 */
export function createLibrarySession({ port, state, remoteState }) {
  /**
   * The catalog exactly as the server last sent it, kept as text so a
   * refresh can tell "the same again" from "something arrived" with one
   * comparison — including a title that finished caching, whose only change
   * is its offline badge.
   */
  let catalogText = "";
  /** The catalog as the server last sent it, before any profile's filter. */
  let catalogSets = [];

  let library = groupDepartments([]);
  let byId = new Map();
  let visibleSets = [];

  const dataListeners = new Set();
  const redrawListeners = new Set();
  /** How many things are holding a redraw back right now: the player and a
   *  list picker each hold one, for as long as they are open. */
  let holds = 0;
  /** Whether a redraw arrived while held, for the last `release()` to deliver. */
  let redrawDue = false;

  let refreshing = false;
  let askedAgain = false;
  let stopStream = null;

  /** What the chosen profile may see of a catalog; a kids profile is a filter at its own limit, nothing else. */
  const filtered = (sets, limit = kidsLimitOf(state.profile())) =>
    (limit === null ? sets : forKidsProfile(sets, state.kidsMarks(), limit));

  function notify(listeners, what, change) {
    for (const listener of listeners) {
      try { listener(change); }
      catch (error) { console.error(`Could not ${what} a library-session view`, error); }
    }
  }

  /** Fires a redraw now, or leaves it owed to whichever hold releases last. */
  function requestRedraw() {
    if (holds > 0) { redrawDue = true; return; }
    redrawDue = false;
    notify(redrawListeners, "redraw");
  }

  /**
   * Builds the library and counts the current profile sees from a catalog.
   *
   * The one place a kids profile's filter is applied — every shelf, search,
   * reel and Play next reads `current()`, so none can miss it. Cheap enough
   * to run on every profile change, which is what lets `reapply()` skip a
   * second download. Takes the sets explicitly, defaulting to what is
   * already committed, so `loadCatalog` can build against a freshly parsed
   * body and let a construction failure throw before that body is recorded.
   */
  function applyCatalog(sets = catalogSets, catalog = false) {
    const visible = filtered(sets);
    library = groupDepartments(visible);
    byId = new Map(visible.map((set) => [set.setId, set]));
    visibleSets = visible;
    // Said, so a view that states something about the catalogue itself (the
    // colophon's publish date) can ask again only when there is a new one.
    notify(dataListeners, "update", { catalog });
  }

  /**
   * Reads the catalog and rebuilds the committed data from it.
   * @returns {Promise<boolean>} Whether anything changed.
   */
  async function loadCatalog() {
    const text = await port.fetchCatalog();
    if (text === catalogText) return false;
    const sets = JSON.parse(text);
    // A live refresh replaces a catalog already showing; start()'s first read has none to.
    applyCatalog(sets, catalogText !== "");
    catalogSets = sets;
    catalogText = text;
    return true;
  }

  async function readCatalogOnce() {
    try {
      if (await loadCatalog()) requestRedraw();
    } catch {
      // A server that is restarting answers nothing for a moment. The
      // shelves already showing are still true, and the next event asks again.
    }
  }

  /**
   * One read at a time, but never a notice dropped — once the first load has
   * committed; one arriving before it finishes is dropped on purpose, since
   * that load already draws the page with whatever the notice would ask for.
   */
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

  function openStream() {
    if (stopStream) return;
    stopStream = port.listen({
      catalog: () => void refreshCatalog(),
      open: () => void refreshCatalog(),
      // Another device's positions or marks, pulled by the server.
      state: () => remoteState(),
    });
  }
  function closeStream() {
    stopStream?.();
    stopStream = null;
  }

  // Open only while the tab is visible: without HTTP/2 a browser allows about
  // six connections to one host, and a stream held by every background tab
  // would leave the tab being watched none for its video. A tab brought back
  // opens its stream again, and that open reads the catalog through `open`.
  port.onVisibility(() => {
    if (port.visible()) {
      openStream();
      // State saved on another device while this tab was away follows the
      // same notification path as a local mutation.
      remoteState();
    } else closeStream();
  });
  if (port.visible()) openStream();

  return {
    // The first catalog read; rejecting is left to the caller, whose startup
    // shows it as the reason nothing loaded.
    start: () => loadCatalog(),
    current: () => ({ library, byId, visibleSets }),
    // Profile changed: re-filter the committed catalog, no fetch.
    reapply: () => applyCatalog(catalogSets),
    // Something the shelves show changed outside the catalog itself.
    invalidate: () => requestRedraw(),
    // Clears an owed redraw without firing it — the caller's own draw covers it.
    drawn: () => { redrawDue = false; },
    onData(fn) {
      dataListeners.add(fn);
      return () => dataListeners.delete(fn);
    },
    onRedraw(fn) {
      redrawListeners.add(fn);
      return () => redrawListeners.delete(fn);
    },
    hold() {
      holds++;
      let released = false;
      return () => {
        if (released) return;
        released = true;
        if (--holds === 0 && redrawDue) requestRedraw();
      };
    },
  };
}
