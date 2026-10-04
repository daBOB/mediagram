/**
 * Which nav link names the page showing — the rail's and the departments
 * bar's alike, one rule for both, so a page is never named by a link for
 * somewhere else.
 */

/**
 * Marks the link for `section` current, and only it.
 *
 * Focus goes with the mark. A link clicked to get somewhere keeps focus when
 * the viewer moves on by a way that does not take it — Back, Forward, a card
 * that is not itself focusable — and the next key press, Alt+Left included,
 * rings it as the page's own: Collections lit on My List. A link that no
 * longer names the page lets go of focus; the one that does keeps it.
 * @param {Iterable<HTMLAnchorElement>} links
 * @param {string} section what `sectionOf` says the address showing lights up
 */
export function markCurrent(links, section) {
  for (const link of links) {
    const current = link.dataset.section === section;
    link.classList.toggle("active", current);
    if (current) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
    if (!current && link === document.activeElement) link.blur();
  }
}
