/**
 * The ☰ sidebar: the run beside the picture a season at a time, the title
 * playing marked and the watched ones greyed. The video keeps playing under it.
 *
 * Its rows are the show page's own (`seasonBlock`), so a season reads the same
 * in both places — the tick, the progress line and the click are theirs. The
 * sidebar only says less (the runtime, not the file) and marks the one playing.
 */

import { el } from "../dom.js";
import { humanDuration } from "../format.js";
import { isWatched } from "../watch-state.js";
import { seasonBlock } from "../catalog/course-view.js";
import { episodeGroups } from "./episode-list.js";

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 * @param {{onPick: (set: CatalogSet) => void, onClose?: () => void}} deps `onPick`
 *   opens a row the way ⏭ opens the next title; `onClose` hears it shut.
 */
export function mountEpisodeSidebar({ onPick, onClose }) {
  const dialog = document.getElementById("player");
  const button = document.getElementById("episodes");
  const panel = document.getElementById("episode-sidebar");

  const back = el("button", "tsp", "‹");
  back.setAttribute("aria-label", "Previous season");
  const title = el("h2", "sidebar-title");
  const on = el("button", "tsp", "›");
  on.setAttribute("aria-label", "Next season");
  const shut = el("button", "tsp", "✕");
  shut.setAttribute("aria-label", "Close episodes");
  const head = el("header", "sidebar-head");
  head.append(back, title, on, shut);
  const rows = el("div", "sidebar-rows");
  panel.append(head, rows);

  /** The open title's run as `episodeGroups` gives it, the season in view, and the title. */
  let model = null;
  let shown = 0;
  let playing = null;
  /** The row of the title playing, while the season in view holds it. */
  let playingRow = null;

  /** A row as the show page draws it, told what the sidebar says differently. */
  function mark(row, set) {
    const meta = [...row.children].find((child) => child.className === "meta");
    if (isWatched(set.setId)) row.classList.add("is-watched");
    if (set.setId !== playing) {
      if (meta) meta.textContent = humanDuration(set.duration);
      return;
    }
    // Not a way to restart what is already playing: that is ↺'s job.
    playingRow = row;
    row.disabled = true;
    row.setAttribute("aria-current", "true");
    if (meta) meta.textContent = "Now playing";
  }

  function draw() {
    const group = model.groups[shown];
    title.textContent = group.title;
    back.hidden = model.groups.length < 2;
    on.hidden = model.groups.length < 2;
    back.disabled = shown === 0;
    on.disabled = shown === model.groups.length - 1;
    playingRow = null;
    const block = seasonBlock(group, (set) => {
      close();
      onPick(set);
    });
    group.items.forEach((set, at) => mark(block.children[at], set));
    rows.replaceChildren(block);
  }

  function show() {
    shown = model.at;
    draw();
    panel.hidden = false;
    // The card stands clear of the sidebar while it is up.
    dialog.classList.add("sidebar-open");
    button.setAttribute("aria-expanded", "true");
    panel.focus();
    // A season runs to thirty rows; the one playing is the one being looked for.
    playingRow?.scrollIntoView({ block: "center" });
  }

  function close() {
    if (panel.hidden) return;
    panel.hidden = true;
    dialog.classList.remove("sidebar-open");
    button.setAttribute("aria-expanded", "false");
    onClose?.();
  }

  /**
   * As each title opens. `collection` is its show or course, or `null` — for
   * a film, and for a hand-built list, which ⏮/⏭ walk but which has no seasons
   * to group and is its own page's table of contents.
   * @param {CatalogSet} set
   * @param {{name: string, divisions: import("../library.js").Division[]}|null} collection
   */
  function open(set, collection) {
    close();
    playing = set.setId;
    model = episodeGroups(collection, set.setId);
    button.hidden = model === null;
  }

  button.addEventListener("click", () => (panel.hidden ? show() : close()));
  shut.addEventListener("click", close);
  back.addEventListener("click", () => {
    shown -= 1;
    draw();
  });
  on.addEventListener("click", () => {
    shown += 1;
    draw();
  });
  dialog.addEventListener("keydown", (event) => {
    // An open menu takes Esc first and marks it taken; the sidebar is next.
    if (event.key !== "Escape" || event.defaultPrevented || panel.hidden) return;
    event.preventDefault();
    close();
    button.focus();
  });

  return { open, close, isOpen: () => !panel.hidden };
}
