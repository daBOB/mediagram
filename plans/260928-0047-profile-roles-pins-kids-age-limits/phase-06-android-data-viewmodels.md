# Phase 06 — Android: model, repository, view models, marks

## Context links

- Contract (authoritative): [shared-contract.md](shared-contract.md) §2 rule, §3 outcomes + order (amended 2026-09-28), §4 wait, §9 uniffi API (incl. `create_first_admin`), §11 filter, §12 picker start states
- Spec: `docs/superpowers/specs/2026-09-28-profile-roles-design.md` §2 (table, Managing, first admin, pre-upgrade grown-ups, wrong PINs), §4, §6
- Web reference for words and behaviour: [phase-03](phase-03-web-browser-picker-manage-filter.md) (`pin-prompt.js` `REFUSALS`/`refusalText`, `askGrownUp` titles, `profile-manage.js` sections, `profile-picker.js` note, §12 states at its line 76)
- Depends on: phase 05 Task 7 — regenerated `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt` + four rebuilt `.so`, committed as "not pushed alone — lands with phase 06"; phase 02's `web/test/fixtures/watch-state/profile-rules.json`
- Precedent for a core-then-Android pair: `2277e9eb`
- Bumping: [phase-08 § Bumping](phase-08-verify-docs-version.md) (B1–B4, changelog entry, subject ends `; release <next>`)

## Overview

Priority P1 (07 draws everything from here). Status: pending.
Android learns the roles: the model carries admin / parent / limit / has-PIN; one repository call
sends every profile request to the core and re-reads on success; `FakeCore` enforces the
contract's rule, order and wait so view-model tests meet the answers the tablet gives; the picker
asks a grown-up's PIN, bootstraps the first admin and asks who runs the household; a Manage view
model runs the panel; the catalog filters by the kid's own limit; the player's Kids mark carries
an age.

## Key insights (verified against the code, 2026-09-28)

- **Phase 05's bindings commit leaves Android red until Task 1 here** — it says so in its own message (phase-05 Task 7 Step 5). So Task 1 is the atomic "adopt the API" commit, first; nothing else in this phase can be verified before it. What breaks is known: `core/testing/.../FakeCore.kt:404,432,450` (+ eight members it does not implement), `CoreContract.kt:66-98`, `core/data/.../WatchStateRepository.kt:255,271,306`, `core/data/src/test/kotlin/WatchStateRepositoryTest.kt:37,68`, `WatchStateOwnershipTest.kt:50`. `Profile(id, name)` and positional `StateSnapshot(…)` keep compiling (new fields defaulted, `kids_from_six` last).
- **The limit is global today.** `KIDS_AGE_LIMIT = 12` (`core/model/src/main/kotlin/AgeRating.kt:13`) feeds `kidsVerdictOf` (`:28-31`) and `forKidsProfile(sets, marked: Set<String>)` (`:44-54`); the only filter caller is `CatalogViewModel.project` (`feature/catalog/src/main/kotlin/CatalogViewModel.kt:153-163`) via `currentKids()` (`:64-68`). Other `kidsVerdictOf` callers: `feature/player/.../PlayerMarksController.kt:59,91`.
- **The empty shelf hard-codes 12 three times**: `CatalogUiState.KidsEmpty` is a `data object` (`CatalogUiState.kt:34-35`), drawn at `ui-mobile/.../catalog/CatalogScreen.kt:90` and `ui-tv/.../catalog/TvCatalogBody.kt:49`, asserted at `ui-tv/src/test/.../catalog/TvCatalogScreenStateTest.kt:243`.
- **`model.Profile` has three fields** (`core/model/src/main/kotlin/WatchSnapshot.kt:39-44`); mapped by a private `toProfile` (`WatchStateRepository.kt:385`) and `StateSnapshot.toModel` (`:393-401`).
- **Repository API to replace**: `createProfile(name, kids)` (`WatchStateRepository.kt:52-56`, impl `:265-277`), `deleteProfile(id)` with a `false` default (`:122-130`, impl `:252-263`), `setKids(setId, marked)` (`:80-84`, impl `:303-306`).
- **Every fake of the interface overrides `createProfile` and `setKids(…, marked)`**: `core/data/src/test/kotlin/WatchSyncTest.kt:63-66`, `feature/catalog/src/test/kotlin/CatalogViewModelTest.kt:80-83`, `feature/catalog/src/test/kotlin/profile/ProfileViewModelTest.kt:64-82,102-105`, `feature/player/src/test/kotlin/FakeWatchStateRepository.kt:40-43,95-105`, `ui-tv/src/test/kotlin/ui/tv/FakeWatchState.kt:47-54,77-82,89-92`; `setKids` also in `feature/player/src/test/kotlin/PlayerActionFailureTest.kt:121-127`, `PlayerActionNoticeTest.kt:153`.
- **`FakeCore` is the one core fake** (`core/testing/src/main/kotlin/testing/FakeCore.kt`, 496 lines): seedable `profiles` (`:139`), `createProfile`/`cleanProfileName`/`chooseProfile`/`deleteProfile` (`:398-437`), no-op `setKids` (`:450`), empty `snapshot` (`:439-440`). Its honesty is held by `CoreContract` against the fake (`FakeCoreContractTest.kt:10-12`) and the real core on the tablet (`core/rust/src/androidTest/kotlin/rust/RealCoreContractTest.kt:17-38`, fresh dir per test). `create_first_admin` means a fresh real core can make profiles again, so the contract can cover create, choose, remove and the PIN — not only refusals.
- **VM tests already run over `FakeCore` + the real repository**: `ProfileOwnershipTest.kt:21-46,71`. Role tests follow that shape, so they exercise `FakeCore`'s rules rather than a hand fake.
- **`ProfileViewModel` (240 lines)** owns choose/add/remove (`profile/ProfileViewModel.kt:139-216`), `ProfileUiState` in the same file (`:21-44`); "Stay as I am" is dropped by `remove` itself (`:212-214`). Manage removes from elsewhere, so it must follow `chosenProfileId` instead.
- **The pickers wire add/remove**: `ui-mobile/.../profile/ProfileGate.kt:30,33`, `ProfilePickerScreen.kt:54,57,103,110-133`; `ui-tv/.../profile/TvProfileGate.kt:43,46`, `TvProfilePicker.kt:66,69,95-104,172-179,190-196,206-208`. They go in Task 1 (the core call is gone); the new picker is phase 07. The TV picker focuses the Add tile when nobody exists (`TvProfilePicker.kt:109,175`) — without it something else must take focus, or `requestFocus()` throws on an unattached requester.
- **Player Kids mark is a toggle**: `PlayerMarksController.toggleKids` (`:86-97`), `PlayerMarksState.kids: Boolean` (`PlayerMarksState.kt:18`), `kidsLabel` (`KidsLabel.kt:6-10`), delegate (`PlayerViewModelDelegates.kt:48`), UI (`ui-mobile/.../player/PlayerMarks.kt:43-50`, `ui-tv/.../player/TvMarksRail.kt:150-157`); hidden on a kids profile (`PlayerMarksController.kt:40-43,61`). The web shows an unrated title's control as its select's current option ("Not for kids" / "From 6" / "From 12") and a rated one as the verdict at 12 (phase-03 1.15); `ui-tv/src/test/.../player/TvPlayerMarksTest.kt:50,130` assert today's "Kids".
- **feature/setup has no role logic**: `SettingsViewModel.kt:204-220` only reloads; `ProfileResetTest.kt:19-31` builds `Profile("alice","Alice")` + positional `StateSnapshot` — still valid. Verify, no change.
- **Shared-fixture precedent**: `core/data/src/test/kotlin/ResumePointFixtureTest.kt`; `core/model/build.gradle.kts:13-16` declares a web fixture as a test input. `core:model` is JVM — task `:core:model:test` (verified); Android modules `:<module>:testDebugUnitTest` (verified on `:feature:catalog`).
- **Hilt/fixtures**: repository singleton (`core/data/src/main/kotlin/di/DataModule.kt:137-141`); VMs built directly in `ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt:155-156,236-260` and `ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt:118`. `ProfileViewModel(repository, sync)` keeps its shape; `ManageProfilesViewModel(repository, sync)` matches it.

## Requirements

Functional
- `model.Profile` gains `kidsAge`, `parentId`, `admin`, `hasPin` (defaulted) and `kidsLimit` (`kidsAge ?: 12`); `WatchSnapshot` gains `kidsFromSix` and `kidsMarks`.
- `KIDS_AGE_LIMIT` deleted; `KIDS_LIMITS = [6, 12]`; `kidsVerdictOf(fsk, limit)`, `MediaSet.kidsVerdict(limit)`, `forKidsProfile(sets, marks, limit)` (contract §11).
- `RoleAction`, `ownerOf`, `allowed` in `core/model`, pinned to `profile-rules.json` — enforced by `FakeCore`, offered from by Manage.
- `ProfileRequest` (8 incl. `CreateFirstAdmin`) and `ProfileOutcome` (7) in `core/model`.
- `WatchStateRepository.manage(request)` replaces create/delete; re-reads on `Done` except `Unlock`; reset-guarded. `setKids(setId, age: Int?)`.
- `FakeCore` implements the new calls in the amended order (§3), the wait only where a PIN is compared (§4), the rule (§2), create-first; `CoreContract` holds fake and real core to it.
- Catalog: a kid sees up to its own limit; `KidsEmpty(limit)` with the contract's sentence.
- Picker VM (§12): no grown-up → `createFirst(name)` (PIN twice); grown-ups, no admin → `claim(id)`; `pick(id)` (kid at once; grown-up PIN or first PIN twice); `enterPin`/`cancelPin`/`pin`; "Stay as I am" follows the chosen id.
- `ManageProfilesViewModel`: actor → PIN → panel per role; `addKid`, `setKidsAge`, `remove`, `addGrownUp`, `changePin`, `close` drops the PIN.
- One sentence per outcome, **the web's words** (phase-03 `REFUSALS`): wrong PIN, wait N s, not allowed, no PIN, not found, not accepted, did not go through.
- Player: `setKidsMark(age: Int?)`; label shows the choice on an unrated title, the verdict at 12 on a rated one; hidden on a kids profile.

Non-functional
- New Kotlin files < 200 lines; no plan refs in code/tests/commits; every commit bumps per phase 08.
- Every module compiles and every unit test passes after each commit of this phase.

## Architecture

```
UI (07) ─pick/claim/createFirst/enterPin─► ProfileViewModel ─┐          ┌─► PinAsk (prompt, twice-typed new PIN, stale guard)
UI (07) ─actAs/addKid/…/enterPin────────► ManageProfilesVM  ┼─manage(ProfileRequest)─► WatchStateRepository ─sendTo─► CoreInterface.createFirstAdmin/unlockProfile/…
                                                             │          ▲ Done (≠ Unlock): reload() → profiles / chosen flows
                                                             └─ sync.soon() on a Done change (reach the other devices now, not in 5 min)
Catalog: profiles+chosen+snapshot ─► KidsView(snapshot.kidsMarks, kid.kidsLimit) ─► forKidsProfile ─► shelves | KidsEmpty(limit)
Player:  snapshot.kidsMarks[setId] ─► PlayerMarksState.kidsMark;  setKidsMark(age) ─► repository.setKids ─► core.setKids(setId, age?.toUByte())
Tests:   FakeCore.profiles + roles.pins ─► FakeProfiles (order §3, wait §4, allowed §2) — the same `allowed` Manage offers from
```

In: core `Profile{kids,kidsAge:UByte?,parentId,admin,hasPin}`, `StateSnapshot.kidsFromSix`, `ProfileOutcome`. Transform: `CoreProfile.toModel()`, outcome mapping (UInt → Int), `kidsMarks`. Out: `ProfileUiState` (+ derived flags), `pin: StateFlow<PinPrompt?>`, `ManageUiState`, `PlayerMarksState.kidsMark`, `CatalogUiState.KidsEmpty(limit)`.

## Interfaces

**Consumes (contract §9, generated Kotlin — confirmed in Task 1 Step 1):**
```kotlin
data class Profile(var id: String, var name: String, var kids: Boolean = false, var kidsAge: UByte? = null,
                   var parentId: String? = null, var admin: Boolean = false, var hasPin: Boolean = false)
sealed class ProfileOutcome { Done; Invalid; NotFound; data class Wait(val seconds: UInt); NoPin; WrongPin; NotAllowed }
suspend fun createFirstAdmin(name: String, newPin: String): ProfileOutcome
suspend fun createGrownUp(actorId: String, pin: String, name: String, newPin: String): ProfileOutcome
suspend fun createKid(actorId: String, pin: String, name: String, kidsAge: UByte): ProfileOutcome
suspend fun deleteProfile(actorId: String, pin: String, id: String): ProfileOutcome
suspend fun unlockProfile(id: String, pin: String): ProfileOutcome
suspend fun claimAdmin(id: String, pin: String): ProfileOutcome
suspend fun setPin(actorId: String, pin: String, id: String, newPin: String): ProfileOutcome
suspend fun setKidsAge(actorId: String, pin: String, id: String, kidsAge: UByte): ProfileOutcome
suspend fun setKids(setId: String, age: UByte?)
data class StateSnapshot(…, var editorsChoice: String?, var kidsFromSix: List<String> = listOf())
```

**Produces (phase 07 relies on exactly these):**
```kotlin
// model
data class Profile(id, name, kids = false, kidsAge: Int? = null, parentId: String? = null, admin = false, hasPin = false) { val kidsLimit: Int }
val KIDS_LIMITS: List<Int>                                       // [6, 12]
enum class RoleAction; fun List<Profile>.allowed(actorId, action, targetId): Boolean; fun List<Profile>.ownerOf(kid): String?
// catalog.profile — ProfileWords.kt
data class PinPrompt(title: String, newPin: Boolean, confirming = false, error: String? = null, busy = false) { val heading: String }
const val PIN_LENGTH = 4
val Profile.kidsTag: String?                                     // "Kids · FSK 6" | null
fun removeQuestion(profile: Profile): String
const val PICKER_NOTE, FIRST_PROFILE, WHO_RUNS_THIS, MANAGE_PROFILES, LOOK_AGAIN, WHO_ARE_YOU, GROWN_UPS, KIDS_SECTION,
          YOUR_PIN, ADD_A_KID, ADD_A_GROWN_UP, CHANGE_YOUR_PIN, RESET_PIN, REMOVE, DONE; fun managingAs(name: String): String
// catalog.profile — picker
ProfileUiState.Picking(profiles, canStay, error) { val needsFirstProfile; val needsAdmin; val grownUps }
ProfileViewModel: pin; pick(id); claim(id); createFirst(name); enterPin(pin); cancelPin(); choose(id); stay(); retry(); reopen()
// catalog.profile — Manage
sealed interface ManageUiState { Closed; ChoosingActor(grownUps); Managing(actor, grownUps, kids, canAddGrownUp, notice) }
ManageProfilesViewModel: state; pin; open(); actAs(id); addKid(name, age); setKidsAge(id, age); remove(id);
                         addGrownUp(name); changePin(id); enterPin(pin); cancelPin(); close()
// catalog
data class CatalogUiState.KidsEmpty(val limit: Int) { val message: String }
// player
PlayerMarksState(watchlisted, kidsMark: Int?, …); data class KidsChoice(age: Int?, label: String); val KIDS_CHOICES
fun PlayerViewModel.setKidsMark(age: Int?)                        // toggleKids() stays until phase 07 moves the UI off it
```

