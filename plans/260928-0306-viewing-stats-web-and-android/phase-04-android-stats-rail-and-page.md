# Phase 04 — Android: Stats rail item and page, phone and TV

**Goal:** every progress write on Android carries the device's local date, read at the moment of
the write. The chosen profile's stats open from a new rail item "Stats" (between Genres and
Settings) on the phone, the tablet and the TV. The page shows this week / this month / all time, a
bar per day for the last 30 days, and the history newest first, worded exactly as the web's page
(contract §6).

**Architecture:**
- `DefaultWatchStateRepository` gets an injectable `today()` in place of the inline
  `LocalDate.now()` that phase 03 left at its one `core.setProgress` call.
- A new `:feature:stats` module has three parts:
  - pure wording (`StatsFormat.kt`);
  - the read and the page state, with their pure mapping (`StatsUiState.kt`);
  - a `StatsViewModel` that reads `core.stats(chosenProfile, today)` into a `StatsRead`.
- Sets are named at the UI edge from **this profile's own catalogue** (`CatalogUiState`, Kids
  filter applied), as the web does (phase 02 decision 15). The join is one shared composable in
  `ui-common`, `rememberStatsPage(catalogState)`, because feature modules may not depend on each
  other and the catalogue lives in `feature:catalog`.
- `ui-common` also holds the one bar chart: 30 bottom-aligned boxes, no chart library. The phone's
  `StatsScreen` and the TV's `TvStatsPage` are thin renderers.
- Navigation follows Latest: `RailItem.STATS`, `Destination.Stats`, `FrameKind.STATS`/`openStats()`,
  and a TV restore key `TvStatsRailKey` so Back puts the remote on the rail's Stats row.

```
ProgressRecorder.save ─► WatchStateRepository.setProgress(setId, at, dur)
                          └─► core.setProgress(id, setId, at, dur, today())     today = LocalDate.now(), per write
rail "Stats" ─► at.openStats() ─► FrameKind.STATS ─► StatsFrame / TvStatsFrame(catalogState)
   └─► rememberStatsPage(catalogState)                                            (ui-common)
         StatsViewModel.state: chosenProfileId ─flatMapLatest─► core.stats(id, now().toLocalDate())
                               ─► StatsRead{Loading | Failed(reason) | Done(summary, now)}
         allSetsById(Ready.shelves)  — this profile's catalogue, Kids filter applied
         statsUiStateOf(read, sets) ─► StatsUiState{Loading | Failed(text) | Empty | Ready}
   ─► StatsScreen (M3) | TvStatsPage (tv-material) ─► StatsBars (ui-common)
```

**Effort:** ~5.5 h. **Bump:** minor, once, on the last commit (Task 4.7).

## Context links

- Contract (amended): [shared-contract.md](shared-contract.md) §1 (local day), §4 (summary), §5 (uniffi: async, never throws, a storage failure answers an empty summary), §6 (the page: every string, title rule, icon), §8 (constraints). Decisions 1–11 in [plan.md](plan.md); 3, 4, 9 and 10 bear on this phase.
- Web reference, final: [phase-02-web-record-sync-route-and-stats-page.md](phase-02-web-record-sync-route-and-stats-page.md):
  - Task 2.5 `stats-format.js`: `watchTime`, `whenLabel`, `shortDate`, `weekdayInitial`, `historyTitle`, `historyLine`;
  - Task 2.6 `stats-page.js`: empty when history is empty, failure line, bar labels;
  - key decision 15: names come from the profile's own Kids-filtered `byId`.
- Core, final: [phase-03-core-record-sync-summary-uniffi.md](phase-03-core-record-sync-summary-uniffi.md) Task 3.7 (`Core::stats`) and Task 3.8 (bindings, `FakeCore` stubs, all 16 call sites; it puts `LocalDate.now().toString()` inline at `WatchStateRepository.kt:284`).
- Format reference: `plans/261002-0213-android-self-update/phase-04-android-updater-module-and-settings-row.md`.
- Patterns this phase copies, verified 2026-10-03:
  - per-visit read: `feature/system/src/main/kotlin/SystemViewModel.kt:44-90`
  - `chosenProfileId` per-profile flow: `feature/setup/src/main/kotlin/ProfileSettingsViewModel.kt:50-56`
  - override-by-delegation test: `feature/setup/src/test/kotlin/ProfileSettingsViewModelTest.kt:36-45`
  - rail-reached frame: `ui-mobile/.../ui/LibraryBrowseBranches.kt:90-107` (Latest)
  - TV frame: `ui-tv/.../tv/TvPreloadsFrame.kt:31-55`
  - TV sentinel restore: `ui-tv/.../tv/catalog/TvCatalogNav.kt:22-26,94-152`
  - TV page harness: `ui-tv/src/test/kotlin/ui/tv/catalog/TvScreenStateTest.kt`, `TvPreloadsPageTest.kt`
  - TV app-level test: `ui-tv/src/test/kotlin/ui/tv/TvMenuTest.kt:49-77,413-421`
  - hand-rolled 24 h time: `feature/player/src/main/kotlin/EndsAt.kt:17-21`
- Naming a set the way Continue watching does:
  - web `home-resume.js:32-33` (`set.kind !== "movie" && set.show` → show, else title);
  - Android `ResumeCard.kt:78-79`;
  - episode label: `core/model/src/main/kotlin/EpisodeLabel.kt:14-19` (`S1E4` for an episode, `3` for a lesson);
  - this profile's catalogue by id: `feature/catalog/src/main/kotlin/AllSetsIndex.kt:13` (`allSetsById`, walks every collection's divisions, `HomeShelves.kt:145-159`);
  - Kids filtering: `CatalogViewModel.kt:61-69,153-167`.

All Android paths below are relative to `android/` unless they start with `scripts/`, `web/` or `plans/`.

## Generated Kotlin names (phase 03, confirmed)

Phase 03 Task 3.4 defines the Rust records (`StatsSummary{week_seconds, month_seconds, all_seconds, last30, history}`, `DayBar{day, seconds}`, `HistoryEntry{kind, set_id, at: i64, seconds}`, `HistoryKind{Started, Finished, Again}`). Task 3.7 exports `async fn stats`, and Task 3.8 regenerates the bindings. In Kotlin that gives:

```kotlin
// package uniffi.mediagram_core
interface CoreInterface {
    suspend fun setProgress(profileId: String, setId: String, at: Double, duration: Double?, localDay: String)
    suspend fun stats(profileId: String, today: String): StatsSummary   // never throws; storage failure → empty summary
}
data class StatsSummary(var weekSeconds: Double, var monthSeconds: Double, var allSeconds: Double,
                        var last30: List<DayBar>, var history: List<HistoryEntry>)
data class DayBar(var day: String, var seconds: Double)
data class HistoryEntry(var kind: HistoryKind, var setId: String, var at: Long, var seconds: Double)
enum class HistoryKind { STARTED, FINISHED, AGAIN }
```

`FakeCore.stats` is a zero stub (phase 03 Task 3.8 Step 2). The tests here override `stats` through `CoreInterface by FakeCore()` and build records with named arguments, so they don't depend on the stub's answer or on field order.

## Global constraints (contract §8)

- No plan references (phase numbers, decision numbers) in code, test names or commit messages.
- New files ≤ 200 lines. Existing over-limit files (`TvCatalogScreen.kt` 299, `LibraryFlowBranches.kt` 259, `LibraryBrowseBranches.kt` 235) only gain the one or two lines named below. New frames go in files of their own.
- Versions: all three manifests bumped by pattern, once, on this phase's last commit.
- Branch `feat/viewing-stats`, worktree off `main`. Follow memory rules:
  - pin every adb/gradle device command to a serial;
  - device walks only navigate, never change a setting;
  - rebuild the native core before installing (`scripts/build-android-core.sh`), because a stale `.so` crashes the app at launch.

## Key decisions (verified against code and the final phases 02/03)

1. **The local day is injectable, read per write.** Phase 03 leaves `core.setProgress(id, setId, at, duration, LocalDate.now().toString())` inline (Task 3.8 Step 3). Task 4.1 lifts that into a constructor lambda, `today: () -> String = { LocalDate.now().toString() }`, so a midnight crossing is testable. The default keeps every existing `DefaultWatchStateRepository(...)` construction compiling (`DataModule.kt:138-141`, `TvAppFixture`). `ProgressRecorder.kt:36` is unchanged.
2. **No `StatsRepository`.** The ViewModel reaches the core through `CoreProvider.awaitCore()` (`SystemViewModel.kt:91`). A one-implementation interface would be scaffolding.
3. **Sets are named from this profile's own catalogue, as on the web.** The web resolves through `app.js`'s Kids-filtered `byId`: phase 02 decision 15, "a title this profile cannot see is never named". On Android that is `CatalogUiState.Ready.shelves`, whose shelves `CatalogViewModel` has already put through the Kids filter.
   - `allSetsById` is in `feature:catalog`, so the join happens in `ui-common` (`rememberStatsPage`), which already depends on both. The ViewModel exposes the raw read (`StatsRead`), and `statsUiStateOf(read, sets)` is pure.
   - While the catalogue isn't ready, a page with history reads as Loading rather than calling every title gone.
4. **Title rule = Continue watching's, not the player's title line** (contract §6). A film is its title. An episode or lesson is "show episodeLabel" joined by a space ("Crime 101 S1E4", "Geldhochschule 3"), using the existing `model.episodeLabel`. `titleLine` stays where it is, so the old Task 4.2 is dropped.
5. **Each profile sees only its own stats.** `flatMapLatest` on `chosenProfileId` cancels a read for a profile that was left, so it is never published. `WhileSubscribed(5_000, replayExpirationMillis = 0)` resets the cached read once the page is left, so whoever opens it next never sees a flash of the previous profile.
6. **The achievement dot is not built.** Both rail rows already take a trailing slot: TV `TvIndexRow(trailing = …)` at `TvLibraryRail.kt:167`, phone `RailRow(count = …)` at `LibraryRail.kt:117`. Adding it later costs nothing now.

## Deliberate differences from the web (Surface Parity: written down, not silent)

- **TV:** the totals, the chart and each history line are focus stops. A page with nothing to press still needs somewhere for the remote to rest, and stepping is what scrolls it (precedent `ui-tv/.../system/TvInfoBlock.kt:39-43`).
- **Phone at compact/medium width:** a pushed frame offers Stats in its ⋮ as well, the same way it carries Latest and Genres (`OverflowMenu.kt:36-45`). Those widths have no rail beside a pushed frame.
- **Loading:** Android shows only the heading until both the read and this profile's catalogue are in. The web's `byId` is always in memory by the time its page renders.

