/**
 * Going to another page: where it opens, and how it arrives.
 *
 * A page gone to fresh opens at its top; one come back to with Back or
 * Forward opens where it was left. The browser's own restoration cannot do
 * that here: it restores the offset before the router has drawn the page it
 * belongs to, so the offset lands on the page being left and is clamped to
 * that page's length. A page that is still fetching when it is drawn — search
 * results, a film's overview — is short at that moment, so it can open higher
 * than it was left.
 *
 * The page rises in on an animation of its own, not a view transition. A view
 * transition shows snapshots, and moving the scroll offset under them — to the
 * top, or clamped to a shorter page — sometimes drew them out of place: a
 * blank screen for a moment, then the page snapping in. It also lifted the
 * page above the masthead and rail while it ran.
 */

history.scrollRestoration = "manual";

/** Where the viewer was on each history entry when they left it. */
const leftAt = new Map();
let shown = entryId();

/** The current history entry's id, stamped on it the first time it is shown. */
function entryId() {
  if (typeof history.state?.page !== "string") {
    history.replaceState({ ...history.state, page: Math.random().toString(36).slice(2) }, "");
  }
  return history.state.page;
}

/** Draws the page the address now names into `main`, rising in unless `still`. */
export function turnPage(main, draw, still) {
  leftAt.set(shown, window.scrollY);
  shown = entryId();
  draw();
  window.scrollTo(0, leftAt.get(shown) ?? 0);
  if (still) return;
  // Taken off and put back, the animation plays again only with a style
  // flush in between; the scroll above has already laid the page out.
  main.classList.remove("turning");
  void main.offsetWidth;
  main.classList.add("turning");
}
