# Phase 01: Web control card

## Context links

- [spec.md](spec.md) — binding; see Layout, Look, Behaviour (Transport, Tools, Menus, Stats, Hiding), Keys, Testing, Rollout step 1, and the User decisions table.
- [plan.md](plan.md) — Global Constraints and Review Focus 1–4.
- Code read for this phase (line numbers are `main` before this phase; later tasks shift them, so match on the quoted text):
  `web/public/index.html:111-231`, `web/public/lib/playback/{player.js,transport.js,player-hud.js,player-keys.js,framing.js,subtitle-picker.js,subtitle-choice.js,subtitle-panel.js,audio-chooser.js,player-next-title.js,plays-next.js,preload-readout.js,playback-report.js}`, `web/public/lib/format.js:60-111`, `web/public/styles/playback.css`, `web/public/styles/shell.css:83-84`, `web/test/code-standards.test.ts`.
- Memory notes: `verify-player-ui-with-a-stub-harness`, `player-aesthetic-is-a-user-decision`, `profile-data-spreads-household-wide`.
- Every code block was applied to a scratch copy of `web/` task by task, with the full suite, `typecheck` and `lint` run after each; the card was looked at in the stub preview at 1440×900 and 390×844. Diff blocks are relative to `web/` (`git apply --directory=web`, or apply by hand).

## Overview

- **Priority:** P1 — the reference surface every Android phase copies.
- **Status:** pending.
- **What:** the bottom bar, rail and `<select>`s become one blurred card at the foot of the picture under a slim top bar. Speed, Audio, Framing and CC ▾ open small menus; CC toggles; ⏮ ⏭ ↺ and 15 s skips join the transport; ⓘ shows a stats overlay that absorbs the preload readout and the `#tech` line.
- **Baseline:** `cd web && bun test` → 3002 pass across 211 files (2026-10-05). After this phase: 3033 pass across 215 files (dry-run on a scratch copy; `typecheck` and `lint` clean).

## Key insights

- **Line ceilings are a ratchet** (`web/test/code-standards.test.ts:58-87`). `player.js` sits at exactly 996 of 996, `transport.js` 388 of 388, `app.js` 694 of 694, `shell.css` 257 of 257. Every task below is sized so those never grow: framing moves out of `transport.js` into `framing-control.js`, and `player.js` pays for its new lines with a misplaced 6-line comment and three repeated `recall`/`remember` lambdas. Phase end: `player.js` 994, `transport.js` 350, `playback.css` 625. Phase 02 adds no net line to `player.js`.
- **`.card` is taken.** `catalog.css:42` styles `.card` as a 64px+1fr grid; a dry run with `class="card"` put the seek row and the tools row side by side. The card is `control-card` (class and id).
- **The test DOM is thin.** `test/support/player-environment.ts` `Node` has no `querySelector`, `closest` or `classList.contains`, and `getElementById` invents missing nodes. Code here sticks to `children`, `className`, `classList.add/remove/toggle`, `getBoundingClientRect`, `focus`; that is why `player-menus.js` positions with `getBoundingClientRect` and why lists are found with `children.find`.
- **A menu opened by the click that started the rest timer** would still let the card rest 2.6 s later: `holding()` is asked again when the timer fires (`player-hud.js`), not only when `show()` runs.
- **Esc order needs no coordinator.** `player-menus.js` registers its dialog `keydown` listener first and calls `preventDefault()`; anything registered later (phase 02's sidebar) checks `event.defaultPrevented`. `preventDefault()` on the Esc keydown is also what stops a modal `<dialog>` from closing.
- **The menu is a sibling of the card, not a child.** An element with `backdrop-filter` is a backdrop root: a blurred menu nested inside the blurred card would only blur what lies inside the card's box. `#card-menu` and the cue panel sit in `#card-dock` beside `#control-card`.
- **The preview caches static files at startup** — restart `bun run preview` after editing CSS/HTML, and load the page with a fresh query string (`/?r=1#/…`), or the browser keeps the old document.
- **Previous/next ride the existing next-title path.** `player-next-title.js` already owns `onOpenNext`; ⏮, ⏭ and the countdown all open through one `openInRun(set, how)` that phase 02's sidebar reuses.

## Requirements

- Functional: everything in spec Rollout step 1 — card (three rows), slim top bar (title, My List, Kids/age, Add to…, Notes, ✕), CC toggle + ▾ menu (languages, Off, Style…), Speed / Audio / Framing menus, volume + fullscreen in row 2, ↺ ⏮ −15 ▶ +15 ⏭ in row 3, ⓘ stats overlay (video, audio, buffer, cache, dropped), 15 s on buttons and arrow keys, bottom bar / rail / selects / bottom gradient / `#tech` / `#preload` retired, blur exception documented.
- Accessible names exactly: "Restart", "Previous", "Back 15 seconds", "Play"/"Pause", "Forward 15 seconds", "Next", "Subtitles" (`aria-pressed`), "Subtitle options", "Speed", "Audio", "Framing", "Stats" (`aria-pressed`). Menu openers carry `aria-haspopup`/`aria-expanded`/`aria-controls`; menu rows carry `aria-pressed` for the current value.
- Non-functional: no new dependency; every new source file under 200 lines; no ceiling grows; targets ≥ 44px; controls never lift or grow on hover/focus; code comments say why and never cite plans.

## Related code files

- **Create:** `web/public/lib/playback/player-menus.js`, `web/public/lib/playback/framing-control.js`, `web/public/lib/playback/player-stats.js`, `web/public/styles/player-card.css`, `web/test/player-menus.test.ts`, `web/test/framing-control.test.ts`, `web/test/player-card.test.ts`, `web/test/player-stats.test.ts`.
- **Modify:** `web/public/index.html`, `web/public/app.js`, `web/public/lib/playback/{player.js,transport.js,player-hud.js,player-keys.js,subtitle-picker.js,subtitle-panel.js,audio-chooser.js,player-next-title.js,plays-next.js}`, `web/public/lib/format.js`, `web/public/lib/format.d.ts`, `web/public/styles/playback.css`, `web/public/styles/shell.css`, `web/DESIGN.md`; tests `web/test/{browser-html-player,browser-application,player-keys,player-features,player-initialization,player-lifetime,player-manual-play,plays-next,subtitle-picker,audio-chooser,format,code-standards}.test.ts`, `web/test/support/player-environment.ts`.
- **Delete:** nothing as a file. Retired in place: `#tech`, `#preload`, `.hud-bottom`, `.rail`, `.bar`, the three `<select>`s and their `.picker` rules, `#cue-settings`, `fillChooser` (`audio-chooser.js:106-130`), `technicalLine`/`partsLabel` (`format.js:60-111`, dead once `#tech` goes).

## Success criteria

- `cd web && bun test` → 3033 pass, 0 fail; `bun run typecheck` and `bun run lint` clean.
- `wc -l` → `player.js` 994, `transport.js` 350, `playback.css` 625; ceilings lowered to match (Task 11).
- Stub preview at 1440×900 and 390×844 shows the card in three rows (the stats button wraps under the transport on the narrow one), menus open above their buttons inside the card's width, Esc closes only an open menu, the stats overlay sits top left under the top bar.
- Review focus 1–4 each pinned by a named test: `player-card.test.ts` "back 15 near the start…" (1), `player-menus.test.ts` "Esc closes the menu…" (2), `player-features.test.ts` "an open menu or a pointer on the card holds it up…" and `player-card.test.ts` "the card stays up while a menu is open…" (3), `subtitle-picker.test.ts` "on, with nothing remembered…" and "a title with no subtitles at all…" (4).

## Risks

| Risk | L × I | Mitigation |
|---|---|---|
| A ceiling test fails mid-phase because a task grew `player.js`/`transport.js` | M × M | Each task's code was dry-run on a scratch copy and the full suite run after every task; run `bun test test/code-standards.test.ts` before each commit. |
| Esc in browser fullscreen: the browser itself leaves fullscreen and the page cannot stop it (Keyboard Lock is not used) | H × L | The test asserts only that the player's own handler does not exit fullscreen or close the dialog; documented here, not fought. |
| Up-next card overlaps the card on short windows | M × L | `.up-next` bottom raised to 12.5rem (15rem narrow); Task 11 checks it in the preview by un-hiding `#up-next` and adjusts the one value if needed. |
| Focus rule holds the card forever after a mouse click focuses a button (Chrome) | pre-existing | Unchanged by this phase (`player-hud.js` keeps today's focus rule); not widened. |
| Removing `technicalLine` breaks a caller outside the player | L × L | Only `player.js:647` and `format.test.ts` used it (grep, 2026-10-05). |

## Rollback

Each task is one commit; `git revert` in reverse order restores the previous player. Tasks 3–10 touch `index.html` and the tests together, so reverting a task never leaves ids the code still reads.

---

### Task 1: Skip is 15 seconds

**Files:**
- Modify: `web/public/lib/playback/player-keys.js:14-15,56`; `web/public/lib/playback/transport.js:10-11,45-46,87,92-96,250-251`; `web/public/index.html:188,190`
- Test: `web/test/player-keys.test.ts:12-15,106,114`; `web/test/browser-html-player.test.ts:86-87`

**Interfaces:**
- Consumes: `keyAction` (`player-keys.js:52`), `SKIP_SECONDS`, `skipButton` (`transport.js:46,92`).
- Produces: `SKIP_SECONDS = 15`; `keyAction` arrows `{ do: "skip", by: ±15 }`; `skipButton(button, path, seconds, way)` now also sets `aria-label` `"<way> <seconds> seconds"` ("Back 15 seconds", "Forward 15 seconds"), so the markup no longer carries one.

- [ ] Step 1: Write the failing test. In `web/test/player-keys.test.ts` replace

```ts
  test("the arrows skip the same ten the buttons do", () => {
    expect(keyAction({ key: "ArrowLeft" })).toEqual({ do: "skip", by: -10 });
    expect(keyAction({ key: "ArrowRight" })).toEqual({ do: "skip", by: 10 });
```

with

```ts
  test("the arrows skip the same fifteen the buttons do", () => {
    expect(keyAction({ key: "ArrowLeft" })).toEqual({ do: "skip", by: -15 });
    expect(keyAction({ key: "ArrowRight" })).toEqual({ do: "skip", by: 15 });
```

and `expect(keyAction({ key: "ArrowRight", onButton: true })).toEqual({ do: "skip", by: 10 });` with `... by: 15 });`, and the comment `// ctrl+F is find and meta+← is back; neither asks to skip ten seconds.` with `// ctrl+F is find and meta+← is back; neither asks to skip.`
In `web/test/browser-html-player.test.ts` replace

```ts
  env.node("skip-forward").fire("click");
  expect(env.video.currentTime).toBe(10);
```

with

```ts
  expect(env.node("skip-back").getAttribute("aria-label")).toBe("Back 15 seconds");
  expect(env.node("skip-forward").getAttribute("aria-label")).toBe("Forward 15 seconds");
  env.node("skip-forward").fire("click");
  expect(env.video.currentTime).toBe(15);
```

- [ ] Step 2: `cd web && bun test test/player-keys.test.ts test/browser-html-player.test.ts` → FAIL: "the arrows skip the same fifteen the buttons do" (`Expected … "by": -15` / `Received … "by": -10`) and the HTML test (`Expected: "Back 15 seconds"` / `Received: "Back ten seconds"`).
- [ ] Step 3: Minimal implementation.
`player-keys.js:14-15`:

```js
/** Fifteen: the same the buttons skip, and the same every other surface skips. */
const SKIP = 15;
```

`player-keys.js:56`: `// find, meta+← is back, and neither is a request to skip.`
`transport.js:10-11`: `* The shape is the phone's, from the Android player's transport design: skip` / `* back, play/pause, skip on, elapsed on the left and duration on the right.`
`transport.js:45-46`:

```js
/** Fifteen: the same every surface skips, and the same the buttons say. */
export const SKIP_SECONDS = 15;
```

`transport.js:87`: `* skip of some unstated length — the phone had to be told its skip explicitly`
`transport.js:92-96` (the function body; the text node needs no wrapper, `#skip-back`/`#skip-forward` style the button, not a span):

```js
function skipButton(button, path, seconds, way) {
  button.replaceChildren(icon(path), String(seconds));
  button.setAttribute("aria-label", `${way} ${seconds} seconds`);
}
```

`transport.js:250-251`:

```js
  skipButton(back, ICONS.back, SKIP_SECONDS, "Back");
  skipButton(forward, ICONS.forward, SKIP_SECONDS, "Forward");
```

`index.html:188` → `<button id="skip-back" class="tsp"></button>`; `index.html:190` → `<button id="skip-forward" class="tsp"></button>` (the label now comes from `SKIP_SECONDS`, one source).
- [ ] Step 4: same command → PASS (27 tests). `bun test test/code-standards.test.ts` → PASS (`transport.js` 387).
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/player-keys.js web/public/lib/playback/transport.js web/public/index.html web/test/player-keys.test.ts web/test/browser-html-player.test.ts
git commit -m "feat(player): skip fifteen seconds and name the skip buttons by their length"
```

---

### Task 2: Run neighbours — previous, next and whether there is a run

**Files:**
- Modify: `web/public/lib/playback/plays-next.js:1-51` (whole file)
- Test: `web/test/plays-next.test.ts` (whole file)

**Interfaces:**
- Consumes: `flattenCollection`, `nextInQueue` (`web/public/lib/library.js:211,231`).
- Produces: `collectionOf(library, set) → Collection|null`; `previousInQueue(sets, setId) → T|null` (null at the start and for an id the run lacks); `playsNext(library, set, queue) → { next, previous, inRun, preload }`. `inRun` is false only when there is neither a list nor a collection. Phase 02 imports `collectionOf`.

- [ ] Step 1: Write the failing test — replace `web/test/plays-next.test.ts` with:

```ts
/**
 * What plays after a title, and what the server is asked to fetch ahead of
 * it. The preload rule is a decision about Telegram traffic from a
 * flood-limited account — the next two series episodes, and nothing for a
 * lesson, a documentary or a hand-built list — so it is pinned here, beside
 * the "next" it must always agree with.
 */

import { describe, expect, test } from "bun:test";
import { catalogSet as set } from "./support/catalog-set";
import { groupDepartments } from "../public/lib/departments.js";
import { collectionOf, playsNext, previousInQueue } from "../public/lib/playback/plays-next.js";
import type { CatalogSet, Library } from "../public/lib/library.js";

/** A library the way the session builds one. */
function libraryOf(sets: CatalogSet[]): Library {
  return groupDepartments(sets);
}

const e1 = set({ kind: "ep", show: "Star City", season: 1, episode: "1", title: "S1E1" });
const e2 = set({ kind: "ep", show: "Star City", season: 1, episode: "2", title: "S1E2" });
const e3 = set({ kind: "ep", show: "Star City", season: 2, episode: "1", title: "S2E1" });
const e4 = set({ kind: "ep", show: "Star City", season: 2, episode: "2", title: "S2E2" });
const l1 = set({ kind: "tut", show: "Kurs", path: "A", episode: "1", title: "A1" });
const l2 = set({ kind: "tut", show: "Kurs", path: "A", episode: "2", title: "A2" });
const d1 = set({ kind: "docu", show: "Terra X", episode: "1", title: "Terra 1" });
const d2 = set({ kind: "docu", show: "Terra X", episode: "2", title: "Terra 2" });
const film = set({ kind: "movie", title: "Blade" });
const a1 = set({ kind: "ep", show: "Dragonball", episode: "1", title: "A1", anime: true });
const a2 = set({ kind: "ep", show: "Dragonball", episode: "2", title: "A2", anime: true });
const library = libraryOf([e1, e2, e3, e4, l1, l2, d1, d2, film, a1, a2]);

describe("playsNext", () => {
  test("an episode plays on through its show and preloads the next two, across seasons, and no more", () => {
    expect(playsNext(library, e1, null)).toEqual({ next: e2, previous: null, inRun: true, preload: [e2.setId, e3.setId] });
  });

  test("the second-to-last episode preloads the one left", () => {
    expect(playsNext(library, e3, null)).toEqual({ next: e4, previous: e2, inRun: true, preload: [e4.setId] });
  });

  test("the last episode has nothing next and preloads nothing", () => {
    expect(playsNext(library, e4, null)).toEqual({ next: null, previous: e3, inRun: true, preload: [] });
  });

  test("a lesson plays on through its course but preloads nothing", () => {
    expect(playsNext(library, l1, null)).toEqual({ next: l2, previous: null, inRun: true, preload: [] });
  });

  test("a documentary plays on through its collection but preloads nothing", () => {
    expect(playsNext(library, d1, null)).toEqual({ next: d2, previous: null, inRun: true, preload: [] });
  });

  test("a film belongs to no collection: no run, nothing either side, nothing preloaded", () => {
    expect(playsNext(library, film, null)).toEqual({ next: null, previous: null, inRun: false, preload: [] });
  });

  test("a list plays on in its own order and preloads nothing, even when it holds episodes", () => {
    expect(playsNext(library, e1, [e1, e4, film])).toEqual({ next: e4, previous: null, inRun: true, preload: [] });
  });

  test("an anime episode plays the next one, and preloads the same as any other show", () => {
    expect(playsNext(library, a1, null)).toEqual({ next: a2, previous: null, inRun: true, preload: [a2.setId] });
  });

  test("a list steps back in its own order too, and its first title has nothing before it", () => {
    expect(playsNext(library, film, [e1, e4, film])).toEqual({ next: null, previous: e4, inRun: true, preload: [] });
    expect(playsNext(library, e4, [e1, e4, film]).previous).toBe(e1);
  });

  test("the step back crosses a season boundary the same way the step on does", () => {
    expect(playsNext(library, e3, null).previous).toBe(e2);
  });

  test("a title its run does not hold is still in a run, with neither neighbour", () => {
    const stray = set({ kind: "ep", show: "Star City", season: 9, episode: "9", title: "Not indexed" });
    expect(playsNext(library, stray, null)).toEqual({ next: null, previous: null, inRun: true, preload: [] });
  });
});

describe("collectionOf", () => {
  test("finds a title's show, course or documentary collection, and nothing for a film", () => {
    expect(collectionOf(library, e1)?.name).toBe("Star City");
    expect(collectionOf(library, l1)?.name).toBe("Kurs");
    expect(collectionOf(library, d1)?.name).toBe("Terra X");
    expect(collectionOf(library, a1)?.name).toBe("Dragonball");
    expect(collectionOf(library, film)).toBeNull();
  });
});

describe("previousInQueue", () => {
  const run = [{ setId: "a" }, { setId: "b" }, { setId: "c" }];

  test("the one before, and nothing before the first or for an id not in the run", () => {
    expect(previousInQueue(run, "c")?.setId).toBe("b");
    expect(previousInQueue(run, "b")?.setId).toBe("a");
    expect(previousInQueue(run, "a")).toBeNull();
    expect(previousInQueue(run, "gone")).toBeNull();
    expect(previousInQueue([], "a")).toBeNull();
  });
});
```

- [ ] Step 2: `cd web && bun test test/plays-next.test.ts` → FAIL: `SyntaxError: Export named 'collectionOf' not found in module '…/plays-next.js'`.
- [ ] Step 3: Minimal implementation — replace `web/public/lib/playback/plays-next.js` with:

```js
/**
 * What plays after a title, what played before it, and what the server
 * fetches ahead of it.
 *
 * One answer for all three, so what is fetched ahead is always what would play
 * next and the step back walks the same run as the step on. A hand-built list
 * plays on in its own order; anything else plays on through the show, course
 * or documentary collection it belongs to.
 *
 * Only a series episode preloads, and only the next two: not lessons, not
 * lists — the rule the preload was built to — and documentaries were never
 * added to it. The server fetches in the background from a flood-limited
 * account, so widening this is its own decision, measured first;
 * `POST /api/preload` keeps at most two, and only episodes, anyway.
 */

import { flattenCollection, nextInQueue } from "../library.js";

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 */

/**
 * The show, course or documentary collection `set` belongs to, or `null`.
 * @param {import("../library.js").Library} library
 * @param {CatalogSet} set
 */
export function collectionOf(library, set) {
  const every = [...library.series, ...library.anime.collections, ...library.tutorials, ...library.documentaries.collections];
  return every.find((entry) => entry.name === set.show) ?? null;
}

/**
 * What comes before `setId` in an ordered run, or `null` at its start — and
 * for an id the run does not hold, which has no neighbours to speak of.
 * @template {{setId: string}} T
 * @param {readonly T[]} sets
 * @param {string} setId
 * @returns {T|null}
 */
export function previousInQueue(sets, setId) {
  const at = sets.findIndex((set) => set.setId === setId);
  return at <= 0 ? null : sets[at - 1];
}

/**
 * `inRun` is whether there is a run at all. A film opened from a shelf has
 * none, and its player hides the step buttons rather than disabling them; the
 * first and last of a run disable one of the two instead.
 * @param {import("../library.js").Library} library
 * @param {CatalogSet} set the title being opened
 * @param {CatalogSet[]|null} queue the list it was played from, if any
 * @returns {{next: CatalogSet|null, previous: CatalogSet|null, inRun: boolean, preload: string[]}}
 */
export function playsNext(library, set, queue) {
  const collection = queue ? null : collectionOf(library, set);
  const run = queue ?? (collection ? flattenCollection(collection) : null);
  if (!run) return { next: null, previous: null, inRun: false, preload: [] };
  const next = nextInQueue(run, set.setId);
  const previous = previousInQueue(run, set.setId);
  if (queue || set.kind !== "ep" || !next) return { next, previous, inRun: true, preload: [] };
  const after = nextInQueue(run, next.setId);
  return { next, previous, inRun: true, preload: after ? [next.setId, after.setId] : [next.setId] };
}

/**
 * Asks the server to take `setIds` into its cache. Fire and forget: a player
 * with preload turned off answers 404, and either way the title plays the same.
 * @param {string[]} setIds
 */
export function requestPreload(setIds) {
  if (setIds.length === 0) return;
  fetch("/api/preload", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ setIds }),
  }).catch(() => {});
}
```

`app.js` keeps reading `{ next, preload }` until Task 9; the extra keys are ignored.
- [ ] Step 4: same command → PASS (13 tests).
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/plays-next.js web/test/plays-next.test.ts
git commit -m "feat(player): say what played before a title and whether it is in a run"
```

