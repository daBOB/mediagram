# SENSE-CHECK report: Android app (19 clusters, 69 steps, 100 live review issues)

## How this was checked
Source was re-read at the worktree /home/andre/Workspace/mediagram-desloppify. Its Android tree is byte-identical to 31cb8561, the commit enrich verified: `git diff 31cb8561..HEAD -- android scripts` is empty. The web player has moved since (63 files), so every `../web` line reference was rechecked.

The work was split across three verifiers, run at most two at a time, each with exclusive clusters:
- Verifier A: catalog-mark, lan-cache, secret-stores, system-telegram, failure-handling, parity-fixtures, profile-rules.
- Verifier B: typed-department, typed-navigation, tv-focus, shared-surface, process-globals.
- The lead: dead-aliases, playback-bindings, player-and-preloader, build-deadwood, catalog-records, comments-to-invariants, single-package-move-pass.

Every step was checked in order: over-engineering first, then staleness, line numbers, names, counts, vagueness, effort and duplicates. The verifiers only proposed changes. The lead reviewed every one and applied it through `desloppify plan cluster update`. Every applied detail was validated before it was applied: at least 80 characters, every path-like token resolving from android/, and the refs carried over from the step.

An earlier sense-check run had been cut off. Its edits were checked first: plan.json showed no cluster updated after enrich's 14:40 timestamp, so this run started from enrich's plan.

No source file was modified, Gradle was not run, and no deferred user decision was reversed or absorbed: ktlint, secret storage, LAN pairing, the m3-adaptive pin, PlayerViewModel size, the UniFFI surface and the Start-over LAN token.

The two core commits that landed after enrich:
- 41ab9392 (on this branch: core refuses to forget a synced preference). Android production code never forgets a synced name. The writers were checked: feature/player/src/main/kotlin/SubtitleChoiceController.kt:179, feature/setup/src/main/kotlin/ProfileSettingsViewModel.kt:59 and feature/player/src/main/kotlin/SubtitleStyleController.kt:102-106 all write a value, "off" included. Only the test fake core/testing/src/main/kotlin/testing/FakeWatchState.kt:270-275 diverges. No cluster covered it, so parity-fixtures-and-test-gates gains step 5 (below).
- 1ab59974 (on desloppify/rust-exec, not merged yet: resolving an app release or a subtitle bundle answers NotAuthorized("this device was signed out of Telegram") and forgets the key on a 401). The Android update path renders it through coreSentence() as "last update failed: this device was signed out of Telegram". failure-handling-one-way step 5 now pins exactly that in core/update/src/test/kotlin/AppUpdaterTest.kt. No other cluster touches the subtitle-bundle failure path. A System visit on a revoked session already forgets the key through account().

## Content check, per cluster
### catalog-mark-writes-guarded (verifier A) -> tighten
- Steps 1 and 3 hold as written: the bare launches at feature/catalog/src/main/kotlin/CatalogViewModel.kt:93/:101 beside writeState (:317-339), and the repeated clause at feature/player/src/main/kotlin/PlayerMarksController.kt:93.
- Step 2 drops the planned `Entry.Collection` overloads. They would repeat the first-episode rule rather than move it. Both screens must keep firstEpisodeId anyway, because it decides whether a show's editor's-choice control exists at all (ui-mobile/src/main/kotlin/ui/LibraryTitleBranches.kt:99, ui-tv/src/main/kotlin/ui/tv/TvLibraryCatalogFrames.kt:157).
  - The toggles now take a setId, which is what the issue itself suggests.
  - LibraryFlowBranches.kt:245 and TvLibraryBranches.kt:198 are no longer edited.

### lan-cache-store-failures (verifier A) -> tighten
- Step 1 corrections:
  - The failure is a GeneralSecurityException, not a SecurityException.
  - The why-comment at core/playback/src/main/kotlin/LanWriteQueue.kt:90-92 is kept.
  - The sharing-switch test is dropped: it passes before and after the change.
