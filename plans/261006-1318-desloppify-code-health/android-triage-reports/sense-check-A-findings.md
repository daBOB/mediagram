# Sense-check A: findings

Verified read-only against /home/andre/Workspace/mediagram-desloppify/android (Android source at 31cb8561). Every cited line was opened, and so were the web references (../web as it stands now). No repo file was modified and no Gradle build was run.

**Version manifests.** Every step also edits the three version manifests: ../Cargo.toml, ../web/package.json and app/build.gradle.kts versionName. They are left out of the per-step touch lists below because every step conflicts on them.

Legend: C = create, E = edit, D = delete, M = move.

---

## 1. catalog-mark-writes-guarded: **tighten** (step 2)

The cluster is worth doing. Step 1 fixes a real crash path: an unguarded `viewModelScope.launch` on a repository whose contract propagates core and snapshot failures (WatchStateRepository.kt:392-396). Step 3 deletes a redundant clause. Step 2 is right in substance, but its Collection overloads add API without moving the rule out of the UI.

### Step 1: keep [small]
- CatalogViewModel.kt:89-94 (setEditorsChoice, launch at :93) and :97-102 (setWatchlisted, launch at :101) are bare launches. writeState is at :317-339. Its siblings createList, renameList, deleteList, setInList and markFinished (:280-309) all route through it. writeState clears only its own sentence (:335-336). All verified.
- The player's wording: PlayerMarksController.kt:79 `write("Watchlist update")` and the template at :143. Verified.
- Test pattern: CatalogViewModelTest.kt:235 and :280 `watch.provider.beforeCore = { throw IllegalStateException(...) }`. Verified.
- Touch list: E feature/catalog/src/main/kotlin/CatalogViewModel.kt, E feature/catalog/src/test/kotlin/CatalogViewModelTest.kt.

### Step 2: tighten [small]
- All 8 site lines are verified: LibraryTitleBranches.kt :48, :55, :102, :105 and TvLibraryCatalogFrames.kt :85, :91, :154, :160. So are the explicit entries LibraryFlowBranches.kt:245 and TvLibraryBranches.kt:198, firstItemOf at Collection.kt:77, TvHomeReturnTest.kt:71 and TvAppFixture.kt:108.
- **Over-engineering:** the `catalog.Entry.Collection` overloads do not take the "a show is marked by its first episode" rule out of the UI. Both surfaces must keep `firstEpisodeId` (LibraryTitleBranches.kt:84, TvLibraryCatalogFrames.kt:114) because it also decides whether the show's editor's-choice control exists at all (`if (kidsProfile || firstEpisodeId == null) null`, :99 and :157). The overloads would add two public functions and duplicate the rule, not move it.
- Corrected step: drop the overloads. The UI passes `firstEpisodeId` to `toggleWatchlist`/`toggleEditorsChoice(setId)`. That matches the issue's own suggestion (`toggleWatchlist(setId)`, `toggleEditorsChoice(setId)`).
- Tests: drop the overload tests. Add one flip test (the toggle reads the snapshot), since that is the new branch.
- CatalogViewModel.kt is already 340 lines. The two toggles add about 10 and deleting setEditorsChoice removes 7, so it stays roughly flat.
- Touch list: E CatalogViewModel.kt, E CatalogViewModelTest.kt, E ui-mobile/src/main/kotlin/ui/LibraryTitleBranches.kt, E ui-tv/src/main/kotlin/ui/tv/TvLibraryCatalogFrames.kt. LibraryFlowBranches.kt and TvLibraryBranches.kt are not edited.

### Step 3: keep [trivial]
- PlayerMarksController.kt:93 clause verified. `marks` is the `stateIn(WhileSubscribed(5_000))` at :47-64, `canMarkKids = profile?.kids != true` at :61, and the comment at :92. PlayerMarksTest.kt:195 asserts `kidsFromSix`. All verified.
- The test scenario (collect, stop, advance past 5 s, switch profile) is the only way the stale clause can bite, so it is the right regression test. installMainDispatcher puts viewModelScope on runTest's scheduler, so virtual time works.
- Touch list: E feature/player/src/main/kotlin/PlayerMarksController.kt, E feature/player/src/test/kotlin/PlayerMarksTest.kt.

---

## 2. lan-cache-store-failures: **tighten** (step 1)

The cluster is worth doing. It fixes two crash paths (a worker coroutine dies on a keystore throw; Settings crashes on an unreadable token store), a parity bug where the phone prompts for permission after a refused token, and three copies of a platform constant.

### Step 1: tighten [small]
- LanWriteQueue.kt: :79/:80 sit outside the try, which is IOException-only at :81-93 with the catch at :89. The worker is launched at :57; the KDoc at :35-37 is verified. Safely.kt:6 verified. LanCacheModule.kt:94 `server = { locator.server.value }` verified, `settings` is a parameter at :81, and LanCacheRuntime.kt:41 gates reads. All verified.
- The sharing-switch half is legitimate. The skip note of the deferred `lan_read_write_gates_diverge` says "The write-gate half is carried in cluster lan-cache-store-failures". Enqueue happens only when `runtime.server()` was non-null (LanFirstChunkSource.kt:57/:75), so the leak really is limited to at most 8 already-queued chunks.
- Corrections:
  - The keystore failure is a `GeneralSecurityException`, per the issue and EncryptedTelegramSettings' KDoc, not a "SecurityException".
  - The old catch carries a why-comment (:90-92: the chunk was already served from Telegram). The step must keep it above the safely call; as written, it would silently drop it.