---

### Task 3: One card under a slim top bar

**Files:**
- Modify: `web/public/index.html:23` (stylesheet link), `:118-205` (the HUD); `web/public/styles/playback.css:64-75,204-209,237-253,320-330,414-421,434,729-737,743`; `web/public/styles/shell.css:83-84`; `web/DESIGN.md` (before `## Do's and Don'ts`); `web/public/lib/playback/player.js:361`
- Create: `web/public/styles/player-card.css`
- Test: `web/test/browser-html-player.test.ts:81`; `web/test/player-initialization.test.ts:46`

**Interfaces:**
- Consumes: every existing control id (`now`, `watchlist`, `kids`, `kids-age`, `add-to`, `notes`, `close`, `note`, `seek`, `seek-to`, `at-now`, `at-end`, `ends`, `preload`, `subs`, `sub-track`, `audio`, `audio-track`, `speed`, `speed-rate`, `mute`, `volume`, `fullscreen`, `skip-back`, `play-pause`, `skip-forward`, `play-next`) — all kept in this task, only moved.
- Produces: `#card-dock` (class `hud card-dock`, the positioned dock), `#control-card` (class `control-card`), rows `.card-row.card-seek`, `.card-row.card-tools`, `.card-row.card-transport` with `.transport` centred in a `1fr auto 1fr` grid and `.card-end` in column 3; the top bar holds the marks, Notes and `#close` (✕, `aria-label="Close"`). `player-card.css` exists with an `@media (max-width: 767px)` block that later tasks insert rules above.

- [ ] Step 1: Write the failing test. In `web/test/browser-html-player.test.ts` replace

```ts
  expect(env.document.querySelector(".hud-bottom")?.contains(env.node("cue-settings"))).toBe(true);
```

with

```ts
  // One card at the foot, its menus' panel in the same dock, and the marks in the top bar.
  expect(env.node("control-card").contains(env.node("cue-settings"))).toBe(true);
  expect(env.document.querySelector(".card-dock .cue-panel")).not.toBeNull();
  for (const id of ["now", "watchlist", "kids", "add-to", "notes", "close"]) {
    expect(env.document.querySelector(".hud-top")!.contains(env.node(id))).toBe(true);
  }
  expect(env.document.querySelector(".hud-bottom")).toBeNull();
  expect(env.document.querySelector(".rail")).toBeNull();
```

In `web/test/player-initialization.test.ts` replace `expect(env.node(".hud-bottom").children).toHaveLength(1);` with `expect(env.node("card-dock").children).toHaveLength(1);`.
- [ ] Step 2: `cd web && bun test test/browser-html-player.test.ts test/player-initialization.test.ts` → FAIL: `Missing HTML element: #control-card`, and `Expected length: 1` / `Received length: 0` for `card-dock`.
- [ ] Step 3: Minimal implementation.
`index.html:23` — after the `playback.css` link add `<link rel="stylesheet" href="/styles/player-card.css" />`.
`index.html:118-205` — replace from `<div class="hud hud-top">` through the closing `</div>` of `.hud-bottom` (the line before `<!-- Appears as a title runs out.`) with:

```html
      <!-- The title and what can be done with it, none of which acts on the
           picture: the marks, the notes and the way out. The top scrim stays
           behind it; the card below needs none. -->
      <div class="hud hud-top">
        <span class="what" id="now"></span>
        <!-- What the file is, which the catalogue says nowhere else at this
             length. Set quieter than the title: it answers a question a
             viewer only sometimes has. -->
        <span class="tech" id="tech"></span>
        <button id="watchlist" class="ghost" aria-pressed="false">My List</button>
        <button id="kids" class="ghost" aria-pressed="false">Kids</button>
        <!-- An unrated title is for kids only from the age a grown-up picks
             here; a rated one is decided by its rating, shown on the button above. -->
        <select id="kids-age" class="ghost" aria-label="For kids" hidden>
          <option value="">Not for kids</option>
          <option value="6">From 6</option>
          <option value="12">From 12</option>
        </select>
        <button id="add-to" class="ghost">Add to…</button>
        <button id="notes" class="ghost" hidden aria-expanded="false">Notes</button>
        <button id="close" class="ghost" aria-label="Close">✕</button>
      </div>

      <!-- The controls, as one card over the foot of the picture, in three
           rows: where the viewer is, how the title plays, and the transport.
           What its buttons open sits in the same dock, just above it, so it
           rests and comes back with the card. -->
      <div class="hud card-dock" id="card-dock">
        <div class="control-card" id="control-card">
          <p class="note" id="note" hidden></p>

          <!-- One bar for both ways a title plays, scaled to the film's running
               time and never to the media element's: a conversion is encoded as
               it plays, so its clock counts from wherever ffmpeg began and its
               duration reaches only as far as ffmpeg has got. -->
          <div class="card-row card-seek">
            <span class="at" id="at-now">0:00</span>
            <div class="seek" id="seek" hidden>
              <label for="seek-to">Position</label>
              <input type="range" id="seek-to" min="0" step="1" value="0" />
            </div>
            <span class="at" id="at-end"></span>
            <span class="ends" id="ends"></span>
            <!-- Preloading without playing means a still frame and no sign of
                 life; this is the sign of life. -->
            <span class="preload" id="preload"></span>
          </div>

          <div class="card-row card-tools">
            <span class="picker" id="subs" hidden>
              <label for="sub-track">Subtitles</label>
              <select id="sub-track"></select>
            </span>
            <span class="picker" id="audio" hidden>
              <label for="audio-track">Audio</label>
              <select id="audio-track"></select>
            </span>
            <span class="picker" id="speed">
              <label for="speed-rate">Speed</label>
              <select id="speed-rate"></select>
            </span>
            <span class="spacer"></span>
            <span class="volume">
              <button id="mute" class="tsp" aria-label="Mute"></button>
              <label for="volume">Volume</label>
              <input type="range" id="volume" min="0" max="1" step="0.01" value="1" />
            </span>
            <button id="fullscreen" class="tsp" aria-label="Fullscreen"></button>
          </div>

          <!-- Centred by a three-column grid rather than by the widths of what
               flanks it, so the transport does not drift when a button beside
               it appears. -->
          <div class="card-row card-transport">
            <span class="transport">
              <button id="skip-back" class="tsp"></button>
              <button id="play-pause" class="tsp big" aria-label="Play"></button>
              <button id="skip-forward" class="tsp"></button>
              <!-- Survives cancelling the countdown, which is the point: saying
                   "not automatically" must not mean "not at all". -->
              <button id="play-next" class="ghost" hidden>Play next</button>
            </span>
          </div>
        </div>
      </div>

```

(The selects, `#tech` and `#preload` are temporary; Tasks 6–8 and 10 replace them in place.)
Create `web/public/styles/player-card.css`:

```css
/* The player's control card: one card over the foot of the picture, under a
   slim top bar. Blurred, which the stylesheet allows in one other place (the
   masthead, shell.css) and for the same reason: it stays put while something
   moves under it. */

.card-dock {
  left: 0;
  right: 0;
  bottom: 24px;
  width: min(880px, calc(100% - 48px));
  margin-inline: auto;
}

.control-card {
  display: grid;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: color-mix(in srgb, var(--stage) 45%, transparent);
  -webkit-backdrop-filter: blur(24px) saturate(1.2); /* deslop-ignore 19: player card, see the note above */
  backdrop-filter: blur(24px) saturate(1.2); /* deslop-ignore 19: player card, see the note above */
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-card);
  /* The whole card, not only its buttons: a pointer resting in a gap between
     two controls is still a viewer about to use one. */
  pointer-events: auto;
}
dialog.resting .control-card { pointer-events: none; }

.card-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}
.card-seek .seek { flex: 1; margin: 0; }
/* Wraps rather than shrinking a target below a finger's width. */
.card-tools { flex-wrap: wrap; }
.card-tools .spacer { flex: 1; }
.card-transport {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
}
.card-transport .transport { grid-column: 2; }
.card-end {
  grid-column: 3;
  justify-self: end;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.control-card .at {
  color: var(--stage-ink);
  font-size: 0.82rem;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

@media (max-width: 767px) {
  .hud-top { flex-wrap: wrap; row-gap: 8px; }
  .card-dock { bottom: max(12px, env(safe-area-inset-bottom)); width: calc(100% - 24px); }
  .control-card { padding: 0.6rem 0.75rem; }
  /* The transport keeps its row; stats and episodes wrap beneath it. */
  .card-transport { display: flex; flex-wrap: wrap; justify-content: center; }
  .card-transport .transport { gap: 0.25rem; }
  .volume input { display: none; }
}
```

`playback.css` — delete these blocks whole: `.hud-bottom { … }` (64-75, the bottom gradient); `.rail { … }` and `.rail .spacer { flex: 1; }` (204-209); `.bar { … }`, `.bar-side { … }`, `.bar .picker, .bar .picker select { min-width: 0; }`, `.bar-side.end { … }` (237-253); both `@container hudbar` blocks (320-330); `.bar .at { … }` (414-421, now `.control-card .at`). In the `@media (max-width: 767px)` block delete `.hud-bottom { padding: … }`, `.rail { flex-wrap: wrap; gap: 8px; }`, `.rail .spacer { display: none; }`, `.bar { … }`, `.bar-side { … }`, `.transport { grid-column: 1 / -1; … }`, `.bar-side.end { … }`, and replace `.bar .picker select { font-size: 1rem; max-width: 7rem; }` with `.picker select { font-size: 1rem; max-width: 7rem; }`. In `.up-next` change `bottom: 9.5rem;` (434) to `bottom: 12.5rem;`, and in the narrow block `… bottom: 12rem; max-width: none; }` (743) to `bottom: 15rem;` — the Up-next card keeps clear of the card.
`shell.css:83-84` (same two lines, so the ceiling holds):

```css
  /* Blur is allowed here and on the player's card and what it opens
     (player-card.css): both stay put while something moves under them. */
```

`web/DESIGN.md` — insert before `## Do's and Don'ts`:

```markdown
### Player

The screening room keeps a slim top bar over the top scrim (the title, My List, Kids, Add to…, Notes, Close) and puts every control on one card over the foot of the picture. There is no bottom scrim.

- **Card**: up to 880px wide, centred, 24px from the bottom (12px either side and below on a narrow window). Stage colour at 45% under `backdrop-filter: blur(24px) saturate(1.2)`, a 1px hairline at 8% white, `--radius-card`, no shadow.
- **Blur exception**: the card and the panels it opens are the second place allowed a `backdrop-filter`; the masthead is the first. Both stay put while something moves under them, and the `shell.css` masthead comment names both. No other surface uses one.
- **Rows**: seek (elapsed, bar, running time, "ends"); tools (subtitles, speed, audio, framing, then volume and fullscreen); transport (centred, with stats at the end).
- **Hiding**: the card and the top bar rest together on the player's 2.6s timer, and stay up while paused.

```

(Task 11 and phase 02 each extend this section by one or two lines.)
`player.js:361` — `document.querySelector(".hud-bottom").prepend(cuePanel.panel);` → `document.getElementById("card-dock").append(cuePanel.panel);`
- [ ] Step 4: same command → PASS (10 tests); then `bun test` → 3007 pass.
- [ ] Step 5: Commit.

```bash
git add web/public/index.html web/public/styles/player-card.css web/public/styles/playback.css web/public/styles/shell.css web/DESIGN.md web/public/lib/playback/player.js web/test/browser-html-player.test.ts web/test/player-initialization.test.ts
git commit -m "feat(player): move the controls onto one card under a slim top bar"
```

---

### Task 4: Menus that open above the card

**Files:**
- Create: `web/public/lib/playback/player-menus.js`; `web/test/player-menus.test.ts`
- Modify: `web/public/index.html` (`#card-dock`, first child); `web/public/styles/player-card.css` (above the `@media` block)

**Interfaces:**
- Consumes: `el` (`web/public/lib/dom.js:10`); ids `player`, `card-dock`, `card-menu`.
- Produces: `mountPlayerMenus({ onClose }?) → { list(button, { items: () => MenuItem[], pick: (value: string) => void }), panel(button, node), close() → HTMLElement|null, isOpen() → boolean }` with `MenuItem = { value, label, current? }`. Rows are `button.menu-item` with `dataset.value` and `aria-pressed`. Esc on `#player` with something open: `preventDefault()` + close + focus back to the opener; with nothing open: untouched. Pointerdown outside the open node and its opener closes it.

- [ ] Step 1: Write the failing test — create `web/test/player-menus.test.ts`:

```ts
/**
 * The card's menus: one open at a time, choosing closes, Esc closes only the
 * menu, and a panel opened from a menu closes by the same rules.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mountPlayerMenus } from "../public/lib/playback/player-menus.js";
import { browserEnvironment, Node } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(() => {
  env = browserEnvironment();
});
afterEach(() => env.restore());

const rows = () => env.node("card-menu").children;
const escape = () => {
  const event = Object.assign(new Event("keydown", { cancelable: true }), { key: "Escape" });
  env.node("player").dispatchEvent(event);
  return event;
};

function mount() {
  let closes = 0;
  const picked: string[] = [];
  const menus = mountPlayerMenus({ onClose: () => { closes++; } });
  const items = () => [
    { value: "1", label: "1×", current: true },
    { value: "1.5", label: "1.5×" },
  ];
  menus.list(env.node("speed"), { items, pick: (value) => picked.push(`speed:${value}`) });
  menus.list(env.node("framing"), { items: () => [{ value: "fit", label: "Fit", current: true }], pick: (value) => picked.push(`framing:${value}`) });
  return { menus, picked, closes: () => closes };
}

describe("a list", () => {
  test("opens above its button with the current value marked, and choosing closes it", () => {
    const { menus, picked, closes } = mount();
    expect(env.node("speed").getAttribute("aria-expanded")).toBe("false");
    env.node("speed").fire("click");
    expect(menus.isOpen()).toBe(true);
    expect(env.node("card-menu").hidden).toBe(false);
    expect(env.node("speed").getAttribute("aria-expanded")).toBe("true");
    expect(rows().map((row) => [row.dataset.value, row.textContent, row.getAttribute("aria-pressed")])).toEqual([
      ["1", "1×", "true"], ["1.5", "1.5×", "false"],
    ]);
    rows()[1]!.fire("click");
    expect(picked).toEqual(["speed:1.5"]);
    expect(menus.isOpen()).toBe(false);
    expect(env.node("card-menu").hidden).toBe(true);
    expect(env.node("speed").getAttribute("aria-expanded")).toBe("false");
    expect(closes()).toBe(1);
  });

  test("only one is open at a time, and pressing the open one's button closes it", () => {
    const { menus } = mount();
    env.node("speed").fire("click");
    env.node("framing").fire("click");
    expect(env.node("speed").getAttribute("aria-expanded")).toBe("false");
    expect(env.node("framing").getAttribute("aria-expanded")).toBe("true");
    expect(rows().map((row) => row.dataset.value)).toEqual(["fit"]);
    env.node("framing").fire("click");
    expect(menus.isOpen()).toBe(false);
    expect(env.node("card-menu").hidden).toBe(true);
  });

  test("Esc closes the menu, takes the key from the dialog and leaves the player open", () => {
    const { menus, picked } = mount();
    const exits: string[] = [];
    Object.assign(env.document, { exitFullscreen: async () => { exits.push("exit"); } });
    env.node("player").open = true;
    env.node("speed").fire("click");
    const event = escape();
    expect(event.defaultPrevented).toBe(true);
    expect(menus.isOpen()).toBe(false);
    expect(picked).toEqual([]);
    expect(env.node("player").open).toBe(true);
    expect(exits).toEqual([]);
  });

  test("Esc with nothing open is left alone, for the dialog to close itself", () => {
    mount();
    expect(escape().defaultPrevented).toBe(false);
  });

  test("a press anywhere else in the player closes it; one on its rows or its button does not", () => {
    const { menus } = mount();
    env.node("speed").fire("click");
    const press = (target: Node) => {
      const event = new Event("pointerdown");
      Object.defineProperty(event, "target", { value: target });
      env.node("player").dispatchEvent(event);
    };
    press(rows()[0]!);
    press(env.node("speed"));
    expect(menus.isOpen()).toBe(true);
    press(env.video);
    expect(menus.isOpen()).toBe(false);
  });
});

describe("a panel", () => {
  test("opens in the list's place, and Esc, its button or another list close it", () => {
    const { menus } = mount();
    const style = env.node("cue-panel");
    style.hidden = true;
    menus.panel(env.node("cc-menu"), style);
    expect(style.hidden).toBe(false);
    expect(env.node("cc-menu").getAttribute("aria-expanded")).toBe("true");
    escape();
    expect(style.hidden).toBe(true);
    menus.panel(env.node("cc-menu"), style);
    env.node("speed").fire("click");
    expect(style.hidden).toBe(true);
    expect(env.node("card-menu").hidden).toBe(false);
    menus.close();
    expect(menus.isOpen()).toBe(false);
  });
});
```

- [ ] Step 2: `cd web && bun test test/player-menus.test.ts` → FAIL: `Cannot find module '../public/lib/playback/player-menus.js'`.
- [ ] Step 3: Minimal implementation — create `web/public/lib/playback/player-menus.js`:

```js
/**
 * What the card's buttons open: a short list just above the button — speed,
 * audio, framing, the subtitle options — or, for the subtitle style, a panel
 * of its own in the same place.
 *
 * One at a time, because two open lists over a picture are two things in the
 * way. Choosing closes the list. Esc closes whatever is open and nothing else:
 * the dialog would otherwise take the same key as a request to close the
 * player, and a viewer backing out of a list asked for no such thing.
 */

import { el } from "../dom.js";

/**
 * @typedef {{value: string, label: string, current?: boolean}} MenuItem
 * @param {{onClose?: () => void}} [options] `onClose` hears every close, so
 *   whatever was held up while a list was open can start its clock again.
 */
export function mountPlayerMenus({ onClose } = {}) {
  const dialog = document.getElementById("player");
  const dock = document.getElementById("card-dock");
  const menu = document.getElementById("card-menu");
  /** The button whose list or panel is open, and what it opened. */
  let opener = null;
  let shown = null;

  /** Closes whatever is open; answers the button that opened it, if any. */
  function close() {
    const was = opener;
    if (was === null) return null;
    shown.hidden = true;
    was.setAttribute("aria-expanded", "false");
    opener = shown = null;
    onClose?.();
    return was;
  }

  /** Shows `node` above `button`, closing anything else first. */
  function open(button, node) {
    close();
    opener = button;
    shown = node;
    node.hidden = false;
    button.setAttribute("aria-expanded", "true");
    // Kept inside the card's width: a list opened from the last button on a
    // row must not hang off the card's edge.
    const area = dock.getBoundingClientRect();
    const from = button.getBoundingClientRect().left - area.left;
    const room = area.width - node.getBoundingClientRect().width;
    node.style.left = `${Math.round(Math.max(0, Math.min(from, room)))}px`;
  }

  /**
   * A list of values behind `button`. `items` is asked on every open, so the
   * list marks what is current now rather than when it was first built.
   * @param {HTMLElement} button
   * @param {{items: () => MenuItem[], pick: (value: string) => void}} choices
   */
  function list(button, { items, pick }) {
    button.setAttribute("aria-haspopup", "true");
    button.setAttribute("aria-expanded", "false");
    button.setAttribute("aria-controls", menu.id);
    button.addEventListener("click", () => {
      if (opener === button) {
        close();
        return;
      }
      const rows = items().map((item) => {
        const row = el("button", "menu-item", item.label);
        row.type = "button";
        row.dataset.value = item.value;
        row.setAttribute("aria-pressed", String(item.current === true));
        row.addEventListener("click", () => {
          close();
          pick(item.value);
        });
        return row;
      });
      menu.replaceChildren(...rows);
      open(button, menu);
      // A keyboard starts from what is chosen now.
      (rows.find((row) => row.getAttribute("aria-pressed") === "true") ?? rows[0])?.focus();
    });
  }

  /** A panel opened from `button` that stays open while it is used. */
  function panel(button, node) {
    open(button, node);
    node.focus();
  }

  dialog.addEventListener("keydown", (event) => {
    if (event.key !== "Escape" || opener === null) return;
    // Taken here, so neither the dialog nor a listener after this one reads
    // the same key as a request of its own.
    event.preventDefault();
    close()?.focus();
  });
  // Anywhere else in the player is a viewer done with the list.
  dialog.addEventListener("pointerdown", (event) => {
    if (opener !== null && !shown.contains(event.target) && !opener.contains(event.target)) close();
  });

  return { list, panel, close, isOpen: () => opener !== null };
}
```

`index.html` — make the dock's first child `<div class="card-menu" id="card-menu" hidden></div>` (before `<div class="control-card" …>`).
`player-card.css` — insert above `@media (max-width: 767px) {` (the `.cue-panel` here re-skins the subtitle style panel, which sits in the same dock):

```css
/* What the card's buttons open: just above the card, inside its width, in
   the card's own fill. Placed by player-menus.js, which sets `left`. */
.card-menu,
.cue-panel {
  position: absolute;
  bottom: calc(100% + 8px);
  z-index: 2;
  background: color-mix(in srgb, var(--stage) 45%, transparent);
  -webkit-backdrop-filter: blur(24px) saturate(1.2); /* deslop-ignore 19: player card, see the note above */
  backdrop-filter: blur(24px) saturate(1.2); /* deslop-ignore 19: player card, see the note above */
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-card);
  pointer-events: auto;
}
.card-menu { display: grid; min-width: 9rem; padding: 0.35rem; }
.menu-item {
  min-height: 44px;
  padding: 0 0.85rem;
  border: none;
  border-radius: var(--radius);
  background: transparent;
  color: var(--stage-ink-2);
  font: inherit;
  font-size: 0.875rem;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition: color var(--hover), background-color var(--hover);
}
.menu-item:hover { color: var(--stage-ink); background: rgba(255, 255, 255, 0.08); }
.menu-item[aria-pressed="true"] { color: var(--stage-accent); }

```

- [ ] Step 4: same command → PASS (6 tests); `bun test test/browser-html-player.test.ts` → PASS.
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/player-menus.js web/test/player-menus.test.ts web/public/index.html web/public/styles/player-card.css
git commit -m "feat(player): add one-at-a-time menus that open above the control card"
```

---

### Task 5: The card stays up while a menu is open or the pointer rests on it

**Files:**
- Modify: `web/public/lib/playback/player-hud.js:1-40` (whole file); `web/public/lib/playback/player.js:23` (import), `:80` (HUD), `:346-351` (misplaced comment)
- Test: `web/test/player-features.test.ts:170-207`

**Interfaces:**
- Consumes: `mountPlayerMenus` (Task 4), `#control-card` (Task 3).
- Produces: `mountPlayerHud({ dialog, video, card, holding })` — `holding: () => boolean` is asked in `show()` and again when the rest timer fires; `pointerenter`/`pointerleave` on `card` hold/release. `player.js` now owns `const menus = mountPlayerMenus({ onClose: () => hud.show() })`, which Tasks 6–8 pass to `mountTransport`, `mountSubtitlePicker` and the audio menu, and phase 02 extends `holding` with the sidebar.

- [ ] Step 1: Write the failing test. In `web/test/player-features.test.ts`, in both existing "player HUD" tests replace `mountPlayerHud({ dialog, video: env.video })` with `mountPlayerHud({ dialog, video: env.video, card: env.node("control-card"), holding: () => false })`, and add after the second test, inside `describe("player HUD", …)`:

```ts
  test("an open menu or a pointer on the card holds it up, and letting go starts the clock again", async () => {
    const dialog = env.node("player");
    const card = env.node("control-card");
    let menuOpen = false;
    const hud = mountPlayerHud({ dialog, video: env.video, card, holding: () => menuOpen });
    hud.open();
    await env.video.play();
    // Opened by the same click that started the clock; nothing told the HUD.
    menuOpen = true;
    env.advance(10_000);
    expect(dialog.classes.has("resting")).toBe(false);
    menuOpen = false;
    hud.show();
    card.fire("pointerenter");
    env.advance(10_000);
    expect(dialog.classes.has("resting")).toBe(false);
    card.fire("pointerleave");
    env.advance(2599);
    expect(dialog.classes.has("resting")).toBe(false);
    env.advance(1);
    expect(dialog.classes.has("resting")).toBe(true);
    hud.clear();
  });
```
- [ ] Step 2: `cd web && bun test test/player-features.test.ts` → FAIL: "an open menu or a pointer on the card holds it up, and letting go starts the clock again" — `Expected: false` / `Received: true` (the card rested with a menu open).
- [ ] Step 3: Minimal implementation — replace `web/public/lib/playback/player-hud.js` with:

```js
/**
 * The control overlay's focus rules and rest timer, independent of media sources.
 *
 * `holding` answers whether something the viewer opened — a menu, a panel —
 * is up; the card stays while it is, and whatever closes it calls `show` so
 * the clock starts again from then.
 */
export function mountPlayerHud({ dialog, video, card, holding }) {
  const REST_MS = 2600;
  let active = false;
  let timer = null;
  /** A pointer on the card is a viewer about to use it. */
  let over = false;

  function clear() {
    active = false;
    clearTimeout(timer);
    timer = null;
    dialog.classList.remove("resting");
  }

  function show() {
    if (!active) return;
    dialog.classList.remove("resting");
    clearTimeout(timer);
    if (video.paused || over || holding()) return;
    const focused = document.activeElement;
    // The picture itself does not hold the controls open; a focused button does.
    if (focused !== null && focused !== video && dialog.contains(focused)) return;
    // Asked again as the clock runs out: a menu opened since it started — by
    // the very click that started it — holds the card as much as one open before.
    timer = setTimeout(() => {
      if (!holding()) dialog.classList.add("resting");
    }, REST_MS);
  }

  for (const event of ["pointermove", "pointerdown", "focusin", "focusout"]) {
    dialog.addEventListener(event, show);
  }
  for (const event of ["play", "pause", "ratechange"]) {
    video.addEventListener(event, show);
  }
  card.addEventListener("pointerenter", () => {
    over = true;
    show();
  });
  card.addEventListener("pointerleave", () => {
    over = false;
    show();
  });

  return {
    open() {
      active = true;
      show();
    },
    show,
    clear,
  };
}
```

