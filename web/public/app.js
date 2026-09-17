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

import { groupLibrary } from "./lib/library.js";
import { loadLink, playbackFor } from "./lib/link.js";
import { openPlayer } from "./lib/player.js";
import { codecLine, episodeLabel, humanDuration, humanSize } from "./lib/format.js";

const main = document.getElementById("main");

/** @type {{movies: any[], series: any[], tutorials: any[]}} */
let library = { movies: [], series: [], tutorials: [] };

const SECTIONS = {
  movies: { label: "Movies", empty: "No films yet." },
  series: { label: "Series", empty: "No series yet." },
  tutorials: { label: "Tutorials", empty: "No courses yet." },
};

/** Cleared and rebuilt per view; every node is created, never interpolated. */
function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

/** A card for a film, a show or a course. */
function card({ name, meta, initials, onClick, badge }) {
  const button = el("button", "card");
  const thumb = el("div", "thumb", initials);
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

function transcodeBadge(set) {
  return playbackFor(set).kind === "direct" ? null : el("span", "badge", "needs transcode");
}

function heading(title, subtitle) {
  main.append(el("h1", null, title));
  if (subtitle) main.append(el("p", "sub", subtitle));
}

function emptyState(section) {
  const p = el("p", "empty");
  p.append(SECTIONS[section].empty + " ");
  if (section === "movies") p.append("Upload one with "), p.append(el("code", null, "mediagram add <file> --tmdb <id>"));
  if (section === "series") p.append("Upload episodes with "), p.append(el("code", null, "mediagram add <file> --season 1 --episode 1"));
  if (section === "tutorials") p.append("Upload a course with "), p.append(el("code", null, "mediagram add-course <folder>"));
  p.append(".");
  main.append(p);
}

/** Films: a flat grid, since a film is one thing. */
function viewMovies() {
  heading("Movies", `${library.movies.length} in the library`);
  if (library.movies.length === 0) return emptyState("movies");

  const grid = el("div", "grid");
  for (const set of library.movies) {
    grid.append(
      card({
        name: set.title ?? set.setId,
        meta: [set.year, humanDuration(set.duration), humanSize(set.total)].filter(Boolean).join(" · "),
        initials: initialsOf(set.title),
        badge: transcodeBadge(set),
        onClick: () => openPlayer(set),
      }),
    );
  }
  main.append(grid);
}

/** Shows and courses: a grid of collections, each opening its own view. */
function viewCollections(section) {
  const collections = library[section];
  heading(SECTIONS[section].label, `${collections.length} in the library`);
  if (collections.length === 0) return emptyState(section);

  const grid = el("div", "grid");
  for (const collection of collections) {
    const divisions = collection.seasons.length;
    grid.append(
      card({
        name: collection.name,
        meta: `${collection.count} ${section === "series" ? "episodes" : "lessons"} · ${divisions} ${
          section === "series" ? (divisions === 1 ? "season" : "seasons") : divisions === 1 ? "chapter" : "chapters"
        }`,
        initials: initialsOf(collection.name),
        onClick: () => {
          location.hash = `#/${section}/${encodeURIComponent(collection.name)}`;
        },
      }),
    );
  }
  main.append(grid);
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

  for (const division of collection.seasons) {
    const block = el("section", "season");
    // A folder path reads badly as a heading. Show the folder itself, with
    // the ones above it in smaller type, so the shelf still says where the
    // lesson sat without shouting the whole path.
    const parts = division.title.split("/");
    const leaf = parts[parts.length - 1];
    const heading = el("h2");
    if (parts.length > 1) {
      heading.append(el("span", "crumb", parts.slice(0, -1).join(" / ") + " / "));
    }
    heading.append(document.createTextNode(`${leaf} · ${division.items.length}`));
    block.append(heading);

    for (const set of division.items) {
      const row = el("button", "row");
      row.append(el("div", "num", episodeLabel(set)));

      const title = el("div", "title");
      title.append(el("b", null, set.title ?? set.setId));
      title.append(el("span", null, codecLine(set)));
      row.append(title);

      const badge = transcodeBadge(set);
      if (badge) row.append(badge);
      if (set.hasSummary) row.append(el("span", "has-summary", "notes"));

      row.append(
        el("div", "meta", [humanDuration(set.duration), humanSize(set.total)].filter(Boolean).join(" · ")),
      );
      row.addEventListener("click", () => openPlayer(set));
      block.append(row);
    }
    main.append(block);
  }
}

function route() {
  const [section = "movies", name] = location.hash.replace(/^#\/?/, "").split("/");
  const known = SECTIONS[section] ? section : "movies";

  for (const link of document.querySelectorAll("nav a")) {
    link.classList.toggle("active", link.dataset.section === known);
  }

  main.textContent = "";
  if (name) viewCollection(known, decodeURIComponent(name));
  else if (known === "movies") viewMovies();
  else viewCollections(known);
}

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
