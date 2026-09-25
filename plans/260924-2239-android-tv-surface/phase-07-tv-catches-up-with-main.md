# Phase 7: The television catches up with main

**Context:** [plan.md](plan.md) · merge `2d7dfd8` brought `main` (android-web-parity, Kids shelf removal) into this branch; its report listed what the phone and web gained that the TV does not have. Surface Parity makes each of those owed to the TV unless a difference is written down.

## Overview
- **Priority:** High — a television player without subtitles or an audio-track choice is not usable for half the library.
- **Status:** pending
- **Order:** runs after phase 5's emulator pass and before phase 6 (docs, version, final walk), so phase 6 documents the finished surface.

## Deliberate differences (not built, recorded here)
- **Shelf list/grid toggle** — plan Open Question 1 chose plates (walls) with no list toggle on TV; unchanged.
- **Double-tap seek, pinch framing, immersive bars, picture-in-picture** — touch/window affordances with no remote equivalent.
- **Search** — built (task 5), but typed through the system keyboard; voice search is out of scope.

Built, but differently from the phone or the web, on purpose:
- **Notes: links not followable, no ✕, no selection** — a remote cannot point at one word of a paragraph or select text; a link keeps its words. Back and the Notes button close the column instead of a ✕.
- **Notes entry among the controls, not in the top bar** — the top bar is not focusable (nothing there to press), so Notes sits with the tools (Notes, ⚙, ⓘ) at the end of the marks row, where the remote reaches it.
- **Tools at the end of the marks row, not in the transport** — the transport holds only what moves the film; with the tools in it the row overflowed a stage narrowed by the notes column and the gear could not be reached. The row wraps to a second line on a narrow stage.
- **Search: why-it-matched in italic grey** — the phone's accent colour means "the remote is here" on the television.
- **Previous = previous in the run** — the remote's Previous key steps back through the run, never "restart this title" (the playback session's answer); a held key is one step.
- **Mark finished as a row under a Continue plate** — the phone's long-press menu has no remote equivalent; the action sits one press down from the plate it acts on.
- **Up-next card does not take focus from the settings panel, the list dialog or the seek bar** — a viewer busy in one of those keeps the remote; the card stays one press Up from the seek bar.
- **Up-next card floats bottom-right above the controls, opaque** — as the web floats `.up-next`; inside the controls it pushed them into the title. Opaque because a countdown read through a bright scene is not read. Beside the settings panel while that is open.
- **Back order: settings panel → up-next card → notes → controls → leave** — each Back undoes the most recent thing put in front of the viewer.
- **Dialogs route media keys to the player** — the list dialog's window takes every key; play/pause and Next/Previous still reach the film rather than the playback session.
- **Action notice at the top-end of the stage** — out of the way of the bottom controls and the subtitles, where the phone's snackbar would sit over them.
- **Subtitles never rise above the top band** — lifted clear of the controls (and of the up-next card while it shows) as on the phone lifts them clear of its bar, but capped under the title/statistics; a cue over the title is unreadable, one over a scrim's edge is not. On a stage narrowed by the notes a long cue can still meet the card — the card is drawn over it for the last half-minute (the phone overlaps the same way).
- **Cues beside the settings panel** — while the panel is open the cue is centred in what the panel leaves of the picture, so a size or sync change can be judged.

## Implementation steps

Phone references live in `android/ui-mobile/src/main/kotlin/ui/`; shared logic in `android/feature/*` and `android/ui-common`. Move shared rules out of `ui-mobile` when the TV needs them (one copy), never copy. No ViewModel changes unless a phone ViewModel already exposes what TV needs.