The weekday letters are not a difference: contract §6 places them at expanded width (≥ 840 dp) and on TV, matching the web's 768 px.

## Review focus (each has its test)

| # | Risk | Test (task) |
|---|---|---|
| R1 | Profile switched while stats load shows the old profile's numbers | `StatsViewModelTest.aProfileSwitchedMidReadShowsOnlyTheNewProfilesStats` (4.4) |
| R2 | Previous profile's stats flash on the next visit | `StatsViewModelTest.aReturnVisitReadsAgainAndNeverShowsTheProfileItLeft` (4.4) |
| R3 | Empty means empty history: only the heading and "Nothing watched yet.", no zero totals or chart | `StatsUiStateTest.theHistoryDecidesWhetherThePageIsEmpty` (4.3), `StatsScreenTest.nothingWatchedSaysSoUnderTheHeading`, `TvStatsPageTest.nothingWatchedSaysSoUnderTheHeading` (4.5) |
| R4 | A set no longer in the library, or not for this profile, gets named | `StatsUiStateTest.aSetIsNamedTheWayContinueWatchingNamesIt`, `…eachLineSaysWhatHappenedToWhichTitleWhenAndForHowLongInTheCoresOrder` (4.3) |
| R5 | A failed read shows as "nothing watched", or loses its reason | `StatsViewModelTest.aCoreThatCannotBeReachedFailsWithItsReason` (4.4), `StatsUiStateTest.theReadIsLoadingUntilTheCatalogueIsAndAFailureSaysWhy` (4.3), `TvStatsPageTest.aFailedReadSaysSo` (4.5) |
| R6 | TV Back from Stats loses rail focus | `TvStatsRailTest.theStatsRowOpensThePageAndBackPutsTheRemoteBackOnIt` (4.6) |
| R7 | A very long history composes eagerly or cannot be walked on TV | `TvStatsPageTest.aLongHistoryIsComposedLazilyAndTheRemoteWalksDownIt` (4.5) |
| R8 | A write across midnight is counted on the wrong day | `WatchStateLocalDayTest.eachProgressWriteCountsOnThisDevicesDateAtTheMomentItLands` (4.1) |
| R9 | Times told in UTC rather than the device's zone | `StatsFormatTest.aTimeIsToldOnThisDevicesOwnClock` (4.3) |
| R10 | Rail order differs from decision 9 on a surface | `LibraryRailTest.everyRowItsCountsAndTheTallyAreOnScreenAtOnce` (order assert), `TvStatsRailTest.statsSitsBetweenGenresAndSettingsOnTheRail` (4.6) |
| R11 | A finish from before stats existed claims "under a minute" | `StatsUiStateTest.aFinishFromBeforeStatsExistedHasNoDuration` (4.3) |
| R12 | A catalogue still loading calls every title gone | `StatsUiStateTest.theReadIsLoadingUntilTheCatalogueIsAndAFailureSaysWhy` (4.3) |

## Dependencies

- **Blocked by:** phases 01–03 merged into `feat/viewing-stats`. Phase 03 Task 3.8 leaves the regenerated bindings, the `FakeCore` stubs, all 16 call sites on five arguments and the rebuilt `.so`. Phase 02 is the reference for every string.
- **Blocks:** phase 05 (device verification), phase 07 (the achievements section and the rail dot build on `StatsViewModel`, `StatsRead`, `StatsUiState`, `RailItem.STATS`).
- **File ownership:** `android/**` only. This phase does not touch `docs/**` (phase 05) or `plan.md` (lead).

---

## Task 4.1: The local day, injectable

Follows phase 03 Task 3.8, which left `android/core/data/src/main/kotlin/WatchStateRepository.kt` with `import java.time.LocalDate` (added above `import kotlinx.coroutines.CoroutineDispatcher`) and `core.setProgress(id, setId, at, duration, LocalDate.now().toString())` inside `setProgress` (was :284; one line lower after the import). There is no call-site sweep here; phase 03 did all 16.

**Files:**
- Modify: `core/data/src/main/kotlin/WatchStateRepository.kt` (interface doc on `setProgress`, the `DefaultWatchStateRepository` constructor, its `setProgress`)
- Create: `core/data/src/test/kotlin/WatchStateLocalDayTest.kt`. `WatchStateRepositoryTest.kt` is at 196 lines, so the test goes in a file of its own.

**Interfaces:**
- Consumes: `CoreInterface.setProgress(profileId, setId, at, duration, localDay: String)`.
- Produces: `DefaultWatchStateRepository(coreProvider, dispatcher = Dispatchers.IO, today: () -> String = { LocalDate.now().toString() })`. The `WatchStateRepository` interface is unchanged.

- [ ] **Step 1: Confirm the starting point**

Run: `git grep -n "LocalDate.now().toString()" -- android/core/data/src/main/kotlin/WatchStateRepository.kt`
Expected: one hit, inside `setProgress`. If there is none, phase 03 Task 3.8 isn't merged yet. Stop.

- [ ] **Step 2: Failing test**: `core/data/src/test/kotlin/WatchStateLocalDayTest.kt`

```kotlin
package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import testing.FakeCore
import testing.ResolvedCoreProvider
import uniffi.mediagram_core.CoreInterface
import kotlin.test.Test
import kotlin.test.assertEquals
import uniffi.mediagram_core.Profile as CoreProfile

class WatchStateLocalDayTest {
    @Test
    fun eachProgressWriteCountsOnThisDevicesDateAtTheMomentItLands() =
        runTest {
            val seeded =
                FakeCore().apply {
                    profiles = listOf(CoreProfile("p1", "Alice"))
                    chosen = "p1"
                }
            val days = mutableListOf<String>()
            val core =
                object : CoreInterface by seeded {
                    override suspend fun setProgress(
                        profileId: String,
                        setId: String,
                        at: Double,
                        duration: Double?,
                        localDay: String,
                    ) {
                        days += localDay
                        seeded.setProgress(profileId, setId, at, duration, localDay)
                    }
                }
            var today = "2026-09-26"
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined) { today }
            repository.reload()

            repository.setProgress("set-1", 600.0, 5_400.0)
            // The title plays on past midnight: the next write counts on the new day.
            today = "2026-09-27"
            repository.setProgress("set-1", 610.0, 5_400.0)

            assertEquals(listOf("2026-09-26", "2026-09-27"), days)
            assertEquals(listOf(610.0), repository.snapshot.value.progress.map { it.at })
        }
}
```

