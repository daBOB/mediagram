# Phase 02 — Stage 2: rename a profile

**Design:** sections 4 (name rules), 5 (the action), 6 (surfaces), 8 (testing). **Status:** blocked on the **Gate** in `plan.md`.
**Starts only when** `feat/owner-manages-all` is merged and the user has confirmed the TV box and the tablets run the stage-1 release. Then `git merge main` in `../mediagram-rename`.
**Re-measure first.** The owner plan changes `profile-rules.json`, `profiles-rules.ts`, `rules.rs`, `ProfileRoles.kt`, `profile-api.js`, `profile-manage.js` and the offer tests. Build on its `owner` field and its cross-household check (`ownerReaches` on the web at planning time). Every line count below is main's before that merge.

**The rule, as decided:** "rename" is allowed when one of these holds:
- **the owner** renames anyone, in any household, themselves included;
- **a household's admin** renames the grown-ups of its own household, itself included;
- **a kid** is renamed by `ownerOf(kid)`: its parent, or the household's admin when the parent is gone.

Nobody else may rename: a kid renames nobody, itself included; a grown-up who runs nothing cannot rename themselves; nobody renames a target that is not there.

### Task 6: The `rename` rule in the fixture and its three ports

**Files**
- `web/test/fixtures/watch-state/profile-rules.json`: the cases below, on the fixture's usual profiles plus the owner plan's `owner`/`household` fields.
- `web/src/state/profiles-rules.ts`:
  - `Action` gains `"rename"`;
  - the owner's cross-household reach includes it;
  - in the household, `case "rename": return actor.owner === true || (target.kids ? ownerOf(profiles, target) === actor.id : actor.admin);`.
  
  Extend the header comment by the rule in one sentence.
- `crates/mediagram-core/src/state/profiles/rules.rs`: `Action::Rename` (kebab-case `rename`), the same branch.
- `android/core/model/src/main/kotlin/ProfileRoles.kt` (80): `RENAME("rename")`, the same branch.
- `web/test/profile-api.test.ts` `offered()`: `rename` is offered on a row in `grownUps` or `kids`, and on the actor's own row when it runs its household or is the owner. A case the rule allows on a row Manage does not draw (the owner on another parent's kid at home, if the merged owner plan still leaves it out) is skipped as `ownerAcross` skips, with a one-line reason. List it in the Review.
- `android/feature/catalog/src/test/kotlin/profile/ManageOffersFixtureTest.kt`: skip `rename` cases, with the line "Android's Manage offers no Rename until its own phase."

**Fixture cases** (`expect` in brackets)
- "a kid renames nobody, itself included" [false]
- "the owner renames a grown-up in their household" [true]
- "the owner renames themselves" [true]
- "the owner renames a kid whose parent is another grown-up" [true]
- "the owner renames a grown-up in another household" [true]
- "the owner renames another household's admin" [true]
- "the owner renames a kid in another household" [true]
- "a household's admin renames a grown-up of its household" [true]
- "a household's admin renames itself" [true]
- "a household's admin renames a kid whose parent is gone" [true]
- "a household's admin may not rename across households" [false]
- "a parent renames their kid" [true]
- "a parent may not rename another parent's kid" [false]
- "a grown-up who runs nothing may not rename themselves" [false]
- "a grown-up who runs nothing may not rename another grown-up" [false]
- "nobody renames as somebody not there" [false]
- "nobody renames a profile not there" [false]

**Commands**
- `cd web && bun test test/shared-profile-fixtures.test.ts test/profile-api.test.ts test/state-profiles-rules.test.ts`
- `cargo test -p mediagram-core --test shared_watch_state_fixtures profile_rule`
- `cd android && ./gradlew :core:model:test --tests '*ProfileRoles*' :feature:catalog:testDebugUnitTest --tests '*ManageOffersFixtureTest*'`

**Commit:** `feat: who may rename a profile, in the shared rule and its three ports`

### Task 7: Web — the route and the Manage control

**Files**
- `web/src/state/profiles-write.ts` (43): `writeName(db, id, name)`. In one update it sets:
  - `name`;
  - `former_names`: the stored list plus `normalName` of the old name, less `normalName` of the new one, `sortedNames`, the first 32;
  - `renamed_at = MAX(now, MIN(renamed_at, LAST_STAMP) + 1)`, clamped as the other writes are.
  
  A case-only change keeps the list and still dates the change, so its spelling wins everywhere.
