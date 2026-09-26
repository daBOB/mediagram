/**
 * A show's own page, as a feature article with a table of contents: the
 * opening spread, then Episodes (a season picker over a readable list, not a
 * wall of thumbnails), About, and Similar.
 *
 * The season in view is part of the URL (`#/series/Show/Season 2`), so a
 * season can be linked and the back button steps out of it.
 */

import { el, focusWhenAttached } from "../dom.js";
import { countOf } from "../format.js";
import { firstItemOf } from "../library.js";
import { genresOf } from "./genres.js";
import { pinControl } from "./pin-control.js";
import { describeHero, factSheet, titleSpread, moreMenu, playPill } from "./title-spread.js";
import { genreLinks } from "./film-page.js";
import { listToggle } from "./list-toggle.js";
import { tabbed } from "./tabs.js";
import { offerCast } from "./cast.js";
import { seasonBlock } from "./course-view.js";
import { collectionGrid } from "./shelf-view.js";
import { GRID } from "./shelf-mode.js";
import { detailRows, pictureLine, provenance, scaleLine, summarize, yearLine } from "./series-summary.js";
import { episodeShort, seriesResume } from "./series-resume.js";
import { similarTo } from "./similar.js";
import { heading } from "./shelf-view.js";
import { inProgress, isWatched, progressOf } from "../watch-state.js";
import { resumeAt } from "../resume-point.js";

/**
 * The route: one show, with the season the URL names (if any) in view.
 * `shelf` is every show, which Similar ranks against.
 */
export function renderSeries(main, _section, collection, name, folders, { play, open, shelf }) {
  if (!collection) {
    heading(main, "Series");
    main.append(el("p", "error", `No show called "${name}".`));
    return;
  }
  const lead = (c) => ({ ...firstItemOf(c.divisions), setId: c.name, show: c });
  const page = seriesPage(collection, {
    season: folders[0] ?? null,
    resume: seriesResume(collection, {
      resumeOf: (setId) => resumeAt(progressOf(setId)),
      recent: inProgress().map((row) => row.setId),
      watched: isWatched,
    }),
    play,
    openSeason: (title) => open("series", collection.name, [title]),
    similar: () => similarTo(lead(collection), shelf.map(lead),
      (item) => item.show.divisions.every((d) => d.items.every((set) => isWatched(set.setId)))).map((item) => item.show),
    openShow: (show) => open("series", show, []),
  });
  main.append(page);
  const first = firstItemOf(collection.divisions);
  if (first?.showKey) {
    fetch(`/api/shows/${encodeURIComponent(first.showKey)}`)
      .then((res) => (res.ok ? res.json() : null))
      .then((meta) => describeSeriesPage(page, meta))
      .catch(() => {});
  }
}

/**
 * @param {import("../library.js").Collection} collection
 * @param {{ season: string|null, resume: ReturnType<typeof import("./series-resume.js").seriesResume>,
 *   play: (set: any) => void, openSeason: (title: string) => void,
 *   similar: () => import("../library.js").Collection[], openShow: (name: string) => void }} on
 */
export function seriesPage(collection, { season, resume, play, openSeason, similar, openShow }) {
  const facts = summarize(collection);
  const first = firstItemOf(collection.divisions);
  const page = el("article", "title-page series-page");
  page.facts = facts;
  page.first = first;

  const start = resume ? playPill(`${resume.verb} ${episodeShort(resume.set)}`, () => play(resume.set)) : null;
  const hero = titleSpread({
    back: { href: "#/series", label: "Back to Series" },
    title: collection.name,
    facts: factsLine(facts, null, first),
    art: first?.backdrop ?? first?.poster ?? null,
    actions: [start, first ? listToggle(first.setId) : null, moreMenu([first ? pinControl(first.setId) : null])],
  });
  page.append(hero);

  // The season the URL names; else the one the resume point is in; else the first.
  const shown = collection.divisions.find((d) => d.title === season)
    ?? collection.divisions.find((d) => resume && d.items.includes(resume.set))
    ?? collection.divisions[0];

  const about = el("div", "about-panel");
  const tabs = tabbed([
    { label: "Episodes", build: () => episodes(collection, shown, play, openSeason) },
    { label: "About", build: () => fillAbout(about, facts, page.meta ?? null) },
    { label: "Similar", build: () => similarShows(similar(), openShow) },
  ], collection.name);
  page.append(tabs);
  offerCast(tabs, first?.showKey, 2);
  return page;
}

/** Adds what the provider says, once it arrives. */
export function describeSeriesPage(page, meta) {
  if (!meta) return;
  page.meta = meta;
  const hero = page.querySelector(".spread");
  if (hero) describeHero(hero, meta);
  const line = page.querySelector(".spread-facts");
  if (line) line.textContent = factsLine(page.facts, meta, page.first);
  const about = page.querySelector(".about-panel");
  if (about?.childElementCount > 0) fillAbout(about, null, meta);
}

/** `1987–1994 · 7 seasons · Sci-Fi, Drama` */
function factsLine(facts, meta, first) {
  return [yearLine(facts, meta), countOf(facts.seasons, "season"), genresOf(first).slice(0, 3).join(", ")]
    .filter(Boolean).join(" · ");
}

/** The show whose season picker was just used: its rebuilt picker takes focus back. */
let pickedOn = null;

function episodes(collection, shown, play, openSeason) {
  const box = el("div", "episodes-panel");
  if (collection.divisions.length > 1) {
    const label = el("label", "season-pick");
    label.append(el("span", "sr-only", "Season"));
    const select = el("select");
    for (const division of collection.divisions) {
      const option = el("option", null, `${division.title} · ${countOf(division.items.length, "episode")}`);
      option.value = division.title;
      option.selected = division === shown;
      select.append(option);
    }
    select.addEventListener("change", () => {
      pickedOn = collection.name;
      openSeason(select.value);
    });
    if (pickedOn === collection.name) {
      pickedOn = null;
      focusWhenAttached(select);
    }
    label.append(select);
    box.append(label);
  }
  if (shown) box.append(seasonBlock(shown, play));
  return box;
}

/** Keeps the facts it was first given, so a later call can add the provider's. */
function fillAbout(about, facts, meta) {
  about.facts = facts ?? about.facts;
  const known = about.facts;
  about.replaceChildren(factSheet([
    ["Aired", yearLine(known, meta)],
    ["Held", scaleLine(known, meta)],
    ["From", provenance(meta ? { ...meta, genres: null } : null)],
    ["Genres", genreLinks(String(meta?.genres ?? "").split(",").map((g) => g.trim()).filter(Boolean))],
    ["Picture", pictureLine(known)],
    ...detailRows(known).map((row) => [row.label, row.value]),
  ]));
  return about;
}

function similarShows(shows, openShow) {
  if (shows.length === 0) return el("p", "empty", "Nothing else in the library shares its genres.");
  return collectionGrid("series", shows, openShow, { mode: GRID, strip: true });
}