## Related code files

Create
- `android/core/model/src/main/kotlin/ProfileRoles.kt`, `ProfileRequests.kt`; tests `android/core/model/src/test/kotlin/ProfileRolesTest.kt`, `ProfileRolesFixtureTest.kt`
- `android/core/data/src/main/kotlin/CoreProfileCalls.kt`
- `android/core/testing/src/main/kotlin/testing/FakeProfiles.kt`; test `android/core/testing/src/test/kotlin/testing/FakeProfilesTest.kt`
- `android/feature/catalog/src/main/kotlin/profile/{ProfileWords,PinAsk,ProfileUiState,ManageProfilesViewModel}.kt`
- Tests `android/feature/catalog/src/test/kotlin/profile/{PinAskTest,ProfileWordsTest,ProfilePinTest,ManageProfilesViewModelTest}.kt`

Modify
- `android/core/model/src/main/kotlin/{AgeRating,WatchSnapshot}.kt`, `android/core/model/build.gradle.kts`
- `android/core/data/src/main/kotlin/WatchStateRepository.kt`
- `android/core/testing/src/main/kotlin/testing/{FakeCore,CoreContract}.kt`, `android/core/testing/build.gradle.kts`
- `android/feature/catalog/src/main/kotlin/{CatalogViewModel,CatalogUiState}.kt`, `profile/ProfileViewModel.kt`
- `android/feature/player/src/main/kotlin/{PlayerMarksController,PlayerMarksState,KidsLabel,PlayerViewModelDelegates}.kt`
- Tests: `core/data/src/test/kotlin/{WatchStateRepositoryTest,WatchStateOwnershipTest,WatchSyncTest}.kt`; `feature/catalog/src/test/kotlin/{AgeRatingTest,CatalogViewModelTest}.kt`, `profile/ProfileViewModelTest.kt`; `feature/player/src/test/kotlin/{FakeWatchStateRepository,PlayerMarksTest,PlayerKidsLabelTest,PlayerActionFailureTest,PlayerActionNoticeTest}.kt`
- **Compile-keeping UI edits — phase 07 owns these files afterwards (sequential, not parallel):** `ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt:90`, `ui-mobile/src/main/kotlin/ui/profile/{ProfileGate,ProfilePickerScreen}.kt`, `ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogBody.kt:49`, `ui-tv/src/main/kotlin/ui/tv/profile/{TvProfileGate,TvProfilePicker}.kt`; tests `ui-tv/src/test/kotlin/ui/tv/{FakeWatchState,TvHousekeepingTest}.kt`, `ui-tv/src/test/kotlin/ui/tv/catalog/TvCatalogScreenStateTest.kt:243`, `ui-tv/src/test/kotlin/ui/tv/profile/TvProfilePickerStateTest.kt`, `ui-tv/src/test/kotlin/ui/tv/player/TvPlayerMarksTest.kt:50,130`, `ui-tv/src/androidTest/kotlin/ui/tv/profile/TvProfilePickerTest.kt`
- Brought in from phase 05, not edited: `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`, `android/core/rust/src/main/jniLibs/*/libmediagram_core.so` (gitignored)

Delete: none (`RemoveProfileDialog.kt`, `TvAddProfileFlow.kt`, `TvRemoveProfileDialog.kt`, `TvAddTile` go unused after Task 1; phase 07 reworks them).

## Implementation steps

All commands from `/home/andre/Workspace/mediagram/android` unless stated. Rebase on `origin/main` before starting; phase 05's bindings commit must be the tip (unpushed).

### Task 1: Adopt the core's profile API (one atomic commit — the tree does not build between its steps)

- [ ] **Step 1: phase 05's output is here.** Do not regenerate — phase 05 Task 7 ran `CARGO_INCREMENTAL=0 ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh`; rerun that exact step only if the check fails. Check:

```bash
cd /home/andre/Workspace/mediagram
git log -1 --format=%s        # phase 05's "feat(core): profile roles, PINs and kid limits across the boundary; …"
f=android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt
for n in 'fun createFirstAdmin(' 'fun createGrownUp(' 'fun createKid(' 'fun unlockProfile(' 'fun claimAdmin(' 'fun setPin(' \
         'fun setKidsAge(' 'class Wait(' 'kidsFromSix' 'hasPin' 'parentId' 'kidsAge'; do
  grep -q "$n" "$f" && echo "ok  $n" || echo "MISSING $n"; done
grep -c 'fun createProfile(' "$f"                                            # 0
grep -n 'suspend fun deleteProfile(\|suspend fun setKids(' "$f" | head -4   # (actorId, pin, id) / (setId, age: kotlin.UByte?)
ls android/core/rust/src/main/jniLibs/*/libmediagram_core.so | wc -l        # 4
```
Every line `ok`, `0`, the two new signatures, `4`. A spelling that differs from § Interfaces is used as generated, and noted in the commit body.

- [ ] **Step 2: failing model tests** — `core/model/src/test/kotlin/ProfileRolesTest.kt`

```kotlin
package model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The household rule, row by row of the spec's own table — `profile-rules.json` holds the rest. */
class ProfileRolesTest {
    private val andre = Profile("a", "andre", admin = true)
    private val bea = Profile("b", "Bea")
    private val mia = Profile("m", "Mia", kids = true, kidsAge = 6, parentId = "a")
    private val tom = Profile("t", "Tom", kids = true, kidsAge = 12, parentId = "b")
    private val tvKids = Profile("o", "TV kids", kids = true)
    private val household = listOf(andre, bea, mia, tom, tvKids)

    @Test
    fun aKidBelongsToItsParentOrElseToTheAdmin() {
        assertEquals("b", household.ownerOf(tom))
        assertEquals("a", household.ownerOf(tvKids))
        assertEquals("a", household.ownerOf(tom.copy(parentId = "removed-here")))
        assertNull(listOf(bea, tvKids).ownerOf(tvKids))
    }

    @Test
    fun onlyTheAdminAddsGrownUpsAndEveryGrownUpAddsKids() {
        assertTrue(household.allowed("a", RoleAction.CREATE_GROWN_UP, null))
        assertFalse(household.allowed("b", RoleAction.CREATE_GROWN_UP, null))
        assertTrue(household.allowed("b", RoleAction.CREATE_KID, null))
    }

    @Test
    fun aParentManagesItsOwnKidsAndNoOneElses() {
        assertTrue(household.allowed("b", RoleAction.SET_KIDS_AGE, "t"))
        assertTrue(household.allowed("b", RoleAction.REMOVE, "t"))
        assertFalse(household.allowed("a", RoleAction.SET_KIDS_AGE, "t"))
        assertTrue(household.allowed("a", RoleAction.REMOVE, "o"))
    }

    @Test
    fun nobodyRemovesTheAdminNotEvenTheAdmin() {
        assertFalse(household.allowed("a", RoleAction.REMOVE, "a"))
        assertFalse(household.allowed("b", RoleAction.REMOVE, "a"))
        assertTrue(household.allowed("a", RoleAction.REMOVE, "b"))
    }

    @Test
    fun aGrownUpSetsItsOwnPinAndTheAdminAnyGrownUps() {
        assertTrue(household.allowed("b", RoleAction.SET_PIN, "b"))
        assertFalse(household.allowed("b", RoleAction.SET_PIN, "a"))
        assertTrue(household.allowed("a", RoleAction.SET_PIN, "b"))
        assertFalse(household.allowed("a", RoleAction.SET_PIN, "m"))
    }

    @Test
    fun aKidOrSomeoneUnknownMayDoNothing() {
        RoleAction.entries.forEach { action ->
            assertFalse(household.allowed("m", action, "m"), "kid: $action")
            assertFalse(household.allowed("nobody", action, "m"), "unknown: $action")
        }
    }

    @Test
    fun aKidWithNoLimitRecordedSeesUpToTwelve() {
        assertEquals(12, tvKids.kidsLimit)
        assertEquals(6, mia.kidsLimit)
    }

    @Test
    fun eachMarkReadsAsTheAgeItWasMadeFrom() {
        val snapshot = WatchSnapshot.Empty.copy(kids = listOf("a", "b"), kidsFromSix = listOf("b"))
        assertEquals(mapOf("a" to 12, "b" to 6), snapshot.kidsMarks)
    }
}
```

`core/model/src/test/kotlin/ProfileRolesFixtureTest.kt`

```kotlin
package model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the web's own `profile-rules.json` — the file the web's rule and the
 * core's `profiles.rs` both answer — against [allowed]. The web decides: a
 * case that only passes after moving [allowed] away from it is a bug here.
 */
class ProfileRolesFixtureTest {
    @Test
    fun agreesWithTheWebOnEveryCase() {
        val file = assertNotNull(locateFixture(), "profile-rules.json not found above ${System.getProperty("user.dir")}")
        val cases = Json.parseToJsonElement(file.readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "profile-rules.json holds no cases")
        for (case in cases.map { it.jsonObject }) {
            val action = case.getValue("action").jsonPrimitive.content.let { wire -> RoleAction.entries.single { it.wire == wire } }
            assertEquals(
                case.getValue("expect").jsonPrimitive.boolean,
                case.getValue("profiles").jsonArray.map { it.jsonObject.toProfile() }
                    .allowed(case.getValue("actorId").jsonPrimitive.content, action, case["targetId"]?.jsonPrimitive?.contentOrNull),
                "case: ${case.getValue("name").jsonPrimitive.content}",
            )
        }
    }
}

/** A fixture `RoleView` — `{ id, kids, admin, parentId }` — as the profile the rule reads. */
private fun JsonObject.toProfile(): Profile {
    val id = getValue("id").jsonPrimitive.content
    return Profile(
        id = id,
        name = id,
        kids = getValue("kids").jsonPrimitive.boolean,
        admin = getValue("admin").jsonPrimitive.boolean,
        parentId = get("parentId")?.jsonPrimitive?.contentOrNull,
    )
}

private fun locateFixture(): File? {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
        File(dir, "web/test/fixtures/watch-state/profile-rules.json").takeIf { it.isFile }?.let { return it }
        dir = dir.parentFile
    }
    return null
}
```

Run `./gradlew :core:model:test --tests 'model.ProfileRoles*'` → compile failure (`Unresolved reference 'ownerOf'`, `'RoleAction'`, `No parameter with name 'admin'`, `'kidsFromSix'`). `core:model` has no dependency on the bindings, so this runs even while the rest is red.

- [ ] **Step 3: implement the model.** In `AgeRating.kt`, below the KDoc (deleting `KIDS_AGE_LIMIT` is Task 2):

```kotlin
/** The limits a kids profile can have — FSK 6 or FSK 12, chosen by its parent. */
val KIDS_LIMITS = listOf(6, 12)
```

`WatchSnapshot.kt:34-44` — `Profile`:

```kotlin
/**
 * Who is watching. A name is the cross-device identity a sync round
 * matches on; `id` is this device's local shorthand for it and means
 * nothing on another device.
 */
data class Profile(
    val id: String,
    val name: String,
    /** Sees only what [kidsLimit] allows. */
    val kids: Boolean = false,
    /** A kid's own limit, 6 or 12; null on a grown-up. */
    val kidsAge: Int? = null,
    /** The grown-up who made this kid, by this device's id for them — see [ownerOf] for a parent no longer here. */
    val parentId: String? = null,
    /** The household's one admin: adds and removes grown-ups and resets their PINs. */
    val admin: Boolean = false,
    /** Whether a PIN is set. Never the PIN or its hash — the core hands out neither. */
    val hasPin: Boolean = false,
) {
    /**
     * The FSK this kid sees up to. A kid recorded before limits existed has
     * none, and reads as 12 — what the sync merge makes of a bare `kids: true`.
     */
    val kidsLimit: Int get() = kidsAge ?: KIDS_LIMITS.max()
}
```

`WatchSnapshot` — after `editorsChoice` (`:59-64`):

```kotlin
    /** The Kids marks made "from 6" — a subset of [kids]; every other mark is "from 12". */
    val kidsFromSix: List<String> = emptyList(),
) {
    /** Each Kids mark by the age it was made from — the `marks` `forKidsProfile` reads. */
    val kidsMarks: Map<String, Int> get() = kids.associateWith { if (it in kidsFromSix) 6 else 12 }

    companion object {
```

`core/model/src/main/kotlin/ProfileRoles.kt`:

```kotlin
package model

/** What the household rule in [allowed] is asked about; [wire] is the contract's own name for it. */
enum class RoleAction(val wire: String) {
    CREATE_GROWN_UP("create-grown-up"),
    CREATE_KID("create-kid"),
    REMOVE("remove"),
    SET_PIN("set-pin"),
    SET_KIDS_AGE("set-kids-age"),
}

/**
 * Who manages [kid]: its parent while that names a grown-up still here,
 * otherwise the admin. That covers kids made before parents existed and a
 * kid whose parent was removed on this device but came back from another's
 * document — no migration has to guess a parent. Null with no admin either.
 */
fun List<Profile>.ownerOf(kid: Profile): String? =
    kid.parentId?.takeIf { id -> any { it.id == id && !it.kids } }
        ?: firstOrNull { it.admin }?.id

/**
 * The household's one rule — the web's `profiles.ts` and the core's
 * `profiles.rs`, all three held to `profile-rules.json`. The core enforces it
 * on every change; a screen asks it only to offer what the core will allow,
 * so a button left showing still cannot do the thing.
 */
fun List<Profile>.allowed(
    actorId: String,
    action: RoleAction,
    targetId: String?,
): Boolean {
    val actor = find { it.id == actorId }?.takeUnless { it.kids } ?: return false
    val target = targetId?.let { id -> find { it.id == id } }
    return when (action) {
        RoleAction.CREATE_GROWN_UP -> actor.admin
        RoleAction.CREATE_KID -> true
        RoleAction.REMOVE ->
            target != null && !target.admin &&
                if (target.kids) ownerOf(target) == actor.id else actor.admin && target.id != actor.id
        RoleAction.SET_PIN -> target != null && !target.kids && (target.id == actor.id || actor.admin)
        RoleAction.SET_KIDS_AGE -> target != null && target.kids && ownerOf(target) == actor.id
    }
}
```

`core/model/src/main/kotlin/ProfileRequests.kt`:

```kotlin
package model

/**
 * One thing asked of the core about who watches — checked there against
 * who is asking ([actorId]) and their PIN. Plain classes, not data classes:
 * they carry PINs, and a generated `toString` would print one into any log
 * or failure message it ever reached.
 */
sealed interface ProfileRequest {
    /** The first grown-up on a device that has none; it runs the household. */
    class CreateFirstAdmin(val name: String, val newPin: String) : ProfileRequest

    /** Opens a grown-up's profile from the picker; a kid's needs no PIN. */
    class Unlock(val id: String, val pin: String) : ProfileRequest

    /** Makes [id] the household's admin; with no PIN yet, [pin] becomes its PIN. */
    class ClaimAdmin(val id: String, val pin: String) : ProfileRequest

    class CreateGrownUp(val actorId: String, val pin: String, val name: String, val newPin: String) : ProfileRequest

    class CreateKid(val actorId: String, val pin: String, val name: String, val age: Int) : ProfileRequest

    /** A grown-up's kids go with it. */
    class Remove(val actorId: String, val pin: String, val id: String) : ProfileRequest

    /** [pin] is ignored when a grown-up with no PIN sets its own first one. */
    class SetPin(val actorId: String, val pin: String, val id: String, val newPin: String) : ProfileRequest

    class SetKidsAge(val actorId: String, val pin: String, val id: String, val age: Int) : ProfileRequest
}

/** How the core answered a [ProfileRequest] — the contract's reasons. */
sealed interface ProfileOutcome {
    data object Done : ProfileOutcome

    /** A malformed name, new PIN or age — or a store that could not be written. */
    data object Invalid : ProfileOutcome

    data object NotFound : ProfileOutcome

    /** Five wrong PINs in a row: no PIN is compared again for [seconds]. */
    data class Wait(val seconds: Int) : ProfileOutcome

    /** A grown-up with no PIN yet — it sets one first. */
    data object NoPin : ProfileOutcome

    /** Also the answer to a malformed current PIN: it is compared, and counts. */
    data object WrongPin : ProfileOutcome

    data object NotAllowed : ProfileOutcome
}
```

`core/model/build.gradle.kts` — declare the fixture beside the markdown one:

```kotlin
tasks.named<Test>("test") {
    inputs.file(rootProject.file("../web/test/fixtures/markdown/cases.json"))
    inputs.file(rootProject.file("../web/test/fixtures/watch-state/profile-rules.json"))
}
```

`./gradlew :core:model:test` → green, fixture cases included.

- [ ] **Step 4: failing fake-core tests** — `core/testing/src/test/kotlin/testing/FakeProfilesTest.kt`

```kotlin
package testing

import kotlinx.coroutines.runBlocking
import org.junit.Test
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.ProfileOutcome
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [FakeCore]'s household rule, its refusals and the wrong-PIN wait, so a view model test meets what the tablet's core answers. */
class FakeProfilesTest {
    private fun household(admin: Boolean = true) =
        FakeCore().apply {
            profiles = listOf(
                Profile("a", "andre", admin = admin),
                Profile("b", "Bea"),
                Profile("t", "Tom", kids = true, kidsAge = 12u, parentId = "b"),
                Profile("o", "Old"),
            )
            roles.pins["a"] = "1234"
            roles.pins["b"] = "5678"
        }

    @Test
    fun refusalsComeInTheContractsOrder() =
        runBlocking {
            val core = household()
            assertEquals(ProfileOutcome.Invalid, core.createKid("nobody", "12a4", "  ", 12u))
            assertEquals(ProfileOutcome.NotFound, core.createKid("nobody", "12a4", "Mia", 12u))
            assertEquals(ProfileOutcome.NotAllowed, core.createKid("t", "1234", "Mia", 12u))
            assertEquals(ProfileOutcome.NoPin, core.createKid("o", "1234", "Mia", 12u))
            assertEquals(ProfileOutcome.WrongPin, core.createKid("b", "12a4", "Mia", 12u))
            assertEquals(ProfileOutcome.NotAllowed, core.deleteProfile("b", "5678", "a"))
        }

    @Test
    fun theFifthWrongPinStartsAMinuteThatEvenTheRightPinMeets() =
        runBlocking {
            val core = household()
            repeat(5) { assertEquals(ProfileOutcome.WrongPin, core.unlockProfile("a", "0000")) }
            assertEquals(ProfileOutcome.Wait(60u), core.unlockProfile("a", "1234"))
            core.roles.nowMs += 59_001
            assertEquals(ProfileOutcome.Wait(1u), core.unlockProfile("b", "5678"))
            core.roles.nowMs += 999
            assertEquals(ProfileOutcome.Done, core.unlockProfile("a", "1234"))
        }

    @Test
    fun aKidsUnlockAndAFirstOwnPinNeverWait() =
        runBlocking {
            val core = household()
            repeat(5) { core.unlockProfile("a", "0000") }
            assertEquals(ProfileOutcome.Done, core.unlockProfile("t", ""))
            assertEquals(ProfileOutcome.Done, core.setPin("o", "", "o", "4321"))
            assertEquals(true, core.profiles().single { it.id == "o" }.hasPin)
        }

    @Test
    fun aRightPinClearsTheCount() =
        runBlocking {
            val core = household()
            repeat(4) { core.unlockProfile("a", "0000") }
            assertEquals(ProfileOutcome.Done, core.unlockProfile("a", "1234"))
            repeat(4) { assertEquals(ProfileOutcome.WrongPin, core.unlockProfile("a", "0000")) }
        }

    @Test
    fun removingAGrownUpTakesItsKidsAndTheChoiceWithIt() =
        runBlocking {
            val core = household().apply { chosen = "t" }
            assertEquals(ProfileOutcome.Done, core.deleteProfile("a", "1234", "b"))
            assertEquals(listOf("a", "o"), core.profiles().map { it.id })
            assertNull(core.chosenProfile())
        }

    @Test
    fun theFirstClaimMakesTheOnlyAdminAndSetsAMissingPin() =
        runBlocking {
            val core = household(admin = false)
            assertEquals(ProfileOutcome.Invalid, core.claimAdmin("o", "12"))
            assertEquals(ProfileOutcome.Done, core.claimAdmin("o", "4321"))
            assertEquals(listOf("o"), core.profiles().filter { it.admin }.map { it.id })
            assertEquals("4321", core.roles.pins["o"])
            assertEquals(ProfileOutcome.NotAllowed, core.claimAdmin("b", "5678"))
        }

    @Test
    fun theFirstProfileRunsTheHouseholdOnlyWhileThereIsNoGrownUp() =
        runBlocking {
            val core = FakeCore().apply { profiles = listOf(Profile("k", "TV kids", kids = true)) }
            assertEquals(ProfileOutcome.Done, core.createFirstAdmin("Ann", "1234"))
            val ann = core.profiles().single { it.name == "Ann" }
            assertEquals(Triple(true, true, false), Triple(ann.admin, ann.hasPin, ann.kids))
            assertEquals(ProfileOutcome.NotAllowed, core.createFirstAdmin("Bo", "5678"))
        }

    @Test
    fun aKidIsMadeUnderItsParentAtSixOrTwelveOnly() =
        runBlocking {
            val core = household()
            assertEquals(ProfileOutcome.Invalid, core.createKid("b", "5678", "Mia", 7u))
            assertEquals(ProfileOutcome.Done, core.createKid("b", "5678", "Mia", 6u))
            val mia = core.profiles().single { it.name == "Mia" }
            assertEquals(Triple(true, 6.toUByte(), "b"), Triple(mia.kids, mia.kidsAge, mia.parentId))
        }
}
```

Rewrite the profile half of `core/testing/src/main/kotlin/testing/CoreContract.kt` (`:66-98`; the KDoc bullet `:28-31` names the new calls and says a fresh core starts from `createFirstAdmin`):

```kotlin
    @Test
    fun aFreshCoreHasNobodyToChoose() {
        runBlocking {
            val core = core()
            assertEquals(emptyList(), core.profiles())
            assertFalse(core.chooseProfile("nobody"))
            assertEquals(null, core.chosenProfile())
        }
    }

    @Test
    fun theFirstProfileRunsTheHouseholdAndThereIsOnlyOneFirst() {
        runBlocking {
            val core = core()
            assertEquals(ProfileOutcome.Invalid, core.createFirstAdmin("  ", "1234"))
            assertEquals(ProfileOutcome.Invalid, core.createFirstAdmin("Alice", "12"))
            assertEquals(ProfileOutcome.Done, core.createFirstAdmin("Alice", "1234"))
            val alice = core.profiles().single()
            assertTrue(alice.admin && alice.hasPin && !alice.kids)
            assertEquals(ProfileOutcome.NotAllowed, core.createFirstAdmin("Bea", "5678"))
        }
    }

    @Test
    fun theAdminMakesAKidWhoOpensWithoutAPinAndCanBeChosen() {
        runBlocking {
            val core = core()
            core.createFirstAdmin("Alice", "1234")
            val alice = core.profiles().single().id
            assertEquals(ProfileOutcome.Done, core.createKid(alice, "1234", "Mia", 6u))
            val mia = core.profiles().single { it.name == "Mia" }
            assertEquals(6.toUByte() to alice, mia.kidsAge to mia.parentId)
            assertEquals(ProfileOutcome.Done, core.unlockProfile(mia.id, ""))
            assertTrue(core.chooseProfile(mia.id))
            assertEquals(mia.id, core.chosenProfile())
        }
    }

    @Test
    fun aWrongOrMalformedPinIsWrongNotInvalid() {
        runBlocking {
            val core = core()
            core.createFirstAdmin("Alice", "1234")
            val alice = core.profiles().single().id
            assertEquals(ProfileOutcome.WrongPin, core.unlockProfile(alice, "0000"))
            assertEquals(ProfileOutcome.WrongPin, core.unlockProfile(alice, "12a4"))
            assertEquals(ProfileOutcome.WrongPin, core.createKid(alice, "12a4", "Mia", 6u))
            assertEquals(ProfileOutcome.Done, core.unlockProfile(alice, "1234"))
        }
    }

    @Test
    fun removingTakesAProfileOffTheListButNeverTheAdmin() {
        runBlocking {
            val core = core()
            core.createFirstAdmin("Alice", "1234")
            val alice = core.profiles().single().id
            core.createKid(alice, "1234", "Chris", 12u)
            val chris = core.profiles().single { it.name == "Chris" }.id
            assertEquals(ProfileOutcome.Done, core.deleteProfile(alice, "1234", chris))
            assertTrue(core.profiles().none { it.id == chris })
            assertEquals(ProfileOutcome.NotAllowed, core.deleteProfile(alice, "1234", alice))
        }
    }

    @Test
    fun malformedInputIsInvalidAndActingAsNobodyIsNotFound() {
        runBlocking {
            val core = core()
            assertEquals(ProfileOutcome.Invalid, core.createGrownUp("nobody", "1234", "Ann", "123"))
            assertEquals(ProfileOutcome.Invalid, core.createKid("nobody", "1234", "  ", 12u))
            assertEquals(ProfileOutcome.Invalid, core.createKid("nobody", "1234", "Mia", 7u))
            assertEquals(ProfileOutcome.NotFound, core.createKid("nobody", "12a4", "Mia", 6u))
            assertEquals(ProfileOutcome.NotFound, core.deleteProfile("nobody", "1234", "nobody"))
            assertEquals(ProfileOutcome.NotFound, core.unlockProfile("nobody", "1234"))
            assertEquals(ProfileOutcome.NotFound, core.claimAdmin("nobody", "1234"))
        }
    }

    @Test
    fun aKidsMarkCarriesTheAgeItWasMadeFrom() {
        runBlocking {
            val core = core()
            core.setKids("s1", 6u)
            assertEquals(listOf("s1") to listOf("s1"), core.snapshot("nobody").let { it.kids to it.kidsFromSix })
            core.setKids("s1", 12u)
            assertEquals(listOf("s1") to emptyList<String>(), core.snapshot("nobody").let { it.kids to it.kidsFromSix })
            core.setKids("s1", null)
            assertEquals(emptyList<String>() to emptyList<String>(), core.snapshot("nobody").let { it.kids to it.kidsFromSix })
        }
    }
```
(imports `uniffi.mediagram_core.ProfileOutcome`, `kotlin.test.assertFalse`.)

`./gradlew :core:testing:testDebugUnitTest` → compile failure (`Unresolved reference 'roles'`, `FakeCore is not abstract and does not implement 'createFirstAdmin'`, `'createProfile' overrides nothing`).

- [ ] **Step 5: implement the fake.** `core/testing/build.gradle.kts` — add `implementation(project(":core:model"))`, commented: the fake enforces the same `allowed` Manage offers from. Create `core/testing/src/main/kotlin/testing/FakeProfiles.kt`:

```kotlin
package testing

import data.toModel
import model.RoleAction
import model.allowed
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.ProfileOutcome

private const val MAX_WRONG_PINS = 5
private const val WAIT_MS = 60_000L
private val PIN_FORMAT = Regex("[0-9]{4}")
private val KIDS_AGES = setOf<UByte>(6u, 12u)

/**
 * [FakeCore]'s profile management, in the contract's order: malformed input,
 * nobody by that id, a kid acting, the wait (only where a PIN is about to be
 * compared), no PIN yet, a wrong PIN — a malformed one included, which is
 * compared and counted — and then the household rule through [allowed], the
 * function Manage offers from. `CoreContract` holds this and the real core
 * to the same cases.
 *
 * PINs are kept in the clear in [pins]: a fake has nothing to protect.
 */
class FakeProfiles(
    private val core: FakeCore,
) {
    /** PINs by profile id; `FakeCore.profiles()` reports `hasPin` from this. */
    val pins = mutableMapOf<String, String>()

    /** The wait's clock in ms; a test moves it on to end a wait. */
    var nowMs = 0L

    private var wrongPins = 0
    private var waitEndsAt: Long? = null
    private var nextId = 1

    fun createFirstAdmin(name: String, newPin: String): ProfileOutcome {
        val clean = cleanName(name)
        if (clean == null || !PIN_FORMAT.matches(newPin)) return ProfileOutcome.Invalid
        // Only while no grown-up exists here — or a fresh install could never begin.
        if (core.profiles.any { !it.kids }) return ProfileOutcome.NotAllowed
        return added(Profile("p${nextId++}", clean, admin = true), newPin)
    }

    fun createGrownUp(actorId: String, pin: String, name: String, newPin: String): ProfileOutcome {
        val clean = cleanName(name)
        if (clean == null || !PIN_FORMAT.matches(newPin)) return ProfileOutcome.Invalid
        refusal(actorId, pin, RoleAction.CREATE_GROWN_UP, null)?.let { return it }
        return added(Profile("p${nextId++}", clean), newPin)
    }

    fun createKid(actorId: String, pin: String, name: String, kidsAge: UByte): ProfileOutcome {
        val clean = cleanName(name)
        if (clean == null || kidsAge !in KIDS_AGES) return ProfileOutcome.Invalid
        refusal(actorId, pin, RoleAction.CREATE_KID, null)?.let { return it }
        return added(Profile("p${nextId++}", clean, kids = true, kidsAge = kidsAge, parentId = actorId), null)
    }

    fun delete(actorId: String, pin: String, id: String): ProfileOutcome {
        refusal(actorId, pin, RoleAction.REMOVE, id)?.let { return it }
        val gone = setOf(id) + core.profiles.filter { it.parentId == id }.map { it.id }
        core.profiles = core.profiles.filterNot { it.id in gone }
        pins.keys.removeAll(gone)
        if (core.chosen in gone) core.chosen = null
        return ProfileOutcome.Done
    }

    fun unlock(id: String, pin: String): ProfileOutcome {
        val target = core.profiles.find { it.id == id } ?: return ProfileOutcome.NotFound
        if (target.kids) return ProfileOutcome.Done
        return check(id, pin) ?: ProfileOutcome.Done
    }

    fun claimAdmin(id: String, pin: String): ProfileOutcome {
        val target = core.profiles.find { it.id == id } ?: return ProfileOutcome.NotFound
        if (target.kids || core.profiles.any { it.admin }) return ProfileOutcome.NotAllowed
        if (id in pins) {
            check(id, pin)?.let { return it }
        } else if (!PIN_FORMAT.matches(pin)) {
            return ProfileOutcome.Invalid
        }
        pins.getOrPut(id) { pin }
        core.profiles = core.profiles.map { it.copy(admin = it.id == id) }
        return ProfileOutcome.Done
    }

    fun setPin(actorId: String, pin: String, id: String, newPin: String): ProfileOutcome {
        if (!PIN_FORMAT.matches(newPin)) return ProfileOutcome.Invalid
        // A grown-up with no PIN sets its own first one with nothing to prove —
        // the way in for a profile made before PINs existed. Nothing is compared, so nothing waits.
        val firstOwn = actorId == id && id !in pins && core.profiles.any { it.id == id && !it.kids }
        if (!firstOwn) refusal(actorId, pin, RoleAction.SET_PIN, id)?.let { return it }
        pins[id] = newPin
        return ProfileOutcome.Done
    }

    fun setKidsAge(actorId: String, pin: String, id: String, kidsAge: UByte): ProfileOutcome {
        if (kidsAge !in KIDS_AGES) return ProfileOutcome.Invalid
        refusal(actorId, pin, RoleAction.SET_KIDS_AGE, id)?.let { return it }
        core.profiles = core.profiles.map { if (it.id == id) it.copy(kidsAge = kidsAge) else it }
        return ProfileOutcome.Done
    }

    private fun added(profile: Profile, pin: String?): ProfileOutcome {
        core.profiles += profile
        pin?.let { pins[profile.id] = it }
        return ProfileOutcome.Done
    }

    /** Everything after well-formed input, for a request made as [actorId]. */
    private fun refusal(actorId: String, pin: String, action: RoleAction, targetId: String?): ProfileOutcome? {
        val actor = core.profiles.find { it.id == actorId }
        if (actor == null || (targetId != null && core.profiles.none { it.id == targetId })) return ProfileOutcome.NotFound
        if (actor.kids) return ProfileOutcome.NotAllowed
        check(actorId, pin)?.let { return it }
        return ProfileOutcome.NotAllowed.takeUnless { core.profiles.map { it.toModel() }.allowed(actorId, action, targetId) }
    }

    /**
     * Null when [pin] is [id]'s. No PIN yet answers that, without waiting —
     * nothing is compared. Otherwise the wait comes first; then each miss
     * counts toward it and a match clears the count.
     */
    private fun check(id: String, pin: String): ProfileOutcome? {
        val stored = pins[id] ?: return ProfileOutcome.NoPin
        waiting()?.let { return it }
        if (stored == pin) {
            wrongPins = 0
            return null
        }
        if (++wrongPins >= MAX_WRONG_PINS) waitEndsAt = nowMs + WAIT_MS
        return ProfileOutcome.WrongPin
    }

    private fun waiting(): ProfileOutcome? {
        val ends = waitEndsAt ?: return null
        if (nowMs >= ends) {
            waitEndsAt = null
            wrongPins = 0
            return null
        }
        return ProfileOutcome.Wait(((ends - nowMs + 999) / 1000).toUInt())
    }

    /** Trims and collapses whitespace, null when nothing is left — `clean_name`'s rule in `state/profiles.rs`. */
    private fun cleanName(name: String): String? =
        name.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ").takeIf { it.isNotEmpty() }
}
```

