# Phase 03 — Access per profile: hidden sections

**Status:** pending. **Confirmed by the user 2026-10-20:** "yes, section access per profile". The user skipped the design questions, so every decision below is the controller's, marked as such, and provisional.

**Precedent to copy:** the kids daily limit (0.125.0, `plans/261017-0219-kids-daily-watch-limit/`): a nullable value plus `_updated_at`, newest write wins, carried in the profile roles record, ruled by `profile-rules.json`, run by web, Rust core and Kotlin alike. Reports beside that plan list every file it touched.

## Decisions (controller's)

1. **The sections are the five departments of sets:** `movies`, `series`, `anime`, `documentaries`, `tutorials` — the keys of `web/public/lib/catalog/sections.js` `SECTIONS`, and Android's `Department` (`feature/catalog/.../Department.kt`). Each set sits in exactly one (`web/public/lib/departments.js:341-346` `groupDepartments`, then `library.js:278-288`; Android `Shelves.kt:37-63` `shelvesOf`): anime first, then `docu` → documentaries, `ep` → series, `tut`/document → tutorials, everything else → movies.
2. **Collections is not a section.** It is a page over the others (franchises from Movies and Anime films) plus each viewer's own lists, and Android has no Collections department. Hiding Movies or Anime shrinks the franchises by itself.
3. **Stored as the hidden ones:** a sorted, de-duplicated list of section keys; empty means everything is visible, so every existing profile is unchanged. Unknown keys from a newer build are kept as they are (each `^[a-z]{1,24}$`, at most 16), so they survive a round trip through an older device.
4. **Whole-list last write wins**, stamped like the daily limit (`MAX(now, MIN(old, LAST_STAMP) + 1)`), ties by device id. Two people editing the same profile at once: the later list wins whole.
5. **Who sets it — a new rule action `set-access`:** a kid → its owner (`ownerOf`); a grown-up who is not the admin → the admin; the admin → nobody (the admin never hides sections from itself, as it is never removed). A kid actor sets nothing.
6. **What hiding does, per profile:** the department's sets are filtered out at the one place each surface filters a catalogue — web `library-session.js` `filtered` (beside the kids filter, AND), Android `KidsProjection.kt` `projected` / `CatalogViewModel` (both phone and TV use it). Everything downstream follows: nav pill and rail, Home shelves, search, Continue, My List, Latest, Genres, franchises, title pages by URL ("not in the library", as for a kid). The nav pill of a hidden section is hidden.
7. **Not a lock.** Like the kids filter, nothing is filtered server-side; it is a household rule. Stated in the docs and the changelog.
8. **Words:** the control is titled **Sections**, one switch per section by its `SECTIONS` label, on = visible. The Everyone line adds ` · hides <Label>, <Label>` (labels in nav order) when any are hidden. Android reuses the same words through `core/model`.
9. **Staleness:** a change made on this device applies at once (the profile list is re-read after every change and the catalogue re-filtered). A change from another device reaches an open web tab on its next profile reload, the same as a kid's age limit today.

### Task 3.1: The rule and the shared fixtures

