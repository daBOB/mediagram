/**
 * Watch state for whatever a card stands for.
 *
 * A film held more than once is played when any of its copies is, and its
 * card shows the newest place any copy was left at; anything else is read as
 * itself. Kept apart from `watch-state.js`, which stores rows per file and
 * knows nothing of how the catalog groups them.
 */

import { isWatched, progressOf, watchedAt } from "./watch-state.js";
import { filmPlayed, filmPosition } from "./film-versions.js";

/** Whether the title on this card has been watched to the end. */
export const playedOf = (set) => (set.versions ? filmPlayed(set, watchedAt) : isWatched(set.setId));

/** The progress row this card's bar and caption are drawn from, or `null`. */
export const positionOf = (set) => (set.versions ? filmPosition(set, progressOf, watchedAt) : progressOf(set.setId));