- The write-gate half does not absorb the deferred LAN-pairing decision. That decision's skip note explicitly hands the write half to this cluster.
- `android.Manifest.permission.ACCESS_LOCAL_NETWORK` was confirmed in the android-37 platform jar.

### secret-stores-main-safe (verifier A) -> tighten
- Step 1:
  - The four near-identical dispatch tests collapse into one test over the four stores.
  - `withContext(NonCancellable)` in core/data/src/main/kotlin/CoreProvider.kt still holds, because the store's own hop inherits the NonCancellable job.
- Step 3 gains three stale references it had missed:
  - the two PackageSettings.kt entries in app/lint-baseline.xml:330-350
  - core/data/src/androidTest/kotlin/settings/EncryptedSettingsTest.kt:18
  - core/data/src/main/kotlin/settings/LibrarySettings.kt:18
- Only one new CoreStorage test is needed.

### failure-handling-one-way (verifier A) -> tighten; priority 5 -> 4
- Step 2 now names the `what` at every converted site. Before, its silent PinAsk conversion contradicted step 6's logging rule: PinAsk's null becomes the visible "did not go through" sentence, so it must log.
- Step 4's new test passed before and after the deletion; it is dropped.
- Step 5:
  - The web line is now `../web/public/app.js:437`.
  - Commit 1ab59974 gets one assertion. aFailedDownloadSaysWhyAndInstallsNothing (core/update/src/test/kotlin/AppUpdaterTest.kt:93/:96) throws `CoreException.NotAuthorized("this device was signed out of Telegram")` and asserts that reason. This is also the only test of the coreSentence branch.
  - The commit says the Search/Stats failure text changes on both surfaces.
- Step 6:
  - Only SettingsViewModel :116, :132 and :258 are terminal. :175, :188 and :214 rethrow into `act` and would log twice.
  - Three non-failure `log(...)` calls need `null`.
  - Two missed sites are added: feature/system/src/main/kotlin/SystemViewModel.kt:79 and LanCacheViewModel's new catches.

### system-telegram-row-live (verifier A + lead) -> tighten; priority 4 -> 5, now after failure-handling-one-way
- A timed account() probe is the simplest honest fix:
  - The web reads its MTProto client's `connected` flag at `../web/src/telegram/client.ts:156-167` (it was :161-172).
  - The core exposes no such flag, and adding one is the deferred UniFFI change.
  - feature/setup/src/main/kotlin/SettingsViewModel.kt:86 already uses this probe.
- The probe runs in sequence, not with async. The timeout is named (3 s), and the screen's "reading…" for that long offline is stated.
- The duplicate "needs attention" assertion (SystemRowsTest.kt:177) is dropped.
- Device check: airplane mode on the tablet only. The TV box's network must never be cut, because adb reaches it over the network.
- Lead change: the probe is `withTimeoutOrNull(TELEGRAM_PROBE_MS) { orDefault(false, "Telegram probe") { core.account(); true } } ?: false`. The cluster now depends on failure-handling-one-way, so it uses the shared helper instead of a new hand-written cancellation catch, which is the rework loop the strategy names.
- account() goes through revoked::checked_for, so a System visit on a revoked session forgets the dead key, as a Settings visit already does. No action needed.

### parity-fixtures-and-test-gates (verifier A + lead) -> tighten; one step added
- Steps 1-2 hold. All six assumeTrue sites and fixtures exist, and verifyNativeCore hooks only the merge tasks.
- Step 3: the shared preload/LAN fakes go into core:testing, the project's existing shared-fakes module, with one `implementation(project(":core:playback"))` line in core/testing/build.gradle.kts. This replaces testFixtures on core:playback, four consumer build edits and the FilmPreloaderTest rename.
  - The import cascade now names the five subpackage tests whose MainDispatcherRule import must be replaced, not added.
