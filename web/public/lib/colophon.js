/**
 * What the whole library adds up to, and where the catalogue came from.
 *
 * Set across the foot of the page because that is where a book prints the
 * same kind of fact: how many of a thing there are, how much of it, and when
 * this edition was made. Nobody comes to the page for it, and everybody
 * wants it once.
 *
 * Pure, and given the sets rather than fetching them: the page already holds
 * every row, so counting them here costs one pass and no round trip.
 */

import { countOf, humanSize } from "./format.js";
import { isDocument } from "./library.js";

/** A day, in milliseconds. */
const DAY = 86_400_000;

/**
 * How old a published catalogue is, in the words someone would use.
 *
 * Deliberately coarse. The useful distinction is "current" against "this
 * stopped refreshing a while ago", and an exact timestamp invites arithmetic
 * to answer a question that is really yes-or-no.
 */
export function catalogueAge(publishedAt, now) {
  if (!Number.isFinite(publishedAt)) return null;
  const days = Math.floor((now - publishedAt) / DAY);
  // A clock that disagrees with the publisher's is likelier than a package
  // from the future, and "published in -2 days" helps nobody.
  if (days < 0) return "published just now";
  if (days === 0) return "published today";
  if (days === 1) return "published yesterday";
  if (days < 14) return `published ${days} days ago`;
  if (days < 60) return `published ${Math.floor(days / 7)} weeks ago`;
  return `published ${Math.floor(days / 30)} months ago`;
}

/**
 * Total running time, as whole hours.
 *
 * Hours rather than `humanDuration`'s hours-and-minutes: across a library the
 * minutes are noise, and "412 hours" is the figure a person repeats.
 */
function runtimeLabel(sets) {
  const seconds = sets.reduce((total, set) => total + (Number(set.duration) || 0), 0);
  const hours = Math.round(seconds / 3600);
  return hours > 0 ? countOf(hours, "hour") : "";
}

function bytesLabel(sets) {
  const bytes = sets.reduce((total, set) => total + (Number(set.total) || 0), 0);
  return bytes > 0 ? humanSize(bytes) : "";
}

/**
 * The line itself.
 *
 * `catalog` is what `/api/player` said, and may be absent: the page renders
 * before that answer arrives on a slow start, and a colophon that waits for
 * it would flash empty. Without it the line is simply shorter.
 *
 * @param {Array<{kind?: string, duration?: number|null, total?: number}>} sets
 * @param {{origin?: string, publishedAt?: number|null, schema?: number|null}|null} catalog
 * @param {Date} [now]
 */
export function colophonLine(sets, catalog, now = new Date()) {
  // Counted by what each thing is, and `isDocument` rather than a kind string
  // so there is one answer to that. Subtracting the known kinds from the
  // total was how a course's PDFs came to be counted as films: they are
  // neither, and the rule that put them on the courses shelf did not reach
  // this line.
  //
  // An unrecognised kind still counts as a film, which is the shelf it lands
  // on, so nothing silently vanishes from a total the viewer might check
  // against what they uploaded.
  const episodes = sets.filter((set) => set.kind === "ep").length;
  const lessons = sets.filter((set) => set.kind === "tut").length;
  const documents = sets.filter(isDocument).length;
  const films = sets.length - episodes - lessons - documents;

  const parts = [
    films > 0 ? countOf(films, "film") : "",
    episodes > 0 ? countOf(episodes, "episode") : "",
    lessons > 0 ? countOf(lessons, "lesson") : "",
    documents > 0 ? countOf(documents, "document") : "",
    runtimeLabel(sets),
    bytesLabel(sets),
  ];

  if (catalog) {
    // A local index has no publication date because nothing published it —
    // the player is reading the uploader's own library off this disk. A
    // package and a channel snapshot were both published, and say when.
    parts.push(
      catalog.origin === "local"
        ? "read from this machine"
        : (catalogueAge(catalog.publishedAt ?? null, now.getTime()) ??
            (catalog.origin === "channel" ? "the channel's index" : "published catalogue")),
    );
    if (Number.isFinite(catalog.schema)) parts.push(`schema ${catalog.schema}`);
  }

  const line = parts.filter(Boolean).join(" · ");
  // An empty library still deserves a sentence rather than a blank strip.
  return line || "Nothing in the library yet";
}
