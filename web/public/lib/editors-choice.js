/**
 * The household's editor's choice: the one title the home page's features
 * lead with, pinned by hand from a film or series page.
 *
 * Kept apart from `watch-state.js` because it belongs to no profile — like
 * the Kids mark, it is a fact about the library — and because the home page
 * is its only reader. The server owns it and syncs it between devices; this
 * holds the answer the page last heard and says when it changes.
 */

/** @type {string|null} */
let pick = null;
const listeners = new Set();

function notify() {
  for (const listener of listeners) {
    try { listener(); }
    catch (error) { console.error("Could not update an editor's choice view", error); }
  }
}

/** The pinned set id, or `null` when nothing is pinned. */
export const editorsChoice = () => pick;

/** Calls `listener` whenever the pick changes; returns the unsubscribe. */
export function onEditorsChoice(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * Asks the server for the pick. Quiet on failure: a page that cannot ask
 * shows the features' own fallback, which is what it shows with no pin.
 */
export async function loadEditorsChoice() {
  try {
    const response = await fetch("/api/editors-choice");
    if (!response.ok) return;
    const said = await response.json();
    const next = typeof said?.setId === "string" ? said.setId : null;
    if (next === pick) return;
    pick = next;
    notify();
  } catch {
    // Unreachable for a moment; the next state event asks again.
  }
}

/**
 * Pins `setId`, or unpins it. Shown at once and written behind, as the Kids
 * mark is: a pin that failed to save is corrected by the next load.
 */
export function setEditorsChoice(setId, pinned) {
  const before = pick;
  if (pinned) pick = setId;
  else if (pick === setId) pick = null;
  if (pick !== before) notify();
  void fetch(`/api/editors-choice/${encodeURIComponent(setId)}`, {
    method: pinned ? "PUT" : "DELETE",
    headers: pinned ? { "content-type": "application/json" } : {},
    body: pinned ? "{}" : undefined,
  }).catch(() => {});
}
