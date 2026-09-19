/**
 * Hand-built lists: the shelf of them, and the inside of one.
 *
 * Kept apart from `shelf-view.js` because these are the only shelves a viewer
 * can change. Everything there renders the catalog, which is read-only and
 * arrives whole; everything here has a control attached to it.
 */

import { el } from "./dom.js";
import { countOf } from "./format.js";
import * as state from "./watch-state.js";
import { addTitles } from "./collection-add.js";

/** The lists, each a door to its own view, with a way to make another. */
export function listsView(onOpen, onChanged) {
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
    void state.createCollection(name).then((made) => {
      if (made) onChanged();
    });
  });
  block.append(make);
  return block;
}

/** The controls at the head of one list: fill it, rename it, or delete it. */
export function listControls(list, onChanged, onGone) {
  const head = el("div", "list-head");
  const bar = el("div", "list-controls");

  // First, because it is the one a viewer opening an empty list is looking
  // for. Rename and delete are things you do to a list you already have.
  const { trigger, panel } = addTitles(list, onChanged);
  bar.append(trigger);

  const rename = el("button", "quiet", "Rename");
  rename.addEventListener("click", () => {
    const name = window.prompt("Name for the list", list.name);
    if (name === null) return;
    void state.renameCollection(list.id, name).then((ok) => ok && onChanged());
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

/** One list's titles, each with a way off the list. */
export function listView(list, sets, onPlay, onChanged) {
  const block = el("section", "level");

  for (const set of sets) {
    const row = el("div", "row");
    const open = el("button", "row-open");
    open.append(el("b", null, set.title ?? set.setId));
    const where = [set.show, set.year].filter(Boolean).join(" · ");
    if (where) open.append(el("span", null, where));
    open.addEventListener("click", () => onPlay(set));
    row.append(open);

    const remove = el("button", "quiet", "Remove");
    remove.addEventListener("click", () => {
      state.setInCollection(list.id, set.setId, false);
      onChanged();
    });
    row.append(remove);
    block.append(row);
  }

  return block;
}
