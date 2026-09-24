/**
 * Pages of a long shelf, and the row of links between them.
 *
 * The page lives in the address (`#/movies/page/3`) rather than in memory, so
 * the back button, a reload and a shared link all land where the viewer was.
 * The library is already whole in the page; this only chooses which slice to
 * draw.
 */

import { el } from "../dom.js";

/** Where the pager leaves pages out. */
export const GAP = "gap";

// The longest the row gets with gaps: both ends, three around the current
// page and two gaps.
const FITS = 7;

/**
 * One page of `items`, with the page number brought into range.
 * @template T
 * @param {T[]} items
 * @param {number} page
 * @param {number} size
 * @returns {{items: T[], page: number, pages: number}}
 */
export function pageOf(items, page, size) {
  const pages = Math.max(1, Math.ceil(items.length / size));
  const wanted = Number.isInteger(page) && page >= 1 ? page : 1;
  const current = Math.min(wanted, pages);
  const start = (current - 1) * size;
  return { items: items.slice(start, start + size), page: current, pages };
}

/**
 * The page a hash segment names; anything that is not a positive whole number
 * is the first page, so a mistyped address still shows a shelf.
 * @param {string|undefined} text
 */
export function parsePage(text) {
  return /^[1-9]\d*$/.test(text ?? "") ? Number(text) : 1;
}

/**
 * The address of one page. The first is the plain shelf, so links made before
 * there were pages are still the first page's links.
 * @param {string} section
 * @param {number} page
 */
export function pageHash(section, page) {
  return page <= 1 ? `#/${section}` : `#/${section}/page/${page}`;
}

/**
 * The page numbers the pager shows: both ends, the current page and its
 * neighbours, with a gap wherever pages are left out. A gap that would hide
 * a single page shows that page instead, since a gap is no shorter.
 * @param {number} current
 * @param {number} pages
 * @returns {(number|typeof GAP)[]}
 */
export function pageLinks(current, pages) {
  if (pages <= 1) return [];
  // Seven fit in the row as they are; gaps would save nothing.
  if (pages <= FITS) return Array.from({ length: pages }, (_, index) => index + 1);
  const shown = [1, current - 1, current, current + 1, pages]
    .filter((page) => page >= 1 && page <= pages);
  const unique = [...new Set(shown)].sort((a, b) => a - b);

  /** @type {(number|typeof GAP)[]} */
  const links = [];
  for (const page of unique) {
    const previous = links.at(-1);
    if (typeof previous === "number" && page - previous === 2) links.push(previous + 1);
    else if (typeof previous === "number" && page - previous > 2) links.push(GAP);
    links.push(page);
  }
  return links;
}

/**
 * The row of links under a paged shelf, or null when there is one page.
 * Real links, as the crumbs are: every page is a URL that works on its own.
 * @param {string} section
 * @param {number} current
 * @param {number} pages
 */
export function pager(section, current, pages) {
  const links = pageLinks(current, pages);
  if (links.length === 0) return null;

  const nav = el("nav", "pager");
  nav.setAttribute("aria-label", "Pages");
  const step = (label, page, rel) => {
    if (page < 1 || page > pages) return el("span", "pager-step off", label);
    const link = el("a", "pager-step", label);
    link.href = pageHash(section, page);
    link.rel = rel;
    return link;
  };

  nav.append(step("‹ Prev", current - 1, "prev"));
  for (const page of links) {
    if (page === GAP) {
      nav.append(el("span", "pager-gap", "…"));
    } else if (page === current) {
      const here = el("span", "pager-page here", String(page));
      here.setAttribute("aria-current", "page");
      nav.append(here);
    } else {
      const link = el("a", "pager-page", String(page));
      link.href = pageHash(section, page);
      nav.append(link);
    }
  }
  nav.append(step("Next ›", current + 1, "next"));
  return nav;
}