`player.js` — after `import { mountPlayerHud } from "./player-hud.js";` add `import { mountPlayerMenus } from "./player-menus.js";`; replace `const hud = mountPlayerHud({ dialog, video });` with

```js
  const menus = mountPlayerMenus({ onClose: () => hud.show() });
  const hud = mountPlayerHud({ dialog, video, card: document.getElementById("control-card"), holding: () => menus.isOpen() });
```

and delete the six-line comment above `const cuePanel = subtitlePanel({` (`The buttons the browser used to lend us. …` — it describes the transport, whose own doc at `transport.js:102-105` already says it).
- [ ] Step 4: same command → PASS (10 tests); `bun test test/browser-html-player.test.ts test/player-lifetime.test.ts test/code-standards.test.ts` → PASS.
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/player-hud.js web/public/lib/playback/player.js web/test/player-features.test.ts
git commit -m "feat(player): keep the card up while a menu is open or the pointer rests on it"
```

---

### Task 6: Speed and Framing open menus; Restart

**Files:**
- Create: `web/public/lib/playback/framing-control.js`; `web/test/framing-control.test.ts`; `web/test/player-card.test.ts`
- Modify: `web/public/lib/playback/transport.js:19,111-155,191-199,212-238,249-253,271-280,299-304,316,370-371`; `web/public/lib/playback/player.js:392-393`; `web/public/index.html` (speed picker, transport); `web/public/styles/player-card.css`; `web/test/support/player-environment.ts` (append)
- Test: `web/test/player-initialization.test.ts:23-26,42-46`; `web/test/browser-html-player.test.ts` (labels)

**Interfaces:**
- Consumes: `menus` (Task 5), `framings`/`framingBox`/`framingLabel`/`framingStyle`/`nextFraming`/`DEFAULT_FRAMING` (`framing.js:30-99`), `speedLabel` (`transport.js:49`), `skipTo` via `onSeekTo` (`player.js:396`).
- Produces: `mountFramingControl({ video, button, menus, recall, remember }) → { recall(), cycle() }`; `mountTransport({ video, menus, … })` (new `menus` key); `#speed` (`aria-label="Speed"`, text = current speed), `#framing` (`aria-label="Framing"`, text = current framing), `#restart` (`aria-label="Restart"`, seeks to 0 keeping play state). Test helpers `chooseInMenu(node, opener, value)` and `markedInMenu(node, opener)` in `test/support/player-environment.ts`, used by every later menu test.

- [ ] Step 1: Write the failing test.
Append to `web/test/support/player-environment.ts`:

```ts
/** Opens the card menu behind `opener` and picks `value`, the way a viewer does. */
export function chooseInMenu(node: (id: string) => Node, opener: string, value: string) {
  node(opener).fire("click");
  const row = node("card-menu").children.find((child) => child.dataset.value === value);
  if (!row) throw new Error(`No ${value} in the menu behind #${opener}`);
  row.fire("click");
}

/** What the menu behind `opener` marks as current, read by opening it; leaves it closed. */
export function markedInMenu(node: (id: string) => Node, opener: string) {
  node(opener).fire("click");
  const value = node("card-menu").children.find((child) => child.getAttribute("aria-pressed") === "true")?.dataset.value ?? null;
  node(opener).fire("click");
  return value;
}
```

Create `web/test/framing-control.test.ts`:

```ts
/**
 * The Framing button: its label follows the picture whether the menu or `z`
 * changed it, the choice is remembered for the show, and a remembered value
 * this player does not offer falls back to Fit.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { mountFramingControl } from "../public/lib/playback/framing-control.js";
import { mountPlayerMenus } from "../public/lib/playback/player-menus.js";
import { browserEnvironment, chooseInMenu, markedInMenu } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(() => {
  env = browserEnvironment();
});
afterEach(() => env.restore());

function mount(remembered: string | null = null) {
  const kept: string[] = [];
  const framing = mountFramingControl({
    video: env.video,
    button: env.node("framing"),
    menus: mountPlayerMenus(),
    recall: () => remembered,
    remember: (_name: string, value: string) => kept.push(value),
  });
  return { framing, kept };
}

test("z steps the picture round the cycle and the label follows it", () => {
  const { framing, kept } = mount();
  framing.recall();
  expect(env.node("framing").textContent).toBe("Fit");
  framing.cycle();
  expect(env.node("framing").textContent).toBe("Fill");
  expect(env.video.style.objectFit).toBe("cover");
  expect(kept).toEqual(["fill"]);
});

test("the menu offers every framing with the current one marked, and choosing applies and remembers it", () => {
  const { framing, kept } = mount("fill");
  framing.recall();
  env.node("framing").fire("click");
  expect(env.node("card-menu").children.map((row) => row.textContent)).toEqual(["Fit", "Fill", "16:9", "4:3"]);
  env.node("framing").fire("click");
  expect(markedInMenu(env.node, "framing")).toBe("fill");
  chooseInMenu(env.node, "framing", "4:3");
  expect(env.node("framing").textContent).toBe("4:3");
  expect(env.video.style.aspectRatio).toBe(String(4 / 3));
  expect(kept).toEqual(["4:3"]);
});

test("a remembered framing this player does not offer opens as Fit", () => {
  const { framing } = mount("sideways");
  framing.recall();
  expect(env.node("framing").textContent).toBe("Fit");
  expect(markedInMenu(env.node, "framing")).toBe("fit");
});
```

Create `web/test/player-card.test.ts` (covers review focus 1 and 3 through the real player):

```ts
/**
 * The control card, driven through the real player: the speed menu, restart,
 * the skip at either edge of a title, and the card staying up while a menu
 * is open.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment, chooseInMenu, markedInMenu, settle } from "./support/player-environment";
import * as state from "../public/lib/watch-state.js";

let env: ReturnType<typeof browserEnvironment>;
let openPlayer: (set: any, options?: any) => void;
let serial = 0;
const set = (id: string, convert = false) => ({
  setId: id,
  title: id,
  kind: "movie",
  duration: 600,
  container: convert ? "mkv" : "mp4",
  vcodec: "h264",
  acodec: "aac",
});
const rows = () => env.node("card-menu").children;

beforeEach(async () => {
  env = browserEnvironment();
  await state.useProfile(null);
  const player = await import(`../public/lib/playback/player.js?card=${++serial}`);
  player.initializePlayer();
  ({ openPlayer } = player);
});

afterEach(async () => {
  env.node("player").close();
  await settle();
  env.restore();
});

test("Speed opens the six speeds with the current one marked, and a choice plays at it", () => {
  openPlayer(set("speed"));
  expect(env.node("speed").textContent).toBe("1×");
  expect(markedInMenu(env.node, "speed")).toBe("1");
  env.node("speed").fire("click");
  expect(rows().map((row) => row.textContent)).toEqual(["0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×"]);
  env.node("speed").fire("click");
  chooseInMenu(env.node, "speed", "1.5");
  env.video.fire("ratechange");
  expect(env.video.playbackRate).toBe(1.5);
  expect(env.node("speed").textContent).toBe("1.5×");
  expect(env.node("card-menu").hidden).toBe(true);
});

test("Restart goes back to 0:00 and leaves playing or paused as it was", async () => {
  openPlayer(set("restart"));
  env.video.currentTime = 300;
  await env.video.play();
  env.node("restart").fire("click");
  expect(env.video.currentTime).toBe(0);
  expect(env.video.paused).toBe(false);
  env.video.pause();
  env.video.currentTime = 120;
  env.node("restart").fire("click");
  expect(env.video.currentTime).toBe(0);
  expect(env.video.paused).toBe(true);
});

test("Restart on a converted title starts the conversion again from 0:00", async () => {
  env.respondWith(async (url) =>
    url.includes("/transcode?") ? Response.json({ playlist: "/hls/0000000000000001/index.m3u8" }) : new Response("", { status: 404 }));
  openPlayer(set("converted", true));
  await settle();
  env.node("seek-to").value = "200";
  env.node("seek-to").fire("change");
  await settle();
  env.node("restart").fire("click");
  await settle();
  const seeks = env.requests.filter(({ url }) => url.includes("/transcode?"))
    .map(({ url }) => new URL(url, "http://local").searchParams.get("seek"));
  expect(seeks).toEqual(["0", "200", "0"]);
});

test("back 15 near the start lands on 0:00; on 15 near the end stops at the end and the ended path still runs", () => {
  const opened: string[] = [];
  openPlayer(set("edges"), { next: set("after"), onOpenNext: (following: { setId: string }) => opened.push(following.setId) });
  env.video.currentTime = 5;
  env.node("skip-back").fire("click");
  expect(env.video.currentTime).toBe(0);
  env.video.currentTime = 590;
  env.node("skip-forward").fire("click");
  expect(env.video.currentTime).toBe(600);
  // Inside the last half minute: the next title is offered, not yet started.
  expect(env.node("up-next").hidden).toBe(false);
  expect(opened).toEqual([]);
  env.video.ended = true;
  env.video.fire("ended");
  env.advance(10_000);
  expect(opened).toEqual(["after"]);
});

test("the card stays up while a menu is open and rests on the usual timer once it closes", async () => {
  openPlayer(set("held"));
  await env.video.play();
  env.node("speed").fire("click");
  env.advance(10_000);
  expect(env.node("player").classes.has("resting")).toBe(false);
  chooseInMenu(env.node, "speed", "1");
  env.advance(2600);
  expect(env.node("player").classes.has("resting")).toBe(true);
});
```

In `web/test/player-initialization.test.ts` replace

```ts
    expect(env.node("speed-rate").children).toHaveLength(0);
    player.initializePlayer();
    expect(env.node("speed-rate").children).toHaveLength(6);
```

with

```ts
    expect(env.node("speed").getAttribute("aria-haspopup")).toBeNull();
    player.initializePlayer();
    expect(env.node("speed").getAttribute("aria-haspopup")).toBe("true");
```

and

```ts
  expect(env.node("speed-rate").children).toHaveLength(0);
  player.initializePlayer();
  player.initializePlayer();
  expect(env.node("speed-rate").children).toHaveLength(6);
  expect(env.node("card-dock").children).toHaveLength(1);
```

with

```ts
  expect(env.node("speed").getAttribute("aria-haspopup")).toBeNull();
  player.initializePlayer();
  player.initializePlayer();
  // A second listener would open the list and close it again in one click.
  env.node("speed").fire("click");
  expect(env.node("card-menu").hidden).toBe(false);
  expect(env.node("card-menu").children).toHaveLength(6);
  env.node("speed").fire("click");
  expect(env.node("card-dock").children).toHaveLength(1);
```

In `web/test/browser-html-player.test.ts`, before `expect(env.node("skip-back").getAttribute("aria-label")).toBe("Back 15 seconds");` add:

```ts
  expect(env.node("speed").textContent).toBe("1×");
  expect(env.node("framing").textContent).toBe("Fit");
  expect(env.node("restart").getAttribute("aria-label")).toBe("Restart");
```

- [ ] Step 2: `cd web && bun test test/framing-control.test.ts test/player-card.test.ts test/player-initialization.test.ts test/browser-html-player.test.ts` → FAIL: `Cannot find module '../public/lib/playback/framing-control.js'`; player-card "Speed opens the six speeds…" `Expected: "1×"` / `Received: ""`; HTML `Missing HTML element: #framing`.
- [ ] Step 3: Minimal implementation.
Create `web/public/lib/playback/framing-control.js`:

```js
/**
 * The Framing button: how the picture sits in the window, chosen from a menu
 * or stepped through with `z`.
 *
 * Both go through one setter, so the button's label can never disagree with
 * the picture — before the button existed, `z` changed the framing and
 * nothing on screen said what it had become. The decisions themselves are
 * `framing.js`'s; this is the element and the button.
 */

import { DEFAULT_FRAMING, framingBox, framingLabel, framingStyle, framings, nextFraming } from "./framing.js";

/**
 * @param {{video: HTMLVideoElement, button: HTMLElement,
 *   menus: ReturnType<typeof import("./player-menus.js").mountPlayerMenus>,
 *   recall?: (name: string) => string|null, remember?: (name: string, value: string) => void}} deps
 */
export function mountFramingControl({ video, button, menus, recall, remember }) {
  /** How the picture sits in the window. Per title, not per player. */
  let framing = DEFAULT_FRAMING;

  /**
   * `fit`/`fill` are left to the stylesheet's own `inset: 0; width: 100%;
   * height: 100%` — only `objectFit` changes for them. A named ratio
   * overrides all four with a pixel box computed against the stage this
   * element's parent already is (`framingBox`; see there for why `object-fit`
   * alone cannot do this): cleared back to the stylesheet's own values the
   * moment framing moves off a named ratio, so `fit`/`fill` are never left
   * sized to a stale window from before.
   */
  function apply() {
    const style = framingStyle(framing);
    video.style.objectFit = style.objectFit;
    video.style.aspectRatio = style.aspectRatio;
    const box = framingBox(framing, video.parentElement?.clientWidth ?? 0, video.parentElement?.clientHeight ?? 0);
    video.style.left = box ? `${box.left}px` : "";
    video.style.top = box ? `${box.top}px` : "";
    video.style.width = box ? `${box.width}px` : "";
    video.style.height = box ? `${box.height}px` : "";
    button.textContent = framingLabel(framing);
  }

  function choose(name) {
    framing = name;
    apply();
    remember?.("framing", name);
  }

  menus.list(button, {
    items: () => framings().map((one) => ({ value: one.name, label: one.label, current: one.name === framing })),
    pick: choose,
  });
  // A named ratio's window is computed against the stage's own pixels, which
  // a window resize (or entering/leaving fullscreen, which resizes it too)
  // changes without this hearing about it any other way; `fit`/`fill`
  // re-apply the same 100% they already were, so this is a no-op for them.
  window.addEventListener("resize", apply);

  return {
    /**
     * How this show was last framed. A framing is a property of how a show
     * was mastered, so it is remembered; one this player does not offer is
     * the default rather than a menu with nothing marked.
     */
    recall() {
      const asked = recall?.("framing");
      framing = framings().some((one) => one.name === asked) ? asked : DEFAULT_FRAMING;
      apply();
    },
    /** `z`: the next framing round the cycle. */
    cycle: () => choose(nextFraming(framing)),
  };
}
```

`transport.js` — apply this diff (framing leaves for `framing-control.js`; the speed `<select>` becomes a menu; restart is a seek to 0; the orphaned speed comment moves above `recallSpeed`):

```diff
--- a/public/lib/playback/transport.js
+++ b/public/lib/playback/transport.js
@@ -17,5 +17,5 @@
 
 import { readVolume, writeVolume } from "./volume-store.js";
-import { DEFAULT_FRAMING, framingBox, framingLabel, framingStyle, nextFraming } from "./framing.js";
+import { mountFramingControl } from "./framing-control.js";
 import { isLooping, loopBack, loopLabel, markLoop, NO_LOOP } from "./ab-loop.js";
 import { clockTime } from "../format.js";
@@ -40,5 +40,5 @@ const ICONS = {
 };
 
-/** The speeds worth offering. Anything finer is a setting, not a choice. */
+/** The speeds the Speed menu offers. Anything finer is a setting, not a choice. */
 const SPEEDS = [0.75, 1, 1.25, 1.5, 1.75, 2];
 
