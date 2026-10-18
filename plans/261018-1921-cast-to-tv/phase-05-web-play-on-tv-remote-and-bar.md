# Phase 05 — Web page: Play on TV, ask, remote, bar

**Spec:** §2 (sender side), §3–§5. **Status:** pending.

**Context:**
- **Markup** (all in `web/public/index.html`):
  - the player dialog `#player` (:135);
  - the control card `#control-card` (:183), with its tools row (:200) where `#together` sits (:210);
  - the transport row (:223).
- **Wiring:**
  - `web/public/lib/playback/player.js` (ceiling 1018: grows only by the few lines named here, with the ceiling raised);
  - `web/public/app.js`: `play()` :286, `setTogetherOpener` :231, `playsNext` :320.
- **Together, browser side:**
  - `together-now.js`: `together()` :95, `onTogether()` :48, `togetherNow()` :32, `inTogether()` :36, `screen` :20;
  - `catalog/together-join.js`: `togetherNotice()` :34 and `joinOr()` :53;
  - `title-spread.js` `playPill()` :52;
  - `catalog/film-page.js` :39 and `series-page.js` :87.
- **Tests:** `test/together-player-controls.test.ts`, `test/together-join.test.ts` and `test/together-now.test.ts` show the browser stubs (`browserEnvironment`).

All new browser modules go under `web/public/lib/cast/`. Each stays under 200 lines and gets a `.d.ts` like its neighbours.

### Task 5.1: Asking the TV

**Files:**
- Create: `web/public/lib/cast/cast-ask.js` (+ `.d.ts`)
- Modify: `index.html` (add `<dialog id="cast-ask"><p role="status"></p><div class="cast-ask-actions"></div></dialog>`); `web/public/styles/player.css` or the catalog CSS beside the report sheet (same look as the Report sheet)
- Test: `web/test/cast-ask.test.ts`

**Interfaces — Consumes:** `CAST_WORDS`, `busyQuestion`, `refusedLine` (phase 02); `POST /api/together` `offer`/`replace`/`cancel` (phase 04); `together()`, `screen`.
**Interfaces — Produces:** `askTv(own: {set: string; playing: boolean; pos: number; rate: number}, profile: string): Promise<Live | null>`. It resolves to the accepted session, or `null` when nothing was cast. While it waits, the dialog shows `CAST_WORDS.asking`.

- [ ] **Step 1: Write the failing tests.** Stub `fetch`, one test per answer.
  - `"on resolves the live session and closes the dialog"`.
  - `"busy asks to replace, and Replace sends replace and resolves on"`: the dialog text is `busyQuestion("Dune (2021)")`. Clicking Replace sends a body with `op:"replace"` and the same `id`.
  - `"busy then Cancel sends cancel and resolves null"`.
  - `"refused shows the reason and resolves null"`: text `refusedLine("it's rated above Mia's age limit")`.
  - `"no answer says Mediagram isn't open"`: `CAST_WORDS.noAnswer`.
  - `"unreachable says Telegram isn't reachable"`: on 503.
- [ ] **Step 2: Run** `bun test test/cast-ask.test.ts` → FAIL. **Step 3: Implement.** The refusal, no-answer and unreachable messages close with one OK button. **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(web): ask the TV to play a title, and the Replace question`.

### Task 5.2: The remote

**Files:**
- Create: `web/public/lib/cast/cast-remote.js` (open/close, follow, moves) and `web/public/lib/cast/cast-remote-card.js` (DOM: drawing the card)
- Modify:
  - `index.html`: add `<dialog id="cast-remote">`. It holds a backdrop `<img>`, the title, a `Playing on TV` label, a `role="status"` note, and a card built from the control card's classes (seek row, transport row with `−10`, play/pause, `+10`, Next episode, a tools row with Speed, Subtitles & audio and Stop). It has no PiP, info, report or volume.
  - `together-now.js` `setTogetherOpener(open, find, next)`: the third callback, `(setId) => playsNext(library, byId.get(setId), null).next?.setId ?? null`, is registered in `app.js:231`.
  - `transport.js`: export its speed list, which is today internal to the speed menu (:244), so the remote offers the same speeds.
- Test: `web/test/cast-remote.test.ts`

**Interfaces — Consumes:** `expectedPosition`, `changeOf` (follow.js); `endingOf` (cast.js); `sayOnRemote`, `CAST_WORDS` (phase 02); `together()`, `onTogether()`, `serverNow()`, `setForTogether()`.
**Interfaces — Produces:**
- `openRemote(live: Live): void`: joins with `{op:"join", screen, id, remote:true}`, shows the dialog and follows the session.
- `closeRemote(): void`: leaves with `op:"leave"` (a remote leaving never ends it) and closes the dialog.
- `remoteOpen(): boolean`.

**Behaviour:**
- **Clock:** the seek bar shows `expectedPosition(session, serverNow())`, redrawn every 500 ms while playing.
- **Moves**, each sent as `{op:"act", screen, id, change}`:
  - play/pause → `change: {playing}`;
  - seek, ±10 → `change: {pos}`;
  - speed → `change: {rate}`;
  - Next episode → `change: {set: next, pos: 0, playing: true}`, and only when the `next` callback answers an id.
- **Subtitles & audio:** the menu lists "Off", then `cast.tracks.text`, then `cast.tracks.audio`. The current choice is marked from `cast.text`/`cast.audio`. A pick sends `{op:"tracks", screen, id, text}` or `{…, audio}`.
- **Stop:** sends `{op:"end", screen, id}` and closes.
- **Notices:** each change heard from another screen shows `sayOnRemote(changeOf(before, after), after)` for 2 s, when non-null.
- **Ending:**
  - `endingOf(session, screen)` = `"finished"` → the note shows `CAST_WORDS.finished` for 2 s, then the dialog closes;
  - `"stopped"` → the same with `CAST_WORDS.stopped`;
  - the session is gone (`heard.session === null`) → it closes with `CAST_WORDS.stopped`.

- [ ] **Step 1: Write the failing tests**, with a stubbed `together()` and heard events.
  - `"opening joins as a remote"`.
  - `"play/pause sends playing:false at the expected position"`.
  - `"+10 sends pos expected+10"`.
  - `"choosing German subtitles sends tracks text:de"`.
  - `"Stop sends end and closes"`.
  - `"a pause by the TV shows Paused on TV"`.
  - `"the TV leaving closes with Stopped on the TV"`.
  - `"the title ending closes with Finished"`.
  - `"Next episode is hidden without a next title"`.
- [ ] **Step 2: Run** `bun test test/cast-remote.test.ts` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(web): the remote for a title playing on the TV`.

