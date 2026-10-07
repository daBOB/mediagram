## Coverage Ledger
- catalog_watchlist_editors_choice_unguarded -> cluster "catalog-mark-writes-guarded"
- catalog_mark_writes_unguarded_and_toggle_logic_in_ui -> cluster "catalog-mark-writes-guarded"
- kids_mark_guard_checks_same_condition_twice -> cluster "catalog-mark-writes-guarded"
- lan_write_queue_failure_escapes -> cluster "lan-cache-store-failures"
- lan_cache_viewmodel_no_failure_boundary -> cluster "lan-cache-store-failures"
- phone_requests_permission_on_refused_token -> cluster "lan-cache-store-failures"
- local_network_permission_literal_triplicated -> cluster "lan-cache-store-failures"
- encrypted_settings_suspend_not_main_safe -> cluster "secret-stores-main-safe"
- settings_suspend_not_main_safe -> cluster "secret-stores-main-safe"
- encrypted_preferences_claim_excludes_auth_key -> cluster "secret-stores-main-safe"
- system_telegram_connected_is_auth_key -> cluster "system-telegram-row-live"
- cancellation_safe_catch_reimplemented -> cluster "failure-handling-one-way"
- safely_helper_copied_and_shadowed -> cluster "failure-handling-one-way"
- cancellation_fallback_helper_copies -> cluster "failure-handling-one-way"
- cancellation_catch_helper_duplicated_and_bypassed -> cluster "failure-handling-one-way"
- runcatching_on_suspend_swallows_cancellation -> cluster "failure-handling-one-way"
- run_catching_swallows_cancellation_in_preload_write -> cluster "failure-handling-one-way"
- failure_logging_three_styles -> cluster "failure-handling-one-way"
- safely_attempt_hide_swallowing -> cluster "failure-handling-one-way"
- refresh_result_double_handled -> cluster "failure-handling-one-way"
- raw_exception_messages_still_rendered -> cluster "failure-handling-one-way"
- raw_exception_text_bypasses_core_sentence -> cluster "failure-handling-one-way"
- web_parity_fixture_tests_skip_silently -> cluster "parity-fixtures-and-test-gates"
- instrumented_contract_suites_never_gated -> cluster "parity-fixtures-and-test-gates"
- preload_fakes_copied_and_drifting -> cluster "parity-fixtures-and-test-gates"
- blocking_writer_tests_sync_on_sleeps -> cluster "parity-fixtures-and-test-gates"
- fake_core_provider_legacy_names -> cluster "parity-fixtures-and-test-gates"
- household_rule_not_used_by_screens -> cluster "profile-rules-one-source"
- profile_choose_public_pin_bypass -> cluster "profile-rules-one-source"
- allsetsbyid_alias_file -> cluster "dead-aliases-and-pass-throughs"
- dead_next_after_duplicates_next_in_queue -> cluster "dead-aliases-and-pass-throughs"
- fetch_ui_state_typealias_shim -> cluster "dead-aliases-and-pass-throughs"
- wide_positional_passthrough_signatures -> cluster "dead-aliases-and-pass-throughs"
- rollout_inert_defaults_on_wired_entry_points -> cluster "dead-aliases-and-pass-throughs"
- catalog_boundary_leaks_generated_records -> cluster "catalog-records-mirrored-in-core-model"
- catalog_repository_mixed_ffi_model_returns -> cluster "catalog-records-mirrored-in-core-model"
- catalog_repository_leaks_generated_types -> cluster "catalog-records-mirrored-in-core-model"
- ui_tv_redundant_core_rust_dependency -> cluster "catalog-records-mirrored-in-core-model"
- shared_playback_bindings_owned_by_player_feature -> cluster "playback-bindings-out-of-feature-player"
- playback_composition_root_inside_feature_player -> cluster "playback-bindings-out-of-feature-player"
- playback_bindings_live_in_feature_player -> cluster "playback-bindings-out-of-feature-player"
- unqualified_scope_hidden_confinement -> cluster "playback-bindings-out-of-feature-player"
- active_playback_runtime_class_in_di -> cluster "playback-bindings-out-of-feature-player"
- player_handle_release_duplicates_set_listener -> cluster "player-and-preloader-contracts-in-code"
- player_handle_contract_misdescribes_impl -> cluster "player-and-preloader-contracts-in-code"
- player_preferences_kdoc_orphaned -> cluster "player-and-preloader-contracts-in-code"
- preference_names_bypass_constant -> cluster "player-and-preloader-contracts-in-code"
- film_preloader_single_thread_precondition -> cluster "player-and-preloader-contracts-in-code"
- player_controllers_in_viewmodel_named_files -> cluster "player-and-preloader-contracts-in-code"
- department_identity_by_display_string -> cluster "typed-department-and-tab-identity"
- shelf_title_string_department_dispatch -> cluster "typed-department-and-tab-identity"
- department_identity_by_display_title -> cluster "typed-department-and-tab-identity"
- branches_keyed_on_display_labels -> cluster "typed-department-and-tab-identity"
- home_rows_filtered_by_display_heading -> cluster "typed-department-and-tab-identity"
- catalog_tab_identity_by_index_offsets -> cluster "typed-department-and-tab-identity"
- half_migrated_tab_index_space -> cluster "typed-department-and-tab-identity"
- utility_destination_parallel_enum -> cluster "typed-department-and-tab-identity"
- composable_shadows_department_type -> cluster "typed-department-and-tab-identity"
- navigation_ids_round_trip_strings -> cluster "typed-navigation-ids"
- search_destination_kind_in_href_prefix -> cluster "typed-navigation-ids"
- tv_department_targets_untyped_pairs -> cluster "tv-focus-targets-and-effects"
- home_target_search_not_on_shared_helper -> cluster "tv-focus-targets-and-effects"
- tv_page_section_identity_in_parallel_lists -> cluster "tv-focus-targets-and-effects"
- tv_catalog_nav_duplicate_focus_effects -> cluster "tv-focus-targets-and-effects"
- tv_restore_effects_copy_pasted -> cluster "tv-focus-targets-and-effects"
- tv_own_requester_pointer_comments -> cluster "tv-focus-targets-and-effects"
- tv_key_action_positional_boolean_flags -> cluster "tv-focus-targets-and-effects"
- identical_surface_glue_outside_ui_common -> cluster "shared-surface-glue-in-ui-common"
- surface_twins_copy_nonvisual_logic -> cluster "shared-surface-glue-in-ui-common"
- surface_wiring_copied_instead_of_shared_in_ui_common -> cluster "shared-surface-glue-in-ui-common"
- film_preload_ui_wiring_duplicated_per_surface -> cluster "shared-surface-glue-in-ui-common"
- lookup_and_fallback_skeletons_duplicated -> cluster "shared-surface-glue-in-ui-common"
- person_lookup_dual_path -> cluster "shared-surface-glue-in-ui-common"
- palette_imprint_global_accent -> cluster "process-globals-to-owned-state"
- palette_imprint_sideeffect_global -> cluster "process-globals-to-owned-state"
- tv_accent_two_accessors -> cluster "process-globals-to-owned-state"
- portrait_fetch_state_file_globals -> cluster "process-globals-to-owned-state"
- should_request_portrait_reserves -> cluster "process-globals-to-owned-state"
- pip_entry_point_global_bridge -> cluster "process-globals-to-owned-state"
- dead_version_catalog_residue -> cluster "build-deadwood"
- vendored_ffmpeg_media3_lock_by_comment_only -> cluster "build-deadwood"
- stale_narrative_kdoc -> cluster "comments-to-invariants"
- stale_module_contracts -> cluster "comments-to-invariants"
- playback_vs_player_split_has_no_stated_rule -> cluster "comments-to-invariants"
- stale_docs_after_completed_migration -> cluster "comments-to-invariants"
- comment_history_narration -> cluster "comments-to-invariants"
- line_guideline_provenance_comments -> cluster "comments-to-invariants"
- open_title_source_thread_contract_contradiction -> cluster "comments-to-invariants"
- query_named_functions_that_mutate -> cluster "comments-to-invariants"
- settings_screens_packaged_differently_per_surface -> cluster "single-package-move-pass"
- settings_package_diverges_across_surfaces -> cluster "single-package-move-pass"
- settings_sections_named_three_ways -> cluster "single-package-move-pass"
- file_names_misdescribe_contents -> cluster "single-package-move-pass"
- mobile_chrome_split_with_reciprocal_imports -> cluster "single-package-move-pass"
- tv_leaf_pages_import_shell_files -> cluster "single-package-move-pass"
- mobile_only_nav_model_in_feature_catalog -> cluster "single-package-move-pass"
- directory_package_mapping_outliers -> cluster "single-package-move-pass"
- dir_package_mismatch_player_settings -> cluster "single-package-move-pass"
- ui_common_shares_mobile_namespace -> cluster "single-package-move-pass"
- ui_common_shares_mobile_packages -> cluster "single-package-move-pass"
- ui_mobile_shares_ui_common_namespace -> cluster "single-package-move-pass"
- build_logic_template_scaffold -> skip "user-decision-ktlint"
- secret_store_on_deprecated_security_crypto -> skip "user-decision-secret-storage"
- deprecated_security_crypto_unacknowledged -> skip "user-decision-secret-storage"
- lan_read_write_gates_diverge -> skip "user-decision-lan-pairing"
- adaptive_release_candidate_pin -> skip "user-decision-m3-adaptive-pin"
- player_vm_state_exposed_for_extensions -> skip "user-decision-player-viewmodel-size"
- player_viewmodel_split_by_line_budget -> skip "user-decision-player-viewmodel-size"
- player_viewmodel_state_exposed_for_extension_split -> skip "user-decision-player-viewmodel-size"
- player_viewmodel_split_command_surface -> skip "user-decision-player-viewmodel-size"
- player_vm_split_leaks_mutable_state -> skip "user-decision-player-viewmodel-size"
- player_viewmodel_internal_backing_flows -> skip "user-decision-player-viewmodel-size"
- generated_bindings_cross_data_layer -> skip "user-decision-uniffi-surface"
- generated_binding_types_cross_data_boundary -> skip "user-decision-uniffi-surface"
- start_over_secret_coverage_uneven -> skip "user-decision-start-over-lan-token"
- player_forwarding_chain -> skip "player-facade-keeps-controllers-private"
- player_viewmodel_passthrough_delegates -> skip "player-facade-keeps-controllers-private"
- repository_contract_test_stub_defaults -> skip "interface-defaults-serve-test-doubles"
- interface_defaults_mask_unimplemented -> skip "interface-defaults-serve-test-doubles"
- fakecore_overlapping_knobs -> skip "test-fixture-knobs-no-production-impact"
- frame_chrome_params_threaded -> skip "explicit-compose-parameters-are-idiomatic"
- tv_restore_key_wrapper_repeated -> skip "restore-helper-fits-half-the-sites"
- cache_provider_global_object -> skip "cache-provider-global-encodes-simplecache-limit"
- newest_unreachable_null_branch -> skip "harmless-unused-result-fold-when-touched"
- session_clear_return_unused -> skip "harmless-unused-result-fold-when-touched"
- watchsync_side_effecting_loop_condition -> skip "correct-five-line-loop-already-commented"
- player_controllers_field_reset_sprawl -> skip "explicit-short-resets-no-missed-field"
- catalogue_spelling_and_secs_drift -> skip "rename-churn-without-misleading-reader"
- dept_department_abbreviation_drift -> skip "rename-churn-without-misleading-reader"
- library_positions_is_back_stack -> skip "rename-churn-without-misleading-reader"
- library_update_verbs_overlap -> skip "rename-churn-without-misleading-reader"
- player_open_real_suffix -> skip "rename-churn-without-misleading-reader"
- plain_vs_sharedpreferences_store_prefix -> skip "rename-churn-without-misleading-reader"
- tv_destination_suffix_drift -> skip "rename-churn-without-misleading-reader"
- collection_names_two_concepts -> skip "web-reference-uses-the-same-collection-split"
- core_playback_flat_multi_subsystem -> skip "subpackaging-churn-for-navigation-only"
- feature_catalog_flat_root_mixes_navigation -> skip "subpackaging-churn-for-navigation-only"
- surface_catalog_dirs_overloaded -> skip "subpackaging-churn-for-navigation-only"
- ui_tv_shell_and_primitives_package_cycle -> skip "subpackaging-churn-for-navigation-only"
- cue_backing_raw_string -> skip "validated-on-load-two-local-literals"
- ffi_origin_string_leaks_to_ui -> skip "two-comparisons-in-one-module"