- Step 4: the `:221` Queued assertion is dropped. enqueue sets Queued synchronously (core/playback/src/main/kotlin/FilmPreloader.kt:110), so the wait passes on its first poll.
- Lead step 5, added: FakeWatchState refuses to forget a synced preference, as the core now does, and the contract suite pins it.
  - Ref: instrumented_contract_suites_never_gated (non-deferred). The contract suite is what holds the fake to the real core.
  - Commit 41ab9392 made `preferences::set` refuse to forget a SYNCED_NAMES row: subtitle, cue-size, cue-backing and cue-offset (`../crates/mediagram-core/src/state/record/preference_record.rs:15`).
  - core/testing/src/main/kotlin/testing/FakeWatchState.kt:270-275 still deletes it and answers true.
  - core/testing/src/main/kotlin/testing/CoreContract.kt:602/:620 only forget device-only names.
  - The step aligns the fake and adds one CoreContract case. FakeCoreContractTest runs that case on the JVM, and RealCoreContractTest runs it on the tablet against a rebuilt .so.
  - Android production needs no change. I checked every writer, and none forgets a synced name: SubtitleChoiceController.kt:179, ProfileSettingsViewModel.kt:59 and SubtitleStyleController.kt:102-106 all write a value.

### profile-rules-one-source (verifier A, accepted by the lead) -> tighten
- Step 1 now follows the web instead of re-deciding (CLAUDE.md Surface Parity).
  - The web hand-codes its `manageable` (`../web/public/lib/profile-api.js:96-103`) line for line as Android's feature/catalog/src/main/kotlin/profile/ManageUiState.kt:41-53 does.
  - It holds that panel to profile-rules.json with an offered() test (`../web/test/profile-api.test.ts:123-143`).
- Android gets the same: a ManageOffersFixtureTest plus a true KDoc in core/model/src/main/kotlin/ProfileRoles.kt:32-37.
- Deriving the panel from allowed() would have been an Android-only decision touching 15 test constructions. All 42 fixture cases pass against today's code.
- This reverses no user decision: the derivation was a triage proposal, never a user choice.
- The cluster now depends on parity-fixtures-and-test-gates, for webFixture().
### typed-department-and-tab-identity (verifier B) -> tighten
- The 8 -> 6 change is confirmed against the web. `../web/public/lib/catalog/home-shelves.js:64-66` cuts latestCourses to `limit`, and Home calls it with the defaults: limit 6 (`:18`) and posterLimit 8 (`:21`), at `../web/public/app.js:139`. Android passes HOME_POSTER_ROW_LIMIT (8) to every Latest row: ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt:195 and ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogScreen.kt:188.
  - Step 4 now labels this a VISIBLE CHANGE that follows the web.
  - The commit message must say so.
  - The user checks it on the tablet and the TV box.
- Step 3 no longer retypes HomeRow.seeAll. Its only production reader is ui-tv/src/main/kotlin/ui/tv/catalog/TvLatestPage.kt:56, which step 4 deletes. So the TvLatestPage bridge edit is gone, and steps 3 and 4 are no longer order-dependent. This replaces enrich's note 3.
- Step 4 (small -> medium):
  - It also deletes setCard (dead; its KDoc at feature/catalog/src/main/kotlin/HomeShelves.kt:127-137 is false), HomeRow and RowContent.
  - MagazineHome.recentlyAddedRow, read only for `.total`, becomes `recentlyAddedTotal: Int`.
- Step 2:
  - The test count is 64 constructions in 18 files, not about 53.
  - The phone's unreachable `else -> ShelfWall` branch and the shelfView values it leaves unused (CatalogScreen.kt:157-159) must go.
  - A test that compared the enum with the same hard-coded strings was dropped.
- Step 1 now owns the whole MastheadSplit KDoc (feature/catalog/src/main/kotlin/CatalogTabs.kt:32-42). The duplicate bullets in comments-to-invariants were removed.