In `FakeCore.kt`: delete `createProfile`, `nextProfileId`, `cleanProfileName` (`:398-422`) and the old `deleteProfile` (`:432-437`); the `profiles` KDoc (`:138`) now says "Written directly to seed a test, or grown through [roles]."; change `profiles()` (`:392-396`), `setKids` (`:450`), `snapshot` (`:439-440`) and add:

```kotlin
    /** Who may do what, their PINs and the wrong-PIN wait — see [FakeProfiles]. */
    val roles = FakeProfiles(this)

    /** Kids marks by set id, as the core keeps them: 6 = from 6, 12 = from 12. */
    val kidsMarks = mutableMapOf<String, UByte>()

    override suspend fun profiles(): List<Profile> {
        profilesCalls++
        profilesFailure?.let { throw it }
        return profiles.map { it.copy(hasPin = it.id in roles.pins) }
    }

    override suspend fun createFirstAdmin(name: String, newPin: String) = roles.createFirstAdmin(name, newPin)

    override suspend fun createGrownUp(actorId: String, pin: String, name: String, newPin: String) = roles.createGrownUp(actorId, pin, name, newPin)

    override suspend fun createKid(actorId: String, pin: String, name: String, kidsAge: UByte) = roles.createKid(actorId, pin, name, kidsAge)

    override suspend fun deleteProfile(actorId: String, pin: String, id: String) = roles.delete(actorId, pin, id)

    override suspend fun unlockProfile(id: String, pin: String) = roles.unlock(id, pin)

    override suspend fun claimAdmin(id: String, pin: String) = roles.claimAdmin(id, pin)

    override suspend fun setPin(actorId: String, pin: String, id: String, newPin: String) = roles.setPin(actorId, pin, id, newPin)

    override suspend fun setKidsAge(actorId: String, pin: String, id: String, kidsAge: UByte) = roles.setKidsAge(actorId, pin, id, kidsAge)

    override suspend fun setKids(setId: String, age: UByte?) {
        if (age == null) kidsMarks.remove(setId) else kidsMarks[setId] = age
    }

    override suspend fun snapshot(profileId: String): StateSnapshot =
        StateSnapshot(
            emptyList(), emptyList(), emptyList(), kidsMarks.keys.toList(), emptyList(), null,
            kidsFromSix = kidsMarks.filterValues { it == SIX }.keys.toList(),
        )
```
with `private val SIX: UByte = 6u` at file bottom.

- [ ] **Step 6: repository.** Create `core/data/src/main/kotlin/CoreProfileCalls.kt`:

```kotlin
package data

import model.Profile
import model.ProfileOutcome
import model.ProfileRequest
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.Profile as CoreProfile
import uniffi.mediagram_core.ProfileOutcome as CoreOutcome

/** [this] request as the one core call that answers it. */
internal suspend fun ProfileRequest.sendTo(core: CoreInterface): ProfileOutcome =
    when (this) {
        is ProfileRequest.CreateFirstAdmin -> core.createFirstAdmin(name, newPin)
        is ProfileRequest.Unlock -> core.unlockProfile(id, pin)
        is ProfileRequest.ClaimAdmin -> core.claimAdmin(id, pin)
        is ProfileRequest.CreateGrownUp -> core.createGrownUp(actorId, pin, name, newPin)
        is ProfileRequest.CreateKid -> core.createKid(actorId, pin, name, age.toUByte())
        is ProfileRequest.Remove -> core.deleteProfile(actorId, pin, id)
        is ProfileRequest.SetPin -> core.setPin(actorId, pin, id, newPin)
        is ProfileRequest.SetKidsAge -> core.setKidsAge(actorId, pin, id, age.toUByte())
    }.toModel()

private fun CoreOutcome.toModel(): ProfileOutcome =
    when (this) {
        CoreOutcome.Done -> ProfileOutcome.Done
        CoreOutcome.Invalid -> ProfileOutcome.Invalid
        CoreOutcome.NotFound -> ProfileOutcome.NotFound
        is CoreOutcome.Wait -> ProfileOutcome.Wait(seconds.toInt())
        CoreOutcome.NoPin -> ProfileOutcome.NoPin
        CoreOutcome.WrongPin -> ProfileOutcome.WrongPin
        CoreOutcome.NotAllowed -> ProfileOutcome.NotAllowed
    }

/** The core's profile as this app's own — the one place the two meet, shared with `testing.FakeProfiles`. */
fun CoreProfile.toModel(): Profile = Profile(id, name, kids, kidsAge?.toInt(), parentId, admin, hasPin)
```

`WatchStateRepository.kt`: delete `createProfile` (`:52-56`, impl `:265-277`) and `deleteProfile` (`:122-130`, impl `:252-263`); delete `toProfile` (`:385`) and read `core.profiles().map { it.toModel() }` at `:208`; add `kidsFromSix = kidsFromSix` in `StateSnapshot.toModel` (`:393-401`); replace `setKids` (`:80-84`, impl `:303-306`) and add `manage`:

```kotlin
    /**
     * Marks a title for kids from [age] — 6 or 12 — or takes the mark away
     * with null. The mark is the household's, not the profile's, but still
     * requires a chosen profile; otherwise does nothing.
     */
    suspend fun setKids(
        setId: String,
        age: Int?,
    )

    /**
     * Sends one profile request to the core, which checks it against who is
     * asking and their PIN — the household's rule is enforced there, not
     * here. On [ProfileOutcome.Done] for anything that may have changed who
     * exists or what they are, [profiles] and the choice are re-read (the core
     * forgets a removed profile's choice itself); an unlock changes nothing
     * stored and re-reads nothing. The default refuses, for a repository with
     * no core behind it.
     *
     * Local, as on the web: a profile another device's sync document still
     * names is created again by the next round that pulls it.
     */
    suspend fun manage(request: ProfileRequest): ProfileOutcome = ProfileOutcome.NotAllowed
```

```kotlin
    override suspend fun manage(request: ProfileRequest): ProfileOutcome {
        val started = synchronized(publicationLock) { resetRevision }
        val core = coreProvider.awaitCore()
        val outcome = withContext(dispatcher) { request.sendTo(core) }
        if (outcome != ProfileOutcome.Done || request is ProfileRequest.Unlock) return outcome
        synchronized(publicationLock) {
            if (resetRevision != started || coreProvider.core.value !== core) return outcome
        }
        reload()
        return outcome
    }

    override suspend fun setKids(
        setId: String,
        age: Int?,
    ) = writing { core, _ -> core.setKids(setId, age?.toUByte()) }
```

`core/data/src/test/kotlin/WatchStateRepositoryTest.kt`: `StateCore` — delete `createProfile` (`:37-44`); `setKids` (`:68-78`) becomes `(setId: String, age: UByte?)`, writing `"setKids $setId $age"` and marking when `age != null`. Replace the two creation tests (`:134-157`) and `:187-199`:

```kotlin
    @Test
    fun aDoneRequestRereadsWhoExistsAndWhatTheyAre() =
        runTest {
            val core = FakeCore().apply { profiles = listOf(CoreProfile("a", "andre", admin = true)); roles.pins["a"] = "1234" }
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
            repository.reload()

            assertEquals(ProfileOutcome.Done, repository.manage(ProfileRequest.CreateKid("a", "1234", "Mia", 6)))

            val mia = repository.profiles.value.single { it.name == "Mia" }
            assertEquals(Triple(true, 6, "a"), Triple(mia.kids, mia.kidsAge, mia.parentId))
            assertEquals(true, repository.profiles.value.single { it.id == "a" }.let { it.admin && it.hasPin })
        }

    @Test
    fun anUnlockOrARefusalRereadsNothing() =
        runTest {
            val core = FakeCore().apply { profiles = listOf(CoreProfile("a", "andre")); roles.pins["a"] = "1234" }
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
            repository.reload()
            val reads = core.profilesCalls

            assertEquals(ProfileOutcome.Done, repository.manage(ProfileRequest.Unlock("a", "1234")))
            assertEquals(ProfileOutcome.WrongPin, repository.manage(ProfileRequest.CreateKid("a", "0000", "Mia", 6)))

            assertEquals(reads, core.profilesCalls)
        }

    /** [CoreInterface.setKids] takes no profile id — marking is shared, not this profile's own — and carries its age. */
    @Test
    fun setKidsWritesGloballyWithTheAgeItWasMadeFrom() =
        runTest {
            val core = StateCore(initialProfiles = listOf(CoreProfile("p1", "Alice")), chosen = "p1")
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
            repository.reload()

            repository.setKids("set-1", 6)

            assertEquals(listOf("setKids set-1 6"), core.writes)
            assertEquals(listOf("set-1"), repository.snapshot.value.kids)
        }
```

`WatchStateOwnershipTest.kt:50-56` → `override suspend fun createKid(actorId: String, pin: String, name: String, kidsAge: UByte): ProfileOutcome { write(); return ProfileOutcome.Done }`; `:243` → `if (creating) repository.manage(ProfileRequest.CreateKid("a", "1234", "Chris", 12)) else repository.reload()`; rename the test `resetPreventsPendingReadsAndProfileChangesFromRestoringState`.

- [ ] **Step 7: every other fake** — delete the `createProfile`/`deleteProfile` overrides and make `setKids(setId: String, age: Int?)` in `WatchSyncTest.kt:63-66` (+ its `setKids`), `CatalogViewModelTest.kt:80-83` (+ `setKids`; KDoc `:49` drops "[createProfile] included"), `profile/ProfileViewModelTest.kt:35-39,64-82,102-105`, `ui-tv/.../FakeWatchState.kt:47-54,77-82,89-92`, `PlayerActionFailureTest.kt:121-127` and `PlayerActionNoticeTest.kt:153` (forward `age`). `feature/player/src/test/kotlin/FakeWatchStateRepository.kt:40-43` goes; `:95-105` becomes:

```kotlin
    override suspend fun setKids(
        setId: String,
        age: Int?,
    ) {
        if (chosenProfileId.value == null) return
        calls += "setKids $setId $age"
        val kids = _snapshot.value.kids - setId
        val fromSix = _snapshot.value.kidsFromSix - setId
        _snapshot.value =
            _snapshot.value.copy(
                kids = if (age == null) kids else kids + setId,
                kidsFromSix = if (age == 6) fromSix + setId else fromSix,
            )
    }
```

- [ ] **Step 8: `ProfileViewModel` without add/remove.** Delete `add` and `remove` (`ProfileViewModel.kt:162-216`); class KDoc "choose, add and remove" → "choose; adding and removing live in Manage, behind a grown-up's PIN"; "Stay as I am" follows the choice (`:76-95`):

```kotlin
        val state: StateFlow<ProfileUiState> =
            combine(mode, repository.profiles, repository.chosenProfileId) { current, profiles, chosenId ->
                when (current) {
                    Mode.Loading -> ProfileUiState.Loading
                    // Someone to stay as: a profile removed in Manage takes the offer with it.
                    is Mode.Picking -> ProfileUiState.Picking(profiles, current.canStay && chosenId != null, current.error)
                    is Mode.Chosen ->
                        profiles
                            .find { it.id == current.profileId }
                            ?.let(ProfileUiState::Chosen)
                            // The chosen row has not reached [profiles] yet — right
                            // after this device's own chooseProfile() — not a real gap.
                            ?: ProfileUiState.Loading
                }
            }.onStart { settle() }
```

`ProfileViewModelTest.kt`: delete `aFailedAddKeepsThePickerAndDoesNotInventAProfile` (`:194-208`), `addingAProfileListsItWithoutChoosingIt` (`:383-400`), `addingAKidsProfilePassesTheFlagThrough` (`:402-411`); drop the `vm.add(...)` lines and their assertions in `refusedProfileWritesAreShownAsFailures` (`:275-289`) and `cancelledProfileWritesLeaveThePickerUnchanged` (`:291-309`); replace the two removal tests (`:439-489`) with:

```kotlin
    /** Manage removes elsewhere; the picker follows the repository rather than being told. */
    @Test
    fun aChosenProfileRemovedElsewhereTakesAwayStayAsIAm() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeWatchStateRepository(listOf(Profile("p1", "Alice"), Profile("p2", "Bea")), chosenId = "p1")
            val vm = ProfileViewModel(repository, FakeWatchSync())
            vm.state.test {
                awaitItem()
                awaitItem() as ProfileUiState.Chosen
                vm.reopen()
                assertEquals(true, (awaitItem() as ProfileUiState.Picking).canStay)

                repository.profiles.value = listOf(Profile("p2", "Bea"))
                repository.chosenProfileId.value = null
                runCurrent()

                val picking = expectMostRecentItem() as ProfileUiState.Picking
                assertEquals(listOf(Profile("p2", "Bea")), picking.profiles)
                assertEquals(false, picking.canStay)
            }
        }
```

- [ ] **Step 9: pickers lose add/remove (compile-keeping; phase 07 draws the new picker).**
  - `ui-mobile/.../profile/ProfileGate.kt:30,33` — delete `onAdd = viewModel::add,` and `onRemove = viewModel::remove,`.
  - `ui-mobile/.../profile/ProfilePickerScreen.kt` — delete params `onAdd`/`onRemove` (`:54,57,80,83`), `NEW_PROFILE`/`NAME_PROMPT` (`:42-43`), `naming`/`removing` (`:85-86`), the `AddTile` item (`:103`) and function (`:161-173`), the remove button (`:110-114`), both dialogs (`:122-133`), `NameDialog` (`:192-220`). Show `TextButton(onClick = onRetry) { Text("Try again") }` when `state.error != null || state.profiles.isEmpty()`.
  - `ui-tv/.../profile/TvProfileGate.kt:43,46` — delete the two lines.
  - `ui-tv/.../profile/TvProfilePicker.kt` — delete params `onAdd`/`onRemove` (`:66,69,82,85`), `REMOVE` (`:39`), `adding`/`removing` + add-flow branch (`:87-104`), the `TvAddTile` item (`:172-179`), remove row (`:188-196`) and dialog (`:206-208`); `:115` → `val focusTryAgainFirst = state.profiles.isEmpty()`, and draw the Try again row when `error != null || state.profiles.isEmpty()` (on an empty list it is the only focusable thing, and `requestFocus()` on an unattached requester throws); `:152` → `profileTileWidth(state.profiles.size, …)`.
  - Tests: `TvProfilePickerStateTest.kt` — drop the `"New profile"` assertion (`:71`), delete `theAddTileOpensTheNameQuestion` (`:102-111`), turn `fourProfilesAndNewProfileFit…` (`:118-134`) into `fourProfilesFitInsideTheSafeWidthOfA960dpTelevision` measuring `onNodeWithTag("tv-profile-tile-TV kids")`; `show()` drops `onAdd`. androidTest `TvProfilePickerTest.kt` — delete `theAddTileOpensTheNameQuestionWithTheFieldFocused` and `theKidsStepIsFocusedFlipsAndAddsWithTheChosenValue` (`:96-132`); the six-profile test (`:74-84`) presses right five times and asserts `tv-profile-tile-profile-6`; `show()` drops `onAdd`. `TvHousekeepingTest.kt` — delete the four removal tests (`:124-180`) and `nameInDialog` (`:212`); KDoc `:38` drops "Remove a profile…". Phase 07 replaces them with Manage-flow tests.

- [ ] **Step 10: player plumbing.** `PlayerMarksController.toggleKids` (`:92-96`) keeps today's behaviour over the new call (import `model.KIDS_LIMITS`):

```kotlin
        val marked = setId in repository.snapshot.value.kids
        write("Kids update") {
            repository.setKids(setId, if (marked) null else KIDS_LIMITS.max())
            true
        }
```

- [ ] **Step 11: run everything, expect green**

```bash
./gradlew :core:model:test :core:testing:testDebugUnitTest :core:data:testDebugUnitTest \
  :feature:catalog:testDebugUnitTest :feature:player:testDebugUnitTest :feature:setup:testDebugUnitTest \
  :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin :core:rust:compileDebugAndroidTestKotlin
```
All pass/compile; `feature:setup` unchanged and green.

- [ ] **Step 12: commit** — bump (§ Bumping, patch), then:

```bash
cd /home/andre/Workspace/mediagram
git add android/core android/feature android/ui-mobile/src/main/kotlin/ui/profile android/ui-tv/src/main/kotlin/ui/tv/profile \
  android/ui-tv/src/test android/ui-tv/src/androidTest \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(android): profiles are managed by role and PIN through the core; release <next>"
```
Changelog (B4), for the viewer: the Android picker no longer adds or removes profiles until Manage profiles arrives in the next release. Push only with phase 05's commit beneath it.

### Task 2: A kid sees up to its own limit

**Files:** Modify `core/model/src/main/kotlin/AgeRating.kt`, `feature/catalog/src/main/kotlin/{CatalogViewModel,CatalogUiState}.kt`, `feature/player/src/main/kotlin/PlayerMarksController.kt:59,91`, `ui-mobile/.../catalog/CatalogScreen.kt:90`, `ui-tv/.../catalog/TvCatalogBody.kt:49`; tests `feature/catalog/src/test/kotlin/{AgeRatingTest,CatalogViewModelTest}.kt`, `ui-tv/src/test/.../catalog/TvCatalogScreenStateTest.kt:243`.

- [ ] **Step 1: failing tests.** `AgeRatingTest.kt` — replace `twelveAndYoungerIsForKidsAndOlderIsNot` (`:19-25`) and `aKidsProfileSeesRatedForKidsOrMarkedByHandAndNothingElse` (`:53-66`), pass `12` to the unrated cases (`:28-34`):

```kotlin
    @Test
    fun theKidsOwnLimitDecidesARatedTitle() {
        assertEquals(KidsVerdict.SAFE, kidsVerdictOf("6", 6))
        assertEquals(KidsVerdict.UNSAFE, kidsVerdictOf("12", 6))
        assertEquals(KidsVerdict.SAFE, kidsVerdictOf("12", 12))
        assertEquals(KidsVerdict.UNSAFE, kidsVerdictOf("16", 12))
        assertEquals(KidsVerdict.SAFE, kidsVerdictOf("0", 6))
    }

    @Test
    fun aKidAtTwelveSeesRatedTwelveOrUnderAndEveryHandMark() {
        val sets = listOf(
            rated("Zero", "0"), rated("Six", "6"), rated("Twelve", "12"), rated("Sixteen", "16"),
            rated("Unrated", null), rated("FromTwelve", null), rated("FromSix", null), rated("SixteenMarked", "16"),
        )
        val marks = mapOf("FromTwelve" to 12, "FromSix" to 6, "SixteenMarked" to 12)
        assertEquals(listOf("Zero", "Six", "Twelve", "FromTwelve", "FromSix"), forKidsProfile(sets, marks, 12).map { it.setId })
    }

    @Test
    fun aKidAtSixSeesRatedSixOrUnderAndOnlyMarksFromSix() {
        val sets = listOf(rated("Six", "6"), rated("Twelve", "12"), rated("FromTwelve", null), rated("FromSix", null))
        val marks = mapOf("FromTwelve" to 12, "FromSix" to 6, "Twelve" to 6)
        assertEquals(listOf("Six", "FromSix"), forKidsProfile(sets, marks, 6).map { it.setId })
    }
```

`CatalogViewModelTest.kt`: `CatalogUiState.KidsEmpty` → `CatalogUiState.KidsEmpty(12)` (`:761,782`); `CatalogUiState.KidsEmpty -> emptySet()` → `is CatalogUiState.KidsEmpty -> emptySet()` (`:836`); add:

```kotlin
    @Test
    fun aKidAtSixSeesItsOwnLimitAndAChangedLimitRefiltersWithoutARead() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeCatalogRepository(
                    given = listOf(
                        fakeSet(Kind.MOVIE, "Family").copy(fsk = "6"),
                        fakeSet(Kind.MOVIE, "Teen").copy(fsk = "12"),
                        fakeSet(Kind.MOVIE, "FromSix"),
                        fakeSet(Kind.MOVIE, "FromTwelve"),
                    ),
                )
            val watch = FakeCatalogWatchState(WatchSnapshot.Empty.copy(kids = listOf("FromSix", "FromTwelve"), kidsFromSix = listOf("FromSix")))
            watch.profiles.value = listOf(Profile("k", "Mia", kids = true, kidsAge = 6))
            watch.chosenProfileId.value = "k"
            val vm = catalogViewModel(repository, watch)
            vm.state.test {
                awaitItem()
                val atSix = awaitItem() as CatalogUiState.Ready
                assertEquals(setOf("Family", "FromSix"), atSix.shelves.flatMap { it.entries }.filterIsInstance<Entry.Film>().map { it.set.setId }.toSet())
                val reads = repository.reads

                watch.profiles.value = listOf(Profile("k", "Mia", kids = true, kidsAge = 12))
                val atTwelve = awaitItem() as CatalogUiState.Ready
                assertEquals(setOf("Family", "Teen", "FromSix", "FromTwelve"), atTwelve.shelves.flatMap { it.entries }.filterIsInstance<Entry.Film>().map { it.set.setId }.toSet())
                assertEquals(reads, repository.reads)
            }
        }

    @Test
    fun anEmptyShelfNamesTheKidsOwnLimit() {
        assertEquals("Nothing rated FSK 6 or under yet.", CatalogUiState.KidsEmpty(6).message)
    }
```

`TvCatalogScreenStateTest.kt:243` → `CatalogUiState.KidsEmpty(6) to "Nothing rated FSK 6 or under yet.",`.

- [ ] **Step 2: run, expect failure** — `./gradlew :feature:catalog:testDebugUnitTest` → `No value passed for parameter 'limit'`, `Type mismatch: Map<String, Int> but Set<String>`, `KidsEmpty has no constructor`.

- [ ] **Step 3: implement.** `AgeRating.kt` — delete `KIDS_AGE_LIMIT` (`:13`); KDoc, verdict and filter:

```kotlin
/**
 * Age ratings, and what they decide for a kids profile — a port of the web
 * player's `age-rating.js`, whose rules the household chose. For a kid whose
 * own limit is `N` (6 or 12, see [Profile.kidsLimit]):
 *
 *  - Rated at or below `N`: shown by itself. Nobody has to mark it, and a
 *    mark cannot take it off — the rating decides.
 *  - Rated above `N`: never shown. A hand mark does not bring it back.
 *  - Unrated: only a hand mark made from an age at or below `N`.
 */

/** The limits a kids profile can have — FSK 6 or FSK 12, chosen by its parent. */
val KIDS_LIMITS = listOf(6, 12)

/** Which of the three rules applies to a title, for a kid with a given limit. */
enum class KidsVerdict { SAFE, UNSAFE, UNRATED }
```
```kotlin
fun kidsVerdictOf(
    fsk: String?,
    limit: Int,
): KidsVerdict {
    val age = ageOf(fsk) ?: return KidsVerdict.UNRATED
    return if (age <= limit) KidsVerdict.SAFE else KidsVerdict.UNSAFE
}

fun MediaSet.ageLabel(): String? = ageLabelOf(fsk)

fun MediaSet.kidsVerdict(limit: Int): KidsVerdict = kidsVerdictOf(fsk, limit)

private val AGE = Regex("""\d{1,2}""")

/**
 * What a kid with [limit] sees — the web player's `forKidsProfile(sets,
 * marks, limit)`: rated at or under the limit, or unrated and marked from an
 * age at or under it. [marks] maps a set id to the age its mark was made from.
 */
fun forKidsProfile(
    sets: List<MediaSet>,
    marks: Map<String, Int>,
    limit: Int,
): List<MediaSet> =
    sets.filter {
        when (it.kidsVerdict(limit)) {
            KidsVerdict.SAFE -> true
            KidsVerdict.UNSAFE -> false
            KidsVerdict.UNRATED -> marks[it.setId]?.let { from -> from <= limit } == true
        }
    }
```

`CatalogUiState.kt:34-35`:

```kotlin
    /** A kids profile over a library with nothing its own [limit] allows yet. */
    data class KidsEmpty(
        val limit: Int,
    ) : CatalogUiState {
        val message: String get() = "Nothing rated FSK $limit or under yet."
    }
```

`CatalogViewModel.kt:54-68` and `:152-163`:

```kotlin
        /** What a kids profile is shown by: its hand marks by age, and its own limit. */
        private data class KidsView(
            val marks: Map<String, Int>,
            val limit: Int,
        )

        /**
         * The chosen kid's [KidsView], `null` for a grown-up. Distinct, so
         * progress updates — which also move `snapshot` — do not regroup the
         * shelves; a limit changed in Manage or by sync does.
         */
        private val kidsFilter: Flow<KidsView?> =
            combine(watchState.profiles, watchState.chosenProfileId, watchState.snapshot) { _, _, _ -> currentKids() }
                .distinctUntilChanged()

        /** [kidsFilter] as a synchronous read, for a value computed outside collection. */
        private fun currentKids(): KidsView? {
            val chosen = watchState.chosenProfileId.value
            val kid = watchState.profiles.value.firstOrNull { it.id == chosen }?.takeIf { it.kids } ?: return null
            return KidsView(watchState.snapshot.value.kidsMarks, kid.kidsLimit)
        }
```
```kotlin
        private fun project(
            shown: CatalogUiState,
            kids: KidsView?,
        ): CatalogUiState =
            when {
                shown is CatalogUiState.Ready && kids != null -> {
                    val shelves = shelvesOf(forKidsProfile(lastSets, kids.marks, kids.limit))
                    if (shelves.isEmpty()) CatalogUiState.KidsEmpty(kids.limit) else shown.copy(shelves = shelves)
                }
                else -> shown
            }
```

UI: `CatalogScreen.kt:90` → `is CatalogUiState.KidsEmpty -> CenteredMessage(state.message)`; `TvCatalogBody.kt:49` → `state is CatalogUiState.KidsEmpty -> TvCenteredMessage(state.message)`. Player (Task 6 explains the 12): `PlayerMarksController.kt:59` → `kidsVerdict = kidsVerdictOf(fsk, KIDS_LIMITS.max()),`, `:91` → `if (kidsVerdictOf(openFsk.value, KIDS_LIMITS.max()) != KidsVerdict.UNRATED) return`.

- [ ] **Step 4: run, expect pass** — `./gradlew :feature:catalog:testDebugUnitTest :feature:player:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest`.
- [ ] **Step 5: commit** — bump, then `git add android/core/model android/feature android/ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt android/ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogBody.kt android/ui-tv/src/test/kotlin/ui/tv/catalog/TvCatalogScreenStateTest.kt <manifests> docs/project-changelog.md && git commit -m "feat(android): a kid sees up to its own limit, FSK 6 or 12; release <next>"`.

### Task 3: One PIN prompt and the web's words

**Files:** Create `feature/catalog/src/main/kotlin/profile/{ProfileWords,PinAsk}.kt`, tests `profile/{ProfileWordsTest,PinAskTest}.kt`.

- [ ] **Step 1: failing tests** — `feature/catalog/src/test/kotlin/profile/PinAskTest.kt`

