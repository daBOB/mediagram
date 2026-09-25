# Sixth Rust coverage attribution reconciliation

Status: DONE

All eight recurrent `test_coverage::transitive_only` findings have existing
behavior coverage. Their tests call the production boundaries and assert state,
filesystem, returned-error or CLI-output effects; none of the mappings below
relies solely on an import. This supports attribution false-positive
dispositions, not a claim of exhaustive branch coverage or a source repair.

I read the current target files, test bodies, module declarations and callers.
The latest `/tmp/rust-retirement-final-tests.log` confirms the named tests pass;
summing its result records gives **1,025 passed, 0 failed, 4 ignored**. No test,
source, manifest or scanner state was changed, and no Cargo or scanner command
was run. This report supersedes older log references for these mappings.

## Current mappings

1. **`crates/mediagram-core/src/api/account/auth_attempt.rs`**

   [auth_tests.rs](../../../crates/mediagram-core/src/api/account/auth_tests.rs)
   calls the actual `Attempt::begin/resume/complete/persist` and production
   `finish_password` boundary. `password_success_after_sign_out_does_not_persist_or_panic`
   gates a response across sign-out and checks sanitized refusal and absent
   authorization. `an_old_client_cannot_persist_over_a_replacement_even_with_the_same_attempt_marker`
   independently checks connection identity by retaining the attempt marker and
   verifying the replacement's actual persisted key survives. The current-owner
   counterpart, `the_current_password_step_can_retry_then_persist_its_own_session`,
   asserts retained retry token and exact persisted key. `every_login_result_is_checked_before_it_can_be_interpreted`
   exercises six result variants against an obsolete attempt. Latest log lines
   **934–935, 949, 981**; additional refusal/new-attempt cases are also green.

2. **`crates/mediagram-core/src/api/account/session_updates.rs`**

   [events_lifecycle_tests.rs](../../../crates/mediagram-core/src/api/events_lifecycle_tests.rs)
   tests the actual `session` re-exports from this file, not duplicate helpers.
   `a_replaced_subscription_does_not_consume_the_new_connections_receiver`
   replaces a connection during production subscription/open, then checks old
   identity rejection and that the replacement receiver remains available.
   `an_old_dropped_listener_does_not_discard_a_concurrent_replacement` actively
   waits through production `next`, replaces its connection, then verifies
   identity, receiver and authorization retention. `dropped_listening_can_run_the_entire_subscription_and_open_sequence_again`
   verifies invalidation creates a distinct connection and consumes its receiver
   exactly once. Latest log lines **964, 967, 972**.

3. **`crates/mediagram-core/src/api/events/listener.rs`**

   The same [lifecycle suite](../../../crates/mediagram-core/src/api/events_lifecycle_tests.rs)
   enters actual `listener::next` through `events::next` and a real in-memory
   grammers UpdateStream. `concurrent_revocation_wakes_an_active_listener_and_releases_its_lock`
   proves the listener is waiting, then revokes it and asserts bounded completion,
   empty listener/client slots and removed authorization. `waiting_errors_share_revocation_handling_without_relocking_the_listener`
   directly calls production `finish_wait` while holding its required slot guard,
   checking 401 cleanup versus ordinary-error retention. The dropped/replaced
   listener cases above additionally test identity-safe invalidation. Latest log
   lines **953, 967, 972, 993**. This mapping does not claim every debounce timing
   branch is exercised by these lifecycle cases.

4. **`crates/mediagram-core/src/api/events/open.rs`**

   [events_lifecycle_tests.rs](../../../crates/mediagram-core/src/api/events_lifecycle_tests.rs)
   uses production `open::with_subscription` with only the RPC response
   controlled; it constructs the real stream. `dropped_listening_can_run_the_entire_subscription_and_open_sequence_again`
   verifies successful reopening. `stream_initialization_errors_release_the_consumed_receiver_and_keep_error_kind`
   sends raw initialization errors through production `open::finish`, asserting
   consumed-connection release, 401 revocation, ordinary-error key retention and
   successful retry. `a_late_stream_initialization_revocation_keeps_the_replacement_login`
   verifies old initialization failure cannot erase the replacement key/receiver.
   Latest log lines **955, 972, 982**.