### typed-navigation-ids (verifier B) -> tighten
- The 12 openers, the 4 readers and the web lines (`../web/public/lib/address.js:63/:70/:97/:105`) are exact.
- Verify is unconditional: the dependency chain has already widened the gate.
- The readers need no edit.
- One test that was true by construction is dropped.
- Only two fixtures (SearchCollectionsTest.kt:33, TvSearchResultsStateTest.kt:174) gain `franchiseId`.

### tv-focus-targets-and-effects (verifier B) -> tighten
- Step 1 (small -> medium): new JVM cases that duplicate TvDepartmentTargetsTest/TvHomeTargetsTest are dropped. ui-tv/src/main/kotlin/ui/tv/catalog/TvHome.kt:123 and a Home walk are added.
- Step 2: the guards read `in included`. The proposed focus tests already pass before the fix, so they are replaced by an item-count assertion (`listState.layoutInfo.totalItemsCount`), which fails before it.
- Step 3 (small -> trivial): a local `when` replaces a new railRestoreOf function and its test.
- Step 4 (small -> medium):
  - All 27 own-requester vals were checked safe to convert.
  - rememberStableRequester is appended at the end of TvFocus.kt, so process-globals' line references there hold.
- Step 5: the device walk is dropped. Named arguments in the same order change no value.
- Device wording everywhere:
  - OK only on plates or rail rows.
  - Only Back inside Settings and System.
  - Never Back at the library root.

### shared-surface-glue-in-ui-common (verifier B) -> tighten
- Only byte-identical non-visual glue moves. ui-mobile/src/main/kotlin/ui/TitlePreloadWiring.kt:42-69 is the same code as ui-tv/src/main/kotlin/ui/tv/TvFilmPreloadWiring.kt:33-60. No visual twin is merged.
- Step 1's new Robolectric test is dropped, because FilmPreloadFlowTest already covers it. The moved KDocs must not link phone-only symbols.
- Step 3 nets about -40 lines, and its proposed cancellation test already exists (RememberLookupTest.kt:56-67).
- The Preload device check is look-only, because pressing it enqueues a download.

### process-globals-to-owned-state (verifier B) -> tighten
- Step 2 is simplified, not skipped (it stays medium). The planned "while in flight, a second card waits" was new behaviour nobody asked for.
  - The log's `asked` set always equals RememberPortrait's attemptedPortraitFetches: core/data/src/main/kotlin/PortraitRequestLog.kt:25 and ui-common/src/main/kotlin/ui/catalog/RememberPortrait.kt:55.
  - So the step deletes the reservation and turns it into a plain `needsFetch` query.
  - It threads the log itself through the same 17 files, with behaviour unchanged.
  - The step title was updated to match.
- Step 1: TV has one MaterialTheme, so `colorScheme.primary` is exactly the old accent. The imports to add and drop are listed.
- Step 3: the either/or choices are decided: keep a one-line onPictureInPictureModeChanged override, and move isPipDismissal into PipController.kt.

### dead-aliases-and-pass-throughs (lead) -> keep
Every step was re-read and holds:
- feature/catalog/src/main/kotlin/HomeShelves.kt:139-145 and feature/catalog/src/main/kotlin/AllSetsIndex.kt (13 lines).
- 5 in-module indexById call sites.
- feature/catalog/src/main/kotlin/NextUp.kt:148-155, with no caller anywhere. The web's nextInQueue is at `../web/public/lib/library.js:231`.
- feature/system/src/main/kotlin/FetchUiState.kt and its 4 parameters.
- The positional `Shelves(...)` call at ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt:118-122.
- The pass-through TvCatalogRoot at ui-tv/src/main/kotlin/ui/tv/TvLibraryBranches.kt:25-82, whose parameters and defaults match TvCatalogScreen.kt:75-98.
- The rollout KDocs at TitleDetailScreen.kt:47-50 and CollectionScreen.kt:37-40.