- Tests:
  - The third bullet ("a server lambda gated on a flag that flips off ... puts stays empty") passes before and after the change: the queue already reads `server()` per write. It pins nothing the step changes, so drop it.
  - The server-throws bullet runs through the same `safely` as token-throws. One test, token throwing GeneralSecurityException, is enough.
- Touch list: E core/playback/src/main/kotlin/LanWriteQueue.kt, E feature/player/src/main/kotlin/di/LanCacheModule.kt, E core/playback/src/test/kotlin/LanWriteQueueTest.kt.

### Step 2: keep [small]
- Verified: LanCacheViewModel.kt:58-67 (`state`), the snapshot reads at :144-148 including `tokenSettings.read()` at :147 and `client.status` at :144, `act` at :120-125, and the writes at :75, :82-85 and :100. Also verified: the SystemViewModel pattern at :62-85, LanCacheBlock.kt:58-77 with its early return at :88, the tokenRejected line at :105-110, TvLanCacheBlock.kt:46-92 with its early return at :55, TvQuietLine at :72, LanCacheBlockTest's show helper at :58-69, and testLanCacheViewModel's `tokenSettings` parameter at LanCacheViewModelTest.kt:80.
- Note for failure-handling-one-way step 6: the two new catches turn a failure into UI state and so fall under that step's logging rule. I added them to that step's list (see below) rather than here.
- Touch list: E feature/system/src/main/kotlin/LanCacheViewModel.kt, E ui-mobile/src/main/kotlin/ui/settings/LanCacheBlock.kt, E ui-tv/src/main/kotlin/ui/tv/system/TvLanCacheBlock.kt, E feature/system/src/test/kotlin/LanCacheViewModelTest.kt, E feature/system/src/test/kotlin/LanCacheViewModelValidationTest.kt, E ui-mobile/src/test/kotlin/ui/settings/LanCacheBlockTest.kt.

### Step 3: keep [trivial]
- LanCacheBlock.kt:71-74 verified. So is TV's gate at TvLanCachePanel.kt:52. ui-common has activity-compose only as testImplementation, so there is no reason to share the launcher.
- Touch list: E ui-mobile/src/main/kotlin/ui/settings/LanCacheBlock.kt.

### Step 4: keep [trivial]
- The three copies are at LanCacheViewModel.kt:30 (KDoc :26-29), LanCacheBlock.kt:44 and TvLanCacheBlock.kt:28; their uses at :163, :65 and :99. Verified.
- `android.Manifest.permission.ACCESS_LOCAL_NETWORK` exists in /home/andre/android-sdk/platforms/android-37.0/android.jar, and compileSdk is 37.
- Lint has no warningsAsErrors; app's lint uses a baseline only. A possible InlinedApi warning will not fail the build, and the step's fallback is fine.
- Touch list: E LanCacheViewModel.kt, E LanCacheBlock.kt, E TvLanCacheBlock.kt.

---

## 3. secret-stores-main-safe: **tighten** (steps 1 and 3)

The cluster is worth doing. Step 1 puts the dispatcher hop in the one place every caller goes through, and fixes four main-thread keystore paths at once. That is the root-cause fix, not four caller patches. Step 3 deletes a dead class and its tests.

### Step 1: tighten [medium]
- Verified:
  - TmdbSettings: class at :51, read :56, write :58, clear :62.
  - TelegramSettings: class at :81, read :86, write :89, clear :100.
  - LanCacheTokenSettings: class at :41, read :46, write :48, clear :52.
  - LibrarySettings: read :63 already uses withContext; write :70 and clear :75 do not.
  - DataModule: providers at :69-71, :75-77 and :87-89, :56-59 already passes the dispatcher, and the rule comment is at :45-49.
  - Callers: CatalogEnrichmentFetcher.kt:41, FetchViewModel init (calls refreshKeyStatus on viewModelScope), SetupViewModel.kt:144, LanCacheViewModel.kt:100 and :147.
  - Wrappers: Libraries.kt :39, :43, :45; SettingsViewModel.kt:88 (and :89 keeps dcId); CoreProvider.kt:178. CoreProvider :123-125, :149-153 and :254-255 wrap build/closeHeld and stay.
  - Libraries constructions: SetupFixture.kt:47/:50, MobileAppFixture.kt:50, SettingsProfileRetryTest.kt:83, TvAppFixture.kt:177. Nothing else constructs it.
  - No other caller-side wrapper exists (git grep).
