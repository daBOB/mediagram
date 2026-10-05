# Phase 02: Web episode sidebar

## Context links

- [spec.md](spec.md) — binding; see Behaviour → Episode sidebar (☰), Hiding, Testing ("The episode-list model…"), Rollout step 2, and User decision 5.
- [plan.md](plan.md) — Global Constraints (sidebar words "Season N", "Now playing") and Review Focus 2, 3, 5.
- [phase-01-web-control-card.md](phase-01-web-control-card.md) — must be merged first.
- Code read for this phase (lines are `main` after phase 01; match on the quoted text): `web/public/lib/catalog/course-view.js:46-78,155-161` (`lessonRow`, `seasonBlock`), `web/public/lib/catalog/set-badge.js:34,45` (`progressRuleFor`, `watchedTick`), `web/public/lib/catalog/series-page.js:131-156` (the show page's season picker), `web/public/lib/library.js:60-69,82-89,185-222` (`trailOf`, `sortDivision`, `levelEntries`, `playableInOrder`), `web/public/lib/playback/plays-next.js` (`collectionOf`), `web/public/lib/playback/player-next-title.js` (`openInRun`), `web/public/styles/catalog.css:63-90` (row styles).
- Every code block was dry-run on a scratch copy of `web/` after phase 01's, with the full suite, `typecheck` and `lint` after each task, and looked at in the stub preview at 1440×900 and 390×844. Diff blocks are relative to `web/`.

## Overview

- **Priority:** P2 — finishes the web reference before Android copies it.
- **Status:** pending.
- **What:** ☰ in the card's last row opens a 360px sidebar over the right of the picture: a `‹ Season N ›` switcher opened on the season playing, then that season's rows — the show page's own rows (`seasonBlock`) with the runtime only, watched ones at 45% opacity with ✓, a progress line under partly watched ones, "Now playing" on the current one (not actionable). Picking a row opens it the way ⏭ does and shuts the sidebar. Courses and documentary collections list the folders that hold lessons, or one list. ☰ is hidden for a film and for a hand-built list.
- **Baseline:** 3033 pass after phase 01. After this phase: 3055 pass across 217 files.

## Key insights

- **One run, one source.** The sidebar's groups come from the same collection `playsNext` flattens for ⏮/⏭ (`collectionOf`), walked with the same `levelEntries` the course page and `flattenCollection` use. So the sidebar cannot list a title ⏭ would never reach.
- **The card clears the open sidebar (lead ruling, 2026-10-05, found on the tablet walk).** While the sidebar is open, the control card is laid out to the left of it: the card's right edge stops 24px short of the sidebar's left edge, it is centred in the remaining width, and it narrows if it has to (rows wrap, targets never shrink). Implement it in the task that mounts the sidebar: toggle a class on the player root while the sidebar is open (e.g. `.sidebar-open`) and give `#card-dock` a `right: 360px` there. Add a test that the class is set while open and cleared on close, and check the 1440×900 and 390×844 previews: at 390 the sidebar is full width and covers the card, which is fine.
- **Lists hide ☰ (ruling).** A hand-built list is a run for ⏮/⏭ (phase 01), but the sidebar groups by season or section, which a list has none of, and a row picked from a show's sidebar would be opened with the list as its queue — `nextInQueue` would then find nothing after it. `app.js` passes `collection: null` for a list. Written down in `episode-sidebar.js`'s `open` doc.
- **"Unknown last" is the sidebar's own order.** `collections()` sorts a season-less "Episodes" division *before* "Season 1" by name (`library.js:84-87`), and the run (⏭) follows that. Review focus 5 wants unplaced episodes grouped last, so `episodeGroups` moves season-less episode groups to the end for display only; ⏮/⏭ keep the run's order. A test pins both.
- **"An id the catalogue doesn't know" on the web** is the playing title missing from its collection (a refreshed catalogue, a stale row). The model still builds and opens on the first group; no row says "Now playing".
- **Reuse, then say less.** `seasonBlock` builds each row with `watchedTick` and `progressRuleFor` and wires the click. The sidebar only rewrites `.meta` (runtime, or "Now playing"), disables the playing row, adds `is-watched`, and CSS hides the codec line and badges. `.seen` and `.watched` are already taken (`catalog.css:63-66`), hence `is-watched`.
- **`button:disabled` is 55% opacity globally** (`theme.css:166`): the playing row must reset `opacity: 1` or it reads like a watched one; `.row:hover .title b` uses the paper accent, so the sidebar re-inks it with `--stage-accent`.
- **No `player.js` growth.** The sidebar mounts inside `mountPlayerNextTitle` (the owner of the run, `openInRun` and `showControls`), so `player.js` changes only its HUD line and its typedef; the new `collection` typedef line is paid for by tightening the `freshResume` description. `app.js` stays at 694 by extending existing lines.
- **Esc order is registration order.** `mountPlayerMenus` is mounted (and registers its dialog `keydown`) before `mountPlayerNextTitle`, so an open menu takes Esc and calls `preventDefault()`; the sidebar's listener skips a `defaultPrevented` event.

## Requirements

- Functional: spec Episode sidebar for web — shown only for a series, anime show, course or documentary collection; all seasons with `‹ Season N ›` (no arrows with one); rows: number, title, runtime; watched 45% + ✓ and still playable; partly watched progress line; current "Now playing", not actionable, scrolled into view; pick → same open path as ⏭ (`openInRun`, `autoplay: "asap"`), sidebar closes; close by ✕, ☰, Esc, or a pick; card does not rest while it is open; a new title closes it.
- Accessible names: "Episodes" (☰, `aria-expanded`, `aria-controls="episode-sidebar"`), "Previous season", "Next season", "Close episodes"; the panel is `aside[aria-label="Episodes"]`, focus moves to it on open and back to ☰ on Esc; the playing row has `aria-current="true"`.
- Non-functional: no new dependency; new files under 200 lines (`episode-list.js` 53, `episode-sidebar.js` 126, `player-card.css` 183); `player.js` stays 994; no plan references in code.

## Related code files

- **Create:** `web/public/lib/playback/episode-list.js`, `web/public/lib/playback/episode-sidebar.js`, `web/test/episode-list.test.ts`, `web/test/episode-sidebar.test.ts`.
- **Modify:** `web/public/index.html` (☰ in `.card-end`, the `aside`), `web/public/styles/player-card.css` (sidebar block), `web/public/lib/playback/player-next-title.js`, `web/public/lib/playback/player.js` (HUD line, typedef), `web/public/app.js` (import, one options line), `web/DESIGN.md` (Player section), `web/test/player-card.test.ts`, `web/test/browser-application.test.ts`, `web/test/support/player-environment.ts` (`scrollIntoView` on the fake `Node`).
- **Delete:** none.

## Success criteria

- `cd web && bun test` → 3055 pass, 0 fail; `bun run typecheck`, `bun run lint` clean; `bun test test/code-standards.test.ts` passes with no ceiling change.
- Review focus 5 pinned: `episode-list.test.ts` "a messy run" (three tests) and `episode-sidebar.test.ts` "a run with an untitled, numberless, seasonless episode still draws…". Focus 2 and 3 for the sidebar: `player-card.test.ts` "the card stays up while it is open, and Esc shuts a menu over it before the sidebar…".
- Stub preview: ☰ on an episode opens the sidebar on its season with "Now playing" in view; ‹ › switch seasons; a row opens that episode and the sidebar shuts; at 390×844 the sidebar is 360px and nothing scrolls sideways.

## Risks

| Risk | L × I | Mitigation |
|---|---|---|
| The show page and the sidebar order a season-less "Episodes" group differently | M × L | Deliberate (review focus 5); pinned by test; noted in Key insights so the Android model (phase 03) makes the same call. |
| `seasonBlock`'s row markup changes later and the sidebar's `.meta` rewrite silently misses | L × M | The rewrite looks the child up by `className === "meta"` and leaves the row alone if it is missing; `episode-sidebar.test.ts` asserts the runtime and "Now playing" text, so a markup change fails a test. |
| A very large season (Die Simpsons, 36 seasons) makes the switcher slow to reach a far season | L × L | Opens on the playing season; ‹ › only. A season jump list is out of scope (spec). |
| The sidebar covers the Notes column on a lesson | L × L | It is over the picture for a moment and closes on a pick; Notes stay open beneath it. |

## Rollback

Four commits; reverting Task 3 alone removes the wiring and leaves an inert, hidden ☰ and an unused module (harmless); reverting all four restores phase 01 exactly.

---

### Task 1: The episode list model

**Files:**
- Create: `web/public/lib/playback/episode-list.js`
- Test: `web/test/episode-list.test.ts`

**Interfaces:**
- Consumes: `levelEntries(level)` (`web/public/lib/library.js:185`), `groupDepartments` (`web/public/lib/departments.js`) and `collectionOf`/`playsNext` (`web/public/lib/playback/plays-next.js`, phase 01 Task 2) in the test.
- Produces: `episodeGroups(collection, currentId) → { groups: { title, season, items: CatalogSet[] }[], at: number } | null` — `null` for no collection or nothing playable; `at` is the group holding `currentId`, else 0; groups are the divisions that hold lessons, in `levelEntries` order, season-less episode groups last.

- [ ] Step 1: Write the failing test — create `web/test/episode-list.test.ts`:

```ts
/**
 * What the episode sidebar lists: a show by its seasons, a course by the
 * folders its page walks, the season the title is in opened first — and a
 * messy run still listed, with what the index could not place kept last.
 */

import { describe, expect, test } from "bun:test";
import { catalogSet as set } from "./support/catalog-set";
import { groupDepartments } from "../public/lib/departments.js";
import { episodeGroups } from "../public/lib/playback/episode-list.js";
import { collectionOf, playsNext } from "../public/lib/playback/plays-next.js";
import type { CatalogSet } from "../public/lib/library.js";

const shape = (model: ReturnType<typeof episodeGroups>) =>
  model && { at: model.at, groups: model.groups.map((group) => [group.title, group.items.map((item) => item.setId)]) };
const runOf = (sets: CatalogSet[], current: CatalogSet) => episodeGroups(collectionOf(groupDepartments(sets), current), current.setId);

describe("a show", () => {
  const ep = (id: string, season: number, episode: string) => set({ setId: id, kind: "ep", show: "Star City", season, episode, title: id });
  const sets = [ep("s2e1", 2, "1"), ep("s1e2", 1, "2"), ep("s1e1", 1, "1"), ep("s2e2", 2, "2")];

  test("lists every season in number order and opens on the one playing", () => {
    expect(shape(runOf(sets, sets[3]!))).toEqual({
      at: 1,
      groups: [["Season 1", ["s1e1", "s1e2"]], ["Season 2", ["s2e1", "s2e2"]]],
    });
  });

  test("one season is one group", () => {
    expect(shape(runOf(sets.slice(1, 3), sets[1]!))).toEqual({ at: 0, groups: [["Season 1", ["s1e1", "s1e2"]]] });
  });
});

describe("a course", () => {
  const lesson = (id: string, path: string | null, episode: string, kind = "tut") =>
    set({ setId: id, kind, show: "Kurs", path, episode, title: id });

  test("lists the folders that hold lessons in the order its page walks them, documents left out", () => {
    const sets = [
      lesson("a1", "1. Basics", "1"), lesson("a2", "1. Basics", "2"),
      lesson("b1", "1. Basics/2. Deeper", "1"), lesson("w", "1. Basics", "3", "doc"),
      lesson("c1", "3. Last", "1"),
    ];
    expect(shape(runOf(sets, sets[4]!))).toEqual({
      at: 2,
      groups: [["1. Basics", ["a1", "a2"]], ["2. Deeper", ["b1"]], ["3. Last", ["c1"]]],
    });
  });

  test("a course without folders is one list", () => {
    const sets = [lesson("l2", null, "2"), lesson("l1", null, "1")];
    expect(shape(runOf(sets, sets[0]!))?.groups).toEqual([["Chapter 1", ["l1", "l2"]]]);
  });

  test("a course of documents alone has nothing to list", () => {
    const sets = [lesson("w1", "Workbooks", "1", "doc")];
    expect(runOf(sets, sets[0]!)).toBeNull();
  });
});

describe("no run", () => {
  test("a film has no list", () => {
    const film = set({ kind: "movie", title: "Blade" });
    expect(runOf([film], film)).toBeNull();
    expect(episodeGroups(null, film.setId)).toBeNull();
  });
});

describe("a messy run", () => {
  const sets = [
    set({ setId: "s1e1", kind: "ep", show: "Patchy", season: 1, episode: "1", title: "Pilot" }),
    set({ setId: "s1e2", kind: "ep", show: "Patchy", season: 1, episode: "2", title: "Second" }),
    set({ setId: "s2-nonumber", kind: "ep", show: "Patchy", season: 2, episode: null, title: "Special" }),
    set({ setId: "s2e1", kind: "ep", show: "Patchy", season: 2, episode: "1", title: "Return" }),
    set({ setId: "unplaced", kind: "ep", show: "Patchy", season: null, episode: null, title: null }),
  ];

  test("an episode without a number goes last in its season, one without a season after every season", () => {
    expect(shape(runOf(sets, sets[0]!))?.groups).toEqual([
      ["Season 1", ["s1e1", "s1e2"]],
      ["Season 2", ["s2e1", "s2-nonumber"]],
      ["Episodes", ["unplaced"]],
    ]);
  });

  test("a title the catalogue does not know still gets the list, opened on the first season", () => {
    const stranger = set({ setId: "not-in-the-index", kind: "ep", show: "Patchy", season: 7, episode: "7" });
    expect(shape(runOf(sets, stranger))?.at).toBe(0);
    expect(shape(runOf(sets, stranger))?.groups).toHaveLength(3);
  });

  test("and ⏮/⏭ still step through the run, the unplaced episode and the numberless one included", () => {
    const library = groupDepartments(sets);
    // The run keeps the order the show page plays in, where sorting by name
    // puts "Episodes" before "Season 1"; only the sidebar moves it last.
    const step = (at: CatalogSet) => {
      const { previous, next } = playsNext(library, at, null);
      return [previous?.setId ?? null, next?.setId ?? null];
    };
    expect(step(sets[4]!)).toEqual([null, "s1e1"]);
    expect(step(sets[0]!)).toEqual(["unplaced", "s1e2"]);
    expect(step(sets[2]!)).toEqual(["s2e1", null]);
  });
});
```

- [ ] Step 2: `cd web && bun test test/episode-list.test.ts` → FAIL: `Cannot find module '../public/lib/playback/episode-list.js'`.
- [ ] Step 3: Minimal implementation — create `web/public/lib/playback/episode-list.js`:

```js
/**
 * What the episode sidebar lists: the open title's run, grouped the way its
 * own page groups it.
 *
 * A show is its seasons. A course or a documentary collection is its folders
 * that hold lessons, in the order the course page walks them — or one list,
 * when it has no folders. Built from the same collection `playsNext` walks, so
 * the sidebar and ⏮/⏭ cannot disagree about what is in the run.
 */

import { levelEntries } from "../library.js";

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 * @typedef {import("../library.js").Division} Division
 * @typedef {{title: string, season: number|null, items: CatalogSet[]}} EpisodeGroup
 */

/** Every folder under `level` that holds something playable, each before the ones below it. */
function groupsUnder(level, into) {
  const entries = levelEntries(level);
  const items = entries.filter((entry) => entry.kind === "lesson").map((entry) => entry.set);
  if (items.length > 0) into.push({ title: level.title, season: level.season, items });
  for (const entry of entries) {
    if (entry.kind === "folder") groupsUnder(entry.division, into);
  }
  return into;
}

/**
 * An episode the index could not place in a season. Its group goes after
 * every season, where a viewer looks for what does not fit, rather than first,
 * where sorting by name would put "Episodes".
 * @param {EpisodeGroup} group
 */
function unplaced(group) {
  return group.season == null && group.items.some((set) => set.kind === "ep");
}

/**
 * @param {{name: string, divisions: Division[]}|null} collection the run, or `null` for a title with none
 * @param {string} currentId the title playing
 * @returns {{groups: EpisodeGroup[], at: number}|null} `at` is the group holding
 *   `currentId`, or the first when the run does not hold it.
 */
export function episodeGroups(collection, currentId) {
  if (!collection) return null;
  const found = groupsUnder({ title: collection.name, season: null, items: [], children: collection.divisions }, []);
  const groups = [...found.filter((group) => !unplaced(group)), ...found.filter(unplaced)];
  if (groups.length === 0) return null;
  const at = groups.findIndex((group) => group.items.some((set) => set.setId === currentId));
  return { groups, at: Math.max(0, at) };
}
```

- [ ] Step 4: same command → PASS (9 tests).
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/episode-list.js web/test/episode-list.test.ts
git commit -m "feat(player): group a run into seasons or sections for the episode sidebar"
```

---

### Task 2: The sidebar

**Files:**
- Create: `web/public/lib/playback/episode-sidebar.js`
- Modify: `web/public/index.html` (`.card-end`; after `#stats-panel`); `web/public/styles/player-card.css` (above `@media (max-width: 767px)`); `web/test/support/player-environment.ts:86`
- Test: `web/test/episode-sidebar.test.ts`

**Interfaces:**
- Consumes: `episodeGroups` (Task 1), `seasonBlock(season, onPlay)` (`web/public/lib/catalog/course-view.js:155`), `isWatched` (`web/public/lib/watch-state.js:317`), `humanDuration` (`web/public/lib/format.js:22`), `el` (`web/public/lib/dom.js:10`); ids `player`, `episodes`, `episode-sidebar`.
- Produces: `mountEpisodeSidebar({ onPick(set), onClose? }) → { open(set, collection|null), close(), isOpen() → boolean }`. `#episodes` (☰) hidden unless the title has groups; header buttons "Previous season" / "Next season" / "Close episodes" and `h2.sidebar-title`; rows marked `is-watched` / `aria-current="true"` + disabled. Esc on `#player` while open and not already `defaultPrevented`: `preventDefault()`, close, focus ☰. The fake test `Node` gains `scrollIntoView()` and `scrolledIntoView`.

- [ ] Step 1: Write the failing test. In `web/test/support/player-environment.ts`, right after `  focus() {}` in `class Node`, add:

```ts
  scrolledIntoView = false;
  scrollIntoView() {
    this.scrolledIntoView = true;
  }
```

Create `web/test/episode-sidebar.test.ts`:

```ts
/**
 * The ☰ sidebar against a fake DOM: shown only for a run, opened on the
 * season playing, the show page's rows with the playing one marked and the
 * watched ones greyed, a pick handed on and the sidebar shut, and Esc.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mountEpisodeSidebar } from "../public/lib/playback/episode-sidebar.js";
import { collectionOf } from "../public/lib/playback/plays-next.js";
import { groupDepartments } from "../public/lib/departments.js";
import * as state from "../public/lib/watch-state.js";
import { catalogSet as set } from "./support/catalog-set";
import { browserEnvironment, settle, type Node } from "./support/player-environment";
import type { CatalogSet } from "../public/lib/library.js";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(async () => {
  env = browserEnvironment();
  await state.useProfile(null);
  env.node("episode-sidebar").hidden = true;
});
afterEach(async () => {
  await settle();
  env.restore();
});

const ep = (id: string, season: number | null, episode: string | null, title: string | null = id) =>
  set({ setId: id, kind: "ep", show: "Star City", season, episode, title, duration: 1260 });
const s1 = [ep("s1e1", 1, "1"), ep("s1e2", 1, "2"), ep("s1e3", 1, "3")];
const s2 = [ep("s2e1", 2, "1")];
const showOf = (sets: CatalogSet[]) => collectionOf(groupDepartments(sets), sets[0]!);

const panel = () => env.node("episode-sidebar");
const textOf = (node: Node): string => node.textContent + node.children.map(textOf).join("");
const find = (root: Node, test: (node: Node) => boolean): Node | undefined =>
  root.children.find(test) ?? root.children.map((child) => find(child, test)).find(Boolean);
/** The header's four: ‹, the season's name, ›, ✕. */
const head = (at: number) => panel().children[0]!.children[at]!;
const titleText = () => head(1).textContent;
const prevSeason = () => head(0);
const nextSeason = () => head(2);
const shut = () => head(3);
const rows = () => panel().children[1]!.children[0]!.children;

function mount() {
  const picked: string[] = [];
  let closes = 0;
  const sidebar = mountEpisodeSidebar({ onPick: (chosen) => picked.push(chosen.setId), onClose: () => { closes++; } });
  return { sidebar, picked, closes: () => closes };
}

describe("when it is offered", () => {
  test("☰ shows for a run and hides for a title without one", () => {
    const { sidebar } = mount();
    sidebar.open(s1[0]!, showOf([...s1, ...s2]));
    expect(env.node("episodes").hidden).toBe(false);
    sidebar.open(set({ kind: "movie" }), null);
    expect(env.node("episodes").hidden).toBe(true);
  });
});

describe("the seasons", () => {
  test("opens on the season playing, and steps between seasons, each end disabled", () => {
    const { sidebar } = mount();
    sidebar.open(s2[0]!, showOf([...s1, ...s2]));
    env.node("episodes").fire("click");
    expect(panel().hidden).toBe(false);
    expect(env.node("episodes").getAttribute("aria-expanded")).toBe("true");
    expect(titleText()).toBe("Season 2");
    expect(nextSeason().disabled).toBe(true);
    prevSeason().fire("click");
    expect(titleText()).toBe("Season 1");
    expect(prevSeason().disabled).toBe(true);
    expect(rows()).toHaveLength(3);
  });

  test("one season is a title without arrows", () => {
    const { sidebar } = mount();
    sidebar.open(s1[0]!, showOf(s1));
    env.node("episodes").fire("click");
    expect(titleText()).toBe("Season 1");
    expect(prevSeason().hidden).toBe(true);
    expect(nextSeason().hidden).toBe(true);
  });
});

describe("the rows", () => {
  test("watched greyed with a tick, partly watched with a progress line, the playing one says so and cannot be picked", () => {
    state.setWatched("s1e1", true);
    state.setProgress("s1e3", 630, 1260);
    const { sidebar } = mount();
    sidebar.open(s1[1]!, showOf(s1));
    env.node("episodes").fire("click");
    const [watched, playing, partway] = rows();
    expect(watched!.classes.has("is-watched")).toBe(true);
    expect(find(watched!, (node) => node.className === "tick")?.textContent).toBe("✓");
    expect(find(watched!, (node) => node.className === "meta")?.textContent).toBe("21m");
    expect(playing!.scrolledIntoView).toBe(true);
    expect(playing!.disabled).toBe(true);
    expect(playing!.getAttribute("aria-current")).toBe("true");
    expect(find(playing!, (node) => node.className === "meta")?.textContent).toBe("Now playing");
    expect(partway!.classes.has("is-watched")).toBe(false);
    expect(find(partway!, (node) => node.className === "watched")).toBeDefined();
  });

  test("picking a row hands it on to be opened and shuts the sidebar", () => {
    const { sidebar, picked, closes } = mount();
    sidebar.open(s1[0]!, showOf(s1));
    env.node("episodes").fire("click");
    rows()[2]!.fire("click");
    expect(picked).toEqual(["s1e3"]);
    expect(panel().hidden).toBe(true);
    expect(env.node("episodes").getAttribute("aria-expanded")).toBe("false");
    expect(closes()).toBe(1);
  });

  test("a run with an untitled, numberless, seasonless episode still draws, that episode in a group of its own last", () => {
    const messy = [...s1, ep("odd", null, null, null)];
    const { sidebar } = mount();
    sidebar.open(set({ setId: "unknown-to-the-index", kind: "ep", show: "Star City" }), showOf(messy));
    env.node("episodes").fire("click");
    expect(titleText()).toBe("Season 1");
    nextSeason().fire("click");
    expect(titleText()).toBe("Episodes");
    expect(textOf(rows()[0]!)).toContain("odd");
  });
});

describe("closing", () => {
  const escape = () => {
    const event = Object.assign(new Event("keydown", { cancelable: true }), { key: "Escape" });
    env.node("player").dispatchEvent(event);
    return event;
  };

  test("✕, ☰ again, and Esc each shut it; Esc is taken from the dialog only while it is open", () => {
    const { sidebar } = mount();
    sidebar.open(s1[0]!, showOf(s1));
    env.node("episodes").fire("click");
    shut().fire("click");
    expect(panel().hidden).toBe(true);
    env.node("episodes").fire("click");
    env.node("episodes").fire("click");
    expect(panel().hidden).toBe(true);
    env.node("episodes").fire("click");
    expect(escape().defaultPrevented).toBe(true);
    expect(panel().hidden).toBe(true);
    expect(escape().defaultPrevented).toBe(false);
  });

  test("an Esc something earlier already took leaves it open", () => {
    const { sidebar } = mount();
    sidebar.open(s1[0]!, showOf(s1));
    env.node("episodes").fire("click");
    const taken = Object.assign(new Event("keydown", { cancelable: true }), { key: "Escape" });
    taken.preventDefault();
    env.node("player").dispatchEvent(taken);
    expect(panel().hidden).toBe(false);
  });

  test("a new title shuts it", () => {
    const { sidebar } = mount();
    sidebar.open(s1[0]!, showOf(s1));
    env.node("episodes").fire("click");
    sidebar.open(s1[1]!, showOf(s1));
    expect(sidebar.isOpen()).toBe(false);
  });
});
```

- [ ] Step 2: `cd web && bun test test/episode-sidebar.test.ts` → FAIL: `Cannot find module '../public/lib/playback/episode-sidebar.js'`.
- [ ] Step 3: Minimal implementation — create `web/public/lib/playback/episode-sidebar.js`:

```js
/**
 * The ☰ sidebar: the run beside the picture a season at a time, the title
 * playing marked and the watched ones greyed. The video keeps playing under it.
 *
 * Its rows are the show page's own (`seasonBlock`), so a season reads the same
 * in both places — the tick, the progress line and the click are theirs. The
 * sidebar only says less (the runtime, not the file) and marks the one playing.
 */

import { el } from "../dom.js";
import { humanDuration } from "../format.js";
import { isWatched } from "../watch-state.js";
import { seasonBlock } from "../catalog/course-view.js";
import { episodeGroups } from "./episode-list.js";

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 * @param {{onPick: (set: CatalogSet) => void, onClose?: () => void}} deps `onPick`
 *   opens a row the way ⏭ opens the next title; `onClose` hears it shut.
 */
export function mountEpisodeSidebar({ onPick, onClose }) {
  const dialog = document.getElementById("player");
  const button = document.getElementById("episodes");
  const panel = document.getElementById("episode-sidebar");

  const back = el("button", "tsp", "‹");
  back.setAttribute("aria-label", "Previous season");
  const title = el("h2", "sidebar-title");
  const on = el("button", "tsp", "›");
  on.setAttribute("aria-label", "Next season");
  const shut = el("button", "tsp", "✕");
  shut.setAttribute("aria-label", "Close episodes");
  const head = el("header", "sidebar-head");
  head.append(back, title, on, shut);
  const rows = el("div", "sidebar-rows");
  panel.append(head, rows);

  /** The open title's run as `episodeGroups` gives it, the season in view, and the title. */
  let model = null;
  let shown = 0;
  let playing = null;
  /** The row of the title playing, while the season in view holds it. */
  let playingRow = null;

  /** A row as the show page draws it, told what the sidebar says differently. */
  function mark(row, set) {
    const meta = [...row.children].find((child) => child.className === "meta");
    if (isWatched(set.setId)) row.classList.add("is-watched");
    if (set.setId !== playing) {
      if (meta) meta.textContent = humanDuration(set.duration);
      return;
    }
    // Not a way to restart what is already playing: that is ↺'s job.
    playingRow = row;
    row.disabled = true;
    row.setAttribute("aria-current", "true");
    if (meta) meta.textContent = "Now playing";
  }

  function draw() {
    const group = model.groups[shown];
    title.textContent = group.title;
    back.hidden = model.groups.length < 2;
    on.hidden = model.groups.length < 2;
    back.disabled = shown === 0;
    on.disabled = shown === model.groups.length - 1;
    playingRow = null;
    const block = seasonBlock(group, (set) => {
      close();
      onPick(set);
    });
    group.items.forEach((set, at) => mark(block.children[at], set));
    rows.replaceChildren(block);
  }

  function show() {
    shown = model.at;
    draw();
    panel.hidden = false;
    button.setAttribute("aria-expanded", "true");
    panel.focus();
    // A season runs to thirty rows; the one playing is the one being looked for.
    playingRow?.scrollIntoView({ block: "center" });
  }

  function close() {
    if (panel.hidden) return;
    panel.hidden = true;
    button.setAttribute("aria-expanded", "false");
    onClose?.();
  }

  /**
   * As each title opens. `collection` is its show or course, or `null` — for
   * a film, and for a hand-built list, which ⏮/⏭ walk but which has no seasons
   * to group and is its own page's table of contents.
   * @param {CatalogSet} set
   * @param {{name: string, divisions: import("../library.js").Division[]}|null} collection
   */
  function open(set, collection) {
    close();
    playing = set.setId;
    model = episodeGroups(collection, set.setId);
    button.hidden = model === null;
  }

  button.addEventListener("click", () => (panel.hidden ? show() : close()));
  shut.addEventListener("click", close);
  back.addEventListener("click", () => {
    shown -= 1;
    draw();
  });
  on.addEventListener("click", () => {
    shown += 1;
    draw();
  });
  dialog.addEventListener("keydown", (event) => {
    // An open menu takes Esc first and marks it taken; the sidebar is next.
    if (event.key !== "Escape" || event.defaultPrevented || panel.hidden) return;
    event.preventDefault();
    close();
    button.focus();
  });

  return { open, close, isOpen: () => !panel.hidden };
}
```

`index.html` — in `.card-end`, after `#stats-toggle`:

```html
              <button id="episodes" class="tsp" aria-label="Episodes" aria-expanded="false" aria-controls="episode-sidebar" hidden><svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"></path></svg></button>
```

and after `<dl class="stats-overlay" id="stats-panel" hidden></dl>`:

```html

      <!-- The run beside the picture, a season at a time: over the video
           rather than beside it, because it is open for a moment and the
           film keeps playing under it. -->
      <aside class="episode-sidebar" id="episode-sidebar" tabindex="-1" aria-label="Episodes" hidden></aside>
```

`player-card.css` — insert above `@media (max-width: 767px) {`:

```css
/* The episode sidebar: over the right of the picture, in the card's fill.
   Its rows are the show page's, re-inked for the stage. */
.episode-sidebar {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  width: min(360px, 100%);
  background: color-mix(in srgb, var(--stage) 45%, transparent);
  -webkit-backdrop-filter: blur(24px) saturate(1.2); /* deslop-ignore 19: player card, see the note above */
  backdrop-filter: blur(24px) saturate(1.2); /* deslop-ignore 19: player card, see the note above */
  border-left: 1px solid rgba(255, 255, 255, 0.08);
}
.sidebar-head { display: flex; align-items: center; gap: 0.25rem; padding: 0.75rem 0.75rem 0.5rem 1rem; }
.sidebar-title { flex: 1; margin: 0; font-family: var(--display); font-size: 1.05rem; font-weight: 500; }
.sidebar-rows { flex: 1; overflow-y: auto; padding: 0 0.5rem 1rem; }
.episode-sidebar .season { margin: 0; }
.episode-sidebar .row { min-height: 48px; padding: 12px 8px; border-bottom-color: rgba(255, 255, 255, 0.08); color: var(--stage-ink); cursor: pointer; }
.episode-sidebar .row:hover { background: rgba(255, 255, 255, 0.06); }
.episode-sidebar .row:hover .title b { color: var(--stage-accent); }
.episode-sidebar .row .num,
.episode-sidebar .row .meta { color: var(--stage-ink-2); }
/* Number, title and runtime: the file's codec and badges are the show page's to say. */
.episode-sidebar .row .title > span,
.episode-sidebar .row .badge,
.episode-sidebar .row .has-summary { display: none; }
.episode-sidebar .row.is-watched { opacity: 0.45; }
/* Disabled only so it cannot be picked; it is the one row that should read loudest. */
.episode-sidebar .row[aria-current="true"] { opacity: 1; cursor: default; background: none; }
.episode-sidebar .row[aria-current="true"] .title b { color: var(--stage-ink); }
.episode-sidebar .row[aria-current="true"] .meta { color: var(--stage-accent); }
.episode-sidebar .row .watched-at { background: var(--stage-accent); }

```

- [ ] Step 4: same command → PASS (9 tests); `bun test test/browser-html-player.test.ts test/player-card.test.ts` → PASS (☰ is in the page, hidden, and nothing mounts it yet).
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/episode-sidebar.js web/public/index.html web/public/styles/player-card.css web/test/support/player-environment.ts web/test/episode-sidebar.test.ts
git commit -m "feat(player): add the episode sidebar"
```

---

### Task 3: Open the sidebar from the card

**Files:**
- Modify: `web/public/lib/playback/player-next-title.js` (doc, import, mount, `open`, `clear`, return); `web/public/lib/playback/player.js:44-53` (typedef), `:85` (HUD line); `web/public/app.js:55,314`
- Test: `web/test/player-card.test.ts:7` (import) + append; `web/test/browser-application.test.ts` (before `test("a film opened from its page has no run to step through"`)

**Interfaces:**
- Consumes: `mountEpisodeSidebar` (Task 2), `openInRun` and `showControls` (`player-next-title.js`, phase 01 Task 9), `collectionOf` (phase 01 Task 2), `mountPlayerHud`'s `holding` (phase 01 Task 5), `mountPlayerMenus` Esc `preventDefault` (phase 01 Task 4).
- Produces: `PlayerOptions.collection` (the run's show or course, `null` for a film or a list); `mountPlayerNextTitle(...)` also returns `sidebarOpen() → boolean`; the HUD holds while the sidebar is open.

- [ ] Step 1: Write the failing test. In `web/test/player-card.test.ts` change the first import to `import { afterEach, beforeEach, describe, expect, test } from "bun:test";` and append:

```ts
describe("the episode sidebar", () => {
  const ep = (id: string, episode: string) => ({ ...set(id), kind: "ep", show: "Star City", season: 1, episode });
  const show = { name: "Star City", divisions: [{ title: "Season 1", season: 1, items: [ep("e1", "1"), ep("e2", "2"), ep("e3", "3")], children: [] }] };
  const escape = () => {
    const event = Object.assign(new Event("keydown", { cancelable: true }), { key: "Escape" });
    env.node("player").dispatchEvent(event);
    return event;
  };

  test("☰ is offered for a run with a show behind it and not for a film", () => {
    openPlayer(set("film"));
    expect(env.node("episodes").hidden).toBe(true);
    openPlayer(ep("e2", "2"), { next: ep("e3", "3"), previous: ep("e1", "1"), inRun: true, collection: show });
    expect(env.node("episodes").hidden).toBe(false);
  });

  test("a row opens through the same path as Next, and the sidebar shuts", () => {
    const opened: Array<[string, unknown]> = [];
    const onOpenNext = (following: { setId: string }, how: unknown) => opened.push([following.setId, how]);
    openPlayer(ep("e1", "1"), { next: ep("e2", "2"), inRun: true, collection: show, onOpenNext });
    env.node("episodes").fire("click");
    env.node("episode-sidebar").children[1]!.children[0]!.children[2]!.fire("click");
    expect(opened).toEqual([["e3", { autoplay: "asap" }]]);
    expect(env.node("episode-sidebar").hidden).toBe(true);
  });

  test("the card stays up while it is open, and Esc shuts a menu over it before the sidebar, the player staying open", async () => {
    openPlayer(ep("e1", "1"), { next: ep("e2", "2"), inRun: true, collection: show });
    env.node("player").open = true;
    await env.video.play();
    env.node("episodes").fire("click");
    env.advance(10_000);
    expect(env.node("player").classes.has("resting")).toBe(false);
    env.node("speed").fire("click");
    expect(escape().defaultPrevented).toBe(true);
    expect(env.node("card-menu").hidden).toBe(true);
    expect(env.node("episode-sidebar").hidden).toBe(false);
    expect(escape().defaultPrevented).toBe(true);
    expect(env.node("episode-sidebar").hidden).toBe(true);
    expect(env.node("player").open).toBe(true);
    expect(escape().defaultPrevented).toBe(false);
    env.advance(2600);
    expect(env.node("player").classes.has("resting")).toBe(true);
  });
});
```

Insert into `web/test/browser-application.test.ts` before `test("a film opened from its page has no run to step through"`:

```ts
test("an episode opened from its show offers the sidebar; the same title from a list does not", async () => {
  const ep = (setId: string, episode: string) => ({ ...film(setId), kind: "ep", show: "Star City", season: 1, episode });
  catalog = JSON.stringify([ep("One", "1"), ep("Two", "2")]);
  snapshot = { collections: [{ id: "list", name: "My list", items: ["Two", "One"] }] };
  await start();
  await env.navigate("#/series/Star%20City");
  descendants(env.node("main")).filter((node) => node.className === "row")[1]!.fire("click");
  await settle();
  expect(env.video.src).toBe("/api/sets/Two/stream");
  expect(env.node("episodes").hidden).toBe(false);
  env.node("episodes").fire("click");
  expect(textOf(env.node("episode-sidebar"))).toContain("Season 1");
  env.node("player").close();
  await env.navigate("#/collections/list");
  descendants(env.node("main")).find((node) => node.textContent === "Play all")!.fire("click");
  await settle();
  expect(env.video.src).toBe("/api/sets/Two/stream");
  expect(env.node("episodes").hidden).toBe(true);
});
```

- [ ] Step 2: `cd web && bun test test/player-card.test.ts test/browser-application.test.ts` → FAIL: "☰ is offered for a run with a show behind it and not for a film" (`Expected: true` / `Received: false` — nothing hides ☰ yet), and the app test `Expected to contain: "Season 1"`.
- [ ] Step 3: Minimal implementation. `player-next-title.js` — apply:

```diff
--- a/public/lib/playback/player-next-title.js
+++ b/public/lib/playback/player-next-title.js
@@ -1,6 +1,6 @@
 /**
  * The run around the open title: the next-title offer with its countdown,
- * cancellation and speculative work, and the card's ⏮ and ⏭ — every way of
- * moving along a run opens through `openInRun`.
+ * cancellation and speculative work, the card's ⏮ and ⏭, and the ☰ sidebar —
+ * every way of moving along a run opens through `openInRun`.
  */
 import { episodeLabel } from "../format.js";
@@ -10,4 +10,5 @@ import * as state from "../watch-state.js";
 import { warmTranscode } from "./streaming/hls-playback.js";
 import { COUNTDOWN_SECONDS, upNextPhase } from "./up-next.js";
+import { mountEpisodeSidebar } from "./episode-sidebar.js";
 
 const PRELOAD_BYTES = 8 * 1024 * 1024;
@@ -34,4 +35,5 @@ export function mountPlayerNextTitle({ showControls, openTitle }) {
   let preloaded = null;
   let warm = null;
+  const sidebar = mountEpisodeSidebar({ onPick: (set) => openInRun(set), onClose: showControls });
 
   const titleLine = (set) => [set.show, episodeLabel(set), set.title].filter(Boolean).join(" · ");
@@ -74,4 +76,5 @@ export function mountPlayerNextTitle({ showControls, openTitle }) {
     preloaded = null;
     offer();
+    sidebar.open(set, options.collection ?? null);
   }
 
@@ -82,4 +85,5 @@ export function mountPlayerNextTitle({ showControls, openTitle }) {
     inRun = false;
     offer();
+    sidebar.close();
   }
 
@@ -164,4 +168,4 @@ export function mountPlayerNextTitle({ showControls, openTitle }) {
     dropWarm();
   });
-  return { open, update, preload, releaseWarm, clear, openInRun };
+  return { open, update, preload, releaseWarm, clear, openInRun, sidebarOpen: sidebar.isOpen };
 }
```

`player.js` — in the `PlayerOptions` typedef, after the `[inRun]` line add

```js
 * @property {import("../library.js").Collection|null} [collection] The run's show or course, for the sidebar.
```

and replace the four `[freshResume]` lines with these three (same meaning, one line shorter, so the file stays at 994):

```js
 * @property {Promise<number|null>} [freshResume] A position read from the server
 *   after the title opened (see `play` in `app.js`), applied only for direct
 *   playback while the viewer has not really begun; see `RESUME_DRIFT_SECONDS`.
```

and make the HUD line

```js
  const hud = mountPlayerHud({ dialog, video, card: document.getElementById("control-card"), holding: () => menus.isOpen() || upNext.sidebarOpen() });
```

(`upNext` is declared a few lines later; `holding` is only called from events, after mount.)
`app.js:55` → `import { collectionOf, playsNext, requestPreload } from "./lib/playback/plays-next.js";`
`app.js:314` → `    next, previous, inRun, collection: queue ? null : collectionOf(library, set),`
- [ ] Step 4: same command → PASS; `bun test` → 3055 pass; `bun test test/code-standards.test.ts` → PASS (`player.js` 994, `app.js` 694); `bun run typecheck && bun run lint` clean.
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/player-next-title.js web/public/lib/playback/player.js web/public/app.js web/test/player-card.test.ts web/test/browser-application.test.ts
git commit -m "feat(player): open the episode sidebar from the card for a show or course"
```

---

### Task 4: Close-out — docs, full check, preview walk

**Files:**
- Modify: `web/DESIGN.md` (Player section)
- Test: the whole web suite

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: the web reference phases 03–05 port; `DESIGN.md` describes the sidebar.

- [ ] Step 1: Write the doc change. In `web/DESIGN.md`'s Player section change the Hiding line's end to `…while a menu or the episode sidebar is open, or while the pointer is on the card.`, extend Rows' transport item to `transport (centred, with stats and episodes at the end)`, and add after the Stats line:

```markdown
- **Episodes**: ☰ opens a 360px sidebar over the right of the picture, in the card's fill, a season at a time behind a `‹ Season N ›` switcher. Its rows are the show page's, re-inked for the stage: number, title, runtime; watched ones at 45% opacity with ✓; a progress line under a part-watched one; "Now playing" in the accent on the current one. Shown for a show, a course or a documentary collection; not for a film or a hand-built list.
```

- [ ] Step 2: `cd web && bun test` → 3055 pass, 0 fail (the doc change adds no test; this run is the gate).
- [ ] Step 3: Full check and walk: `cd web && bun run lint && bun run typecheck && bun test`. Then `cd web && bun run preview` (restart it after edits; open `http://127.0.0.1:8795/?r=<n>#/series`), pick a profile that opens without a PIN (Cancel any "set a PIN" prompt; never answer "Who runs this household?"), open a show with several seasons and an episode in the middle of a season:
  1. ☰ sits at the right end of the card's last row; pressing it opens the sidebar on that season, "Now playing" visible without scrolling, ‹ disabled on the first season.
  2. ‹ / › change the season title and rows; the card does not fade while the sidebar is open (wait 5 s).
  3. Open Speed while the sidebar is open, press Esc: the list closes, the sidebar stays; Esc again: the sidebar closes, focus is on ☰, the player is still open.
  4. Pick another row: that episode opens (its title in the top bar), the sidebar is shut.
  5. Play a film from its page: no ☰. Play a list from Collections: ⏮/⏭ shown, no ☰.
  6. At 390×844: the sidebar is 360px from the right edge, `document.getElementById("player").scrollLeft === 0`.
  Screenshots to `plans/261005-0157-player-control-card/reports/`.
- [ ] Step 4: All of the above hold.
- [ ] Step 5: Commit.

```bash
git add web/DESIGN.md
git commit -m "docs(player): describe the episode sidebar"
```

## Security considerations

Row text is the catalog's titles through `seasonBlock`/`el()` (`textContent`, never markup), as on the show page. No new requests.

## Next steps

Phase 03 builds the Android episode-list model to the same rules: groups from the collection, season-less episodes last for display, the run order untouched for ⏮/⏭, an unknown playing id still listing everything, lists without a sidebar unless Android decides otherwise and writes down why.

## Unresolved questions

- Should a hand-built list get a flat sidebar of its own titles? Ruled no here (no seasons to group, and the list page is its table of contents); cheap to add later as one group if the user wants it.