- [ ] **Step 3: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :core:data:testDebugUnitTest --tests data.WatchStateLocalDayTest`
Expected: FAIL. The trailing lambda doesn't match `DefaultWatchStateRepository`'s constructor.

- [ ] **Step 4: Implement** in `core/data/src/main/kotlin/WatchStateRepository.kt`

Interface doc on `setProgress`:
```kotlin
    /**
     * Saves progress for the chosen profile, the watch time since this
     * device's last write counted on this device's own date; does nothing
     * without one.
     */
    suspend fun setProgress(
```

Class header:
```kotlin
class DefaultWatchStateRepository(
    private val coreProvider: CoreProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * This device's date now, `YYYY-MM-DD`: the day a progress write's watch
     * time counts on. Read at each write rather than once, so a title
     * playing across midnight, or a device moved to another time zone,
     * counts on the day the write actually lands.
     */
    private val today: () -> String = { LocalDate.now().toString() },
) : WatchStateRepository {
```

`setProgress`, replacing the inline `LocalDate.now().toString()`:
```kotlin
    override suspend fun setProgress(
        setId: String,
        at: Double,
        duration: Double?,
    ) = writing { core, id ->
        core.setProgress(id, setId, at, duration, today())
    }
```

- [ ] **Step 5: Run, expect PASS**

Run: `cd android && ./gradlew -q :core:data:testDebugUnitTest :feature:player:testDebugUnitTest`
Expected: green. `WatchStateLocalDayTest` passes 1 test, and the existing repository, ownership and recorder suites are unchanged.

- [ ] **Step 6: Commit**

```bash
git add android/core/data
git commit -m "feat(android): progress writes take today's date from an injectable source, read per write"
```

## Task 4.2 — dropped (title rule reconciled, 2026-10-03)

Contract §6 names a set the way Continue watching does: show + episode label, not the player's title line. `model.episodeLabel` already lives in `core:model`, so `titleLine` doesn't move. Task 4.3's `statsTitle` uses `episodeLabel` directly.

## Task 4.3: `:feature:stats`, the page's wording and state as pure functions

**Files:**
- Create: `feature/stats/build.gradle.kts`
- Modify: `settings.gradle.kts` (add `include(":feature:stats")` after `include(":feature:system")`)
- Create: `feature/stats/src/main/kotlin/StatsFormat.kt`
- Create: `feature/stats/src/main/kotlin/StatsUiState.kt`
- Create: `feature/stats/src/test/kotlin/StatsFixtures.kt`
- Create: `feature/stats/src/test/kotlin/StatsFormatTest.kt`
- Create: `feature/stats/src/test/kotlin/StatsUiStateTest.kt`

**Interfaces:**
- Consumes: `StatsSummary`, `HistoryEntry`, `HistoryKind` (phase 03), `model.episodeLabel`, `model.MediaSet`, `model.Kind`.
- Produces (package `stats`):
  - wording: `durationText(seconds: Double): String`, `whenText(atMs: Long, now: ZonedDateTime): String`, `shortDate(date: LocalDate): String`, `weekdayInitial(date: LocalDate): String`, `failureLine(reason: String?): String`, `kindLabel(kind: HistoryKind): String`, `statsTitle(set: MediaSet?): String`, `historyLine(entry: HistoryEntry, set: MediaSet?, now: ZonedDateTime): String`;
  - the read: `sealed interface StatsRead { Loading; Failed(reason: String?); Done(summary: StatsSummary, now: ZonedDateTime) }`;
  - the page: `sealed interface StatsUiState { Loading; Failed(text: String); Empty; Ready(totals: List<Pair<String, String>>, bars: List<StatsBar>, history: List<StatsLine>) }`, `data class StatsBar(fraction: Float, initial: String, description: String)`, `data class StatsLine(key: String, text: String)`, `statsUiStateOf(read: StatsRead, sets: Map<String, MediaSet>?): StatsUiState`;
  - constants: `NOTHING_WATCHED`, `NO_LONGER_IN_LIBRARY`.

- [ ] **Step 1: Module**: `feature/stats/build.gradle.kts`

```kotlin
// The Stats page's ViewModel, its read and its page state, no composables —
// ui-mobile and ui-tv each render the same finished strings, so the two
// pages cannot word a line differently.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.feature.stats"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))

    testImplementation(project(":core:testing"))
}
```

`settings.gradle.kts`, after `include(":feature:system")`:
```kotlin
include(":feature:stats")
```

- [ ] **Step 2: Failing tests**

`feature/stats/src/test/kotlin/StatsFixtures.kt`:
```kotlin
package stats

import model.Kind
import model.MediaSet
import uniffi.mediagram_core.DayBar
import uniffi.mediagram_core.HistoryEntry
import uniffi.mediagram_core.HistoryKind
import uniffi.mediagram_core.StatsSummary
import java.time.ZoneId
import java.time.ZonedDateTime

internal val Berlin: ZoneId = ZoneId.of("Europe/Berlin")

/** Saturday 26 September 2026, 22:00 in Berlin — every test's "now". */
internal val Now: ZonedDateTime = ZonedDateTime.of(2026, 9, 26, 22, 0, 0, 0, Berlin)

/** Epoch milliseconds of a Berlin wall-clock time. */
internal fun ms(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, Berlin).toInstant().toEpochMilli()

/** Thirty days ending on [Now]'s date, oldest first, as the core answers them; [today] and [yesterday] fill the last two. */
internal fun last30(
    today: Double = 0.0,
    yesterday: Double = 0.0,
): List<DayBar> =
    (29 downTo 0).map { back ->
        val seconds =
            when (back) {
                0 -> today
                1 -> yesterday
                else -> 0.0
            }
        DayBar(day = Now.toLocalDate().minusDays(back.toLong()).toString(), seconds = seconds)
    }

internal fun entry(
    kind: HistoryKind,
    setId: String,
    at: Long,
    seconds: Double,
) = HistoryEntry(kind = kind, setId = setId, at = at, seconds = seconds)

internal fun summary(
    week: Double = 0.0,
    month: Double = 0.0,
    all: Double = 0.0,
    days: List<DayBar> = last30(),
    history: List<HistoryEntry> = emptyList(),
) = StatsSummary(weekSeconds = week, monthSeconds = month, allSeconds = all, last30 = days, history = history)

internal fun mediaSet(
    id: String,
    title: String,
    kind: Kind = Kind.MOVIE,
    show: String? = null,
    season: Int? = null,
    episode: Int? = null,
) = MediaSet(
    setId = id, kind = kind, title = title, show = show, chapter = null, path = null,
    season = season, episodeFirst = episode, episodeLast = null, year = null, durationSecs = null,
    posterPath = null, totalBytes = 0,
)

/** A film, an episode and a lesson — this profile's catalogue, by id. */
internal val Catalogue: Map<String, MediaSet> =
    listOf(
        mediaSet("f1", "Der Pate"),
        mediaSet("e1", "Pilot", Kind.EPISODE, show = "Crime 101", season = 1, episode = 4),
        mediaSet("l3", "Zinsen", Kind.TUTORIAL, show = "Geldhochschule", episode = 3),
    ).associateBy { it.setId }
```

`feature/stats/src/test/kotlin/StatsFormatTest.kt`:
```kotlin
package stats

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsFormatTest {
    @Test
    fun durationsCountWholeMinutes() {
        assertEquals("under a minute", durationText(0.0))
        assertEquals("under a minute", durationText(59.9))
        assertEquals("1 min", durationText(60.0))
        assertEquals("42 min", durationText(42 * 60 + 59.0))
        assertEquals("59 min", durationText(3_599.0))
        assertEquals("1 h", durationText(3_600.0))
        assertEquals("3 h", durationText(3 * 3_600 + 59.0))
        assertEquals("3 h 12 min", durationText(3 * 3_600 + 12 * 60 + 30.0))
    }

    @Test
    fun aTimeIsTodayThenAWeekdayForSixDaysThenADate() {
        assertEquals("today 21:14", whenText(ms(2026, 9, 26, 21, 14), Now))
        assertEquals("today 00:05", whenText(ms(2026, 9, 26, 0, 5), Now))
        assertEquals("Fri 20:05", whenText(ms(2026, 9, 25, 20, 5), Now))
        assertEquals("Sun 09:03", whenText(ms(2026, 9, 20, 9, 3), Now), "six days back is still a weekday")
        assertEquals("19 Sep", whenText(ms(2026, 9, 19, 23, 59), Now), "seven days back would repeat today's weekday")
        assertEquals("3 Jan", whenText(ms(2026, 1, 3, 12, 0), Now), "the day is unpadded")
        assertEquals("21 Sep 2025", whenText(ms(2025, 9, 21, 18, 30), Now))
    }

    @Test
    fun aTimeIsToldOnThisDevicesOwnClock() {
        // 22:30 UTC on Friday is already half past midnight on Saturday in Berlin.
        val at = ZonedDateTime.of(2026, 9, 25, 22, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("today 00:30", whenText(at, Now))
    }

    @Test
    fun weekdayLettersRunMondayToSundayAndBarDatesCarryNoYear() {
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), (21..27).map { weekdayInitial(LocalDate.of(2026, 9, it)) })
        assertEquals("3 Oct", shortDate(LocalDate.of(2026, 10, 3)))
        assertEquals("31 Dec", shortDate(LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun aFailureSaysWhyWhenThereIsAReason() {
        assertEquals("Could not read your stats: database is locked", failureLine("database is locked"))
        assertEquals("Could not read your stats.", failureLine(null))
        assertEquals("Could not read your stats.", failureLine(" "))
    }
}
```

`feature/stats/src/test/kotlin/StatsUiStateTest.kt`:
```kotlin
package stats

import uniffi.mediagram_core.HistoryKind
import uniffi.mediagram_core.StatsSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StatsUiStateTest {
    private fun page(summary: StatsSummary) = statsUiStateOf(StatsRead.Done(summary, Now), Catalogue)

    @Test
    fun theHistoryDecidesWhetherThePageIsEmpty() {
        assertEquals(StatsUiState.Empty, page(summary()))
        assertEquals(StatsUiState.Empty, statsUiStateOf(StatsRead.Done(summary(), Now), sets = null), "nothing to name, so nothing waits for the catalogue")
    }

    @Test
    fun theReadIsLoadingUntilTheCatalogueIsAndAFailureSaysWhy() {
        val watched = StatsRead.Done(summary(all = 60.0, history = listOf(entry(HistoryKind.STARTED, "f1", ms(2026, 9, 26, 21, 0), 60.0))), Now)
        assertEquals(StatsUiState.Loading, statsUiStateOf(StatsRead.Loading, Catalogue))
        assertEquals(StatsUiState.Loading, statsUiStateOf(watched, sets = null), "never call every title gone while the catalogue loads")
        assertEquals(StatsUiState.Failed("Could not read your stats: database is locked"), statsUiStateOf(StatsRead.Failed("database is locked"), Catalogue))
        assertEquals(StatsUiState.Failed("Could not read your stats."), statsUiStateOf(StatsRead.Failed(null), Catalogue))
    }

    @Test
    fun aFinishFromBeforeStatsExistedHasNoDuration() {
        val state = page(summary(history = listOf(entry(HistoryKind.FINISHED, "f1", ms(2026, 9, 25, 20, 5), 0.0))))
        assertEquals(listOf("Finished · Der Pate · Fri 20:05"), assertIs<StatsUiState.Ready>(state).history.map { it.text })
    }

    @Test
    fun theTotalsAreThisWeekThisMonthAndAllTime() {
        val state =
            page(
                summary(
                    week = 0.0,
                    month = 3 * 3_600 + 12 * 60.0,
                    all = 12 * 3_600.0,
                    history = listOf(entry(HistoryKind.STARTED, "f1", ms(2026, 9, 1, 20, 0), 12 * 3_600.0)),
                ),
            )
        assertEquals(
            listOf("This week" to "under a minute", "This month" to "3 h 12 min", "All time" to "12 h"),
            assertIs<StatsUiState.Ready>(state).totals,
        )
    }

    @Test
    fun eachLineSaysWhatHappenedToWhichTitleWhenAndForHowLongInTheCoresOrder() {
        val history =
            listOf(
                entry(HistoryKind.AGAIN, "f1", ms(2026, 9, 26, 21, 14), 42 * 60.0),
                entry(HistoryKind.FINISHED, "e1", ms(2026, 9, 25, 20, 5), 3 * 3_600 + 12 * 60.0),
                entry(HistoryKind.STARTED, "l3", ms(2026, 9, 19, 10, 0), 59.0),
                entry(HistoryKind.STARTED, "gone", ms(2025, 9, 21, 18, 30), 600.0),
            )
        val state = assertIs<StatsUiState.Ready>(page(summary(all = 1.0, history = history)))
        assertEquals(
            listOf(
                "Watched again · Der Pate · today 21:14 · 42 min",
                "Finished · Crime 101 S1E4 · Fri 20:05 · 3 h 12 min",
                "Started · Geldhochschule 3 · 19 Sep · under a minute",
                "Started · No longer in the library · 21 Sep 2025 · 10 min",
            ),
            state.history.map { it.text },
        )
        assertEquals(state.history.size, state.history.map { it.key }.toSet().size, "a lazy list needs every key distinct")
    }

    @Test
    fun eachBarIsItsDaysShareOfTheBusiestOldestFirst() {
        val history = listOf(entry(HistoryKind.STARTED, "f1", ms(2026, 9, 25, 20, 0), 5_400.0))
        val state = assertIs<StatsUiState.Ready>(page(summary(all = 5_400.0, days = last30(today = 3_600.0, yesterday = 1_800.0), history = history)))
        assertEquals(30, state.bars.size)
        assertEquals(listOf(0f, 0.5f, 1f), listOf(state.bars[0], state.bars[28], state.bars[29]).map { it.fraction })
        assertEquals("S", state.bars.last().initial, "today, a Saturday, is the rightmost bar")
        assertEquals("F", state.bars[28].initial)
        assertEquals("26 Sep · 1 h", state.bars.last().description)
        assertEquals("28 Aug · under a minute", state.bars.first().description)
    }

    @Test
    fun aSetIsNamedTheWayContinueWatchingNamesIt() {
        assertEquals("Der Pate", statsTitle(Catalogue.getValue("f1")))
        assertEquals("Crime 101 S1E4", statsTitle(Catalogue.getValue("e1")))
        assertEquals("Geldhochschule 3", statsTitle(Catalogue.getValue("l3")))
        assertEquals("No longer in the library", statsTitle(null), "gone from the library, or not on this profile's shelves: not named")
    }
}
```

- [ ] **Step 3: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :feature:stats:testDebugUnitTest`
Expected: FAIL with unresolved `durationText`, `statsUiStateOf`, `StatsRead`, …

- [ ] **Step 4: Implement**

`feature/stats/src/main/kotlin/StatsFormat.kt`:
```kotlin
package stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/*
 * The Stats page's words, the web's exactly (stats-format.js). Hand-rolled
 * English rather than the device's locale, as the player's own clock is
 * (EndsAt.kt): a viewer who reads the page on the web and on a television
 * reads the same line on both.
 */

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Watch time in whole minutes, floored: "under a minute" (0 s included), "42 min", "3 h 12 min", "3 h". */
fun durationText(seconds: Double): String {
    val minutes = (seconds / 60).toLong()
    return when {
        minutes < 1 -> "under a minute"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/**
 * When something happened, told on [now]'s own clock and zone: "today
 * 21:14"; a weekday for the six calendar days before ("Sat 21:14") — a
 * seventh would repeat today's weekday; a date further back ("21 Sep"),
 * with the year once it is another year's. A time ahead of [now] (another
 * device's clock running fast) reads as its date.
 */
fun whenText(
    atMs: Long,
    now: ZonedDateTime,
): String {
    val at = Instant.ofEpochMilli(atMs).atZone(now.zone)
    val clock = "%02d:%02d".format(at.hour, at.minute)
    return when (ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate())) {
        0L -> "today $clock"
        in 1L..6L -> "${WEEKDAYS[at.dayOfWeek.value - 1]} $clock"
        else -> if (at.year == now.year) shortDate(at.toLocalDate()) else "${shortDate(at.toLocalDate())} ${at.year}"
    }
}

/** "3 Oct": the day unpadded, no year — a bar's label, and the start of an older history time. */
fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"

/** The letter under a day's bar, Monday first: M T W T F S S. */
fun weekdayInitial(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1].take(1)

/** The page's line when the read failed, with the platform's reason when it gave one. */
fun failureLine(reason: String?): String = if (reason.isNullOrBlank()) "Could not read your stats." else "Could not read your stats: $reason"
```

`feature/stats/src/main/kotlin/StatsUiState.kt`:
```kotlin
package stats

import model.Kind
import model.MediaSet
import model.episodeLabel
import uniffi.mediagram_core.HistoryEntry
import uniffi.mediagram_core.HistoryKind
import uniffi.mediagram_core.StatsSummary
import java.time.LocalDate
import java.time.ZonedDateTime

/** Under the heading when the profile's history is empty. */
const val NOTHING_WATCHED = "Nothing watched yet."

/** A history line's title for a set this profile's catalogue does not hold. */
const val NO_LONGER_IN_LIBRARY = "No longer in the library"

/** What the core answered for the chosen profile, before any set is named — [StatsViewModel]'s state. */
sealed interface StatsRead {
    data object Loading : StatsRead

    /** The read could not be made; [reason] as the platform gave it, if it gave one. */
    data class Failed(
        val reason: String?,
    ) : StatsRead

    /** The profile's summary, read at [now]: its date was the read's "today", and its clock tells every line's time. */
    data class Done(
        val summary: StatsSummary,
        val now: ZonedDateTime,
    ) : StatsRead
}

/** The Stats page, every string finished, so both surfaces render the same words. */
sealed interface StatsUiState {
    data object Loading : StatsUiState

    data class Failed(
        val text: String,
    ) : StatsUiState

    /** Nothing in the history: only the heading and [NOTHING_WATCHED] — no zero totals, no empty chart. */
    data object Empty : StatsUiState

    data class Ready(
        /** "This week", "This month", "All time", each with its duration. */
        val totals: List<Pair<String, String>>,
        /** Thirty days, oldest first, today last. */
        val bars: List<StatsBar>,
        /** Newest first, in the core's order. */
        val history: List<StatsLine>,
    ) : StatsUiState
}

/** One day's bar: its height as a share of the busiest day's, its weekday letter, and its label ("3 Oct · 42 min"). */
data class StatsBar(
    val fraction: Float,
    val initial: String,
    val description: String,
)

/** One history line. [key] is unique on the page: a set has at most one line of each kind. */
data class StatsLine(
    val key: String,
    val text: String,
)

/**
 * The page for [read], every set named from [sets]: this profile's own
 * catalogue by set id, so a title this profile cannot see is never named.
 * [sets] is `null` while that catalogue is still loading; a page with
 * history waits for it rather than calling every title gone.
 */
fun statsUiStateOf(
    read: StatsRead,
    sets: Map<String, MediaSet>?,
): StatsUiState =
    when (read) {
        StatsRead.Loading -> StatsUiState.Loading
        is StatsRead.Failed -> StatsUiState.Failed(failureLine(read.reason))
        is StatsRead.Done ->
            when {
                read.summary.history.isEmpty() -> StatsUiState.Empty
                sets == null -> StatsUiState.Loading
                else -> pageOf(read.summary, sets, read.now)
            }
    }

private fun pageOf(
    summary: StatsSummary,
    sets: Map<String, MediaSet>,
    now: ZonedDateTime,
): StatsUiState.Ready {
    val busiest = summary.last30.maxOfOrNull { it.seconds } ?: 0.0
    return StatsUiState.Ready(
        totals =
            listOf(
                "This week" to durationText(summary.weekSeconds),
                "This month" to durationText(summary.monthSeconds),
                "All time" to durationText(summary.allSeconds),
            ),
        bars =
            summary.last30.map { bar ->
                val date = LocalDate.parse(bar.day)
                StatsBar(
                    fraction = if (busiest > 0) (bar.seconds / busiest).toFloat().coerceIn(0f, 1f) else 0f,
                    initial = weekdayInitial(date),
                    description = "${shortDate(date)} · ${durationText(bar.seconds)}",
                )
            },
        history = summary.history.map { entry -> StatsLine(key = "${entry.kind}:${entry.setId}", text = historyLine(entry, sets[entry.setId], now)) },
    )
}

/**
 * One history line: "Started · Der Pate · Sat 21:14 · 42 min". No duration
 * when nothing was counted: a finish from before stats existed was not
 * watched in under a minute.
 */
fun historyLine(
    entry: HistoryEntry,
    set: MediaSet?,
    now: ZonedDateTime,
): String {
    val parts = mutableListOf(kindLabel(entry.kind), statsTitle(set), whenText(entry.at, now))
    if (entry.seconds > 0) parts += durationText(entry.seconds)
    return parts.joinToString(" · ")
}

fun kindLabel(kind: HistoryKind): String =
    when (kind) {
        HistoryKind.STARTED -> "Started"
        HistoryKind.FINISHED -> "Finished"
        HistoryKind.AGAIN -> "Watched again"
    }

/**
 * The library's own name for a set, the way Continue watching names it: a
 * film by its title, an episode or lesson by its show and number ("Crime
 * 101 S1E4", "Geldhochschule 3"). A set this profile's catalogue does not
 * hold — gone, or not for this profile — is not named.
 */
fun statsTitle(set: MediaSet?): String {
    if (set == null) return NO_LONGER_IN_LIBRARY
    val show = set.show?.takeIf { it.isNotEmpty() }
    if (set.kind == Kind.MOVIE || show == null) return set.title
    return listOf(show, episodeLabel(set)).filter { it.isNotEmpty() }.joinToString(" ")
}
```

- [ ] **Step 5: Run, expect PASS**

Run: `cd android && ./gradlew -q :feature:stats:testDebugUnitTest`
Expected: green, 12 tests (`StatsFormatTest` 5, `StatsUiStateTest` 7).

- [ ] **Step 6: Commit**

```bash
git add android/settings.gradle.kts android/feature/stats
git commit -m "feat(android): stats page wording and state for durations, times, titles and history lines"
```

## Task 4.4: `StatsViewModel`

**Files:**
- Create: `feature/stats/src/main/kotlin/StatsViewModel.kt`
- Create: `feature/stats/src/test/kotlin/MainDispatcherRule.kt` (the per-module copy every feature module keeps, e.g. `feature/setup/src/test/kotlin/MainDispatcherRule.kt`)
- Create: `feature/stats/src/test/kotlin/StatsViewModelTest.kt`

**Interfaces:**
- Consumes: `CoreProvider.awaitCore()`, `CoreInterface.stats(profileId, today)`, `WatchStateRepository.chosenProfileId`, `StatsRead` (4.3).
- Produces: `@HiltViewModel class StatsViewModel @Inject constructor(coreProvider: CoreProvider, watchState: WatchStateRepository) { val state: StateFlow<StatsRead>; internal var now: () -> ZonedDateTime }`. Both dependencies are already bound (`DataModule.kt:136-141`, `CoreProvider` in the app's `CoreModule`), so no new Hilt module is needed.

- [ ] **Step 1: Failing tests**

`feature/stats/src/test/kotlin/MainDispatcherRule.kt`:
```kotlin
package stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Routes `viewModelScope`'s Main dispatcher onto an unconfined test
 * dispatcher, so a ViewModel's launched coroutines run eagerly; `runTest`
 * shares its scheduler, so `advanceTimeBy` drives the sharing timeout.
 */
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

`feature/stats/src/test/kotlin/StatsViewModelTest.kt`:
```kotlin
package stats

import data.DefaultWatchStateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.FakeCore
import testing.FakeCoreProvider
import testing.ResolvedCoreProvider
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.StatsSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A core whose stats are set per profile, whose read for "a" can be held open, and which records every read. */
private class StatsCore(
    raw: FakeCore,
) : CoreInterface by raw {
    val answers = mutableMapOf<String, StatsSummary>()
    val asked = mutableListOf<Pair<String, String>>()
    var holdA: CompletableDeferred<Unit>? = null

    override suspend fun stats(
        profileId: String,
        today: String,
    ): StatsSummary {
        asked += profileId to today
        if (profileId == "a") holdA?.await()
        return answers.getValue(profileId)
    }
}

class StatsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val raw =
        FakeCore().apply {
            profiles = listOf(Profile("a", "Ada"), Profile("b", "Ben"))
            chosen = "a"
        }
    private val core =
        StatsCore(raw).apply {
            answers["a"] = summary(all = 600.0)
            answers["b"] = summary(all = 1_800.0)
        }
    private val watch = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)

    private fun model() = StatsViewModel(ResolvedCoreProvider(core), watch).apply { now = { Now } }

    private fun allSeconds(read: StatsRead) = assertIs<StatsRead.Done>(read).summary.allSeconds

    @Test
    fun theChosenProfilesStatsAreReadForTodayOnThisDevicesClock() =
        runTest {
            watch.reload()

            val done = assertIs<StatsRead.Done>(model().state.first { it != StatsRead.Loading })

            assertEquals(listOf("a" to "2026-09-26"), core.asked)
            assertEquals(600.0, done.summary.allSeconds)
            assertEquals(Now, done.now, "the read's clock is the one every line is told on")
        }

    @Test
    fun aProfileSwitchedMidReadShowsOnlyTheNewProfilesStats() =
        runTest {
            watch.reload()
            core.holdA = CompletableDeferred()
            val model = model()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
            assertEquals(StatsRead.Loading, model.state.value, "a's read is held open")

            watch.chooseProfile("b")
            advanceUntilIdle()
            assertEquals(1_800.0, allSeconds(model.state.value), "b's stats show without waiting on a's read")

            core.holdA!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(1_800.0, allSeconds(model.state.value), "a's read, finishing late, is never published")
        }

    @Test
    fun aReturnVisitReadsAgainAndNeverShowsTheProfileItLeft() =
        runTest {
            watch.reload()
            val model = model()
            val visit = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
            advanceUntilIdle()
            assertEquals(600.0, allSeconds(model.state.value))

            visit.cancel()
            advanceTimeBy(5_001)
            assertEquals(StatsRead.Loading, model.state.value, "a's stats are not kept for whoever opens the page next")

            watch.chooseProfile("b")
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
            advanceUntilIdle()
            assertEquals(1_800.0, allSeconds(model.state.value))
            assertEquals(listOf("a", "b"), core.asked.map { it.first })
        }

    @Test
    fun withNobodyChosenNothingIsRead() =
        runTest {
            raw.chosen = null
            watch.reload()
            val model = model()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
            advanceUntilIdle()

            assertEquals(StatsRead.Loading, model.state.value)
            assertTrue(core.asked.isEmpty())
        }

    @Test
    fun aCoreThatCannotBeReachedFailsWithItsReason() =
        runTest {
            watch.reload()
            // The core itself never throws from stats() (a storage failure answers an
            // empty summary); what can fail is reaching a core at all.
            val model = StatsViewModel(FakeCoreProvider(null), watch).apply { now = { Now } }

            assertEquals(StatsRead.Failed("no core built for this fixture"), model.state.first { it != StatsRead.Loading })
        }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :feature:stats:testDebugUnitTest`
Expected: FAIL with unresolved `StatsViewModel`.

- [ ] **Step 3: Implement**: `feature/stats/src/main/kotlin/StatsViewModel.kt`

```kotlin
package stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * The chosen profile's stats, read once per visit the way the System
 * screen is: the read stops five seconds after the page is left and runs
 * again on the next visit, so minutes watched in between are there when
 * the viewer comes back.
 *
 * Only ever the chosen profile's. [flatMapLatest] cancels a read for a
 * profile that was left, so it is never published; resetting the shared
 * state once the page is left means whoever opens it next never sees the
 * previous profile's page, not even for a frame.
 *
 * Sets are named later, from the profile's own catalogue (`statsUiStateOf`),
 * which lives in another feature module.
 */
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        private val watchState: WatchStateRepository,
    ) : ViewModel() {
        /** This device's clock and zone: today's date for the read, and the clock every line's time is told on. */
        internal var now: () -> ZonedDateTime = { ZonedDateTime.now() }

        val state: StateFlow<StatsRead> =
            watchState.chosenProfileId
                .flatMapLatest { id -> if (id == null) flowOf(StatsRead.Loading) else readOf(id) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), StatsRead.Loading)

        private fun readOf(profileId: String): Flow<StatsRead> =
            flow {
                emit(StatsRead.Loading)
                val read =
                    try {
                        val at = now()
                        StatsRead.Done(coreProvider.awaitCore().stats(profileId, at.toLocalDate().toString()), at)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        // stats() itself never throws (a storage failure answers an
                        // empty summary); this is the core not being reachable at all.
                        Log.w(TAG, "stats: ${e.message}")
                        StatsRead.Failed(e.message)
                    }
                emit(read)
            }

        private companion object {
            const val TAG = "stats"
        }
    }
```

- [ ] **Step 4: Run, expect PASS**

Run: `cd android && ./gradlew -q :feature:stats:testDebugUnitTest`
Expected: green, 17 tests (12 + `StatsViewModelTest` 5).

- [ ] **Step 5: Commit**

```bash
git add android/feature/stats
git commit -m "feat(android): StatsViewModel reads the chosen profile's stats"
```

## Task 4.5: The page on both surfaces (not reachable yet)

**Files:**
- Modify: `ui-common/build.gradle.kts`, `ui-mobile/build.gradle.kts` (deps :13-24), `ui-tv/build.gradle.kts` (deps :19-35). Add `implementation(project(":feature:stats"))` after `:feature:system` (ui-common: after `:feature:setup`).
- Create: `ui-common/src/main/kotlin/ui/StatsBars.kt`
- Create: `ui-mobile/src/main/kotlin/ui/catalog/StatsScreen.kt`
- Create: `ui-tv/src/main/kotlin/ui/tv/catalog/TvStatsPage.kt`
- Create: `ui-mobile/src/test/kotlin/ui/catalog/StatsScreenTest.kt`
- Create: `ui-tv/src/test/kotlin/ui/tv/catalog/TvStatsPageTest.kt`

**Interfaces:**
- Consumes: `StatsUiState`, `StatsBar`, `StatsLine`, `NOTHING_WATCHED`.
- Produces: `ui.StatsBars(bars: List<StatsBar>, color: Color, labelStyle: TextStyle?, modifier: Modifier = Modifier, height: Dp = 96.dp)`, `internal fun ui.catalog.StatsScreen(state: StatsUiState)`, `internal fun ui.tv.catalog.TvStatsPage(state: StatsUiState)`.

- [ ] **Step 1: Failing tests**

`ui-mobile/src/test/kotlin/ui/catalog/StatsScreenTest.kt`:
```kotlin
package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import stats.StatsBar
import stats.StatsLine
import stats.StatsUiState

/** [StatsScreen] rendered straight from a state; the strings themselves are `feature:stats`' and tested there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(state: StatsUiState) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { StatsScreen(state) } }
        }
        compose.waitForIdle()
    }

    private val ready =
        StatsUiState.Ready(
            totals = listOf("This week" to "42 min", "This month" to "3 h 12 min", "All time" to "12 h"),
            bars = List(30) { StatsBar(fraction = if (it == 29) 1f else 0f, initial = listOf("M", "T", "W", "T", "F", "S", "S")[it % 7], description = "day $it") },
            history =
                listOf(
                    StatsLine("STARTED:f1", "Started · Der Pate · today 21:14 · 42 min"),
                    StatsLine("FINISHED:gone", "Finished · No longer in the library · 19 Sep"),
                ),
        )

    @Test
    fun nothingWatchedSaysSoUnderTheHeading() {
        show(StatsUiState.Empty)
        compose.onNodeWithText("Stats").assertIsDisplayed()
        compose.onNodeWithText("Nothing watched yet.").assertIsDisplayed()
        compose.onNodeWithText("This week").assertDoesNotExist()
        compose.onNodeWithText("History").assertDoesNotExist()
    }

    @Test
    fun aReadyPageShowsTheTotalsTheChartAndEveryLine() {
        show(ready)
        compose.onNodeWithText("This month").assertIsDisplayed()
        compose.onNodeWithText("3 h 12 min").assertIsDisplayed()
        compose.onNodeWithText("Last 30 days").assertIsDisplayed()
        compose.onNodeWithContentDescription("day 29").assertExists()
        compose.onNodeWithText("Finished · No longer in the library · 19 Sep").assertExists()
    }

    @Test
    fun aCompactScreenLeavesTheWeekdayLettersOut() {
        show(ready)
        compose.onAllNodesWithText("M").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w1164dp-h777dp")
    fun anExpandedScreenLettersEachDay() {
        show(ready)
        compose.onAllNodesWithText("M").assertCountEquals(5)
    }
}
```

`ui-tv/src/test/kotlin/ui/tv/catalog/TvStatsPageTest.kt`:
```kotlin
package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stats.StatsBar
import stats.StatsLine
import stats.StatsUiState

/** [TvStatsPage] with real D-pad keys, in the harness every catalogue page's own test uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvStatsPageTest : TvScreenStateTest() {
    private fun line(n: Int) = "Started · Film $n · today 21:14 · 42 min"

    private fun ready(lines: Int) =
        StatsUiState.Ready(
            totals = listOf("This week" to "42 min", "This month" to "3 h 12 min", "All time" to "12 h"),
            bars = List(30) { StatsBar(fraction = if (it == 29) 1f else 0f, initial = "S", description = "day $it") },
            history = List(lines) { StatsLine(key = "STARTED:f$it", text = line(it)) },
        )

    @Test
    fun nothingWatchedSaysSoUnderTheHeading() {
        show { TvStatsPage(StatsUiState.Empty) }
        compose.onNodeWithText("Stats").assertExists()
        compose.onNodeWithText("Nothing watched yet.").assertExists()
        compose.onNodeWithText("This week").assertDoesNotExist()
        compose.onNodeWithText("History").assertDoesNotExist()
    }

    @Test
    fun aFailedReadSaysSo() {
        show { TvStatsPage(StatsUiState.Failed("Could not read your stats: database is locked")) }
        compose.onNodeWithText("Could not read your stats: database is locked").assertExists()
        compose.onNodeWithText("Nothing watched yet.").assertDoesNotExist()
    }

    @Test
    fun theTotalsTakeTheRemoteOnArrival() {
        show { TvStatsPage(ready(lines = 3)) }
        compose.onNode(hasText("This week")).assertIsFocused()
        compose.onNodeWithText("3 h 12 min").assertExists()
        compose.onNodeWithContentDescription("day 29").assertExists()
    }

    @Test
    fun aLongHistoryIsComposedLazilyAndTheRemoteWalksDownIt() {
        show { TvStatsPage(ready(lines = 500)) }
        compose.onNodeWithText(line(499)).assertDoesNotExist()

        // Totals, then the chart, then one press per history line.
        repeat(12) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) } }

        compose.onNodeWithText(line(10)).assertIsFocused().assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :ui-mobile:testDebugUnitTest --tests ui.catalog.StatsScreenTest :ui-tv:testDebugUnitTest --tests ui.tv.catalog.TvStatsPageTest`
Expected: FAIL with unresolved `StatsScreen`, `TvStatsPage`.

- [ ] **Step 3: Implement**

`ui-common/src/main/kotlin/ui/StatsBars.kt`:
```kotlin
package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import stats.StatsBar

private val BarGap = 2.dp

/**
 * The Stats page's last thirty days, the same on both surfaces: one bar per
 * day, oldest left and today right, each as tall as its share of the
 * busiest day and labelled for a screen reader ("3 Oct · 42 min"). Thirty
 * bottom-aligned boxes in a row are the whole chart; no chart library.
 * [labelStyle] draws each day's weekday letter under its bar, and `null`
 * leaves the letters out where thirty would not fit.
 */
@Composable
fun StatsBars(
    bars: List<StatsBar>,
    color: Color,
    labelStyle: TextStyle?,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(BarGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (bar in bars) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(bar.fraction)
                            .background(color)
                            .semantics { contentDescription = bar.description },
                )
            }
        }
        if (labelStyle != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(BarGap)) {
                for (bar in bars) {
                    BasicText(text = bar.initial, style = labelStyle.copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
```

`ui-mobile/src/main/kotlin/ui/catalog/StatsScreen.kt`:
```kotlin
package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowWidthSizeClass
import designsystem.Spacing
import stats.NOTHING_WATCHED
import stats.StatsUiState
import ui.StatsBars

/**
 * The chosen profile's own watch time, the web's Stats page: this week,
 * this month and all time, a bar per day for the last thirty, then every
 * start, finish and rewatch, newest first. Every string arrives finished
 * from `feature:stats`, so the television's page cannot word a line
 * differently. The weekday letters under the bars need an EXPANDED width.
 */
@Composable
internal fun StatsScreen(state: StatsUiState) {
    val wide = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "heading") { Text(text = "Stats", style = MaterialTheme.typography.headlineSmall) }
        when (state) {
            StatsUiState.Loading -> Unit
            is StatsUiState.Failed -> item(key = "failed") { Text(text = state.text, color = quiet) }
            StatsUiState.Empty -> item(key = "empty") { Text(text = NOTHING_WATCHED, color = quiet) }
            is StatsUiState.Ready -> {
                item(key = "totals") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                        for ((label, value) in state.totals) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = label, style = MaterialTheme.typography.labelMedium, color = quiet)
                                Text(text = value, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
                item(key = "chart") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        Text(text = "Last 30 days", style = MaterialTheme.typography.titleMedium)
                        StatsBars(
                            bars = state.bars,
                            color = MaterialTheme.colorScheme.primary,
                            labelStyle = if (wide) MaterialTheme.typography.labelSmall.copy(color = quiet) else null,
                        )
                    }
                }
                item(key = "history") { Text(text = "History", style = MaterialTheme.typography.titleMedium) }
                items(items = state.history, key = { it.key }) { line ->
                    Text(text = line.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
```

`ui-tv/src/main/kotlin/ui/tv/catalog/TvStatsPage.kt`:
```kotlin
package ui.tv.catalog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import stats.NOTHING_WATCHED
import stats.StatsUiState
import ui.StatsBars
import ui.tv.TvFocus

private val ChartHeight = 120.dp

/**
 * The chosen profile's own watch time on a television: the phone's
 * `StatsScreen` at a television's sizes, with the same strings
 * `feature:stats` hands both. Nothing on the page opens anything, but the
 * remote still needs somewhere to rest and stepping is what scrolls: the
 * totals, the chart and each history line are stops of their own, and the
 * totals take the remote on arrival. The history is a lazy list, so a
 * profile with years of it composes only what is on screen.
 */
@Composable
internal fun TvStatsPage(state: StatsUiState) {
    val arrival = remember { FocusRequester() }
    val ready = state is StatsUiState.Ready
    LaunchedEffect(ready) { if (ready) runCatching { arrival.requestFocus() } }
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "heading") { Text(text = "Stats", style = TvTypeScale.title) }
        when (state) {
            StatsUiState.Loading -> Unit
            is StatsUiState.Failed -> item(key = "failed") { TvQuietLine(state.text) }
            StatsUiState.Empty -> item(key = "empty") { TvQuietLine(NOTHING_WATCHED) }
            is StatsUiState.Ready -> {
                item(key = "totals") {
                    TvStatsStop(Modifier.focusRequester(arrival)) { focused ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge)) {
                            for ((label, value) in state.totals) {
                                Column {
                                    Text(text = label, style = TvFocus.textStyle(TvTypeScale.body, focused))
                                    Text(text = value, style = TvTypeScale.title)
                                }
                            }
                        }
                    }
                }
                item(key = "chart") {
                    TvStatsStop { focused ->
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                            Text(text = "Last 30 days", style = TvFocus.textStyle(TvTypeScale.body, focused))
                            StatsBars(
                                bars = state.bars,
                                color = MaterialTheme.colorScheme.primary,
                                labelStyle = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow, color = quiet),
                                height = ChartHeight,
                            )
                        }
                    }
                }
                item(key = "history") { Text(text = "History", style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
                items(items = state.history, key = { it.key }) { line ->
                    TvStatsStop { focused -> Text(text = line.text, style = TvFocus.textStyle(TvTypeScale.body, focused)) }
                }
            }
        }
    }
}

/** One stop for the remote on a page with nothing to press: focusable, read as one, and told whether it holds focus so it can wear the focus treatment. */
@Composable
private fun TvStatsStop(
    modifier: Modifier = Modifier,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier = modifier.onFocusChanged { focused = it.isFocused }.focusable().semantics(mergeDescendants = true) {}) {
        content(focused)
    }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `cd android && ./gradlew -q :ui-common:testDebugUnitTest :ui-mobile:testDebugUnitTest --tests ui.catalog.StatsScreenTest :ui-tv:testDebugUnitTest --tests ui.tv.catalog.TvStatsPageTest`
Expected: green, `StatsScreenTest` 4 and `TvStatsPageTest` 4.

If the D-pad walk test lands one row off, Robolectric's focus search may have skipped the chart stop. Check the press count against the comment's order (totals → chart → line 0). Don't loosen the laziness assert.

- [ ] **Step 5: Commit**

```bash
git add android/ui-common android/ui-mobile android/ui-tv
git commit -m "feat(android): Stats page for phone and TV"
```

## Task 4.6: "Stats" on the rail, phone and TV

All of this lands in one commit. Adding `RailItem.STATS` and `FrameKind.STATS` breaks the exhaustive `when`s on both surfaces until both are wired:
- `ui-mobile/.../ui/AppChrome.kt:175-182`
- `ui-tv/.../tv/catalog/TvCatalogScreen.kt:149-158`
- `ui-mobile/.../ui/LibraryFlowBranches.kt:134-216`
- `ui-tv/.../tv/TvLibrary.kt:109-151`

**Files:**
- Create: `core/designsystem/src/main/res/drawable/core_designsystem_ic_rail_stats.xml`
- Create: `ui-common/src/main/kotlin/ui/RememberStatsPage.kt`
- Modify (shared):
  - `ui-common/src/main/kotlin/ui/RailItem.kt:5-19`
  - `ui-common/src/main/kotlin/ui/LibraryPositions.kt:14-19,214-218`
  - `feature/catalog/src/main/kotlin/Destination.kt:59-66,88,113`
- Modify (phone):
  - `ui-mobile/src/main/kotlin/ui/OverflowMenu.kt:22-34,77`
  - `ui-mobile/src/main/kotlin/ui/AppChrome.kt:160-182`
  - `ui-mobile/src/main/kotlin/ui/chrome/LibraryRail.kt:116`
  - `ui-mobile/src/main/kotlin/ui/chrome/CompactLibraryHeader.kt:96` (comment)
  - `ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt:91-96,216`
- Create (phone): `ui-mobile/src/main/kotlin/ui/StatsFrame.kt`
- Modify (TV):
  - `ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryRail.kt:45,142`
  - `ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogScreen.kt:63-66,89,149-158`
  - `ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogNav.kt:22-36,94-152`
  - `ui-tv/src/main/kotlin/ui/tv/TvLibraryBranches.kt:12-14,43,65,155-158`
  - `ui-tv/src/main/kotlin/ui/tv/TvLibrary.kt:140`
- Create (TV): `ui-tv/src/main/kotlin/ui/tv/TvStatsFrame.kt`
- Tests:
  - `feature/catalog/src/test/kotlin/DestinationTest.kt`
  - `ui-common/src/test/kotlin/ui/LibraryPositionsTest.kt`
  - `ui-mobile/src/test/kotlin/ui/chrome/LibraryRailTest.kt:63`
  - `ui-mobile/src/test/kotlin/ui/catalog/browse/OverflowUtilitiesTest.kt`
  - `ui-mobile/src/test/kotlin/ui/chrome/WidthClassStateTest.kt:66`
  - `ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt:123-147`
  - `ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt:253-289`
  - Create: `ui-tv/src/test/kotlin/ui/tv/TvStatsRailTest.kt`

The fixture trap: `LibraryFlowFixture` and `TvAppFixture` hand back every `hiltViewModel()` through `models.getValue`, so an unregistered `StatsViewModel` throws `NoSuchElementException` the moment the page opens. These are the only two fixtures that reach library frames. `MobileAppFixture`, `SettingsPanesTest` and `SettingsProfileRetryTest` never open one.

**Interfaces:**
- Produces:
  - `RailItem.STATS`;
  - `Destination.Stats`;
  - `FrameKind.STATS`, `LibraryPositions.openStats()`;
  - `BrowseActions.onStats`;
  - `ui.rememberStatsPage(catalogState: CatalogUiState, viewModel: StatsViewModel = hiltViewModel()): StatsUiState`;
  - `TvCatalogScreen(onOpenStats)`, `TvCatalogRoot(onOpenStats)`;
  - `internal const val TvStatsRailKey = "rail:stats"`;
  - `internal fun StatsFrame(at, catalogState, menuActions, profileBar, browse)`, `internal fun TvStatsFrame(catalogState, leave)`.

Backwards compatibility: `FrameKind.STATS` is appended, and frames are encoded by name (`LibraryPositions.kt:36`), so a saved stack from an older build decodes unchanged. A newer stack read by an older build drops the unknown `STATS` frame rather than crashing (`decode`, `LibraryPositions.kt:47-55`).

- [ ] **Step 1: Failing tests**

`feature/catalog/src/test/kotlin/DestinationTest.kt`, new test after :74:
```kotlin
    @Test
    fun theStatsPageIsNamedAndCanBeLeft() {
        assertEquals("Stats", barTitleFor(Destination.Stats))
        assertEquals("Back", backLabelFor(Destination.Stats))
    }
```

`ui-common/src/test/kotlin/ui/LibraryPositionsTest.kt`, new test after `theGenresIndexLatestAndTheMoviesPagedShelfAreEachOneFrame` (:136-150):
```kotlin
    @Test
    fun statsIsOneFrameOverWhateverOpenedIt() {
        val at = positions()
        at.openTitle("t1")
        at.openStats()
        assertEquals(FrameKind.STATS, at.top)
        at.pop()
        assertEquals(FrameKind.TITLE, at.top)
    }
```

`ui-mobile/src/test/kotlin/ui/chrome/LibraryRailTest.kt`: replace the loop at :63-65 and add a test. Add `import kotlin.test.assertEquals`.
```kotlin
    @Test fun everyRowItsCountsAndTheTallyAreOnScreenAtOnce() {
        val labels = listOf("My List", "Continue watching", "Latest", "Genres", "Stats", "Settings", "System")
        for (label in labels) {
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        val tops = labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops, "the rail lists its rows in the web's order")
```
(The rest of the existing body is unchanged.)
```kotlin
    @Test fun statsLandsOnTheStatsPageWithTheRailStillBesideIt() {
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("Nothing watched yet.").assertIsDisplayed()
        compose.onNodeWithText("Genres").assertIsDisplayed()
    }
```

`ui-mobile/src/test/kotlin/ui/catalog/browse/OverflowUtilitiesTest.kt`, new test:
```kotlin
    @Test fun statsOpensFromItsOwnHeaderIcon() {
        tapIcon("Stats")
        compose.onNodeWithText("Nothing watched yet.").assertIsDisplayed()
    }
```

`ui-mobile/src/test/kotlin/ui/chrome/WidthClassStateTest.kt:66`:
```kotlin
    private val browse = BrowseActions({}, {}, {}, {}, {})
```

`ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt`: after the `lanCache` stub (:123), add the stub below. Then add `StatsViewModel::class.java to stats,` to `models` after `LanCacheViewModel::class.java to lanCache,`. Imports: `stats.StatsRead`, `stats.StatsViewModel`, `uniffi.mediagram_core.StatsSummary`, `java.time.ZonedDateTime`.
```kotlin
        // The Stats page resolves StatsViewModel through hiltViewModel(),
        // the same reason every entry below exists; an empty history is the
        // page a fresh profile shows.
        val stats = mockk<StatsViewModel>(relaxed = true)
        every { stats.state } returns
            MutableStateFlow<StatsRead>(
                StatsRead.Done(StatsSummary(weekSeconds = 0.0, monthSeconds = 0.0, allSeconds = 0.0, last30 = emptyList(), history = emptyList()), ZonedDateTime.now()),
            )
```

`ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt`: after `every { lanCache.state } returns lanCacheState` (:253), add the same stub with the same imports and the comment "TvStatsFrame resolves StatsViewModel through hiltViewModel(), the same reason as every entry below." Then add `StatsViewModel::class.java to stats,` after `TitlePreloadViewModel::class.java to titlePreload,` (:279).

`ui-tv/src/test/kotlin/ui/tv/TvStatsRailTest.kt`:
```kotlin
package ui.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Profile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.tv.catalog.films
import kotlin.test.assertEquals

/**
 * The rail's Stats row over the real [TvLibrary]: it sits between Genres
 * and Settings, opens the Stats page, and Back from the page puts the
 * remote back on the row that opened it, as Latest, Genres, Settings and
 * System already do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvStatsRailTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", films(2))
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Film 1")).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test
    fun statsSitsBetweenGenresAndSettingsOnTheRail() {
        val tops = listOf("Genres", "Stats", "Settings").map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun theStatsRowOpensThePageAndBackPutsTheRemoteBackOnIt() {
        press(compose.onNodeWithContentDescription("Stats"))
        compose.onNodeWithText("Nothing watched yet.").assertExists()

        back()
        compose.onNodeWithText("Nothing watched yet.").assertDoesNotExist()
        compose.onNodeWithText("Stats").assertIsFocused()
    }

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :feature:catalog:testDebugUnitTest :ui-common:testDebugUnitTest`
Expected: FAIL with unresolved `Destination.Stats`, `openStats`, `FrameKind.STATS`.

- [ ] **Step 3: Implement, shared**

`core/designsystem/src/main/res/drawable/core_designsystem_ic_rail_stats.xml`. This is the web's path verbatim (contract §6); `pathData` takes SVG path syntax, relative commands included, as the Genres icon's `l8.3,8.3` already shows.
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Rail's Stats row — the web's bars over a baseline, `index.html` rail, as a vector resource. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FF000000"
        android:strokeWidth="1.4"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M4.5 20.5h15M7 17v-4.5M12 17V7M17 17v-7.5" />
</vector>
```

`ui-common/src/main/kotlin/ui/RailItem.kt`: in the doc, "the six rows" becomes "the seven rows"; then:
```kotlin
enum class RailItem(val label: String, val icon: Int) {
    MY_LIST("My List", R.drawable.core_designsystem_ic_rail_my_list),
    CONTINUE_WATCHING("Continue watching", R.drawable.core_designsystem_ic_rail_continue),
    LATEST("Latest", R.drawable.core_designsystem_ic_rail_latest),
    GENRES("Genres", R.drawable.core_designsystem_ic_rail_genres),
    STATS("Stats", R.drawable.core_designsystem_ic_rail_stats),
    SETTINGS("Settings", R.drawable.core_designsystem_ic_rail_settings),
    SYSTEM("System", R.drawable.core_designsystem_ic_settings_system),
}
```
`CompactLibraryHeader.kt:103` and `TvLibraryChrome.kt:87` iterate `RailItem.entries`, so they gain the row and its focus requester with no edit.

`ui-common/src/main/kotlin/ui/LibraryPositions.kt`:
- in the doc at :14, "[MOVIES_PAGE] and [PRELOADS]" becomes "[MOVIES_PAGE], [PRELOADS] and [STATS]";
- :19 becomes `enum class FrameKind { PLAYER, MENU, SEARCH, GENRE, TITLE, SEASON, COLLECTION, LIST, PERSON, FRANCHISE, GENRES, LATEST, MOVIES_PAGE, PRELOADS, STATS }`;
- after `openPreloads()` (:218) add:
```kotlin
    /** The chosen profile's Stats page — the rail's Stats row. */
    fun openStats() = push(FrameKind.STATS, "")
```

`feature/catalog/src/main/kotlin/Destination.kt`: after `Preloads` (:60) add the object below. Add `Destination.Stats -> "Stats"` to `barTitleFor` and `Destination.Stats -> "Back"` to `backLabelFor`, each after its `Preloads` line.
```kotlin
    /** The chosen profile's own watch time and history — the rail's Stats row. */
    data object Stats : Destination
```

`ui-common/src/main/kotlin/ui/RememberStatsPage.kt`:
```kotlin
package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.allSetsById
import stats.StatsUiState
import stats.StatsViewModel
import stats.statsUiStateOf

/**
 * The Stats page both surfaces draw: the chosen profile's read, every set
 * named from [catalogState] — this profile's own catalogue, Kids filter
 * applied — so a title this profile cannot see is never named, as on the
 * web. Joined here because the read and the catalogue live in two feature
 * modules that may not depend on each other.
 */
@Composable
fun rememberStatsPage(
    catalogState: CatalogUiState,
    viewModel: StatsViewModel = hiltViewModel(),
): StatsUiState {
    val read by viewModel.state.collectAsStateWithLifecycle()
    val sets = remember(catalogState) { (catalogState as? CatalogUiState.Ready)?.let { allSetsById(it.shelves) } }
    return remember(read, sets) { statsUiStateOf(read, sets) }
}
```

- [ ] **Step 4: Implement, phone**

`ui-mobile/src/main/kotlin/ui/OverflowMenu.kt`:
- change the KDoc's first sentence (:22-27) to "The browsing utilities the web keeps in its own rail-nav — My List, Continue watching, Latest, Genres and Stats — …";
- add `val onStats: () -> Unit,` as the last field of `BrowseActions` (:29-34);
- after the Genres item (:77) add:
```kotlin
        DropdownMenuItem(text = { Text("Stats") }, onClick = { menuExpanded = false; browse.onStats() })
```

`ui-mobile/src/main/kotlin/ui/AppChrome.kt`:
- `railItemFor` doc becomes "Latest, Genres and Stats are the only pushed frames the rail also names; …", and it gains `Destination.Stats -> RailItem.STATS` before `else`;
- the `railSelect` doc says "seven-way branch", and the function gains `RailItem.STATS -> browse.onStats()` after `RailItem.GENRES`.

`ui-mobile/src/main/kotlin/ui/chrome/LibraryRail.kt:116`:
```kotlin
        for (item in listOf(RailItem.MY_LIST, RailItem.CONTINUE_WATCHING, RailItem.LATEST, RailItem.GENRES, RailItem.STATS, RailItem.SETTINGS)) {
```
In `CompactLibraryHeader.kt:96` the comment "all six rows" becomes "all seven rows".

`ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt`: add `onStats = at::openStats,` after `onGenres = at::openGenresIndex,` (:95), and after the `FrameKind.PRELOADS` branch (:216) add:
```kotlin
        FrameKind.STATS -> StatsFrame(at, catalogState, menuActions, profileBar, browse)
```

`ui-mobile/src/main/kotlin/ui/StatsFrame.kt`:
```kotlin
package ui

import androidx.compose.runtime.Composable
import catalog.CatalogUiState
import catalog.Destination
import ui.catalog.StatsScreen

/**
 * The Stats page as one frame of its own on [at]'s stack, opened from the
 * rail, the header's icon row or a pushed frame's ⋮ the way Latest is.
 * Kept out of `LibraryBrowseBranches.kt`, which is already past the line
 * guideline.
 */
@Composable
internal fun StatsFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
    browse: BrowseActions,
) {
    val state = rememberStatsPage(catalogState)
    LibraryBranch(Destination.Stats, menuActions, profileBar, browse, at, at::pop) {
        StatsScreen(state)
    }
}
```

- [ ] **Step 5: Implement, TV**

`ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryRail.kt:45`:
```kotlin
private val RailRowsBeforeSystem = listOf(RailItem.MY_LIST, RailItem.CONTINUE_WATCHING, RailItem.LATEST, RailItem.GENRES, RailItem.STATS, RailItem.SETTINGS)
```
At :142, "so the six rows land" becomes "so the seven rows land". Seven 44 dp rows plus the 44 dp wordmark slot, overscan and the System gap come to about 422 dp, inside 540 dp, and the rail scrolls anyway.

`ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogNav.kt`: after `TvGenresRailKey` (:26) add the key below. In the class doc, add `[TvStatsRailKey]` to the sentinel list and change "six different sentinels" to "seven".
```kotlin
/** The catalogue's restore key for "Stats was opened from the rail". */
internal const val TvStatsRailKey = "rail:stats"
```
After :97, `redirectsFocus` (:100) gains `|| backFromStatsRail`:
```kotlin
    val backFromStatsRail = restoreKey == TvStatsRailKey
```
After the Genres effect (:135-140):
```kotlin
    LaunchedEffect(backFromStatsRail, ready) {
        if (backFromStatsRail && ready) {
            chromeFocus.railRowFocus.getValue(RailItem.STATS).requestFocus()
            onEntryRestored()
        }
    }
```

`ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogScreen.kt`:
- in the KDoc (:64-65), "Settings/System/Latest/Genres now that the rail reaches all four directly" becomes "Settings/System/Latest/Genres/Stats now that the rail reaches all five directly";
- add the parameter `onOpenStats: () -> Unit = {},` after `onOpenGenresIndex` (:89);
- in `onRailSelect`, add `RailItem.STATS -> onOpenStats()` after `RailItem.GENRES -> onOpenGenresIndex()`.

`ui-tv/src/main/kotlin/ui/tv/TvLibraryBranches.kt`:
- `TvCatalogRoot` gains `onOpenStats: () -> Unit = {},` after `onOpenGenresIndex` (:43) and passes `onOpenStats = onOpenStats,` to `TvCatalogScreen` (after :65);
- import `ui.tv.catalog.TvStatsRailKey`;
- in `TvLibraryHomeFrame`, after `onOpenGenresIndex = { … }` (:155-158) add:
```kotlin
            onOpenStats = {
                restore.opened(here, TvStatsRailKey)
                at.openStats()
            },
```

`ui-tv/src/main/kotlin/ui/tv/TvLibrary.kt`, after the `FrameKind.PRELOADS` branch (:140):
```kotlin
        FrameKind.STATS -> TvStatsFrame(catalogState, leave)
```

`ui-tv/src/main/kotlin/ui/tv/TvStatsFrame.kt`:
```kotlin
package ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import catalog.CatalogUiState
import ui.rememberStatsPage
import ui.tv.catalog.TvStatsPage

/**
 * The Stats page as one frame on the library's stack, opened from the
 * rail's Stats row. Back leaves through [leave], and the catalogue puts the
 * remote back on that row (`TvStatsRailKey`). Nothing on the page opens
 * anything, so unlike `TvPreloadsFrame` it keeps no restore key of its own.
 */
@Composable
internal fun TvStatsFrame(
    catalogState: CatalogUiState,
    leave: () -> Unit,
) {
    BackHandler(onBack = leave)
    TvStatsPage(rememberStatsPage(catalogState))
}
```

- [ ] **Step 6: Run, expect PASS**

Run: `cd android && ./gradlew -q :feature:catalog:testDebugUnitTest :ui-common:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest`
Expected: green, with the new tests (`DestinationTest` +1, `LibraryPositionsTest` +1, `LibraryRailTest` +1, `OverflowUtilitiesTest` +1, `TvStatsRailTest` 2) passing beside every existing suite.

- [ ] **Step 7: Commit**

```bash
git add android/core/designsystem android/ui-common android/feature/catalog android/ui-mobile android/ui-tv
git commit -m "feat(android): Stats on the rail between Genres and Settings, phone and TV"
```

## Task 4.7: Full gate, review, tablet walk, release bump

- [ ] **Step 1: Every touched module, plus Hilt's aggregation**

Run: `cd android && ./gradlew :core:data:testDebugUnitTest :feature:player:testDebugUnitTest :feature:catalog:testDebugUnitTest :feature:stats:testDebugUnitTest :ui-common:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest :app:assembleDebug`

Expected: BUILD SUCCESSFUL. `:app:assembleDebug` proves Hilt's graph sees `StatsViewModel` through `ui-mobile`/`ui-tv`, the same way `feature:system` reaches the app (`app/build.gradle.kts:101-109`).

- [ ] **Step 2:** `scripts/check.sh` (repo root). Expected: `==> all checks passed`.
- [ ] **Step 3:** Review with the `code-reviewer` agent: this file, the five task diffs, contract §6. Each real finding gets a fix and a test; re-run Steps 1–2.
- [ ] **Step 4: Tablet walk (navigation only, "test" profile)**

```bash
adb devices                       # caad49da listed; never run an unpinned install
ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/build-android-core.sh
cd android && ANDROID_SERIAL=caad49da ./gradlew :app:installDebug
adb -s caad49da shell monkey -p com.mediagram.android -c android.intent.category.LAUNCHER 1
```

If the install fails with `INSTALL_FAILED_VERSION_DOWNGRADE`, ask the user before running `adb -s caad49da install -r -d app/build/outputs/apk/debug/app-debug.apk`, and say which build it replaces.

Walk. Tap by coordinates from a screenshot; Compose text nodes aren't clickable in uiautomator dumps.
1. Who's watching = "test". Switch through the avatar if needed, never through Settings.
2. Rail → Stats. Expected: the "Stats" heading. Either "Nothing watched yet." alone, or the totals, "Last 30 days" with 30 bars and weekday letters (the tablet is expanded), and "History". The rail's Stats row is highlighted.
3. Open a film's title page, Play, let it run about 2 minutes, then Back out.
4. Rail → Stats. Expected:
   - "This week" is at least "2 min";
   - today's bar is the rightmost and non-zero;
   - the top line reads `Started · <film> · today hh:mm · 2 min`;
   - an episode line reads `<show> S1E4`-style.
5. Screenshot both visits: `adb -s caad49da exec-out screencap -p > "$SCRATCH/stats-tablet-<n>.png"` (`$SCRATCH` = the session scratchpad).

Tell the user the test play landed in "test"'s Continue watching.

- [ ] **Step 5: TV.** Robolectric (Task 4.6) covers focus and Back. The box walk is phase 05 (Task 5.1 Step 4 / 5.2 Step 5), because reaching the self-updating release build on `192.168.0.35:5555` needs a release publish or an adb install of the release APK, with the user's go-ahead. Optional now, if `adb devices` lists the TV emulator `emulator-5554`: `cd android && ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug`, then D-pad rail → Stats → Back. Expected: the remote is back on the Stats row.

- [ ] **Step 6: Bump (minor) by pattern, read back, commit**

```bash
git fetch -q && git show origin/main:Cargo.toml | grep -m1 '^version'   # another machine may have released meanwhile
V=$(grep -m1 -oE '^version = "[0-9.]+"' Cargo.toml | grep -oE '[0-9.]+' | awk -F. '{print $1"."$2+1".0"}')
sed -i -E '0,/^version = "[0-9.]+"/s//version = "'$V'"/' Cargo.toml
sed -i -E '0,/"version": "[0-9.]+"/s//"version": "'$V'"/' web/package.json
sed -i -E 's/versionName = "[0-9.]+"/versionName = "'$V'"/' android/app/build.gradle.kts
cargo metadata -q --format-version 1 >/dev/null
grep -m1 '^version' Cargo.toml; grep '"version"' web/package.json; grep 'versionName =' android/app/build.gradle.kts
git add Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts   # plus any review fixes from Step 3
git commit -m "feat(android): Stats page on the rail for phone, tablet and TV; release $V"
```

Expected: all three manifests show the same `$V`. `versionCode` is derived and is not touched.

Docs impact: minor. Phase 05 owns `docs/**`: `feature:stats` goes into the module table (`docs/system-architecture.md:332` area), plus a changelog entry for this release.

## Rollback

- Each task is one commit; revert in reverse order (4.6 → 4.1).
- 4.6 alone reverts cleanly: a saved `STATS` frame is dropped on decode (`LibraryPositions.kt:47-55`).
- 4.1 alone reverts cleanly: it only moves phase 03's inline `LocalDate.now()` into a default argument.
- No data migration: Android stores nothing new here. The rows live in the core (phase 03).

## Risk assessment

| Risk | L × I | Mitigation |
|---|---|---|
| Robolectric D-pad walk through a `LazyColumn` lands off by one | M × M | The comment pins the stop order; fix the count, never the laziness assert |
| Hilt can't create `StatsViewModel` → crash on opening Stats | L × H | `:app:assembleDebug` in Step 1; the tablet walk opens the page |
| Stale native core on the tablet → `UnsatisfiedLinkError` at launch | M × H | `scripts/build-android-core.sh` before every install (memory) |
| A title still on this profile's shelves is called gone because the catalogue was not ready | L × M | `statsUiStateOf` stays Loading while `sets == null` (R12) |
| Seven rail rows crowd a compact phone header | L × L | The header already scrolls sideways with an edge fade (`CompactLibraryHeader.kt:93-100`) |
| `Clock` zone frozen in a singleton | — | Avoided: the day is computed per write via `LocalDate.now()` and per read via `ZonedDateTime.now()` |

## Security

- Only the chosen profile is ever read (`chosenProfileId`). A switch cancels the old read, and the shared read resets after the page is left (R1, R2), so no profile sees another's page.
- A title outside this profile's (Kids-filtered) catalogue is never named (R4). A kids profile's page reveals nothing its shelves don't.
- No new permission, network call or stored data on Android. `Log.w` carries only the platform's error message.

## Success criteria

- `scripts/check.sh` passes. `WatchStateLocalDayTest` passes 1 test and `:feature:stats` passes 17 (`StatsFormatTest` 5, `StatsUiStateTest` 7, `StatsViewModelTest` 5). The new UI tests pass: `StatsScreenTest` 4, `TvStatsPageTest` 4, `TvStatsRailTest` 2, plus 4 added to existing suites.
- Every core progress write from Android carries this device's date as of the write (`WatchStateLocalDayTest`).
- "Stats" sits between Genres and Settings on the tablet rail, the phone's header icons and the TV rail, asserted by bounds on two surfaces.
- Every string on the page matches contract §6 and the web's `stats-format.js`/`stats-page.js` word for word:
  - empty = heading + "Nothing watched yet." only;
  - failure "Could not read your stats[: reason]";
  - titles "Crime 101 S1E4";
  - no duration on a 0 s line;
  - bar labels "3 Oct · 42 min".
- On the tablet, a 2-minute play on "test" shows as `Started · <film> · today hh:mm · 2 min` with "This week" ≥ 2 min. Screenshots are kept.
- On the TV (Robolectric): Back from Stats puts the remote on the rail's Stats row, and a 500-line history composes lazily and can be walked.
- All three manifests carry one new minor version on the last commit.

## Questions for the lead

None open. Resolved 2026-10-03 by the lead's reconciliation and contract §5/§6.

One alignment beyond the list sent: names now come from this profile's own (Kids-filtered) catalogue (Key decision 3), matching phase 02 decision 15. The earlier draft had named them from the whole library.