- `withContext(NonCancellable)` around a store that itself does `withContext(dispatcher)` keeps the write non-cancellable, because the inner block inherits the NonCancellable job. The CoreProvider change is safe.
- **Test over-engineering:** "one JVM test per encrypted store (or parameterised)" invites four near-identical files. Collapse it to one test function in one new file, looping over the four stores and the three calls. The hop is plumbing; one regression check is enough.
- Touch list: E core/data/src/main/kotlin/settings/TmdbSettings.kt, TelegramSettings.kt, LanCacheTokenSettings.kt and LibrarySettings.kt; E core/data/src/main/kotlin/di/DataModule.kt; E feature/setup/src/main/kotlin/Libraries.kt; E feature/setup/src/main/kotlin/SettingsViewModel.kt; E core/data/src/main/kotlin/CoreProvider.kt; E feature/setup/src/test/kotlin/SetupFixture.kt; E ui-mobile/src/test/kotlin/ui/MobileAppFixture.kt; E ui-mobile/src/test/kotlin/ui/settings/SettingsProfileRetryTest.kt; E ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt; C core/data/src/test/kotlin/settings/EncryptedSettingsDispatchTest.kt.

### Step 2: keep [trivial]
- EncryptedPreferences.kt:8-17 verified ("The one way this app puts a secret on disk"). session.key is CoreStorage.kt:100, and data_extraction_rules.xml names it at :8 (in its comment; the rules exclude every domain).
- Touch list: E core/data/src/main/kotlin/settings/EncryptedPreferences.kt.

### Step 3: tighten [small]
- This is not absorbing the deferral. The skip note of `start_over_secret_coverage_uneven` says "The orphaned package_settings deletion is carried in cluster secret-stores-main-safe"; only the LAN-token half waits for the user.
- Verified:
  - PackageSettings.kt has no production reference (git grep).
  - fdda4863 is "pick a library from a list instead of pasting a URL and a key".
  - Stale comments: LanCacheTokenSettings.kt:7, LanCacheSettings.kt:11-12, app/build.gradle.kts:105-106 (MainActivity injects WatchSync, :40, and AppUpdater, :43), TmdbSettings.kt:40-42 and TelegramSettings.kt:70-72.
  - EncryptedSettingsTest.kt :31, :40, :57-79 and :128-145 (package parts at :132, :135, :139, :143).
  - CoreStorage.clear() is at :72-96 and DataModule.kt:98 is filesDir.
  - CoreStorageTest's `storage()` is at :23, the undeletable test at :89, and the "nothing yet" case at :47.
- **Missed references the step must also fix:**
  - **app/lint-baseline.xml:330-339 and :341-350.** Two UseKtx entries for `src/main/kotlin/settings/PackageSettings.kt`, lines 70 and 78. Once the file is deleted they are stale; remove both `<issue>` blocks.
  - core/data/src/androidTest/kotlin/settings/EncryptedSettingsTest.kt:18. Its class KDoc lists "the package key" among what the app stores; :29-30 say "the two stores".
  - core/data/src/main/kotlin/settings/LibrarySettings.kt:18 ("not a secret the way the package key was"). The reader can no longer find what "the package key" was.
- Tests: the "file absent" case is already exercised by every existing CoreStorageTest. They build on the TemporaryFolder root, whose parent has no `shared_prefs/package_settings.xml`, and clearingADirectoryThatHoldsNothingYetIsNotAFailure (:47) is the explicit one. The undeletable case uses the same check as its siblings. One new test (the file is removed) is enough.
- Touch list: E core/data/src/main/kotlin/CoreStorage.kt; D core/data/src/main/kotlin/settings/PackageSettings.kt; D core/data/src/test/kotlin/settings/PackageSettingsTest.kt; E core/data/src/androidTest/kotlin/settings/EncryptedSettingsTest.kt; E core/data/src/main/kotlin/settings/LanCacheTokenSettings.kt; E core/playback/src/main/kotlin/LanCacheSettings.kt; E core/data/src/main/kotlin/settings/TmdbSettings.kt, TelegramSettings.kt and LibrarySettings.kt (comments); E app/build.gradle.kts (comment); E app/lint-baseline.xml; E core/data/src/test/kotlin/CoreStorageTest.kt.

---

## 4. system-telegram-row-live: **tighten**

The cluster is worth doing. The row currently says "connected" for an offline device. That is a lie the web does not tell, so this is a Surface Parity defect.

### Step 1: tighten [small]
- **Is the probe the simplest honest fix? Yes, with three tightenings.**
  - The web reads its MTProto client's own `connected` flag (teleproto) at ../web/src/telegram/client.ts:156-167, which is now at that range (was :161-172). The core exposes no such flag, and adding one is a UniFFI surface change, which is deferred.
  - `is_authorized` (../crates/mediagram-core/src/api/mod.rs:91-94) only checks that session.key exists.
  - `account()` is a real getMe round trip (../crates/mediagram-core/src/api/account/profile.rs:24-26, through `account_with` and `revoked::checked_for`), and Settings already uses it as its liveness probe (SettingsViewModel.kt:86/:97).
  - The alternative, renaming the row's meaning to "signed in", would be honest without a network call. But it would drop the liveness question the web's row answers. So the probe it is.
