# Focused Rust delivery commits

Status: DONE

Created 15 local commits on `desloppify/quality-20260923`, from `df88713` through `55f8ab2`. All 225 committed paths belong to the delegated Rust/manifests scope. No push, PR, scanner-state action, branch switch, or unrelated staging occurred.

## Version and preservation

- Cargo workspace, web package, and Android versionName are synchronized at **0.40.2**. Android versionCode remains **5**.
- Cargo.lock changes only the version of `mediagram`, `mediagram-core`, `mediagram-tmdb`, and `mlib-spec`; no dependency changes.
- The original 0.40.1 version intent was advanced as instructed. Browser behavior, original changelog work, Android behavior changes, docs, plans, generated Kotlin bindings, and ignored local state were not staged by this task.
- Repository/readme/keywords/categories metadata was included as explicitly approved by the controller.

## Verification

- Recorded source delivery evidence: `.desloppify/rust-delivery-test.log` sums to **969 passed, 0 failed, 4 intentional ignores**. Existing strict workspace Clippy evidence is `.desloppify/rust-delivery-clippy.log`.
- Commit preparation did not modify Rust sources. It only advanced versions, staged existing verified changes, and wrote this report.
- The 97 formatting-only files were verified byte-for-byte against rustfmt applied to their HEAD content before staging.
- Twelve staged snapshots, from the state-sync commit through the final TMDB source-documentation commit, pass `cargo check --workspace --all-targets --all-features --locked --offline` in an isolated source export. Each subsequent commit was checked before creation, so later unstaged fixes were unavailable to its build.
- Final working-tree `cargo fmt --all -- --check` and `git diff --check df88713..HEAD` pass.
- Exact staged path allowlists, whitespace checks, and credential-pattern scans passed before every commit. A final added-line audit found no credential-name string assignments.
- Final index is empty. `crates/`, Cargo.toml, Cargo.lock, web/package.json, and android/app/build.gradle.kts are clean.

Snapshot check logs remain under `/tmp/mediagram-check-<group>.log`. The temporary source export was removed after all checks; every owned build process completed. The shared Cargo target cache was retained.

## Commits and exact paths

### `acae0b5` — chore(release): synchronize version 0.40.2 and package metadata

8 files:

```text
Cargo.lock
Cargo.toml
android/app/build.gradle.kts
crates/mediagram-core/Cargo.toml
crates/mediagram-tmdb/Cargo.toml
crates/mediagram/Cargo.toml
crates/mlib-spec/Cargo.toml
web/package.json
```

### `64415e9` — style(rust): apply workspace rustfmt conventions

97 files:

```text
crates/mediagram-core/src/api/account/revoked.rs
crates/mediagram-core/src/api/account/session.rs
crates/mediagram-core/src/api/channel/index_tests.rs
crates/mediagram-core/src/api/channel/install.rs
crates/mediagram-core/src/api/enrich/artwork.rs
crates/mediagram-core/src/api/enrich/artwork_tests.rs
crates/mediagram-core/src/api/enrich/details.rs
crates/mediagram-core/src/api/enrich/details_tests.rs
crates/mediagram-core/src/api/enrich/fetch.rs
crates/mediagram-core/src/api/enrich/fetch_tests.rs
crates/mediagram-core/src/api/read.rs
crates/mediagram-core/src/api/refresh/download.rs
crates/mediagram-core/src/api/store.rs
crates/mediagram-core/src/api/store_tests.rs
crates/mediagram-core/src/catalog.rs
crates/mediagram-core/src/connection_params.rs
crates/mediagram-core/src/package/reader.rs
crates/mediagram-core/src/shows/sidecar.rs
crates/mediagram-core/src/shows/sidecar_tests.rs
crates/mediagram-core/src/state/channel.rs
crates/mediagram-core/src/state/exchange_tests.rs
crates/mediagram-core/src/state/lists_exchange_tests.rs
crates/mediagram-core/src/state/record/hostile_json.rs
crates/mediagram-core/src/state/record/list_record.rs
crates/mediagram-core/src/state/record/list_record_tests.rs
crates/mediagram-core/src/state/rows.rs
crates/mediagram-core/src/state/rows_tests.rs
crates/mediagram-core/src/state/schema.rs
crates/mediagram-core/src/transport/document.rs
crates/mediagram-core/src/transport/documents.rs
crates/mediagram-core/src/updates.rs
crates/mediagram-core/src/versions/identity.rs
crates/mediagram-core/tests/age_ratings_in_catalog.rs
crates/mediagram-core/tests/artwork_fetch.rs
crates/mediagram-core/tests/catalog_facts.rs
crates/mediagram-core/tests/dto_mapping.rs
crates/mediagram-core/tests/fetch_cache.rs
crates/mediagram-core/tests/fetch_stub/mod.rs
crates/mediagram-core/tests/package_cipher.rs
crates/mediagram-core/tests/range_plan.rs
crates/mediagram-core/tests/shared_channel_update_fixtures.rs
crates/mediagram-core/tests/shared_watch_state_fixtures.rs
crates/mediagram-core/tests/shows_query.rs
crates/mediagram-tmdb/src/certification.rs
crates/mediagram-tmdb/src/poster_files.rs
crates/mediagram-tmdb/src/posters.rs
crates/mediagram-tmdb/src/tmdb_client.rs
crates/mediagram/src/commands/add.rs
crates/mediagram/src/commands/background.rs
crates/mediagram/src/commands/edit.rs
crates/mediagram/src/commands/export_package.rs
crates/mediagram/src/commands/prepare/report.rs
crates/mediagram/src/commands/rescan.rs
crates/mediagram/src/config.rs
crates/mediagram/src/config_tests.rs
crates/mediagram/src/course/report.rs
crates/mediagram/src/course/walk.rs
crates/mediagram/src/edit/plan.rs
crates/mediagram/src/export/publish.rs
crates/mediagram/src/index/columns.rs
crates/mediagram/src/index/parts.rs
crates/mediagram/src/index/rescan_parts.rs
crates/mediagram/src/media/ffmpeg_progress_tests.rs
crates/mediagram/src/media/mod.rs
crates/mediagram/src/media/probe.rs
crates/mediagram/src/media/show_episodes.rs
crates/mediagram/src/media/streams.rs
crates/mediagram/src/metadata/lookup.rs
crates/mediagram/src/metadata/resolve.rs
crates/mediagram/src/upload/finish_set.rs
crates/mediagram/src/upload/part_upload.rs
crates/mediagram/src/upload/plan_set.rs
crates/mediagram/src/upload/progress.rs
crates/mediagram/tests/code_standards.rs
crates/mediagram/tests/course_documents_walk.rs
crates/mediagram/tests/course_report_tree.rs
crates/mediagram/tests/course_sidecars.rs
crates/mediagram/tests/export_budget.rs
crates/mediagram/tests/export_posters.rs
crates/mediagram/tests/export_publish.rs
crates/mediagram/tests/export_snapshot_is_read_only.rs
crates/mediagram/tests/export_titles_and_pointer.rs
crates/mediagram/tests/index_pin_bookkeeping.rs
crates/mediagram/tests/index_rescan.rs
crates/mediagram/tests/index_snapshot.rs
crates/mediagram/tests/live_tmdb.rs
crates/mediagram/tests/shared_playable_sql.rs
crates/mediagram/tests/snapshot_open_accepts_older_schema.rs
crates/mediagram/tests/sqlite_init_order.rs
crates/mediagram/tests/status_report.rs
crates/mediagram/tests/support/tmdb.rs
crates/mediagram/tests/tmdb_resolve.rs
crates/mediagram/tests/upload_finish.rs
crates/mediagram/tests/verify_report.rs
crates/mlib-spec/tests/filename_grammar.rs
crates/mlib-spec/tests/package_format.rs
crates/mlib-spec/tests/part_name.rs
```

### `7776185` — fix(spec): reject oversized plans and clarify wire contracts

