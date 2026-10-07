# ENRICH report: Android app (19 clusters, 69 steps, 101 open review issues)

Every one of the 69 steps was checked again, read-only, against the source at 31cb8561. That is the same HEAD organize verified, and the working tree has no source changes. Four verifiers read every file each step cites, two at a time with exclusive clusters:
- A: priorities 1-5
- B: parity, profile, dead-aliases, playback-bindings, player-contracts, build-deadwood
- C: typed-department, typed-navigation, tv-focus, shared-surface
- D: process-globals, catalog-records, comments-to-invariants, single-package-move

Each verifier returned exact replacements. The lead reviewed them and applied them with `desloppify plan cluster update <name> --update-step N --detail ... --effort ... --issue-refs ...`. 66 of 69 steps changed. No source file was modified, Gradle was not run, and no step was marked done.

## Blocking requirements: state after enrich
- Every step has a detail of 80+ characters and names at least one existing android-relative file path.
- Every path-like token (a slash plus a file extension) resolves on disk from android/:
  - Files a step creates or renames to are named bare. Examples: OrDefault.kt, WebFixture.kt, MainScope.kt, SharedPlaybackModule.kt, Department.kt, FilmPreloadUi.kt, AppearanceModule.kt.
  - Files outside android/ use `../`. Examples: ../web/public/lib/address.js, ../web/test/fixtures/..., ../scripts/check.sh, ../docs/project-changelog.md.
  - Abbreviated paths such as `catalog/TvCatalogBlend.kt` were expanded to full module paths.
  - Device paths and code that merely looked like paths were reworded: shared_prefs/package_settings.xml, `dept.featured/acclaimed/recentlyAdded`, `PlayerViewModelOpen/Run/Delegates.kt`.
- Every step has issue_refs, and every ref is a member of its cluster. No ref names a skipped or deferred issue. Every member issue is referenced by at least one step.
- Every step has an effort tag, set individually.
- Three steps deliberately carry decision-independent halves of deferred issues: lan-cache-store-failures 1, secret-stores-main-safe 3 and build-deadwood 1. Their prose now names the deferred issue by its short name only, not its full review:: id. Their refs remain non-deferred members.
- The version-bump line in every step was rewritten as "the three manifests (workspace Cargo.toml, the web player's package.json, app/build.gradle.kts versionName)". The old wording carried repo-root paths that do not resolve from android/.

## Effort changes (with the reason seen in code)
- **lan-cache-store-failures 3**, small to trivial: one line in one file, no new test.
- **secret-stores-main-safe 1**, small to medium: dropping the Libraries dispatcher also breaks MobileAppFixture.kt:50, SettingsProfileRetryTest.kt:83 and TvAppFixture.kt:177. That makes about 12 files in 6 modules.
- **failure-handling-one-way 2**, small to medium: about 18 production files in 4 modules.
- **failure-handling-one-way 6**, small to medium: about 14 files in 6 modules.
- **process-globals-to-owned-state 2**, small to medium: it redesigns in-flight/done tracking and changes signatures in 17 production files and 4 test files.

## Corrected facts (beyond path normalisation)

**catalog-mark-writes-guarded**
- The show type is catalog.Entry.Collection. TvHomeReturnTest.kt:71 calls the repository's setEditorsChoice.
- writeState clears only its own sentence, so the recovery test reuses the same setter.
- The kids-mark test must advance past the 5 s WhileSubscribed window and assert on repository.snapshot.kidsFromSix.

**lan-cache-store-failures**
- The TvQuietLine is at :72.
- LanCacheBlockContent gains a `failure` parameter.
- testLanCacheViewModel's token-store parameter (LanCacheViewModelTest.kt:80) is widened.
- The orphaned KDoc above LanCacheViewModel's ACCESS_LOCAL_NETWORK copy goes with it.

**secret-stores-main-safe**
- Added the stale PackageSettings comment at app/build.gradle.kts:105-106.
- Spelled out the four comment rewrites.
- The clear() test roots FileCoreStorage on a `files` subfolder.