- `web/src/state/profiles-access.ts` (29): `rename(actorId, pin, id, name): Outcome`. In the order every management action answers in:
  1. `cleanName(name) === null` → `invalid`;
  2. `nameTaken(takenNames(this.db, id), name)` → `name-taken` (the target's own current and former names are not counted, so renaming back and case-only changes pass);
  3. `this.check(actorId, pin, "rename", id)`: `not-found`, a kid acting, `no-pin`, `wait`, `wrong-pin`, the rule;
  4. `writeName(this.db!, id, cleanName(name)!)`.
- `web/src/state/profiles-routes.ts` (106): the `ACTION` route gains `name`; add `case "PUT name": return answer(manage.rename(actorId, said.pin, id, said.name));`. Done is 204. It starts a sync round like every profile write (`routes.ts`).
- `web/public/lib/profile-api.js` (115): `export const rename = (actorId, pin, id, name) => change("PUT", at(id, "/name"), { actorId, pin, name });`.
- Create `web/public/lib/profile-rename.js` (≤90): `renamePill(entry, { pill, send, report })`. It takes `pill` from the caller, to avoid an import cycle.
  - It is a `pill(HOUSEHOLD_WORDS.rename, …)` that opens an inline one-field form under its row: Manage's `who-new` form pattern, with `nameField` from `profile-add-form.js` labelled "A new name for Maja" and holding the current name, and a submit button "Rename".
  - It sends `api.rename` with the PIN held for the visit, through the caller's `send`, and reports through `report`; refusals read through `refusalText` (`name-taken` is already "That name is already used on this install.").
  - Words come from `household-words.js` only, or a new key there.
- `web/public/lib/profile-manage.js` (200 before the owner plan; re-measure): `renamePill(entry, …)` on every grown-up and kid row it draws. Beside "Change your PIN", a Rename of the actor's own profile when the actor runs its household or is the owner. The file must stay ≤200: if it would pass, move a self-contained helper (e.g. `removeOne`, `newPin`) into a new module, with no change in behaviour.

**Tests**
- Create `web/test/state-profiles-rename-http.test.ts`:
  - "a rename answers 204 and stores the name, a new stamp and the old name as a former name"
  - "a name in use, or one a profile had before, is name-taken"
  - "renaming back, or changing only the case, is allowed"
  - "an unusable name is invalid"
  - "somebody not there is not-found"
  - "a kid acting is not allowed, and neither is a grown-up who runs nothing renaming themselves"
  - "a household's admin renames its grown-ups and itself, a parent their kid, and nobody another parent's kid"
  - "the owner renames anyone in any household; a household's admin cannot rename across households"
  - "wrong PINs wait, as for every management action"
  - "a grown-up with no PIN yet is no-pin"
  - "a rename keeps at most 32 former names"
  - "a rename starts a sync round"
  - "a rename made here is taken in by another store as the same person" (export, `mergeStates`, `importMerged` on a second store: same viewer, new name, history kept)
- `web/test/profile-manage.test.ts`:
  - "Rename shows on every row Manage draws, and beside Change your PIN for whoever runs the household"
  - "a grown-up who runs nothing has no Rename of their own"
  - "a rename sends the new name with the held PIN, and the panel redraws"
  - "a refused rename says why"

**Preview walk:** `cd web && bun run preview` on a scratch copy, gstack `/browse`. As the owner (PIN 1357 in the copy only), rename a grown-up and a kid, at desktop width and at 390 px. Screenshots go into `plans/261020-1600-profile-rename/reports/`.

**Commands**
- `cd web && bun test test/state-profiles-rename-http.test.ts test/profile-manage.test.ts test/code-standards.test.ts`
- then the full web checks.

**Commit:** `feat(web): rename a profile from Manage profiles`

### Task 8: Core — `rename_profile`, the Android bindings, `FakeCore`

**Files**
- Create `crates/mediagram-core/src/state/profiles/manage/rename.rs` (≤90), beside `hidden_sections.rs`: `ProfileManager::rename(&mut self, actor_id, pin, id, name: &str) -> Answer`. It is the web's `rename`:
  - `clean_name` → `Invalid`;
  - `rules::name_taken(&role_rows::taken_names(self.conn, Some(id))?, name)` → `NameTaken`;
  - `self.check(actor_id, pin, Action::Rename, Some(id), false)`;
  - then the web's `writeName`, with `LAST_STAMP`, `record::sorted_names` (make it `pub(crate)`) and the cap of 32.
- `src/state/profiles/manage.rs` (190): `mod rename;`.
- `src/api/state/profile_roles.rs` (164): `pub async fn rename_profile(self: Arc<Self>, actor_id: String, pin: String, id: String, name: String) -> ProfileOutcome`, shaped like `set_hidden_sections`. If the file would pass 200, split it the way `hidden_sections` was split.
- Bindings: `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh`. Commit the regenerated `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`, and check `git diff --check`.
- `android/core/testing/src/main/kotlin/testing/FakeCore.kt` (714, over 200): only the override the generated interface forces, delegating to the fake's rename. `setHiddenSections` grew it by 3 the same way (0601f685).
- Create `android/core/testing/src/main/kotlin/testing/FakeProfileRename.kt`: `internal fun FakeProfiles.rename(actorId, pin, id, name): ProfileOutcome`, in the core's order:
  - `Invalid` for an unusable name;
  - `NameTaken` against the other profiles' names;
  - `check(actorId, pin, RoleAction.RENAME, id)`;
  - `update(id) { it.copy(name = …) }`.
  
  `FakeProfiles.kt` (321, over 200) does not grow: make the helpers it needs `internal` instead of `private`, a modifier change only. Add the comment `// shortcut: the fake keeps no former names, so it does not refuse one; add them when an Android test needs it`.
- `android/core/model/src/main/kotlin/ProfileRequests.kt` (114): `data class Rename(actorId, pin, id, name)`. `android/core/data/src/main/kotlin/CoreProfileCalls.kt` (68): `is ProfileRequest.Rename -> core.renameProfile(actorId, pin, id, name)`, and any other exhaustive `when` over `ProfileRequest`. This leaves the Android phase UI only. `CoreContract.kt` (696) gains no case.

**Tests**
- `src/state/profiles/manage/rename_tests.rs`:
  - `a_rename_stores_the_name_a_new_stamp_and_the_old_name_as_a_former_name`
  - `a_name_in_use_or_given_up_is_name_taken`
  - `renaming_back_or_changing_only_the_case_is_allowed`
  - `an_unusable_name_is_invalid`
  - `refusals_come_in_the_order_every_management_call_answers_in`
  - `the_owner_renames_across_households_and_a_households_admin_cannot`
  - `a_rename_keeps_at_most_32_former_names`
  - `a_rename_made_here_is_taken_in_by_another_core_as_the_same_person`
- `crates/mediagram-core/tests/api_surface.rs`: `rename_profile_answers_through_the_api`.
- `android/core/testing/src/test/kotlin/testing/FakeProfileRenameTest.kt`:
  - `renamesWhereTheRuleAllows`
  - `refusesANameInUse`
  - `refusesAKidActing`

**Commands**
- `cargo test -p mediagram-core rename`
- `cargo test -p mediagram-core --test api_surface`
- `cargo test -p mediagram --test code_standards`
- clippy
- `cd android && ./gradlew :core:testing:test :core:data:testDebugUnitTest :core:rust:compileDebugAndroidTestKotlin`

**Commit:** `feat(core): rename a profile, with its Android bindings and fake`

### Task 9: Docs, version, merge

- `docs/web-player.md`: Manage profiles has Rename (who sees it; former names stay reserved).
- `docs/system-architecture.md`: the web renames; the core offers `rename_profile`; Android's Rename UI follows in its own phase, a recorded lag.
- `docs/project-changelog.md`: a new minor entry.
- Bump the minor (Global Constraints), run `scripts/check.sh`, and merge with the user's word.

The live player then offers Rename. The real rename (Celine) is the user's: they type the owner's PIN. Afterwards, navigate on the TV and a tablet to see one profile under the new name after a round. Change nothing.

**Commits:**
- `docs: renaming a profile`
- `chore: 0.1xx.0, rename a profile`