## Strategy in one paragraph

Observe leaves 141 open issues: 97 genuine, 14 exaggerated, 28 not-worth-it and 2 over-engineering. This blueprint clusters 101 of them into 19 clusters and skips 40: 14 because they are the user's decision and 26 on the merits. The 141 issues are not 141 problems. They come from about ten root causes, and each cluster below is one root cause, not one dimension queue. Last cycle averaged 1.9 items per cluster and skipped more than it did, so the clusters here are deliberately larger, except the four behavioural-defect clusters. Those stay small so each fix lands with its own regression test before any structural work starts. Order:
1. The four defect clusters.
2. The two root causes the other clusters build on: the failure helper, and parity fixtures that fail loudly.
3. Content clusters.
4. The comment pass.
5. The package-move pass, which stays last so files are not moved and then edited.

Visible Android changes need a check on the devices by the user: phone/tablet and the TV box.

## Cluster Blueprint

Execution order is the numbering. Every cluster ends with the Android check command, run from android/: `./gradlew testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin`. Cluster 6 adds two compile targets to that command. Per CLAUDE.md § Versioning, every commit that changes code bumps all three manifests in step. Nothing in this plan changes the Rust/UniFFI surface.

### 1. Cluster "catalog-mark-writes-guarded" (3 issues, behavioural defect)
Files: feature/catalog/src/main/kotlin/CatalogViewModel.kt; the toggle sites in ui-mobile LibraryTitleBranches.kt:48/55/102/105 and LibraryFlowBranches.kt:245, and in ui-tv TvLibraryCatalogFrames.kt:85/91/154/160 and TvLibraryBranches.kt:198; feature/player/src/main/kotlin/PlayerMarksController.kt.
Why: setEditorsChoice (:93) and setWatchlisted (:101) are bare `viewModelScope.launch` writes. Every sibling write goes through writeState, which catches failures and shows a notice. A Rust panic, or a core closed by sign-out racing the tap, reaches the main thread uncaught and kills the app from a My List or editor's-choice tap, on phone and TV alike. The player's identical write already shows a notice.
Steps:
1. (small) Route both setters through writeState, each with its own failure sentence in the player's wording ("Could not confirm the … update"), returning true after the call.
2. (small) Move the toggle rules into the ViewModel the way PlayerMarksController already does: the new value comes from watchState.snapshot.value, a kids profile cannot pin, and a show is marked by its first episode. Add toggleWatchlist(setId) and toggleEditorsChoice(setId) and switch the 4 sites per surface to them. Keep a guarded explicit (setId, listed) entry for the home cover and the cards.
3. (trivial) PlayerMarksController.kt:93: drop the second `marks.value?.canMarkKids == false` clause. It repeats the first, and a stale WhileSubscribed value can wrongly block a grown-up after a kids profile.
4. (small) Regression tests (JVM). In CatalogViewModelTest, a watch-state repository whose setWatchlisted/setEditorsChoice throws IllegalStateException must leave the ViewModel alive with the notice set, and a later success clears the notice. Add tests for the kids and first-episode rules of the new toggles. A PlayerMarksController test switches from a kids profile to a grown-up and checks the kids mark is not blocked.
Device (user): toggle My List and editor's choice on a title, and on a show's cover, on the tablet and the TV box.

