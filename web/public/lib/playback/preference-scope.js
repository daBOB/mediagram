/**
 * What "this show" means when a choice is remembered.
 *
 * Choosing English is choosing it once, not once per episode — so a
 * preference has to belong to something larger than the set it was made on.
 * The question is what.
 *
 * Deliberately the same answer the shelves already use. `library.js` groups a
 * kind's sets by `set.show`, so that is what a viewer means by a series or a
 * course, and a second idea of it here would produce a player that remembered
 * a choice for a group the shelf does not draw.
 */

/**
 * The key a choice made on `set` is filed under.
 *
 * Three answers, most precise first:
 *
 * - **`showKey`**, which is TMDB-backed and therefore survives a title being
 *   renamed, re-ripped or re-uploaded. Absent for anything the metadata pass
 *   could not identify, which is most of a private course.
 * - **`show`**, the name the shelves group by. Two unrelated shows sharing a
 *   name would share a preference, which is a wrong subtitle size and not a
 *   wrong anything else.
 * - **`setId`**, for a one-off: a film with no TMDB id and no series.
 *
 * Prefixed, so the three namespaces cannot collide — a course called
 * `tmdb-tv-1399` would otherwise be filed with Game of Thrones.
 */
export function scopeOf(set) {
  if (!set) return null;
  const key = text(set.showKey);
  if (key !== null) return `key:${key}`;
  const show = text(set.show);
  if (show !== null) return `show:${show}`;
  const setId = text(set.setId);
  return setId === null ? null : `set:${setId}`;
}

/** A usable string, or nothing. Whitespace is not a name. */
function text(value) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}
