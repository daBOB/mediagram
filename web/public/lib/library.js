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

/**
 * True for a set that is a document rather than something to play.
 *
 * The kind, and nothing else. Going by container would mean teaching this
 * every format a course folder might ever hold; the uploader already decided
 * and said so.
 */
export function isDocument(set) {
  return set.kind === "doc";
}

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
function getOrCreateChildDivision(parent, name, season) {
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
 * Counts do not depend on display order, so they can share this simple walk.
 * Playback uses playableInOrder to interleave folders and lessons by number.
 */
function* walk(divisions) {
  for (const division of divisions) {
    yield division;
    yield* walk(division.children);
  }
}

/**
 * The first playable set anywhere under these divisions, in display order.
 *
 * What a collection's play button starts with, so it skips documents: a
 * course whose first folder is its workbooks would otherwise open a PDF.
 */
export function firstItemOf(divisions) {
  return playableInOrder({ items: [], children: divisions }).next().value ?? null;
}

/**
 * What sits under `division`, at whatever depth, counted in one walk.
 *
 * One walk rather than two: both figures are wanted together everywhere they
 * are wanted at all — a heading and the row that opens a folder each say
 * "n lessons · m documents" — and walking a three-hundred-lesson course twice
 * to write one line is work for nothing.
 */
export function countsUnder(division) {
  let lessons = 0;
  let documents = 0;
  for (const node of walk([division])) {
    for (const set of node.items) {
      if (isDocument(set)) documents += 1;
      else lessons += 1;
    }
  }
  return { lessons, documents };
}

/** How many lessons sit under `division`, at whatever depth. */
export function lessonsUnder(division) {
  return countsUnder(division).lessons;
}

/** How many documents sit under `division`, at whatever depth. */
export function documentsUnder(division) {
  return countsUnder(division).documents;
}

/**
 * The division `names` leads to, as folders from the top down.
 *
 * An empty trail is the collection itself, which is not a division and has to
 * be stood in for: a synthetic one holding the top-level folders, so that
 * every level of a course — including the first — is one object with `items`
 * and `children` and the view has a single shape to render.
 *
 * `null` means the trail names a folder that is not there, which is what a
 * hand-edited or stale URL produces.
 */
export function divisionAt(divisions, names) {
  let here = { title: null, season: null, items: [], children: divisions };
  for (const name of names) {
    const next = here.children.find((child) => child.title === name);
    if (!next) return null;
    here = next;
  }
  return here;
}

/** The number a lesson or a folder leads with, or `null` for neither. */
function leadingNumber(text) {
  const match = /^\s*(\d+)/.exec(text ?? "");
  return match ? Number(match[1]) : null;
}

/**
 * One level's lessons and folders, in the order the course puts them.
 *
 * Interleaved rather than lessons-then-folders, because a folder is numbered
 * in the same sequence as the lessons around it. "3. Signal" holds lessons
 * numbered 1 to 21 with 14 missing, and the folder standing in that gap is
 * called "14. Exkurs TWS" — it belongs between 13 and 15, which is where the
 * course put it and where someone working through it will look for it.
 *
 * Anything unnumbered sorts to the end, lessons before folders. The sort is
 * stable, so each keeps the order `sortDivision` already gave it.
 */
export function levelEntries(level) {
  const entries = [
    ...level.items.map((set) => ({
      kind: isDocument(set) ? "document" : "lesson",
      set,
      order: leadingNumber(set.episode),
    })),
    ...(level.children ?? []).map((division) => ({
      kind: "folder",
      division,
      order: leadingNumber(division.title),
    })),
  ];
  return entries.sort((a, b) => (a.order ?? Infinity) - (b.order ?? Infinity));
}

/**
 * Every playable set in a collection, in display order. Documents are excluded.
 *
 * One rule for a show and for a course, because `levelEntries` already covers
 * both: a season holds episodes and no folders, so its order is the episodes;
 * a course folder holds lessons beside folders, so its order is the two
 * interleaved by number. Flattening with the same function the pages render
 * with is what stops "next" from meaning something different to the button
 * and to the list it came from.
 */
export function flattenCollection(collection) {
  return [...playableInOrder({ items: [], children: collection.divisions })];
}

/** Share display order while letting a first-item lookup stop at its first lesson. */
function* playableInOrder(level) {
  for (const entry of levelEntries(level)) {
    if (entry.kind === "lesson") yield entry.set;
    // Documents have nothing below them, and "next" means something that plays.
    else if (entry.kind === "folder") yield* playableInOrder(entry.division);
  }
}

/**
 * What follows `setId` in an ordered run of sets, or `null` at the end.
 *
 * The one definition of "the one after this", shared by a show's episodes and
 * by a hand-built list. Both are a flat run by the time they get here; only
 * the flattening differs.
 */
export function nextInQueue(sets, setId) {
  const at = sets.findIndex((set) => set.setId === setId);
  return at === -1 || at === sets.length - 1 ? null : sets[at + 1];
}

/**
 * What follows `setId` in its collection, or `null` at the end of one.
 *
 * Crosses a season or folder boundary without being told to, because the
 * flattening does not know there was one — which is the behaviour wanted:
 * the last episode of a season is followed by the first of the next.
 */
export function nextAfter(collection, setId) {
  return nextInQueue(flattenCollection(collection), setId);
}

/** Groups one kind's sets by container (show or course), then by folder. */
function collections(sets, fallbackName) {
  const byName = new Map();

  for (const set of sets) {
    const name = set.show ?? fallbackName;
    if (!byName.has(name)) byName.set(name, { title: name, season: null, items: [], children: [] });

    const { names, season } = trailOf(set);
    let node = byName.get(name);
    for (const folder of names) node = getOrCreateChildDivision(node, folder, season);
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
        // A folder of documents alone is structure too — "3 chapters" should
        // not count the one holding the workbooks.
        chapters: divisions.filter((d) => d.items.some((set) => !isDocument(set))).length,
        count: divisions.reduce(
          (n, d) => n + d.items.filter((set) => !isDocument(set)).length,
          0,
        ),
        documents: divisions.reduce(
          (n, d) => n + d.items.filter(isDocument).length,
          0,
        ),
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
 * Documents are the one kind that must not fall through to that rule. A
 * course handout on the film shelf reads as a broken film, so they go into
 * the course tree beside the lessons they were found with.
 *
 * @param {CatalogSet[]} sets
 */
export function groupLibrary(sets) {
  const episodes = sets.filter((set) => set.kind === "ep");
  const course = sets.filter((set) => set.kind === "tut" || isDocument(set));
  const rest = sets.filter(
    (set) => set.kind !== "ep" && set.kind !== "tut" && !isDocument(set),
  );

  return {
    movies: [...rest].sort(byTitle),
    series: collections(episodes, "Unknown show"),
    tutorials: collections(course, "Unknown course"),
  };
}