### 2. Cluster "lan-cache-store-failures" (4 issues, behavioural defect)
Files: core/playback/src/main/kotlin/LanWriteQueue.kt, feature/player/src/main/kotlin/di/LanCacheModule.kt, feature/system/src/main/kotlin/LanCacheViewModel.kt, ui-mobile/src/main/kotlin/ui/settings/LanCacheBlock.kt, ui-tv/src/main/kotlin/ui/tv/system/TvLanCacheBlock.kt, feature/system/src/main/kotlin/LanCacheInput.kt.
Why: the encrypted LAN token store can throw a keystore exception on open, and two callers let it escape. In LanWriteQueue.write, `server()`/`token()` sit outside the IOException-only try (:79-80). The worker launched on a scope with no handler would then kill the app mid-playback, against its own KDoc ("a write failure of any kind stays entirely on this side of enqueue"). LanCacheViewModel has no catch at all: `snapshot` reads the token at :147 inside stateIn, and act() (:120) launches unguarded, so an unreadable store crashes Settings every time it opens. Separately, the phone raises the API-37 local-network prompt even for a refused token, where the TV prompts only after a successful save. That is a parity defect in the phone surface.
Steps:
1. (small) Put the whole LanWriteQueue.write body (server, token, put) inside core:playback's existing `safely(Unit) { … }`. Cluster 5 later swaps it for the shared helper with an import change. Keep the 401 halt. Regression test in LanWriteQueueTest: token() throws SecurityException on the first chunk, the worker survives, and later chunks are still PUT.
2. (trivial) LanCacheModule.kt:94: gate the writer's server lambda on `settings.enabled()`, as reads already are, so chunks queued before the viewer switched LAN sharing off are dropped rather than PUT. This is the write half of the deferred LAN-pairing issue, carried here because it is not part of that decision. Test: a disabled gate makes the queue PUT nothing.
3. (small) LanCacheViewModel: follow SystemViewModel's pattern. Catch inside snapshot, rethrowing cancellation, keep the last state and expose one fixed failure sentence. Wrap act()'s work the same way. Regression tests in LanCacheViewModelTest: a token store whose read() throws yields the failure state instead of a crash, and saveToken with a throwing write does not crash.
4. (small) Phone LanCacheBlock.kt:71: prompt only when `viewModel.saveToken(token)` returns true, as TvLanCachePanel already does. Replace the three ACCESS_LOCAL_NETWORK string constants (LanCacheViewModel.kt:30, LanCacheBlock.kt:44, TvLanCacheBlock.kt:28) with android.Manifest.permission.ACCESS_LOCAL_NETWORK. Share TV's rememberLocalNetworkRequest only if ui-common already has activity-compose on its main classpath (today it is testImplementation only); otherwise keep the two three-line launchers. JVM coverage comes from step 3's validation tests; the prompt itself is verified on device.
Device (user): on the tablet and the TV box, open Settings → LAN cache, save a valid and an invalid token, and switch sharing off and on during playback. The API-37 prompt can only be seen on an API-37 device; the tablet is API 36.

### 3. Cluster "secret-stores-main-safe" (3 issues, behavioural defect)
Files: core/data/src/main/kotlin/settings/{TmdbSettings,TelegramSettings,LanCacheTokenSettings,LibrarySettings,EncryptedPreferences,PackageSettings}.kt, core/data/src/main/kotlin/di/DataModule.kt, feature/setup/src/main/kotlin/{SetupViewModel,SettingsViewModel,Libraries}.kt, core/data/src/main/kotlin/CoreProvider.kt.
Why: the stores' `suspend` read/write/clear never suspend, so EncryptedSharedPreferences.create runs on the caller's thread: MasterKey lookup, a keyset read from disk and a keystore unwrap. Three callers run on Main: FetchViewModel.init via CatalogEnrichmentFetcher.kt:41, SetupViewModel.kt:144, and LanCacheViewModel.kt:147/:100. That breaks the project's own rule at DataModule.kt:45-49 and risks jank or an ANR. Only EncryptedLibrarySettings.read dispatches today. The contract goes into code: the store owns its dispatcher, rather than gaining another KDoc.
Steps:
1. (small) Give EncryptedTmdbSettings, EncryptedTelegramSettings and EncryptedLanCacheTokenSettings the injected CoroutineDispatcher that DataModule already provides. Wrap every read/write/clear in withContext(dispatcher), including EncryptedLibrarySettings.write/clear (:70/:75).
2. (small) Delete the caller-side wrappers that only wrapped a store call: Libraries.kt:43 (read), SettingsViewModel.kt:88 and the one in CoreProvider. Keep Libraries.kt:39, which is still needed.
3. (small) Regression test (JVM, Robolectric): build each store with a dispatcher that counts dispatches, then assert that read, write and clear each dispatch through it before touching the preferences. The keystore open may throw under Robolectric, so the test asserts the hop, not the value. FetchViewModelTest and SetupViewModelTest must still pass.
4. (trivial) EncryptedPreferences.kt:9: narrow "the one way this app puts a secret on disk" to the settings stores, and name session.key as the core-owned exception, protected by the sandbox and data_extraction_rules.xml. Wrapping the auth key itself would be a Rust change and is not proposed.
5. (small) Start over deletes the legacy `package_settings` file, which still holds the package decryption key and has been orphaned since fdda4863. Add the deletion to the Start over path, delete the dead PackageSettings/EncryptedPackageSettings and their tests (git keeps them), and repoint the LanCacheTokenSettings.kt:7 and LanCacheSettings.kt:12 docs at TmdbSettings. This is the decision-independent half of the deferred Start-over issue. The LAN token is NOT cleared: that half waits for the user. Regression test (Robolectric): after startOver the package_settings preferences file no longer exists.
Device (user): smoke only. Open Settings, Fetch and LAN cache, and sign in or out if convenient, on the tablet and the box. Never run Start over on a real device; the JVM test covers it.
Not in scope: migrating off security-crypto (deferred, user decision).

