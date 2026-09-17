/**
 * Turning a flat catalog into the three shelves a viewer expects.
 *
 * The index stores one row per set and says what kind it is. The shape a
 * library page needs — films, shows with seasons, courses with chapters — is
 * derived here, in one place, rather than inside a render loop where it
 * cannot be tested.
 *
 * A course nests, and nests unevenly: four levels in one branch, one in
 * another, and folders that hold lessons beside a subfolder. That shape is
 * kept rather than flattened, because flattening it produces twenty-one
 * sibling headings that each repeat the same two parent folders and tell a
 * viewer nothing about how the course is actually built.
 *
 * Plain JavaScript because the browser loads this file directly; the tests
 * import the same file, so there is one copy rather than two that drift.
 */

/**
 * @typedef {import("./library.js").CatalogSet} CatalogSet
 * @typedef {import("./library.js").Division} Division
 */

/** Episode numbers are text in the index ("1", "1-2"); sort by the number. */
function episodeOrder(set) {
  const match = /\d+/.exec(set.episode ?? "");
  return match ? Number(match[0]) : Number.MAX_SAFE_INTEGER;
}

function byTitle(a, b) {
  return (a.title ?? "").localeCompare(b.title ?? "", undefined, { numeric: true });
}

/**
 * Where a set sits inside its collection, as folders from the top down.
 *
 * A path is the good case: it is the shape the course had on disk. A chapter
 * title comes next, and a set uploaded before either existed still has its
 * number. Seasons carry theirs alongside, because they sort by it rather than
 * by name.
 */
function trailOf(set) {
  if (set.path) return { names: set.path.split("/").filter(Boolean), season: null };
  if (set.chap) return { names: [set.chap], season: null };
  if (set.kind === "ep") {
    const season = set.season ?? null;
    return { names: [season != null ? `Season ${season}` : "Episodes"], season };
  }
  const chapter = set.season ?? 1;
  return { names: [`Chapter ${chapter}`], season: chapter };
}

/** Finds or creates the child of `parent` called `name`. */
function descend(parent, name, season) {
  let node = parent.children.find((child) => child.title === name);
  if (!node) {
    node = { title: name, season, items: [], children: [] };
    parent.children.push(node);
  }
  return node;
}

/** Sorts a division and everything under it, in place. */
function sortDivision(division) {
  division.items.sort((a, b) => episodeOrder(a) - episodeOrder(b) || byTitle(a, b));
  division.children.sort((a, b) => {
    if (a.season != null && b.season != null) return a.season - b.season;
    return a.title.localeCompare(b.title, undefined, { numeric: true });
  });
  for (const child of division.children) sortDivision(child);
}

/**
 * Every division under these, each before the ones beneath it.
 *
 * The one descent through the tree. Counting sets, counting the folders that
 * hold them and finding the first of them are the same walk with different
 * questions asked of it, and answering them separately meant three places to
 * change the day a division earns a reason to be skipped.
 */
function* walk(divisions) {
  for (const division of divisions) {
    yield division;
    yield* walk(division.children);
  }
}

/** The first set anywhere under these divisions, in the order they display. */
export function firstItemOf(divisions) {
  for (const division of walk(divisions)) {
    if (division.items.length > 0) return division.items[0];
  }
  return null;
}

/** Groups one kind's sets by container (show or course), then by folder. */
function collections(sets, fallbackName) {
  const byName = new Map();

  for (const set of sets) {
    const name = set.show ?? fallbackName;
    if (!byName.has(name)) byName.set(name, { title: name, season: null, items: [], children: [] });

    const { names, season } = trailOf(set);
    let node = byName.get(name);
    for (const folder of names) node = descend(node, folder, season);
    node.items.push(set);
  }

  return [...byName.values()]
    .map((root) => {
      sortDivision(root);
      const divisions = [...walk(root.children)];
      return {
        name: root.title,
        divisions: root.children,
        // Folders that actually hold lessons, however deep: what a shelf card
        // means by a chapter. A folder of folders is structure, not a chapter.
        chapters: divisions.filter((d) => d.items.length > 0).length,
        count: divisions.reduce((n, d) => n + d.items.length, 0),
      };
    })
    .sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
}

/**
 * Splits a catalog into films, shows and courses.
 *
 * A kind this does not recognise is shelved with the films rather than
 * dropped: a viewer noticing something in the wrong place can act on it,
 * whereas a title that silently vanishes looks like a failed upload.
 *
 * @param {CatalogSet[]} sets
 */
export function groupLibrary(sets) {
  const episodes = sets.filter((set) => set.kind === "ep");
  const lessons = sets.filter((set) => set.kind === "tut");
  const rest = sets.filter((set) => set.kind !== "ep" && set.kind !== "tut");

  return {
    movies: [...rest].sort(byTitle),
    series: collections(episodes, "Unknown show"),
    tutorials: collections(lessons, "Unknown course"),
  };
}
