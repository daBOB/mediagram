/**
 * The grids a viewer lands on: films, shows and courses as cards.
 *
 * Cards are the one view that never nests — a card stands for a whole film,
 * show or course, and opening it is what reveals the shape inside. The
 * nesting lives in `course-view.js`.
 */

import { el } from "./dom.js";
import { initialsOf, plate } from "./plate.js";
import { countOf, episodeLabel, humanDuration, humanSize } from "./format.js";
import { isWatched, progressOf } from "./watch-state.js";
import { watchedFraction } from "./resume-point.js";
import { firstItemOf } from "./library.js";
import { offlineBadge, transcodeBadge } from "./set-badge.js";
import { GRID } from "./shelf-mode.js";

// `extent` is what the shelf counts in, for the line under its title: a
// catalogue says "twelve films", not "12 items".
export const SECTIONS = {
  movies: { label: "Movies", empty: "No films yet.", extent: "film" },
  series: { label: "Series", empty: "No series yet.", extent: "show" },
  tutorials: { label: "Tutorials", empty: "No courses yet.", extent: "course" },
};

/**
 * The container the cards go in, in whichever of the two shapes.
 *
 * One class rather than two renderers: a plate and a row hold the same
 * nodes, and the difference is entirely how they are laid out. Building the
 * grid twice would be two places for a badge or a progress rule to be
 * forgotten.
 */
function container(mode) {
  return el("div", mode === GRID ? "grid plates" : "grid");
}

/**
 * The caption under a film, which is shorter on a plate than in a row.
 *
 * A row has the width of the page and sets its figures against the right
 * edge; a plate has the width of a poster. Five facts do not fit that, and
 * the ones to drop are the ones the poster and the list already answer — a
 * wall is for finding the film, not for comparing encodes.
 *
 * Resolution and HDR sit between the year and the runtime because that is the
 * order a viewer reads them in: what it is, then how long it is. `SDR` is
 * left out — see `technicalLine`.
 */
function filmMeta(set, mode) {
  const hdr = set.hdr && set.hdr !== "SDR" ? set.hdr : null;
  const facts =
    mode === GRID
      ? [set.year, set.quality, humanDuration(set.duration)]
      : [set.year, set.quality, hdr, humanDuration(set.duration), humanSize(set.total)];
  return facts.filter(Boolean).join(" · ");
}

/** A card for a film, a show or a course. */
function card({ name, meta, initials, onClick, badges, poster, progress, watched }) {
  const button = el("button", "card");
  const thumb = plate({ poster, name, initials, progress, watched });

  const body = el("div", "body");
  body.append(el("div", "name", name));
  if (meta) body.append(el("div", "meta", meta));
  // Two at most, and both may be true at once: a title already on this disk
  // that still has to be converted plays offline all the same, because the
  // conversion reads from the same cache.
  for (const badge of badges ?? []) if (badge) body.append(badge);
  button.append(thumb, body);
  button.addEventListener("click", onClick);
  return button;
}

/** What to say when a shelf is empty: the command that would fill it. */
export function emptyState(section) {
  const p = el("p", "empty");
  p.append(SECTIONS[section].empty + " ");
  if (section === "movies") p.append("Upload one with "), p.append(el("code", null, "mediagram add <file> --tmdb <id>"));
  if (section === "series") p.append("Upload episodes with "), p.append(el("code", null, "mediagram add <file> --season 1 --episode 1"));
  if (section === "tutorials") p.append("Upload a course with "), p.append(el("code", null, "mediagram add-course <folder>"));
  p.append(".");
  return p;
}

/** Films: a flat grid, since a film is one thing. */
export function movieGrid(movies, onPlay, mode) {
  const grid = container(mode);
  for (const set of movies) {
    grid.append(
      card({
        name: set.title ?? set.setId,
        meta: filmMeta(set, mode),
        initials: initialsOf(set.title),
        poster: set.poster ?? null,
        badges: [offlineBadge(set), transcodeBadge(set)],
        progress: watchedFraction(progressOf(set.setId)),
        watched: isWatched(set.setId),
        onClick: () => onPlay(set),
      }),
    );
  }
  return grid;
}

/**
 * Any set at all, as plates: what the shelves built from watch state hold.
 *
 * A film, an episode and a lesson end up side by side here, which the three
 * catalog shelves never have to deal with — so the caption says where a title
 * came from rather than assuming everything on the shelf is one kind of thing.
 */
export function setGrid(sets, onPlay) {
  const grid = el("div", "grid");
  for (const set of sets) {
    grid.append(
      card({
        name: set.title ?? set.setId,
        meta: [set.show, episodeLabel(set), set.year, humanDuration(set.duration)]
          .filter(Boolean)
          .join(" · "),
        initials: initialsOf(set.title ?? set.show),
        poster: set.poster ?? null,
        badges: [offlineBadge(set), transcodeBadge(set)],
        progress: watchedFraction(progressOf(set.setId)),
        watched: isWatched(set.setId),
        onClick: () => onPlay(set),
      }),
    );
  }
  return grid;
}

/** Shows and courses: a grid of collections, each opening its own view. */
export function collectionGrid(section, collections, onOpen, mode) {
  const series = section === "series";
  const grid = container(mode);
  for (const collection of collections) {
    // `chapters` counts the folders that hold something, however deep: a
    // course's top-level folders are too few to describe it, its total
    // folders too many.
    const { count, chapters } = collection;
    grid.append(
      card({
        name: collection.name,
        meta: [
          countOf(count, series ? "episode" : "lesson"),
          countOf(chapters, series ? "season" : "chapter"),
        ].join(" · "),
        initials: initialsOf(collection.name),
        // A show's artwork is the one its episodes share.
        poster: firstItemOf(collection.divisions)?.poster ?? null,
        onClick: () => onOpen(collection.name),
      }),
    );
  }
  return grid;
}