**system-telegram-row-live**
- Named the FakeCore knob (catalogFactsAnswer) for the non-channel case.

**failure-handling-one-way**
- The reason() KDoc is at :110.
- ChunkMemo.kt:78 is added to the leave-alone list.
- Named the refreshGate knob.
- AppUpdater has no Log/TAG yet.
- Lead change, step 5: the update fallbacks are lowercase clauses ("the update check did not finish", "the installer did not take the download"). updateLine renders "last update failed: <reason>" (UpdateStatus.kt:34), and apkRefusal already words its reasons that way. The old sentences would have reproduced the "failed: Could not..." doubling the step removes from Search.
- Lead change, step 6: CatalogEnrichmentFetcher's four catches log the exception class name only, never the throwable. KEY_READ_ERROR's comment says storage exceptions can carry credentials. The TMDB fetch also sends a v3 key in the api_key query parameter (crates/mediagram-tmdb/src/tmdb_client.rs:32-33), which a transport error message would repeat into logcat.

**parity-fixtures-and-test-gates**
- MainDispatcherRule has 16 users, not 20. The aliases have 110 calls, not 112.
- The catalog FakeFilmPreloading's emitHeld/emitUnheld must be added to the shared fake.
- Twelve files need imports, not two.
- The private FakeHeldSets in FilmPreloaderTest.kt:48 would clash, so it is renamed.
- `<size>` is 200L, and the :131 sleep is the mid-write wait.

**profile-rules-one-source**
- Named the 15 positional Managing(...) constructions a new field breaks (6 in ManageProfilesScreenTest, 9 in TvManageProfilesStateTest).

**dead-aliases-and-pass-throughs**
- RunFor.kt:11 names the web's `nextAfter`, which the web dropped (it now uses nextInQueue, library.js:231). The comment is updated.
- The retargeted TV tests need `import ui.tv.catalog.TvCatalogScreen`.

**playback-bindings-out-of-feature-player**
- The app dependencies block is at :101-118.
- DefaultPlayerHandle's @Inject constructor is an eighth unqualified CoroutineScope request.
- The LanServerLocator class doc is at :25-31.
- The `import player.safely` is now same-package.

**player-and-preloader-contracts-in-code**
- Test ranges are :32-44/:47-70 and the Ops KDoc is at :41-49.
- Three more release() references would dangle (DefaultPlayerHandle.kt:26-29, DefaultPlayerHandleTest.kt:23/:66).

**build-deadwood**
- 22 *.gradle.kts plus 19 build-logic *.kt files. Five serialization users, not six.

**typed-department-and-tab-identity**
- The web nav ends at index.html:82.
- The OverflowMenu.kt:22-28 KDoc is added.
- The EXTENT_NOUNS fold must keep the rail tally to movies, series and tutorials (web app.js:108-110, LibraryTallyTest:42).
- ChromeCounts' interim shape is pinned.
- TvLatestPage.kt:56 breaks in step 3, so it gets a bridge edit.
- Seven more test files are named.
- CatalogScreen has four call lines, not three.

**typed-navigation-ids**
- The web's person and franchise ids are strings, so typing them Long is Android tightening, not parity. The person parse is at :63.
- Named the hrefs passed by the TvSearchResultsStateTest helper's callers.

**tv-focus-targets-and-effects**
- The per-page site counts (11/8/14/2/9) matched nothing, so they are replaced by exact lines.
- The tests compare Pairs and TvHomeTarget values; they never read .first/.second.
- TvDepartmentsBar's pointer comment has no own-requester val.
- The TvPlayerScreen.kt:161 call is onKey(onSeekBar = ...), not focusInControls.

**shared-surface-glue-in-ui-common**
- The collectionsOf removal is typed-department step 4, not step 3.
- The cited person-page flow tests do not exist, so one LibraryFlowTest case is added.
- Lead change, step 3: it now names data.orDefault. Logging then moves to orDefault's tag, so RememberLookupTest's three "CatalogMetadata" reads (:52/:53/:66) change. The step had claimed that test passes unchanged.