15 files:

```text
crates/mlib-spec/src/caption.rs
crates/mlib-spec/src/caption_codec.rs
crates/mlib-spec/src/caption_codec_tests.rs
crates/mlib-spec/src/filename.rs
crates/mlib-spec/src/ids.rs
crates/mlib-spec/src/index_caption.rs
crates/mlib-spec/src/kind_spelling.rs
crates/mlib-spec/src/package/mod.rs
crates/mlib-spec/src/package/naming.rs
crates/mlib-spec/src/part_name.rs
crates/mlib-spec/src/part_plan.rs
crates/mlib-spec/src/schema.rs
crates/mlib-spec/src/slug.rs
crates/mlib-spec/tests/part_plan.rs
crates/mlib-spec/tests/schema_migrations.rs
```

### `ea8d6ad` — fix(core): preserve state sync transactions and retry semantics

21 files:

```text
crates/mediagram-core/src/api/state.rs
crates/mediagram-core/src/api/state_sync.rs
crates/mediagram-core/src/api/state_sync/publish.rs
crates/mediagram-core/src/api/state_sync/publish_tests.rs
crates/mediagram-core/src/api/state_sync/telegram_channel.rs
crates/mediagram-core/src/api/state_sync/telegram_channel_tests.rs
crates/mediagram-core/src/state/exchange.rs
crates/mediagram-core/src/state/lists.rs
crates/mediagram-core/src/state/lists_exchange.rs
crates/mediagram-core/src/state/lists_tests.rs
crates/mediagram-core/src/state/merge.rs
crates/mediagram-core/src/state/migration_tests.rs
crates/mediagram-core/src/state/mod.rs
crates/mediagram-core/src/state/profiles.rs
crates/mediagram-core/src/state/record.rs
crates/mediagram-core/src/state/sync.rs
crates/mediagram-core/src/state/sync/device.rs
crates/mediagram-core/src/state/sync/device_tests.rs
crates/mediagram-core/src/state/sync/error.rs
crates/mediagram-core/src/state/sync_tests.rs
crates/mediagram-core/tests/api_surface.rs
```

### `695e45f` — fix(core): reject stale login completions and retain error causes

9 files:

```text
crates/mediagram-core/src/api/account/auth.rs
crates/mediagram-core/src/api/account/auth_attempt.rs
crates/mediagram-core/src/api/account/auth_tests.rs
crates/mediagram-core/src/api/account/profile.rs
crates/mediagram-core/src/api/account/subscribe.rs
crates/mediagram-core/src/api/events.rs
crates/mediagram-core/src/api/mod.rs
crates/mediagram-core/src/error.rs
crates/mediagram-core/src/error_tests.rs
```

### `09371d4` — fix(core): validate catalogs before publishing refreshed versions

3 files:

```text
crates/mediagram-core/src/api/refresh/mod.rs
crates/mediagram-core/src/api/refresh/refresh_tests.rs
crates/mediagram-core/src/versions/install.rs
```

### `a2a6e66` — fix(core): cancel abandoned transfers and verify blocking dispatch

5 files:

```text
crates/mediagram-core/src/api/blocking.rs
crates/mediagram-core/src/api/blocking_tests.rs
crates/mediagram-core/src/transport/fetch.rs
crates/mediagram-core/src/transport/fetch_tests.rs
crates/mediagram-core/src/transport/stream.rs
```

### `f082606` — refactor(core): name library registration by its side effect

4 files:

```text
crates/mediagram-core/src/api/channel/library.rs
crates/mediagram-core/src/api/channel/library_tests.rs
crates/mediagram-core/src/api/channel/mod.rs
crates/mediagram-core/src/api/read_tests.rs
```

### `2d69b4d` — fix(index): validate persisted schema and provider identifiers

30 files:

