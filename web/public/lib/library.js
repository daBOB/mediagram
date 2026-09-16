/**
 * Turning a flat catalog into the three shelves a viewer expects.
 *
 * The index stores one row per set and says what kind it is. The shape a
 * library page needs — films, shows with seasons, courses with chapters — is
 * derived here, in one place, rather than inside a render loop where it
 * cannot be tested.
 *
 * Plain JavaScript because the browser loads this file directly; the tests
 * import the same file, so there is one copy rather than two that drift.
 */

/** @typedef {import("./types.js").PlayableSet} PlayableSet */

/** Episode numbers are text in the index ("1", "1-2"); sort by the number. */
function episodeOrder(set) {
  const match = /\d+/.exec(set.episode ?? "");
  return match ? Number(match[0]) : Number.MAX_SAFE_INTEGER;
}

function byTitle(a, b) {
  return (a.title ?? "").localeCompare(b.title ?? "", undefined, { numeric: true });
}

/**
 * Groups one kind's sets by container (show or course), then by the division
 * inside it (season or chapter).
 */
function collections(sets, divisionOf, fallbackName) {
  const byName = new Map();

  for (const set of sets) {
    const name = set.show ?? fallbackName;
    if (!byName.has(name)) byName.set(name, new Map());
    const divisions = byName.get(name);

    const { key, title, season } = divisionOf(set);
    if (!divisions.has(key)) divisions.set(key, { key, title, season, items: [] });
    divisions.get(key).items.push(set);
  }

  return [...byName.entries()]
    .map(([name, divisions]) => {
      const seasons = [...divisions.values()].sort((a, b) => {
        if (a.season != null && b.season != null) return a.season - b.season;
        return a.title.localeCompare(b.title, undefined, { numeric: true });
      });
      for (const division of seasons) {
        division.items.sort((a, b) => episodeOrder(a) - episodeOrder(b) || byTitle(a, b));
      }
      return {
        name,
        seasons,
        count: seasons.reduce((n, division) => n + division.items.length, 0),
      };
    })
    .sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
}

/**
 * Which division of its collection a set belongs to.
 *
 * Sorted by path when there is one, so folders read in the order someone
 * browsing the course on disk would see them; by number otherwise.
 */
function divisionOf(set) {
  if (set.path) {
    return { key: set.path, title: set.path, season: null };
  }
  if (set.chap) {
    return { key: set.chap, title: set.chap, season: null };
  }
  if (set.kind === "ep") {
    return {
      key: `s${set.season ?? 0}`,
      title: set.season != null ? `Season ${set.season}` : "Episodes",
      season: set.season ?? null,
    };
  }
  return { key: `c${set.season ?? 0}`, title: `Chapter ${set.season ?? 1}`, season: set.season ?? 1 };
}

/**
 * Splits a catalog into films, shows and courses.
 *
 * A kind this does not recognise is shelved with the films rather than
 * dropped: a viewer noticing something in the wrong place can act on it,
 * whereas a title that silently vanishes looks like a failed upload.
 *
 * @param {PlayableSet[]} sets
 */
export function groupLibrary(sets) {
  const episodes = sets.filter((set) => set.kind === "ep");
  const lessons = sets.filter((set) => set.kind === "tut");
  const rest = sets.filter((set) => set.kind !== "ep" && set.kind !== "tut");

  return {
    movies: [...rest].sort(byTitle),

    series: collections(episodes, divisionOf, "Unknown show"),

    // The folder path is the good case: a course nests unevenly and its
    // chapter numbers are made unique across the whole course, so they stop
    // describing any shape a person recognises. A chapter title comes next,
    // and a course uploaded before either existed still has its number.
    tutorials: collections(lessons, divisionOf, "Unknown course"),
  };
}
