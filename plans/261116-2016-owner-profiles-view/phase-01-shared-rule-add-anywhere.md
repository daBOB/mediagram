# Phase 01 — The shared rule: the owner adds anywhere

Status: todo · Priority: first (every later phase reads this rule)

## Context

- Spec: `docs/superpowers/specs/2026-11-16-owner-profiles-view-design.md` § Rule and API change
- The rule lives in three ports held to one fixture:
  - web `web/src/state/profiles-rules.ts` → `allowed()` (lines ~63-86)
  - Rust `crates/mediagram-core/src/state/profiles/rules.rs` → `allowed()` (`(Action::CreateGrownUp, _) => actor.admin` ~L97, `CreateKid` ~L98, ~L122)
  - Kotlin `android/core/model/src/main/kotlin/ProfileRoles.kt`
- Fixture `web/test/fixtures/watch-state/profile-rules.json` (94 cases), run by
  `web/test/shared-profile-fixtures.test.ts`, `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`,
  `android/core/model/src/test/kotlin/ProfileRolesFixtureTest.kt`.

## The rule (identical in all three ports)

`targetId` of the two create actions gains a meaning. Today both are always `""`.

- `create-grown-up`: `targetId` is a **household id** (not a profile id), or `""` for the
  maker's own. Allowed iff `actor.admin && (targetId == "" || targetId == householdOf(actor) || actor.owner == true)`.
- `create-kid`: `targetId` is the **parent's profile id**, or `""` for the maker.
  Allowed iff `targetId == "" || targetId == actor.id || (actor.owner == true && target exists && !target.kids)`.
- Write one comment on each port saying the create-grown-up target is a household, not a profile.
- Whether the household exists is the manager's business (Phase 02/03), not the rule's.

### Task 1.1: Fixture cases, then the three ports

**Files:** Modify `web/test/fixtures/watch-state/profile-rules.json`, the three rule files above.

- [ ] **Step 1: Add these cases** to the fixture, using the owner profile set the existing
  owner cases use (`andre` owner+admin home, `test` home, `mia` kid of test, `oma` admin of `b`,
  `tvtest` in `b`, `tvkids` kid of tvtest, `lostb`):

| name | actor | action | target | expect |
|---|---|---|---|---|
| the owner may create a grown-up in another household | andre | create-grown-up | b | true |
| the owner may name their own household for a new grown-up | andre | create-grown-up | home | true |
| a household's admin may name their own household for a new grown-up | oma | create-grown-up | b | true |
| a household's admin may not create a grown-up in another household | oma | create-grown-up | home | false |
| a grown-up who is not an admin may not create a grown-up in their own household by name | test | create-grown-up | home | false |
| the owner may create a kid for a grown-up in another household | andre | create-kid | tvtest | true |
| the owner may create a kid for another grown-up in their own household | andre | create-kid | test | true |
| a grown-up may create a kid by naming themselves | test | create-kid | test | true |
| a household's admin may not create a kid for another grown-up | oma | create-kid | tvtest | false |
| nobody may create a kid for a kid | andre | create-kid | mia | false |

- [ ] **Step 2: Run the three fixture runners, expect the new cases to fail**
  - `cd web && bun test test/shared-profile-fixtures.test.ts`
  - `cargo test -p mediagram-core --test shared_watch_state_fixtures`
  - `cd android && ./gradlew :core:model:testDebugUnitTest --tests '*ProfileRolesFixtureTest*'` (use the module's unit-test task name if it differs)
- [ ] **Step 3: Implement the rule above in each port.**
- [ ] **Step 4: Run the three runners again, plus** `web/test/state-profiles-rules.test.ts`,
  `android/core/model/src/test/kotlin/ProfileRolesTest.kt`, and
  `android/feature/catalog/src/test/kotlin/profile/ManageOffersFixtureTest.kt`. Expect all PASS.
- [ ] **Step 5: Commit** — `feat(profiles): the owner may add a grown-up or a kid in any household`

## Success criteria

All 104 fixture cases pass in web, Rust and Kotlin; no existing case changed.

## Risks

- `ManageOffersFixtureTest` runs the same fixture for Android's offers; if a case there pins
  "add only in own household", update that expectation in this task so every commit is green.