### 4. Cluster "system-telegram-row-live" (1 issue, behavioural and parity defect)
Files: feature/system/src/main/kotlin/{SystemViewModel,SystemRows,SystemUiState}.kt.
Why: SystemViewModel.kt:118 fills "connected" from core.isAuthorized(). The core documents that call as "whether a login has ever completed … needs no connection", so an offline device reads "connected" and "all current". The web player, which is the reference, reports live MTProto state ("Asked rather than remembered", web/src/telegram/client.ts:161-171). Settings already probes liveness with core.account().
Steps:
1. (small) Replace isAuthorized() with the account() probe Settings uses, bounded by a short timeout and run alongside the other reads, so a silent Telegram cannot hold up the System screen. A failure or timeout means not connected. The probe runs once per System visit, matching the screen's once-per-subscription read. No UniFFI change; a live flag from the core would be one, and is listed for the user below.
2. (small) Regression tests (JVM, SystemViewModelTest with FakeCore): account() succeeds → connected; account() throws → not connected and the overall status needs attention; account() hangs past the timeout → not connected, and the other rows still arrive.
Device (user): open System on the tablet and the TV box online, then with Wi-Fi off. Compare with the web player's System row; the wording stays the same and only the truth changes.

### 5. Cluster "failure-handling-one-way" (11 issues, root cause: error_consistency rework loop)
Files: new helper in core/data/src/main/kotlin beside CoreErrors.kt. Deletes core/playback/src/main/kotlin/Safely.kt and feature/player/src/main/kotlin/Safely.kt. Edits SettingsViewModel.kt:271, ProfileSettingsViewModel.kt:68, PinAsk.kt:114/:130, UpNextAsync.kt:48-54, the CatalogViewModel writeState catch, FilmPreloader.kt:322, CacheDataSourceWriter.kt:105, LanServerLocator.kt:178, LibraryUpdateCoordinator.kt:34-44, SearchViewModel.kt:99, StatsViewModel.kt:85, AppUpdater.kt:116/:133, SearchScreen.kt:142 and TvSearchResults.kt:79.
Why: last cycle fixed cancellation once per call site and produced 7 named catch-to-default copies: two byte-identical Safely.kt files, plus optionalRow, two attempt()s, rereadQuietly and a shadowing private safely. It also produced 2 runCatching-on-suspend sites that swallow cancellation, three logging styles, and triple-layered handling around refresh(). Raw Throwable text also reaches the Search, Stats and Updates rows, bypassing coreSentence().
Steps:
1. (small) Add one public catch-to-default helper in core:data, which every caller already reaches (core:playback, the feature modules and ui-common). Give it a name that says it returns a default, e.g. `orDefault(default, what = null) { }`. It rethrows CancellationException and follows one logging rule: Log.w with the throwable when `what` is given. Unit tests: the default on failure, the value on success, and cancellation rethrown.
2. (small) Delete both Safely.kt files and point their callers at the helper: feature/player's 12 calls, FilmWriteAttempt.kt:73, and LanWriteQueue from cluster 2. Replace optionalRow, the two attempt()s, rereadQuietly and the two inline default-only catches (UpNextAsync, CatalogViewModel.writeState). Rename FilmPreloader's private `safely(what)` to logFailure, so one name no longer carries two behaviours.
3. (trivial) Convert the two runCatching-on-suspend sites (CacheDataSourceWriter.kt:105 and LanServerLocator.kt:178) to the helper. Regression test: cancellation thrown from holdSubtitles propagates out of the writer instead of being swallowed.
4. (trivial) LibraryUpdateCoordinator: replace the try / catch / re-check block with `val result = repository.refresh()`. The repository contract already returns Result and throws only cancellation. Add a test that cancellation from refresh propagates.
5. (small) User-facing failure sentences: at SearchViewModel, StatsViewModel and the two AppUpdater sites, use `coreSentence()` with a fixed fallback and log the throwable. Drop the duplicated "Search failed:" prefix on both surfaces, and keep the web's wording (app.js:438, stats-page.js:35). JVM tests: a throwing core yields the fixed sentence and never raw text.
6. (small) Logging rule: every terminal catch that becomes UI state or a fallback logs Log.w with the throwable. The unlogged sites are SetupViewModel, CatalogEnrichmentFetcher, SettingsViewModel, SearchViewModel, CatalogViewModel.writeState and PlayerMarksController.write; the message-only sites are WatchSync, StatsViewModel, ProgressRecorder, SubtitleTrackSource, SummarySource, FilmPreloader, ReadAhead and AchievementDotViewModel.
Guard against the rework loop: do NOT sweep the ~45 inline cancellation catches that do their own work (set UI state, retry, map outcomes). Convert one only when its catch merely maps to a value or logs.
Device (user): none required; copy changes are covered by tests.

### 6. Cluster "parity-fixtures-and-test-gates" (5 issues, test_strategy)
Files: 6 web-parity fixture tests (NextUpFixtureTest, CategoryRowsFixtureTest, EditorialPicksFixtureTest, SubtitleChoiceTest, ResumePointFixtureTest, AchievementLabelsFixtureTest); core/testing; ../scripts/check.sh; the preload/LAN fakes in feature/player, ui-mobile and ui-tv tests; the MainDispatcherRule copies; core/playback/src/test/kotlin/FilmPreloaderBlockingWriterTest.kt; 21 test files using legacy FakeCoreProvider aliases.
Why: this runs before the department cluster on purpose. Today a moved web fixture turns a parity test into a silent skip. The department and home-row changes in cluster 12 depend on exactly those fixtures failing loudly.
Steps:
1. (small) assumeTrue → assertNotNull at the 6 sites. Add one webFixture() locator to core:testing and delete the 6 copied locators in its consumers; core:model keeps its own, since it does not depend on core:testing.
2. (trivial) Add `:core:rust:compileDebugAndroidTestKotlin :core:data:compileDebugAndroidTestKotlin` to scripts/check.sh, which becomes the new Android check command. Running the instrumented suites stays a device step for the user.
3. (medium) Consolidate the preload/LAN fakes (take the feature/player versions) and one MainDispatcherRule into shared test code: core:testing, or core:playback testFixtures if core:testing cannot reach core:playback. Delete the copies.
4. (small) FilmPreloaderBlockingWriterTest: wait on state instead of Thread.sleep at :221/:290, wrap the 13 bare waitUntil calls in assertTrue, track mid-write with an AtomicLong, and make started/completed thread-safe (CopyOnWriteArrayList).
5. (trivial) Replace `ResolvedCoreProvider(` and `CatalogCoreProvider(` with `FakeCoreProvider(` across the 21 test files and delete the two aliases.
Device: none (test-only).

### 7. Cluster "profile-rules-one-source" (2 issues, auth consistency)
Files: feature/catalog/src/main/kotlin/profile/{ManageUiState,ProfileViewModel}.kt, core/model/src/main/kotlin/ProfileRoles.kt.
Steps:
1. (small) Derive manageable()'s grown-up/kid lists and canAddGrownUp from `profiles.allowed(...)`, the rule pinned to profile-rules.json, instead of the hand-coded filters. Port the web's "offered vs fixture" check (web/test/profile-api.test.ts:124-141) as a JVM test over every case in the shared fixture.
2. (trivial) Make ProfileViewModel.choose() internal. No UI path calls it; this is API hygiene, not a security fix.
Device (user): only if step 1's fixture test shows the panel offered something different. Then open Manage profiles as the admin and as a plain grown-up on the tablet and the box.

