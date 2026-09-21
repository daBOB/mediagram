# Phase 4: The phone asks

**Deliverable:** `Fetch details and artwork` in the overflow menu, reporting
what it filled in, with synopses appearing on titles that had none.

## Context

- `android/ui-mobile/src/main/kotlin/AppChrome.kt` — `MenuActions` and the five items
- `android/ui-mobile/src/main/kotlin/LibraryFlow.kt` — the wiring, the disabled reason, the progress line and the result dialog
- `android/feature/system/src/main/kotlin/PostersViewModel.kt` — the view model this renames and widens
- `android/ui-mobile/src/main/kotlin/PosterFetchReport.kt` — the sentence a finished run leaves behind
- `android/ui-mobile/src/main/kotlin/TitleDetailScreen.kt` — where a fetched synopsis shows up

## Key insight

Everything the phone needs is already wired: a menu item that starts a run, a
disabled reason while one is in flight, a progress line that keeps the shelves
up, and a dialog that waits rather than covering a film. This phase renames
what they say and widens what they count. The only genuinely new thing is the
device locale, which Kotlin already knows and Rust cannot.

The result sentence is the piece worth care. Six counts is more than a sentence
can carry gracefully, and a viewer reading it wants to know two things: did it
find anything, and is anything still missing.

---

### Task 1: The sentence, and the locale

**Files:**
- Modify: `android/ui-mobile/src/main/kotlin/PosterFetchReport.kt` → rename to `FetchReportSentence.kt`
- Modify: `android/feature/system/src/main/kotlin/PostersViewModel.kt` → rename to `FetchViewModel.kt`, and `PostersUiState.kt` with it
- Test: `android/ui-mobile/src/test/kotlin/PosterFetchReportTest.kt` → renamed alongside

**Interfaces — Consumes:** `FetchReport` (phase 3).

- [ ] **Step 1: Write the failing test**

```kotlin
    /** A run that filled both gaps says so in one line. */
    @Test
    fun aRunThatFoundBothSaysBoth() {
        assertEquals(
            "12 described, 9 posters fetched.",
            fetchSentence(detailsRecorded = 12, postersFetched = 9, detailsAlreadyKnown = 0, postersAlreadyHeld = 0, noProviderId = 0, failed = 0),
        )
    }

    /** Nothing new is the ordinary second run, and is not a failure. */
    @Test
    fun aSecondRunSaysEverythingWasAlreadyThere() {
        assertEquals(
            "Nothing new — 12 already described, 9 posters already held.",
            fetchSentence(detailsRecorded = 0, postersFetched = 0, detailsAlreadyKnown = 12, postersAlreadyHeld = 9, noProviderId = 0, failed = 0),
        )
    }

    /** A course has no provider entry, which is ordinary and worth saying once. */
    @Test
    fun titlesWithNoProviderEntryAreCountedNotBlamed() {
        val line = fetchSentence(detailsRecorded = 3, postersFetched = 3, detailsAlreadyKnown = 0, postersAlreadyHeld = 0, noProviderId = 1, failed = 0)

        assert(line.contains("1 title has no provider entry"))
        assert(!line.contains("failed"))
    }

    /** A failure is named only when there was one, and never blamed on the key. */
    @Test
    fun failuresAreNamedOnlyWhenTheyHappened() {
        val line = fetchSentence(detailsRecorded = 3, postersFetched = 3, detailsAlreadyKnown = 0, postersAlreadyHeld = 0, noProviderId = 0, failed = 2)

        assert(line.contains("2 could not be read"))
    }
```

- [ ] **Step 2: Run it**

```bash
cd /home/andre/Workspace/mediagram-android/android
./gradlew :ui-mobile:testDebugUnitTest --tests '*FetchReport*'
```

Expected: FAIL to compile.

- [ ] **Step 3: Implement**