- Tightenings:
  1. **No `async`/`coroutineScope`.** The probe needs `facts.origin`, so it can only start after catalogFacts(). The remaining reads (counters, CacheProvider.occupancy, refreshes, updater status) are local and take milliseconds, so concurrency buys nothing. Use a plain private suspend function called in sequence.
  2. **Name the constant's value (3 s) and say what the user will see.** snapshot() awaits the probe, so offline the whole System screen shows "reading…" until the timeout. That is a visible cost the device check should expect.
  3. **Device line:** do not switch the TV box's network off. adb reaches it over the network (192.168.0.35:5555), and the walks rule is navigate-only. Use airplane mode on the tablet; check only the online case on the box.
- Tests:
  - "the overall status line is 'needs attention'" duplicates SystemRowsTest.kt:177 (`systemStatus(facts().copy(connected = false))`). Assert `connected == false` only.
  - The hang knob (`accountGate`) is justified: it is the only way to pin the timeout.
- Side effect worth knowing (no action): `account_with` goes through `revoked::checked_for`, so a System visit on a revoked session now forgets the dead key, as a Settings visit already does.
- With failure-handling-one-way's orDefault: put it inside `withTimeoutOrNull`, never around it. orDefault rethrows CancellationException, which includes TimeoutCancellationException.
- Verified:
  - SystemViewModel.kt:118. snapshot() is at :92-129 and the once-per-subscription KDoc at :28-36.
  - SystemRows.kt:80 (telegramLine) and :213. SystemUiState.kt:37.
  - FakeCore: accountAnswer :81, accountFailure :83, `gate` :126, account() :407.
  - SystemViewModelTest prepareIoBoundaries sets "channel" at :50.
  - No existing System test depends on `authorized`.
- Touch list: E feature/system/src/main/kotlin/SystemViewModel.kt, E feature/system/src/test/kotlin/SystemViewModelTest.kt, E core/testing/src/main/kotlin/testing/FakeCore.kt.

---

## 5. failure-handling-one-way: **tighten** (steps 2, 4, 5, 6)

The cluster is worth doing. It deletes two byte-identical Safely.kt copies (diffed) and four hand-written catch-to-default helpers. It removes a cancellation-swallowing runCatching on a suspend call, a three-layer cancellation re-check, and raw exception text that reaches the screen.

### Step 1: keep [small]
- CoreErrors.kt is `package data`.
- Every caller module depends on core:data: core/playback:16, core/update:15, feature/catalog:13, feature/player:22, feature/setup:14, feature/stats:14, feature/system:15, ui-common:24, ui-mobile:21 and ui-tv:34. All verified.
- `isReturnDefaultValues` is at AndroidLibraryConventionPlugin.kt:52.
- Touch list: C core/data/src/main/kotlin/OrDefault.kt, C core/data/src/test/kotlin/OrDefaultTest.kt.

### Step 2: tighten [medium]
- Verified:
  - All 12 feature/player sites: EpisodeListFlow:53, AudioChoiceController:136, PlayerChoicesController :124, :137, :141, :145 and :193, SubtitleChoiceController :163 and :179, SubtitleStyleController:112, FramingController:56, di/ActivePlayback:107.
  - FilmWriteAttempt:73, and FilmPreloader's private safely at :322 (caller :143).
  - optionalRow at SettingsViewModel:271 (callers :86, :103, :227).
  - ProfileSettingsViewModel's private attempt at :68 (callers :59, :65).
  - PinAsk: attempt at :114 (callers PinAsk:93, ManageProfilesViewModel:134), reason() KDoc at :110, rereadQuietly at :130 (callers ProfileViewModel:213, ManageProfilesViewModel:78).
  - UpNextAsync :48-54 and writeState :322-331.
  - "~57 inline catches in 35 files" is exact: `git grep -c "catch (.*CancellationException)"` gives 57/35 outside the bindings.
- **Vagueness:** "ProfileSettingsViewModel ... -> orDefault(null, ...)" does not say whether `...` is a `what`. Step 6's rule ("a catch that turns a failure into UI state ... logs; through orDefault this means passing `what`") then contradicts step 2's silent PinAsk conversion: PinAsk's null becomes the visible DID_NOT_GO_THROUGH sentence. The corrected detail names every `what`. Silent sites are the ones that were silent and invisible before: the player's preference and catalog look-ups, FilmWriteAttempt's metered watch, rereadQuietly (its name and KDoc say why) and UpNextAsync.
- ProfileSettingsViewModel:59 `attempt { remember(...) } == true` becomes `orDefault(false, …) { remember(...) }` and drops the `== true`.
- Touch list: D core/playback/src/main/kotlin/Safely.kt; D feature/player/src/main/kotlin/Safely.kt; E feature/player/src/main/kotlin/EpisodeListFlow.kt, AudioChoiceController.kt, PlayerChoicesController.kt, SubtitleChoiceController.kt, SubtitleStyleController.kt, FramingController.kt, UpNextAsync.kt and di/ActivePlayback.kt; E core/playback/src/main/kotlin/FilmWriteAttempt.kt, LanWriteQueue.kt and FilmPreloader.kt; E feature/setup/src/main/kotlin/SettingsViewModel.kt and ProfileSettingsViewModel.kt; E feature/catalog/src/main/kotlin/profile/PinAsk.kt and ManageProfilesViewModel.kt; E feature/catalog/src/main/kotlin/CatalogViewModel.kt.

