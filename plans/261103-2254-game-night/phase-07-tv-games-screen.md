# Phase 07: TV app: Games entry and screen

Spec: "Hosting and joining", "Which titles the games use" (kids profile vs grown-up), "Language", Calls #1 (WebView), #2 (Games on kids profiles), #5 (leaving ends the night; Back mid-game returns to the lobby).

Outcome: the TV rail has *Games*; it opens a full-screen page that starts the host and shows the TV page; Back returns from a game to the lobby, and from the lobby leaves and stops the host.

Context (`android/`): rail items are the shared `enum class RailItem` (`ui-common/src/main/kotlin/ui/common/RailItem.kt:14-22`), looped on the phone too (`ui-mobile/.../chrome/CompactLibraryHeader.kt:105`, exhaustive `when` in `ui-mobile/.../chrome/RailSelect.kt:22-30`). TV pages are a `FrameKind` stack (`ui-common/.../LibraryPositions.kt:28`, `openStats()` at :290), drawn in `ui-tv/.../TvLibrary.kt:216-290`; PLAYER, MENU, REMOTE are drawn outside `TvFrameChrome` (:218-223). Back pattern: `TvStatsFrame` (`ui-tv/.../TvStatsFrame.kt`). Keep-screen-on: `KeepScreenOnWhile` (`ui-common/.../player/KeepScreenOnWhile.kt:14`). Profile: `WatchStateRepository.chosenProfile` and `snapshot.kidsMarks`; `Profile.kids`, `Profile.kidsLimit`. Cleartext to 127.0.0.1 is already permitted (`app/src/main/res/xml/network_security_config.xml`).

---

### Task 7.1: Feature module, view model and the kids decision

**Files:**
- Create: `android/feature/games/build.gradle.kts` (as `feature/stats`), `android/feature/games/src/main/kotlin/GameNightConfig.kt`, `GameNightViewModel.kt`, `android/feature/games/src/test/kotlin/GameNightConfigTest.kt`, `GameNightViewModelTest.kt`
- Modify: `android/settings.gradle.kts` (include `:feature:games`), `android/ui-tv/build.gradle.kts` (depend on it)

**Interfaces:**
- Consumes: Task 6.2 `startGameNight`, `gameNightBack`, `stopGameNight`, `GameNightConfig`, `GameNightUrls`, `FakeCore`.
- Produces:
  - `const val GAME_NIGHT_KIDS_LIMIT = 12`
  - `fun gameNightConfig(sets: List<MediaSet>, profile: Profile, marks: Map<String, Int>): GameNightConfig` — kids profile: `forKidsProfile(sets, marks, profile.kidsLimit)` ids, `kidsLocked = true`; otherwise `forKidsProfile(sets, marks, GAME_NIGHT_KIDS_LIMIT)` ids, `kidsLocked = false`
  - `@HiltViewModel class GameNightViewModel @Inject constructor(coreProvider: CoreProvider, watchState: WatchStateRepository, catalog: CatalogRepository, tmdb: TmdbSettings, @AppScope appScope: CoroutineScope)` with `val state: StateFlow<GameNightUi>`; `sealed interface GameNightUi { data object Starting; data class Ready(val tvUrl: String); data class Failed(val message: String) }`; `fun start()` (config from `gameNightConfig(…)` plus `tmdbKey = tmdb.read()`); `suspend fun back(): Boolean` (the core's answer); `fun stop()` — launched on `appScope` (`android/core/data/src/main/kotlin/di/DataModule.kt:132`) so leaving the screen cannot cancel it
  - `gameNightConfig` returns the config with `tmdbKey = null`; the view model fills it in

- [ ] **Step 1: Write the failing tests:**
  - `kidsProfileLocksAtItsOwnLimit` — kids profile with limit 6; sets FSK 0, FSK 6, FSK 12, unrated marked from 6 → ids = FSK 0, FSK 6, the marked one; `kidsLocked == true`.
  - `grownUpProfileMarksFsk12` — grown-up; FSK 12 in, FSK 16 out, unrated marked from 12 in, unmarked unrated out; `kidsLocked == false`.
  - `startPassesTheConfigAndShowsTheTvPage` (FakeCore records the config; state becomes `Ready("http://127.0.0.1:8080/tv")`).
  - `failedStartShowsTheMessage` (FakeCore throws → `Failed`).
  - `stopReachesTheCore` (`stop()` → `stopGameNight` count 1).
- [ ] **Step 2: Run** `cd android && ./gradlew :feature:games:testDebugUnitTest` — FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit** `feat(android): game night view model and kids decision`

### Task 7.2: Rail entry and the Games page

**Files:**
- Modify: `ui-common/.../RailItem.kt` (add `GAMES("Games", <icon>)` and `val tvOnly: Boolean`, true only for `GAMES`), `ui-mobile/.../chrome/CompactLibraryHeader.kt` and `RailSelect.kt` (phone skips `tvOnly` items; the `when` gets an unreachable `GAMES` branch), `ui-tv/.../chrome/TvLibraryRail.kt:61` (after STATS), `ui-tv/.../system/TvMenuPage.kt`, `ui-common/.../LibraryPositions.kt` (`FrameKind.GAMES`, `fun openGames() = push(FrameKind.GAMES, "")`), `ui-tv/.../TvLibrary.kt` (draw GAMES outside `TvFrameChrome`), `ui-tv/.../catalog/TvCatalogScreen.kt:150-159` (`onRailSelect`), `ui-tv/.../catalog/TvCatalogNav.kt:104` (rail-key restore), `app/src/main/res/xml/network_security_config.xml` (comment: the game page on 127.0.0.1 is the other plain-http use), `ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt` (add `GameNightViewModel` to `models`)
- Create: `ui-tv/src/main/kotlin/ui/tv/games/TvGamesFrame.kt`, `ui-tv/src/test/kotlin/ui/tv/TvGamesRailTest.kt`

**Interfaces:**
- Consumes: Task 7.1 `GameNightViewModel`, `GameNightUi`.
- Produces: `@Composable internal fun TvGamesFrame(leave: () -> Unit)`:
  - `Starting` → centred `Starting game night…`; `Failed` → the message and `Press Back to return`.
  - `Ready` → full-screen `AndroidView` WebView (`javaScriptEnabled = true`, `domStorageEnabled = true`), `loadUrl(tvUrl)`, focus requested so the remote's D-pad and OK reach the page.
  - `KeepScreenOnWhile(true)`.
  - `BackHandler`: launch `vm.back()`; if `false`, `leave()`.
  - `DisposableEffect` `onDispose` → `vm.stop()`.

- [ ] **Step 1: Write the failing tests** (Robolectric, pattern of `TvStatsRailTest`):
  - `gamesOnTheRailOpensTheGamePage` — select *Games* on the rail; the WebView's last loaded URL (`shadowOf(webView).lastLoadedUrl`) is `http://127.0.0.1:8080/tv`.
  - `backInTheLobbyLeavesAndStopsTheHost` — FakeCore `gameNightBack = false`; press Back → the rail page is shown again; `stopGameNight` count 1.
  - `backDuringAGameStaysOnThePage` — `gameNightBack = true`; Back → still on the Games page.
  - `phoneRailHasNoGames` — the phone header's items do not include `RailItem.GAMES`.
- [ ] **Step 2: Run** `cd android && ./gradlew :ui-tv:testDebugUnitTest --tests '*TvGamesRailTest*' :ui-mobile:testDebugUnitTest` — FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same — PASS; then `scripts/check.sh` — `all checks passed`.
- [ ] **Step 5: Commit** `feat(android): Games on the TV rail opens game night`