**Files:**
- `web/test/fixtures/watch-state/profile-rules.json`: `set-access` cases first — owner sets own kid; other grown-up refused for that kid; orphan kid → admin; admin sets a grown-up; non-admin grown-up refused for a grown-up; admin refused for itself; kid actor refused.
- `profile-roles-record-parse.json`: `hiddenSections: { sections, updatedAt }` — valid, unsorted/duplicate (canonicalised), unknown-but-well-formed key kept, malformed entry drops the key alone, too many entries drops the key.
- `profile-roles-merge.json`: newest wins whole; tie by device; empty list stamped wins over an older non-empty one; a grown-up keeps the key (no kid gate).
- Web: `web/src/state/profiles-rules.ts` (`Action` + `allowed`); `web/test/shared-watch-state-fixtures.test.ts` `canonicalRoles` learns the key.
- Rust: `crates/mediagram-core/src/state/profiles/rules.rs` (`Action::SetAccess`, kebab-case).
- Kotlin: `android/core/model/.../ProfileRoles.kt` `RoleAction.SET_ACCESS("set-access")`.
**Tests:** the three runners of `profile-rules.json` (web `shared-profile-fixtures.test.ts`, Rust `tests/shared_watch_state_fixtures.rs`, Kotlin `ProfileRolesFixtureTest.kt`) pass on the new cases; record/merge fixture runners stay red until 3.2/3.3 (mark the new cases so 3.1's commit stays green: add them in 3.2 for the web and 3.3 for the core if a runner cannot skip).
**Commit** `feat: a rule for who may hide sections from a profile`.

### Task 3.2: Web server — store, sync, set

**Files:** `web/src/state/roles-schema.ts` (a new group: `hidden_sections TEXT NOT NULL DEFAULT '[]'`, `hidden_sections_updated_at INTEGER NOT NULL DEFAULT 0`; append to `ROLES_GROUPS`); `profiles.ts` (row, `Profile.hiddenSections: string[]` for every profile); `profiles-write.ts` (`writeHiddenSections`, stamped); a new `profiles-access.ts` for the manage method if `profiles-manage.ts` (ceiling 212) would grow; `profiles-routes.ts` (`PUT /api/profiles/{id}/hidden-sections` `{actorId, pin, sections}` → validate known-or-well-formed keys, canonicalise); `roles-record.ts`, `roles-merge.ts` (no `kid &&` gate), `roles-exchange.ts` (export once stamped, for any profile; import newer).
**Tests:** `state-migration` (new version), `state-http` (route: owner ok, refused cases, bad body 400), `state-roles-*`, the parse/merge fixtures from 3.1, `code-standards` ceilings with dated sentences if raised.
**Commit** `feat(web): profiles carry hidden sections and sync them`.

### Task 3.3: Rust core — store, sync, set, bindings

**Files:** `src/state/schema.rs` (next group, v11; `schema.rs` is at 199 lines — move the new group to its own module as the web did with `roles-schema.ts`), `upgrade_tests.rs`; `record/roles_record.rs` + `record.rs`; `merge/roles.rs` (no kid filter), `merge/tie_break.rs` (`Stamped` impl); `exchange/roles.rs`; `profiles.rs` (uniffi `Profile.hidden_sections: Vec<String>` with `#[uniffi(default = [])]` or the uniffi equivalent, declared last), `profiles/role_rows.rs` (near its ceiling — split if needed); `profiles/manage/hidden_sections.rs`; `api/state/profile_roles.rs` `set_hidden_sections(actor_id, pin, id, sections: Vec<String>) -> ProfileOutcome`; `tests/api_surface.rs`; the shared fixture runners.
**Then:** `scripts/generate-android-bindings.sh` (rebuilds the native core for all four ABIs, needs `ANDROID_NDK_HOME`, and regenerates `android/core/rust/.../mediagram_core.kt`, which is committed). Kotlin callers of `uniffi Profile` use named arguments.
**Tests:** `cargo test --all`, `cargo clippy --all-targets --all-features -- -D warnings`.
**Commit** `feat(core): profiles carry hidden sections and sync them`.

### Task 3.4: Web player — hide the sections, and the control

**Files:** a new `web/public/lib/section-access.js` (`departmentOf(set)` from the same rules as `groupDepartments`, `withoutHidden(sets, hidden)`), applied in `library-session.js` `filtered` (at its 200-line cap: the call only); nav: hide `[data-section=<key>]` links for hidden keys (index.html nav; Anime's existing zero-count rule stays); a route to a hidden department draws the department's existing empty words; `profile-api.js` `setHiddenSections`; `profile-manage.js` (`mountManage`'s role view: a **Sections** control per manageable row — kids for their owner, non-admin grown-ups for the admin; six-or-fewer switches by `SECTIONS` label); `catalog/settings-profiles.js` Everyone line (` · hides …`). After a change on this device, the catalogue is re-filtered at once.
**Tests:** `section-access.test.ts` (departmentOf for each kind incl. anime film and document; withoutHidden AND kids filter); the nav hides; a hidden department route; the control sends `PUT …/hidden-sections` with the PIN and redraws; Everyone line; `code-standards` ceilings.
**Preview walk:** hide Anime for a test kid, switch to it, screenshot the nav and Home (desktop + 390 px) into `reports/`.
**Commit** `feat(web): hide sections from a profile`.

### Task 3.5: Android data

**Files:** `core/model` (`WatchSnapshot.kt` `Profile.hiddenSections: List<String> = emptyList()`, `ProfileRequests.kt` `SetHiddenSections`, section keys/labels in one place beside `Department`'s words), `core/data/CoreProfileCalls.kt` (`sendTo`, `toModel` with named args), `core/testing` (`FakeProfiles`, `FakeCore`, `CoreContract` case, `WatchStateFixture`), `feature/catalog/.../ManageProfilesViewModel.kt` `setHiddenSections`, `ManageUiState.kt` (which rows carry the control), `ui-common/.../ManageActions.kt`.
**Tests:** `FakeProfilesTest`, `ManageProfilesViewModelTest`, `ManageOffersFixtureTest`, `ProfileRolesFixtureTest`, `CoreContract` against FakeCore.
**Commit** `feat(android): profiles carry hidden sections`.

### Task 3.6: Android catalogue and controls (phone, tablet, TV)

**Files:** `KidsProjection.kt` / `CatalogViewModel.kt` (filter by department for the chosen profile, AND the kids filter; department tabs drop hidden ones; phone and TV share it), `TitlePageCandidates.kt` if it reads shelves directly, `PlayerViewModelTogether.kt` `onThisProfile` (a title in a hidden section is not on this profile); phone `ManageProfilesScreen.kt` + `ProfileForms.kt` (Sections chips per row), TV `TvManageProfiles.kt` + `TvManageDialogs.kt` (a Sections step like Daily limit); the Profiles section's Everyone line (phase 02).
**Tests:** `CatalogViewModelTest` (hidden department gone, tabs, AND kids), `ManageProfilesScreenTest`, `ManageProfilesFlowTest`, `TvManageProfilesStateTest`, `TvProfilesFlowTest`.
**Commit** `feat(android): hide sections from a profile on phone, tablet and TV`.

### Task 3.7: Device check (the user's hands for nothing but switching the TV on)

- Rebuild the native core and install on the tablet (`caad49da` or `d54bc94f`, whichever is attached) and the box, test profiles only.
- On the web Settings → Profiles as the admin: hide Anime for "TV test". On the box, switch to "TV test": no Anime in the rail or Home; the web Everyone line reads "… · hides Anime". Show it again; it comes back after the next profile reload.
