# Phase 08 — Android UI: phone/tablet and TV

**Spec:** `../spec.md` §3. **Status:** pending. **Depends on:** phase 07, and phase 04 for the wording and layout (the web is the reference).

## Context

The anchors come from mapping `android/`:

- **Phone player.**
  - The tool row is `CardToolsRow` (`ui-mobile/.../player/PlayerCardTools.kt:34-90`). Its buttons are `LabelButton`s inside a `FlowRow`; PiP sits outside it at `:86-88`.
  - Callbacks come from `PlayerCardActions` (`PlayerControlCard.kt:51-65`), built by `playerCardActions(viewModel, …)` (`PlayerCardBridge.kt:21-38`).
  - The title is in `PlayerTopBar` (`PlayerTopBar.kt:75-80`).
  - There is no timed notice on the phone: `ActionNoticeBar` is a Snackbar with no timeout.
- **TV player.**
  - The tools are `TvCardTools` (`ui-tv/.../player/TvToolGroup.kt:63-92`), each a `TvMenuTool(label, description, modifier) { … }`. Framing ends the row with `focusProperties { right = FocusRequester.Cancel }` (`:91`).
  - Each tool's `FocusRequester` lives in `TvPlayerFocus.kt:6-19`, and these are never conditional (memory: a conditional `focusRequester` resets its target).
  - The callback goes in `TvPlayerExtras` (`TvPlayerExtras.kt:9-32`), wired in `TvPlayerControlsBridge.kt:98-135`.
  - The top bar is `TvPlayerTopBar.kt:37`. The timed-notice precedent is `TvActionNotice` (`ACTION_NOTICE_MS`, `LaunchedEffect(notice) { delay; gone() }`).
- **Home.**
  - Phone: `HomeScreen.kt:54`, a `LazyColumn` whose first item is `item(key = "cover")`.
  - TV: `TvHome.kt:80`. A section needs entries in the `TvHomeSection` enum (`home/TvHomeTargets.kt:9`), the `sections` and `drawn` lists, and the `entryFocus` map. Arrival focus goes to the first non-empty section (`homeTargetOf`). Use the `TvPlate` look (`TvPlate.kt:79`).
- **Title pages.**
  - Phone: `TitleDetailScreen.kt:98-99`, with `TitlePills(playLabel = …, onPlay = …)`, and series in `CollectionScreen.kt:193`.
  - TV: `TvTitlePage.kt:136-140`, `TvSpreadPill(text = "▶ …", solid = true, modifier = Modifier.focusRequester(play))`, and series in `TvSeriesResume.kt`.
- **Catalog state.** `CatalogViewModel` (`feature/catalog`) can depend on `core/data`'s `TogetherLive`. Its kids projection (`CatalogViewModel.project`, `:184-193`) already decides what a profile may see. Show Join only for a set in the projected catalog, which is the spec's kids rule.
- **TV focus rules from memory** (`tv-compose-focus-gotchas`):
  - no `focusRestorer`;
  - a row whose key is fixed;
  - the `FocusRequester` is unconditional; hide the plate's *content*, not the requester.

## Files

- **Phone/tablet (`ui-mobile`):**
  - `player/PlayerCardTools.kt`: the button;
  - `player/PlayerCardBridge.kt` and `PlayerControlCard.kt`: the callback;
  - `player/PlayerTopBar.kt`: the label;
  - `player/PlayerScreen.kt`: the notice pill;
  - Create `player/TogetherNoticePill.kt`;
  - `catalog/HomeScreen.kt`: the Join row;
  - Create `catalog/TogetherJoinRow.kt`;
  - `catalog/TitleDetailScreen.kt` and `CollectionScreen.kt`: the Join label.
