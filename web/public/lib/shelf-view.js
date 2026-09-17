/**
 * The grids a viewer lands on: films, shows and courses as cards.
 *
 * Cards are the one view that never nests — a card stands for a whole film,
 * show or course, and opening it is what reveals the shape inside. The
 * nesting lives in `course-view.js`.
 */

import { el } from "./dom.js";
import { humanDuration, humanSize } from "./format.js";
import { transcodeBadge } from "./course-view.js";

export const SECTIONS = {
  movies: { label: "Movies", empty: "No films yet." },
  series: { label: "Series", empty: "No series yet." },
  tutorials: { label: "Tutorials", empty: "No courses yet." },
};

/** A card for a film, a show or a course. */
function card({ name, meta, initials, onClick, badge, poster }) {
  const button = el("button", "card");
  const thumb = el("div", "thumb", poster ? undefined : initials);
  if (poster) {
    // The initials stay underneath as the alt text, so a poster that fails to
    // load leaves a card that still says what it is.
    const image = el("img");
    image.src = `/api/posters/${encodeURIComponent(poster)}.jpg`;
    image.alt = name;
    image.loading = "lazy";
    thumb.append(image);
  }
  const body = el("div", "body");
  body.append(el("div", "name", name));
  if (meta) body.append(el("div", "meta", meta));
  if (badge) body.append(badge);
  button.append(thumb, body);
  button.addEventListener("click", onClick);
  return button;
}

function initialsOf(text) {
  return (text ?? "?")
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word[0] ?? "")
    .join("")
    .toUpperCase();
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
export function movieGrid(movies, onPlay) {
  const grid = el("div", "grid");
  for (const set of movies) {
    grid.append(
      card({
        name: set.title ?? set.setId,
        meta: [set.year, humanDuration(set.duration), humanSize(set.total)].filter(Boolean).join(" · "),
        initials: initialsOf(set.title),
        poster: set.poster ?? null,
        badge: transcodeBadge(set),
        onClick: () => onPlay(set),
      }),
    );
  }
  return grid;
}

/** Shows and courses: a grid of collections, each opening its own view. */
export function collectionGrid(section, collections, firstItemOf, onOpen) {
  const grid = el("div", "grid");
  for (const collection of collections) {
    // Folders that hold something, however deep. A course's top-level folders
    // are too few to describe it and its total folders too many.
    const divisions = collection.chapters;
    const unit =
      section === "series"
        ? divisions === 1
          ? "season"
          : "seasons"
        : divisions === 1
          ? "chapter"
          : "chapters";
    grid.append(
      card({
        name: collection.name,
        meta: `${collection.count} ${section === "series" ? "episodes" : "lessons"} · ${divisions} ${unit}`,
        initials: initialsOf(collection.name),
        // A show's artwork is the one its episodes share.
        poster: firstItemOf(collection.divisions)?.poster ?? null,
        onClick: () => onOpen(collection.name),
      }),
    );
  }
  return grid;
}