### 8. Cluster "dead-aliases-and-pass-throughs" (5 issues, incomplete migration and AI debt)
Files: feature/catalog/src/main/kotlin/{AllSetsIndex,HomeShelves,NextUp}.kt, feature/system/src/main/kotlin/FetchUiState.kt, ui-mobile/src/main/kotlin/ui/catalog/{CatalogScreen,TitleDetailScreen,CollectionScreen}.kt, ui-tv/src/main/kotlin/ui/tv/TvLibraryBranches.kt.
Steps:
1. (trivial) Rename internal indexById to the public allSetsById in HomeShelves.kt, keeping the doc that TvDepartmentPages.kt:62 cites, update its 5 in-module callers and delete AllSetsIndex.kt.
2. (trivial) Delete the unused nextAfter (NextUp.kt:149-155); nextInQueue is the one in use.
3. (trivial) Delete the FetchUiState typealias and type the 4 parameters as data.CatalogEnrichmentState.
4. (small) Call the private Shelves with named arguments at CatalogScreen.kt:114-118, where five adjacent (String)->Unit callbacks are positional today. Delete the TvCatalogRoot pass-through and call TvCatalogScreen from TvLibraryHomeFrame and its 2 tests. That also removes TvCatalogRoot's duplicated defaults.
5. (trivial) Reword the TitleDetailScreen and CollectionScreen KDocs: the defaults now serve short tests, not "a caller not yet wired".
Tests: the existing suites, with TvCatalogRootPlayStateTest and TvLibraryRemoteTest retargeted at TvCatalogScreen. Device: covered by cluster 12's walk.

### 9. Cluster "catalog-records-mirrored-in-core-model" (4 issues, Kotlin only)
Files: core/model (new TitleInfo and SearchHit mirrors), core/data/src/main/kotlin/CatalogRepository.kt, the 15 feature/ui files importing the generated TitleInfo, SearchUiState.kt, SearchGroups.kt, both FakeCatalogRepository test fakes, core/testing FakeCore, ui-tv/build.gradle.kts and core/data/build.gradle.kts.
Why: CatalogRepository maps five read kinds to model types but passes the generated TitleInfo (10 `var` fields, UInt? counts) and SearchHit straight through. SearchUiState.Ready mixes a generated type with a mirrored one. This is a local inconsistency inside one repository; the cross-module rule is not this cluster's business.
Steps:
1. (medium) Add val-only mirrors with Int? counts to core:model. Keep genres' shape unless every consumer splits it the same way. Map them in DefaultCatalogRepository beside toMediaSet and toCredit, then switch the 17 imports, the two fakes and FakeCore.
2. (trivial) Delete ui-tv's redundant `implementation(project(":core:rust"))` and its comment. core:data already exposes it as api.
3. (trivial) Add one sentence to core/data/build.gradle.kts saying which records the catalog repository mirrors, and that other generated value records (stats, session, auth) still cross through the documented api edge until the user picks the module-wide rule.
4. (small) Unit test the mapping: UInt counts → Int, nulls preserved.
Dependency: run it after the user answers the UniFFI-reach question below. If the user chooses "binding records are the shared read model", drop steps 1 and 4 and keep 2 and 3.
Device (user): smoke the title pages and search on both surfaces.

### 10. Cluster "playback-bindings-out-of-feature-player" (5 issues, High elegance and init coupling)
Files: feature/player/src/main/kotlin/di/{LanCacheModule,PlaybackModule,PreloadModule,ActivePlayback}.kt, app/src/main/kotlin/com/mediagram/android/di/, app/build.gradle.kts, core/data/src/main/kotlin/di/AppScope.kt, and the 7 consumers of the unqualified scope.
Why: core:playback types are bound inside feature:player but injected by feature:system and feature:catalog, which do not depend on it. The Main.immediate scope that LanServerLocator's lock-free state relies on is unqualified, and the AppScope KDoc that describes it is stale.
Steps:
1. (small) Move LanCacheModule, plus the PlaybackCounters and HeldSetsQuery providers that sibling features inject, to app/src/main/kotlin/com/mediagram/android/di, and add `implementation(project(":core:playback"))` to app. PreloadModule stays in feature:player, because provideFilmPreloader needs ActivePlayback and PreloadService. Do not add Hilt to core:playback: HeldSets.kt:51 and PreloadModule.kt:83 document that it carries none.
2. (small) Add a @MainScope qualifier beside AppScope. Annotate PlaybackModule.kt:57 and its 7 consumers (ExoPlayer deferred, PlayerHandle, LanServerLocator, LanCacheRuntime, SeriesPreloader, ActivePlayback, FilmPreloader), and correct the AppScope KDoc ("for the player alone").
3. (trivial) Move ActivePlayback.kt and its test out of di/ into the feature/player root (package player), add the one import in PreloadModule, and update the four backticked doc references.
Tests: Hilt validates the graph when :app compiles; the existing ViewModel tests construct directly. Device (user): smoke playback, LAN cache status and a film preload on the tablet and the box.

### 11. Cluster "player-and-preloader-contracts-in-code" (6 issues, Contracts and API coherence)
Files: feature/player/src/main/kotlin/{PlayerHandle,DefaultPlayerHandle,DefaultPlayerHandleOps,PlayerViewModel,PlayerChoicesController,SubtitleChoiceController,AudioChoiceController,FramingController,SubtitleStyleController}.kt, test/FakePlayerHandle.kt, core/data/src/main/kotlin/PlayerPreferences.kt, feature/setup/src/main/kotlin/ProfileSettingsViewModel.kt, core/playback/src/main/kotlin/FilmPreloader.kt, the three PlayerViewModel{Held,Notes,Preload}.kt files.
Steps:
1. (trivial) Delete PlayerHandle.release() and its two overrides, and call handle.setListener(null) in onCleared. Reword setMetadata's KDoc to "applied to the open title now, and remembered for every later load until replaced", and fix setMetadataReal's sentence.
2. (small) Add the six missing preference-name constants beside SUBTITLE_PREFERENCE in PlayerPreferences.kt and use them at all 14 literal sites. Move the orphaned interface KDoc (:5-13) above `interface PlayerPreferences` and state its failure contract in one sentence. JVM test: each controller writes and reads back the same key through a fake PlayerPreferences.
3. (small) FilmPreloader: state the single-thread precondition in code with `dispatcher.limitedParallelism(1)` if FilmPreloaderTest's three UnconfinedTestDispatcher setups still pass. Otherwise fall back to one KDoc line on the constructor parameter.
4. (trivial) `git mv` PlayerViewModelHeld/Notes/Preload.kt to PlayerHeldController.kt, PlayerNotesController.kt and PlayerPreloadController.kt, the classes they hold. These are file renames only; nothing is merged or moved into PlayerViewModel.kt.
Device (user): play a title on the tablet and the box, change subtitle, audio, speed and framing, reopen, and confirm the choices are remembered.

