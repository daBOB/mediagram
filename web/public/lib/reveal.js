/**
 * Fades sections up as they scroll into view, once each.
 *
 * An IntersectionObserver rather than a scroll listener: it costs nothing
 * between crossings and never forces a layout. Everything is shown at once
 * where there is no observer, or where the viewer asked for less motion —
 * and the stylesheet hides nothing until `motion` is set on the root, so a
 * page whose script never ran is a page with nothing missing.
 */

let observer = null;
/** The address last revealed, so a redraw of the same page shows at once. */
let revealedHash = null;

function canAnimate() {
  if (typeof IntersectionObserver !== "function") return false;
  return !globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
}

/**
 * Marks every `.reveal` under `root` to be shown as it arrives.
 *
 * Only on arrival at a page. A redraw of the page already showing — a sync
 * from another device, a pin — puts everything back at once: fading it all in
 * again would make the page blink at the viewer for something they did not do.
 */
export function revealWithin(root) {
  if (!canAnimate()) return;
  document.documentElement.classList.add("motion");
  // The blocks the last drawing observed are gone; stop holding on to them.
  observer?.disconnect();
  const redraw = revealedHash === location.hash;
  revealedHash = location.hash;
  if (redraw) {
    for (const node of root.querySelectorAll(".reveal")) node.classList.add("in");
    return;
  }
  observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      entry.target.classList.add("in");
      observer.unobserve(entry.target);
    }
  }, { rootMargin: "0px 0px -40px 0px", threshold: 0 });
  for (const node of root.querySelectorAll(".reveal")) observer.observe(node);
}