- **TV (`ui-tv`):**
  - `player/TvToolGroup.kt`, `TvPlayerFocus.kt`, `TvPlayerExtras.kt` and `TvPlayerControlsBridge.kt`: the tool;
  - `player/TvPlayerTopBar.kt`: the label;
  - Create `player/TvTogetherNotice.kt`;
  - `catalog/TvHome.kt`, `home/TvHomeTargets.kt`: the section;
  - Create `catalog/TvTogetherPlate.kt`;
  - `catalog/TvTitlePage.kt` and `TvSeriesResume.kt`: the Join label.
- **Catalog state:** `feature/catalog/.../CatalogViewModel.kt` exposes `together: StateFlow<TogetherOffer?>` and `fun join(offer)`.
- **Tests:**
  - `ui-mobile/src/test/kotlin/ui/player/TogetherToolTest.kt`;
  - `ui-mobile/src/test/kotlin/ui/catalog/TogetherJoinRowTest.kt`;
  - `ui-tv/src/test/kotlin/ui/tv/TvTogetherTest.kt`.

## Interfaces

- **Consumes** from phase 07: `PlayerViewModel.together: StateFlow<TogetherUi>`, `onTogetherButton()`, `leaveTogether()`, and `TogetherLive`.
- **Produces:**

```kotlin
// feature/catalog
data class TogetherOffer(val id: Int, val set: MediaSet, val members: String) // members = "TV, Tablet"
```

## Wording (from the web, phase 04)