```kotlin
package catalog.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.ProfileOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinAskTest {
    @Test
    fun aPinIsSentAndTheDialogClosesOnDone() =
        runTest {
            val ask = PinAsk(this)
            var sent: String? = null
            var finished: String? = null
            ask.ask(PinPrompt("Ada’s PIN", newPin = false), send = { sent = it; ProfileOutcome.Done }, done = { finished = it })
            ask.enter("1234")
            runCurrent()
            assertEquals("1234", sent)
            assertEquals("1234", finished)
            assertNull(ask.prompt.value)
        }

    @Test
    fun aRefusalStaysOpenAndSaysWhy() =
        runTest {
            val ask = PinAsk(this)
            ask.ask(PinPrompt("Ada’s PIN", newPin = false), send = { ProfileOutcome.WrongPin }, done = {})
            ask.enter("0000")
            runCurrent()
            assertEquals(PinPrompt("Ada’s PIN", newPin = false, error = "Wrong PIN."), ask.prompt.value)
        }

    @Test
    fun aNewPinIsTypedTwiceBeforeAnythingIsSent() =
        runTest {
            val ask = PinAsk(this)
            val sent = mutableListOf<String>()
            ask.ask(PinPrompt("Choose a PIN for Bo", newPin = true), send = { sent += it; ProfileOutcome.Done }, done = {})
            ask.enter("4321")
            runCurrent()
            assertEquals(emptyList(), sent)
            assertEquals("The same PIN again", ask.prompt.value?.heading)
            ask.enter("4321")
            runCurrent()
            assertEquals(listOf("4321"), sent)
        }

    @Test
    fun twoDifferentNewPinsAreRefusedAndAskedForAgain() =
        runTest {
            val ask = PinAsk(this)
            var sends = 0
            ask.ask(PinPrompt("Choose a PIN for Bo", newPin = true), send = { sends++; ProfileOutcome.Done }, done = {})
            ask.enter("1111")
            ask.enter("2222")
            runCurrent()
            assertEquals(0, sends)
            assertEquals(PinPrompt("Choose a PIN for Bo", newPin = true, error = "The two PINs are not the same."), ask.prompt.value)
        }

    @Test
    fun aCancelledAskIgnoresItsLateAnswer() =
        runTest {
            val ask = PinAsk(this)
            val answer = CompletableDeferred<ProfileOutcome>()
            var finished = false
            ask.ask(PinPrompt("Ada’s PIN", newPin = false), send = { answer.await() }, done = { finished = true })
            ask.enter("1234")
            runCurrent()
            ask.cancel()
            answer.complete(ProfileOutcome.Done)
            runCurrent()
            assertNull(ask.prompt.value)
            assertEquals(false, finished)
        }

    @Test
    fun aCoreThatCouldNotBeAskedSaysItDidNotGoThrough() =
        runTest {
            val ask = PinAsk(this)
            ask.ask(PinPrompt("Ada’s PIN", newPin = false), send = { error("private path") }, done = {})
            ask.enter("1234")
            runCurrent()
            assertEquals("That did not go through. Please try again.", ask.prompt.value?.error)
        }

    @Test
    fun whileBusyAnotherEntryIsIgnored() =
        runTest {
            val ask = PinAsk(this)
            val answer = CompletableDeferred<ProfileOutcome>()
            var sends = 0
            ask.ask(PinPrompt("Ada’s PIN", newPin = false), send = { sends++; answer.await() }, done = {})
            ask.enter("1234")
            runCurrent()
            ask.enter("5678")
            runCurrent()
            assertEquals(1, sends)
            assertTrue(ask.prompt.value!!.busy)
            answer.complete(ProfileOutcome.WrongPin)
        }
}
```

`feature/catalog/src/test/kotlin/profile/ProfileWordsTest.kt`

```kotlin
package catalog.profile

import model.Profile
import model.ProfileOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The words are the web's (`pin-prompt.js`, `profile-manage.js`), so a household reads one app, not two. */
class ProfileWordsTest {
    @Test
    fun everyRefusalIsTheWebsSentenceAndDoneIsNone() {
        assertNull(ProfileOutcome.Done.sentence())
        assertEquals("Wrong PIN.", ProfileOutcome.WrongPin.sentence())
        assertEquals("Too many wrong PINs. Try again in 42 s.", ProfileOutcome.Wait(42).sentence())
        assertEquals("Your profile cannot do that.", ProfileOutcome.NotAllowed.sentence())
        assertEquals("This profile has no PIN yet. Choose it again to set one.", ProfileOutcome.NoPin.sentence())
        assertEquals("That profile is not here any more.", ProfileOutcome.NotFound.sentence())
        assertEquals("That was not accepted. Check the name and the PIN.", ProfileOutcome.Invalid.sentence())
    }

    @Test
    fun aGrownUpIsAskedItsPinOrToChooseOne() {
        assertEquals("andre’s PIN", pinTitleFor(Profile("a", "andre", hasPin = true)))
        assertEquals("Choose a PIN for test", pinTitleFor(Profile("t", "test")))
        assertEquals("The same PIN again", PinPrompt("Choose a PIN for test", newPin = true, confirming = true).heading)
    }

    @Test
    fun aKidsTileNamesItsOwnLimitAndAGrownUpsNothing() {
        assertEquals("Kids · FSK 6", Profile("m", "Mia", kids = true, kidsAge = 6).kidsTag)
        assertEquals("Kids · FSK 12", Profile("o", "TV kids", kids = true).kidsTag)
        assertNull(Profile("a", "andre").kidsTag)
    }

    @Test
    fun removingAGrownUpSaysItsKidsGoToo() {
        assertEquals("Remove Mia and everything they have watched?", removeQuestion(Profile("m", "Mia", kids = true)))
        assertEquals("Remove Bea, their kids, and everything they have watched?", removeQuestion(Profile("b", "Bea")))
    }
}
```

- [ ] **Step 2: run, expect failure** — `./gradlew :feature:catalog:testDebugUnitTest --tests 'catalog.profile.PinAskTest' --tests 'catalog.profile.ProfileWordsTest'` → `Unresolved reference 'PinAsk'`, `'PinPrompt'`, `'sentence'`, `'pinTitleFor'`.

- [ ] **Step 3: implement** `feature/catalog/src/main/kotlin/profile/ProfileWords.kt`

```kotlin
package catalog.profile

import model.Profile
import model.ProfileOutcome

/**
 * What the PIN dialog shows, on a phone and a television alike: [title]
 * (whose PIN, or a new one), whether it is a new PIN typed twice, and why
 * the last try failed.
 */
data class PinPrompt(
    val title: String,
    val newPin: Boolean,
    val confirming: Boolean = false,
    val error: String? = null,
    val busy: Boolean = false,
) {
    /** The web asks a new PIN in two fields at once; a remote has one pad, so the second entry is its own step. */
    val heading: String get() = if (confirming) "The same PIN again" else title
}

/** A PIN is exactly four digits; a field or a pad hands one over only once it has them. */
const val PIN_LENGTH = 4

// The words below are the web player's own (`profile-picker.js`,
// `pin-prompt.js`, `profile-manage.js`), shared by the phone and the
// television so neither drifts from it. PICKER_NOTE says "Android" where the
// web says "a browser": what gets past a PIN differs by surface.
const val PICKER_NOTE =
    "Profiles keep your places and lists apart. A grown-up’s PIN keeps children out of it; " +
        "it is not a login, and someone who knows their way around Android can get past it."
const val FIRST_PROFILE = "Create the first profile — it runs this household"
const val WHO_RUNS_THIS = "Who runs this household?"
const val MANAGE_PROFILES = "Manage profiles"
const val LOOK_AGAIN = "Look again"
const val WHO_ARE_YOU = "Who are you?"
const val GROWN_UPS = "Grown-ups"
const val KIDS_SECTION = "Kids"
const val YOUR_PIN = "Your PIN"
const val ADD_A_KID = "Add a kid"
const val ADD_A_GROWN_UP = "Add a grown-up"
const val CHANGE_YOUR_PIN = "Change your PIN"
const val RESET_PIN = "Reset PIN"
const val REMOVE = "Remove"
const val DONE = "Done"

fun managingAs(name: String): String = "As $name"

/** What the PIN dialog asks a grown-up for: its PIN, or — from before PINs — a first one. */
fun pinTitleFor(profile: Profile): String = if (profile.hasPin) "${profile.name}’s PIN" else "Choose a PIN for ${profile.name}"

internal fun newPinTitleFor(name: String): String = "A PIN for $name"

internal const val YOUR_NEW_PIN = "Your new PIN"

internal fun resetPinTitleFor(name: String): String = "A new PIN for $name"

internal const val PINS_DIFFER = "The two PINs are not the same."
internal const val DID_NOT_GO_THROUGH = "That did not go through. Please try again."

/** Why a request did not take, in the web's words; null once it did. */
internal fun ProfileOutcome.sentence(): String? =
    when (this) {
        ProfileOutcome.Done -> null
        ProfileOutcome.Invalid -> "That was not accepted. Check the name and the PIN."
        ProfileOutcome.NotFound -> "That profile is not here any more."
        is ProfileOutcome.Wait -> "Too many wrong PINs. Try again in $seconds s."
        ProfileOutcome.NoPin -> "This profile has no PIN yet. Choose it again to set one."
        ProfileOutcome.WrongPin -> "Wrong PIN."
        ProfileOutcome.NotAllowed -> "Your profile cannot do that."
    }

/** The line under a kid's name on a tile or a row: its own limit, never a fixed one. */
val Profile.kidsTag: String? get() = if (kids) "Kids · FSK $kidsLimit" else null

/** Asked before a removal; a grown-up's kids go with it, so it says so. */
fun removeQuestion(profile: Profile): String =
    if (profile.kids) {
        "Remove ${profile.name} and everything they have watched?"
    } else {
        "Remove ${profile.name}, their kids, and everything they have watched?"
    }
```

`feature/catalog/src/main/kotlin/profile/PinAsk.kt`

```kotlin
package catalog.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.ProfileOutcome

/**
 * The PIN dialog's half of a view model, shared by the picker and Manage so
 * both ask the same way: what is asked ([prompt]), a new PIN's first entry
 * held until the second matches it, and what to send once there are four
 * digits. A new PIN is typed twice because nobody can reset the admin's own —
 * one slip there would lock the household's only admin out for good.
 *
 * Only the latest [ask] may answer: a request still in flight when the
 * dialog was cancelled or replaced finishes without touching the screen.
 */
internal class PinAsk(
    private val scope: CoroutineScope,
) {
    private val shown = MutableStateFlow<PinPrompt?>(null)
    val prompt: StateFlow<PinPrompt?> = shown.asStateFlow()

    private var first: String? = null
    private var send: suspend (String) -> ProfileOutcome = { ProfileOutcome.Invalid }
    private var done: (String) -> Unit = {}
    private var asking = 0L

    fun ask(
        prompt: PinPrompt,
        send: suspend (pin: String) -> ProfileOutcome,
        done: (pin: String) -> Unit,
    ) {
        asking++
        first = null
        this.send = send
        this.done = done
        shown.value = prompt
    }

    /** Four digits typed: a new PIN's first entry is held, a second that differs refused, anything else sent. */
    fun enter(pin: String) {
        val current = shown.value ?: return
        if (current.busy) return
        if (current.newPin && !current.confirming) {
            first = pin
            shown.value = current.copy(confirming = true, error = null)
            return
        }
        if (current.newPin && pin != first) {
            first = null
            shown.value = current.copy(confirming = false, error = PINS_DIFFER)
            return
        }
        val started = asking
        val sending = send
        val finish = done
        shown.value = current.copy(busy = true, error = null)
        scope.launch {
            val outcome = attempt { sending(pin) }
            if (started != asking) return@launch
            if (outcome == ProfileOutcome.Done) {
                cancel()
                finish(pin)
            } else {
                first = null
                shown.value = current.copy(confirming = false, busy = false, error = outcome?.sentence() ?: DID_NOT_GO_THROUGH)
            }
        }
    }

    fun cancel() {
        asking++
        first = null
        shown.value = null
    }
}

/** [block]'s answer, or null when the core could not be asked at all — which says nothing about the PIN. */
internal suspend fun attempt(block: suspend () -> ProfileOutcome): ProfileOutcome? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        null
    }
```

- [ ] **Step 4: run, expect pass** — command from Step 2.
- [ ] **Step 5: commit** — bump, then `git add android/feature/catalog <manifests> docs/project-changelog.md && git commit -m "feat(android): one PIN prompt in the web's words, typed twice when new; release <next>"`.

### Task 4: The picker — first profile, who runs the household, a grown-up's PIN

**Files:** Create `feature/catalog/src/main/kotlin/profile/ProfileUiState.kt`, test `profile/ProfilePinTest.kt`; Modify `profile/ProfileViewModel.kt`.

- [ ] **Step 1: failing tests** — `feature/catalog/src/test/kotlin/profile/ProfilePinTest.kt`

```kotlin
package catalog.profile

import catalog.MainDispatcherRule
import data.DefaultWatchStateRepository
import data.WatchSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.CatalogCoreProvider
import testing.FakeCore
import uniffi.mediagram_core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Counts [soon]: a change made here should reach the other devices without waiting five minutes. */
internal class RecordingSync : WatchSync {
    var soonCalls = 0
        private set

    override fun onForeground() = Unit

    override fun onBackground() = Unit

    override fun soon() {
        soonCalls++
    }

    override suspend fun awaitFirstRound() = Unit
}

/** The picker over [FakeCore]'s own rules — the answers the tablet's core gives, not a hand fake's. */
class ProfilePinTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val sync = RecordingSync()

    private fun picker(profiles: List<Profile> = household()): Pair<FakeCore, ProfileViewModel> {
        val core =
            FakeCore().apply {
                this.profiles = profiles
                if (profiles.any { it.id == "a" }) roles.pins["a"] = "1234"
            }
        return core to ProfileViewModel(DefaultWatchStateRepository(CatalogCoreProvider(core), Dispatchers.Unconfined), sync)
    }

    private fun household(admin: Boolean = true) =
        listOf(Profile("a", "andre", admin = admin), Profile("t", "test"), Profile("k", "TV kids", kids = true))

    /** [ProfileViewModel.state] is shared `WhileSubscribed`: something has to be watching it. */
    private fun TestScope.watch(vm: ProfileViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

    private fun ProfileViewModel.picking() = state.value as ProfileUiState.Picking

    @Test
    fun aKidOpensWithoutAPin() =
        runTest {
            val (_, vm) = picker()
            watch(vm)
            vm.pick("k")
            assertEquals("k", (vm.state.value as ProfileUiState.Chosen).profile.id)
            assertNull(vm.pin.value)
        }

    @Test
    fun aGrownUpOpensOnlyOnItsOwnPin() =
        runTest {
            val (_, vm) = picker()
            watch(vm)
            vm.pick("a")
            assertEquals(PinPrompt("andre’s PIN", newPin = false), vm.pin.value)
            vm.enterPin("0000")
            assertEquals("Wrong PIN.", vm.pin.value?.error)
            vm.enterPin("1234")
            assertEquals("a", (vm.state.value as ProfileUiState.Chosen).profile.id)
            assertNull(vm.pin.value)
        }

    @Test
    fun aGrownUpFromBeforePinsChoosesOneTwiceThenOpens() =
        runTest {
            val (core, vm) = picker()
            watch(vm)
            vm.pick("t")
            assertEquals(PinPrompt("Choose a PIN for test", newPin = true), vm.pin.value)
            vm.enterPin("4321")
            vm.enterPin("4321")
            assertEquals("t", (vm.state.value as ProfileUiState.Chosen).profile.id)
            assertEquals("4321", core.roles.pins["t"])
            assertEquals(1, sync.soonCalls)
        }

    @Test
    fun fiveWrongPinsMakeEvenTheRightOneWaitAMinute() =
        runTest {
            val (core, vm) = picker()
            watch(vm)
            vm.pick("a")
            repeat(5) { vm.enterPin("0000") }
            vm.enterPin("1234")
            assertEquals("Too many wrong PINs. Try again in 60 s.", vm.pin.value?.error)
            core.roles.nowMs += 60_000
            vm.enterPin("1234")
            assertEquals("a", (vm.state.value as ProfileUiState.Chosen).profile.id)
        }

    @Test
    fun aHouseholdWithNoAdminIsAskedAndTheClaimSticks() =
        runTest {
            val (_, vm) = picker(household(admin = false))
            watch(vm)
            assertEquals(true to false, vm.picking().needsAdmin to vm.picking().needsFirstProfile)
            assertEquals(listOf("a", "t"), vm.picking().grownUps.map { it.id })
            vm.claim("t")
            vm.enterPin("4321")
            vm.enterPin("4321")
            assertEquals(false, vm.picking().needsAdmin)
            assertEquals(listOf("t"), vm.picking().profiles.filter { it.admin }.map { it.id })
            assertEquals(1, sync.soonCalls)
        }

    @Test
    fun aDeviceWithNoGrownUpCreatesTheFirstWhoRunsTheHousehold() =
        runTest {
            val (_, vm) = picker(listOf(Profile("k", "TV kids", kids = true)))
            watch(vm)
            assertEquals(true to false, vm.picking().needsFirstProfile to vm.picking().needsAdmin)
            vm.createFirst("  Ann ")
            assertEquals(PinPrompt("A PIN for Ann", newPin = true), vm.pin.value)
            vm.enterPin("2468")
            vm.enterPin("2468")
            val ann = vm.picking().profiles.single { it.name == "Ann" }
            assertEquals(true to true, ann.admin to ann.hasPin)
            assertEquals(false, vm.picking().needsFirstProfile)
            assertEquals(1, sync.soonCalls)
        }

    @Test
    fun aKidCannotClaimAndCancellingChangesNothing() =
        runTest {
            val (_, vm) = picker(household(admin = false))
            watch(vm)
            vm.claim("k")
            assertNull(vm.pin.value)
            vm.pick("a")
            vm.cancelPin()
            assertNull(vm.pin.value)
            assertTrue(vm.state.value is ProfileUiState.Picking)
        }
}
```