The cluster deletes 2 files and about 60 lines. Its only change is structural (see Structure).

### playback-bindings-out-of-feature-player (lead) -> tighten
- Step 2:
  - The new qualifier is named MainThreadScope, not MainScope. `MainScope` shadows kotlinx.coroutines.MainScope(), and a file importing both would not compile.
  - DefaultPlayerHandle's `@Inject` (feature/player/src/main/kotlin/DefaultPlayerHandle.kt:14/:35) is deleted rather than qualified. providePlayerHandle (PlaybackModule.kt:86-89) builds it by hand, and nothing injects it.
- Step 3:
  - ActivePlayback.kt's `import player.safely` will already be gone; failure-handling-one-way, a dependency, replaced it.
  - The three "same reason di/ActivePlayback.kt exists" sentences (TitlePreloadWiring.kt:19-20, TvFilmPreloadWiring.kt:19-21, TvPreloadsFrame.kt:21-23) are now deleted here instead of repointed. comments-to-invariants step 4 would otherwise delete them a second time.
- Verified: LanCacheModule's bindings (:35/:41/:45/:49/:67/:77), its stale PlaybackModule pointers (:70/:87), PlaybackModule.kt:55-61/:108-110 and the 7 scope consumers.

### player-and-preloader-contracts-in-code (lead) -> tighten; one member skipped
- Step 3 (FilmPreloader limitedParallelism(1)) was removed, and film_preloader_single_thread_precondition was permanently skipped.
  - This is new evidence, not just an audit opinion: I read the kotlinx-coroutines-test 1.11.0 bytecode. UnconfinedTestDispatcherImpl.dispatch throws UnsupportedOperationException outside yield, and a limitedParallelism view always dispatches.
  - So the code change is known to break core/playback/src/test/kotlin/FilmPreloaderTest.kt:80/:306/:372.
  - The step's own fallback was a constructor KDoc line, which is the docs-as-fix loop. The rule is already stated where it is relied on: core/playback/src/main/kotlin/FilmPreloader.kt:63 and :124-127.
- Step 2: the five new round-trip tests are dropped. A test that writes and reads through the same constant can never fail. The existing tests already seed the literal persisted names:
  - AudioChoiceControllerTest.kt:128
  - FramingControllerTest.kt:25
  - PlayerChoicesResetTest.kt:59
  - SubtitleChoiceControllerTest.kt:112
  - SubtitleStyleControllerTest.kt:47/:57/:70
- The PlayerPreferences KDoc sentence ("[remember] returns false when the core wrote nothing") also covers 41ab9392's refusal.
- Steps 1 and 4 were verified as written: PlayerHandle.kt:35-36, DefaultPlayerHandle.kt:131-136/:148-150 (setListener(null) is equivalent), PlayerViewModel.kt:198, and the three file names.

### build-deadwood (lead) -> tighten
- I recomputed the counts with a script over every *.gradle.kts and build-logic *.kt file. The step's numbers are exact:
  - 39 unreferenced libraries
  - 3 bundles (gradle/libs.versions.toml:191/:197/:211)
  - 27 orphaned version keys
- Added the app-android-lint alias (:237). Enrich called it out of scope, but it is dead for the same reason as android-test: the conventions apply "app.android.lint" by id (AndroidApplicationConventionPlugin.kt:19, AndroidLibraryConventionPlugin.kt:19, JvmLibraryConventionPlugin.kt:16).
- Step 2 (the media3 comment at :50) is unchanged.

### catalog-records-mirrored-in-core-model (lead) -> tighten
- Step 4 now states the rule, not an inventory of record names in core/data/build.gradle.kts. An inventory would drift the first time a repository method is added.
- Counts verified: 15 TitleInfo main importers, 2 SearchHit importers, 18 test files.
- The cited lines hold: core/data/src/main/kotlin/CatalogRepository.kt:21/:24/:43/:51/:149/:152/:257/:259, ui-tv/build.gradle.kts:34-39 and ui-common/build.gradle.kts:19-24.
- Steps 1-2 still wait for the deferred UniFFI-reach decision. That deferral is untouched.

