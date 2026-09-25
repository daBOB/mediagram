/**
 * Continue watching, set quietly: landscape cards in one scrolling row, each
 * with where the viewer stopped. The part of the home page that is a tool
 * rather than a feature, so it is drawn small and plain.
 *
 * What goes on it is decided in `home-shelves.js` — titles part-way through,
 * then the next episode of shows underway — and a click plays, as every
 * card on the shelves does.
 */

import { el } from "../dom.js";
import { episodeLabel, humanDuration, resumeLine } from "../format.js";
import { progressOf } from "../watch-state.js";
import { progressRuleFor } from "./set-badge.js";
import { artUrl } from "./home-cover.js";

/**
 * @param {import("./home-shelves.js").HomeShelves} shelves
 * @param {(set: any) => void} play
 * @returns {HTMLElement[]} the cards, in the order they are offered
 */
export function resumeCards(shelves, play) {
  const cards = shelves.continues.map((set) => resumeCard(set, resumeLine(progressOf(set.setId)), play));
  for (const entry of shelves.nextUp) {
    const caption = entry.resume ? resumeLine(progressOf(entry.set.setId)) : "Next up";
    cards.push(resumeCard(entry.set, caption, play));
  }
  return cards;
}

function resumeCard(set, caption, play) {
  const episode = set.kind !== "movie" && set.show;
  const name = episode ? set.show : (set.title ?? set.setId);
  const card = el("button", "resume-card");
  card.type = "button";

  const art = el("span", "resume-art");
  const key = set.backdrop ?? set.poster;
  if (key) {
    const image = el("img", set.backdrop ? null : "poster-crop");
    image.src = artUrl(key);
    image.alt = "";
    image.loading = "lazy";
    image.decoding = "async";
    art.append(image);
  } else {
    art.append(el("span", "resume-initial", name.slice(0, 1).toUpperCase()));
  }
  const rule = progressRuleFor(set);
  if (rule) art.append(rule);

  const text = el("span", "resume-text");
  text.append(el("span", "resume-name", name));
  const sub = episode
    ? [episodeLabel(set), set.title].filter(Boolean).join(" · ")
    : [set.year, humanDuration(set.duration)].filter(Boolean).join(" · ");
  if (sub) text.append(el("span", "resume-sub", sub));
  if (caption) text.append(el("span", "resume-at", caption));
  card.append(art, text);
  card.addEventListener("click", () => play(set));
  return card;
}
