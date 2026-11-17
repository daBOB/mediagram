/**
 * The page's view of films held more than once: which film a stored copy
 * belongs to, where each copy starts, what counts as watched, and the
 * question asked when a card would play one straight away.
 *
 * Follows the catalog the profile sees, rebuilding its index whenever that
 * library is replaced, so it always agrees with the shelves it was grouped
 * from; `app.js` reads it wherever a set id becomes a title.
 */

import { asFilms, continueRows, filmIndex, filmPlayed, versionPosition } from "./film-versions.js";
import { everyFilm } from "./departments.js";
import { resumeAt } from "./resume-point.js";
import { chooseVersion } from "./catalog/film-versions-view.js";

/**
 * @param {typeof import("./watch-state.js")} state
 * @param {() => { library: import("./library.js").Library, byId: Map<string, any> }} current
 *   the catalog this profile sees, as `library-session.js` holds it
 */
export function filmSession(state, current) {
  let indexed = null;
  let bySet = new Map();

  /** The film `setId` is a copy of, or `null` for anything that is not a film. */
  function filmOf(setId) {
    const { library } = current();
    if (library !== indexed) {
      bySet = filmIndex(everyFilm(library));
      indexed = library;
    }
    return bySet.get(setId) ?? null;
  }

  /** Where `set` starts from: copies of one cut share a place, anything else keeps its own. */
  function positionFor(set) {
    const film = filmOf(set.setId);
    return film?.versions ? versionPosition(set, film, state.progressOf, state.watchedAt) : state.progressOf(set.setId);
  }

  /** How each copy reads in a Versions list and in the chooser. */
  const versionState = {
    resumeOf: (version) => resumeAt(positionFor(version)),
    played: (version) => state.isWatched(version.setId),
  };

  return {
    filmOf,
    positionFor,
    versionState,
    /** Watched, counting every copy of a film held more than once. */
    watched(setId) {
      const film = filmOf(setId);
      return film?.versions ? filmPlayed(film, state.watchedAt) : state.isWatched(setId);
    },
    /** Positions, newest first, with a film held more than once in them once. */
    continuing: () => continueRows(state.inProgress(), filmOf, state.watchedAt),
    /** Sets or hits with each copy of a film standing in as its whole film, once. */
    asFilms: (items) => asFilms(items, filmOf),
    /** Ids to sets, dropping any the catalog no longer holds; a copy of a film shows as the film. */
    setsFor: (ids) => asFilms(ids.map((id) => current().byId.get(id)).filter(Boolean), filmOf),
    /**
     * Whether playing `set` has to ask which copy first: only for a film held
     * more than once, and never when the copy is already chosen or when the
     * player hands over to the next title, where a dialog behind a fullscreen
     * player would never be seen and the first copy plays instead.
     * @param {any} set @param {{ chosen?: boolean, autoplay?: unknown }} options
     */
    asks: (set, options) => !options.chosen && !options.autoplay && Boolean(filmOf(set.setId)?.versions),
    /**
     * Asks which copy of `set`'s film to play and plays it through `play`;
     * resolves `false` when the viewer backs out. In a list being played
     * through, the copy takes the film's place, so what follows it still does.
     */
    pick(set, queue, options, play) {
      return chooseVersion(filmOf(set.setId), versionState).then((copy) => {
        if (!copy) return false;
        const run = queue?.map((item) => (item.setId === set.setId ? copy : item)) ?? null;
        return play(copy, run, { ...options, chosen: true });
      });
    },
  };
}
