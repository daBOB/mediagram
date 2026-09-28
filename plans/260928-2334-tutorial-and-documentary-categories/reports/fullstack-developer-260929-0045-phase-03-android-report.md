## Phase Implementation Report

### Executed Phase
- Phase: phase-03-android-category-rows
- Plan: plans/260928-2334-tutorial-and-documentary-categories
- Worktree: `.claude/worktrees/agent-a5c8c3ceaed3446c2`
- Branch: `feat/categories-android`
- Status: completed

### Files Modified
Created:
- `crates/mediagram-core/src/catalog_categories.rs` (57 lines) + `catalog_categories_tests.rs` (7 tests) — `categories(conn)`, `category_of(map, kind, show, title)`.
- `android/feature/catalog/src/main/kotlin/Categories.kt` (37 lines) — `OTHER_CATEGORY`, `CategoryRow<T>`, `categoryRowsOf`, `categoryOf(Entry)`.
- `android/feature/catalog/src/test/kotlin/CategoryRowsFixtureTest.kt` — runs `web/test/fixtures/categories/rows.json` via a `locateFixture`-style walk-up.
- `android/ui-mobile/src/main/kotlin/ui/catalog/DocumentaryUnitRow.kt` (61 lines) — mixed collection/single row for a Documentaries category strip.

Modified: `crates/mediagram-core/src/{lib.rs, dto/summary.rs, api/store/editorial.rs, api/store/editorial_tests.rs}` (189/186 lines, both ≤200); `android/core/rust/.../mediagram_core.kt` (regenerated); `android/core/model/src/main/kotlin/MediaSet.kt` (+`category`); `android/core/data/src/{main,test}/kotlin/{CatalogRepository,CatalogRepositoryTest}.kt`; `android/feature/catalog/src/{main,test}/kotlin/{Departments,DepartmentsTest}.kt`; `android/ui-mobile/src/main/kotlin/ui/catalog/{ShowsDepartmentScreen,DocumentariesDepartmentScreen}.kt` + their Test files; `android/ui-tv/src/main/kotlin/ui/tv/catalog/{TvShowsDepartmentPage,TvDepartmentTargets,TvDepartmentPages}.kt` + `TvDepartmentTargetsTest.kt`; `Cargo.toml`, `Cargo.lock` (5 crates), `web/package.json`, `android/app/build.gradle.kts` (0.80.0→0.81.0); `docs/{project-changelog.md, system-architecture.md, web-player.md}`.

### Tasks Completed
All 10 implementation steps. Core reads `categories` once beside `anime_overrides` in `enrich`, resolves per-row from `set.kind/show/title` via `mlib_spec::category_key::category_key`. Bindings regenerated with `ANDROID_NDK_HOME=… scripts/generate-android-bindings.sh` (rebuilds `.so` for all 4 ABIs then runs `uniffi-bindgen`); only the `.kt` diff is committed. `Categories.kt`'s `categoryRowsOf` is a line-for-line Kotlin port of the web's `categoryRows`, using the existing `NATURAL` comparator (`NaturalOrder.kt`) for `.thenBy`. `ShowsDepartment`/`DocumentariesDepartment` each gained a `categories` field; phone screens reuse the existing `CollectionRow` (Tutorials) or the new `DocumentaryUnitRow` (Documentaries, mixed collection+single). TV Tutorials threads category rows through `DeptEntryRow` between Continue and Popular, with `showsDeptTargetOf` extended for arrival/restore (`"category:<i>"` targets). TV Documentaries gets one added sentence in the existing `DOCUMENTARIES` branch comment; `docs/web-player.md`'s Categories section gained the Android paragraph naming the same deliberate difference.

### Tests Status
- `cargo clippy -p mediagram-core --all-targets -- -D warnings`: pass, no warnings.
- `cargo test --all`: pass, 0 failures across the whole workspace (new: 7 `catalog_categories` tests, 5 new `editorial_tests` cases).
- Kotlin: `:feature:catalog:testDebugUnitTest`, `:ui-mobile:testDebugUnitTest`, `:ui-tv:testDebugUnitTest`, `:core:data:testDebugUnitTest` all green, including `CategoryRowsFixtureTest`, `DepartmentsTest`'s 5 new category cases, `ShowsDepartmentScreenTest`'s 2 new cases, `DocumentariesDepartmentScreenTest`'s 1 new case, `TvDepartmentTargetsTest`'s 2 new cases.
- `scripts/check.sh`: **all checks passed** (clippy, `cargo test --all`, bun lint/test, gradle `testDebugUnitTest`/`lint` across every module).
- `cargo metadata --locked --offline`: succeeds.

### Native core / bindings
- `.so` rebuilt for arm64-v8a/armeabi-v7a/x86_64/x86 inside the worktree; `git status` confirms only the `.kt` bindings changed, `.so` files stay untracked.
- Verified with the NDK's `llvm-nm -D`: `UNIFFI_META_MEDIAGRAM_CORE_RECORD_SETSUMMARY` present in the rebuilt `libmediagram_core.so`; the regenerated `.kt` shows `var category: kotlin.String?` on `SetSummary` with the field's doc comment carried through, plus the matching `FfiConverterOptionalString` read/write/allocationSize wiring.

### Deviations from the phase file
- Line numbers in the phase file were stale (written against 0.77.1); re-read current `main` (0.80.0) first, as instructed. `editorial.rs` landed at 189 lines (phase estimated ~190), `Departments.kt` at 163 — no split needed.
- `Categories.kt`'s `categoryOf(entry: Entry)` covers both `ShowsDepartment` (`List<Entry.Collection>`, via Kotlin function-type contravariance) and `DocumentariesDepartment` (`List<Entry>`, mixed) — the phase specified one function for both, confirmed workable without an overload.
- Device check (phase step 9: tablet/TV install, screenshots) not done — explicit hard limit in this session (no adb, no device installs). Everything up to the fresh `.so` rebuild and symbol verification was done; the phase's own risk table already treats a stale `.so` as the main device risk, which this worktree does not carry.

### Issues Encountered
None against the phase text or the codebase; no file-ownership conflicts (this phase owns Android + the Rust core only, per plan.md's phase order).

### Next Steps
Device verification (tablet + TV, screenshots beside phase 2's web screenshots) is the one remaining phase-9 item, blocked on this session's hard limits, not on any code gap. Categories plan is now fully implemented across index, web and Android; plan.md's frontmatter set to `status: completed`.