### Task 1: Subtitles on the television
- [ ] **1.0** Carried from the merge review: fix `feature/catalog/…/CatalogTabs.kt` comments still counting four kept entries incl. Kids; move the end-time line (`trustedRuntime` + `endsAtLabel`) duplicated in TV `TvPlayerControls.endsLine` and phone `PlayerControls.kt` into one pure function in `feature/player`.
- [ ] **1.1** Draw subtitles on the TV player exactly as the phone's `ui/player/SubtitleLayer.kt` (move the shared parts to `ui-common` if composable-neutral), including the chosen style (size, backing) and sync offset from the shared settings.
- [ ] **1.2** Controls never cover subtitles when hidden; when shown, subtitles lift above the controls as on the phone.
- [ ] **1.3** Tests + commit — `feat(android): subtitles on the television player`.

### Task 2: Player settings on the television
- [ ] **2.1** A TV settings panel reachable from the transport (a gear/settings control), D-pad navigable, with the phone sheet's sections: speed, audio track, subtitle choice, subtitle size/backing/sync, framing — the same choices and labels as `PlayerSettingsSheet*.kt`, `AudioSection.kt`, `SubtitleSection.kt`, `SubtitleStyleSection.kt`, `FramingSection.kt`. Back closes the panel first.
- [ ] **2.2** Speed readout in the TV top bar as the phone's `PlayerTopBar.kt` shows it.
- [ ] **2.3** Tests + commit — `feat(android): playback settings on the television`.

### Task 3: Up next, autoplay and the next-episode preload
- [ ] **3.1** Pass the run to the TV player as the phone does (`LibraryPositions.run`), restoring autoplay and the next-two-episodes preload.
- [ ] **3.2** An up-next card on TV matching `UpNextCard.kt` (countdown, Play now focused, Cancel), reachable by remote; Play all where the phone has `PlayAllButton.kt`.
- [ ] **3.3** Media Next/Previous keys move through the run (replace the key table's Ignore rows; update its tests). Today MediaPrevious restarts the title (the media session added on main handles it before the screen) — the TV must own both keys.
- [ ] **3.4** Tests + commit — `feat(android): up next and autoplay on the television`.

### Task 4: Retry and the notes panel
- [ ] **4.1** Playback failure offers Retry (focused) as `PlayerFailure.kt` does.
- [ ] **4.2** Notes panel for courses as `NotesPanel.kt`/`NotesLayout.kt`/`NotesSpans.kt`, readable and scrollable by D-pad.
- [ ] **4.3** Tests + commit — `feat(android): retry and course notes on the television player`.

### Task 5: Search and genre pages
- [ ] **5.1** A Search entry in the masthead → TV search screen over `SearchViewModel` (system keyboard for the query, results as rows/plates as the phone's `SearchScreen.kt`/`SearchRow.kt`).
- [ ] **5.2** Genre pages: genres on a title page become focusable links → a genre wall as `GenreScreen.kt`/`GenreLinks.kt` over `GenreShelf.kt`.
- [ ] **5.3** Tests + commit — `feat(android): search and genre pages on the television`.

### Task 6: Continue and profile housekeeping
- [ ] **6.1** Offline badge on plates and Continue/Next up cards (`OfflineBadge.kt`, `SetCard.held`, `heldIds`).
- [ ] **6.2** Mark finished on a Continue plate (remote: a secondary action — e.g. long-press Centre or a Mark finished row on the title page — mirror the phone's reachability; write down the choice).
- [ ] **6.3** Remove a profile from "Who's watching?" as `RemoveProfileDialog.kt` (confirm dialog, cancel focused).
- [ ] **6.4** Tests + commit — `feat(android): offline marks, mark finished and profile removal on the television`.

### Task 7: Emulator pass on `TV test`
- [ ] **7.1** Play a title with subtitles; change audio/subtitle/speed; let an episode end into up next; Retry on a forced failure if reproducible; search; a genre page; mark finished; remove `TV kids` (created by the phase 4 walk).
- [ ] **7.2** Report every title played and every preference changed; reset preferences.

## Success criteria
Every item from the merge report's parity list is either on the TV or listed above as a deliberate difference.
