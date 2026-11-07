# Parked minor items — profiles, households, owner powers (2026-10-20)

Collected from today's reviews (ledgers: ../mediagram-households, ../mediagram-owner, ../mediagram-access).
Each was rated Minor and parked; this batch fixes the ones with a user-visible effect.

## Web player
1. Stale actor keeps the PIN in Manage: an actor moved out of this device's household on another device stays "As <name>".
   Fix (same line count): `web/public/lib/profile-manage.js:113` — `const view = session === null || !state.profiles().some((one) => one.id === session.actorId) ? null : api.manageable(state.everyone(), session.actorId);`
2. Dead fallback in `web/public/lib/profile-manage-households.js:198-199,210` (an unlisted household id as heading) — drop the third spread and the `?? id`.
3. "Join a household" box with no buttons when every other household is unopenable — say a line instead (a word in household-words.js, mirrored into household-rules.json words and Android's HouseholdWords).

## Android
4. Nobody-chosen join that took + failed households read closes the prompt silently (`JoinHouseholdViewModel.kt` ~147 with ~102): call leftBehind only if a profile was chosen before the join; else re-read.
5. TV `TvJoinHousehold.kt:85,103`: `holding` not cleared when focus leaves to the chrome bar — a removed row can pull the remote out of the bar. Clear it on focus leaving (as TvProfilesPage's `held()` does).
6. TV gate `joiningElsewhere` (`TvProfileGate.kt:105,116`) can go stale and reopen the join screen without a press — reset it when `joinsElsewhere` turns false.
7. Stale comment `ui-mobile/.../ProfileGate.kt:70` ("a chosen profile no longer here reads as loading") — reword.
8. `TvManageHouseholdsStateTest.kt:118` claims focus after Reset PIN that the app doesn't keep — rename the test or drop that step.

## Core
9. `households/manage/writes.rs:34-35` no-row error names nothing — name the household.
10. A test that a profile made in Home keeps `household_updated_at == 0` and one made elsewhere is dated.
