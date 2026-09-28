## Phase Implementation Report

### Executed Phase
- Phase: phase-01-index-categories-and-uploader-flags
- Plan: plans/260928-2334-tutorial-and-documentary-categories
- Status: completed

### Files Modified
Created:
- `crates/mlib-spec/src/category_key.rs` (30 lines) — `category_key(kind, show, title)`.
- `crates/mlib-spec/tests/shared_category_keys.rs` — runs `web/test/fixtures/categories/keys.json`.
- `web/test/fixtures/categories/keys.json` — 9 cases incl. umlaut slug taken from a real test run, not hand-typed.
- `crates/mediagram/src/index/categories.rs` (+`categories_tests.rs`) — `get`/`set`/`in_use`.
- `crates/mediagram/src/index/merge_categories.rs` — last-writer-wins merge.
- `crates/mediagram/src/edit/category.rs` (179 lines, +`category_tests.rs`) — `normalise`, `planned`, `write`, `key_for`, `unit_label`, `run`.
- `crates/mediagram/src/commands/args_edit.rs` — `EditArgs` moved out of `args.rs`.

Modified: `crates/mlib-spec/src/{lib.rs,schema.rs}` (V12, SCHEMA_VERSION 12, READABLE_SCHEMAS); `crates/mediagram/src/index/{mod.rs,merge.rs}`; `crates/mediagram/src/channel_index/report.rs`; `crates/mediagram/src/commands/{args.rs,args_docu.rs,edit.rs,add_course.rs,add_docu/mod.rs,add_docu/collection.rs}`; `crates/mediagram/src/edit/mod.rs`; `crates/mediagram/src/{cli_tests.rs,index/merge_tests.rs}`; `crates/mediagram/tests/schema_migrations.rs`; `web/src/catalog.ts` (EXPECTED_SCHEMA=12); `docs/{mlib-spec.md,system-architecture.md,mlib-package-v1.md,project-changelog.md}`; `README.md`; `CONTEXT.md`; `Cargo.toml`, `Cargo.lock`, `web/package.json`, `android/app/build.gradle.kts` (0.78.0→0.79.0).

### Tasks Completed
All 10 implementation steps + both plan/phase-frontmatter status updates.

### Tests Status
- Type check / clippy: pass (`-D warnings`)
- Unit tests: pass — new tests in `categories_tests.rs`, `category_tests.rs`, `shared_category_keys.rs`, `merge_tests.rs` (6 new merge cases), `cli_tests.rs` (2 new), `schema_migrations.rs` (1 new v11→v12 upgrade case)
- `scripts/check.sh`: **all checks passed** (clippy, `cargo test --all`, bun lint/test, gradle test/lint)
- One flaky failure hit once in `mediagram-core`'s `artwork_fetch.rs` (`a_rejected_key_is_still_rejected_after_a_successful_run`, a pre-existing rustls-crypto-provider test-parallelism race, unrelated to this phase — `mediagram-core` was not touched); passed on immediate re-run and in the final `check.sh` pass.

### Design decisions beyond the phase's literal text
- `write(conn, planned)` performs the DB-aware adoption itself (re-running `normalise` against real `in_use`), rather than being a dumb setter — `planned()` has no connection to check `in_use` against, so adoption has to happen somewhere with one; `edit::run` also resolves adoption itself first (for the printed note and the "already says" check) then calls `write`, which redundantly but harmlessly re-normalises an already-normalised name.
- `edit::category::run`'s "set … is a movie; categories are for courses and documentaries" / "has no name …" refusals are row-specific (`key_for`); `planned()`'s own refusal for the same underlying cause (a name that slugs to nothing) is worded generically since `add-course`/`add-docu` have no set id yet — not verbatim-specified by the phase, a reasonable extension of its wording.
- Adoption note wording ("using the existing spelling … instead of …") is invented; the phase specifies the refusal/no-op message texts exactly but not the note's.

### Issues Encountered
None — no file-ownership conflicts, phase text matched the codebase as read.

### Next Steps
Phase 2 (web) and phase 3 (Android) unblocked; both read this table starting from schema v12.