### Task 5.3: Play on TV buttons, and the player stepping aside

**Files:**
- Create: `web/public/lib/cast/play-on-tv.js` (+ `.d.ts`); `web/public/lib/playback/player-cast.js` (+ `.d.ts`)
- Modify:
  - `index.html`: add `<button id="cast" type="button">Play on TV</button>` right after `#together` (:210);
  - `catalog/film-page.js` :39 and `catalog/series-page.js` :87: the Play on TV pill beside Play/Resume;
  - `player.js`: import, then `mountPlayerCast({...})` beside `mountTogether` (:406). Raise `player.js`'s ceiling in `code-standards.test.ts` by the lines added, with a dated sentence.
- Test: `web/test/cast-buttons.test.ts`

**Interfaces — Produces:**
- `castPill(setId: string, pos: number): HTMLElement`: the pill. It is greyed with a `title` reason when `!togetherNow().available` (`UNREACHABLE`).
  - On click: `askTv({set: setId, playing: true, pos, rate: 1}, state.profileId())`, then `openRemote(live)` when non-null.
  - The film page passes `resume ?? 0`. The series page passes the episode its Play/Resume pill would start, at that pill's resume point.
- `mountPlayerCast({button, video, filmTime, setId: () => string|null, close: () => void}): void`. It greys the button:
  - with `CAST_WORDS.leaveFirst` while `inTogether()`;
  - with `UNREACHABLE` while unavailable.
  - On click it asks with the player's own `{set, playing: !video.paused, pos: filmTime(), rate: video.playbackRate}`. On a live answer it `close()`s the player (saving progress as closing always does), then calls `openRemote(live)`. Otherwise the player plays on.

- [ ] **Step 1: Write the failing tests.**
  - `"the pill asks with the resume point"`.
  - `"the pill is greyed with the reason while Telegram is unreachable"`.
  - `"the player's button is greyed while in watch together"`.
  - `"an accepted cast closes the player and opens the remote"`.
  - `"a refused cast leaves the player playing"`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS, plus `test/code-standards.test.ts`.
- [ ] **Step 5: Commit** `feat(web): Play on TV on the title page and in the player`.

### Task 5.4: The bar, and Home's notice

**Files:**
- Create: `web/public/lib/cast/cast-bar.js` (+ `.d.ts`)
- Modify:
  - `app.js`: one `mountCastBar()` call beside `initializePlayer` (raise its ceiling if it is on the ratchet);
  - `catalog/together-join.js` `togetherNotice()`: nothing is shown for a session whose `cast !== null`;
  - the catalog CSS: `.cast-bar`, fixed to the bottom, the width of `#main`, the same surface as the control card, and clear of `.daily-limit-notice`.
- Test: `web/test/cast-bar.test.ts`

**Interfaces — Produces:** `mountCastBar(): void`.
- **Shown when all three hold:**
  - `togetherNow().live?.session.cast?.state === "on"`;
  - `setForTogether(live.session.set)` is not `null`;
  - `!remoteOpen()`.
- **Contents:** text `barLine(title)` (the title line `titleLine(set)` gives the player), and a play/pause button that sends `act {playing}`.
- **Tapping** the text calls `openRemote(live)`.

- [ ] **Step 1: Write the failing tests.**
  - `"a cast on the TV shows the bar with its title"`.
  - `"an offer shows no bar"`.
  - `"a title hidden on this profile shows no bar"`.
  - `"Home shows no Watching together notice for a cast"`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(web): a Playing on TV bar on every page while a cast plays`.

### Task 5.5: Walk it in the preview

- [ ] `cd web && PREVIEW_CAST=accept bun run preview`. With gstack `/browse`, on http://127.0.0.1:8795:
  1. choose a profile, open a film, press **Play on TV** → the remote opens with the backdrop, "Playing on TV", the tracks menu (English 5.1/Deutsch; Off/English/Deutsch);
  2. press pause and +10, then check the seek bar;
  3. Back → the bar shows "Playing on TV · *title*"; tap it → the remote again; Stop → closes.
- [ ] Repeat with `PREVIEW_CAST=busy` (the Replace question), `refuse` (the reason) and `none` (the no-answer words after 5 s).
- [ ] Screenshot each state into `plans/261018-1921-cast-to-tv/reports/`, then check the look against the control card. It must be the same surface, type and spacing (memory: the player aesthetic is a user decision).

**Success criteria:** `bun test`, `tsc`, `eslint` and `code-standards` all pass, and the four preview modes look and behave as above.