**process-globals-to-owned-state**
- Named the imports left unused.
- Ranges corrected to :76-103, :96-103 and :109-119.
- PipController's KDoc at :53-60 is added.

**catalog-records-mirrored-in-core-model**
- The mappers become internal top-level extensions so a test can reach them.
- CatalogRepositoryTest.kt:119 must expect model.SearchHit. assertEquals compiles across the two types and fails only at run time.
- TitleCredits is added to the type list.

**comments-to-invariants**
- budgetLadder lives in CacheLocation.kt.
- 15 media3 importers, not 16.
- TvWall's KDoc is at :52-90.
- PlayerViewModelRun's comment is at :5-9.
- FilmWriteAttempt.kt:67 also reads openTitle.

**single-package-move-pass**
- TvSystemContent's call sites are TvSettingsScreen.kt:146/156/165 and TvAppearanceBlockTest.kt:40.
- Added the BrowseActions imports.
- TvTextQuestion only KDoc-links TvSafeArea.
- Destination.kt also declares showsSearchAction.
- Step 6 now names concrete files. Its greps were re-run and confirmed: 14 declaring files, 47 importers. The KDoc grep is widened to catch the backticked form.
- Lead change, step 7: the package order is now actually smallest first (setup, chrome, settings, profile, root, player, catalog).
- Lead change, all :app:assemble* gates (playback-bindings 1-3, build-deadwood 1, single-package-move-pass 7): a note that assembling needs the gitignored native .so files. A fresh worktree such as this one lacks them, so build them with ../scripts/build-android-core.sh and ../scripts/build-android-ffmpeg.sh, or copy them from the main checkout.

## Notes for sense-check
1. **secret-stores-main-safe 3**
   - Its ref, encrypted_preferences_claim_excludes_auth_key, mainly serves the deferred start_over_secret_coverage_uneven. Step 2 already fixes that member.
   - The ref is kept because every step needs a non-deferred ref; this was organize's deliberate design.
2. **typed-department 4: Home course count**
   - It changes Home's Latest courses list from 8 to 6 on phone and TV, to match the web Home (latestCourses uses `limit`).
   - This is a parity change under CLAUDE.md's Surface Parity rule, not a regression. It is visible to the user, so it should be called out in the commit.
3. **typed-department 3 and 4: ordering**
   - Step 3 now carries a bridge edit at TvLatestPage.kt:56 (`row.seeAll?.label`), which step 4 rewrites, so the order is load-bearing.
   - If step 4 runs first, drop step 3's bridge edit.
4. **parity-fixtures 4: the :221 sleep**
   - The 50 ms sleep at FilmPreloaderBlockingWriterTest.kt:221 is kept after the new Queued assert.
   - enqueue sets Queued synchronously (FilmPreloader.kt:110), so a wait on Queued cannot replace the lane-reach window. Nothing public signals that the lane has been reached.
5. **catalog-records-mirrored-in-core-model and the UniFFI decision**
   - The steps hold under all three options for the deferred uniffi-surface decision.
   - If the user picks "binding records are the shared read model", steps 1-2 drop. catalog_repository_mixed_ffi_model_returns would then have no step, so the plan must be revisited.
6. **The @MainScope qualifier name**
   - It is the name of playback-bindings 2's new qualifier, and it shadows kotlinx.coroutines.MainScope(). It compiles; this is a naming call only.
   - The `app-android-lint` alias is dead too but out of scope.
7. **Out-of-tree references use the `../` form**
   - Web parity references (web, scripts, docs, crates) use `../`, resolved from android/. Converting them to prose would cost the executor the exact file.
8. **comments-to-invariants 2: media3 count**
   - The count of 15 media3 importers may move if an earlier cluster splits feature:player files.
   - The step's grep is the source of truth.