5. **`crates/mediagram-core/src/api/state.rs`**

   [api_surface.rs](../../../crates/mediagram-core/tests/api_surface.rs) invokes
   exported `Core` methods against actual temporary SQLite storage:
   `watch_state_is_profile_scoped_except_kids_and_survives_reopening`,
   `a_collection_can_only_be_changed_by_its_owner` and
   `unavailable_state_storage_returns_safe_defaults_and_can_be_retried` verify
   profile isolation, global Kids, ordered collections, ownership refusals,
   reopening, safe defaults and retry after removing a real storage obstruction.
   Latest log lines **1148, 1150–1151**.

   The newly added export is also covered by
   [state_retirement.rs](../../../crates/mediagram-core/tests/state_retirement.rs):
   `retired_unopened_core_cannot_create_storage_from_a_queued_write`,
   `retired_open_core_cannot_restore_deleted_storage_from_a_queued_write` and
   `replacing_a_retired_core_preserves_profiles_without_reviving_its_owner`.
   These poll actual `Core::create_profile` into a held blocking pool, call the
   exported retirement method, release/delete storage and assert no old-owner
   recreation; the replacement case verifies legitimate persisted data remains.
   Latest log lines **1303–1305**. Their red mutation evidence remains in
   [the retirement report](rust-local-state-retirement.md).

6. **`crates/mediagram-core/src/state/schema.rs`**

   [migration_tests.rs](../../../crates/mediagram-core/src/state/migration_tests.rs)
   uses actual schema statements to create populated v1 storage, then production
   `migrate/open` consumes the remaining statements. `populated_v1_rows_and_collection_order_survive_migration_and_reopening`
   asserts retained rows/timestamps/order, tombstone defaults, collection timestamp
   backfill and schema version after reopening. `a_later_migration_conflict_rolls_back_earlier_schema_changes_and_version`
   induces a real later ALTER conflict and checks earlier ALTER rollback, old
   version/data preservation and successful retry. Latest log lines **1066, 1075**.

7. **`crates/mediagram-core/src/state/sync/error.rs`**

   [sync_tests.rs](../../../crates/mediagram-core/src/state/sync_tests.rs), module
   `typed_failures`, exercises the actual private round and public outcome.
   `channel_causes_survive_the_private_round_without_being_formatted` inspects
   `SyncError::Channel`'s original list/send cause and counts Display calls:
   zero inside `round`, one at `once`'s public failure conversion.
   `inaccessible_storage_retains_distinct_import_and_read_messages` uses a real
   blocking file to exercise `Import` and `Read`, their distinct messages, no
   publication/memo advancement and unchanged obstruction. Latest log lines
   **1087, 1103**. The concrete `Serialization` error variant is not forced by
   these tests; claiming coverage of that unreachable-with-current-record-shape
   failure would overstate the evidence.

8. **`crates/mediagram/src/commands/prepare/report.rs`**

   [prepare_report.rs](../../../crates/mediagram/tests/prepare_report.rs) executes
   the compiled CLI, whose `prepare::run` calls the actual `print_table` and
   `warn_about_video_codecs`. `dry_run_reports_each_verdict_and_preserves_original_bytes`
   uses real ffmpeg/ffprobe fixtures and checks all four verdicts, dropped-stream
   columns, summary/part limit, Unicode truncation and unchanged input/no output
   artifacts. `mp4_dry_run_warns_about_unfixable_video_but_not_wrapper_or_audio`
   checks two unsupported-video files produce the deduplicated video reason,
   excluding fixable wrapper/audio reasons while preserving both files.
   Latest log lines **624–625**, suite result **627**: 2 passed, none ignored.
   The helper requires ffmpeg unless an explicit missing-tool opt-out is set;
   `/usr/bin/ffmpeg` is present in this environment.

## Disposition boundary

No real absence of behavior tests was found for these eight modules. These are
supported attribution false positives under the current source/test layout.
That conclusion does not authorize exclusions, permanent suppression, score
editing, or treating every future change in these modules as covered. Root owns
any supported scanner dispositions separately.