| Where | Text |
|---|---|
| Tool | **Watch together** / **Join** / **Leave watch together** |
| Unavailable | Disabled, with the reason "Telegram isn't reachable" (the TV shows it as the tool's description line) |
| Player label | **Together · TV, Tablet** |
| Notices (2 s) | "Paused by TV", "Resumed by TV", "Skipped to 41:20 by Tablet", "1.5× by TV", "Next title by Browser", "Tablet joined", "Tablet left", "Watch together ended" |
| Home row | "Watching together: *Title* · TV, Tablet", with a **Join** action |
| Title page | **Join** in place of Play/Resume |

## Tasks

### Task 8.1: Catalog offer

- [ ] **Write a failing test** in `feature/catalog/src/test/kotlin/...CatalogViewModelTest.kt`:
  - with a `FakeTogetherLive` holding a live session on `film-1`, and `film-1` in the projected catalog, `together` emits `TogetherOffer(id, film-1, "TV")`;
  - with a kids profile whose projection drops `film-1`, it emits `null`;
  - `join(offer)` calls `joinOnOpen(id)` and opens `film-1` the way `onPlay` does.
- [ ] **Implement it in `CatalogViewModel`:**
  - `val together = combine(togetherLive.live, projectedSets) { live, sets -> live?.let { l -> sets.find { it.setId == l.session.set }?.let { TogetherOffer(l.id, it, l.session.members.joinToString(", ") { m -> m.label }) } } }.stateIn(...)`;
  - call `togetherLive.refresh()` whenever Home is shown, from the same lifecycle hook that refreshes Continue.
- [ ] **Run** `./gradlew :feature:catalog:testDebugUnitTest`. Expected: PASS. Then commit: `feat(android): offer Join from the catalog`.

### Task 8.2: Phone and tablet

- [ ] **Write failing Robolectric tests** (`@Config(sdk = [35])`, `createEmptyComposeRule()`, as `PlayerNoticesTest.kt:36-39` does):
  - the tool reads "Watch together", "Join" or "Leave watch together" from `TogetherUi.button`, and is disabled with the reason as its `contentDescription` suffix when `enabled = false`;
  - the label is shown only when `TogetherUi.label != null`;
  - `TogetherJoinRow` shows "Watching together: Film · TV" and calls `onJoin`;
  - the title page shows "▶ Join" when the offer is for its set.
- [ ] **Implement:**
  - **Tool.** In `CardToolsRow`'s `FlowRow`, after Framing: `LabelButton(text = togetherText(ui.button), enabled = ui.enabled, onClick = actions.onTogether)`. Add `onTogether` to `PlayerCardActions`, set to `viewModel::onTogetherButton` in `playerCardActions`.
  - **Label.** In `PlayerTopBar`, after the title `Text`: `ui.label?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }`. Pass `together` into the top chrome the way the marks are passed.
  - **Notice.** `TogetherNoticePill(text: String?)`: `AnimatedVisibility(text != null)`, a rounded pill at the top centre, on the card's translucent fill. The controller already clears it after 2 s, so the UI keeps no timer. Place it in `PlayerScreen` above the card.
  - **Home.** In `HomeScreen`'s `LazyColumn`, before the cover: `together?.let { offer -> item(key = "together") { TogetherJoinRow(offer, onJoin = { onJoin(offer) }) } }`. It is one line in the theme's warning colour, as the web's notice is (`DESIGN.md` Notice). Thread `together` and `onJoin` from `CatalogScreen.kt:154`.
  - **Title page.** `TitlePills(playLabel = if (offer?.set?.setId == set.setId) "Join" else <existing>, onPlay = if (…) { { onJoin(offer) } } else onPlay)`. For series in `CollectionScreen.kt:193`, use Join when the offer's set is one of the collection's episodes.
- [ ] **Run** `./gradlew :ui-mobile:testDebugUnitTest`. Expected: PASS. Then commit: `feat(android): watch together on the phone and tablet`.

### Task 8.3: TV

- [ ] **Write failing Robolectric tests:**
  - **The tool.** It sits after Framing. D-pad right from Framing reaches it, and right from it goes nowhere (`FocusRequester.Cancel` has moved to it). OK calls `onTogetherButton`.
  - **The plate.** With an offer, it is Home's first section and takes arrival focus. OK calls `join`.
  - **No offer.** No plate. Arrival focus goes to the cover, as today.
  - **The title page.** It shows "▶ Join" for the offered set, and Join takes the page's initial focus as Play did.
- [ ] **Implement:**
  - **Tool.** Add `val together = FocusRequester()` in `TvPlayerFocus`, unconditionally. In `TvCardTools`, after Framing: `TvMenuTool(label = togetherText(ui.button), description = ui.reason ?: "", modifier = Modifier.focusRequester(focus.together).focusProperties { right = FocusRequester.Cancel }) { extras.onTogether() }`. Remove `right = FocusRequester.Cancel` from Framing. Add `onTogether` to `TvPlayerExtras`, wired to `viewModel::onTogetherButton` in `TvPlayerControlsForViewModel`. When `!ui.enabled` it still takes focus, so the reason is readable, but OK does nothing.
  - **Label and notice.** The label goes in `TvPlayerTopBar` next to the title. `TvTogetherNotice(text)` copies `TvActionNotice`'s look, without its own timer.
  - **Home.**
    - Add `TOGETHER` first in `TvHomeSection`.
    - In `TvHome`, `item(key = "together") { if (offer != null) TvTogetherPlate(offer, onJoin, Modifier.focusRequester(entryFocus.getValue(TvHomeSection.TOGETHER))) }`. The item and its requester are always there; only the content is conditional.
    - The section counts as non-empty for `homeTargetOf` only while `offer != null`.
    - `TvTogetherPlate` is a wide focusable plate with the `TvFocus` scale (1.08f) reading "Watching together: *Title* · TV, Tablet · Join".
  - **Title page.** In `TvTitlePage.kt:136-140`, the pill text becomes "▶ Join" and `onClick = onJoin` when the offer is for this set. Do the same in `TvSeriesResume.kt`.
- [ ] **Run** `./gradlew :ui-tv:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin lint`. Expected: PASS. Then commit: `feat(android): watch together on TV`.

## Todo

- [ ] 8.1 Catalog offer
- [ ] 8.2 Phone and tablet
- [ ] 8.3 TV

## Success criteria

- The wording matches the table above, which is the web's wording.
- Every new tool and plate is reachable by D-pad, and focus behaviour that worked before is unchanged. The existing TV focus tests stay green.

## Risks

- **TV Home's arrival focus moving to a plate can surprise a viewer.** It happens only while a session is live, which is the moment Join matters most. The spec asked for the plate first.