### Step 3: keep [trivial]
- Verified: CacheDataSourceWriter.kt:105, the comment at :102-104, the internal constructor at :50, LanServerLocator.kt:178-179, the non-suspend sites at LanServerLocator :101/:112, CacheVolumes :95-96, LanServerNetworkWatcher :56 and LanServerPicker :19, and ChunkMemo :78/:96. CacheErrorFallthroughTest.kt:95 builds a writer.
- core:playback has testImplementation(core:testing) at :33, so `CoreInterface by FakeCore()` works.
- Nit: `orDefault(Unit, "hold subtitles") { currentCore()?.holdSubtitles(...) }` infers T = Any? because the block answers Boolean?. It compiles, but `orDefault(null, "hold subtitles")` says what it means. The executor can take either; no edit is needed.
- The new test fails before the change: runCatching swallows the CancellationException and openCache runs. So it pins the change.
- Touch list: E core/playback/src/main/kotlin/CacheDataSourceWriter.kt, E core/playback/src/main/kotlin/LanServerLocator.kt, C core/playback/src/test/kotlin/CacheDataSourceWriterTest.kt.

### Step 4: tighten [trivial]
- Verified: LibraryUpdateCoordinator.kt:34-44 and the re-check at :44. CatalogRepository.refresh is at :126-138; the interface contract at :32 reads "returning its set count or an operational failure; cancellation is thrown".
- **The test is redundant.** "Refresh throws CancellationException, so update() propagates it and refreshing resets" passes before the change (the old catch rethrows) and after it. The `finally` that resets `reading` is untouched, and cancellationDuringRefreshDoesNotFetchAndReleasesTheNextUpdate (:104) already pins `refreshing` false after cancellation. A deletion of a redundant layer needs no new test; the existing suite covers it.
- Touch list: E core/data/src/main/kotlin/LibraryUpdateCoordinator.kt. LibraryUpdateCoordinatorTest.kt is no longer edited.

### Step 5: tighten [small]
- Verified:
  - SearchViewModel.kt:98-99; the TAG "search" exists at :104 and Log is imported.
  - SearchScreen.kt:142 and TvSearchResults.kt:79.
  - StatsViewModel.kt:84-85, StatsFormat.kt:82, AppUpdater.kt :116, :127 and :133, UpdateStatus.kt:34 and ApkInstaller.kt:28-31.
  - Test lines: SearchViewModelTest:118, StatsViewModelTest:137, AppUpdaterTest :96, :130, :140 and :191.
- Corrections:
  - The web reference moved: ../web/public/app.js:437, not :438. stats-page.js:35 is unchanged.
  - AppUpdater.kt:80 `dropReady(message ?: "Android refused the update ($status)")` passes PackageInstaller's status text, not a Throwable message. It is out of scope; say so, so nobody "fixes" it.
- **The 1ab59974 decision: yes, one assertion, and it costs nothing extra.**
  - Without a CoreException case, AppUpdaterTest would only exercise the new code's fallback branch, and `e.coreSentence()` would be untested.
  - Change aFailedDownloadSaysWhyAndInstallsNothing (:93/:96) to throw `CoreException.NotAuthorized("this device was signed out of Telegram")` from `core.downloadFailure` (FakeCore.kt:335, a Throwable) and assert `UpdateStatus.Failed("this device was signed out of Telegram")`.
  - That covers the coreSentence branch and pins the sentence the core answers since 1ab59974. updateLine's "last update failed: " prefix is already pinned by UpdateRulesTest.kt:45.
  - :191 keeps IllegalStateException and asserts the fallback "the update check did not finish".
- Commit line: the Search and Stats failure text visibly changes on both surfaces, and Search without a core sentence now reads "Search failed. Try again.". Say so in the commit message.
- Touch list: E feature/catalog/src/main/kotlin/SearchViewModel.kt, E ui-mobile/src/main/kotlin/ui/catalog/SearchScreen.kt, E ui-tv/src/main/kotlin/ui/tv/catalog/TvSearchResults.kt, E feature/stats/src/main/kotlin/StatsViewModel.kt, E core/update/src/main/kotlin/AppUpdater.kt, E feature/catalog/src/test/kotlin/SearchViewModelTest.kt, E feature/stats/src/test/kotlin/StatsViewModelTest.kt, E core/update/src/test/kotlin/AppUpdaterTest.kt.