### 12. Cluster "typed-department-and-tab-identity" (9 issues, Design coherence, Type safety, Mid and Low elegance; parity defect)
Files: feature/catalog (Shelves, CatalogUiState, Anime, HeroArtOf, TitlePageCandidates, MagazineHome, HomeShelves, LibraryTally, DepartmentLines, CatalogTabs, KeptShelves) and the dispatch sites on both surfaces: ui-mobile CatalogScreen, LibraryBranchSupport, LibraryBrowseBranches, ShowsDepartmentScreen, LatestScreen, HomeScreen, LibraryFlowBranches, chrome/LibraryHome, AnimeDepartmentScreen and DocumentariesDepartmentScreen; ui-tv TvLibrary, TvDepartmentPages, TvCatalogBlend, TvShowsDepartmentPage, TvHome, TvCatalogScreen, TvCatalogBody and TvCatalogNav.
Why: Shelf has no identity besides its display title. About 37 branch lines in 17 files dispatch on "Movies"/"Series"/"Tutorials" or on Latest-row headings, each ending in an else, so renaming a label compiles and silently degrades that department. Tabs are decoded by firstKept offset arithmetic at about 16 sites. The web player, which is the reference, keys sections structurally: SECTIONS in web/public/lib/catalog/sections.js:9-18, home-shelves.js returns named latestSeries/latestCourses fields, and addresses are keyed by section id. Android's divergence is a Surface Parity defect.
Steps:
1. (trivial) Delete MastheadSplit.utilities, UtilityDestination and their test, and fix the stale CatalogTabs KDoc, which points at a plan phase.
2. (medium) Add one Department enum in feature:catalog mirroring the web's SECTIONS (movies, series, tutorials, documentaries, anime, each with label, extent noun and item noun). Set it non-null on Shelf in shelvesOf. Switch every dispatch and lookup line to it, keep the title for display only, and fold LibraryTally.EXTENT_NOUNS and the DepartmentLines nouns into the enum.
3. (small) Add one feature:catalog function that returns Home's latest-series and latest-courses rows with their totals, as named fields like the web's homeShelves(), and one function for the Continue / Next up / Latest films exclusion. Call them from both surfaces and delete both private collectionsOf copies and the heading filters.
4. (medium) Replace the firstKept arithmetic with one tab identity shared by both surfaces: Home | Dept(Department) | Kept(KeptKind). Persist the phone's chosen tab by key, not by title.
5. (trivial) Rename the composables that shadow the department data classes to AnimeDepartmentTab, DocumentariesDepartmentTab and ShowsDepartmentTab, and drop the fully qualified workarounds in their two tests.
6. (small) Tests: the parity fixture tests (loud since cluster 6) must pass unchanged. New JVM tests: shelvesOf sets every department, the tab key round-trips, and the home-row function matches the web's field semantics.
Device (user, required): walk every department tab, Home's Latest series/courses rows, the kept tabs and Back-restores-tab on the tablet and the TV box. Navigation only; change no settings.

### 13. Cluster "typed-navigation-ids" (2 issues, Type safety)
Files: ui-common/src/main/kotlin/ui/LibraryPositions.kt; 12 openers and 4 parsers across ui-mobile LibraryTitleBranches, LibraryFlowBranches and LibraryBrowseBranches and ui-tv TvLibraryFrames, TvLibraryCatalogFrames, TvLibraryBranches and TvLibrary; feature/catalog/src/main/kotlin/SearchGroups.kt, ui-tv TvSearchGroups.kt, SearchGroupsView.kt and TvSearch.kt.
Steps:
1. (small) openPerson(id: Long) and openFranchise(id: Long), with personId/franchiseId as Long?, converting inside LibraryPositions. Update the 16 call sites and the 2 LibraryPositions tests.
2. (small) Make SearchGroups' franchiseId a Long? constructor field, set where the franchise row is built and null for lists, and drop the href-parsing getter. Export the TV destination key builders from TvSearchGroups.kt so TvLibraryFrames.kt:56-68 stops re-spelling "dest:tmdb-". The restore-key strings must stay byte-identical on both the producer and consumer side.
Tests: LibraryPositionsTest and SearchGroupsTest. Device (user): open a person and a franchise from a title page and from search on both surfaces, press Back, and check that TV focus returns to the search entry.

### 14. Cluster "tv-focus-targets-and-effects" (7 issues, Type safety and Low elegance)
Files: ui-tv/src/main/kotlin/ui/tv/catalog/{TvDepartmentTargets,home/TvHomeTargets,TvMoviesDepartmentPage,TvShowsDepartmentPage,TvDocumentariesDepartmentPage,TvAnimeDepartmentPage,TvHome,TvCatalogNav}.kt, ui-tv TvFocus.kt and the ~20 files carrying own-requester pointer comments, ui-tv/src/main/kotlin/ui/tv/player/{TvPlayerRemote,TvPlayerScreen}.kt.
Steps:
1. (small) Replace Pair<String, Int> with a DeptTarget(section, stop) data class, plus one private const per fixed section name in each page. Make restoreTargetOf generic over the section type and have homeTargetOf delegate to it, which makes its KDoc true.
2. (trivial) TvMoviesDepartmentPage: emit the featured/acclaimed/recentlyAdded items under the same emptiness conditions as `included`, fixing the latent focus-index off-by-one.
3. (small) TvCatalogNav: one restore-key → RailItem map and a single LaunchedEffect replace the five near-identical rail effects. Keep the search and menu effects separate, and keep the new effect after the `!ready` bar effect.
4. (small) Add rememberStableRequester(wanted) to TvFocus.kt, with the one explanation in its KDoc. Use it at the ~25 own-requester sites and delete the pointer comments and the local ownRequester vals.
5. (trivial) Use named arguments at the two positional Boolean calls (TvPlayerRemote.kt:91, TvPlayerScreen.kt:161). No TvKeyState class.
6. (small) Tests: a pure JVM test for the generic restore-target search (last section first, first match, home fallback). The existing TvDepartmentPagesStateTest and TvLibraryRemoteTest must pass.
Device (user, required): on the TV box with ANDROID_SERIAL pinned, do a focus walk of Back from Search, the menu and each rail item, every department page (restore to the last row) and Home. Navigate only: never press OK on a settings row.

### 15. Cluster "shared-surface-glue-in-ui-common" (6 issues, Cross-module arch and Design coherence)
Files: ui-mobile TitlePreloadWiring.kt, catalog/TitlePreload.kt, catalog/DepartmentScrollStates.kt and LibraryBrowseBranches.kt; ui-tv TvFilmPreloadWiring.kt, catalog/TvTitlePreload.kt, catalog/TvDepartmentScrollStates.kt and TvLibraryExtraFrames.kt; ui-common/src/main/kotlin/ui/catalog/RememberLookups.kt.
Why: the film-preload wiring, which includes the kids-profile queue filter, and the department scroll-state holders are byte-identical twins. A fix to one copy only would become a privacy and parity defect. The phone hand-rolls the person-page loading flag that TV gets from rememberPersonLookup.
Steps:
1. (small) Move TitlePreloadUi and rememberFilmPreloadUi into ui-common/src/main/kotlin/ui/catalog/, with the over-polling remember keys kept once. Do the same for DepartmentScrollStates and its remember function. Delete the Tv* copies. The visual Compose twins stay as they are.
2. (small) Switch the phone PersonFrame to rememberPersonLookup and delete rememberPerson.
3. (small) Collapse RememberLookups' five remember / LaunchedEffect / catch / log skeletons onto one private rememberLookup built on cluster 5's helper.
4. (small) Tests: the existing TitlePreloadFixtures suites on both surfaces and RememberLookupTest, plus one JVM test that the shared wiring still filters kids-unresolvable queue rows.
Depends on: "failure-handling-one-way". Device (user): title-page preload, the preload queue as a kids profile, and a person page on the tablet and the box.

