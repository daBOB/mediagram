# Planner report — Android phases 06 and 07 (profile roles, PINs, per-kid limits)

Plan: `plans/260928-0047-profile-roles-pins-kids-age-limits/`
Written: `phase-06-android-data-viewmodels.md`, `phase-07-android-phone-tv-screens.md`
Against: shared-contract.md as amended 2026-09-28 (create-first, new refusal order, wait only on comparison, §12), web phase-03 for words/behaviour, core phase-05 for the bindings hand-off.

## Summary

- **06 (7 tasks):** Task 1 is one atomic commit that lands right on top of phase 05's bindings commit (phase 05 says so in its own message): model roles (`Profile` + `RoleAction`/`ownerOf`/`allowed`, pinned to `profile-rules.json`), `ProfileRequest`/`ProfileOutcome`, `FakeProfiles` (the order, the wait, create-first) behind `FakeCore`, `CoreContract` rewritten (a fresh real core can create again through `createFirstAdmin`, so the contract now covers create, choose, remove and wrong PINs as well as refusals), `WatchStateRepository.manage(request)` + `setKids(setId, age)`, every fake adjusted, the pickers lose Add/Remove. Then: per-kid catalog filter + `KidsEmpty(limit)`; `PinAsk` + the web's words; picker `pick`/`claim`/`createFirst`; `ManageProfilesViewModel`; player `setKidsMark`; `RealCoreContractTest` on caad49da.
- **07 (8 tasks):** phone PIN dialog → phone Manage → phone picker+gate; TV PIN pad (D-pad only, 56dp keys to fit 960×540) → TV Manage → TV picker+gate+whole-app walks (`TvAppFixture(viewerCore = FakeCore)`); Kids choice on both surfaces and `toggleKids` retired; checks + a read-only tablet walk.

## File decisions

- `WatchStateRepository.kt` is already 401 lines — kept its growth to one method: `manage(request: ProfileRequest): ProfileOutcome` (default `NotAllowed`, like today's `deleteProfile = false`), with the request→core mapping in a new `core/data/CoreProfileCalls.kt`. One method instead of eight means the 5 repository fakes only lose `createProfile`/`deleteProfile`.
- `ProfileRequest` variants are plain classes, not data classes — they carry PINs; no generated `toString`.
- `allowed()` lives once in `core/model` and serves both `FakeCore` (enforce) and Manage (offer). A Kotlin fixture test holds it to the web.
- `ProfileViewModel` stays the picker (240 → ~200 lines; `ProfileUiState` moved to its own file); Manage is a separate `ManageProfilesViewModel` with the same constructor shape, so fixtures only gain one map entry.
- `PinAsk` (shared by both VMs) holds the prompt, the twice-typed new PIN and a stale-answer guard. Every new PIN is typed twice: nobody can reset the admin's own.
- All words (refusals, titles, note, labels) in one `feature/catalog/profile/ProfileWords.kt`, copied verbatim from phase-03's `REFUSALS`, `askGrownUp`, `profile-manage.js` — pinned by `ProfileWordsTest`.
- New shared `ui-common/.../ui/profile/ManageActions.kt` (precedent `MenuActions.kt`).
- TV flows compose in place (PIN pad, Manage, name questions) as the old add flow did; phone uses dialogs.
- Sequential cross-phase edits, listed in each phase: 06 Task 1 makes compile-keeping edits in ui-mobile/ui-tv (pickers lose Add/Remove, `KidsEmpty` call sites, TV tests); 07 Task 7 retires `toggleKids` in feature/player. No two parallel phases touch a file.

## Contract questions (not invented — defaults chosen are marked)

1. **`unlock` / any request on a grown-up with no PIN while the wait runs:** FakeCore answers `NoPin` without waiting (nothing is compared, per §3 step 4). Confirm the core does the same.
2. **`claim-admin` on a missing id with a malformed PIN:** FakeCore answers `NotFound` (whether the PIN is "new" and so format-checked depends on the target). Edge only; not in `CoreContract`.
3. **`CoreContract.aKidsMarkCarriesTheAgeItWasMadeFrom`** marks a set id the fresh core's catalog does not hold. If the real `set_kids` refuses unknown ids, drop that case from the contract (keep it in `FakeProfilesTest`).
4. **Generated name `createFirstAdmin`** — phase 05's Task 7 Step 3 grep list does not include it yet; 06 Task 1 Step 1 checks for it.

## Parity decisions for the user (deliberate differences, or gaps)

- **Android Settings has no "Who is watching" panel** (web `settings-page.js:98-114` has one; phase 03 makes it `Name · Kids · FSK N`). Pre-existing gap; the kid's limit shows on Android's tiles, Manage rows and empty shelf. Not built — add later?
- **Picker note** says "…someone who knows their way around Android can get past it." where the web says "a browser". One-word, on purpose.
- **"Look again" beside "Create the first profile"** on Android: a fresh device's first sync can outlast the picker's 5-second wait, and a first profile made meanwhile loses admin to the household's older claim. Web plan line 76 does not show it — should the web have it too?
- **A new PIN is typed in two steps** on both Android surfaces ("The same PIN again"), where the web shows two fields at once — the TV pad has one entry at a time and the phone shares its logic.
- **Kids choice on TV is a dialog** (web: `<select>`, phone: menu).

## Risks the lead should see

- **Phase 08 Task 2 Step 1 ("first admin" on web *then the tablet*)**: claiming admin on the tablet makes whoever is claimed the household's admin for good (earliest claim wins, no hand-on) and it syncs everywhere. 07 Task 8 forbids any device claim; phase 08 should say "web only, andre".
- Any PIN/kid/limit made on a device spreads household-wide and removals do not propagate (spec §8). 07 Task 8 is read-only by default; household-changing steps need the user's go-ahead. Saved as planner memory `profile-data-spreads-household-wide`.
- Between 06 Task 1 and 07 Task 3/6 the Android picker has no Add/Remove (stated in the changelog; don't install on devices in between).

## Unresolved

- Whether to build the Android settings "Who is watching" panel now (parity gap) — user decision.

**Status:** DONE_WITH_CONCERNS
**Summary:** Phases 06 and 07 written against the amended contract, with real Kotlin/Compose, TDD steps, file:line insights and the web's words; phase 06 starts with the atomic commit phase 05's bindings require. Concerns: four small contract edge questions, one pre-existing parity gap (Android has no settings profile panel), and phase 08's tablet admin-claim step, which must be web-only.
