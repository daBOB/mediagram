/**
 * Age ratings, and what they decide about a kids profile.
 *
 * The rating is TMDB's for the library's country — an FSK in Germany — carried
 * on every catalog row as `fsk` (`"12"`), or `null` when the title has none.
 * Every kids profile has its own limit, 6 or 12, set by the grown-up it
 * belongs to. The rules for a kid with limit N, as the household chose them:
 *
 *  - Rated N or under: for that kid by itself. Nobody has to mark it, and a
 *    mark cannot take it off — the rating decides.
 *  - Rated above N: not for that kid. A hand mark does not bring it back.
 *  - Unrated: only what someone marked by hand, and only from an age at or
 *    under N — "from 6" is for every kid, "from 12" for a 12 only.
 *
 * Pure, so the rules are tested without a browser; the catalog filter and the
 * player's Kids control only ask.
 */

/**
 * The rating as an age, or `null` when there is none that reads as one.
 * FSK is always a bare number; a letter rating from another country is not an
 * age this rule can compare, so it counts as unrated.
 */
export function ageOf(set) {
  const text = String(set?.fsk ?? "").trim();
  return /^\d{1,2}$/.test(text) ? Number(text) : null;
}

/** `"FSK 12"`, or `null` for an unrated title. */
export function ageLabel(set) {
  const age = ageOf(set);
  return age === null ? null : `FSK ${age}`;
}

/**
 * The limit a profile sees up to: 6 or 12 on a kids profile, `null` for a
 * grown-up or for nobody. Anything but 6 reads as 12 — the limit every kid
 * had before each had its own — so a kid is never taken for a grown-up.
 */
export const kidsLimitOf = (profile) => (profile?.kids === true ? (profile.kidsAge === 6 ? 6 : 12) : null);

/** `"safe"`, `"unsafe"`, or `"unrated"` — which of the three rules applies at `limit`. */
export function kidsVerdict(set, limit) {
  const age = ageOf(set);
  if (age === null) return "unrated";
  return age <= limit ? "safe" : "unsafe";
}

/**
 * The catalog a kid with this limit sees: rated at or under it, or unrated and
 * marked by hand from an age at or under it. Applied once to the whole
 * catalog, so every shelf, search and reel built from it agrees.
 * @param {import("./library.js").CatalogSet[]} sets
 * @param {Map<string, number>} marks set id -> the age it is for kids from, 6 or 12
 * @param {number} limit the kid's own, 6 or 12
 */
export function forKidsProfile(sets, marks, limit) {
  return sets.filter((set) => {
    const verdict = kidsVerdict(set, limit);
    return verdict === "safe" || (verdict === "unrated" && (marks.get(set.setId) ?? Infinity) <= limit);
  });
}