### 16. Cluster "process-globals-to-owned-state" (6 issues, init coupling)
Files: core/designsystem/src/main/kotlin/{Palette,Theme}.kt, ui-tv TvTheme.kt, TvFocus.kt and the 13 files reading Palette.Imprint; ui-common/src/main/kotlin/ui/catalog/RememberPortrait.kt, core/data/src/main/kotlin/PortraitRequestLog.kt, feature/catalog/src/main/kotlin/BrowseViewModel.kt and the 11 files threading the portrait request lambda; ui-mobile/src/main/kotlin/ui/player/PipController.kt and app MainActivity.kt.
Steps:
1. (small) Delete `var Palette.Imprint` and both SideEffect writes. ui-tv reads MaterialTheme.colorScheme.primary, which already holds the accent (TvTheme.kt:36), hoisted out of the two draw lambdas. TvFocus.textStyle takes an accent parameter (10 sites). Update TvThemeTest, TvLibraryRemoteTest and CatalogueColorsTest.
2. (small) Move RememberPortrait's three file-level maps into PortraitRequestLog, which becomes their single owner, created fresh per test. Track "in flight" separately from "done", so a second card for the same person waits instead of refetching. Rename the mutating shouldRequest to tryReserve, and the threaded parameter to match. RememberLookupTest drops its PersonId+1/+2 workarounds.
3. (small) Register addOnUserLeaveHintListener and addOnPictureInPictureModeChangedListener inside PipController's DisposableEffect (androidx.activity 1.13.0 has both; cast to ComponentActivity), and remove them in onDispose. Move isPipDismissal into ui-mobile and delete PipEntryPoint and the two MainActivity overrides.
Device (user): on the TV box, check focus borders and accent text at the current accent (do not change the accent during a walk). On the tablet, press Home during playback to enter PiP, then dismiss PiP and confirm playback stops. Check portraits on a cast list on both surfaces.

### 17. Cluster "build-deadwood" (2 issues, Dep health)
Files: gradle/libs.versions.toml, build-logic/convention (KotlinSerializationConventionPlugin.kt, AndroidApplicationConventionPlugin.kt, convention build.gradle.kts).
Steps:
1. (small) Delete the 39 unreferenced library rows, the 3 unused bundles (navigation3, media3-playback, android-test), the compose-stability-analyzer alias and the 28 version keys this orphans. Leave the ktlint alias and the spotless/detekt rows for the user's tooling decision.
2. (trivial) Add one comment beside `media3` naming core/ffmpeg and scripts/build-android-ffmpeg.sh as the re-vendor points.
3. (trivial, carried from the deferred tooling issue because it does not depend on that decision) Delete the unapplied app.kotlin.serialization convention and its registration, since no module needs the compiler plugin. Fix the false "Dependency Guard" header on AndroidApplicationConventionPlugin.
Tests: the check command proves the catalog still resolves. Device: none.

### 18. Cluster "comments-to-invariants" (8 issues, Contracts and AI generated debt; the docs-as-fix rework loop)
Files: MlibDataSource.kt:104-107, ByteSize.kt:31-32, WatchSnapshot.kt:24-26, CatalogRepository.kt:84, PlayerChoicesController.kt:178, feature/system/build.gradle.kts, core/playback/build.gradle.kts, AudioTrackSelection.kt:20, CatalogTabs.kt:39, ../docs/system-architecture.md (module map and ui-mobile packages), TvDepartmentsBar.kt, ContinueBand.kt, TvWall.kt:108-127, TvHome.kt:51-56/:105-113, SettingsScreen.kt:39-41, the 31 files carrying line-guideline provenance notes, OpenTitleSource.kt:17-19, CoreProvider.kt:46 and WatchSync.kt:121.
Steps:
1. (small) Correct the five stale sentences: refer to [CHUNK_BYTES] rather than restating sizes, drop the fixed-2-GiB claim, and remove the plan-phase reference.
2. (small) Correct the module contracts: feature:system now has a TV counterpart and uses 14 playback symbols. core/playback/build.gradle.kts states its real rule: pure playback rules live in core:playback, and media3 Tracks/Player glue lives in feature:player. Fix the FFmpeg-extension comment and the OverflowMenu link. In system-architecture.md, add feature:stats and baselineprofile and fix the ui-mobile package list.
3. (small) Rewrite TvDepartmentsBar's three docs (the blend comes from rememberTvCatalogBlend) and cut ContinueBand's sentence. Trim the TvWall, TvHome and SettingsScreen narration to the invariant: no "earlier version", "used to" or "round-2 mockups".
4. (small) Delete the "split out of / kept out of … line guideline" clauses and the ActivePlayback pointers in the 31 files, keeping the sentence that says what each file holds. Comments only; re-merge nothing.
5. (trivial) OpenTitleSource: "written only on the player's own thread (main); a StateFlow snapshot any thread may read". coreOrNull's KDoc says it builds the core on first ask, plus a one-line comment at WatchSync.kt:121. No renames.
No codebase-wide comment sweep; other narration is trimmed when its file is next touched. Device: none.
Depends on: clusters 11, 12, 14 and 15, so renamed and edited files are trimmed once.

### 19. Cluster "single-package-move-pass" (12 issues, Structure nav, High elegance and Convention drift; LAST)
Steps, each its own compiler-driven commit, small moves first:
1. (small) Move the 9 TV Settings files to ui-tv/src/main/kotlin/ui/tv/settings (package ui.tv.settings), with TvInfoBlock going to settings, plus their 2 tests. TvSystemSection and TvMenuPage stay in system. Rename TvAppearanceBlock → TvAppearanceSection, TvProfileBlock → TvProfileSection, and the function TvSystemContent → TvSystemSection, which matches its file.
2. (trivial) File renames: ui-mobile catalog/UpdateLibrary.kt → FetchResultDialog.kt, and TvTitleDetails.kt → TvFilmOverview.kt. Keep TvMoviesPage, which matches the phone's naming.
3. (trivial) Move AppChrome.kt and OverflowMenu.kt into ui-mobile ui/chrome and fix the ProfilePickerScreen KDoc link. Move TvSafeArea beside TvFocus, and TvMoviesPageEntryKey into catalog/TvCatalogNav.kt.
4. (small) Move Destination.kt and DestinationTest from feature:catalog into ui-mobile, and replace MenuScreen.destination with a `when` in ui-mobile.
5. (trivial) Move feature/player's player/ directory's 2 files up to the root, which is the same package, and move AppearanceModule and AchievementsSeenModule into di/.
6. (medium) Rename core/data's bare `settings` package to `data.settings` (47 importers).
7. (medium) Rename ui-common's packages to ui.common.* (30 files), fixing the imports the compiler then asks for in ui-mobile and ui-tv. This settles both directions of the shared namespace: ui-mobile keeps ui.*.
Coordinate timing with other sessions committing to main, because steps 6 and 7 touch imports across most UI files. Device (user): smoke launch, Settings and System on the tablet and the box.

## Contradictions resolved between verifiers and strategy
- Portrait request naming: one verifier wanted shouldRequest renamed, another said skip renames. Resolved in cluster 16: the log's API is reshaped there anyway, so the rename comes with it. ProgressThrottle.shouldEmit and the other renames stay as they are.
- core/data `settings` package: one verifier said batch the rename into the package pass, another said leave it. Resolved: include it as step 6 of the final pass, which already rewrites imports module-wide, and where it is the only module whose directory does not map to its package root.
- Shared ui namespace: renaming ui-mobile (152 files) is rejected as churn. Renaming ui-common (30 files) resolves the overlap for all three namespace issues, so they share one step.
- Playback bindings: the strategy proposed Hilt in core:playback. The verifiers showed that contradicts a documented decision, and that provideFilmPreloader needs feature:player types. Resolved: LanCacheModule and the counters/held-sets providers move to app/di.
- Generated records: one verifier preferred a doc-only rule, two preferred the narrow TitleInfo/SearchHit mirror. Resolved: do the narrow, repository-local mirror (cluster 9), and leave the module-wide rule to the user.
- PlayerViewModel: the strategy proposed letting the UI drive controllers directly and deleting the forwarders. The verifiers showed that makes PlayerChoicesController and four private sub-controllers public API for three UI modules, and that any member-based fix breaks the 200-line guideline. Resolved: deferred to the user, with the recommendation to keep the facade.