Lead with what changed, mention what did not, and name the leftovers only when
there are any — the rule `posterReportLine` already follows. Keep it pure and
`internal`; the composable is not where a sentence is built.

- [ ] **Step 4: Pass the locale down**

The core takes a fallback language for a library with no descriptions yet.
Kotlin supplies it from the device: `Locale.getDefault().toLanguageTag()`.
Read it in the view model, not the composable — a screen rotation must not
change what a running fetch asked for.

- [ ] **Step 5: Run, then commit**

```bash
./gradlew :ui-mobile:testDebugUnitTest :feature:system:testDebugUnitTest
git add android/
git commit -m "feat(android): say what a fetch filled in, and ask in the device's language"
```

---

### Task 2: The menu item, and the phone

**Files:**
- Modify: `android/ui-mobile/src/main/kotlin/AppChrome.kt`, `android/ui-mobile/src/main/kotlin/LibraryFlow.kt`

- [ ] **Step 1: Rename the action**

`Fetch posters` becomes **`Fetch details and artwork`**, staying third:

```
System
Refresh library
Fetch details and artwork
TMDB key
Start over
```

`MenuActions.onFetchPosters` and `fetchPostersDisabledReason` rename with it.
`AppChrome.kt`'s KDoc names the five items and their ellipsis rule — it must
still be true afterwards, including about this one.

- [ ] **Step 2: Check the line budget**

```bash
./gradlew :ui-mobile:testDebugUnitTest
wc -l ui-mobile/src/main/kotlin/*.kt feature/system/src/main/kotlin/*.kt
```

`AppChrome.kt` was 197 at the branch point and a longer label does not change
that, but measure rather than assume. Everything strictly under 200.

- [ ] **Step 3: Prove it on the phone**

```bash
./gradlew :app:installDebug
adb shell monkey -p com.mediagram.android -c android.intent.category.LAUNCHER 1
```

A real phone is attached and signed in to a real library. **Never "start over"
on it** — that discards the user's real Telegram session; if the menu opens by
accident, dismiss with `KEYCODE_BACK` rather than tapping. `adb shell input tap`
is unreliable against Compose here; use `adb shell input swipe X Y X Y 150`,
with `uiautomator dump` and `adb exec-out screencap`. Never end your turn to
wait for the device; loop inside one command.

1. A title with no synopsis before the run has one after it.
2. The synopsis is in the library's language, not English — this library is described in `de-DE`.
3. The result sentence matches what actually changed.
4. A second run reports nothing new and downloads nothing.
5. The shelves stay up while it runs, and the item is disabled with a reason.
6. Refresh the library afterwards: the fetched descriptions are still there.
7. The System screen's poster count and the detail screens agree with each other.

Confirmation 6 is the one this plan exists for. If it fails, the sidecar is in
the wrong place and phase 2's boundary is wrong.

- [ ] **Step 4: Commit**

```bash
git add android/
git commit -m "feat(android): ask for the details and the artwork together"
```

## Todo list

- [ ] The menu item reads `Fetch details and artwork`, third of five
- [ ] The sentence reports both halves and names leftovers only when there are any
- [ ] The device locale reaches the core as the fallback language
- [ ] Every file under 200 lines
- [ ] The seven device confirmations made, especially the sixth

## Success criteria

A title with no synopsis has one after a run, in German, and still has it after
a refresh. `./scripts/check.sh` passes.

## Risk assessment

| Risk | Mitigation |
|---|---|
| Descriptions vanish on the next refresh | Device confirmation 6 is exactly this, and phase 2's tests pin it below. |
| The sentence becomes a list of six numbers | Task 1's tests pin four shapes, including the two-count and nothing-new cases. |
| A rotated key reads as "nothing to do" | Phase 3 keeps verification off the cached client; the run reports `NotAuthorized`. |
| The renamed KDoc keeps counting four items | Step 1 names it. |

## Next steps

The whole-branch review, then the version bump computed at the merge against
`main` as it then stands — not on the branch.