### Step 6: tighten [medium]
- Verified:
  - The SetupViewModel catches at :161 and :199.
  - CatalogEnrichmentFetcher :45, :57, :85 and :103, with KEY_READ_ERROR's comment at :119. The TMDB key goes in the `api_key` query (../crates/mediagram-tmdb/src/tmdb_client.rs:32-33).
  - PlayerMarksController.kt:137.
  - Message-only logs: WatchSync :129, :158 and :184; AchievementDotViewModel:102; ProgressRecorder:42; SubtitleTrackSource:41; SummarySource:32; ReadAhead:117.
  - FilmPreloader :277, :292 and :328; SeriesPreloader:136; the `log` parameters at FilmPreloader:51 and SeriesPreloader:70; PreloadModule :113 and :164.
  - FilmPreloaderBlockingWriterTest:114 is the only test that passes `log`.
- Corrections:
  - **SettingsViewModel:** only :116 (loadSessions), :132 (revokeSession) and :258 (act) are terminal. :175 and :214 wrap the failure and rethrow it as SettingsFailure, and :188 folds the recovery failure into `e` via addSuppressed. All three reach `act`, so logging them would log each failure twice. The 7 count is right; the terminal ones are 3, plus optionalRow, whose sites get `what` in step 2.
  - **Widening `log` to `(String, Throwable?) -> Unit`** also breaks the three non-failure calls: FilmPreloader.kt:306, SeriesPreloader.kt:127 and :132. They pass `null`. The step does not say so.
  - **Missed sites under the same rule:**
    - SystemViewModel.kt:79-83, an unlogged catch that becomes UI state. It is the very pattern lan-cache-store-failures step 2 copies.
    - LanCacheViewModel's two new catches (snapshot and act), added by lan-cache-store-failures step 2.
  - **Files with no `android.util.Log` import or TAG yet:** SetupViewModel, SettingsViewModel, CatalogEnrichmentFetcher, SystemViewModel and LanCacheViewModel (AppUpdater is handled in step 5).
- Touch list: E feature/setup/src/main/kotlin/SetupViewModel.kt and SettingsViewModel.kt; E core/data/src/main/kotlin/CatalogEnrichmentFetcher.kt; E feature/player/src/main/kotlin/PlayerMarksController.kt; E core/data/src/main/kotlin/WatchSync.kt; E feature/stats/src/main/kotlin/AchievementDotViewModel.kt; E feature/player/src/main/kotlin/ProgressRecorder.kt; E core/playback/src/main/kotlin/SubtitleTrackSource.kt, SummarySource.kt, ReadAhead.kt, FilmPreloader.kt and SeriesPreloader.kt; E feature/player/src/main/kotlin/di/PreloadModule.kt; E core/playback/src/test/kotlin/FilmPreloaderBlockingWriterTest.kt; E feature/system/src/main/kotlin/SystemViewModel.kt and LanCacheViewModel.kt.
- **New ordering note:** step 6 now also edits SystemViewModel.kt, which system-telegram-row-live edits too, at a different place (the catch at :79 rather than snapshot :118). Either add system-telegram-row-live to depends_on, or accept a trivial rebase.

---

## 6. parity-fixtures-and-test-gates: **tighten** (steps 3 and 4). The FakeWatchState step is not added (the lead does it)

The cluster is worth doing. Step 1 deletes six private locators and makes a silent skip loud. Step 2 is one line. Step 3 deletes three drifted fake copies, three MainDispatcherRule copies and two legacy aliases. Step 4 turns sleeps into waits.

### Step 1: keep [small]
- All six assumeTrue lines and locators are verified: NextUp :36/:118, CategoryRows :25/:50, EditorialPicks :32/:112, SubtitleChoice :27/:72, ResumePoint :29/:107, AchievementLabels :24/:38.
- All four modules have testImplementation(core:testing): catalog:24, player:38, stats:17, core/data:21. core:testing has kotlin.test.junit as `api` (:23).
- All six fixtures still exist under ../web/test/fixtures.
- Touch list: C core/testing/src/main/kotlin/testing/WebFixture.kt; E feature/catalog/src/test/kotlin/NextUpFixtureTest.kt, CategoryRowsFixtureTest.kt and EditorialPicksFixtureTest.kt; E feature/player/src/test/kotlin/SubtitleChoiceTest.kt; E core/data/src/test/kotlin/ResumePointFixtureTest.kt; E feature/stats/src/test/kotlin/AchievementLabelsFixtureTest.kt.

### Step 2: keep [trivial]
- ../scripts/check.sh:47 and the comment at :43-46 are verified.
- verifyNativeCore hooks only `merge*NativeLibs`/`merge*JniLibFolders` (core/rust/build.gradle.kts:76-78), so compileDebugAndroidTestKotlin should not need the .so. The step's run-by-hand precondition stays.
- Touch list: E ../scripts/check.sh.