- [ ] **Step 2: run, expect failure** — `./gradlew :feature:catalog:testDebugUnitTest --tests 'catalog.profile.ProfilePinTest'` → `Unresolved reference 'pick'`, `'pin'`, `'createFirst'`, `'needsAdmin'`.

- [ ] **Step 3: implement.** Move `ProfileUiState` (`ProfileViewModel.kt:21-44`) to `feature/catalog/src/main/kotlin/profile/ProfileUiState.kt`; `Picking` gains the contract §12 states:

```kotlin
    data class Picking(
        val profiles: List<Profile>,
        val canStay: Boolean,
        val error: String? = null,
    ) : ProfileUiState {
        /** No grown-up here at all — a fresh install, or one that knows only kids: the first profile is made, and runs the household. */
        val needsFirstProfile: Boolean get() = profiles.none { !it.kids }

        /** Grown-ups, none of them the admin: "Who runs this household?" until one claims. Earliest claim wins across devices. */
        val needsAdmin: Boolean get() = !needsFirstProfile && profiles.none { it.admin }

        /** Who may answer that question, or open Manage. */
        val grownUps: List<Profile> get() = profiles.filterNot { it.kids }
    }
```

Add to `ProfileViewModel` (imports `model.ProfileRequest`); `reopen()` and `stay()` call `ask.cancel()` first (a prompt left open must not answer for a picker shown again); `choose`'s KDoc: "Enters without a PIN — a kid, or a grown-up [pick] has unlocked. Tiles call [pick]."

```kotlin
        private val ask = PinAsk(viewModelScope)

        /** The PIN the picker is asking for, or null. */
        val pin: StateFlow<PinPrompt?> = ask.prompt

        /**
         * A tile pressed. A kid opens at once — leaving a grown-up for a kid is
         * free. A grown-up asks its PIN, or, from before PINs existed, a first
         * one typed twice. Only the way in is locked: a remembered choice at
         * start-up asks nothing, and the core does not check [choose] — this
         * stops a child's tap, not someone with adb.
         */
        fun pick(id: String) {
            val profile = repository.profiles.value.find { it.id == id } ?: return
            if (profile.kids) return choose(id)
            val first = !profile.hasPin
            ask.ask(
                PinPrompt(pinTitleFor(profile), newPin = first),
                send = { pin ->
                    repository.manage(if (first) ProfileRequest.SetPin(id, "", id, pin) else ProfileRequest.Unlock(id, pin))
                },
                done = {
                    if (first) sync.soon()
                    choose(id)
                },
            )
        }

        /** "Who runs this household?" answered: that grown-up's PIN — or a first one — makes it the admin. */
        fun claim(id: String) {
            val profile = repository.profiles.value.find { it.id == id && !it.kids } ?: return
            ask.ask(
                PinPrompt(pinTitleFor(profile), newPin = !profile.hasPin),
                send = { pin -> repository.manage(ProfileRequest.ClaimAdmin(id, pin)) },
                done = { sync.soon() },
            )
        }

        /**
         * The first grown-up on a device with none; it runs the household. Its
         * tile then opens like anyone's. A device that does this before its
         * first sync, in a household that already has an admin, loses the role
         * to the older claim when the two meet — the merge's rule, not a bug.
         */
        fun createFirst(name: String) {
            val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
            ask.ask(
                PinPrompt(newPinTitleFor(clean), newPin = true),
                send = { pin -> repository.manage(ProfileRequest.CreateFirstAdmin(clean, pin)) },
                done = { sync.soon() },
            )
        }

        fun enterPin(pin: String) = ask.enter(pin)

        fun cancelPin() = ask.cancel()
```

- [ ] **Step 4: run, expect pass** — `./gradlew :feature:catalog:testDebugUnitTest` (all of it: `ProfileViewModelTest`, `ProfileOwnershipTest` unchanged).
- [ ] **Step 5: commit** — bump, then `git add android/feature/catalog <manifests> docs/project-changelog.md && git commit -m "feat(android): the picker makes the first profile, names the admin and asks a grown-up's PIN; release <next>"`.

### Task 5: Manage profiles

**Files:** Create `feature/catalog/src/main/kotlin/profile/ManageProfilesViewModel.kt`, test `profile/ManageProfilesViewModelTest.kt`.

- [ ] **Step 1: failing tests** — `feature/catalog/src/test/kotlin/profile/ManageProfilesViewModelTest.kt`

```kotlin
package catalog.profile

import catalog.MainDispatcherRule
import data.DefaultWatchStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.CatalogCoreProvider
import testing.FakeCore
import uniffi.mediagram_core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Manage over [FakeCore]'s rules: andre (admin) with Mia; Bea with Tom; TV kids from before parents. */
class ManageProfilesViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val sync = RecordingSync()
    private val core =
        FakeCore().apply {
            profiles = listOf(
                Profile("a", "andre", admin = true),
                Profile("b", "Bea"),
                Profile("m", "Mia", kids = true, kidsAge = 6u, parentId = "a"),
                Profile("t", "Tom", kids = true, kidsAge = 12u, parentId = "b"),
                Profile("o", "TV kids", kids = true),
            )
            roles.pins["a"] = "1234"
            roles.pins["b"] = "5678"
        }
    private val repository = DefaultWatchStateRepository(CatalogCoreProvider(core), Dispatchers.Unconfined)

    // Lazy: the view model reaches Dispatchers.Main as it is built, and the
    // rule installs the test one only once the test itself starts.
    private val vm by lazy { ManageProfilesViewModel(repository, sync) }

    private suspend fun managingAs(id: String, pin: String): ManageUiState.Managing {
        repository.reload()
        vm.open()
        vm.actAs(id)
        vm.enterPin(pin)
        return assertIs<ManageUiState.Managing>(vm.state.value)
    }

    @Test
    fun onlyGrownUpsAreAskedWhoTheyAre() =
        runTest {
            repository.reload()
            vm.open()
            assertEquals(listOf("a", "b"), assertIs<ManageUiState.ChoosingActor>(vm.state.value).grownUps.map { it.id })
        }

    @Test
    fun aWrongPinDoesNotOpenThePanel() =
        runTest {
            repository.reload()
            vm.open()
            vm.actAs("b")
            vm.enterPin("0000")
            assertIs<ManageUiState.ChoosingActor>(vm.state.value)
            assertEquals("Wrong PIN.", vm.pin.value?.error)
        }

    @Test
    fun theAdminSeesEveryOtherGrownUpAndOnlyItsOwnKids() =
        runTest {
            val panel = managingAs("a", "1234")
            assertEquals(listOf("b"), panel.grownUps.map { it.id })
            assertEquals(listOf("m", "o"), panel.kids.map { it.id })
            assertEquals(true, panel.canAddGrownUp)
        }

    @Test
    fun aParentSeesItsOwnKidsAndNoGrownUps() =
        runTest {
            val panel = managingAs("b", "5678")
            assertEquals(listOf("t"), panel.kids.map { it.id })
            assertEquals(emptyList(), panel.grownUps)
            assertEquals(false, panel.canAddGrownUp)
        }

    @Test
    fun aKidIsAddedUnderTheParentAtTheChosenLimitAndChanged() =
        runTest {
            managingAs("b", "5678")
            vm.addKid("Lina", 6)
            val lina = assertIs<ManageUiState.Managing>(vm.state.value).kids.single { it.name == "Lina" }
            assertEquals(6, lina.kidsAge)
            vm.setKidsAge(lina.id, 12)
            assertEquals(12, assertIs<ManageUiState.Managing>(vm.state.value).kids.single { it.id == lina.id }.kidsAge)
            assertEquals(2, sync.soonCalls)
        }

    @Test
    fun removingAGrownUpTakesItsKidsWithIt() =
        runTest {
            managingAs("a", "1234")
            vm.remove("b")
            assertEquals(listOf("a", "m", "o"), repository.profiles.value.map { it.id })
        }

    @Test
    fun theAdminCannotBeRemovedEvenByItself() =
        runTest {
            managingAs("a", "1234")
            vm.remove("a")
            assertEquals("Your profile cannot do that.", assertIs<ManageUiState.Managing>(vm.state.value).notice)
        }

    @Test
    fun aNewGrownUpsFirstPinIsTypedTwice() =
        runTest {
            managingAs("a", "1234")
            vm.addGrownUp("Cleo")
            assertEquals(PinPrompt("A PIN for Cleo", newPin = true), vm.pin.value)
            vm.enterPin("2468")
            vm.enterPin("2468")
            assertEquals("2468", core.roles.pins[repository.profiles.value.single { it.name == "Cleo" }.id])
        }

    @Test
    fun changingYourOwnPinKeepsThePanelWorking() =
        runTest {
            managingAs("b", "5678")
            vm.changePin("b")
            assertEquals(PinPrompt("Your new PIN", newPin = true), vm.pin.value)
            vm.enterPin("1357")
            vm.enterPin("1357")
            vm.setKidsAge("t", 6)
            assertEquals(null, assertIs<ManageUiState.Managing>(vm.state.value).notice)
            assertEquals(6, repository.profiles.value.single { it.id == "t" }.kidsAge)
        }

    @Test
    fun theAdminResetsAnotherGrownUpsPin() =
        runTest {
            managingAs("a", "1234")
            vm.changePin("b")
            assertEquals(PinPrompt("A new PIN for Bea", newPin = true), vm.pin.value)
            vm.enterPin("8642")
            vm.enterPin("8642")
            assertEquals("8642", core.roles.pins["b"])
        }

    @Test
    fun aGrownUpWithNoPinSetsOneBeforeManaging() =
        runTest {
            core.roles.pins.remove("b")
            repository.reload()
            vm.open()
            vm.actAs("b")
            assertEquals(PinPrompt("Choose a PIN for Bea", newPin = true), vm.pin.value)
            vm.enterPin("9999")
            vm.enterPin("9999")
            assertIs<ManageUiState.Managing>(vm.state.value)
        }

    @Test
    fun closingForgetsThePin() =
        runTest {
            managingAs("b", "5678")
            vm.close()
            assertIs<ManageUiState.Closed>(vm.state.value)
            vm.setKidsAge("t", 6)
            assertEquals(12, repository.profiles.value.single { it.id == "t" }.kidsAge)
        }
}
```

- [ ] **Step 2: run, expect failure** — `./gradlew :feature:catalog:testDebugUnitTest --tests 'catalog.profile.ManageProfilesViewModelTest'` → `Unresolved reference 'ManageProfilesViewModel'`.

- [ ] **Step 3: implement** `feature/catalog/src/main/kotlin/profile/ManageProfilesViewModel.kt`

