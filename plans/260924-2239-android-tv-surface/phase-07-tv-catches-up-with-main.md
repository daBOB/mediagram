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

## Implementation steps

Phone references live in `android/ui-mobile/src/main/kotlin/ui/`; shared logic in `android/feature/*` and `android/ui-common`. Move shared rules out of `ui-mobile` when the TV needs them (one copy), never copy. No ViewModel changes unless a phone ViewModel already exposes what TV needs.

### Task 1: Subtitles on the television
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
- [ ] **3.3** Media Next/Previous keys move through the run (replace the key table's Ignore rows; update its tests).
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