### Step 3: tighten [medium]
- Verified:
  - FakePreload.kt has 141 lines, with classes at :23, :40, :50, :116 and :124.
  - The copies: ui-mobile at :26, :69 and :77 (94 lines, the three fakes only); ui-tv at :24, :67 and :74 (91 lines, the same); the catalog private copy at CatalogViewModelTest.kt:64, with emitHeld/emitUnheld at :80-81 used at :893/:897.
  - The importers: exactly the nine player tests named, plus LibraryFlowFixture and TvAppFixture.
  - MainDispatcherRule: four copies identical except the package line. Users are 4, 8, 2 and 2.
  - The aliases are at FakeCoreProvider.kt:62/:65, with 110 calls in 21 files (data 10, catalog 6, stats 2, update 1, setup 1, system 1).
- **Over-engineering:** test fixtures on core:playback add a new build mechanism (testFixtures plus possibly the Kotlin-support flag under AGP 9.3.1) and edits to four consumer builds. It also forces renaming FilmPreloaderTest's private FakeHeldSets.
  - The step's own "fallback" is the existing convention: core:testing is the project's shared-fakes module (FakeCore, FakeCoreProvider and the others live there), and all four consumers already have testImplementation(core:testing).
  - It needs one line, `implementation(project(":core:playback"))`, in core/testing/build.gradle.kts and no consumer build edit.
  - The fakes land in package `testing`, so FilmPreloaderTest's `playback`-package private FakeHeldSets does not clash, nothing imports `testing.*`, and the rename goes.
  - It creates no project cycle: it is the same shape core:data's tests already have (core:data test → core:testing → core:data).
  - Make core:testing the primary route.
- **Imports:** five tests in subpackages import the old copies explicitly and need the import *replaced*, not added: `import catalog.MainDispatcherRule` in catalog/profile FirstProfileWaitsForASyncRoundTest, ManageProfilesViewModelTest, ProfileOwnershipTest and ProfilePinTest, and `import setup.MainDispatcherRule` in setup/login LoginViewModelTest.
- Touch list:
  - Build: E core/testing/build.gradle.kts.
  - Preload fakes: M feature/player/src/test/kotlin/FakePreload.kt → core/testing/src/main/kotlin/testing/FakePreload.kt; D ui-mobile/src/test/kotlin/ui/TitlePreloadFixtures.kt; D ui-tv/src/test/kotlin/ui/tv/TitlePreloadFixtures.kt; E feature/catalog/src/test/kotlin/CatalogViewModelTest.kt; E ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt; E ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt; E feature/player/src/test/kotlin/ PlayerActionFailureTest.kt, PlayerChoicesResetTest.kt, PlayerHeldWiringTest.kt, PlayerMarksTest.kt, PlayerPreloadWiringTest.kt, PlayerViewModelTestFixture.kt, PreloadServiceTest.kt, TestPlayerViewModel.kt and TitlePreloadViewModelTest.kt.
  - MainDispatcherRule: M feature/catalog/src/test/kotlin/MainDispatcherRule.kt → core/testing/src/main/kotlin/testing/MainDispatcherRule.kt; D feature/setup, feature/stats and feature/system src/test/kotlin/MainDispatcherRule.kt; E the 16 users:
    - feature/catalog/src/test/kotlin/profile: FirstProfileWaitsForASyncRoundTest, ManageProfilesViewModelTest, ProfileOwnershipTest and ProfilePinTest.
    - feature/setup/src/test/kotlin: ProfileResetTest, ProfileSettingsViewModelTest, SettingsIdentityFailureTest, SettingsViewModelTest, SettingsWatchOwnershipTest, SetupRecoveryTest, SetupViewModelTest and login/LoginViewModelTest.
    - feature/stats/src/test/kotlin: AchievementDotViewModelTest and StatsViewModelTest.
    - feature/system/src/test/kotlin: CatalogEnrichmentPersistenceTest and FetchViewModelTest.
  - Aliases: E core/testing/src/main/kotlin/testing/FakeCoreProvider.kt; E the 21 alias users:
    - core/data/src/test/kotlin: CatalogRepositoryTest, LibraryEventsLifecycleTest, LibraryEventsTest, PlayerPreferencesTest, RefreshLogTest, WatchStateChosenProfileTest, WatchStateLocalDayTest, WatchStateOwnershipTest, WatchStateRepositoryTest and WatchSyncTest.
    - core/update/src/test/kotlin/AppUpdaterTest.
    - feature/catalog/src/test/kotlin: CatalogViewModelTest, LibraryUpdateCoordinatorTest, and in profile/ FirstProfileWaitsForASyncRoundTest, ManageProfilesViewModelTest, ProfileOwnershipTest and ProfilePinTest.
    - feature/setup/src/test/kotlin/login/LoginViewModelTest.
    - feature/stats/src/test/kotlin: AchievementDotViewModelTest and StatsViewModelTest.
    - feature/system/src/test/kotlin/OffUpdater.

### Step 4: tighten [small]
- Verified:
  - The file has 396 lines.
  - Mid-write sleeps: :131 (100), :240 (150), :290 (50) and :315 (150). Each is the first item through its writer, so a per-writer byte counter is unambiguous.
  - Negative-window sleeps at :158 and :185.
  - Exactly 13 bare waits: :130, :152, :155, :179, :182, :218, :239, :289, :314, :339, :341, :368 and :370.
  - `started`/`completed` at :38-39, mutated at :49/:58.