## Backlog Decisions
- auto/ktlint_violation -> skip "generated UniFFI binding only, 100% false positive on sampling and profiling: all 2906 items sit in core/rust/.../mediagram_core.kt, which the scan config excludes and every core build regenerates; the ktlint tooling itself is a user decision"
- auto/test_coverage-untested_module -> skip "100% false positive per observe: 19 Gradle build-logic files verified by every build, 3 Hilt @Module objects, and NaturalOrder.kt, which NaturalOrderTest already tests in the same package"
- auto/stale_exclude -> skip "100% false positive per observe: all 16 are deliberate build-output, native-library or generated-binding excludes, and the uniffi exclude is load-bearing"
- auto/test_coverage-transitive_only -> skip "CoreErrors.kt has CoreErrorsTest (false positive); StartOverAction.kt is a logic-free Compose dialog whose reset is tested in ProfileResetTest, and per-screen Compose tests are the value trap the strategy names"
- Unclustered detector items (2714) -> not promoted. Same-module Kotlin file cycles are harmless. The cycles that carry meaning are handled by review clusters: the mobile chrome cycle by the package pass, the PlayerViewModel cycle by the user's decision, and the ui.tv root cycle partly by moving the two misplaced symbols. Entry-point orphans, androidTest/gradle-script coverage items and boilerplate windows over intentional phone/TV layout are scanner classification noise. Organize may resolve them as false positives by pattern, with no code change. Rescan after the package pass.

## Skip Decisions

User decisions. None of these becomes work; each waits for the user:
- Skip "user-decision-ktlint" (1): apply the Spotless/Detekt/ktlint conventions to every module and run one format-only commit, or delete the unapplied conventions and purge the ktlint carry. If Detekt is kept, its config path must be fixed first (config/detekt/detekt.yml). The decision-independent part (the dead serialization convention and one false header) is carried in "build-deadwood".
- Skip "user-decision-secret-storage" (2): migrate the five secret stores off the deprecated security-crypto 1.1.0, which must read through the old library once or every device signs out, or keep it with a written exit plan and a reasoned @Suppress.
- Skip "user-decision-lan-pairing" (1): should LAN reads require a paired token? Today an unpaired device reads from any responder, and the server is open for reads by design. The two docs that claim otherwise are fixed after the answer. The write-gate half is carried in "lan-cache-store-failures".
- Skip "user-decision-m3-adaptive-pin" (1): pin a stable material3-adaptive, or document why 1.3.0-rc01 ships in release.
- Skip "user-decision-player-viewmodel-size" (6): any member-based or state-ownership restructure pushes PlayerViewModel.kt (exactly 200 lines) past the user's 200-line guideline. Never re-merge. The one shape that stays near 200 lines is a small opener class that owns open/retry and the flows they write. Ask the user before doing it.
- Skip "user-decision-uniffi-surface" (2): choose one module-wide rule for how far generated UniFFI records travel. Either mirror all of them, route the 13 direct awaitCore calls through core:data and let `implementation(core:rust)` enforce it, or declare the binding records the shared read model. A live connection flag or an encrypted auth key would also be core/Rust changes.
- Skip "user-decision-start-over-lan-token" (1): should Start over clear the LAN pairing token, or does it survive as device-owned (write that down)? The orphaned package-key deletion is carried in "secret-stores-main-safe".

On the merits:
- Skip "player-facade-keeps-controllers-private" (2): the 13 forwarders are the ViewModel's facade. Removing them makes the controller split public API of feature:player for three UI modules.
- Skip "interface-defaults-serve-test-doubles" (2): every interface with a default has one production implementation that overrides all of it (FilmPreloading.Noop relies on its defaults correctly). Removing the defaults pushes stubs into about 15 test doubles.
- Skip "test-fixture-knobs-no-production-impact" (1): FakeCore's overlapping knobs are test-only and documented; fold them when auth/refresh tests next change.
- Skip "explicit-compose-parameters-are-idiomatic" (1): moving menu/profile/browse into CompositionLocals would hide dependencies.
- Skip "restore-helper-fits-half-the-sites" (1): only about 20 of the 38 restore sites fit the helper shape, and it would not prevent a forgotten key.
- Skip "cache-provider-global-encodes-simplecache-limit" (1): the global is correct double-checked locking and encodes SimpleCache's one-instance-per-directory rule. DI would change about 10 signatures and tests.
- Skip "harmless-unused-result-fold-when-touched" (2): one unreachable elvis forced by a nullable type, and one discarded return value. Fold them into the next edit of those files.
- Skip "correct-five-line-loop-already-commented" (1): WatchSync's loop condition is correct, short and explained at :77-79.
- Skip "explicit-short-resets-no-missed-field" (1): the resets are explicit, and a shared remember helper would couple four independent controllers to save about 8 lines.
- Skip "rename-churn-without-misleading-reader" (7): each rename touches 13-167 references, and every current name already says what it is. Rename on next touch.
- Skip "web-reference-uses-the-same-collection-split" (1): the web player and the core API use the same two meanings of "collection". An Android-only rename would create a parity divergence.
- Skip "subpackaging-churn-for-navigation-only" (4): name prefixes already group the files, these are same-module package cycles Kotlin compiles fine, and the import rewrites span all UI modules.
- Skip "validated-on-load-two-local-literals" (1): cue backing is validated at load and the two literals sit beside the option list, covered by CueStyleTest.
- Skip "two-comparisons-in-one-module" (1): an enum plus a parse step for two comparisons in feature:system adds more than it removes.

## Recurring patterns (why these clusters, not per-dimension queues)
- error_consistency (12 resolved, 7 new): last cycle's per-site cancellation fixes created the helper copies. "failure-handling-one-way" fixes this once at the root, with one helper in the lowest common module. Its guard: inline catches that do real work are not swept, so the loop does not restart.
- contract_coherence (3 resolved, 10 new) and api_surface_coherence (6 resolved, 7 new): KDoc used as the fix drifted. This cycle states contracts in code first: the dispatcher lives inside the store (cluster 3), release() is deleted and preference names become constants (cluster 11), limitedParallelism replaces a prose precondition, and an `internal` replaces a doc warning (cluster 7). Only then does cluster 18 trim prose to invariants.
- mid_level_elegance (9 resolved, 6 new): two roots. The line-budget PlayerViewModel split goes to the user. Display-string department identity is fixed as one change across both surfaces (cluster 12), keyed the way the web is.
- package_organization (2 resolved, 9 new): piecemeal moves churn imports repeatedly. All moves happen in one final pass (cluster 19), after content clusters stop editing those files.
- test_strategy (7 resolved, 4 new): copied fixture locators and fakes drift. One shared locator and shared fakes, plus androidTest compilation in the gate (cluster 6), run before the identity work they protect.
- Design coherence, Type safety and the elegance dimensions recur through the same three roots: department strings, twin glue and TV identity. That is why clusters 12-15 are each one root cause across phone and TV, never one surface at a time.

## Device validation the user owns
Visible behaviour changes in clusters 1, 2, 4, 7 (conditional), 11, 12, 13, 14, 15 and 16 are unfinished until the user checks them on the phone/tablet and the TV box. Clusters 3, 9, 10 and 19 need a smoke check only. Walks navigate only and change no settings, and nobody runs Start over on a real device.

