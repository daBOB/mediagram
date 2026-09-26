/**
 * A title page's opening spread, set like a feature article: the words down
 * the left, the title's backdrop filling the right and fading into the page
 * behind them, and the tagline set large over the artwork as the pull-quote.
 *
 * Shared by films and shows so the two read as the same kind of page; each
 * passes its own facts and actions. What the provider says (overview,
 * tagline) arrives later and is added by `describeHero` without rebuilding.
 */

import { el } from "../dom.js";
import { artworkUrl } from "./plate.js";

/**
 * @param {{ back: {href: string, label: string}, title: string, eyebrow?: string|null,
 *   facts: string, art: string|null, actions: Node[] }} spread
 */
export function titleSpread({ back, title, eyebrow = null, facts, art, actions }) {
  const hero = el("header", art ? "spread" : "spread no-art");

  if (art) {
    const figure = el("div", "spread-art");
    figure.setAttribute("aria-hidden", "true");
    const image = el("img");
    image.src = artworkUrl(art);
    image.alt = "";
    image.decoding = "async";
    image.setAttribute("fetchpriority", "high");
    figure.append(image);
    hero.append(figure);
  }

  const copy = el("div", "spread-copy");
  const backLink = el("a", "spread-back", `← ${back.label}`);
  backLink.href = back.href;
  copy.append(backLink);
  if (eyebrow) copy.append(el("p", "eyebrow spread-kicker", eyebrow));
  const heading = el("h1", title.length > 22 ? "spread-title long" : "spread-title", title);
  heading.tabIndex = -1;
  copy.append(heading);
  if (facts) copy.append(el("p", "spread-facts", facts));
  copy.append(el("p", "spread-overview"));
  const row = el("div", "spread-actions");
  row.append(...actions.filter(Boolean));
  copy.append(row);
  hero.append(copy);
  return hero;
}

/** Adds the overview and the pull-quote once the provider's words arrive. */
export function describeHero(hero, meta) {
  if (!meta) return;
  const overview = hero.querySelector(".spread-overview");
  if (overview && meta.overview && !overview.textContent) overview.textContent = meta.overview;
  if (meta.tagline && !hero.querySelector(".spread-quote")) {
    const quote = el("blockquote", "spread-quote");
    quote.append(el("p", null, `“${meta.tagline}”`));
    hero.append(quote);
  }
}

/** The pill that starts a title: solid, with a play mark. */
export function playPill(label, onClick) {
  const button = el("button", "pill pill-solid film-play");
  button.type = "button";
  button.append(el("span", "pill-play"), el("span", null, label));
  button.addEventListener("click", onClick);
  return button;
}

/**
 * The ⋯ beside the pills: the actions a page has room for but should not
 * lead with. A native disclosure, so it needs no script to open or close.
 */
export function moreMenu(items) {
  const present = items.filter(Boolean);
  if (present.length === 0) return null;
  const menu = el("details", "more-menu");
  const summary = el("summary", "pill pill-line more-button");
  summary.setAttribute("aria-label", "More");
  summary.append(el("span", null, "⋯"));
  const list = el("div", "more-list");
  list.append(...present);
  menu.append(summary, list);
  return menu;
}

/**
 * Label–value rows, as a magazine prints the facts box beside a feature.
 * A row with no value is left out rather than printed empty.
 *
 * @param {[string, string|Node|null|undefined][]} rows
 */
export function factSheet(rows) {
  const list = el("dl", "fact-sheet");
  for (const [label, value] of rows) {
    if (value === null || value === undefined || value === "") continue;
    const row = el("div", "fact");
    const dd = el("dd");
    dd.append(value);
    row.append(el("dt", null, label), dd);
    list.append(row);
  }
  return list;
}