```kotlin
package catalog.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import model.Profile
import model.ProfileOutcome
import model.ProfileRequest
import model.RoleAction
import model.allowed
import javax.inject.Inject

/** What Manage profiles shows: nothing, "who are you?", or what that grown-up's role allows. */
sealed interface ManageUiState {
    data object Closed : ManageUiState

    /** Only a grown-up manages anything. */
    data class ChoosingActor(
        val grownUps: List<Profile>,
    ) : ManageUiState

    /**
     * [actor]'s PIN was right. For the admin, every other grown-up and a way
     * to add one; for everyone, its own [kids]. [notice] says why the last
     * change did not take, until the next one does.
     */
    data class Managing(
        val actor: Profile,
        val grownUps: List<Profile>,
        val kids: List<Profile>,
        val canAddGrownUp: Boolean,
        val notice: String? = null,
    ) : ManageUiState
}

/**
 * Manage profiles — the web's `profile-manage.js`: choose who you are, prove
 * it with your PIN, then act within your role. The PIN is held here only
 * while the panel is open and sent with every change, since the core checks
 * PIN and role each time; [close] drops it. Nothing opens this but [open] —
 * entering a profile never hands a child the controls.
 */
@HiltViewModel
class ManageProfilesViewModel
    @Inject
    constructor(
        private val repository: WatchStateRepository,
        private val sync: WatchSync,
    ) : ViewModel() {
        private sealed interface Step {
            data object Closed : Step

            data object ChoosingActor : Step

            data class Managing(
                val actorId: String,
                val notice: String? = null,
            ) : Step
        }

        private val step = MutableStateFlow<Step>(Step.Closed)
        private var heldPin: String? = null
        private val ask = PinAsk(viewModelScope)

        /** The PIN Manage is asking for: the actor's own, or a new one typed twice. */
        val pin: StateFlow<PinPrompt?> = ask.prompt

        val state: StateFlow<ManageUiState> =
            combine(step, repository.profiles) { current, profiles -> current.shown(profiles) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, ManageUiState.Closed)

        fun open() {
            close()
            step.value = Step.ChoosingActor
        }

        /** The actor's PIN, or — from before PINs existed — a first one typed twice. */
        fun actAs(id: String) {
            val actor = repository.profiles.value.find { it.id == id && !it.kids } ?: return
            val first = !actor.hasPin
            ask.ask(
                PinPrompt(pinTitleFor(actor), newPin = first),
                send = { pin ->
                    repository.manage(if (first) ProfileRequest.SetPin(id, "", id, pin) else ProfileRequest.Unlock(id, pin))
                },
                done = { pin ->
                    if (first) sync.soon()
                    heldPin = pin
                    step.value = Step.Managing(id)
                },
            )
        }

        fun addKid(name: String, age: Int) = change { actor, pin -> ProfileRequest.CreateKid(actor, pin, name, age) }

        fun setKidsAge(kidId: String, age: Int) = change { actor, pin -> ProfileRequest.SetKidsAge(actor, pin, kidId, age) }

        fun remove(id: String) = change { actor, pin -> ProfileRequest.Remove(actor, pin, id) }

        /** Admin only; the new grown-up's first PIN is typed twice before anything is sent. */
        fun addGrownUp(name: String) =
            newPin(newPinTitleFor(name), own = false) { actor, pin, newPin -> ProfileRequest.CreateGrownUp(actor, pin, name, newPin) }

        /** The actor's own PIN, or — for the admin — another grown-up's. */
        fun changePin(id: String) {
            val managing = step.value as? Step.Managing ?: return
            val target = repository.profiles.value.find { it.id == id } ?: return
            val own = id == managing.actorId
            newPin(if (own) YOUR_NEW_PIN else resetPinTitleFor(target.name), own) { actor, pin, newPin ->
                ProfileRequest.SetPin(actor, pin, id, newPin)
            }
        }

        fun enterPin(pin: String) = ask.enter(pin)

        fun cancelPin() = ask.cancel()

        /** Leaves Manage and forgets the PIN it held. */
        fun close() {
            ask.cancel()
            heldPin = null
            step.value = Step.Closed
        }

        override fun onCleared() {
            heldPin = null
        }

        private fun change(request: (actorId: String, pin: String) -> ProfileRequest) {
            val managing = step.value as? Step.Managing ?: return
            val pin = heldPin ?: return
            viewModelScope.launch { settled(managing.actorId, attempt { repository.manage(request(managing.actorId, pin)) }) }
        }

        private fun newPin(
            title: String,
            own: Boolean,
            request: (actorId: String, pin: String, newPin: String) -> ProfileRequest,
        ) {
            val managing = step.value as? Step.Managing ?: return
            val pin = heldPin ?: return
            ask.ask(
                PinPrompt(title, newPin = true),
                send = { newPin -> repository.manage(request(managing.actorId, pin, newPin)) },
                done = { newPin ->
                    // A changed own PIN is the one this panel sends from now on, or the next change is refused.
                    if (own) heldPin = newPin
                    settled(managing.actorId, ProfileOutcome.Done)
                },
            )
        }

        /** Why a change did not take, or no reason once one did — and a change should reach the other devices soon. */
        private fun settled(actorId: String, outcome: ProfileOutcome?) {
            if ((step.value as? Step.Managing)?.actorId != actorId) return
            if (outcome == ProfileOutcome.Done) sync.soon()
            step.value = Step.Managing(actorId, if (outcome == ProfileOutcome.Done) null else outcome?.sentence() ?: DID_NOT_GO_THROUGH)
        }

        private fun Step.shown(profiles: List<Profile>): ManageUiState =
            when (this) {
                Step.Closed -> ManageUiState.Closed
                Step.ChoosingActor -> ManageUiState.ChoosingActor(profiles.filterNot { it.kids })
                is Step.Managing -> {
                    // The actor itself gone — an account reset — leaves nothing to manage as.
                    val actor = profiles.find { it.id == actorId } ?: return ManageUiState.Closed
                    ManageUiState.Managing(
                        actor = actor,
                        grownUps = profiles.filter { !it.kids && it.id != actorId && profiles.allowed(actorId, RoleAction.SET_PIN, it.id) },
                        kids = profiles.filter { it.kids && profiles.allowed(actorId, RoleAction.SET_KIDS_AGE, it.id) },
                        canAddGrownUp = profiles.allowed(actorId, RoleAction.CREATE_GROWN_UP, null),
                        notice = notice,
                    )
                }
            }
    }
```

- [ ] **Step 4: run, expect pass** — command from Step 2, then `./gradlew :feature:catalog:testDebugUnitTest`.
- [ ] **Step 5: commit** — bump, then `git add android/feature/catalog <manifests> docs/project-changelog.md && git commit -m "feat(android): Manage profiles by role — grown-ups and their PINs, kids and their limits; release <next>"`.

### Task 6: The player's Kids mark carries an age

**Files:** Modify `feature/player/src/main/kotlin/{PlayerMarksController,PlayerMarksState,KidsLabel,PlayerViewModelDelegates}.kt`; tests `PlayerMarksTest.kt`, `PlayerKidsLabelTest.kt`, `ui-tv/src/test/.../player/TvPlayerMarksTest.kt:50,130`.

- [ ] **Step 1: failing tests.** `PlayerKidsLabelTest.kt:9-18` → `marks(verdict, ageLabel = null, kidsMark: Int? = null)` building `PlayerMarksState(watchlisted = false, kidsMark = kidsMark, …)`; `anUnratedTitleSaysWhetherItIsMarked` (`:29-32`) becomes:

```kotlin
    /** The web shows an unrated title's control as its select's current option. */
    @Test
    fun anUnratedTitleShowsItsChoice() {
        assertEquals("Not for kids", kidsLabel(marks(KidsVerdict.UNRATED)))
        assertEquals("From 6", kidsLabel(marks(KidsVerdict.UNRATED, kidsMark = 6)))
        assertEquals("From 12", kidsLabel(marks(KidsVerdict.UNRATED, kidsMark = 12)))
        assertEquals(listOf("Not for kids", "From 6", "From 12"), KIDS_CHOICES.map { it.label })
    }
```

`PlayerMarksTest.kt` — `?.kids` → `?.kidsMark` (`null` / `12`) in `toggleKidsMarksAndUnmarksTheOpenTitle` (`:90-108`) and `aTitleRatedForKidsIsForKidsWithoutAMark` (`:137-150`); in `aKidsProfileCannotMarkTitlesForKids` (`:153-175`) add `vm.setKidsMark(6)` beside the toggle; add:

```kotlin
    @Test
    fun theKidsChoiceMarksFromSixFromTwelveOrNotAtAll() =
        runTest {
            installMainDispatcher()
            val repository = FakeWatchStateRepository()
            val vm = viewModel(repository)
            vm.marks.test {
                assertNull(awaitItem())
                vm.open("s1")
                assertNull(awaitItem()?.kidsMark)

                vm.setKidsMark(6)
                advanceUntilIdle()
                assertEquals(6, expectMostRecentItem()?.kidsMark)

                vm.setKidsMark(12)
                advanceUntilIdle()
                assertEquals(12, expectMostRecentItem()?.kidsMark)

                vm.setKidsMark(null)
                advanceUntilIdle()
                assertNull(expectMostRecentItem()?.kidsMark)
                assertEquals(listOf("setKids s1 6", "setKids s1 12", "setKids s1 null"), repository.calls.filter { it.startsWith("setKids") })
            }
        }

    /** A grown-up reads a rating against the widest limit a kid can have: FSK 12 is for some kids. */
    @Test
    fun aTwelveIsForKidsAndCannotBeChosenFor() =
        runTest {
            installMainDispatcher()
            val repository = FakeWatchStateRepository()
            val vm = viewModel(repository)
            vm.marks.test {
                assertNull(awaitItem())
                vm.open("s1", fsk = "12")
                assertEquals(KidsVerdict.SAFE, awaitItem()?.kidsVerdict)
                vm.setKidsMark(6)
                advanceUntilIdle()
                assertTrue(repository.calls.none { it.startsWith("setKids") })
            }
        }
```

`TvPlayerMarksTest.kt:50` → `compose.onNodeWithText("Not for kids").assertExists()`; `:130` → `compose.onNodeWithText("Not for kids").assertDoesNotExist()`.

- [ ] **Step 2: run, expect failure** — `./gradlew :feature:player:testDebugUnitTest` → `Unresolved reference 'setKidsMark'`, `'kidsMark'`, `'KIDS_CHOICES'`.

- [ ] **Step 3: implement.** `PlayerMarksState.kt:18` → `/** The age the open title is marked for kids from — 6 or 12 — or null when it is not marked. */ val kidsMark: Int?,`; `forKids` (`:33`) reads `kidsMark != null`. `KidsLabel.kt`:

```kotlin
package player

import model.KIDS_LIMITS
import model.KidsVerdict

/**
 * What the Kids control says — the web player's own: a rated title's
 * verdict, locked; an unrated title's current choice, the option its select
 * shows.
 */
fun kidsLabel(marks: PlayerMarksState): String =
    when (marks.kidsVerdict) {
        KidsVerdict.SAFE -> "For kids · ${marks.ageLabel}"
        KidsVerdict.UNSAFE -> "${marks.ageLabel} · not for kids"
        KidsVerdict.UNRATED -> KIDS_CHOICES.firstOrNull { it.age == marks.kidsMark }?.label ?: KIDS_CHOICES.first().label
    }

/** One answer the Kids control offers on an unrated title — an option of the web's select. */
data class KidsChoice(
    val age: Int?,
    val label: String,
)

val KIDS_CHOICES: List<KidsChoice> = listOf(KidsChoice(null, "Not for kids")) + KIDS_LIMITS.map { KidsChoice(it, "From $it") }
```

`PlayerMarksController.kt:51-64` — `kidsMark = snapshot.kidsMarks[it]`; above `kidsVerdict = kidsVerdictOf(fsk, KIDS_LIMITS.max())` the comment "A grown-up reads a rating against the widest limit a kid can have; a kid at 6 is kept from an FSK 12 film by its own filter, not by this label." Replace `toggleKids` (`:85-97`) with:

```kotlin
    /**
     * "Not for kids / From 6 / From 12" — the web's select. Marked here rather
     * than on a shelf: "this is where a viewer is when they find out what a
     * film actually is."
     */
    fun setKidsMark(age: Int?) {
        val setId = session.openSetId ?: return
        // A kids profile does not approve titles for itself.
        if (marks.value?.canMarkKids == false) return
        // A rated title is not marked: its rating already decided.
        if (ageOf(openFsk.value) != null) return
        if (age != null && age !in KIDS_LIMITS) return
        write("Kids update") {
            repository.setKids(setId, age)
            true
        }
    }

    /** The single-button form of [setKidsMark]: a mark from 12, or none. */
    fun toggleKids() {
        val setId = session.openSetId ?: return
        setKidsMark(if (setId in repository.snapshot.value.kids) null else KIDS_LIMITS.max())
    }
```

`PlayerViewModelDelegates.kt` (beside `:48`) — `fun PlayerViewModel.setKidsMark(age: Int?) = marksController.setKidsMark(age)`.

- [ ] **Step 4: run, expect pass** — `./gradlew :feature:player:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest`.
- [ ] **Step 5: commit** — bump, then `git add android/feature/player android/ui-tv/src/test/kotlin/ui/tv/player/TvPlayerMarksTest.kt <manifests> docs/project-changelog.md && git commit -m "feat(android): a Kids mark says from which age, 6 or 12; release <next>"`.

### Task 7: Whole-module check and the real core on the tablet

- [ ] **Step 1:** `./gradlew testDebugUnitTest :core:model:test lint` → green in every module.
- [ ] **Step 2:** `rg -n "KIDS_AGE_LIMIT|createProfile\(|deleteProfile\(id|toggleKids" android --glob '!**/build/**' --glob '!**/uniffi/**'` → only `toggleKids` (phase 07 retires it).
- [ ] **Step 3: the contract against the real core** — a fresh data dir per test, no Telegram session; nothing of the household's is read or written:

```bash
ANDROID_SERIAL=caad49da ./gradlew :core:rust:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=rust.RealCoreContractTest
```
Every `CoreContract` case passes on the tablet. A failure is the fake and the core disagreeing — stop and report which, do not bend the fake toward the core.

## Todo list

- [ ] Task 1 — bindings in; model roles; `FakeProfiles`; contract; repository `manage`/`setKids(age)`; fakes; pickers lose add/remove (one commit, right after phase 05's)
- [ ] Task 2 — per-kid limit in the catalog, `KidsEmpty(limit)` (commit)
- [ ] Task 3 — `PinAsk`, `PinPrompt`, the web's words (commit)
- [ ] Task 4 — `pick` / `claim` / `createFirst` (commit)
- [ ] Task 5 — `ManageProfilesViewModel` (commit)
- [ ] Task 6 — player `setKidsMark` (commit)
- [ ] Task 7 — every module green; `RealCoreContractTest` on caad49da

## Success criteria

- `ProfileRolesFixtureTest` runs every `profile-rules.json` case and passes.
- `FakeCoreContractTest` and `RealCoreContractTest` pass the same `CoreContract` — create-first, kid, remove, wrong/malformed PIN, refusals, marks.
- A kid at 6 sees only FSK ≤ 6 and "from 6" marks; at 12 both; the empty shelf names its own limit.
- Picker: first profile made with a PIN typed twice; claim sticks; kid opens at once; grown-up needs its PIN; no-PIN grown-up sets one twice; five wrong → wait, right PIN still waits (`ProfilePinTest`).
- Manage: admin vs parent panels, remove cascades, admin unremovable, own-PIN change keeps working, close forgets the PIN.
- Every sentence equals the web's (`ProfileWordsTest`).
- Every unit-test module green after each commit.

## Risk assessment

| Risk | L×I | Mitigation |
|---|---|---|
| Phase 05's bindings commit pushed without Task 1 → Android red on `origin/main` | M×H | Task 1 lands next and the two are pushed together (phase 05's own note) |
| Generated names differ from § Interfaces | M×L | Task 1 Step 1 greps them; code uses the generated spelling |
| `FakeCore` drifts from the core | M×M | `CoreContract` on both (now with real creates) + fixture test; remaining ambiguities in the report, not guessed silently |
| A fresh device makes a first admin before its first sync reaches it | M×M | Contract accepts it (earliest claim wins); phase 07 keeps a "Look again" beside the first-profile question |
| Between Task 1 and phase 07 the picker cannot add or remove | H×L | Stated in the changelog; phase 07 lands next; no device install between |
| `canStay` now follows `chosenProfileId` — a transient null during a choice | L×M | `choose` moves to `Chosen` directly; `ProfileOwnershipTest` covers the races |

Rollback: each task is its own commit; revert newest-first. Reverting Task 1 means reverting phase 05's bindings commit with it (one pair).

## Security considerations

- The PIN lives only in: the dialog's field until four digits, `PinAsk.first` (a new PIN's first entry), `ManageProfilesViewModel.heldPin` while the panel is open (cleared on `close`, `open`, `onCleared`). Never in UI state, never logged; `ProfileRequest` is plain classes, so no generated `toString` prints one.
- No hash or salt reaches Kotlin (`Profile.hasPin` only).
- The rule is the core's; Kotlin's `allowed` only decides what to offer.
- Honest ceiling (spec §1): `choose` is not PIN-checked by the core; adb gets past it; `PICKER_NOTE` says so.

## Next steps

Phase 07 draws the first-profile question, the PIN dialog and pad, the admin question, Manage (phone and TV), the Kids choice, then retires `toggleKids`. Contract questions: `plans/reports/planner-260928-0047-android-phases-report.md`.
