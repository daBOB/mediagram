/**
 * The grids a viewer lands on: films, shows and courses as cards.
 *
 * Cards are the one view that never nests — a card stands for a whole film,
 * show or course, and opening it is what reveals the shape inside. The
 * nesting lives in `course-view.js`.
 */

import { el } from "../dom.js";
import { initialsOf, plate } from "./plate.js";
import { countOf, episodeLabel, hdrLabel, humanDuration, humanSize, resumeLine } from "../format.js";
import { isWatched, progressOf } from "../watch-state.js";
import { watchedFraction } from "../resume-point.js";
import { firstItemOf } from "../library.js";
import { offlineBadge, transcodeBadge } from "./set-badge.js";
import { GRID, LIST } from "./shelf-mode.js";
import { seasonPlate } from "./season-wall.js";

/**
 * @typedef {{mode?: "list"|"grid"}} GridOptions
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 * @typedef {import("../library.js").Collection} Collection
 * @typedef {import("../library.js").Division} Division
 */

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
 * order a viewer reads them in: what it is, then how long it is.
 */
function filmMeta(set, mode) {
  const hdr = hdrLabel(set);
  const facts =
    mode === GRID
      ? [set.year, set.quality, humanDuration(set.duration)]
      : [set.year, set.quality, hdr, humanDuration(set.duration), humanSize(set.total)];
  return facts.filter(Boolean).join(" · ");
}

/** A card for a film, a show or a course. */
function card({ name, meta, resume, initials, onClick, badges, poster, progress, watched }) {
  const button = el("button", "card");
  const thumb = plate({ poster, name, initials, progress, watched });

  const body = el("div", "body");
  body.append(el("div", "name", name));
  if (meta) body.append(el("div", "meta", meta));
  // Under the facts about the film, because it is a fact about the viewer.
  if (resume) body.append(el("div", "resume", resume));
  // Two at most, and both may be true at once: a title already on this disk
  // that still has to be converted plays offline all the same, because the
  // conversion reads from the same cache.
  for (const badge of badges ?? []) if (badge) body.append(badge);
  button.append(thumb, body);
  button.addEventListener("click", onClick);
  return button;
}

/**
 * What to say when a shelf is empty: the command that would fill it.
 * @param {"movies"|"series"|"tutorials"} section
 * @param {{kids?: boolean}} [options] a kids profile is waiting for ratings,
 *   not uploads, so it is told that instead of how to upload
 */
export function emptyState(section, { kids = false } = {}) {
  if (kids) return el("p", "empty", "Nothing rated FSK 12 or under yet.");
  const p = el("p", "empty");
  p.append(SECTIONS[section].empty + " ");
  if (section === "movies") p.append("Upload one with "), p.append(el("code", null, "mediagram add <file> --tmdb <id>"));
  if (section === "series") p.append("Upload episodes with "), p.append(el("code", null, "mediagram add <file> --season 1 --episode 1"));
  if (section === "tutorials") p.append("Upload a course with "), p.append(el("code", null, "mediagram add-course <folder>"));
  p.append(".");
  return p;
}

/**
 * A show's seasons, as a wall. A season is ticked once every episode in it
 * is, which is the only sense in which a season is watched.
 * @param {Division[]} divisions
 * @param {(title: string) => void} onOpen
 */
export function seasonGrid(divisions, onOpen) {
  const grid = container(GRID);
  for (const division of divisions) {
    const { name, meta, poster } = seasonPlate(division);
    grid.append(
      card({
        name,
        meta,
        poster,
        initials: initialsOf(name),
        watched: division.items.length > 0 && division.items.every((set) => isWatched(set.setId)),
        onClick: () => onOpen(division.title),
      }),
    );
  }
  return grid;
}

/**
 * Films: a flat grid, since a film is one thing.
 * @param {CatalogSet[]} movies
 * @param {(set: CatalogSet) => void} onPlay
 * @param {GridOptions} [options]
 */