@@ -109,29 +109,18 @@ function skipButton(button, path, seconds, way) {
  *
  * `toggleSubtitles` is `subtitle-picker.js`'s: this bar only asks for it on
- * 'c' and a click, the same as every other control here asks a callback.
+ * 'c', the same as every other control here asks a callback. `menus` is the
+ * card's one set of lists, which Speed and Framing open.
  */
-export function mountTransport({ video, onPlay, onPause, onSeekTo, filmTime, runtime, recall, remember, toggleSubtitles }) {
+export function mountTransport({ video, menus, onPlay, onPause, onSeekTo, filmTime, runtime, recall, remember, toggleSubtitles }) {
   const playPause = document.getElementById("play-pause");
+  const restart = document.getElementById("restart");
   const back = document.getElementById("skip-back");
   const forward = document.getElementById("skip-forward");
   const mute = document.getElementById("mute");
   const volume = document.getElementById("volume");
-  const speed = document.getElementById("speed-rate");
+  const speed = document.getElementById("speed");
   const full = document.getElementById("fullscreen");
-
-  for (const rate of SPEEDS) {
-    const option = document.createElement("option");
-    option.value = String(rate);
-    option.textContent = speedLabel(rate);
-    speed.append(option);
-  }
+  const frame = mountFramingControl({ video, button: document.getElementById("framing"), menus, recall, remember });
 
-  /**
-   * The speed this show was last watched at.
-   *
-   * Applied per title rather than once at mount: a lecture course watched at
-   * 1.5x and a film watched at 1x are two different answers, and the bar is
-   * mounted once for both.
-   */
   /**
    * How this show was last framed, and where any loop from the last title went.
@@ -139,13 +128,18 @@ export function mountTransport({ video, onPlay, onPause, onSeekTo, filmTime, run
    * A loop belongs to the passage it was marked in, so it is dropped: carrying
    * one into the next episode would trap a viewer in a stretch of a film they
-   * have not started. A framing is a property of how a show was mastered, so
-   * it is remembered.
+   * have not started.
    */
   function recallFraming() {
     loop = { ...NO_LOOP };
-    framing = recall?.("framing") ?? DEFAULT_FRAMING;
-    applyFraming();
+    frame.recall();
   }
 
+  /**
+   * The speed this show was last watched at.
+   *
+   * Applied per title rather than once at mount: a lecture course watched at
+   * 1.5x and a film watched at 1x are two different answers, and the bar is
+   * mounted once for both.
+   */
   function recallSpeed() {
     const asked = Number(recall?.("speed") ?? Number.NaN);
@@ -188,14 +182,17 @@ export function mountTransport({ video, onPlay, onPause, onSeekTo, filmTime, run
   }
 
-  /** One rung up or down the speeds the picker already offers. */
-  function stepSpeed(by) {
-    const at = SPEEDS.indexOf(chosenRate);
-    const to = SPEEDS[Math.min(SPEEDS.length - 1, Math.max(0, (at === -1 ? 1 : at) + by))];
+  /** A speed the viewer chose, from the menu or a bracket key, kept for this show. */
+  function setSpeed(to) {
     chosenRate = to;
     video.playbackRate = to;
-    speed.value = String(to);
     remember?.("speed", String(to));
   }
 
+  /** One rung up or down the speeds the menu offers. */
+  function stepSpeed(by) {
+    const at = SPEEDS.indexOf(chosenRate);
+    setSpeed(SPEEDS[Math.min(SPEEDS.length - 1, Math.max(0, (at === -1 ? 1 : at) + by))]);
+  }
+
   /**
    * The picture out of the window and into a corner of the screen.
@@ -209,32 +206,4 @@ export function mountTransport({ video, onPlay, onPause, onSeekTo, filmTime, run
   }
 
-  /** `fit` to `fill` to `16:9` to `4:3` and round again. */
-  function cycleFraming() {
-    framing = nextFraming(framing);
-    applyFraming();
-    remember?.("framing", framing);
-    return framingLabel(framing);
-  }
-
-  /**
-   * `fit`/`fill` are left to the stylesheet's own `inset: 0; width: 100%;
-   * height: 100%` — only `objectFit` changes for them. A named ratio
-   * overrides all four with a pixel box computed against the stage this
-   * element's parent already is (`framingBox`; see there for why `object-fit`
-   * alone cannot do this): cleared back to the stylesheet's own values the
-   * moment framing moves off a named ratio, so `fit`/`fill` are never left
-   * sized to a stale window from before.
-   */
-  function applyFraming() {
-    const style = framingStyle(framing);
-    video.style.objectFit = style.objectFit;
-    video.style.aspectRatio = style.aspectRatio;
-    const box = framingBox(framing, video.parentElement?.clientWidth ?? 0, video.parentElement?.clientHeight ?? 0);
-    video.style.left = box ? `${box.left}px` : "";
-    video.style.top = box ? `${box.top}px` : "";
-    video.style.width = box ? `${box.width}px` : "";
-    video.style.height = box ? `${box.height}px` : "";
-  }
-
   function setVolume(to) {
     const level = Math.min(1, Math.max(0, Number(to)));
@@ -251,4 +220,6 @@ export function mountTransport({ video, onPlay, onPause, onSeekTo, filmTime, run
   back.addEventListener("click", () => onSeekTo(filmTime() - SKIP_SECONDS));
   forward.addEventListener("click", () => onSeekTo(filmTime() + SKIP_SECONDS));
+  // The start of this title, playing or paused as it was: a seek keeps both.
+  restart.addEventListener("click", () => onSeekTo(0));
 
   mute.addEventListener("click", () => {
@@ -269,12 +240,9 @@ export function mountTransport({ video, onPlay, onPause, onSeekTo, filmTime, run
    */
   let chosenRate = 1;
-  /** How the picture sits in the window. Per title, not per player. */
-  let framing = DEFAULT_FRAMING;
   /** The stretch being repeated, if any. */
   let loop = { ...NO_LOOP };
-  speed.addEventListener("change", () => {
-    chosenRate = Number(speed.value);
-    video.playbackRate = chosenRate;
-    remember?.("speed", speed.value);
+  menus.list(speed, {
+    items: () => SPEEDS.map((rate) => ({ value: String(rate), label: speedLabel(rate), current: rate === chosenRate })),
+    pick: (value) => setSpeed(Number(value)),
   });
   video.addEventListener("loadedmetadata", () => {
@@ -297,9 +265,4 @@ export function mountTransport({ video, onPlay, onPause, onSeekTo, filmTime, run
   full.addEventListener("click", toggleFullscreen);
   document.addEventListener("fullscreenchange", () => refresh());
-  // A named ratio's window is computed against the stage's own pixels, which
-  // a window resize (or entering/leaving fullscreen, which resizes it too)
-  // changes without this bar hearing about it any other way; `fit`/`fill`
-  // re-apply the same 100% they already were, so this is a no-op for them.
-  window.addEventListener("resize", () => applyFraming());
 
   /** Everything the bar shows, from what the element and the film both say. */
@@ -313,5 +276,5 @@ export function mountTransport({ video, onPlay, onPause, onSeekTo, filmTime, run
     if (document.activeElement !== volume) volume.value = String(video.muted ? 0 : video.volume);
 
-    speed.value = String(video.playbackRate);
+    speed.textContent = speedLabel(video.playbackRate);
 
     const full_ = document.fullscreenElement !== null;
@@ -368,5 +331,5 @@ export function mountTransport({ video, onPlay, onPause, onSeekTo, filmTime, run
         return togglePictureInPicture();
       case "framing":
-        return cycleFraming();
+        return frame.cycle();
       case "step":
         return step(action.by);
```

`player.js:392-393` — in `mountTransport({` replace the line `    video,` with `    video, menus,`.
`index.html` — replace the speed picker (`<span class="picker" id="speed"> … </span>`) with

```html
            <button id="speed" class="tool" aria-label="Speed">1×</button>
            <button id="framing" class="tool" aria-label="Framing">Fit</button>
```

and make the first child of `<span class="transport">`

```html
              <button id="restart" class="tsp" aria-label="Restart"><svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"></path></svg></button>
```

(Inline SVG with explicit `</path>`, like the rail icons: a static glyph needs no script, and `test/support/html-application.ts` only closes void tags.)
`player-card.css` — insert above `/* What the card's buttons open`:

```css
/* A tool says what it is set to (1×, Fit) and opens a list of the rest. */
.tool {
  flex: none;
  min-width: 44px;
  min-height: 44px;
  padding: 0 0.75rem;
  border: none;
  border-radius: var(--radius);
  background: transparent;
  color: var(--stage-ink);
  font: inherit;
  font-size: 0.875rem;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: color var(--hover), background-color var(--hover);
}
.tool:hover,
.tool[aria-expanded="true"] { color: var(--stage-accent); background: rgba(255, 255, 255, 0.08); }
.tool:disabled { opacity: 0.35; cursor: default; }
.tool:disabled:hover { color: var(--stage-ink); background: transparent; }

```

- [ ] Step 4: same command → PASS; `bun test` → 3022 pass; `bun test test/code-standards.test.ts` → PASS (`transport.js` 350, `player.js` 992).
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/framing-control.js web/public/lib/playback/transport.js web/public/lib/playback/player.js web/public/index.html web/public/styles/player-card.css web/test/support/player-environment.ts web/test/framing-control.test.ts web/test/player-card.test.ts web/test/player-initialization.test.ts web/test/browser-html-player.test.ts
git commit -m "feat(player): open speed and framing from the card, and add restart"
```

---

### Task 7: CC toggles; its ▾ opens languages, Off and Style…

**Files:**
- Modify: `web/public/lib/playback/subtitle-picker.js` (whole file); `web/public/lib/playback/subtitle-panel.js:1-11,78-87,107-114,159-164`; `web/public/lib/playback/player.js:360-368,636,978-980`; `web/public/index.html` (subs picker); `web/public/styles/playback.css` (`.cue-panel`, `#cue-settings`); `web/public/styles/player-card.css`
- Test: `web/test/subtitle-picker.test.ts` (whole file); `web/test/browser-html-player.test.ts:24,80-81,90-91,97,126,131,145,204-205,213`

**Interfaces:**
- Consumes: `chooseSubtitles`, `toggleOn`, `trackKey`, `visibility` (`subtitle-choice.js:18,94,107,134`), `menus.list`/`menus.panel` (Task 4), `subtitlePanel(...).panel`.
- Produces: `mountSubtitlePicker({ video, cc, more, menus, stylePanel, recall, remember }) → { offer, setAudio, toggle }`. `#cc` (`aria-label="Subtitles"`, `aria-pressed` = a regular track showing, `disabled` with no regular track), `#cc-menu` (`aria-label="Subtitle options"`, disabled with no track at all). Menu rows: each language by label, `off` ("Off"), `style` ("Style…", opens the style panel via `menus.panel`). `subtitlePanel` returns `{ panel, recallFor, placement }` — its own "Aa" trigger (`#cue-settings`) is gone. Turning CC off now remembers the language that was showing as `last`, so CC on brings it back; with nothing shown this title, `toggleOn` decides (today's pick).

- [ ] Step 1: Write the failing test — replace `web/test/subtitle-picker.test.ts` with (covers review focus 4):

```ts
/**
 * `mountSubtitlePicker` against a fake DOM: the CC toggle and the menu behind
 * its ▾, forced tracks showing while regular ones are off, 'c' remembering per
 * show, the profile preference, and the style panel's wider reach.
 *
 * `subtitle-choice.js`'s own rule is proved against the shared fixture in
 * `subtitle-choice.test.ts`; this file only proves the DOM around it.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mountPlayerMenus } from "../public/lib/playback/player-menus.js";
import { mountSubtitlePicker } from "../public/lib/playback/subtitle-picker.js";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, chooseInMenu, markedInMenu, settle, TrackElement } from "./support/player-environment";

const SCOPE = "show:Series";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(async () => {
  env = browserEnvironment();
  await state.useProfile(null);
});
afterEach(async () => {
  await settle();
  env.restore();
});

/** A profile is required for `setPreference` to persist anything at all. */
async function useProfile() {
  env.respondWith(async () => Response.json({}));
  await state.useProfile("viewer");
}

type Track = { track: number; lang: string; forced: boolean; sdh: boolean; label: string };

/** Attaches one `<track>` per entry, in order — as `player.js`'s `attachSubtitles` does. */
function attach(tracks: Track[]) {
  for (const existing of env.video.querySelectorAll("track")) existing.remove();
  for (const item of tracks) {
    const el = new TrackElement();
    el.srclang = item.lang;
    el.label = item.label;
    env.video.append(el);
  }
}

function mount() {
  env.node("cue-panel").hidden = true;
  return mountSubtitlePicker({
    video: env.video, cc: env.node("cc"), more: env.node("cc-menu"), menus: mountPlayerMenus(), stylePanel: env.node("cue-panel"),
    recall: (name) => state.preferenceOf(SCOPE, name),
    remember: (name, value) => state.setPreference(SCOPE, name, value),
  });
}

const showing = () => [...env.video.textTracks].filter((t) => t.mode === "showing").map((t) => t.language);
const rows = () => env.node("card-menu").children;
const pick = (value: string) => chooseInMenu(env.node, "cc-menu", value);

describe("forced tracks", () => {
  test("shows automatically for the audio language while off, and yields once a regular track is switched on", async () => {
    await useProfile();
    const tracks = [
      { track: 0, lang: "de", forced: true, sdh: false, label: "German (Forced)" },
      { track: 1, lang: "de", forced: false, sdh: false, label: "German" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);
    picker.setAudio("de");

    expect([...env.video.textTracks].map((t) => t.mode)).toEqual(["showing", "disabled"]); // forced, then regular
    expect(env.node("cc").disabled).toBe(false); // one regular track: CC can act
    expect(env.node("cc").getAttribute("aria-pressed")).toBe("false");
    expect(env.node("cc-menu").disabled).toBe(false);

    // CC switches the regular track on; the forced one yields to it rather
    // than doubling up, per the rule ("no regular showing" is its condition).
    env.node("cc").fire("click");
    expect([...env.video.textTracks].map((t) => t.mode)).toEqual(["disabled", "showing"]);
    expect(env.node("cc").getAttribute("aria-pressed")).toBe("true");
  });

  test("a forced-only title disables CC and offers only Style… behind the ▾", () => {
    const tracks = [{ track: 0, lang: "de", forced: true, sdh: false, label: "Forced (SRT)" }];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);
    picker.setAudio("de");

    expect(showing()).toEqual(["de"]);
    expect(env.node("cc").disabled).toBe(true);
    expect(env.node("cc-menu").disabled).toBe(false);
    env.node("cc-menu").fire("click");
    expect(rows().map((row) => row.textContent)).toEqual(["Style…"]);
  });

  test("never shows when the audio language is unknown", () => {
    const tracks = [{ track: 0, lang: "de", forced: true, sdh: false, label: "German (Forced)" }];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    expect(showing()).toEqual([]);
  });
});

describe("CC", () => {
  test("a title with no subtitles at all has CC and its ▾ disabled, and pressing CC does nothing", async () => {
    await useProfile();
    attach([]);
    const picker = mount();
    picker.offer([], null);
    expect(env.node("cc").disabled).toBe(true);
    expect(env.node("cc-menu").disabled).toBe(true);
    env.node("cc").fire("click");
    picker.toggle();
    expect(showing()).toEqual([]);
    expect(state.preferenceOf(SCOPE, "subtitle")).toBeNull();
  });

  test("on, with nothing remembered, picks what the player picks anywhere else: the audio language", async () => {
    await useProfile();
    const tracks = [
      { track: 0, lang: "en", forced: false, sdh: false, label: "English" },
      { track: 1, lang: "de", forced: false, sdh: false, label: "German" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);
    picker.setAudio("de");
    expect(showing()).toEqual([]);

    env.node("cc").fire("click");
    expect(showing()).toEqual(["de"]);
    expect(env.node("cc").getAttribute("aria-pressed")).toBe("true");
  });

  test("off and on again brings back the language that was showing, even one nobody picked this title", async () => {
    await useProfile();
    state.setPreference(SCOPE, "subtitle", "en");
    const tracks = [
      { track: 0, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 1, lang: "en", forced: false, sdh: false, label: "English" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);
    expect(showing()).toEqual(["en"]);

    env.node("cc").fire("click");
    expect(showing()).toEqual([]);
    expect(env.node("cc").getAttribute("aria-pressed")).toBe("false");
    env.node("cc").fire("click");
    expect(showing()).toEqual(["en"]);
  });
});

describe("'c' remembers per show", () => {
  test("turning subtitles on and off is remembered under the show's own scope", async () => {
    await useProfile();
    const tracks = [
      { track: 0, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 1, lang: "en", forced: false, sdh: true, label: "English (SDH)" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    expect(showing()).toEqual([]);
    picker.toggle();
    expect(showing()).toEqual(["de"]); // first regular track, nothing else set
    expect(state.preferenceOf(SCOPE, "subtitle")).toBe("de");
    picker.toggle();
    expect(showing()).toEqual([]);
    expect(state.preferenceOf(SCOPE, "subtitle")).toBe("off");
  });

  test("a pick from the menu is remembered as last, and 'c' restores exactly that track", async () => {
    await useProfile();
    const tracks = [
      { track: 0, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 1, lang: "en", forced: false, sdh: true, label: "English (SDH)" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    pick("en:sdh");
    expect(showing()).toEqual(["en"]);
    picker.toggle();
    expect(showing()).toEqual([]);
    picker.toggle();
    expect(showing()).toEqual(["en"]);
  });
});

describe("profile preference", () => {
  test("resolves to the SDH track when only an SDH track exists for that language", async () => {
    await useProfile();
    state.setPreference("profile", "subtitle", "en");
    const tracks = [
      { track: 0, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 1, lang: "en", forced: false, sdh: true, label: "English (SDH)" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    expect(showing()).toEqual(["en"]);
    expect(markedInMenu(env.node, "cc-menu")).toBe("en:sdh");
  });

  test("skipped for the fallback when it is itself Off", async () => {
    await useProfile();
    state.setPreference("profile", "subtitle", "off");
    const tracks = [{ track: 0, lang: "de", forced: false, sdh: false, label: "German" }];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    expect(showing()).toEqual([]);
  });
});

describe("the ▾ menu", () => {
  test("the languages by label, then Off, then Style…, with what is showing marked", () => {
    const tracks = [
      { track: 0, lang: "de", forced: true, sdh: false, label: "German (Forced)" },
      { track: 1, lang: "de", forced: false, sdh: false, label: "German" },
      { track: 2, lang: "en", forced: false, sdh: true, label: "English (SDH)" },
    ];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    env.node("cc-menu").fire("click");
    expect(rows().map((row) => [row.dataset.value, row.textContent, row.getAttribute("aria-pressed")])).toEqual([
      ["de", "German", "false"], ["en:sdh", "English (SDH)", "false"], ["off", "Off", "true"], ["style", "Style…", "false"],
    ]);
  });

  test("Style… opens the style panel in the menu's place, and the ▾ closes it again", () => {
    const tracks = [{ track: 0, lang: "de", forced: false, sdh: false, label: "German" }];
    attach(tracks);
    const picker = mount();
    picker.offer(tracks, null);

    pick("style");
    expect(env.node("cue-panel").hidden).toBe(false);
    expect(env.node("card-menu").hidden).toBe(true);
    expect(env.node("cc-menu").getAttribute("aria-expanded")).toBe("true");
    env.node("cc-menu").fire("click");
    expect(env.node("cue-panel").hidden).toBe(true);
  });
});
```

In `web/test/browser-html-player.test.ts`: change the support import to `import { chooseInMenu, markedInMenu, settle, TrackElement } from "./support/player-environment";`; replace the `pick` helper line with

```ts
const pick = (value: string) => chooseInMenu(env.node, "cc-menu", value);
const marked = () => markedInMenu(env.node, "cc-menu");
```

replace `expect(env.node("sub-track").tagName).toBe("SELECT");` and the next two lines (the comment and the `cue-settings` assertion) with

```ts
  expect(env.node("cc").getAttribute("aria-label")).toBe("Subtitles");
  expect(env.node("cc-menu").getAttribute("aria-label")).toBe("Subtitle options");
  // One card at the foot, what it opens in the same dock, and the marks in the top bar.
  expect(env.node("control-card").contains(env.node("cc"))).toBe(true);
  expect(env.node("card-dock").contains(env.node("card-menu"))).toBe(true);
```

replace `env.node("cue-settings").fire("click");` + `expect(env.node("cue-settings").getAttribute("aria-expanded")).toBe("true");` in the first test with

```ts
  pick("style");
  expect(env.node("cc-menu").getAttribute("aria-expanded")).toBe("true");
  expect(env.document.querySelector(".cue-panel")!.hidden).toBe(false);
```

change `test.each(["play-pause", "sub-track", "close"])(` to `test.each(["play-pause", "cc", "close"])(`; replace the three `expect(env.node("sub-track").value).toBe(…)` lines with `expect(marked()).toBe(…)` (same values; the comment above the first becomes `// The menu marks a language key, not a position, so it survives an` / `// episode whose tracks arrived in a different order.`); in the conversion test replace the `cue-settings` click/expect pair with `pick("style");` + `expect(env.node("cc-menu").getAttribute("aria-expanded")).toBe("true");`, and its final `expect(env.node("cue-settings").getAttribute("aria-expanded")).toBe("false");` with

```ts
  // A panel left open belongs to the title it was opened on.
  expect(env.node("cc-menu").getAttribute("aria-expanded")).toBe("false");
  expect(env.document.querySelector(".cue-panel")!.hidden).toBe(true);
```

- [ ] Step 2: `cd web && bun test test/subtitle-picker.test.ts test/browser-html-player.test.ts` → FAIL: `TypeError: undefined is not an object (evaluating 'picker.addEventListener')` (the old picker reads `subs`/`picker`), and `Missing HTML element: #cc`.
- [ ] Step 3: Minimal implementation — replace `web/public/lib/playback/subtitle-picker.js` with:

```js
/**
 * The subtitle half of the control card: the CC toggle, the menu behind its
 * ▾ (the languages, Off, then Style…), and what 'c' does. `subtitle-choice.js`
 * is the rule; this is only the DOM around it.
 *
 * `video.textTracks[i]` is always the API's `tracks[i]`: `player.js` attaches
 * one `<track>` per catalog entry, in the catalog's own order, and never
 * reorders them.
 */

import { chooseSubtitles, toggleOn, trackKey, visibility } from "./subtitle-choice.js";
import { preferenceOf } from "../watch-state.js";

/** @typedef {import("./subtitle-choice.js").SubtitleTrack} SubtitleTrack */

/** The menu row that opens the style panel. No track key can be this word. */
const STYLE = "style";

/** `set.alang`, a JSON array string the way the index stores it, or already an array. */
function parsedAlang(value) {
  if (Array.isArray(value)) return value;
  try {
    const parsed = JSON.parse(value ?? "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/**
 * @param {{video: HTMLVideoElement, cc: HTMLButtonElement, more: HTMLButtonElement,
 *   menus: ReturnType<typeof import("./player-menus.js").mountPlayerMenus>,
 *   stylePanel: HTMLElement, recall: (name: string) => string|null,
 *   remember: (name: string, value: string) => void}} deps
 */
export function mountSubtitlePicker({ video, cc, more, menus, stylePanel, recall, remember }) {
  /** @type {SubtitleTrack[]} */
  let tracks = [];
  let alang = [];
  let audioTag = null;
  /** The last regular track chosen or shown this title, cleared on every title open. */
  let last = null;
  /** The regular track showing now, or `null` for none. */
  let showing = null;

  function preferred() {
    return preferenceOf("profile", "subtitle");
  }

  function applyModes(regularKey, forcedKey) {
    for (const [index, track] of [...video.textTracks].entries()) {
      const meta = tracks[index];
      if (!meta) continue;
      const key = trackKey(meta);
      track.mode = (meta.forced ? key === forcedKey : key === regularKey) ? "showing" : "disabled";
    }
  }

  function labelFor(key) {
    return tracks.find((track) => !track.forced && trackKey(track) === key)?.label ?? key;
  }

  function recompute() {
    const chosen = chooseSubtitles({ tracks, remembered: recall("subtitle"), preferred: preferred(), audioTag, alang });
    applyModes(chosen.regular, chosen.forced);
    showing = chosen.regular;

    const vis = visibility(tracks);
    // Disabled rather than hidden: a CC that is missing reads as a player
    // without subtitles, one that is greyed out as a title without them.
    cc.disabled = !vis.ccVisible;
    cc.setAttribute("aria-pressed", String(showing !== null));
    // A forced-only title has nothing to pick, but its style still matters.
    more.disabled = !vis.styleVisible;
    return chosen;
  }

  /** The menu: each language, Off, then the way to the style panel. */
  function items() {
    const regular = visibility(tracks).pickerRows.filter((key) => key !== "off");
    const rows = regular.map((key) => ({ value: key, label: labelFor(key), current: key === showing }));
    if (regular.length > 0) rows.push({ value: "off", label: "Off", current: showing === null });
    rows.push({ value: STYLE, label: "Style…" });
    return rows;
  }

  function choose(key) {
    if (key !== "off") last = key;
    remember("subtitle", key);
    recompute();
  }

  /**
   * Rebuilt per title: `<track>` elements attached, tracks in catalog order.
   * `alangJson` is `set.alang` as the index stores it — a JSON array string,
   * the audio fallback before the real probe below narrows it.
   */
  function offer(newTracks, alangJson) {
    tracks = newTracks ?? [];
    alang = parsedAlang(alangJson);
    audioTag = null;
    last = null;
    recompute();
  }

  /** The probed audio language arrived, or the viewer switched it. */
  function setAudio(tag) {
    audioTag = tag;
    recompute();
  }

  /**
   * CC and `c`: off, and back on to the language that was showing — or, when
   * none has shown this title, to the one `toggleOn` picks, which is what the
   * player picks anywhere else. Forced follows the audio language on its own
   * line and is never what this switches.
   */
  function toggle() {
    if (cc.disabled) return;
    const chosen = chooseSubtitles({ tracks, remembered: recall("subtitle"), preferred: preferred(), audioTag, alang });
    const next = chosen.regular !== null ? "off" : toggleOn(tracks, { last, preferred: preferred(), audio: chosen.audio });
    if (next === null) return;
    last = next === "off" ? chosen.regular : next;
    remember("subtitle", next);
    recompute();
  }

  cc.addEventListener("click", toggle);
  menus.list(more, {
    items,
    pick: (value) => (value === STYLE ? menus.panel(more, stylePanel) : choose(value)),
  });

  return { offer, setAudio, toggle };
}
```

`subtitle-panel.js` — apply:

```diff
--- a/public/lib/playback/subtitle-panel.js
+++ b/public/lib/playback/subtitle-panel.js
@@ -2,7 +2,8 @@
  * The three controls that make a subtitle usable.
  *
- * A panel rather than four more things on the bar: these are set once for a
+ * A panel rather than four more things on the card: these are set once for a
  * series and then never touched again, and a control that is adjusted once a
- * month does not earn permanent space beside play and pause.
+ * month does not earn permanent space beside play and pause. It opens from
+ * the CC menu's Style…, through the card's menus, which also close it.
  *
  * Everything here is a node or an event. The decisions — what a size means,
@@ -78,10 +79,8 @@ export function subtitlePanel({ recall, remember, onPlacement }) {
   const panel = el("div", "cue-panel");
   panel.hidden = true;
-
-  const trigger = el("button", "tsp", "");
-  trigger.id = "cue-settings";
-  trigger.setAttribute("aria-label", "Subtitle appearance");
-  trigger.setAttribute("aria-expanded", "false");
-  trigger.textContent = "Aa";
+  // Focusable itself, so opening it puts a keyboard at its first control.
+  panel.tabIndex = -1;
+  panel.setAttribute("role", "group");
+  panel.setAttribute("aria-label", "Subtitle style");
 
   let held = { size: "100", backing: "shadow", offset: 0 };
@@ -107,11 +106,4 @@ export function subtitlePanel({ recall, remember, onPlacement }) {
   panel.append(size.wrap, backing.wrap, syncRow);
 
-  trigger.addEventListener("click", () => show(panel.hidden));
-
-  function show(open) {
-    panel.hidden = !open;
-    trigger.setAttribute("aria-expanded", String(open));
-  }
-
   function nudge(by) {
     // Rounded, or floating point turns four taps of 0.1 into 0.30000000000004
@@ -157,10 +149,9 @@ export function subtitlePanel({ recall, remember, onPlacement }) {
       offset: clamp(Number(recall?.("cue-offset") ?? 0), FURTHEST) || 0,
     };
-    show(false);
     draw();
     return placement();
   }
 
-  return { trigger, panel, recallFor, placement };
+  return { panel, recallFor, placement };
 }
 
```

`player.js` — delete `document.getElementById("subs").after(cuePanel.trigger);`; replace the subtitle picker mount with

```js
  /** Which subtitle tracks show, and what CC, its menu and 'c' do about it. */
  const subtitles = mountSubtitlePicker({
    video, cc: document.getElementById("cc"), more: document.getElementById("cc-menu"), menus, stylePanel: cuePanel.panel,
    recall: (name) => state.preferenceOf(scope, name),
    remember: (name, value) => state.setPreference(scope, name, value),
  });
```

in `openPlayer`, right after `scope = scopeOf(set);` add

```js
    // A list or panel left open belongs to the title it was opened on.
    menus.close();
```

and in the `close` listener replace the three lines `// A panel left open belongs to the title it was opened on.` / `cuePanel.panel.hidden = true;` / `cuePanel.trigger.setAttribute("aria-expanded", "false");` with `menus.close();`.
`index.html` — replace the subs picker (`<span class="picker" id="subs" hidden> … </span>`) with

```html
            <!-- One press for on and off; the ▾ for which language, and how
                 the text looks. -->
            <span class="cc">
              <button id="cc" class="tool" aria-label="Subtitles" aria-pressed="false" disabled>CC</button>
              <button id="cc-menu" class="tool" aria-label="Subtitle options" disabled>▾</button>
            </span>
```

`playback.css` — delete the `#cue-settings { … }` block, and trim `.cue-panel { … }` to

```css
.cue-panel {
  display: grid;
  gap: 0.55rem;
  padding: 0.9rem 1rem;
}
```

(position, fill and border now come from `player-card.css`; `left` from `player-menus.js`).
`player-card.css` — after `.tool[aria-expanded="true"] { … }` add

```css
.cc { display: inline-flex; }
.cc .tool + .tool { padding: 0 0.5rem; }
.tool[aria-pressed="true"] { color: var(--stage-accent); }
```

- [ ] Step 4: same command → PASS; `bun test` → 3026 pass; `bun run typecheck && bun run lint` clean.
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/subtitle-picker.js web/public/lib/playback/subtitle-panel.js web/public/lib/playback/player.js web/public/index.html web/public/styles/playback.css web/public/styles/player-card.css web/test/subtitle-picker.test.ts web/test/browser-html-player.test.ts
git commit -m "feat(player): toggle subtitles with CC and choose them from its menu"
```

---

### Task 8: Audio opens the track list

**Files:**
- Modify: `web/public/lib/playback/audio-chooser.js:106-130`; `web/public/lib/playback/player.js:16,90,154,346-368,392-403,630-631,769,788-806`; `web/public/index.html` (audio picker); `web/public/styles/playback.css` (`.picker` rules)
- Test: `web/test/audio-chooser.test.ts` (import, append); `web/test/player-lifetime.test.ts:2,128-132,151-155,228-229,308,368-369`; `web/test/player-manual-play.test.ts:2,71-72`

**Interfaces:**
- Consumes: `trackLabel`, `defaultTrack`, `trackIndexForLanguage`, `loadAudioTracks` (`audio-chooser.js:43,59,78,95`), `menus.list`, `applyAudioTrack` (`player.js:775`).
- Produces: `audioItems(tracks, chosen) → { value: String(index), label, current }[]` (replaces `fillChooser`); `#audio` is a `button.tool` (`aria-label="Audio"`, hidden below two tracks); `player.js` holds `audioTracks` (the probe's list, reset per title) which Task 10's stats read; `player.js` gains `const recall`/`const remember`, shared by the cue panel, the subtitle picker and the transport.

- [ ] Step 1: Write the failing test.
`web/test/audio-chooser.test.ts` — add `audioItems,` to the import list and append:

```ts
describe("the Audio menu", () => {
  test("one row per stream, valued by ordinal and named for a person, the playing one marked", () => {
    const tracks = [track({ index: 0, lang: "deu", channels: 6, codec: "ac3" }), track({ index: 3, lang: "eng" })];
    expect(audioItems(tracks, 3)).toEqual([
      { value: "0", label: "German · 5.1 · ac3", current: false },
      { value: "3", label: "English · stereo · aac", current: true },
    ]);
  });
});
```

`web/test/player-lifetime.test.ts` — import `chooseInMenu` and `markedInMenu` from `./support/player-environment`; replace every

```ts
  env.node("audio-track").value = "1";
  env.node("audio-track").fire("change");
```

with `  chooseInMenu(env.node, "audio", "1");`; replace `expect(env.node("audio-track").value).toBe("1");` + the following `env.node("audio-track").fire("change");` with `expect(markedInMenu(env.node, "audio")).toBe("1");` + `chooseInMenu(env.node, "audio", "1");`; replace the lone retry `env.node("audio-track").fire("change");` with `chooseInMenu(env.node, "audio", "1");`; replace `expect(env.node("audio-track").value).toBe("0");` with `expect(markedInMenu(env.node, "audio")).toBe("0");`. `grep -c audio-track web/test/player-lifetime.test.ts` → 0.
`web/test/player-manual-play.test.ts` — import `chooseInMenu`; replace the `audio-track` value/change pair with `      chooseInMenu(env.node, "audio", "1");`.
- [ ] Step 2: `cd web && bun test test/audio-chooser.test.ts test/player-lifetime.test.ts test/player-manual-play.test.ts` → FAIL: `SyntaxError: Export named 'audioItems' not found`, and `Error: No 1 in the menu behind #audio`.
- [ ] Step 3: Minimal implementation.
`audio-chooser.js:106-130` — replace `fillChooser` and its doc with:

```js
/**
 * The Audio menu's rows, the playing track marked.
 *
 * The value is the ordinal, which is how this file's stream is asked for; what
 * is remembered when one is picked is its language — see
 * `trackIndexForLanguage`. One track is not a menu, it is a label for
 * something nobody can change, so the caller hides the button below two.
 * @param {AudioTrack[]} tracks
 * @param {number} chosen
 * @returns {{value: string, label: string, current: boolean}[]}
 */
export function audioItems(tracks, chosen) {
  return tracks.map((track) => ({ value: String(track.index), label: trackLabel(track), current: track.index === chosen }));
}
```
`player.js`:
- import line 16 → `import { audioItems, defaultTrack, loadAudioTracks, trackIndexForLanguage } from "./audio-chooser.js";`
- delete `const audioTrackPicker = document.getElementById("audio-track");`
- after `let audioTrack = 0;` add `let audioTracks = [];`
- in `openPlayer`, after `audioTrack = 0;` add `audioTracks = [];`
- in `offerAudioTracks` replace `audio.hidden = !fillChooser(audioTrackPicker, found, audioTrack);` with `audioTracks = found;` + `audio.hidden = found.length < 2;`
- replace the picker `change` listener (and keep its doc, first line now `* The viewer picked a language — remembered as one, never as the ordinal.`) with:

```js
  menus.list(audio, {
    items: () => audioItems(audioTracks, audioTrack),
    pick: (value) => {
      const chosen = Number(value);
      if (!playing || !Number.isInteger(chosen) || chosen < 0) return;
      audioTrack = chosen;
      const picked = audioTracks.find((track) => track.index === chosen)?.lang;
      if (picked) state.setPreference(scope, "audio", picked);
      subtitles.setAudio(picked ?? null);
      applyAudioTrack();
    },
  });
```

- keep the file inside its ceiling by sharing the preference lambdas: replace `  const cuePanel = subtitlePanel({` + its two `recall:`/`remember:` lines with

```js
  // What "this show" means is the player's question; every control only asks.
  const recall = (name) => state.preferenceOf(scope, name);
  const remember = (name, value) => state.setPreference(scope, name, value);
  const cuePanel = subtitlePanel({
    recall, remember,
```

the subtitle picker's two `recall:`/`remember:` lines with `    recall, remember,`, and in `mountTransport({` the comment `// What "this show" means is the player's question — the bar only asks.` plus its two `recall:`/`remember:` lines with `    recall, remember,`.
`index.html` — delete the audio picker span and put `<button id="audio" class="tool" aria-label="Audio" hidden>Audio</button>` after `#speed` (row order: CC ▾, Speed, Audio, Framing).
`playback.css` — the last `<select>` is gone: delete `.picker { … }`, `.picker label { … }`, `.picker select { … }`, `.picker select:hover { … }` (211-235); change `.hud label,` / `.hud .picker { pointer-events: auto; }` to `.hud label { pointer-events: auto; }`; change `dialog.resting .hud label,` / `dialog.resting .hud .picker { pointer-events: none; }` to `dialog.resting .hud label { pointer-events: none; }`; drop `.hud .picker label,` from the text-shadow selector list; delete `.picker select { … }` and `.picker label { display: none; }` from the narrow block.
- [ ] Step 4: same command → PASS; `bun test` → 3027 pass; `bun test test/code-standards.test.ts` → PASS (`player.js` 993).
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/audio-chooser.js web/public/lib/playback/player.js web/public/index.html web/public/styles/playback.css web/test/audio-chooser.test.ts web/test/player-lifetime.test.ts web/test/player-manual-play.test.ts
git commit -m "feat(player): choose the audio track from the card's menu"
```

---

### Task 9: Previous and Next

**Files:**
- Modify: `web/public/lib/playback/player-next-title.js:1,17-24,44-66,99-104,133,137-138,144`; `web/public/lib/playback/player.js:44` (typedef); `web/public/index.html` (transport); `web/public/app.js:311-314`
- Test: `web/test/player-card.test.ts` (append); `web/test/browser-application.test.ts` (two tests before `test.each(["close", "pagehide"])("%s invalidates Play next…`)

**Interfaces:**
- Consumes: `playsNext → { next, previous, inRun }` (Task 2), `onOpenNext` (`app.js:315`).
- Produces: `PlayerOptions.previous`, `PlayerOptions.inRun` (defaults to "there is a next or a previous"); `mountPlayerNextTitle(...)` returns `openInRun(set, how = "asap")` alongside `open/update/preload/releaseWarm/clear`; `#previous` (`aria-label="Previous"`) and `#play-next` (now `button.tsp`, `aria-label="Next"`, tooltip = the title it opens): hidden with no run, disabled at the matching end. Phase 02's sidebar opens rows through `openInRun`.

- [ ] Step 1: Write the failing test. Append to `web/test/player-card.test.ts`:

```ts
test("a film has no run, so Previous and Next are hidden rather than disabled", () => {
  openPlayer(set("film"));
  expect(env.node("previous").hidden).toBe(true);
  expect(env.node("play-next").hidden).toBe(true);
});

test("in a run, Previous and Next open their neighbours the way the next title opens, and each end is disabled", () => {
  const opened: Array<[string, unknown]> = [];
  const onOpenNext = (following: { setId: string }, how: unknown) => opened.push([following.setId, how]);
  openPlayer(set("first"), { next: set("second"), previous: null, inRun: true, onOpenNext });
  expect(env.node("previous").hidden).toBe(false);
  expect(env.node("previous").disabled).toBe(true);
  expect(env.node("play-next").disabled).toBe(false);
  expect(env.node("play-next").title).toBe("second");
  openPlayer(set("second"), { next: set("third"), previous: set("first"), inRun: true, onOpenNext });
  env.node("previous").fire("click");
  env.node("play-next").fire("click");
  expect(opened).toEqual([["first", { autoplay: "asap" }], ["third", { autoplay: "asap" }]]);
  openPlayer(set("third"), { next: null, previous: set("second"), inRun: true, onOpenNext });
  expect(env.node("play-next").hidden).toBe(false);
  expect(env.node("play-next").disabled).toBe(true);
  expect(env.node("previous").disabled).toBe(false);
});
```

Insert into `web/test/browser-application.test.ts` before `test.each(["close", "pagehide"])("%s invalidates Play next while its state refresh is pending"`:

```ts
test("a list's Previous and Next walk it through the app's own play path", async () => {
  catalog = JSON.stringify([film("First"), film("Second")]);
  snapshot = { collections: [{ id: "list", name: "My list", items: ["First", "Second"] }] };
  await start();
  await env.navigate("#/collections/list");
  descendants(env.node("main")).find((node) => node.textContent === "Play all")!.fire("click");
  await settle();
  expect(env.video.src).toBe("/api/sets/First/stream");
  expect(env.node("previous").hidden).toBe(false);
  expect(env.node("previous").disabled).toBe(true);
  env.node("play-next").fire("click");
  await settle();
  expect(env.video.src).toBe("/api/sets/Second/stream");
  expect(env.node("previous").disabled).toBe(false);
  expect(env.node("play-next").disabled).toBe(true);
  env.node("previous").fire("click");
  await settle();
  expect(env.video.src).toBe("/api/sets/First/stream");
});

test("a film opened from its page has no run to step through", async () => {
  await start();
  await env.navigate("#/film/First");
  descendants(env.node("main")).find((node) => node.className.split(" ").includes("film-play"))!.fire("click");
  await settle();
  expect(env.video.src).toBe("/api/sets/First/stream");
  expect(env.node("previous").hidden).toBe(true);
  expect(env.node("play-next").hidden).toBe(true);
});
```

- [ ] Step 2: `cd web && bun test test/player-card.test.ts test/browser-application.test.ts` → FAIL: "a film has no run…" `Expected: true` / `Received: false` (`#previous` is never touched), and the list test `Expected: false` / `Received: true` on `previous.disabled` for the second title.
- [ ] Step 3: Minimal implementation. `player-next-title.js` — apply:

```diff
--- a/public/lib/playback/player-next-title.js
+++ b/public/lib/playback/player-next-title.js
@@ -1,3 +1,7 @@
-/** The next-title offer owns its countdown, cancellation and speculative work. */
+/**
+ * The run around the open title: the next-title offer with its countdown,
+ * cancellation and speculative work, and the card's ⏮ and ⏭ — every way of
+ * moving along a run opens through `openInRun`.
+ */
 import { episodeLabel } from "../format.js";
 import { playbackFor } from "../link.js";
@@ -16,4 +20,5 @@ const PRELOAD_BYTES = 8 * 1024 * 1024;
 export function mountPlayerNextTitle({ showControls, openTitle }) {
   const button = document.getElementById("play-next");
+  const back = document.getElementById("previous");
   const panel = document.getElementById("up-next");
   const title = document.getElementById("up-next-title");
@@ -22,4 +27,6 @@ export function mountPlayerNextTitle({ showControls, openTitle }) {
   let playing = null;
   let next = null;
+  let previous = null;
+  let inRun = false;
   let onOpenNext = null;
   let countdown = null;
@@ -42,7 +49,14 @@ export function mountPlayerNextTitle({ showControls, openTitle }) {
   }
 
+  /**
+   * A title with no run hides both steps; in a run, the end with nothing past
+   * it is disabled instead, so the transport does not shift under a finger.
+   */
   function offer() {
-    button.hidden = next === null;
-    button.title = next === null ? "" : titleLine(next);
+    for (const [control, target] of [[back, previous], [button, next]]) {
+      control.hidden = !inRun;
+      control.disabled = target === null;
+      control.title = target === null ? "" : titleLine(target);
+    }
   }
 
@@ -54,4 +68,7 @@ export function mountPlayerNextTitle({ showControls, openTitle }) {
     playing = set;
     next = options.next ?? null;
+    previous = options.previous ?? null;
+    // Saying what is next already says there is a run.
+    inRun = options.inRun ?? (next !== null || previous !== null);
     onOpenNext = options.onOpenNext ?? null;
     preloaded = null;
@@ -62,5 +79,6 @@ export function mountPlayerNextTitle({ showControls, openTitle }) {
     hide();
     dropWarm();
-    playing = next = onOpenNext = preloaded = null;
+    playing = next = previous = onOpenNext = preloaded = null;
+    inRun = false;
     offer();
   }
@@ -97,9 +115,12 @@ export function mountPlayerNextTitle({ showControls, openTitle }) {
   }
 
-  /** @param {"buffered"|"asap"} how */
-  function playNext(how) {
-    const following = next;
+  /**
+   * Opens `set` the way the run's next title opens, for ⏮, ⏭ and the
+   * countdown alike, so a step back is never a different kind of open.
+   * @param {CatalogSet|null} set @param {"buffered"|"asap"} [how]
+   */
+  function openInRun(set, how = "asap") {
     hide();
-    if (following) (onOpenNext ?? openTitle)(following, { autoplay: how });
+    if (set) (onOpenNext ?? openTitle)(set, { autoplay: how });
   }
 
@@ -131,10 +152,11 @@ export function mountPlayerNextTitle({ showControls, openTitle }) {
       left -= 1;
       timing.textContent = `starting in ${left}…`;
-      if (left <= 0) playNext("buffered");
+      if (left <= 0) openInRun(next, "buffered");
     }, 1000);
   }
 
-  document.getElementById("up-next-play").addEventListener("click", () => playNext("asap"));
-  button.addEventListener("click", () => playNext("asap"));
+  document.getElementById("up-next-play").addEventListener("click", () => openInRun(next));
+  button.addEventListener("click", () => openInRun(next));
+  back.addEventListener("click", () => openInRun(previous));
   document.getElementById("up-next-cancel").addEventListener("click", () => {
     if (playing) cancelled.add(playing.setId);
@@ -142,4 +164,4 @@ export function mountPlayerNextTitle({ showControls, openTitle }) {
     dropWarm();
   });
-  return { open, update, preload, releaseWarm, clear };
+  return { open, update, preload, releaseWarm, clear, openInRun };
 }
```

`player.js` — after ` * @property {CatalogSet|null} [next]` add

```js
 * @property {CatalogSet|null} [previous]
 * @property {boolean} [inRun] Whether the title is one of a run; see `playsNext`.
```

`index.html` — in `<span class="transport">`, after `#restart`:

```html
              <button id="previous" class="tsp" aria-label="Previous" hidden><svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"></path></svg></button>
```

and replace `<button id="play-next" class="ghost" hidden>Play next</button>` with

```html
              <button id="play-next" class="tsp" aria-label="Next" hidden><svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"></path></svg></button>
```

`app.js:311-314` (same line count):

```js
  const { next, previous, inRun, preload } = playsNext(library, set, queue);
  requestPreload(preload);
  openPlayer(set, {
    next, previous, inRun,
```

- [ ] Step 4: same command → PASS; `bun test` → 3031 pass; `bun test test/code-standards.test.ts` → PASS (`player.js` 995, `app.js` 694).
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/player-next-title.js web/public/lib/playback/player.js web/public/index.html web/public/app.js web/test/player-card.test.ts web/test/browser-application.test.ts
git commit -m "feat(player): step to the previous and next title of a run"
```

---

### Task 10: Stats overlay behind ⓘ

**Files:**
- Create: `web/public/lib/playback/player-stats.js`; `web/test/player-stats.test.ts`
- Modify: `web/public/lib/playback/player.js:15,17,24,82,91,604-607,648,988`; `web/public/index.html` (`#tech`, `#preload`, `.card-end`, overlay); `web/public/styles/playback.css:100-108,189-202,727`; `web/public/styles/player-card.css`; `web/public/lib/format.js:60-70,82-109`; `web/public/lib/format.d.ts:26-37`
- Test: `web/test/browser-html-player.test.ts` (after the mute assertion); `web/test/format.test.ts:2-14,177-212`

**Interfaces:**
- Consumes: `playbackFields` (`playback-report.js:32`), `preloadReadout` (`preload-readout.js:84`), `bitrateLabel`/`clockTime`/`hdrLabel` (`format.js`), `languageLabel` (`language-label.js:27`), `channelLabel` (`audio-chooser.js:23`), `audioTracks`/`audioTrack` (Task 8).
- Produces: `statsRows({ fields, set, audio, size }) → { name, value }[]` with names `video`, `audio`, `buffer`, `cache`, `dropped` (Android's `PlaybackStats.kt` labels; `reads` is Telegram-side and never here); `mountPlayerStats({ video }) → { draw(fields, set, audio) }`; `#stats-toggle` (`aria-label="Stats"`, `aria-pressed`) in `.card-end`; `#stats-panel` (`dl.stats-overlay`). The `#tech` line and `#preload` readout are gone; `technicalLine` is deleted.

- [ ] Step 1: Write the failing test. Create `web/test/player-stats.test.ts`:

```ts
/**
 * The stats overlay: the rows the browser can fill, under Android's names,
 * with a row nothing knows left out rather than guessed at.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mountPlayerStats, statsRows } from "../public/lib/playback/player-stats.js";
import { catalogSet } from "./support/catalog-set";
import { browserEnvironment } from "./support/player-environment";

const fields = (over: Record<string, unknown> = {}) => ({
  readyState: 4, ahead: 52, starved: false, awaitingStart: false, fillRate: null,
  health: "ok", dropped: 0, frames: 1200, paused: false, held: false, ...over,
});
const film = catalogSet({ quality: "1080p", hdr: "HDR10", vcodec: "hevc", acodec: "eac3", total: 15_247_000_000, duration: 9_840 });
const noSize = { width: 0, height: 0 };
const named = (rows: { name: string; value: string }[]) => Object.fromEntries(rows.map((row) => [row.name, row.value]));

describe("the rows", () => {
  test("video, audio, buffer and cache, in Android's order and words, with no dropped row while none are", () => {
    const rows = statsRows({ fields: fields(), set: film, audio: null, size: { width: 1920, height: 800 } });
    expect(rows.map((row) => row.name)).toEqual(["video", "audio", "buffer", "cache"]);
    expect(named(rows)).toEqual({
      video: "1920×800 hevc HDR10 12 Mbps",
      audio: "eac3",
      buffer: "0:52 ahead",
      cache: "ready · 0:52 ahead",
    });
  });

  test("the probed track says codec, channels and language; the file's label stands in for an undecoded picture", () => {
    const audio = { index: 1, lang: "deu", codec: "ac3", channels: 6, title: null, isDefault: true };
    const rows = named(statsRows({ fields: fields(), set: film, audio, size: noSize }));
    expect(rows.audio).toBe("ac3 5.1 German");
    expect(rows.video).toBe("1080p hevc HDR10 12 Mbps");
  });

  test("a title held in full says cached, and dropped frames appear once there are some", () => {
    const rows = named(statsRows({ fields: fields({ held: true, dropped: 7 }), set: film, audio: null, size: noSize }));
    expect(rows.buffer).toBe("cached");
    expect(rows.dropped).toBe("7 frames");
    // The cache row no longer repeats the count the dropped row now owns.
    expect(rows.cache).not.toContain("dropped");
  });

  test("a row with nothing known is absent, never blank", () => {
    const bare = catalogSet({ quality: null, hdr: null, vcodec: null, acodec: null, total: 0, duration: null });
    const rows = statsRows({ fields: fields(), set: bare, audio: null, size: noSize });
    expect(rows.map((row) => row.name)).toEqual(["buffer", "cache"]);
    expect(statsRows({ fields: fields(), set: null, audio: null, size: noSize }).map((row) => row.name)).toEqual(["buffer", "cache"]);
  });
});

describe("the overlay", () => {
  let env: ReturnType<typeof browserEnvironment>;
  beforeEach(() => {
    env = browserEnvironment();
  });
  afterEach(() => env.restore());

  test("ⓘ toggles it, and nothing is drawn into it while it is shut", () => {
    const panel = env.node("stats-panel");
    panel.hidden = true;
    const stats = mountPlayerStats({ video: env.video });
    stats.draw(fields(), film, null);
    expect(panel.children).toHaveLength(0);
    env.node("stats-toggle").fire("click");
    expect(panel.hidden).toBe(false);
    expect(env.node("stats-toggle").getAttribute("aria-pressed")).toBe("true");
    expect(panel.children.map((node) => node.textContent)).toEqual([
      "video", "1080p hevc HDR10 12 Mbps", "audio", "eac3", "buffer", "0:52 ahead", "cache", "ready · 0:52 ahead",
    ]);
    env.node("stats-toggle").fire("click");
    expect(panel.hidden).toBe(true);
    expect(env.node("stats-toggle").getAttribute("aria-pressed")).toBe("false");
  });
});
```

In `web/test/browser-html-player.test.ts`, after `expect(env.video.muted).toBe(true);` add:

```ts
  // The file facts and the preload readout moved into the stats overlay.
  expect(env.document.getElementById("tech")).toBeNull();
  expect(env.document.getElementById("preload")).toBeNull();
  expect(env.node("stats-toggle").getAttribute("aria-label")).toBe("Stats");
  env.node("stats-toggle").fire("click");
  expect(env.node("stats-toggle").getAttribute("aria-pressed")).toBe("true");
  env.video.fire("progress");
  expect(env.node("stats-panel").children.filter((node) => node.tagName === "DT").map((node) => node.textContent))
    .toEqual(["video", "audio", "buffer", "cache"]);
```

In `web/test/format.test.ts` drop `technicalLine,` from the import, add `hdrLabel,` after `episodeLabel,`, and replace the whole `describe("the technical line", () => { … });` with:

```ts
describe("the dynamic range", () => {
  test("names what is worth naming and leaves SDR out: it is the absence of a fact", () => {
    expect(hdrLabel({ hdr: "HDR10" })).toBe("HDR10");
    expect(hdrLabel({ hdr: "SDR" })).toBeNull();
    expect(hdrLabel({ hdr: null })).toBeNull();
  });

  test("codecLine stays the compact views' one line", () => {
    expect(codecLine({ container: "mkv", vcodec: "hevc", acodec: "eac3" })).toBe("mkv · hevc · eac3");
  });
});
```

- [ ] Step 2: `cd web && bun test test/player-stats.test.ts test/browser-html-player.test.ts test/format.test.ts` → FAIL: `Cannot find module '../public/lib/playback/player-stats.js'`, and the HTML test `Expected: null` / `Received: …` for `getElementById("tech")` (`format.test.ts` already passes; it only stops testing a function that is about to go).
- [ ] Step 3: Minimal implementation. Create `web/public/lib/playback/player-stats.js`:

```js
/**
 * The stats overlay behind ⓘ: what the browser can say about the picture,
 * the sound and the buffer, under the row names the Android player uses.
 *
 * A row nothing here can know is left out rather than shown as a guess — a
 * dash or a zero would read as a measurement. Android's "reads" row counts
 * Telegram fetches, which a browser never sees, so it is never here.
 */

import { el } from "../dom.js";
import { bitrateLabel, clockTime, hdrLabel } from "../format.js";
import { languageLabel } from "../language-label.js";
import { channelLabel } from "./audio-chooser.js";
import { preloadReadout } from "./preload-readout.js";

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 * @typedef {import("../../../src/catalog/audio-tracks").AudioTrack} AudioTrack
 * @param {{fields: ReturnType<typeof import("./playback-report.js").playbackFields>,
 *   set: CatalogSet|null, audio: AudioTrack|null, size: {width: number, height: number}}} at
 * @returns {{name: string, value: string}[]}
 */
export function statsRows({ fields, set, audio, size }) {
  // The element's own size is the picture actually decoded; the index's
  // quality is only what the file was labelled, so it is the fallback.
  const resolution = size.width > 0 && size.height > 0 ? `${size.width}×${size.height}` : set?.quality;
  const sound = audio
    ? [audio.codec, channelLabel(audio.channels), languageLabel(audio.lang, null)]
    : [set?.acodec];
  const dropped = Number(fields.dropped);
  const rows = [
    ["video", [resolution, set?.vcodec, set ? hdrLabel(set) : null, set ? bitrateLabel(set) : null]],
    ["audio", sound],
    ["buffer", [fields.held ? "cached" : `${clockTime(fields.ahead)} ahead`]],
    // The readout that used to sit under the bar. Its dropped count has its own row here.
    ["cache", [preloadReadout({ ...fields, dropped: 0 })]],
    ["dropped", [dropped > 0 ? `${dropped} frames` : null]],
  ];
  return rows
    .map(([name, parts]) => ({ name, value: parts.filter(Boolean).join(" ") }))
    .filter((row) => row.value !== "");
}

/**
 * The ⓘ toggle and the overlay. `draw` is called as often as the player
 * refreshes anything; it builds nothing while the overlay is shut.
 * @param {{video: HTMLVideoElement}} deps
 */
export function mountPlayerStats({ video }) {
  const button = document.getElementById("stats-toggle");
  const panel = document.getElementById("stats-panel");
  let last = null;

  function draw(fields, set, audio) {
    last = { fields, set, audio };
    if (panel.hidden) return;
    const size = { width: video.videoWidth || 0, height: video.videoHeight || 0 };
    panel.replaceChildren(...statsRows({ ...last, size }).flatMap(({ name, value }) => [el("dt", null, name), el("dd", null, value)]));
  }

  button.addEventListener("click", () => {
    panel.hidden = !panel.hidden;
    button.setAttribute("aria-pressed", String(!panel.hidden));
    if (last) draw(last.fields, last.set, last.audio);
  });

  return { draw };
}
```

`player.js`:
- `import { clockTime, endsAt, episodeLabel } from "../format.js";` (drop `technicalLine`); `import { bufferedAhead } from "./preload-readout.js";` (drop `preloadReadout`); after the `player-menus.js` import add `import { mountPlayerStats } from "./player-stats.js";`
- delete `const tech = document.getElementById("tech");`; replace `const preload = document.getElementById("preload");` with `const stats = mountPlayerStats({ video });`
- `refreshPreload` body becomes

```js
    const audioNow = audioTracks.find((track) => track.index === audioTrack) ?? null;
    stats.draw(playbackFields(video, { starved, waitingToStart, held, watch }), playing, audioNow);
```

- delete `tech.textContent = technicalLine(set);` in `openPlayer` and `preload.textContent = "";` in the `close` listener.
`index.html` — delete `#tech` with its three-line comment, and `#preload` with its two-line comment; after `</span>` closing `.transport` add

```html
            <span class="card-end">
              <button id="stats-toggle" class="tsp" aria-label="Stats" aria-pressed="false"><svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M11 7h2v2h-2zm0 4h2v6h-2zm1-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"></path></svg></button>
            </span>
```

and after `#card-dock`'s closing `</div>`:

```html

      <!-- What the browser can say about the picture, the sound and the
           buffer, under the top bar. Not part of the card: it stays while
           the card rests, for as long as ⓘ leaves it on. -->
      <dl class="stats-overlay" id="stats-panel" hidden></dl>
```

`playback.css` — delete `.hud .tech { … }` (100-108) and `.hud .tech { display: none; }` in the narrow block; `.ends,` / `.preload {` becomes `.ends {`; the text-shadow rule becomes `.hud .seek label,` / `.ends { text-shadow: 0 1px 3px rgba(0, 0, 0, 0.9); }`; delete `.preload { color: var(--stage-ink-2); }`.
`player-card.css` — insert above `@media (max-width: 767px) {`:

```css
/* The stats overlay: under the top bar on the left, in the card's fill. */
.stats-overlay {
  position: absolute;
  top: 4.5rem;
  left: 1.5rem;
  z-index: 2;
  display: grid;
  grid-template-columns: auto auto;
  gap: 0.2rem 1rem;
  margin: 0;
  padding: 0.75rem 1rem;
  background: color-mix(in srgb, var(--stage) 45%, transparent);
  -webkit-backdrop-filter: blur(24px) saturate(1.2); /* deslop-ignore 19: player card, see the note above */
  backdrop-filter: blur(24px) saturate(1.2); /* deslop-ignore 19: player card, see the note above */
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-card);
  font-size: 0.78rem;
  font-variant-numeric: tabular-nums;
}
.stats-overlay dt { color: var(--stage-ink-2); }
.stats-overlay dd { margin: 0; color: var(--stage-ink); }
#stats-toggle[aria-pressed="true"] { color: var(--stage-accent); }

```

`format.js` — delete the orphaned doc `/** Everything the index knows about a file, … */` (60-70), `technicalLine` (82-98) and `partsLabel` with its doc (100-109). `format.d.ts` — delete the `technicalLine` declaration and its doc line (26-37).
- [ ] Step 4: same command → PASS; `bun test` → 3033 pass; `bun run typecheck && bun run lint` clean; `bun test test/code-standards.test.ts` → PASS (`player.js` 994).
- [ ] Step 5: Commit.

```bash
git add web/public/lib/playback/player-stats.js web/public/lib/playback/player.js web/public/index.html web/public/styles/playback.css web/public/styles/player-card.css web/public/lib/format.js web/public/lib/format.d.ts web/test/player-stats.test.ts web/test/browser-html-player.test.ts web/test/format.test.ts
git commit -m "feat(player): show playback stats in an overlay behind the info button"
```

---

### Task 11: Close-out — ceilings, docs, full check, preview walk

**Files:**
- Modify: `web/test/code-standards.test.ts:54-56,66,69,72`; `web/DESIGN.md` (Player section)
- Test: the whole web suite

**Interfaces:**
- Consumes: everything above.
- Produces: ceilings `player.js` 994, `transport.js` 350, `playback.css` 625 — phase 02 must not grow `player.js`.

- [ ] Step 1: Write the test change. In `web/test/code-standards.test.ts` set `"public/lib/playback/player.js": 994,`, `"public/lib/playback/transport.js": 350,`, `"public/styles/playback.css": 625,` and end the header comment with

```ts
 * Lowered 2026-10-05 for `player.js`, `transport.js` and `playback.css`, once the
 * player's controls became one card with its menus, framing and stats in modules
 * and styles of their own.
```

In `web/DESIGN.md`, Player section: change the Hiding line's end to `…and stay up while paused, while a menu is open, or while the pointer is on the card.` and add after the Rows line:

```markdown
- **Menus**: Speed, Audio, Framing and the CC ▾ open one short list at a time just above their button, inside the card's width, in the card's fill. Choosing closes it; Esc closes only it.
- **Stats**: ⓘ toggles an overlay under the top bar on the left, in the card's fill, under the Android player's row names (video, audio, buffer, cache, dropped). A row the browser cannot know is left out.
```

- [ ] Step 2: `cd web && bun test test/code-standards.test.ts` → PASS. (It is a ratchet: a FAIL here names a file that grew past its new ceiling — shrink it, do not raise the number.)
- [ ] Step 3: Full check: `cd web && bun run lint && bun run typecheck && bun test` → 3033 pass, 0 fail. Then the stub preview — never the live player on :8770:
  1. `cd web && bun run preview` → `http://127.0.0.1:8795` (copies of the index and `state.db`, no Telegram; restart it after any HTML/CSS edit, and open `/?r=<n>#/…` so the browser drops the old document).
  2. Pick a profile that opens without a PIN (on this machine "TV kids"). If one asks to set a PIN, press Cancel; never answer "Who runs this household?".
  3. Series → any show → an episode in the middle of a season. At 1440×900: the card is three rows (seek; CC ▾ 1× Fit … volume fullscreen; ↺ ⏮ 15 ▶ 15 ⏭ … ⓘ), 880px wide, 24px up, blurred; the top bar shows title, My List, Add to…, ✕.
  4. Speed, then Framing: each list opens above its button inside the card, the current value in the accent; pressing another tool swaps lists; Esc closes the list and leaves the player open.
  5. ⓘ: the overlay sits top left under the top bar with video/audio/buffer/cache.
  6. In devtools run `document.getElementById("up-next").hidden = false` and check the Up-next card clears the card; if not, raise `.up-next { bottom }` in `playback.css` (12.5rem) or its narrow value (15rem) by the overlap.
  7. Viewport 390×844: the card spans the width minus 12px each side, ⓘ wraps under the transport, nothing scrolls sideways (`document.getElementById("player").scrollLeft === 0`).
  Screenshots go to `plans/261005-0157-player-control-card/reports/`.
- [ ] Step 4: All of the above hold.
- [ ] Step 5: Commit.

```bash
git add web/test/code-standards.test.ts web/DESIGN.md
git commit -m "chore(player): lower the line ceilings the control card freed"
```

## Security considerations

No new input crosses a trust boundary. Menu and stats text is built with `el()`/`textContent` (never `innerHTML`); track labels and titles come from the catalog and the probe exactly as before.

## Next steps

Phase 02 (episode sidebar) consumes `collectionOf`, `openInRun`, `mountPlayerMenus`'s Esc ordering, the HUD `holding` hook, `.card-end` and `player-card.css`'s `@media` block. Phases 03–05 port the same decisions to Android; nothing here blocks them.

## Unresolved questions

None blocking. Rulings made against the spec are listed in plan.md's review log by the lead if accepted: lists hide ☰ (phase 02), CC "last language" is per title-open (no new synced preference), the `#tech` line moves into stats and `technicalLine` is deleted.
