/**
 * The typographic breaks between the home page's rows: a pull-quote, and the
 * numbered "This month" column.
 *
 * A page of nothing but rows of cards reads as a catalogue; a line of type
 * set large, with room around it, is what makes it read as a magazine. The
 * line is always a real film's tagline, credited and linked to the film, and
 * the column is always what actually arrived.
 */

import { el } from "../dom.js";
import { genresOf } from "./genres.js";

/** A film's tagline as a pull-quote, credited to the film. */
export function pullQuote(set) {
  const figure = el("figure", "pull-quote");
  const quote = el("blockquote");
  quote.append(el("p", null, set.tagline));
  figure.append(quote);
  const credit = el("figcaption");
  const link = el("a", null, [set.title ?? set.setId, set.year].filter(Boolean).join(", "));
  link.href = `#/film/${encodeURIComponent(set.setId)}`;
  // The rule above the credit stands where a dash would.
  credit.append(link);
  figure.append(credit);
  return figure;
}

/** Films that arrived this month, numbered as a magazine numbers its contents. */
export function thisMonth(films) {
  const column = el("aside", "this-month");
  column.setAttribute("aria-labelledby", "home-this-month");
  const title = el("h2", "eyebrow this-month-title", "This month");
  title.id = "home-this-month";
  column.append(title);
  const list = el("ol");
  films.forEach((set, index) => {
    const item = el("li");
    const link = el("a");
    link.href = `#/film/${encodeURIComponent(set.setId)}`;
    link.append(el("span", "this-month-num", String(index + 1).padStart(2, "0")));
    const text = el("span", "this-month-text");
    text.append(el("span", "this-month-name", set.title ?? set.setId));
    const meta = [set.year, genresOf(set)[0]].filter(Boolean).join(" · ");
    if (meta) text.append(el("span", "this-month-meta", meta));
    link.append(text);
    item.append(link);
    list.append(item);
  });
  column.append(list);
  return column;
}