### comments-to-invariants (lead) -> tighten
- Removed the two CatalogTabs.kt bullets, which duplicated typed-department step 1.
- Step 3 gives the TvWall KDoc (ui-tv/src/main/kotlin/ui/tv/catalog/TvWall.kt:52-90) a concrete target: five facts, each with its reason. Before, it only said "shorten".
- Steps 1-3 say to find text, not lines, because the clusters this one depends on move them.
- Step 4 counts were re-run: 31 main files, 20/8/2/1 by module, and 7 tests. The core/testing hit is core/testing/src/main/kotlin/testing/FakeWatchState.kt:26.
- These were verified as written: core/model/src/main/kotlin/ByteSize.kt:28-32, MlibDataSource.kt:106-107, feature/system/build.gradle.kts:1-4/:17 (8 ui-tv importers, 14 playback symbols) and core/playback/src/main/kotlin/OpenTitleSource.kt:17-19 against FilmPreloader.kt:240/:254 and FilmWriteAttempt.kt:67.

### single-package-move-pass (lead) -> tighten
- Step 1: the three renames name their call sites unambiguously (TvSettingsScreen.kt:146/:156/:165, TvAppearanceBlockTest.kt:40).
- Step 3: TvSafeArea goes to ui-tv/src/main/kotlin/ui/tv/TvTheme.kt, not TvFocus.kt. TvFocus.kt is the focus-treatment file, and tv-focus step 4 already grows it to about 193 lines.
- Step 4: Destination lands in ui-mobile's root `ui` package, where 6 of its 7 importers live. The mapping is a named `internal fun destinationOf(screen)`, replacing the earlier "private when ... make it internal" contradiction.
- Verified: 17 files in ui.tv.system, 14 `package settings` files, 47 importers, and 30 ui-common main files in 7 packages.

## Structure check
I built a file-touch graph from every step's edited files, using the verifiers' C/E/D/M touch lists plus my own. I recomputed it after all edits with a script over the stored step texts. Cited-only files are not counted as edits (for example ../scripts/build-android-core.sh, or CatalogRepository.kt:27-29 cited as wording).

Dependencies added, each from a real shared edit:
- dead-aliases-and-pass-throughs now depends on catalog-mark-writes-guarded and failure-handling-one-way. All three edit feature/catalog/src/main/kotlin/CatalogViewModel.kt: the toggles, writeState's catch, and the :245 indexById rename.
- parity-fixtures-and-test-gates now also depends on secret-stores-main-safe and failure-handling-one-way. It edits ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt (secret-stores step 1 does too), core/update/src/test/kotlin/AppUpdaterTest.kt and feature/stats/src/test/kotlin/StatsViewModelTest.kt (failure-handling step 5 does too), and core/playback/src/test/kotlin/FilmPreloaderBlockingWriterTest.kt (failure-handling step 6 does too).
- system-telegram-row-live now depends on failure-handling-one-way. Both edit feature/system/src/main/kotlin/SystemViewModel.kt, at :79 and :118, and the probe uses orDefault. The priorities were swapped (failure-handling 4, system-telegram 5), so no cluster depends on a later-priority one.
- profile-rules-one-source now depends on parity-fixtures-and-test-gates, because the new fixture test uses webFixture().
- catalog-records-mirrored-in-core-model now also depends on process-globals-to-owned-state. Both edit TitleDetailScreen.kt, CollectionScreen.kt, TvTitlePage.kt, TvCollection.kt and TvSeriesPage.kt, and step 2's ui-common core:data decision depends on process-globals step 2 threading PortraitRequestLog.

Results:
- No cycles.
- No dependency on a later-priority cluster.
- No remaining shared edited file without a transitive dependency.

