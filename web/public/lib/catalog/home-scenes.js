/**
 * Marked scenes, set like Continue: the same landscape cards in one scrolling
 * row, each opening the title at the mark with the clip panel ready.
 *
 * A mark whose title is no longer in the library is left off rather than
 * drawn as a blank card; it stays on the profile and returns with the title.
 */

import { el } from "../dom.js";
import { clockTime, titleLine } from "../format.js";
import { presetAround } from "../playback/clip-flow.js";
import { artworkUrl } from "./plate.js";

/**
 * @param {{id: string, setId: string, at: number}[]} scenes newest first
 * @param {Map<string, any>} byId
 * @returns {{id: string, set: any, at: number, label: string}[]}
 */
export function sceneCards(scenes, byId) {
  const cards = [];
  for (const { id, setId, at } of scenes) {
    const set = byId.get(setId);
    if (set) cards.push({ id, set, at, label: `${titleLine(set)} · ${clockTime(at)}` });
  }
  return cards;
}

/**
 * @param {ReturnType<typeof sceneCards>} cards
 * @param {(set: any, options: {at: number, clip: {from: number, to: number}}) => void} playScene
 * @param {(id: string) => void} remove
 * @returns {HTMLElement[]}
 */
export function sceneNodes(cards, playScene, remove) {
  return cards.map(({ id, set, at, label }) => {
    // Remove is a sibling of the play button: a button inside a button is invalid and
    // would also fire the card's click.
    const node = el("div", "scene-card");
    const play = el("button", "resume-card");
    play.type = "button";
    const art = el("span", "resume-art");
    const key = set.backdrop ?? set.poster;
    if (key) {
      const image = el("img", set.backdrop ? null : "poster-crop");
      image.src = artworkUrl(key);
      image.alt = "";
      image.loading = "lazy";
      image.decoding = "async";
      art.append(image);
    } else {
      art.append(el("span", "resume-initial", (set.show ?? set.title ?? "?").slice(0, 1).toUpperCase()));
    }
    const text = el("span", "resume-text");
    text.append(el("span", "resume-name", label));
    play.append(art, text);
    play.addEventListener("click", () => playScene(set, { at, clip: presetAround(at, set.duration || null) }));

    const drop = el("button", "scene-remove", "Remove");
    drop.type = "button";
    drop.setAttribute("aria-label", `Remove mark at ${clockTime(at)}`);
    drop.addEventListener("click", () => remove(id));
    node.append(play, drop);
    return node;
  });
}
