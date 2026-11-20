/**
 * Audiobooks: a hero without art, what is being listened to, a row per genre,
 * what arrived, and every book by title.
 *
 * Modelled on `renderDocumentariesDept`. A book is one file and has no show
 * or course around it, so there are no collections here — only cards that
 * play. Their covers are square and drawn whole (see `plate.js`), and a
 * book has no backdrop to lead the hero with.
 */

import { el } from "../dom.js";
import { countOf } from "../format.js";
import { categoryRows } from "../categories.js";
import { emptyState, movieGrid } from "./shelf-view.js";
import { SECTIONS } from "./sections.js";
import { GRID } from "./shelf-mode.js";
import { departmentHero, deptRow } from "./department-hero.js";
import { DEPARTMENT_ROW, departmentUnderway } from "./home-shelves.js";
import { resumeCards } from "./home-resume.js";
import { revealWithin } from "../reveal.js";

/** @param {HTMLElement} main @param {import("./department-pages.js").Context} cx */
export function renderAudiobooksDept(main, cx) {
  const books = cx.library.audiobooks;
  if (books.length === 0) return main.append(emptyState("audiobooks", cx));

  main.append(departmentHero({
    title: SECTIONS.audiobooks.label,
    line: countOf(books.length, SECTIONS.audiobooks.extent),
    lead: null,
    leadName: null,
    leadHref: null,
  }));

  const shelf = (sets) => movieGrid(sets, cx.play, { mode: GRID, strip: true });
  const underway = resumeCards(departmentUnderway({
    collections: [], keep: (set) => set.kind === "book",
    byId: cx.byId, progress: cx.progress, watchedAt: cx.watchedAt,
  }), cx.play);
  if (underway.length > 0) {
    const strip = el("div", "resume-strip");
    strip.append(...underway);
    main.append(deptRow("Continue listening", strip));
  }

  for (const { title, units } of categoryRows(books, (set) => set.category ?? null)) {
    main.append(deptRow(title, shelf(units)));
  }

  const recent = [...books].sort((a, b) => (Number(b.addedAt) || 0) - (Number(a.addedAt) || 0));
  main.append(deptRow("Recently added", shelf(recent.slice(0, DEPARTMENT_ROW))));
  main.append(deptRow("All audiobooks", movieGrid(books, cx.play, { mode: GRID })));
  revealWithin(main);
}
