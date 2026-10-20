/**
 * The sections of the library a profile is not shown, and a catalogue without them.
 *
 * A section is a department of sets, a key of `SECTIONS`; Collections is a
 * page over them, not one of them. Who may set a profile's list is the
 * server's rule; this only reads it. Not a lock, as a kid's age limit is not:
 * the page leaves the sets out, and the server still answers for one.
 */

import { SECTIONS } from "./catalog/sections.js";
import { isDocument } from "./library.js";

/** The words of the Sections switches and the Everyone line. */
export const SECTION_ACCESS_WORDS = { title: "Sections", hides: "hides" };

/**
 * The section a set sits in, by `groupDepartments`'s rules: anime before
 * anything else, then documentaries, episodes, lessons and documents, and
 * films for whatever is left, a kind this build does not know included.
 */
export function departmentOf(set) {
  if (set.anime) return "anime";
  if (set.kind === "docu") return "documentaries";
  if (set.kind === "ep") return "series";
  if (set.kind === "tut" || isDocument(set)) return "tutorials";
  return "movies";
}

/**
 * The sections `profile` is not shown that this build knows, in the nav's
 * order. None for nobody, nor for the owner, whatever their list says: the
 * rule lets nobody set theirs — a kid wrongly said to be the owner is still a
 * kid. A household's admin sees what the owner left.
 */
export function hiddenSectionsOf(profile) {
  if (!profile || (profile.owner === true && !profile.kids)) return [];
  const listed = profile.hiddenSections ?? [];
  return Object.keys(SECTIONS).filter((key) => listed.includes(key));
}

/** `sets` without those in a `hidden` section. */
export const withoutHidden = (sets, hidden) =>
  (hidden.length === 0 ? sets : sets.filter((set) => !hidden.includes(departmentOf(set))));

/** What the Everyone line adds for `profile` ("hides Anime, Tutorials"), or nothing when it is shown every section. */
export function hidesWords(profile) {
  const hidden = hiddenSectionsOf(profile);
  return hidden.length === 0 ? "" : `${SECTION_ACCESS_WORDS.hides} ${hidden.map((key) => SECTIONS[key].label).join(", ")}`;
}