export function movieGrid(movies, onPlay, options = {}) {
  const mode = options.mode ?? LIST;
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
 * @param {CatalogSet[]} sets
 * @param {(set: CatalogSet) => void} onPlay
 * @param {GridOptions & {
 *   caption?: (set: import("../library.js").CatalogSet) => string,
 *   finish?: (set: import("../library.js").CatalogSet) => void,
 * }} [options] `finish` puts a "Mark finished" control beside each title,
 *   for a shelf of things started: a film finished on another device, or
 *   given up on, would otherwise sit there until played to the credits.
 */
export function setGrid(sets, onPlay, options = {}) {
  // Where this viewer got to, unless the caller knows better. The start
  // page does: an episode offered because the one before it was finished
  // has no position to report, and "next up" is what that line should say.
  const caption = options.caption ?? ((set) => resumeLine(progressOf(set.setId)));
  // An index by default, because a watch-state shelf is a list of what is
  // outstanding. The start page asks for plates, so its rows are the same
  // shape as the ones below them.
  const mode = options.mode ?? LIST;
  const grid = container(mode);
  for (const set of sets) {
    const shown = card({
      name: set.title ?? set.setId,
      // Under a plate, what identifies an episode is its show and its
      // number; the year and the runtime are what a list has room for.
      meta: (mode === GRID
        ? [set.show, episodeLabel(set)]
        : [set.show, episodeLabel(set), set.year, humanDuration(set.duration)]
      )
        .filter(Boolean)
        .join(" · "),
      initials: initialsOf(set.title ?? set.show),
      poster: set.poster ?? null,
      badges: [offlineBadge(set), transcodeBadge(set)],
      progress: watchedFraction(progressOf(set.setId)),
      // Empty for a title never started, so a watchlist of things not yet
      // begun gains no line it cannot fill.
      resume: caption(set),
      watched: isWatched(set.setId),
      onClick: () => onPlay(set),
    });
    const finish = options.finish;
    grid.append(finish ? withAction(shown, "Mark finished", () => finish(set)) : shown);
  }
  return grid;
}

/**
 * A card with a second thing to do beside it.
 *
 * Beside rather than inside: the card is itself a button, and a button inside
 * a button is one control to a screen reader and a keyboard, which would make
 * this one unreachable.
 * @param {HTMLElement} card
 * @param {string} label
 * @param {() => void} onAction
 */
function withAction(card, label, onAction) {
  const row = el("div", "with-action");
  const action = el("button", "quiet card-action", label);
  action.addEventListener("click", onAction);
  row.append(card, action);
  return row;
}

/**
 * Shows and courses: a grid of collections, each opening its own view.
 * @param {"series"|"tutorials"} section
 * @param {Collection[]} collections
 * @param {(name: string) => void} onOpen
 * @param {GridOptions} [options]
 */
export function collectionGrid(section, collections, onOpen, options = {}) {
  const mode = options.mode ?? LIST;
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

/**
 * A shelf's title and how much is on it, as one block.
 *
 * Together rather than as two siblings because the stylesheet sets them on a
 * shared baseline with the extent flush right, which two separate children
 * of `main` could not do.
 */
export function heading(main, title, subtitle, control) {
  const head = el("header", "shelf-head");
  head.append(el("h1", null, title));
  // The count and the control travel together on the right, so the header
  // stays a two-ended line rather than becoming three things spread across
  // the page.
  const aside = el("div", "shelf-aside");
  if (subtitle) aside.append(el("p", "sub", subtitle));
  if (control) aside.append(control);
  if (aside.childElementCount > 0) head.append(aside);
  main.append(head);
}

/**
 * The way back up, as far as here.
 *
 * Ancestors only: where you are is the heading directly below, and printing
 * it twice says nothing the second time. Real links rather than buttons,
 * because every one of these is a URL that works on its own.
 */
export function crumbs(section, label, collectionName, folders) {
  const nav = el("nav", "crumbs");
  let hash = `#/${section}`;
  const trail = [{ label: label, hash }];

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
