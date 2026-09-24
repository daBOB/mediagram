/**
 * Hand-built lists: the shelf of them, and the inside of one.
 *
 * Kept apart from `shelf-view.js` because these are the only shelves a viewer
 * can change. Everything there renders the catalog, which is read-only and
 * arrives whole; everything here has a control attached to it.
 */

import { el } from "../dom.js";
import { countOf, humanDuration } from "../format.js";
import { offlineBadge } from "./set-badge.js";
import { plateOf } from "./plate.js";
import * as state from "../watch-state.js";
import { addTitles } from "./collection-add.js";
import { crumbs, heading } from "./shelf-view.js";

/** Complete list pages; the application supplies navigation and playback. */
export function renderLists(main, onOpen) {
  heading(main, "Collections", countOf(state.collections().length, "list"));
  main.append(listsView(onOpen));
}

export function renderList(main, list, sets, { play, onEditing, onGone }) {
  main.append(crumbs("collections", "Collections", null, []));
  if (!list) {
    main.append(el("p", "error", "That list is not here any more."));
    return;
  }
  heading(main, list.name, countOf(list.items.length, "title"));
  main.append(listControls(list, onEditing, onGone, sets.length > 0 ? () => play(sets[0], sets) : null));
  if (sets.length === 0) {
    main.append(el("p", "empty", "Nothing in this list yet. Use Add titles above, or Add to… in the player."));
    return;
  }
  main.append(listView(list, sets, (set) => play(set, sets)));
}

/** The lists, each a door to its own view, with a way to make another. */
export function listsView(onOpen) {
  const block = el("section", "level");

  for (const list of state.collections()) {
    const row = el("button", "row folder");
    row.append(el("div", "num", ""));

    const title = el("div", "title");
    title.append(el("b", null, list.name));
    row.append(title);
    row.append(el("div", "meta", countOf(list.items.length, "title")));
    row.append(el("span", "chevron", "›"));
    row.addEventListener("click", () => onOpen(list.id));
    block.append(row);
  }

  const make = el("button", "make", "＋  New list");
  make.addEventListener("click", () => {
    // `prompt` rather than a hand-built dialog: naming a list is one line of
    // text, and a modal built here would be a worse version of the one the
    // browser already has — including for a viewer using a screen reader.
    const name = window.prompt("Name for the list");
    if (name === null) return;
    void state.createCollection(name);
  });
  block.append(make);
  return block;
}

/** The controls at the head of one list: fill it, rename it, or delete it. */
export function listControls(list, onEditing, onGone, onPlayAll) {
  const head = el("div", "list-head");
  const bar = el("div", "list-controls");

  // The thing a filled list is for. Absent while there is nothing to play,
  // rather than present and doing nothing.
  if (onPlayAll) {
    const all = el("button", "quiet play-all", "Play all");
    all.addEventListener("click", onPlayAll);
    bar.append(all);
  }

  // Before rename and delete, because it is what a viewer opening an empty
  // list is looking for. Those are things you do to a list you already have.
  const { trigger, panel } = addTitles(list, onEditing);
  bar.append(trigger);

  const rename = el("button", "quiet", "Rename");
  rename.addEventListener("click", () => {
    const name = window.prompt("Name for the list", list.name);
    if (name === null) return;
    void state.renameCollection(list.id, name);
  });

  const remove = el("button", "quiet", "Delete list");
  remove.addEventListener("click", () => {
    // Asked, because a list is the one thing here that took effort to build
    // and the only one whose loss cannot be undone by watching something.
    if (!window.confirm(`Delete "${list.name}"? The titles stay in the library.`)) return;
    void state.deleteCollection(list.id).then((ok) => ok && onGone());
  });

  bar.append(rename, remove);
  head.append(bar, panel);
  return head;
}

/**
 * One list's titles, each with a way off the list.
 *
 * Given artwork, unlike the rows a course is made of: a list is a run of
 * unrelated films rather than a numbered sequence of one thing, so the
 * picture is what tells them apart at a glance.
 */
export function listView(list, sets, onPlay) {
  const block = el("section", "level");

  for (const set of sets) {
    const row = el("div", "row listed");
    row.append(plateOf(set));

    const open = el("button", "row-open");
    open.append(el("b", null, set.title ?? set.setId));
    const where = [set.show, set.year, humanDuration(set.duration)].filter(Boolean).join(" · ");
    if (where) open.append(el("span", null, where));
    open.addEventListener("click", () => onPlay(set));
    row.append(open);

    // The one fact worth carrying over from the shelf: whether this will play
    // at all, which is what a viewer about to start a run wants to know.
    const held = offlineBadge(set);
    if (held) row.append(held);

    const remove = el("button", "quiet", "Remove");
    remove.addEventListener("click", () => {
      state.setInCollection(list.id, set.setId, false);
    });
    row.append(remove);
    block.append(row);
  }

  return block;
}