Cascades:
- Every rename and removal names its importers: indexById/allSetsById, FetchUiState, TvCatalogRoot, LanCacheModule -> SharedPlaybackModule, PlayerHandle.release, the Safely.kt callers (all 16 checked by git grep), PackageSettings (including app/lint-baseline.xml), UtilityDestination/HomeRow/RowContent/setCard, the TV Settings renames, Destination and the `settings` package.
- The package pass stays compiler-driven and last.

Duplicates removed:
- comments-to-invariants no longer edits the CatalogTabs.kt KDoc, which typed-department step 1 now owns whole.
- The three "same reason di/ActivePlayback.kt exists" sentences are deleted once, in playback-bindings step 3, instead of being repointed there and then deleted by comments-to-invariants step 4.
- typed-department steps 3 and 4 no longer depend on their order: the TvLatestPage.kt:56 bridge edit is gone.

## Value check (YAGNI/KISS)
No cluster is net-negative, so none is skipped.

Skipped (permanent): `review::.::holistic::contract_coherence::film_preloader_single_thread_precondition`, with its step removed from player-and-preloader-contracts-in-code.
- The only code-level statement of the rule is `limitedParallelism(1)` inside FilmPreloader. It provably breaks the three UnconfinedTestDispatcher setups (core/playback/src/test/kotlin/FilmPreloaderTest.kt:80/:306/:372): in kotlinx-coroutines-test 1.11.0, `UnconfinedTestDispatcherImpl.dispatch` throws UnsupportedOperationException outside yield. I read that in the bytecode; enrich had only called it "probable".
- The step's fallback was a constructor KDoc line, which is the docs-as-fix loop. The rule is already stated where it is relied on: core/playback/src/main/kotlin/FilmPreloader.kt:63 and :124-127.

Cut as gold-plating inside kept clusters. Each of these tests either passes before and after its change or can never fail:
- the Collection toggle overloads (catalog-mark 2);
- the sharing-switch test (lan-cache 1);
- four per-store dispatch tests, reduced to one (secret-stores 1);
- the LibraryUpdateCoordinator cancellation test (failure-handling 4);
- the :221 Queued assert (parity 4);
- the five round-trip preference tests (player-and-preloader 2);
- railRestoreOf and its test (tv-focus 3);
- the duplicate JVM target cases (tv-focus 1);
- the new Robolectric preload test (shared-surface 1);
- the duplicate cancellation test (shared-surface 3);
- the invented in-flight wait in process-globals 2, which is now a deletion of the redundant `asked` set (core/data/src/main/kotlin/PortraitRequestLog.kt:25);
- the tautological sections.js test (typed-department 2) and the keyOf test (typed-navigation 2).

Visible changes the user must check on a device. Each step calls them out for the commit message:
- Home's Latest courses goes from 8 to 6 on phone and TV, following the web (typed-department 4).
- Search, Stats and update failure wording (failure-handling 5).
- The System Telegram row's truth, and about 3 s of "reading…" when offline (system-telegram 1).

## Decision Ledger
- catalog-mark-writes-guarded -> tighten
- lan-cache-store-failures -> tighten
- secret-stores-main-safe -> tighten
- failure-handling-one-way -> tighten
- system-telegram-row-live -> tighten
- parity-fixtures-and-test-gates -> tighten
- profile-rules-one-source -> tighten
- dead-aliases-and-pass-throughs -> keep
- playback-bindings-out-of-feature-player -> tighten
- player-and-preloader-contracts-in-code -> tighten
- typed-department-and-tab-identity -> tighten
- typed-navigation-ids -> tighten
- tv-focus-targets-and-effects -> tighten
- shared-surface-glue-in-ui-common -> tighten
- process-globals-to-owned-state -> tighten
- build-deadwood -> tighten
- catalog-records-mirrored-in-core-model -> tighten
- comments-to-invariants -> tighten
- single-package-move-pass -> tighten
