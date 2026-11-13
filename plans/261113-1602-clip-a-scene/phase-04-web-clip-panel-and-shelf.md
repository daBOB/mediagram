# Phase 4: Web clip panel and Marked scenes shelf

Spec § Marking, § Finding marks on the web, § The clip panel, § Clip playback is not watching.
UI checked with the stub harness (`cd web && bun run preview`), never the real player.

### Task 6: Mark a scene in the web player

**Files:**
- Modify: `web/public/lib/watch-state.js` (hold `scenes` from the snapshot; `markScene`, `removeScene`, `scenes`)
- Modify: `web/public/index.html` (button `#mark-scene` beside `#watchlist`), `web/public/lib/playback/player-library-marks.js` (click handler + notice), `web/public/lib/playback/player-keys.js` (key `n` → `{ do: "markScene" }`)
- Test: `web/test/watch-state-scenes.test.ts`

**Interfaces:**
- Consumes: Task 1 HTTP routes.
- Produces: `export function scenes(): { id: string; setId: string; at: number; markedAt: number }[]` (newest first); `export async function markScene(setId, at): Promise<string | null>` (adds locally on success, calls `changed()`); `export function removeScene(id): void` (optimistic, best-effort like `setWatchlisted`).
- Copy: button `"Mark scene"`; notice `"Scene marked at 1:02:15"` using the player's `clockTime`.

- [ ] **Step 1: Failing tests:** `markScene posts and holds the new mark`; `removeScene drops it locally and sends DELETE`; `a refreshed snapshot replaces the held scenes` (copy the fetch-stub pattern from `watch-state-async.test.ts`).
- [ ] **Step 2: Run** `cd web && bun test test/watch-state-scenes.test.ts` — FAIL.
- [ ] **Step 3: Implement**; the key `n` and the button both mark at `filmTime()`.
- [ ] **Step 4: Run** `cd web && bun test && bun run lint` — PASS.
- [ ] **Step 5: Commit** `feat(web): mark a scene from the player`.

### Task 7: The Clip panel and export flow

**Files:**
- Modify: `web/public/lib/playback/transport.js` (expose `loop()` and `setLoop({from, to})` on the returned object; `setLoop` goes through `markLoop`'s tidy rules)
- Create: `web/public/lib/playback/clip-panel.js` (mount, buttons, progress, ready list)
- Create: `web/public/lib/playback/clip-flow.js` (pure: `nextClipStep`, `presetAround`, `clipFileName` display helpers)
- Modify: `web/public/index.html` (button `#clip` in the player bar + panel markup), `web/public/lib/playback/player.js` (mount panel; clip mode gate in `saveProgress`), `player-keys.js` (key `x` → toggle panel), styles file the player bar uses
- Test: `web/test/clip-flow.test.ts`

**Interfaces:**
- Consumes: Task 5 routes; `transport.loop()`/`setLoop()`; `state.profile()?.kids`.
- Produces: `presetAround(at: number, runtime: number | null) -> { from, to }` = `{ from: max(0, at − 15), to: min(runtime ?? ∞, at + 15) }`; `nextClipStep(job: ClipStatus | null, { visible: boolean, downloaded: Set<string> }) -> "poll" | "download" | "wait" | "idle"` — `"download"` only when done, visible, and not yet downloaded; `openPlayer(set, { at, clip: { from, to } })` option.
- Clip mode: the panel is open **or** the title was opened with `options.clip`. While in clip mode and `state.profile()?.kids !== true`, `saveProgress` returns without writing. Leaving clip mode resumes normal saving.
- Panel copy: `"Clip"`, `"Start"`, `"End"`, `"Set to now"`, `"−1 s"`, `"+1 s"`, `"{n} s · plays as a loop"`, over 180 s: `"Clips can be up to 3 minutes"` and Export disabled, `"Export MP4"`, `"Making the clip… {pct}%"`, ready list item `"Download {name}"`.
- Download: navigate a hidden `<a download>` to `/api/clips/{id}/file?profile={p}`; remember downloaded ids in the panel's memory.
- Polling: every 1 s while visible; on `visibilitychange` to visible poll immediately; on panel open fetch `GET /api/profiles/{p}/clips?setId=` to show ready files and resume a running one.

- [ ] **Step 1: Failing tests** (`clip-flow.test.ts`): `presetAround clamps at zero and at the runtime`; `nextClipStep downloads once when done and visible`; `nextClipStep waits while hidden and polls while running`; `a failed job is idle and shows its error`.
- [ ] **Step 2: Run** `cd web && bun test test/clip-flow.test.ts` — FAIL.
- [ ] **Step 3: Implement** panel + flow + `player.js` gate + transport accessors.
- [ ] **Step 4: Run** `cd web && bun test && bun run lint`, then check in `bun run preview` at phone width (375 px) and desktop: panel opens with `x` and the button, ±1 s works by touch, loop plays, Export disabled over 3 min.
- [ ] **Step 5: Commit** `feat(web): clip panel exports the A–B stretch as MP4`.

### Task 8: "Marked scenes" shelf on Home

**Files:**
- Create: `web/public/lib/catalog/home-scenes.js` (shelf, modelled on `home-resume.js`)
- Modify: `web/public/lib/catalog/home-view.js` / `home-shelves.js` (place the shelf right after Continue; hidden when empty), `web/public/app.js` (`play(set, null, { at, clip })` for a scene card)
- Test: `web/test/home-scenes.test.ts`

**Interfaces:**
- Consumes: `state.scenes()`, `state.removeScene(id)`, library lookup by `setId`, `presetAround`, `openPlayer(set, { at, clip })`.
- Produces: `sceneCards(scenes, byId) -> { id, set, at, label }[]` (pure; drops marks whose set is not in the library; label = title, episode line when the set is an episode, and `clockTime(at)`).
- Card: the title's still or backdrop (whatever `home-resume.js` cards use), a remove button `"Remove"` (aria-label `"Remove mark at {time}"`). Shelf title `"Marked scenes"`.

- [ ] **Step 1: Failing tests:** `sceneCards keeps newest first and drops marks for missing titles`; `an episode's label names the episode`.
- [ ] **Step 2: Run** `cd web && bun test test/home-scenes.test.ts` — FAIL.
- [ ] **Step 3: Implement**; card click opens the player at the mark with the panel open and the range preset.
- [ ] **Step 4: Run** `cd web && bun test && bun run lint`; check in `bun run preview` that the shelf appears with a fixture mark and disappears after Remove.
- [ ] **Step 5: Commit** `feat(web): Marked scenes shelf on Home`.
