/**
 * A film held more than once, drawn as the choice it is: the Versions list on
 * its page, and the same rows in a dialog for every card that plays a title
 * straight away (Continue, My List, a hand-built list).
 *
 * Asked every time rather than remembered: the right copy depends on the
 * screen, and a guess made for the TV is wrong on a phone.
 */

import { el } from "../dom.js";
import { clockTime, codecLine } from "../format.js";
import { languageLabel } from "../language-label.js";
import { versionLabel, versionsOf } from "../film-versions.js";
import { offlineBadge, transcodeBadge } from "./set-badge.js";

/** How soon after opening a backdrop click still belongs to the click that opened it. */
const DOUBLE_CLICK_MS = 500;

/** `mkv · av1 · aac · German, English`: what a row needs to tell two copies apart. */
function technicalLine(set) {
  let languages = [];
  try {
    const parsed = JSON.parse(set.alang ?? "[]");
    if (Array.isArray(parsed)) languages = parsed.map((code) => languageLabel(code, code));
  } catch {
    // An unreadable list says nothing rather than something wrong.
  }
  return [codecLine(set), languages.join(", ")].filter(Boolean).join(" · ");
}

/**
 * One row per version, in version order: its label, its technical line,
 * where this viewer is in it, and its own Play.
 * @param {import("../library.js").CatalogSet} film
 * @param {{ resumeOf: (version: any) => number|null, played: (version: any) => boolean,
 *   onPlay: (version: any) => void }} on
 */
export function versionRows(film, { resumeOf, played, onPlay }) {
  const list = el("ul", "versions");
  for (const version of versionsOf(film)) {
    const row = el("li", "version");
    const words = el("div", "version-words");
    words.append(el("div", "version-label", versionLabel(version)));
    const technical = technicalLine(version);
    if (technical) words.append(el("div", "version-tech", technical));
    const at = resumeOf(version);
    const where = at !== null ? `Resume at ${clockTime(at)}` : played(version) ? "Played" : null;
    if (where) words.append(el("div", "version-state", where));
    for (const badge of [offlineBadge(version), transcodeBadge(version)]) if (badge) words.append(badge);

    const play = el("button", "pill pill-line version-play");
    play.type = "button";
    play.append(el("span", "pill-play"), el("span", null, at !== null ? "Resume" : "Play"));
    play.setAttribute("aria-label", `${at !== null ? "Resume" : "Play"} ${versionLabel(version)}`);
    play.addEventListener("click", () => onPlay(version));
    row.append(words, play);
    list.append(row);
  }
  return list;
}

/**
 * Asks which version of `film` to play, in a dialog over the page.
 *
 * Resolves to the version chosen, or `null` when the viewer backs out with
 * Escape, a click outside, or Cancel: nothing plays.
 * @param {import("../library.js").CatalogSet} film
 * @param {{ resumeOf: (version: any) => number|null, played: (version: any) => boolean }} on
 * @returns {Promise<any|null>}
 */
export function chooseVersion(film, { resumeOf, played }) {
  const dialog = el("dialog", "version-chooser");
  dialog.setAttribute("aria-label", `Which version of ${film.title ?? "this film"}?`);
  return new Promise((resolve) => {
    let chosen = null;
    const done = (version) => {
      chosen = version;
      dialog.close();
    };
    dialog.append(
      el("p", "version-chooser-title", film.title ?? film.setId),
      versionRows(film, { resumeOf, played, onPlay: done }),
    );
    const cancel = el("button", "quiet version-chooser-cancel", "Cancel");
    cancel.type = "button";
    cancel.addEventListener("click", () => done(null));
    dialog.append(cancel);
    // A backdrop click lands on the dialog itself, as does one on its padding:
    // only the first is outside the box. The second click of a double-click
    // on the card would land there too, a moment after opening; it is not a "no".
    const openedAt = performance.now();
    dialog.addEventListener("click", (event) => {
      if (event.target !== dialog || performance.now() - openedAt < DOUBLE_CLICK_MS) return;
      const box = dialog.getBoundingClientRect();
      const inside = event.clientX >= box.left && event.clientX <= box.right
        && event.clientY >= box.top && event.clientY <= box.bottom;
      if (!inside) done(null);
    });
    dialog.addEventListener("close", () => {
      dialog.remove();
      resolve(chosen);
    });
    document.body.append(dialog);
    dialog.showModal();
    dialog.querySelector(".version-play")?.focus();
  });
}
