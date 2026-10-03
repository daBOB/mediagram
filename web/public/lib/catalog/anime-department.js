/**
 * Anime: a hero, what is underway, then every show and every film — the
 * department doubles as anime's own "latest", since the library-wide Latest
 * page leaves it out the same way it leaves out the cover.
 *
 * Modelled on `renderDocumentariesDept`: no genre browsing here either,
 * since the rows are few enough to show in full rather than curated down.
 */

import { el } from "../dom.js";
import { countOf } from "../format.js";
import { firstItemOf } from "../library.js";
import { collectionGrid, emptyState, movieGrid, SECTIONS } from "./shelf-view.js";
import { GRID } from "./shelf-mode.js";
import { departmentHero, deptRow } from "./department-hero.js";
import { homeShelves } from "./home-shelves.js";
import { resumeCards } from "./home-resume.js";
import { revealWithin } from "../reveal.js";
import { href } from "../address.js";

const popular = (a, b) => (b.popularity ?? 0) - (a.popularity ?? 0);

/** @param {HTMLElement} main @param {import("./department-pages.js").Context} cx */
export function renderAnimeDept(main, cx) {
  const { collections: shows, singles: films } = cx.library.anime;
  if (shows.length === 0 && films.length === 0) return main.append(emptyState("anime", { kids: cx.kids }));

  const showLeads = shows.map((show) => firstItemOf(show.divisions)).filter(Boolean);
  const unwatched = [...films, ...showLeads].filter((set) => !cx.isWatched(set.setId));
  const lead = [...unwatched].filter((set) => set.backdrop).sort(popular)[0] ?? null;
  const leadIsFilm = lead?.kind === "movie";

  main.append(departmentHero({
    title: SECTIONS.anime.label,
    line: [
      shows.length > 0 ? countOf(shows.length, "show") : null,
      films.length > 0 ? countOf(films.length, "film") : null,
    ].filter(Boolean).join(" · "),
    lead,
    leadName: (leadIsFilm ? lead?.title : lead?.show) ?? null,
    leadHref: !lead
      ? null
      : leadIsFilm
        ? href({ page: "film", setId: lead.setId })
        : href({ page: "show", section: "anime", name: lead.show, folders: [] }),
  }));

  const shelves = homeShelves({ library: cx.library, byId: cx.byId, progress: cx.progress, watchedAt: cx.watchedAt });
  const underway = resumeCards(
    { continues: shelves.continues.filter((set) => set.anime), nextUp: shelves.nextUp.filter((entry) => entry.set.anime) },
    cx.play,
  );
  if (underway.length > 0) {
    const strip = el("div", "resume-strip");
    strip.append(...underway);
    main.append(deptRow("Continue watching", strip));
  }

  if (shows.length > 0) {
    const open = (name) => cx.openShow("anime", name);
    main.append(deptRow("Series", collectionGrid("anime", shows, open, { mode: GRID })));
  }
  if (films.length > 0) {
    const byRecent = [...films].sort((a, b) => (Number(b.addedAt) || 0) - (Number(a.addedAt) || 0));
    main.append(deptRow("Films", movieGrid(byRecent, cx.play, { mode: GRID })));
  }
  revealWithin(main);
}