- **Drop the :221 bullet.** enqueue sets Queued synchronously (FilmPreloader.kt:110), so `assertTrue(waitUntil { … is Queued })` right after `enqueue` is true on its first poll. It asserts nothing and replaces nothing; the 50 ms sleep it sits before stays anyway (enrich note 4). Leave :221 exactly as it is.
- Touch list: E core/playback/src/test/kotlin/FilmPreloaderBlockingWriterTest.kt.

---

## 7. profile-rules-one-source: **tighten** (step 1)

The cluster is worth doing, but step 1 should copy the web's decision, not re-decide it.

### Step 1: tighten [small]
- **Surface Parity: the web, the reference, does not derive its panel from its rule.**
  - ../web/public/lib/profile-api.js:96-103 `manageable` is hand-coded:
    - `grownUps: actor?.admin ? profiles.filter(!kids && id !== actorId) : []`
    - `kids: actor ? profiles.filter(kids && ownerOf(...) === actorId) : []`
  - That is line for line Android's ManageUiState.kt:46-52, and `canAddGrownUp = actor.admin` (:31) matches the web's own `create-grown-up → actor.admin`.
  - The web's answer to "two copies that can drift" is the `offered()` check over profile-rules.json (../web/test/profile-api.test.ts:123-143).
  - Deriving Android's lists from `allowed()` would be an Android-only re-decision. It would also change 15 test constructions (ManageProfilesScreenTest 6, TvManageProfilesStateTest 9, verified) and turn `canAddGrownUp` into a constructor field, for no visible difference.
- I simulated the web's `offered()` over today's Android `manageable`: all 42 fixture cases pass (0 failures). The port is test-only.
- The remaining true part of the issue: ProfileRoles.kt:32-37's KDoc claims "a screen asks it only to offer what the core will allow". No screen does; the only non-test caller is core:testing's FakeProfiles. Reword it to what is true.
- Corrected step: add ManageOffersFixtureTest and reword the KDoc. Do not touch ManageUiState. The title changes (an --update-title is in the edits script). The cluster description should then read something like "Hold Manage profiles' panel to profile-rules.json with a port of the web's offered() check, as the web does, and make ProfileViewModel.choose() internal". The lead can set it with `--description`.
- If the lead keeps the derivation anyway, the original step's lines are all correct (ManageUiState.kt :31, :49-50, ProfileRoles.kt:38-55, ProfileRolesFixtureTest.kt:44-53, 6 + 9 = 15 constructions), and its touch list adds E feature/catalog/src/main/kotlin/profile/ManageUiState.kt, E ui-mobile/src/test/kotlin/ui/profile/ManageProfilesScreenTest.kt and E ui-tv/src/test/kotlin/ui/tv/profile/TvManageProfilesStateTest.kt.
- Touch list (tightened): C feature/catalog/src/test/kotlin/profile/ManageOffersFixtureTest.kt, E core/model/src/main/kotlin/ProfileRoles.kt.

### Step 2: keep [trivial]
- ProfileViewModel.kt:177 `fun choose` and pick at :131 are verified. The only callers of the ProfileViewModel `choose` are ProfileViewModelTest (:112, :148, :163, :230) and ProfileOwnershipTest (:80, :102, :105, :106). No `::choose` reference is to this class: the two hits are ShelfViewModel's.
- Touch list: E feature/catalog/src/main/kotlin/profile/ProfileViewModel.kt.

---

## Cross-cluster overlaps (same file, more than one cluster)
- CatalogViewModel.kt: catalog-mark 1, 2 → failure-handling 2. Declared.
- CatalogViewModelTest.kt: catalog-mark 1, 2 → parity 3. Declared.
- PlayerMarksController.kt: catalog-mark 3 → failure-handling 6. Declared.
- PlayerMarksTest.kt: catalog-mark 3 → parity 3. Declared.
- LanWriteQueue.kt: lan-cache 1 → failure-handling 2. Declared.
- LanCacheViewModel.kt: lan-cache 2, 4 → failure-handling 6 (my addition). Declared.
- SettingsViewModel.kt: secret-stores 1 → failure-handling 2, 6. Declared.
- TvAppFixture.kt: secret-stores 1 and parity 3. **Not declared**: parity does not depend on secret-stores. The edits are trivial and in different places.
- FakeCore.kt: system-telegram 1, and possibly the lead's FakeWatchState/CoreContract step in parity.
- SystemViewModel.kt: system-telegram 1 and failure-handling 6 (my addition). **Not declared**; see step 6.
- AppUpdaterTest.kt and StatsViewModelTest.kt: failure-handling 5 and parity 3 (aliases, MainDispatcherRule). **Not declared** either way; a mechanical rebase.
- FilmPreloaderBlockingWriterTest.kt: parity 4 and failure-handling 6 (`log` lambda at :114). **Not declared**; a one-line rebase.
- LanCacheTokenSettings.kt, TmdbSettings.kt, TelegramSettings.kt: secret-stores 1 and 3 (same cluster, sequential).