```text
crates/mediagram-core/src/shows/mod.rs
crates/mediagram-core/tests/shows_upsert_covers_schema.rs
crates/mediagram/src/commands/setup.rs
crates/mediagram/src/edit/captions.rs
crates/mediagram/src/export/stage.rs
crates/mediagram/src/index/db.rs
crates/mediagram/src/index/db_tests.rs
crates/mediagram/src/index/migrations.rs
crates/mediagram/src/index/mod.rs
crates/mediagram/src/index/rescan.rs
crates/mediagram/src/index/set_lookup.rs
crates/mediagram/src/index/set_row.rs
crates/mediagram/src/index/sets.rs
crates/mediagram/src/index/snapshot.rs
crates/mediagram/src/paths.rs
crates/mediagram/src/telegram/client.rs
crates/mediagram/src/upload/lock.rs
crates/mediagram/src/upload/pipeline.rs
crates/mediagram/src/upload/plan.rs
crates/mediagram/tests/catalog_over_uploader_index.rs
crates/mediagram/tests/course_identity.rs
crates/mediagram/tests/edit_plan.rs
crates/mediagram/tests/index_path_column.rs
crates/mediagram/tests/index_state.rs
crates/mediagram/tests/index_update_metadata.rs
crates/mediagram/tests/remove_plan.rs
crates/mediagram/tests/serve_http.rs
crates/mediagram/tests/support/export.rs
crates/mediagram/tests/support/upload.rs
crates/mediagram/tests/upload_pipeline.rs
```

### `a9a7ba4` — fix(upload): resume valid sets without duplicating pending uploads

12 files:

```text
crates/mediagram/src/commands/add_course.rs
crates/mediagram/src/commands/add_show/mod.rs
crates/mediagram/src/commands/finish_set.rs
crates/mediagram/src/commands/push_index.rs
crates/mediagram/src/commands/resume.rs
crates/mediagram/src/telegram/index_publish.rs
crates/mediagram/src/telegram/mod.rs
crates/mediagram/src/upload/finish.rs
crates/mediagram/src/upload/mod.rs
crates/mediagram/src/upload/resume.rs
crates/mediagram/tests/add_show.rs
crates/mediagram/tests/upload_resume.rs
```

### `e16aefc` — test(media): exercise preparation and survey decision boundaries

7 files:

```text
crates/mediagram/src/commands/add_show/survey.rs
crates/mediagram/src/commands/add_show/survey_tests.rs
crates/mediagram/src/commands/prepare/mod.rs
crates/mediagram/src/commands/prepare/rewrite.rs
crates/mediagram/src/commands/prepare/rewrite_tests.rs
crates/mediagram/src/media/prepare/plan.rs
crates/mediagram/tests/prepare_report.rs
```

### `6915158` — test(verify): cover remote metadata and download failure boundaries

5 files:

```text
crates/mediagram/src/verify/download_hash.rs
crates/mediagram/src/verify/mod.rs
crates/mediagram/src/verify/session.rs
crates/mediagram/src/verify/session_tests.rs
crates/mediagram/src/verify/source.rs
```

### `8874cd5` — fix(cli): preserve connection cleanup on command errors

7 files:

```text
crates/mediagram/src/cli_tests.rs
crates/mediagram/src/commands/login_code.rs
crates/mediagram/src/commands/serve.rs
crates/mediagram/src/commands/smoke_upload.rs
crates/mediagram/src/commands/status/mod.rs
crates/mediagram/src/commands/whoami.rs
crates/mediagram/src/main.rs
```

### `c731c76` — fix(course): report sidecar metadata lookup failures

1 files:

```text
crates/mediagram/src/course/sidecars.rs
```

### `55f8ab2` — docs(tmdb): describe empty search cache behavior

1 files:

```text
crates/mediagram-tmdb/src/disk_cache.rs
```

## Remaining owner scope

The uncommitted tree still includes browser/server work, Android source and generated bindings, root/docs/plan updates, scripts, skill inventory, and .gitignore work. Those remain with their current owners. The controller owns Desloppify commit-log attribution and any final publication decisions.

The generated Kotlin bindings remain intentionally outside this Rust commit batch; their compatible changes belong with the pending Android delivery.

Unresolved questions: none.
