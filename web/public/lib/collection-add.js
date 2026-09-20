/**
 * Putting titles into a hand-built list.
 *
 * Kept apart from `collections-view.js` because it is the one thing here that
 * reaches back to the server while the viewer types, and because a list of
 * three hundred and ninety titles cannot be offered as a menu. The search
 * route the shelf already uses is the picker: type, see what matches, click
 * to file it.
 *
 * Clicking a title already on the list takes it off again, so the same row is
 * the way in and the way out and a viewer who files the wrong thing does not
 * have to go looking for where to undo it.
 */

import { el } from "./dom.js";
import { episodeLabel } from "./format.js";
import * as state from "./watch-state.js";

/** How long after the last keystroke before asking the server. */
const PAUSE_MS = 180;

/**
 * What the picker shows, and which of it is already filed.
 *
 * Pure, and the only part of this worth testing on its own: everything else
 * is a node or a fetch.
 *
 * @param {Array<object>} hits what `/api/search` answered
 * @param {Array<string>} items the set ids already on the list
 */
export function pickerRows(hits, items) {
  const held = new Set(items);
  return hits.map((set) => ({ set, inList: held.has(set.setId) }));
}

/** `Widow's Bay · S1E2`, or the year, or nothing. */
function whereFrom(set) {
  return [set.show, episodeLabel(set), set.year].filter(Boolean).join(" · ");
}

/**
 * The "Add titles" control and the panel it opens.
 *
 * Returns both, because the button belongs in the row of controls at the head
 * of the list and the panel belongs underneath it.
 *
 * `onChanged` rebuilds the view around this panel, so it is called when the
 * panel closes rather than on every click. Calling it per title would tear
 * the picker down under the viewer between one addition and the next, which
 * is the whole case this exists to serve.
 *
 * @param {{id: string, items: string[]}} list
 * @param {() => void} onChanged called once, after the panel closes on changes
 */
export function addTitles(list, onChanged) {
  /** The pending debounce, so closing can cancel it. */
  let timer = null;

  const panel = el("div", "add-panel");
  panel.hidden = true;

  const field = el("input", "add-search");
  field.type = "search";
  field.placeholder = "Search the library";
  field.autocomplete = "off";
  field.spellcheck = false;

  const results = el("div", "add-results");
  panel.append(field, results);

  /** Whether anything was filed, so closing without changes rebuilds nothing. */
  let changed = false;

  const trigger = el("button", "quiet", "Add titles");
  trigger.setAttribute("aria-expanded", "false");
  trigger.addEventListener("click", () => {
    const opening = panel.hidden;
    panel.hidden = !opening;
    trigger.setAttribute("aria-expanded", String(opening));
    // Focus on the way in only: moving it on the way out would take the
    // viewer somewhere they did not ask to go.
    // The label says what closing does. While the panel is open the count in
    // the heading is the count from before it opened — the view around this
    // is rebuilt on close, not per title — and "Done" is what explains that
    // without a number that moves under the viewer as they work.
    trigger.textContent = opening ? "Done" : "Add titles";
    if (opening) {
      field.focus();
      return;
    }
    // A keystroke within the last fraction of a second would otherwise fetch
    // and draw into a panel `onChanged` is about to detach.
    if (timer !== null) clearTimeout(timer);
    timer = null;
    if (changed) {
      changed = false;
      onChanged();
    }
  });

  /** The ids filed during this visit, so a row can be drawn without a reload. */
  const here = new Set(list.items);

  function draw(rows) {
    results.textContent = "";
    if (rows.length === 0) {
      results.append(el("p", "add-none", "Nothing matches."));
      return;
    }
    for (const { set, inList } of rows) {
      const row = el("button", inList ? "add-row on" : "add-row");
      row.append(el("b", null, set.title ?? set.setId));
      const where = whereFrom(set);
      if (where) row.append(el("span", null, where));
      // The state and the way to change it are the same word, which is what
      // makes the row readable as a toggle rather than as a label with a
      // button beside it.
      row.append(el("span", "add-mark", inList ? "on the list" : "add"));
      row.addEventListener("click", () => {
        const now = !here.has(set.setId);
        state.setInCollection(list.id, set.setId, now);
        if (now) here.add(set.setId);
        else here.delete(set.setId);
        // Redrawn from what is held rather than from the server: the write is
        // optimistic there too, and asking again would show the old answer.
        draw(rows.map((r) => (r.set.setId === set.setId ? { ...r, inList: now } : r)));
        changed = true;
      });
      results.append(row);
    }
  }

  let sequence = 0;
  field.addEventListener("input", () => {
    if (timer !== null) clearTimeout(timer);
    const query = field.value.trim();
    if (query === "") {
      results.textContent = "";
      return;
    }
    timer = setTimeout(() => {
      // Numbered, so a slow answer to an earlier query cannot land on top of
      // a later one — the trap every search-as-you-type has.
      const mine = ++sequence;
      void fetch(`/api/search?q=${encodeURIComponent(query)}`)
        .then((response) => (response.ok ? response.json() : { hits: [] }))
        .then((answer) => {
          if (mine !== sequence) return;
          draw(pickerRows(answer.hits ?? [], [...here]));
        })
        .catch(() => {
          if (mine !== sequence) return;
          results.textContent = "";
          results.append(el("p", "add-none", "Could not reach the library."));
        });
    }, PAUSE_MS);
  });

  return { trigger, panel };
}
