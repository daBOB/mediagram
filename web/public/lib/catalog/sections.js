/**
 * What each department is called, and the words its shelves count in.
 *
 * `extent` is what a department's own line counts in ("581 films"); `noun`
 * and `chapterNoun` are what a collection card's line counts in ("12 lessons
 * · 3 chapters") — shared by `collectionGrid` so the noun for a franchise's
 * kind of thing lives in one table rather than a ternary per caller. Listed
 * in the nav's order, which the Sections switches and the Everyone line follow.
 */
export const SECTIONS = {
  movies: { label: "Movies", empty: "No films yet.", extent: "film" },
  series: { label: "Series", empty: "No series yet.", extent: "show", noun: "episode", chapterNoun: "season" },
  anime: { label: "Anime", empty: "No anime yet.", extent: "title", noun: "episode", chapterNoun: "season" },
  documentaries: {
    label: "Documentaries", empty: "No documentaries yet.", extent: "documentary",
    noun: "documentary", chapterNoun: "chapter",
  },
  tutorials: { label: "Tutorials", empty: "No courses yet.", extent: "course", noun: "lesson", chapterNoun: "chapter" },
};
