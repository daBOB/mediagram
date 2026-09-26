/**
 * A department's opening page, the way a magazine opens its cinema or its
 * television section: a spaced-caps kicker, the department's name set very
 * large, one line of real figures, and a backdrop from inside the department
 * with that title's tagline as the pull-quote.
 *
 * Every word is the library's own — counts, and a real film's tagline
 * credited to the film. Nothing here is a slogan.
 */

import { el } from "../dom.js";
import { artworkUrl } from "./plate.js";

/**
 * @param {{ kicker: string, title: string, line: string,
 *   lead?: import("../library.js").CatalogSet|null, leadName?: string|null, leadHref?: string|null }} spec
 */
export function departmentHero({ kicker, title, line, lead = null, leadName = null, leadHref = null }) {
  const art = lead?.backdrop ?? null;
  const hero = el("header", art ? "dept-hero" : "dept-hero no-art");
  if (art) {
    const figure = el("div", "dept-art");
    figure.setAttribute("aria-hidden", "true");
    const image = el("img");
    image.src = artworkUrl(art);
    image.alt = "";
    image.decoding = "async";
    image.setAttribute("fetchpriority", "high");
    figure.append(image);
    hero.append(figure);
  }
  const copy = el("div", "dept-copy");
  copy.append(el("p", "eyebrow dept-kicker", kicker), el("h1", "dept-title", title), el("p", "dept-line", line));
  hero.append(copy);

  if (art && lead?.tagline && leadName) {
    const quote = el("figure", "dept-quote");
    quote.append(el("blockquote", null, `“${lead.tagline}”`));
    const credit = el("figcaption");
    const link = el("a", null, leadName);
    link.href = leadHref ?? "#";
    credit.append(link);
    quote.append(credit);
    hero.append(quote);
  }
  return hero;
}

/**
 * A titled row inside a department, with an optional way to the whole shelf.
 * `extra` is a control that sits between the title and the link (the reel).
 * @param {string} title @param {Node} body @param {{href: string, label?: string}|null} [more]
 * @param {Node|null} [extra]
 */
export function deptRow(title, body, more = null, extra = null) {
  const section = el("section", "dept-row reveal");
  const head = el("header", "row-head");
  head.append(el("h2", null, title));
  if (extra) head.append(extra);
  if (more) {
    const link = el("a", "see-all", more.label ?? "See all");
    link.href = more.href;
    link.setAttribute("aria-label", `${more.label ?? "See all"}: ${title}`);
    head.append(link);
  }
  section.append(head, body);
  return section;
}
