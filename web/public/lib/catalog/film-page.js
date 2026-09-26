/**
 * A film's own page, laid out as a feature article: the opening spread
 * (`title-spread.js`), then tabs — Overview, Similar, Details.
 *
 * What the provider says (overview, tagline) arrives separately and is added
 * by `describeFilm`: the facts already on screen are not redrawn under the
 * viewer. A Cast tab joins these once the title's credits arrive.
 */

import { el } from "../dom.js";
import { clockTime, humanDuration, humanSize, bitrateLabel, hdrLabel } from "../format.js";
import { languageLabel } from "../language-label.js";
import { genreHash, genresOf, scoreLabel } from "./genres.js";
import { ageLabel } from "../age-rating.js";
import { pinControl } from "./pin-control.js";
import { artworkUrl } from "./plate.js";
import { describeHero, factSheet, titleSpread, moreMenu, playPill } from "./title-spread.js";
import { listToggle } from "./list-toggle.js";
import { tabbed } from "./tabs.js";
import { offerCast } from "./cast.js";
import { movieGrid } from "./shelf-view.js";
import { GRID } from "./shelf-mode.js";

/**
 * The page for `set`. `resume` is the second to carry on from, or `null`.
 *
 * @param {import("../library.js").CatalogSet} set
 * @param {{ resume: number|null, onPlay: (set: any) => void,
 *   similar: () => import("../library.js").CatalogSet[], openFilm: (set: any) => void,
 *   hasFranchise: boolean }} on `hasFranchise`: its franchise has a page (two films held)
 */
export function filmPage(set, { resume, onPlay, similar, openFilm, hasFranchise = false }) {
  const page = el("article", "title-page film-page");
  const title = set.title ?? set.setId;
  const facts = [set.year, humanDuration(set.duration), ageLabel(set), genresOf(set).slice(0, 3).join(", ")]
    .filter(Boolean).join(" · ");
  const play = playPill(resume ? `Resume from ${clockTime(resume)}` : "Play", () => onPlay(set));
  page.append(titleSpread({
    back: { href: "#/movies", label: "Back to Movies" },
    title,
    facts,
    art: set.backdrop ?? set.poster,
    actions: [play, listToggle(set.setId), moreMenu([pinControl(set.setId)])],
  }));

  const tabs = tabbed([
    { label: "Overview", build: () => overview(set, hasFranchise) },
    { label: "Similar", build: () => similarShelf(similar(), openFilm) },
    { label: "Details", build: () => details(set) },
  ], title);
  page.append(tabs);
  offerCast(tabs, set.showKey);
  return page;
}

/** Adds the provider's overview and tagline to the spread, once. */
export function describeFilm(page, meta) {
  const hero = page.querySelector(".spread");
  if (hero) describeHero(hero, meta);
}

/** The poster beside the facts a reader looks up first. */
function overview(set, hasFranchise) {
  const box = el("div", "overview-panel");
  if (set.poster) {
    const image = el("img", "overview-poster");
    image.src = artworkUrl(set.poster);
    image.alt = "";
    image.loading = "lazy";
    box.append(image);
  }
  box.append(factSheet([
    ["Released", set.year ? String(set.year) : null],
    ["Runtime", humanDuration(set.duration)],
    ["Rated", ageLabel(set)],
    ["Score", scoreLabel(set.rating)],
    ["Genres", genreLinks(genresOf(set))],
    ["Part of", hasFranchise ? franchiseLink(set) : null],
  ]));
  return box;
}

/** The franchise a film belongs to, as a link to its collection page. */
function franchiseLink(set) {
  if (!set.collectionId || !set.collectionName) return null;
  const link = el("a", "franchise-link", set.collectionName);
  link.href = `#/collections/tmdb-${set.collectionId}`;
  return link;
}

function similarShelf(films, openFilm) {
  if (films.length === 0) return el("p", "empty", "Nothing else in the library shares its genres.");
  return movieGrid(films, openFilm, { mode: GRID, strip: true });
}

/** What the file is: the questions a viewer asks when a title will not play. */
export function details(set) {
  const languages = (codes) => (Array.isArray(codes) && codes.length > 0
    ? codes.map((code) => languageLabel(code, code)).join(", ")
    : null);
  return factSheet([
    ["Quality", [set.quality, hdrLabel(set)].filter(Boolean).join(" · ")],
    ["Video", set.vcodec],
    ["Audio", set.acodec],
    ["Audio languages", languages(set.alang)],
    ["Subtitles", languages(set.slang)],
    ["Container", set.container],
    ["Size", Number(set.total) > 0 ? humanSize(set.total) : null],
    ["Bitrate", bitrateLabel(set)],
    ["Parts", Number(set.partCount) > 1 ? String(set.partCount) : null],
  ]);
}

/**
 * Each genre as a link to its shelf, or `null` when there are none.
 * Shared with the series page, so a genre is followed the same way from both.
 */
export function genreLinks(names) {
  if (names.length === 0) return null;
  const line = el("span", "genre-links");
  for (const name of names) {
    const link = el("a", "genre", name);
    link.href = genreHash(name);
    line.append(link);
  }
  return line;
}
