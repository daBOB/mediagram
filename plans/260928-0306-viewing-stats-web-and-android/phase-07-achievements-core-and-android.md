# Phase 07 — Achievements, core and Android: the Rust port, the Stats section and the rail dot

**Goal:** the Rust core works out a profile's achievements exactly as the web does — held to the
web by phase 06's `achievements.json` — from its own state rows and the installed catalog, and
hands them to Kotlin as `achievements(profile_id, today, utc_offset_minutes)`. The phone, tablet
and TV Stats pages gain the Achievements section, and the rail's Stats row wears the
new-achievement dot — on the icon, so the TV's collapsed rail shows it too — while something
earned is unseen on this device. Seen is per device, per profile, never synced; no pop-ups.

**Architecture (data flow):**

```
Kotlin ── achievements(profileId, today = LocalDate, utcOffsetMinutes = zone offset now) ──▶ api/state/achievements.rs
   state_db: profiles.kids, stats::exchange::export(...).days, rows::watched_for(...)   (phase 03's readers)
   catalog:  catalog_achievements::library_facts(library.db) — list_playable + shows::genres (index rows only)
   └─▶ state::stats::achievements::achievements(&AchievementInput) ── pure, ◀── achievements.json (web) ──▶
         uniffi Record Achievements { earned: Vec<EarnedAchievement{id, earned_at}>, next: Vec<NextAchievement{id, have, need}> }

:feature:stats
  AchievementDotViewModel: chosenProfileId ─flatMapLatest─▶ [null at once, read now, then snapshot ─debounce 15 s─▶ read] ─▶ earned ids
                           combine(earned ids, AchievementsSeen.seen) ─▶ newAchievement: StateFlow<Boolean>
  StatsViewModel.readOf:   core.stats + core.achievements ─▶ StatsRead.Done(summary, now, achievements)
  statsUiStateOf(read, sets) ─▶ StatsUiState.Ready(…, achievements = achievementsUiOf(…))   (pure)
  rememberStatsPage (ui-common, both surfaces' Stats frames): LaunchedEffect(read) ─▶ markAchievementsSeen() ─▶ AchievementsSeen.markSeen
  AchievementsSeen (@Singleton, SharedPreferences "achievements_seen": profileId → Set<id>)

phone: LibraryFlow resolves AchievementDotViewModel ─▶ LibraryBranches ─▶ RailData.newAchievement (LocalRailData)
        ─▶ LibraryRail RailRow / CompactLibraryHeader CircleIconButton: StatusDot on the icon's corner
TV:    TvLibraryHomeFrame resolves it ─▶ LocalNewAchievement ─▶ TvLibraryRail ─▶ TvIndexRow(dot = …) on the icon
```

## Context links

- Contract: [shared-contract.md](shared-contract.md) §5 (uniffi), §6 (page), §7 (achievements), §8.
  Decisions: [plan.md](plan.md) 7, 9, 11 (kids already known: `profiles.kids`) — the user's.
- Web reference (Surface Parity): [phase-06-achievements-web.md](phase-06-achievements-web.md) —
  `achievements.ts`, `achievement-rungs.ts`, `achievement-library.ts`, `stats-achievements.js`,
  `stats-dot.js`, and the two fixtures `achievements.json`, `achievement-labels.json` this phase
  runs.
- Builds on phase 03 (`state/stats.rs` with `mod calendar`, `pub(crate) mod exchange`;
  `calendar::day_number` (`pub(super)`); `stats::exchange::export(conn, id) -> (titles, days)`;
  `state::record::DayStatRow`; `rows::WatchedRow` deserializable from fixtures; `api/state/stats.rs`;
  `FakeCore.stats`) and phase 04 as revised on 2026-10-03 (`:feature:stats`: `StatsFormat.kt`
  `whenText`; `StatsUiState.kt` with `StatsRead { Loading; Failed(reason); Done(summary, now) }`,
  `StatsUiState.Ready(totals, bars, history)`, `statsUiStateOf(read, sets)` → `pageOf(…)`;
  `StatsViewModel(coreProvider, watchState)` whose `state` is a `StatsRead`; test helpers
  `StatsFixtures.kt` `Now`/`Berlin`/`ms`/`summary`/`entry`/`Catalogue`, `MainDispatcherRule`;
  `ui-common/.../ui/RememberStatsPage.kt` `rememberStatsPage(catalogState, viewModel = hiltViewModel())`,
  which both `StatsFrame` and `TvStatsFrame` call; phone `ui/catalog/StatsScreen.kt`, TV
  `ui/tv/catalog/TvStatsPage.kt` (`TvStatsStop`); `RailItem.STATS`; the relaxed `StatsViewModel`
  mocks in both fixtures, whose `state` is a `StatsRead.Done` with no history). Quoted where this
  phase edits them; anchored on text, not line numbers.
- Code read (verified 2026-10-03):
  - `crates/mediagram-core/src/catalog.rs:94-104` `list_playable`; `src/shows/genres.rs:17-48` `genres` (comma split, trimmed — the web's split); `mediagram_tmdb::posters::poster_key` (`posters.rs:164`, `tmdb-{movie|tv}-{id}`); `src/dto/summary.rs:180-194` (`poster_key_for`, the same key).
  - `src/api/store/editorial.rs:79-101` — the Android listing also merges genres fetched into this device's sidecar; achievements read the index rows only (decision below).
  - `src/api/store.rs:25` `current_dir` (`pub(super)`), `src/versions/mod.rs:39,46` `library_db`/`open_ro`; `src/api/state.rs:17` `mod collections;` (180 lines; 190 after phase 03); `src/api/mod.rs` is 199/200 — not touched.
  - `android/ui-common/src/main/kotlin/ui/RailItem.kt:12-19`; phone `ui/chrome/LibraryRail.kt:53-63` (`RailData`, `LocalRailData`), `:116-119` rows, `:152-192` `RailRow`; `ui/chrome/CompactLibraryHeader.kt:103-107` (`RailItem.entries` as `CircleIconButton`s); `ui/chrome/ChromeControls.kt:48-73` `CircleIconButton`; `ui/LibraryFlow.kt:35-73` (`Library` resolves its ViewModels, calls `LibraryBranches`); `ui/LibraryFlowBranches.kt:50-58,101-104` (`LibraryBranches`, `RailData(...)`); `ui/settings/SettingsIndex.kt:164` and `ui-tv/.../system/TvSettingsIndex.kt:132` — the 7 dp tertiary `CircleShape` dot, twice.
  - TV `ui/tv/TvIndexRow.kt:59-128` (no dot slot; `trailing` dropped when collapsed, `:111-126`); `ui/tv/chrome/TvLibraryRail.kt:45,116-127,145-170`; `ui/tv/TvLibraryBranches.kt:108-160` `TvLibraryHomeFrame` → `TvCatalogRoot`; TV rail drawn only by `TvLibraryChrome` under `TvCatalogScreen` (`TvCatalogScreen.kt:208`).
  - Fixtures resolving every `hiltViewModel()` through `models.getValue`: `ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt:126-147`, `ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt:254-289`. `MobileAppFixture` never reaches the library.
  - `android/core/designsystem/src/main/kotlin/Palette.kt:87,120` — `tertiary` is `Sage` `#A3D3A4` / `LightSage` `#2F6A35`, the web's `--held`.

## Global constraints (contract §8)

- No plan references (phase numbers, decision numbers, finding codes) in code, test names or commit messages.
- ≤ 200 lines per non-test `.rs` (`cargo test -p mediagram --test code_standards`); new Kotlin files kept under 200 too. Over-limit Kotlin files (`LibraryFlowBranches.kt` 259+) gain only the lines named here.
- Versions: all three manifests bumped by pattern (minor), once, on this phase's last commit.
- Branch `feat/viewing-stats`, worktree off `main`. Memory rules: pin every adb/gradle device command to a serial (tablet `caad49da`); device walks navigate only, never change a setting; rebuild the native core (`scripts/build-android-core.sh`) before installing — a stale `.so` crashes at launch.

**Line budget:** `state/stats/achievements.rs` 143, `state/stats/achievements/rungs.rs` 157,
`catalog_achievements.rs` 56, `api/state/achievements.rs` 80 (all compiled and clippy-clean
against phase 03's module layout); `state/stats.rs` +1, `api/state.rs` +1 (191), `lib.rs` +1.
`state/mod.rs` (199 after phase 03) is not touched — the module sits under `state::stats`, which is
also what lets it reuse `calendar::day_number`.

## Key decisions

1. **The port reuses phase 03's readers and types.** `AchievementInput.days: Vec<DayStatRow>`,
   `watched: Vec<WatchedRow>` (contract §7's `DayStatRow[]`, live watched rows) are read with
   `stats::exchange::export` and `rows::watched_for`, exactly as `Core::stats` reads them; day
   arithmetic is `calendar::day_number`. Nothing new to keep in step.
2. **Genres are the index's own `shows` rows, not this device's fetched sidecar.** The Android
   shelves also merge the sidecar (`editorial.rs:79-101`); the web has none. An achievement one
   surface shows and the other does not is exactly the drift the two are held together against,
   so both count the channel index's genres. (Deliberate, written here; Questions.)
3. **No new state is stored.** Achievements are derived per call; the only persisted thing is
   "seen", per device: `SharedPreferences` file `achievements_seen`, key = profile id, value = the
   id set the Stats page last showed (replaced whole, as the web's `localStorage` entry is).
4. **Seen is marked by the page while it is on screen** — `LaunchedEffect(read)` in
   `rememberStatsPage`, the one place both surfaces' Stats frames read the page from — not by
   `StatsViewModel`'s read, whose shared flow outlives the page by 5 s.
5. **The dot sits on the icon** (top-right corner), not in the `trailing` slot: the TV rail drops
   `trailing` and the label when collapsed but keeps the icon, so `TvIndexRow` gains one `dot`
   parameter drawn over the icon, and the collapsed row's merged description reads "Stats, New
   achievement". The phone rail, the compact header and the web put it on the same corner.
6. **One dot composable.** `designsystem.StatusDot(color, modifier, description)` replaces the
   two copies of the 7 dp tertiary circle (`SettingsIndex.kt:164`, `TvSettingsIndex.kt:132`) and
   draws the new one.
7. **Where the dot is computed.** Phone: `LibraryFlow`'s `Library`, beside the catalog's own
   ViewModel, because its rail renders in every frame (root and pushed). TV: `TvLibraryHomeFrame`,
   the only frame that draws the rail, so nothing reads while the player is up.
8. **The dot keeps the web's rhythm** (contract §9): a profile switch puts the old dot out at
   once and reads the new profile now; otherwise it reads 15 s after the last watch-state
   change — longer than the 10 s save tick, so a title playing under the phone's still-composed
   rail is not read on every position it saves.

## Deliberate differences from the web (Surface Parity: written down)

- A phone at compact/medium width reaches Stats from a pushed frame's ⋮ (phase 04). That menu
  item wears no dot; the dot is on the compact header's Stats button at the root, as the web's is
  on its always-visible rail.
- TV: every achievement line is a focus stop (`TvStatsStop`), as every history line already is —
  the remote needs somewhere to rest, and stepping is what scrolls.

## Review focus

| # | Risk | Test (task) |
|---|---|---|
| R1 | The port disagrees with the web on any rule, tie or day boundary | `achievement_fixtures_match_the_web` — all 19 cases of `achievements.json` (7.1) |
| R2 | A kids profile sees hours, a streak or a binge — earned or next | fixture "a kids profile is offered no hours…" (7.1); `a_kids_profile_earns_the_film_but_never_hours_streaks_or_binges` through the real `Core` (7.3) |
| R3 | A rewatch moves `finishedAt` later | fixture "a rewatch moves a film's finish later…" (7.1) |
| R4 | A set the library no longer holds | fixtures "…a film the library no longer holds counts for nothing", "a collection the library shrank…" (7.1); `a_set_the_catalog_will_not_play_is_not_in_the_library` (7.2) |
| R5 | `utcOffset` at a DST boundary | fixture "every finish is read at the offset passed in…" (7.1); `AchievementsUiTest.theOffsetIsTheOneInForceAtThatMomentAcrossAClockChange`, `AchievementDotViewModelTest.theReadAsksForThisDevicesDayAndOffset` (7.5, 7.6) |
| R6 | The dot misses an achievement a sync brought from another device | `AchievementDotViewModelTest.anAchievementASyncBroughtFromAnotherDeviceLightsItOnTheNextRead` (7.6) |
| R7 | The dot stays lit after the page showed it / is marked seen while the page is not on screen | `AchievementDotViewModelTest.theStatsPageShowingTheAchievementPutsItOut`, `StatsViewModelTest.markingSeenRecordsWhatThePageWasShownForItsProfile` (7.6); `StatsDotTest.openingStatsMarksWhatItShowsAsSeen`, `TvStatsDotTest.openingStatsMarksWhatItShowsAsSeen` (7.7, 7.8) |
| R8 | One profile's dot shown to another | `AchievementDotViewModelTest.aProfileSwitchReadsTheNewProfilesOwn`, `…aSwitchPutsTheLastProfilesDotOutBeforeTheNewReadLands` (7.6) |
| R9 | The collapsed TV rail hides the dot, or a screen reader never hears it | `TvIndexRowDotTest.aCollapsedRowStillShowsItsDotAndSaysItWithItsName` (7.8); `TvStatsDotTest.theCollapsedRailsStatsRowWearsADotForAnAchievementNotYetShown` (7.8) |
| R10 | A new library-level ViewModel not registered in a fixture crashes every library test | every existing `LibraryFlowFixture`/`TvAppFixture` suite stays green (7.7, 7.8) |
| R11 | Labels or progress lines drift from the web | `AchievementLabelsFixtureTest` on `achievement-labels.json` (7.5) |
| R12 | Seen lost on restart, or shared between profiles | `SharedPreferencesAchievementsSeenTest` (7.6) |
| R13 | Genres differ between surfaces | `a_film_carries_its_own_genres_and_an_episode_its_shows` reads index rows only (7.2) |
| R14 | A playing title re-reads achievements on every 10 s save | `AchievementDotViewModelTest.positionsSavedWhileATitlePlaysAreNotReadOneByOne` (7.6) |

---

## Task 7.1: The rules in the core, run against the web's fixture

**Files:**
- Create: `crates/mediagram-core/src/state/stats/achievements.rs`
- Create: `crates/mediagram-core/src/state/stats/achievements/rungs.rs`
- Modify: `crates/mediagram-core/src/state/stats.rs` (`pub mod achievements;`)
- Modify: `crates/mediagram-core/tests/shared_watch_state_fixtures.rs` (one import, one case type, one test)

**Interfaces:**
- Consumes: `state::record::DayStatRow`, `state::rows::WatchedRow` (both `Deserialize`, camelCase — phase 03), `state::stats::calendar::day_number`.
- Produces: `pub fn achievements(input: &AchievementInput) -> Achievements`; `pub struct AchievementInput { today, utc_offset_minutes: i32, kids, days: Vec<DayStatRow>, watched: Vec<WatchedRow>, library: Vec<LibraryTitle>, collections: Vec<LibraryCollection> }` (serde camelCase — the fixture's `input`); `LibraryTitle { set_id, kind: String, genres, collection: Option<String> }`; `LibraryCollection { id, set_ids }`; uniffi Records `Achievements { earned, next }`, `EarnedAchievement { id, earned_at: i64 }`, `NextAchievement { id, have: u32, need: u32 }` (also `Deserialize` — the fixture's `expect`).
- Kotlin will see `Achievements(earned: List<EarnedAchievement>, next: List<NextAchievement>)`, `EarnedAchievement(id: String, earnedAt: Long)`, `NextAchievement(id: String, have: UInt, need: UInt)` (generated and checked against this code in a scratch build).

- [ ] **Step 1: Failing test** — `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`

Add beside the other `use mediagram_core::state::…` lines:

```rust
use mediagram_core::state::stats::achievements::{AchievementInput, Achievements, achievements};
```

Append:

```rust

#[derive(Deserialize)]
struct AchievementCase {
    name: String,
    input: AchievementInput,
    expect: Achievements,
}

/// Every rule, the kids subset, the day boundaries and the order of both
/// lists, against the web's own answers.
#[test]
fn achievement_fixtures_match_the_web() {
    let Some(cases) = load::<AchievementCase>("achievements.json") else {
        return;
    };
    assert!(!cases.is_empty(), "achievements.json holds no cases");
    for case in cases {
        assert_eq!(achievements(&case.input), case.expect, "case: {}", case.name);
    }
}
```

and add achievements to the module doc's list of what this file runs (its first sentence names
the web fixtures it holds the crate to).

- [ ] **Step 2: Run, expect a compile failure**

Run: `cargo test -p mediagram-core --test shared_watch_state_fixtures achievement`
Expected: FAIL — unresolved import `mediagram_core::state::stats::achievements`.

- [ ] **Step 3: Implement** — `crates/mediagram-core/src/state/stats/achievements.rs`

```rust
//! Achievements, worked out on every read from rows this store already keeps
//! — every device's day rows and the live watched marks — and the installed
//! library; never stored or synced: an achievement is a fact about those
//! rows, so two devices holding the same rows cannot disagree about one.
//!
//! A port of `web/src/state/achievements.ts`, held to it by
//! `web/test/fixtures/watch-state/achievements.json` (see
//! `tests/shared_watch_state_fixtures.rs`).

mod rungs;

use std::collections::HashMap;

use serde::Deserialize;

use crate::state::record::DayStatRow;
use crate::state::rows::WatchedRow;
use rungs::{DAY_MS, Finish};

/// One set the library holds, as the rules read it.
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryTitle {
    pub set_id: String,
    /// `movie`, `ep`, `tut`, `doc` or `docu` — the index's own spelling.
    pub kind: String,
    /// The provider's genres; an episode carries its show's.
    pub genres: Vec<String>,
    /// The show or course it belongs to, if any.
    pub collection: Option<String>,
}

/// A show's episodes or a course's lessons, as the library holds them now.
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryCollection {
    pub id: String,
    pub set_ids: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AchievementInput {
    /// The reading engine's local date, `YYYY-MM-DD`.
    pub today: String,
    /// The reading engine's offset from UTC now, in minutes: `120` in CEST.
    pub utc_offset_minutes: i32,
    /// A kids profile is offered finishing and exploring achievements only.
    pub kids: bool,
    /// Every device's day rows.
    pub days: Vec<DayStatRow>,
    /// Live watched marks only.
    pub watched: Vec<WatchedRow>,
    pub library: Vec<LibraryTitle>,
    pub collections: Vec<LibraryCollection>,
}

/// An achievement earned, and when, in epoch milliseconds.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct EarnedAchievement {
    pub id: String,
    pub earned_at: i64,
}

/// One still to come, and how far along it is.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize, uniffi::Record)]
pub struct NextAchievement {
    pub id: String,
    pub have: u32,
    pub need: u32,
}

/// What one profile has earned, newest first, and the few closest to come.
#[derive(Debug, Clone, Default, PartialEq, Eq, Deserialize, uniffi::Record)]
pub struct Achievements {
    pub earned: Vec<EarnedAchievement>,
    pub next: Vec<NextAchievement>,
}

const FILMS: [u32; 4] = [1, 10, 50, 100];
const GENRES: [u32; 2] = [5, 10];
const DOCS: [u32; 1] = [10];
const HOURS: [u32; 3] = [10, 100, 500];
const STREAKS: [u32; 2] = [7, 30];
const BINGE: u32 = 5;
/// How many of the closest unearned achievements a page shows.
const NEXT_SHOWN: usize = 3;

pub fn achievements(input: &AchievementInput) -> Achievements {
    let offset_ms = i64::from(input.utc_offset_minutes) * 60_000;
    // A day's local midnight at the offset the reader is at now — applied to
    // every day alike, so a day from before a clock change reads an hour out.
    let midnight = move |day: i64| day * DAY_MS - offset_ms;
    let by_set: HashMap<&str, &LibraryTitle> =
        input.library.iter().map(|title| (title.set_id.as_str(), title)).collect();
    // A finish of a set the library no longer holds counts for nothing.
    let mut finishes: Vec<Finish<'_>> = input
        .watched
        .iter()
        .filter_map(|row| by_set.get(row.set_id.as_str()).map(|title| Finish { title, at: row.finished_at }))
        .collect();
    finishes.sort_by_key(|finish| finish.at);
    let times_of = |kind: &str| -> Vec<i64> {
        finishes.iter().filter(|finish| finish.title.kind == kind).map(|finish| finish.at).collect()
    };
    let finished_at: HashMap<&str, i64> =
        finishes.iter().map(|finish| (finish.title.set_id.as_str(), finish.at)).collect();

    let mut ladders = vec![
        rungs::counted("films", &FILMS, &times_of("movie")),
        rungs::counted("genres", &GENRES, &rungs::genre_arrivals(&finishes)),
        rungs::counted("docs", &DOCS, &times_of("docu")),
        rungs::whole_show(&input.collections, &finished_at),
    ];
    if !input.kids {
        let days = rungs::day_totals(&input.days);
        ladders.push(rungs::hours(&HOURS, &days, midnight));
        ladders.push(rungs::streak(&STREAKS, &days, midnight));
        ladders.push(vec![rungs::binge(BINGE, &finishes, offset_ms, midnight)]);
    }

    let mut earned = Vec::new();
    let mut next = Vec::new();
    for ladder in ladders {
        if let Some(open) = ladder.iter().find(|rung| rung.earned_at.is_none()) {
            next.push(NextAchievement { id: open.id.clone(), have: open.have, need: open.need });
        }
        earned.extend(
            ladder
                .into_iter()
                .filter_map(|rung| rung.earned_at.map(|earned_at| EarnedAchievement { id: rung.id, earned_at })),
        );
    }
    earned.sort_by(|a, b| b.earned_at.cmp(&a.earned_at).then_with(|| a.id.cmp(&b.id)));
    // Closest first: have/need compared without dividing.
    next.sort_by(|a, b| {
        let (theirs, mine) = (u64::from(b.have) * u64::from(a.need), u64::from(a.have) * u64::from(b.need));
        theirs.cmp(&mine).then_with(|| a.id.cmp(&b.id))
    });
    next.truncate(NEXT_SHOWN);
    Achievements { earned, next }
}
```

`crates/mediagram-core/src/state/stats/achievements/rungs.rs`

```rust
//! The rules behind each achievement, one ladder each — split out of
//! `achievements.rs`, which gathers them. A port of
//! `web/src/state/achievement-rungs.ts`: every function answers a [`Rung`]
//! per id — when it was earned, or `None`, and the progress towards it.

use std::collections::{BTreeMap, HashMap, HashSet};

use super::super::calendar::day_number;
use super::{LibraryCollection, LibraryTitle};
use crate::state::record::DayStatRow;

pub(super) const DAY_MS: i64 = 86_400_000;

/// One achievement of a ladder: earned at `earned_at`, or not yet.
pub(super) struct Rung {
    pub id: String,
    pub earned_at: Option<i64>,
    pub have: u32,
    pub need: u32,
}

/// A finish the library still holds.
pub(super) struct Finish<'a> {
    pub title: &'a LibraryTitle,
    pub at: i64,
}

/// One local day's seconds across every device, as days since 1970-01-01.
pub(super) struct DayTotal {
    day: i64,
    seconds: f64,
}

fn count(n: usize) -> u32 {
    u32::try_from(n).unwrap_or(u32::MAX)
}

/// Every device's rows summed per local day, oldest day first. A row whose
/// day names no date counts toward no day-based achievement.
pub(super) fn day_totals(rows: &[DayStatRow]) -> Vec<DayTotal> {
    let mut by_day: BTreeMap<i64, f64> = BTreeMap::new();
    for row in rows {
        if let Some(day) = day_number(&row.day) {
            *by_day.entry(day).or_default() += row.seconds;
        }
    }
    by_day.into_iter().map(|(day, seconds)| DayTotal { day, seconds }).collect()
}

/// Rung N of a counting ladder is earned by the Nth time in `times`, which is ascending.
pub(super) fn counted(name: &str, rungs: &[u32], times: &[i64]) -> Vec<Rung> {
    rungs
        .iter()
        .map(|&need| Rung {
            id: format!("{name}-{need}"),
            earned_at: times.get(need as usize - 1).copied(),
            have: count(times.len()),
            need,
        })
        .collect()
}

/// When each new distinct genre arrived: entry K is the finish that brought the (K+1)th.
pub(super) fn genre_arrivals(finishes: &[Finish<'_>]) -> Vec<i64> {
    let mut seen: HashSet<&str> = HashSet::new();
    let mut arrivals = Vec::new();
    for finish in finishes {
        seen.extend(finish.title.genres.iter().map(String::as_str));
        arrivals.resize(seen.len(), finish.at);
    }
    arrivals
}

/// Cumulative watching across days; a rung is earned on the day the total reaches it.
pub(super) fn hours(rungs: &[u32], days: &[DayTotal], midnight: impl Fn(i64) -> i64) -> Vec<Rung> {
    let mut total = 0.0;
    let mut reached: HashMap<u32, i64> = HashMap::new();
    for day in days {
        total += day.seconds;
        for &need in rungs {
            if total >= f64::from(need) * 3600.0 {
                reached.entry(need).or_insert_with(|| midnight(day.day));
            }
        }
    }
    let have = (total / 3600.0).floor() as u32;
    rungs
        .iter()
        .map(|&need| Rung { id: format!("hours-{need}"), earned_at: reached.get(&need).copied(), have, need })
        .collect()
}

/// Consecutive days with any watching; a rung is earned on the day completing the first such run.
pub(super) fn streak(rungs: &[u32], days: &[DayTotal], midnight: impl Fn(i64) -> i64) -> Vec<Rung> {
    let (mut run, mut longest, mut previous) = (0_u32, 0_u32, None::<i64>);
    let mut reached: HashMap<u32, i64> = HashMap::new();
    for day in days.iter().filter(|day| day.seconds > 0.0) {
        run = if previous == Some(day.day - 1) { run + 1 } else { 1 };
        previous = Some(day.day);
        longest = longest.max(run);
        for &need in rungs {
            if run == need {
                reached.entry(need).or_insert_with(|| midnight(day.day));
            }
        }
    }
    rungs
        .iter()
        .map(|&need| Rung { id: format!("streak-{need}"), earned_at: reached.get(&need).copied(), have: longest, need })
        .collect()
}

/// Episodes finished on one local day; earned on the first day reaching `need`.
pub(super) fn binge(need: u32, finishes: &[Finish<'_>], offset_ms: i64, midnight: impl Fn(i64) -> i64) -> Rung {
    let mut per_day: HashMap<i64, u32> = HashMap::new();
    let (mut most, mut first) = (0, None);
    for finish in finishes.iter().filter(|finish| finish.title.kind == "ep") {
        let day = (finish.at + offset_ms).div_euclid(DAY_MS);
        let on_day = per_day.entry(day).or_default();
        *on_day += 1;
        most = most.max(*on_day);
        if *on_day == need && first.is_none() {
            first = Some(day);
        }
    }
    Rung { id: format!("binge-{need}"), earned_at: first.map(midnight), have: most, need }
}

/// Every set of one collection finished. Earned when the first collection
/// was completed; until then, the progress of the one closest to it.
/// Nothing at all for a library with no collections.
pub(super) fn whole_show(collections: &[LibraryCollection], finished_at: &HashMap<&str, i64>) -> Vec<Rung> {
    let mut earned_at: Option<i64> = None;
    let mut best: Option<(u32, u32)> = None;
    for collection in collections {
        let need = count(collection.set_ids.len());
        if need == 0 {
            continue;
        }
        let times: Vec<i64> =
            collection.set_ids.iter().filter_map(|set_id| finished_at.get(set_id.as_str()).copied()).collect();
        let have = count(times.len());
        if have == need {
            let done = times.iter().copied().max().unwrap_or_default();
            earned_at = Some(earned_at.map_or(done, |at| at.min(done)));
        }
        // Closest first; equal shares with equal counts are equal answers.
        let closer = best.is_none_or(|(best_have, best_need)| {
            let (mine, theirs) = (u64::from(have) * u64::from(best_need), u64::from(best_have) * u64::from(need));
            mine > theirs || (mine == theirs && have > best_have)
        });
        if closer {
            best = Some((have, need));
        }
    }
    best.map(|(have, need)| Rung { id: "whole-show".into(), earned_at, have, need }).into_iter().collect()
}
```

`crates/mediagram-core/src/state/stats.rs` — above `mod calendar;` add:

```rust
pub mod achievements;
```

- [ ] **Step 4: Run, expect PASS**

```bash
cargo test -p mediagram-core --test shared_watch_state_fixtures -- --nocapture 2>&1 | grep -E "achievement_fixtures|skipping|test result"
cargo clippy -p mediagram-core --all-targets --all-features -- -D warnings
cargo test -p mediagram --test code_standards
```

Expected: `achievement_fixtures_match_the_web ... ok`, no `skipping` line (the web checkout is
present), clippy silent, code_standards green.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/state/stats.rs crates/mediagram-core/src/state/stats \
  crates/mediagram-core/tests/shared_watch_state_fixtures.rs
git commit -m "feat(core): derive achievements, held to the web by the shared fixtures"
```

---

## Task 7.2: The library the achievements count, from the installed catalog

**Files:**
- Create: `crates/mediagram-core/src/catalog_achievements.rs`
- Create: `crates/mediagram-core/src/catalog_achievements_tests.rs`
- Modify: `crates/mediagram-core/src/lib.rs` (`pub mod catalog_achievements;` after `pub mod catalog;`)

**Interfaces:**
- Consumes: `catalog::list_playable`, `shows::genres`, `mediagram_tmdb::posters::poster_key`, `mlib_spec::Kind`.
- Produces: `pub fn library_facts(conn: &Connection) -> anyhow::Result<(Vec<LibraryTitle>, Vec<LibraryCollection>)>`; `pub fn collection_of(kind: &str, show: Option<&str>) -> Option<String>` — the web's `collectionOf`: `"ep:<show>"`, `"tut:<show>"`, else `None` (an empty show is none).
- Same decisions as the web's adapter (phase 06 Task 6.2): a course's documents are not lessons; an episode with no show joins nothing; `docu` collections are neither series nor course; genres only through a provider id.

- [ ] **Step 1: Failing tests** — `crates/mediagram-core/src/catalog_achievements_tests.rs`

```rust
use rusqlite::{Connection, params};

use super::*;

/// An in-memory index at this crate's own schema.
fn index() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

/// A set with no parts, which `PLAYABLE_SQL` plays: zero parts done of zero, zero bytes of zero.
fn add(conn: &Connection, set_id: &str, kind: &str, show: Option<&str>, tmdb: Option<u64>, status: &str) {
    conn.execute(
        "INSERT INTO sets(set_id, kind, title, show, tmdb, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, ?2, ?1, ?3, ?4, 'mkv', 0, 0, ?5, 1, 3)",
        params![set_id, kind, show, tmdb, status],
    )
    .unwrap();
}

fn described(conn: &Connection, kind: &str, id: u64, genres: &str) {
    conn.execute("INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', ?1, ?2, ?3)", params![kind, id, genres])
        .unwrap();
}

fn sorted((mut library, mut collections): (Vec<LibraryTitle>, Vec<LibraryCollection>)) -> (Vec<LibraryTitle>, Vec<LibraryCollection>) {
    library.sort_by(|a, b| a.set_id.cmp(&b.set_id));
    for collection in &mut collections {
        collection.set_ids.sort();
    }
    collections.sort_by(|a, b| a.id.cmp(&b.id));
    (library, collections)
}

fn title(set_id: &str, kind: &str, genres: &[&str], collection: Option<&str>) -> LibraryTitle {
    LibraryTitle {
        set_id: set_id.into(),
        kind: kind.into(),
        genres: genres.iter().map(|genre| genre.to_string()).collect(),
        collection: collection.map(str::to_string),
    }
}

#[test]
fn a_film_carries_its_own_genres_and_an_episode_its_shows() {
    let conn = index();
    add(&conn, "heat", "movie", None, Some(949), "complete");
    add(&conn, "bb1", "ep", Some("Breaking Bad"), Some(1396), "complete");
    described(&conn, "movie", 949, "Crime, Drama");
    described(&conn, "tv", 1396, "Drama");
    let (library, _) = sorted(library_facts(&conn).unwrap());
    assert_eq!(
        library,
        vec![
            title("bb1", "ep", &["Drama"], Some("ep:Breaking Bad")),
            title("heat", "movie", &["Crime", "Drama"], None),
        ]
    );
}

#[test]
fn episodes_group_by_show_and_lessons_by_course_and_nothing_else_does() {
    let conn = index();
    for (set_id, kind, show) in [
        ("e1", "ep", Some("Dark")),
        ("e2", "ep", Some("Dark")),
        ("lone", "ep", None),
        ("l1", "tut", Some("Rust")),
        ("handout", "doc", Some("Rust")),
        ("terra", "docu", Some("Terra X")),
    ] {
        add(&conn, set_id, kind, show, None, "complete");
    }
    let (library, collections) = sorted(library_facts(&conn).unwrap());
    assert_eq!(
        collections,
        vec![
            LibraryCollection { id: "ep:Dark".into(), set_ids: vec!["e1".into(), "e2".into()] },
            LibraryCollection { id: "tut:Rust".into(), set_ids: vec!["l1".into()] },
        ]
    );
    let alone: Vec<&str> =
        library.iter().filter(|title| title.collection.is_none()).map(|title| title.set_id.as_str()).collect();
    assert_eq!(alone, vec!["handout", "lone", "terra"]);
}

#[test]
fn a_set_the_catalog_will_not_play_is_not_in_the_library() {
    let conn = index();
    add(&conn, "ready", "movie", None, None, "complete");
    add(&conn, "uploading", "movie", None, None, "pending");
    let (library, _) = library_facts(&conn).unwrap();
    assert_eq!(library.iter().map(|title| title.set_id.as_str()).collect::<Vec<_>>(), vec!["ready"]);
}

#[test]
fn the_shelves_and_the_rules_name_a_collection_alike() {
    assert_eq!(collection_of("ep", Some("Dark")), Some("ep:Dark".into()));
    assert_eq!(collection_of("tut", Some("Rust")), Some("tut:Rust".into()));
    assert_eq!(collection_of("ep", Some("")), None);
    assert_eq!(collection_of("doc", Some("Rust")), None);
    assert_eq!(collection_of("movie", None), None);
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cargo test -p mediagram-core --lib catalog_achievements`
Expected: FAIL — `catalog_achievements` is not a module of the crate.

- [ ] **Step 3: Implement** — `crates/mediagram-core/src/catalog_achievements.rs`

```rust
//! What the achievement rules need from the installed catalog: each
//! playable set's kind, genres and collection, and each show's episodes and
//! course's lessons. A port of `web/src/state/achievement-library.ts` — both
//! read the same channel index, so both surfaces count one library alike.
//!
//! Genres are the index's own `shows` rows only, not the ones this device
//! fetched into its sidecar that the Android shelves also show
//! (`api::store::editorial`): the web player has no sidecar, and an
//! achievement one surface shows and the other does not would be exactly the
//! divergence the two are held together against.

use std::collections::HashMap;

use mediagram_tmdb::posters::poster_key;
use mlib_spec::Kind;
use rusqlite::Connection;

use crate::state::stats::achievements::{LibraryCollection, LibraryTitle};

/// Every playable set as the rules read it, and the collections they form.
pub fn library_facts(conn: &Connection) -> anyhow::Result<(Vec<LibraryTitle>, Vec<LibraryCollection>)> {
    let genres = crate::shows::genres(conn)?;
    let mut members: HashMap<String, Vec<String>> = HashMap::new();
    let mut library = Vec::new();
    for set in crate::catalog::list_playable(conn)? {
        // The key the catalog files a title's provider facts under, so a
        // title counts the genres its own page shows. Only a provider id
        // has any: a course or an untagged film has none to count.
        let key = set.tmdb.map(|id| poster_key(set.kind.parse().unwrap_or(Kind::Ep), id));
        let collection = collection_of(&set.kind, set.show.as_deref());
        if let Some(id) = &collection {
            members.entry(id.clone()).or_default().push(set.set_id.clone());
        }
        library.push(LibraryTitle {
            genres: key.and_then(|key| genres.get(&key).cloned()).unwrap_or_default(),
            set_id: set.set_id,
            kind: set.kind,
            collection,
        });
    }
    let collections = members.into_iter().map(|(id, set_ids)| LibraryCollection { id, set_ids }).collect();
    Ok((library, collections))
}

/// A show's episodes or a course's lessons, by name, the way the shelves
/// group them. A course's documents are not lessons, and a set with no show
/// belongs to none: "Unknown show" is a shelf to file it on, not a series to
/// finish.
pub fn collection_of(kind: &str, show: Option<&str>) -> Option<String> {
    let show = show.filter(|show| !show.is_empty())?;
    matches!(kind, "ep" | "tut").then(|| format!("{kind}:{show}"))
}

#[cfg(test)]
#[path = "catalog_achievements_tests.rs"]
mod tests;
```

`crates/mediagram-core/src/lib.rs` — after `pub mod catalog;` add `pub mod catalog_achievements;`.

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mediagram-core --lib catalog_achievements && cargo clippy -p mediagram-core --all-targets --all-features -- -D warnings`
Expected: 4 passed; clippy silent.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/catalog_achievements.rs crates/mediagram-core/src/catalog_achievements_tests.rs \
  crates/mediagram-core/src/lib.rs
git commit -m "feat(core): read the library achievements count from the installed catalog"
```

---

## Task 7.3: `Core::achievements`, end to end

**Files:**
- Create: `crates/mediagram-core/src/api/state/achievements.rs`
- Create: `crates/mediagram-core/tests/achievements_surface.rs`
- Modify: `crates/mediagram-core/src/api/state.rs` (`mod achievements;` above `mod collections;`)

**Interfaces:**
- Produces uniffi `Core::achievements(self: Arc<Self>, profile_id: String, today: String, utc_offset_minutes: i32) -> Achievements` — never throws: a profile this device does not hold (or a store that cannot be read) answers `Achievements::default()`; no catalog, or one that cannot be read, is an empty library, so hours and streaks still count.
- `kids` comes from `profiles::list` (`profiles.kids`, schema v7 — decision 11: kids are already known); Kotlin passes no kids flag.

- [ ] **Step 1: Failing tests** — `crates/mediagram-core/tests/achievements_surface.rs` (the day rows go in through a second connection, the way a sync import leaves them; phase 03's migration created `stats_days` when the core first opened the store)

```rust
//! `Core::achievements` end to end: profiles and marks written through the
//! calls Kotlin makes, day rows as a sync import leaves them, a catalog
//! built by hand, and the answer read back across the same boundary.

use std::path::Path;
use std::sync::Arc;

use mediagram_core::api::Core;
use mediagram_core::state::stats::achievements::Achievements;
use rusqlite::{Connection, params};

const FILM: &str = "01HEAT";
const TODAY: &str = "2026-10-03";

fn core(dir: &Path) -> Arc<Core> {
    Core::new(dir.display().to_string(), 1, "test-hash".into(), "test-device".into())
}

/// `<dir>/catalog/current/library.db` with one playable film, described as Crime and Drama.
fn seed_catalog(dir: &Path) {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn.execute(
        "INSERT INTO sets(set_id, kind, title, tmdb, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'movie', 'Heat', 949, 'mkv', 0, 0, 'complete', 0, 1)",
        [FILM],
    )
    .unwrap();
    conn.execute("INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'movie', 949, 'Crime, Drama')", [])
        .unwrap();
}

/// Eight days of two hours each from the television, as a sync round leaves them.
fn seed_days(dir: &Path, profile_id: &str) {
    let conn = Connection::open(dir.join("state.db")).unwrap();
    for day in 1..=8 {
        conn.execute(
            "INSERT INTO stats_days(profile_id, day, device, seconds, updated_at) VALUES (?1, ?2, 'tv-1', 7200, 1)",
            params![profile_id, format!("2026-09-{day:02}")],
        )
        .unwrap();
    }
}

fn ids(answer: &Achievements) -> Vec<&str> {
    answer.earned.iter().map(|a| a.id.as_str()).chain(answer.next.iter().map(|a| a.id.as_str())).collect()
}

#[tokio::test]
async fn a_kids_profile_earns_the_film_but_never_hours_streaks_or_binges() {
    let dir = tempfile::tempdir().unwrap();
    seed_catalog(dir.path());
    let core = core(dir.path());
    let kid = core.clone().create_profile("Kid".into(), true).await.unwrap();
    let grown = core.clone().create_profile("Grown".into(), false).await.unwrap();
    for profile in [&kid, &grown] {
        core.clone().set_watched(profile.id.clone(), FILM.into(), true).await;
        seed_days(dir.path(), &profile.id);
    }

    let kids = core.clone().achievements(kid.id.clone(), TODAY.into(), 120).await;
    assert!(ids(&kids).contains(&"films-1"), "{kids:?}");
    assert!(!ids(&kids).iter().any(|id| ["hours-", "streak-", "binge-"].iter().any(|p| id.starts_with(p))), "{kids:?}");

    let grown = core.clone().achievements(grown.id.clone(), TODAY.into(), 120).await;
    for id in ["films-1", "hours-10", "streak-7"] {
        assert!(grown.earned.iter().any(|a| a.id == id), "{id} in {grown:?}");
    }
    // Two genres from the catalog's own row for the film.
    assert!(grown.next.iter().any(|a| a.id == "genres-5" && a.have == 2), "{grown:?}");
}

#[tokio::test]
async fn a_profile_this_device_does_not_hold_has_earned_nothing() {
    let dir = tempfile::tempdir().unwrap();
    let answer = core(dir.path()).achievements("nobody".into(), TODAY.into(), 120).await;
    assert_eq!(answer, Achievements::default());
}

#[tokio::test]
async fn with_no_catalog_installed_the_hours_still_count() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    let grown = core.clone().create_profile("Grown".into(), false).await.unwrap();
    seed_days(dir.path(), &grown.id);
    let answer = core.clone().achievements(grown.id.clone(), TODAY.into(), 120).await;
    assert!(answer.earned.iter().any(|a| a.id == "hours-10"), "{answer:?}");
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cargo test -p mediagram-core --test achievements_surface`
Expected: FAIL — `no method named 'achievements' found for struct 'Arc<Core>'`.

- [ ] **Step 3: Implement** — `crates/mediagram-core/src/api/state/achievements.rs`

```rust
//! One profile's achievements for Kotlin: this device's state rows and the
//! installed catalog, read on every call and handed to the pure rules in
//! `crate::state::stats::achievements`. Like the rest of the watch-state
//! surface nothing here throws — a profile this device does not hold, or a
//! store that cannot be read, answers as nothing earned; a catalog that
//! cannot be read, as an empty library.

use std::sync::Arc;

use rusqlite::Connection;

use crate::state::stats::achievements::{
    self, AchievementInput, Achievements, LibraryCollection, LibraryTitle,
};
use crate::state::stats::exchange;
use crate::state::{profiles, record::DayStatRow, rows};
use crate::versions::{library_db, open_ro};

use super::super::Core;
use super::super::store::current_dir;

/// A profile's kids flag, every device's day rows and its live watched marks.
type ProfileRows = (bool, Vec<DayStatRow>, Vec<rows::WatchedRow>);

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// What `profile_id` has earned and the few closest to come. `today` is
    /// this device's local date (`YYYY-MM-DD`) and `utc_offset_minutes` its
    /// offset from UTC now: the day boundaries the day-based ones fall on.
    pub async fn achievements(
        self: Arc<Self>,
        profile_id: String,
        today: String,
        utc_offset_minutes: i32,
    ) -> Achievements {
        self.blocking(move |core| {
            let Some((kids, days, watched)) = core.state_db.with(|conn| profile_rows(conn, &profile_id)).flatten()
            else {
                return Achievements::default();
            };
            let (library, collections) = installed_library(core);
            achievements::achievements(&AchievementInput {
                today,
                utc_offset_minutes,
                kids,
                days,
                watched,
                library,
                collections,
            })
        })
        .await
    }
}

/// `None` for a profile this device does not hold.
fn profile_rows(conn: &Connection, profile_id: &str) -> rusqlite::Result<Option<ProfileRows>> {
    let Some(kids) = profiles::list(conn)?.into_iter().find(|profile| profile.id == profile_id).map(|p| p.kids)
    else {
        return Ok(None);
    };
    let (_, days) = exchange::export(conn, profile_id)?;
    Ok(Some((kids, days, rows::watched_for(conn, profile_id)?)))
}

/// The installed catalog as the rules read it: empty before one is installed
/// or when it cannot be read — which still counts hours and streaks.
fn installed_library(core: &Core) -> (Vec<LibraryTitle>, Vec<LibraryCollection>) {
    let path = library_db(&current_dir(core));
    if !path.exists() {
        return Default::default();
    }
    let Ok(conn) = open_ro(&path) else {
        return Default::default();
    };
    crate::catalog_achievements::library_facts(&conn).unwrap_or_else(|err| {
        tracing::warn!(error = %err, "achievements: the catalog could not be read");
        Default::default()
    })
}
```

`crates/mediagram-core/src/api/state.rs` — above `mod collections;` add `mod achievements;`.

- [ ] **Step 4: Run every Rust gate**

```bash
cargo test -p mediagram-core
cargo clippy -p mediagram-core --all-targets --all-features -- -D warnings
cargo test -p mediagram --test code_standards
```

Expected: all green — `achievements_surface` 3 passed; clippy silent; code_standards green
(`api/state.rs` 191).

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/api/state.rs crates/mediagram-core/src/api/state/achievements.rs \
  crates/mediagram-core/tests/achievements_surface.rs
git commit -m "feat(core): a profile's achievements over the Kotlin surface"
```

---

## Task 7.4: Kotlin bindings, the fake core and the contract

**Files:**
- Modify (generated): `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`
- Modify: `android/core/testing/src/main/kotlin/testing/FakeCore.kt`
- Modify: `android/core/testing/src/main/kotlin/testing/CoreContract.kt`

**Interfaces:**
- `CoreInterface.achievements(profileId: String, today: String, utcOffsetMinutes: Int): Achievements` (suspend). Every other `CoreInterface` implementer delegates (`by FakeCore()` / `by raw`), so `FakeCore` is the only override.
- `FakeCore.achievementsByProfile: Map<String, Achievements>` (default: nothing for anyone), `FakeCore.achievementsAsked: MutableList<Triple<String, String, Int>>`.

- [ ] **Step 1: Regenerate** (also rebuilds the gitignored `.so` for every ABI)

```bash
ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh
grep -n 'suspend fun `achievements`\|data class Achievements\|data class EarnedAchievement\|data class NextAchievement' \
  android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt
```

Expected: the interface method (`profileId: kotlin.String, today: kotlin.String, utcOffsetMinutes: kotlin.Int): Achievements`) and the three records (`earnedAt: kotlin.Long`, `have`/`need: kotlin.UInt`).

- [ ] **Step 2: The fake core** — in `FakeCore`, beside phase 03's `stats` override:

```kotlin
    /** What [achievements] answers, per profile id; nothing earned and nothing to come for any other. */
    var achievementsByProfile: Map<String, Achievements> = emptyMap()

    /** Every [achievements] read, as (profile, today, offset from UTC in minutes). */
    val achievementsAsked = mutableListOf<Triple<String, String, Int>>()

    override suspend fun achievements(
        profileId: String,
        today: String,
        utcOffsetMinutes: Int,
    ): Achievements {
        achievementsAsked += Triple(profileId, today, utcOffsetMinutes)
        return achievementsByProfile[profileId] ?: Achievements(earned = emptyList(), next = emptyList())
    }
```

(import `uniffi.mediagram_core.Achievements`).

- [ ] **Step 3: The contract** — in `CoreContract`, after `aCreatedProfileIsListed`:

```kotlin
    @Test
    fun aProfileThisCoreDoesNotHoldHasEarnedNothing() {
        runBlocking {
            assertEquals(Achievements(earned = emptyList(), next = emptyList()), core().achievements("no-such-profile", "2026-10-03", 120))
        }
    }
```

(import `uniffi.mediagram_core.Achievements`; the real core answers the same — `Achievements::default()` for a profile it does not hold — so `RealCoreContractTest` on the tablet agrees.)

- [ ] **Step 4: Compile every Android module**

Run: `cd android && ./gradlew -q :core:testing:testDebugUnitTest testDebugUnitTest`
Expected: green.

- [ ] **Step 5: Commit**

```bash
git add android/core/rust/src/main/kotlin android/core/testing
git commit -m "feat(android): achievements in the Kotlin bindings and the fake core"
```

---

## Task 7.5: The names, and the page's achievements as finished strings

**Files:**
- Create: `android/feature/stats/src/main/kotlin/AchievementsUi.kt`
- Create: `android/feature/stats/src/test/kotlin/AchievementsUiTest.kt`
- Create: `android/feature/stats/src/test/kotlin/AchievementLabelsFixtureTest.kt`
- Modify: `android/feature/stats/src/main/kotlin/StatsUiState.kt` (`StatsRead.Done.achievements`; `Ready.achievements`; `statsUiStateOf` fills it)
- Modify: `android/feature/stats/build.gradle.kts` (test dependency for the fixture)

**Interfaces:**
- Produces (package `stats`): `data class AchievementsUi(earned: List<EarnedLine>, next: List<NextLine>)` with `AchievementsUi.None`; `EarnedLine(id, label, on)`; `NextLine(id, label, progress, fraction: Float)`; `achievementLabel(id)`, `progressLine(id, have: UInt, need: UInt)` (the web's, held by `achievement-labels.json`); `earnedOn(atMs, now)` = `whenText` without its clock time; `utcOffsetMinutes(at: ZonedDateTime): Int`; `achievementsUiOf(Achievements, now)`; `NEW_ACHIEVEMENT = "New achievement"`; `NO_ACHIEVEMENTS`.
- `StatsRead.Done(summary, now, achievements = NO_ACHIEVEMENTS)` and `StatsUiState.Ready(totals, bars, history, achievements = AchievementsUi.None)` — the defaults keep every phase 04 construction compiling and equal.

- [ ] **Step 1: Failing tests**

`android/feature/stats/src/test/kotlin/AchievementLabelsFixtureTest.kt`:

```kotlin
package stats

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the web's own achievement-labels.json against [achievementLabel] and
 * [progressLine]. A case that only passes after changing them does not
 * belong in the fixture — the web is authoritative, and this file exists to
 * agree with it, not redefine it.
 */
class AchievementLabelsFixtureTest {
    @Test
    fun matchesTheWebsFixtures() {
        val file = locateFixture("achievement-labels.json")
        assumeTrue("achievement-labels.json not found above this module; is the web checkout present?", file != null)
        val cases = Json.parseToJsonElement(file!!.readText()).jsonArray
        assertTrue(cases.isNotEmpty(), "achievement-labels.json holds no cases")
        for (case in cases) {
            val fields = case.jsonObject
            val id = fields.getValue("id").jsonPrimitive.content
            val have = fields.getValue("have").jsonPrimitive.int.toUInt()
            val need = fields.getValue("need").jsonPrimitive.int.toUInt()
            assertEquals(fields.getValue("label").jsonPrimitive.content, achievementLabel(id), "label: $id")
            assertEquals(fields.getValue("progress").jsonPrimitive.content, progressLine(id, have, need), "progress: $id")
        }
    }
}

private fun locateFixture(name: String): File? {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
        val candidate = File(dir, "web/test/fixtures/watch-state/$name")
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    return null
}
```

`android/feature/stats/src/test/kotlin/AchievementsUiTest.kt` (`Now` is phase 04's: Saturday 26 September 2026, 22:00 in Berlin, CEST):

```kotlin
package stats

import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.EarnedAchievement
import uniffi.mediagram_core.HistoryKind
import uniffi.mediagram_core.NextAchievement
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AchievementsUiTest {
    @Test
    fun anEarnedDayIsTheHistorysWordingWithoutItsClock() {
        assertEquals("today", earnedOn(ms(2026, 9, 26, 0, 0), Now))
        assertEquals("Wed", earnedOn(ms(2026, 9, 23, 0, 0), Now))
        assertEquals("19 Sep", earnedOn(ms(2026, 9, 19, 0, 0), Now))
        assertEquals("21 Sep 2025", earnedOn(ms(2025, 9, 21, 0, 0), Now))
    }

    @Test
    fun theOffsetIsTheOneInForceAtThatMomentAcrossAClockChange() {
        assertEquals(60, utcOffsetMinutes(Instant.parse("2026-03-29T00:59:00Z").atZone(Berlin)))
        assertEquals(120, utcOffsetMinutes(Instant.parse("2026-03-29T01:00:00Z").atZone(Berlin)))
        assertEquals(120, utcOffsetMinutes(Instant.parse("2026-10-25T00:59:00Z").atZone(Berlin)))
        assertEquals(60, utcOffsetMinutes(Instant.parse("2026-10-25T01:00:00Z").atZone(Berlin)))
    }

    @Test
    fun theSectionArrivesAsFinishedStrings() {
        val ui =
            achievementsUiOf(
                Achievements(
                    earned = listOf(EarnedAchievement(id = "films-1", earnedAt = ms(2026, 9, 26, 21, 0))),
                    next = listOf(NextAchievement(id = "films-10", have = 7u, need = 10u)),
                ),
                Now,
            )
        assertEquals(listOf(EarnedLine(id = "films-1", label = "First film", on = "today")), ui.earned)
        assertEquals(listOf(NextLine(id = "films-10", label = "10 films", progress = "7 of 10 films", fraction = 0.7f)), ui.next)
    }

    @Test
    fun nothingEarnedAndNothingToComeIsNone() {
        assertEquals(AchievementsUi.None, achievementsUiOf(NO_ACHIEVEMENTS, Now))
    }

    @Test
    fun aReadPageCarriesItsAchievementsFinished() {
        val read =
            StatsRead.Done(
                summary(all = 60.0, history = listOf(entry(HistoryKind.STARTED, "f1", ms(2026, 9, 26, 21, 0), 60.0))),
                Now,
                Achievements(earned = listOf(EarnedAchievement(id = "films-1", earnedAt = ms(2026, 9, 26, 21, 0))), next = emptyList()),
            )
        val ready = assertIs<StatsUiState.Ready>(statsUiStateOf(read, Catalogue))
        assertEquals(listOf(EarnedLine(id = "films-1", label = "First film", on = "today")), ready.achievements.earned)
    }
}
```

`android/feature/stats/build.gradle.kts` — add to `dependencies`:

```kotlin
    // AchievementLabelsFixtureTest reads the web's own achievement-labels.json
    // as plain JSON, the way core:data's ResumePointFixtureTest reads its own.
    testImplementation(libs.findLibrary("kotlinx.serialization").get())
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :feature:stats:testDebugUnitTest`
Expected: FAIL — unresolved `achievementLabel`, `earnedOn`, `utcOffsetMinutes`, `achievementsUiOf`.

- [ ] **Step 3: Implement** — `android/feature/stats/src/main/kotlin/AchievementsUi.kt`

```kotlin
package stats

import uniffi.mediagram_core.Achievements
import java.time.ZonedDateTime

/**
 * The Stats page's Achievements section, every string finished — the web's
 * `achievementsSection` (`web/public/lib/catalog/stats-achievements.js`).
 * [achievementLabel] and [progressLine] are held to that file by
 * `achievement-labels.json`.
 */
data class AchievementsUi(
    val earned: List<EarnedLine>,
    val next: List<NextLine>,
) {
    companion object {
        val None = AchievementsUi(emptyList(), emptyList())
    }
}

/** One achievement earned: its name, and the day it was earned. */
data class EarnedLine(
    val id: String,
    val label: String,
    val on: String,
)

/** One still to come: its name, how far along in words, and as a share for the bar. */
data class NextLine(
    val id: String,
    val label: String,
    val progress: String,
    val fraction: Float,
)

/** What a screen reader hears for the dot on the rail's Stats row. */
const val NEW_ACHIEVEMENT = "New achievement"

/** Nothing earned and nothing to come. */
val NO_ACHIEVEMENTS = Achievements(earned = emptyList(), next = emptyList())

private val RUNG = Regex("([a-z]+)-(\\d+)")
private val CLOCK = Regex(" \\d{2}:\\d{2}$")

/** An achievement's name. An id this build does not know shows as itself rather than vanishing. */
fun achievementLabel(id: String): String {
    if (id == "whole-show") return "A whole series"
    val (ladder, n) = RUNG.matchEntire(id)?.destructured ?: return id
    return when (ladder) {
        "films" -> if (n == "1") "First film" else "$n films"
        "genres" -> "$n genres"
        "docs" -> "$n documentaries"
        "hours" -> "$n hours"
        "streak" -> "$n-day streak"
        "binge" -> "$n episodes in a day"
        else -> id
    }
}

/**
 * How far along one still to come is: "7 of 10 films". A whole series names
 * no unit — its count is a show's episodes as often as a course's lessons.
 */
fun progressLine(
    id: String,
    have: UInt,
    need: UInt,
): String {
    val unit =
        when (RUNG.matchEntire(id)?.groupValues?.get(1)) {
            "films" -> if (need == 1u) "film" else "films"
            "genres" -> "genres"
            "docs" -> "documentaries"
            "hours" -> "hours"
            "streak" -> "days"
            "binge" -> "episodes"
            else -> null
        }
    return if (unit == null) "$have of $need" else "$have of $need $unit"
}

/**
 * The day an achievement was earned: [whenText] without its clock time. A
 * day-based achievement is dated at that day's local midnight, and "Sat
 * 00:00" would read as a moment rather than a day.
 */
fun earnedOn(
    atMs: Long,
    now: ZonedDateTime,
): String = whenText(atMs, now).replace(CLOCK, "")

/**
 * This device's offset from UTC at [at], in minutes — the one in force at
 * that moment, so a clock change is taken as it happens. What the core
 * places a day-based achievement's midnight by.
 */
fun utcOffsetMinutes(at: ZonedDateTime): Int = at.offset.totalSeconds / 60

/** The section for [achievements], every day told on [now]'s clock. */
fun achievementsUiOf(
    achievements: Achievements,
    now: ZonedDateTime,
): AchievementsUi =
    AchievementsUi(
        earned = achievements.earned.map { EarnedLine(it.id, achievementLabel(it.id), earnedOn(it.earnedAt, now)) },
        next =
            achievements.next.map {
                NextLine(it.id, achievementLabel(it.id), progressLine(it.id, it.have, it.need), it.have.toFloat() / it.need.toFloat())
            },
    )
```

`android/feature/stats/src/main/kotlin/StatsUiState.kt` (phase 04's):
- `StatsRead.Done` gains a third field, after `val now: ZonedDateTime,`:

```kotlin
        /** What the profile has earned and the closest to come, read at the same moment. */
        val achievements: Achievements = NO_ACHIEVEMENTS,
```

- in `StatsUiState.Ready`, after `val history: List<StatsLine>,` add

```kotlin
        /** What was earned and what is next; [AchievementsUi.None] draws no section. */
        val achievements: AchievementsUi = AchievementsUi.None,
```

- in `statsUiStateOf`, the `Done` branch's last arm
  `else -> pageOf(read.summary, sets, read.now)` becomes

```kotlin
                else -> pageOf(read.summary, sets, read.now).copy(achievements = achievementsUiOf(read.achievements, read.now))
```

  (import `uniffi.mediagram_core.Achievements`). An empty history is still `Empty`, section and
  all — nothing watched has earned nothing.

- [ ] **Step 4: Run, expect PASS**

Run: `cd android && ./gradlew -q :feature:stats:testDebugUnitTest`
Expected: green — phase 04's tests unchanged plus `AchievementsUiTest` 5 and `AchievementLabelsFixtureTest` 1 (11 cases inside).

- [ ] **Step 5: Commit**

```bash
git add android/feature/stats
git commit -m "feat(android): achievement names and the Stats page's achievements state"
```

---

## Task 7.6: What this device has shown, the dot, and the page marking it

**Files:**
- Create: `android/feature/stats/src/main/kotlin/AchievementsSeen.kt`
- Create: `android/feature/stats/src/main/kotlin/AchievementsSeenModule.kt`
- Create: `android/feature/stats/src/main/kotlin/AchievementDotViewModel.kt`
- Create: `android/feature/stats/src/test/kotlin/AchievementDotViewModelTest.kt`
- Create: `android/feature/stats/src/test/kotlin/SharedPreferencesAchievementsSeenTest.kt`
- Modify: `android/feature/stats/src/main/kotlin/StatsViewModel.kt` (reads achievements; `markAchievementsSeen()`)
- Modify: `android/feature/stats/src/test/kotlin/StatsViewModelTest.kt` (`model()` passes `seen`; three cases)
- Modify: `android/feature/stats/build.gradle.kts` (Robolectric for the preferences test)

**Interfaces:**
- Produces: `interface AchievementsSeen { val seen: StateFlow<Map<String, Set<String>>>; fun markSeen(profileId: String, ids: Set<String>) }`, `InMemoryAchievementsSeen`, `SharedPreferencesAchievementsSeen(context)` (file `achievements_seen`, a string set per profile id, replaced whole); Hilt `AchievementsSeenModule` (`@Singleton` — the dot's and the page's ViewModels must share one); `@HiltViewModel class AchievementDotViewModel(coreProvider, watchState, seen) { val newAchievement: StateFlow<Boolean>; internal var now }`; `StatsViewModel(coreProvider, watchState, seen)` + `fun markAchievementsSeen()` (called from `rememberStatsPage` in Task 7.7, while the page is composed).
- Freshness, as on the web (contract §9, `stats-dot.js`): a profile switch puts the dot out at once and reads the new profile now; any later `snapshot` change is read once it has been quiet for `DOT_SETTLE_MS` (15 s, longer than the 10 s save tick). A sync round reloads the snapshot (`WatchSync` → `reload()`), which is how another device's achievement arrives. Known ceiling, as on the web: a round that brings only day rows and moves no position, mark or list leaves the snapshot equal, so the dot catches up at the next change.

- [ ] **Step 1: Failing tests**

`android/feature/stats/src/test/kotlin/AchievementDotViewModelTest.kt`:

```kotlin
package stats

import data.CoreProvider
import data.DefaultWatchStateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.FakeCore
import testing.ResolvedCoreProvider
import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.EarnedAchievement
import uniffi.mediagram_core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementDotViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val core =
        FakeCore().apply {
            profiles = listOf(Profile("a", "Ada"), Profile("b", "Ben"))
            chosen = "a"
        }
    private val watch = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)
    private val seen = InMemoryAchievementsSeen()

    private fun earned(vararg ids: String) = Achievements(earned = ids.map { EarnedAchievement(id = it, earnedAt = 1L) }, next = emptyList())

    /** The dot as the rail holds it: subscribed, so the reads run. */
    private fun TestScope.dot(provider: CoreProvider = ResolvedCoreProvider(core)): StateFlow<Boolean> {
        val model = AchievementDotViewModel(provider, watch, seen).apply { now = { Now } }
        backgroundScope.launch { model.newAchievement.collect {} }
        advanceUntilIdle()
        return model.newAchievement
    }

    @Test
    fun anAchievementThisDeviceHasNotShownLightsTheDot() =
        runTest {
            core.achievementsByProfile = mapOf("a" to earned("films-1"))
            watch.reload()
            assertTrue(dot().value)
        }

    @Test
    fun whatThisDeviceHasShownLeavesItOut() =
        runTest {
            core.achievementsByProfile = mapOf("a" to earned("films-1"))
            seen.markSeen("a", setOf("films-1"))
            watch.reload()
            assertFalse(dot().value)
        }

    @Test
    fun anAchievementASyncBroughtFromAnotherDeviceLightsItOnTheNextRead() =
        runTest {
            watch.reload()
            val dot = dot()
            assertFalse(dot.value)

            // The television finished a film; the round that pulled its rows reloads the snapshot.
            core.achievementsByProfile = mapOf("a" to earned("films-1"))
            core.setWatched("a", "f1", true)
            watch.reload()
            advanceTimeBy(DOT_SETTLE_MS - 1)
            assertFalse(dot.value, "not yet: changes settle first")

            advanceTimeBy(2)
            assertTrue(dot.value)
        }

    @Test
    fun positionsSavedWhileATitlePlaysAreNotReadOneByOne() =
        runTest {
            watch.reload()
            dot()
            val before = core.achievementsAsked.size

            repeat(6) { tick ->
                watch.setProgress("f1", tick * 10.0, 3_600.0)
                advanceTimeBy(10_000)
            }
            assertEquals(before, core.achievementsAsked.size, "a save every ten seconds never lets it settle")

            advanceTimeBy(DOT_SETTLE_MS)
            assertEquals(before + 1, core.achievementsAsked.size, "one read once the title stops")
        }

    @Test
    fun theStatsPageShowingTheAchievementPutsItOut() =
        runTest {
            core.achievementsByProfile = mapOf("a" to earned("films-1"))
            watch.reload()
            val dot = dot()
            assertTrue(dot.value)

            seen.markSeen("a", setOf("films-1"))
            advanceUntilIdle()

            assertFalse(dot.value)
        }

    @Test
    fun aProfileSwitchReadsTheNewProfilesOwn() =
        runTest {
            core.achievementsByProfile = mapOf("a" to earned("films-1"), "b" to earned("films-1"))
            seen.markSeen("b", setOf("films-1"))
            watch.reload()
            val dot = dot()
            assertTrue(dot.value)

            watch.chooseProfile("b")
            advanceUntilIdle()

            assertFalse(dot.value)
        }

    @Test
    fun aSwitchPutsTheLastProfilesDotOutBeforeTheNewReadLands() =
        runTest {
            core.achievementsByProfile = mapOf("a" to earned("films-1"), "b" to earned("streak-7"))
            val bHeld = CompletableDeferred<Unit>()
            val slow =
                object : CoreInterface by core {
                    override suspend fun achievements(
                        profileId: String,
                        today: String,
                        utcOffsetMinutes: Int,
                    ): Achievements {
                        if (profileId == "b") bHeld.await()
                        return core.achievements(profileId, today, utcOffsetMinutes)
                    }
                }
            watch.reload()
            val dot = dot(ResolvedCoreProvider(slow))
            assertTrue(dot.value)

            watch.chooseProfile("b")
            advanceUntilIdle()
            assertFalse(dot.value, "Ada's dot is not Ben's, even while Ben's read is out")

            bHeld.complete(Unit)
            advanceUntilIdle()
            assertTrue(dot.value, "Ben's own unseen achievement")
        }

    @Test
    fun theReadAsksForThisDevicesDayAndOffset() =
        runTest {
            watch.reload()
            dot()
            assertEquals(Triple("a", "2026-09-26", 120), core.achievementsAsked.last())
        }
}
```

`android/feature/stats/src/test/kotlin/SharedPreferencesAchievementsSeenTest.kt`:

```kotlin
package stats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** What this device has shown, kept across a restart, profile by profile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesAchievementsSeenTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun whatWasShownSurvivesARestartAndIsKeptPerProfile() {
        val first = SharedPreferencesAchievementsSeen(context)
        first.markSeen("ada", setOf("films-1", "genres-5"))
        first.markSeen("ben", setOf("films-1"))

        assertEquals(
            mapOf("ada" to setOf("films-1", "genres-5"), "ben" to setOf("films-1")),
            SharedPreferencesAchievementsSeen(context).seen.value,
        )
    }

    @Test
    fun markingAgainReplacesWhatThatProfileHadShown() {
        val seen = SharedPreferencesAchievementsSeen(context)
        seen.markSeen("ada", setOf("films-1"))
        seen.markSeen("ada", setOf("films-1", "streak-7"))

        assertEquals(setOf("films-1", "streak-7"), SharedPreferencesAchievementsSeen(context).seen.value["ada"])
    }
}
```

`android/feature/stats/src/test/kotlin/StatsViewModelTest.kt` (phase 04's) — add a property
`private val seen = InMemoryAchievementsSeen()` beside `watch`, and pass it as the third
argument in both constructions:

```kotlin
    private fun model() = StatsViewModel(ResolvedCoreProvider(core), watch, seen).apply { now = { Now } }
```

and, in `aCoreThatCannotBeReachedFailsWithItsReason`,
`StatsViewModel(FakeCoreProvider(null), watch, seen).apply { now = { Now } }`.

and append (imports `uniffi.mediagram_core.Achievements`, `EarnedAchievement`, `NextAchievement`,
`kotlin.test.assertEquals` if not already there):

```kotlin
    @Test
    fun theChosenProfilesAchievementsArriveWithItsStats() =
        runTest {
            raw.achievementsByProfile =
                mapOf(
                    "a" to
                        Achievements(
                            earned = listOf(EarnedAchievement(id = "films-1", earnedAt = ms(2026, 9, 26, 21, 0))),
                            next = listOf(NextAchievement(id = "films-10", have = 1u, need = 10u)),
                        ),
                )
            watch.reload()

            val done = assertIs<StatsRead.Done>(model().state.first { it != StatsRead.Loading })

            assertEquals(listOf("films-1"), done.achievements.earned.map { it.id })
            assertEquals(listOf("films-10"), done.achievements.next.map { it.id })
            assertEquals(Triple("a", "2026-09-26", 120), raw.achievementsAsked.last())
        }

    @Test
    fun markingSeenRecordsWhatThePageWasShownForItsProfile() =
        runTest {
            raw.achievementsByProfile =
                mapOf("a" to Achievements(earned = listOf(EarnedAchievement("films-1", 1L), EarnedAchievement("genres-5", 2L)), next = emptyList()))
            watch.reload()
            val model = model()
            model.state.first { it is StatsRead.Done }

            model.markAchievementsSeen()

            assertEquals(mapOf("a" to setOf("films-1", "genres-5")), seen.seen.value)
        }

    @Test
    fun markingSeenBeforeAnythingWasReadRecordsNothing() {
        model().markAchievementsSeen()
        assertEquals(emptyMap(), seen.seen.value)
    }
```

`android/feature/stats/build.gradle.kts` — add to `dependencies`:

```kotlin
    // SharedPreferencesAchievementsSeenTest opens a real SharedPreferences
    // file; the plain unit-test android.jar stub has none, so it runs under
    // Robolectric. androidx-junit brings ApplicationProvider.
    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("androidx.junit").get())
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :feature:stats:testDebugUnitTest`
Expected: FAIL — unresolved `InMemoryAchievementsSeen`, `AchievementDotViewModel`, `SharedPreferencesAchievementsSeen`, `markAchievementsSeen`; `StatsViewModel` takes three arguments.

- [ ] **Step 3: Implement**

`android/feature/stats/src/main/kotlin/AchievementsSeen.kt`:

```kotlin
package stats

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which achievements this device has shown each profile — the rail's dot
 * lights for anything earned that is not in here. Per device and never
 * synced, as on the web (`localStorage` `mediagram.stats-seen.<profileId>`):
 * a badge earned on the television is still news on the tablet.
 */
interface AchievementsSeen {
    /** Profile id to the ids this device has shown it. */
    val seen: StateFlow<Map<String, Set<String>>>

    /** What the Stats page is showing [profileId] becomes what this device has shown it. */
    fun markSeen(
        profileId: String,
        ids: Set<String>,
    )
}

/** In memory, for tests. */
class InMemoryAchievementsSeen : AchievementsSeen {
    private val held = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    override val seen: StateFlow<Map<String, Set<String>>> = held.asStateFlow()

    override fun markSeen(
        profileId: String,
        ids: Set<String>,
    ) = held.update { it + (profileId to ids) }
}

/**
 * One plain preferences file, a string set per profile id. Not the encrypted
 * store: which badges a screen has shown is no secret, and a keystore
 * failure must not be able to light every dot again.
 */
class SharedPreferencesAchievementsSeen(
    context: Context,
) : AchievementsSeen {
    private val preferences = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    private val held =
        MutableStateFlow(
            preferences.all
                .mapNotNull { (profileId, ids) -> (ids as? Set<*>)?.let { profileId to it.filterIsInstance<String>().toSet() } }
                .toMap(),
        )
    override val seen: StateFlow<Map<String, Set<String>>> = held.asStateFlow()

    override fun markSeen(
        profileId: String,
        ids: Set<String>,
    ) {
        held.update { it + (profileId to ids) }
        preferences.edit().putStringSet(profileId, ids).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "achievements_seen"
    }
}
```

`android/feature/stats/src/main/kotlin/AchievementsSeenModule.kt`:

```kotlin
package stats

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * One [AchievementsSeen] for the process: the rail's dot and the Stats page
 * each have a ViewModel of their own, and the page marking an achievement
 * seen must put out the dot the rail is showing.
 */
@Module
@InstallIn(SingletonComponent::class)
object AchievementsSeenModule {
    @Provides
    @Singleton
    fun provideAchievementsSeen(
        @ApplicationContext context: Context,
    ): AchievementsSeen = SharedPreferencesAchievementsSeen(context)
}
```

`android/feature/stats/src/main/kotlin/AchievementDotViewModel.kt`:

```kotlin
package stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CoreProvider
import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * How long the dot waits after a watch-state change before reading again —
 * longer than the player's ten-second save tick, so a title playing under
 * the phone's still-composed rail is not read on every position it saves.
 * The web's dot keeps the same rhythm (`stats-dot.js`).
 */
internal const val DOT_SETTLE_MS = 15_000L

/**
 * Whether the rail's Stats row wears the new-achievement dot: something the
 * chosen profile has earned that this device has not shown it yet.
 *
 * Read at once when a profile is chosen — the last profile's dot goes out
 * before the answer arrives — and again once the profile's watch state has
 * been quiet for [DOT_SETTLE_MS] after a change: a write here, or another
 * device's rows pulled in by a sync round, which is how an achievement
 * earned on the television lights the tablet's dot. Never a pop-up: the dot
 * is all of it.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AchievementDotViewModel
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        watchState: WatchStateRepository,
        seen: AchievementsSeen,
    ) : ViewModel() {
        /** This device's clock and zone: the day and offset the core places a day-based achievement by. */
        internal var now: () -> ZonedDateTime = { ZonedDateTime.now() }

        /** The chosen profile and what it has earned; `null` while there is no answer for it yet. */
        private val earned: Flow<Pair<String, Set<String>>?> =
            watchState.chosenProfileId.flatMapLatest { id ->
                if (id == null) {
                    flowOf(null)
                } else {
                    flow {
                        emit(null)
                        emit(id to earnedIds(id))
                        // The snapshot as it stands was just read; only what changes after it counts.
                        watchState.snapshot.drop(1).debounce(DOT_SETTLE_MS).collect { emit(id to earnedIds(id)) }
                    }
                }
            }

        val newAchievement: StateFlow<Boolean> =
            combine(earned, seen.seen) { found, shown ->
                found != null && !shown[found.first].orEmpty().containsAll(found.second)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        private suspend fun earnedIds(profileId: String): Set<String> {
            val at = now()
            return try {
                coreProvider
                    .awaitCore()
                    .achievements(profileId, at.toLocalDate().toString(), utcOffsetMinutes(at))
                    .earned
                    .mapTo(HashSet()) { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w(TAG, "achievements: ${e.message}")
                emptySet()
            }
        }

        private companion object {
            const val TAG = "stats"
        }
    }
```

`android/feature/stats/src/main/kotlin/StatsViewModel.kt` (phase 04's):
- constructor: after `private val watchState: WatchStateRepository,` add `private val seen: AchievementsSeen,`
- after the `now` property add:

```kotlin
        /** The profile the page last read, and the ids it was shown — what [markAchievementsSeen] records. */
        private var shown: Pair<String, Set<String>>? = null

        /**
         * What the page shows now becomes what this device has shown: the
         * rail's dot goes out. Called by the page while it is on screen —
         * not by the read, whose shared flow outlives the page by a few
         * seconds.
         */
        fun markAchievementsSeen() {
            shown?.let { (profileId, ids) -> seen.markSeen(profileId, ids) }
        }
```

- in `readOf`, the `try` body

```kotlin
                        val at = now()
                        StatsRead.Done(coreProvider.awaitCore().stats(profileId, at.toLocalDate().toString()), at)
```

  becomes

```kotlin
                        val at = now()
                        val today = at.toLocalDate().toString()
                        val core = coreProvider.awaitCore()
                        val summary = core.stats(profileId, today)
                        val achievements = core.achievements(profileId, today, utcOffsetMinutes(at))
                        shown = profileId to achievements.earned.mapTo(HashSet()) { it.id }
                        StatsRead.Done(summary, at, achievements)
```

  (a read cancelled by a profile switch never reaches the `shown` line, so it cannot mark
  the profile that was left).

- [ ] **Step 4: Run, expect PASS**

Run: `cd android && ./gradlew -q :feature:stats:testDebugUnitTest`
Expected: green — `AchievementDotViewModelTest` 8, `SharedPreferencesAchievementsSeenTest` 2, `StatsViewModelTest` phase 04's 5 + 3.

- [ ] **Step 5: Commit**

```bash
git add android/feature/stats
git commit -m "feat(android): remember which achievements this device has shown, and light the dot for the rest"
```

---

## Task 7.7: The phone — the shared dot, the rail and header, the section, and the page marking it

**Files:**
- Create: `android/core/designsystem/src/main/kotlin/StatusDot.kt`
- Create: `android/ui-mobile/src/main/kotlin/ui/catalog/AchievementItems.kt`
- Create: `android/ui-mobile/src/test/kotlin/ui/catalog/AchievementItemsTest.kt`
- Create: `android/ui-mobile/src/test/kotlin/ui/chrome/StatsDotTest.kt`
- Modify: `android/ui-mobile/src/main/kotlin/ui/settings/SettingsIndex.kt` (`:164`, the held dot)
- Modify: `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsIndex.kt` (`:132`, the held dot)
- Modify: `android/ui-mobile/src/main/kotlin/ui/chrome/LibraryRail.kt` (`RailData.newAchievement`, `dotFor`, `RailRow`)
- Modify: `android/ui-mobile/src/main/kotlin/ui/chrome/ChromeControls.kt` (`CircleIconButton(dot)`)
- Modify: `android/ui-mobile/src/main/kotlin/ui/chrome/CompactLibraryHeader.kt` (pass the dot)
- Modify: `android/ui-mobile/src/main/kotlin/ui/LibraryFlow.kt` (resolve the dot's ViewModel)
- Modify: `android/ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt` (+1 line: the parameter; `RailData(...)`)
- Modify: `android/ui-mobile/src/main/kotlin/ui/catalog/StatsScreen.kt` (the section)
- Modify: `android/ui-common/src/main/kotlin/ui/RememberStatsPage.kt` (mark seen while the page is composed — both surfaces)
- Modify: `android/ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt` (register the dot's ViewModel; `achievements` parameter; `stats` and `achievementsSeen` properties)

**Interfaces:**
- Produces: `designsystem.StatusDot(color: Color, modifier: Modifier = Modifier, description: String? = null)`; `RailData(…, newAchievement: Boolean = false)`; `RailData.dotFor(item): String?`; `CircleIconButton(…, dot: String? = null)`; `LazyListScope.achievementItems(AchievementsUi)`; `LibraryBranches(…, newAchievement: Boolean)`; `LibraryFlowFixture(…, achievements: Achievements = NO_ACHIEVEMENTS)` with `val stats`, `val achievementsSeen`.

- [ ] **Step 1: Failing tests**

`android/ui-mobile/src/test/kotlin/ui/catalog/AchievementItemsTest.kt`:

```kotlin
package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import stats.AchievementsUi
import stats.EarnedLine
import stats.NextLine
import stats.StatsBar
import stats.StatsLine
import stats.StatsUiState

/** The Achievements section on [StatsScreen], drawn straight from a state; its words are `feature:stats`' and tested there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AchievementItemsTest {
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
            totals = listOf("This week" to "42 min", "This month" to "42 min", "All time" to "42 min"),
            bars = List(30) { StatsBar(fraction = 0f, initial = "M", description = "day $it") },
            history = listOf(StatsLine("STARTED:f1", "Started · Der Pate · today 21:14 · 42 min")),
        )

    private fun seen(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun earnedAchievementsShowTheirDayAndTheNextHowFarAlong() {
        show(
            ready.copy(
                achievements =
                    AchievementsUi(
                        earned = listOf(EarnedLine(id = "films-1", label = "First film", on = "today")),
                        next = listOf(NextLine(id = "films-10", label = "10 films", progress = "1 of 10 films", fraction = 0.1f)),
                    ),
            ),
        )
        for (text in listOf("Achievements", "First film", "today", "Next", "10 films", "1 of 10 films", "History")) seen(text)
    }

    @Test
    fun nothingEarnedAndNothingToComeDrawsNoSection() {
        show(ready)
        compose.onNodeWithText("Achievements").assertDoesNotExist()
    }
}
```

`android/ui-mobile/src/test/kotlin/ui/chrome/StatsDotTest.kt`:

```kotlin
package ui.chrome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import stats.NEW_ACHIEVEMENT
import stats.NO_ACHIEVEMENTS
import ui.LibraryFlowFixture
import ui.LibraryFlowTestActivity
import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.EarnedAchievement

/**
 * The new-achievement dot over the real library flow: on the tablet rail's
 * Stats row, on the compact header's Stats button, out once shown, and the
 * Stats page marking what it shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1164dp-h777dp")
class StatsDotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: LibraryFlowFixture
    private lateinit var controller: ActivityController<LibraryFlowTestActivity>

    private fun earned(vararg ids: String) = Achievements(earned = ids.map { EarnedAchievement(id = it, earnedAt = 1L) }, next = emptyList())

    private fun open(achievements: Achievements) {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = LibraryFlowFixture(achievements = achievements)
            LibraryFlowTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(LibraryFlowTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
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
    fun theRailsStatsRowWearsADotForAnAchievementNotYetShown() {
        open(earned("films-1"))
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun nothingUnseenWearsNoDot() {
        open(NO_ACHIEVEMENTS)
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun onceShownTheDotGoesOut() {
        open(earned("films-1"))
        compose.runOnUiThread { fixture.achievementsSeen.markSeen("viewer", setOf("films-1")) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun openingStatsMarksWhatItShowsAsSeen() {
        open(earned("films-1"))
        compose.onNodeWithText("Stats").performClick()
        compose.waitForIdle()
        verify { fixture.stats.markAchievementsSeen() }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun theCompactHeadersStatsButtonWearsItToo() {
        open(earned("films-1"))
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertExists()
    }
}
```

`android/ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt`:
- constructor: after `settingsModel: SettingsViewModel? = null,` add `achievements: Achievements = NO_ACHIEVEMENTS,`
- properties: beside `val settings = …` add

```kotlin
    /** What this device has shown the fixture's viewer; a test marks it to put the dot out. */
    val achievementsSeen = InMemoryAchievementsSeen()

    /** The Stats page's ViewModel — relaxed, so a test can verify what the page asked of it. */
    val stats = mockk<StatsViewModel>(relaxed = true)
```

- in `init`, phase 04's stub loses its `val` (the property above replaces it): delete
  `val stats = mockk<StatsViewModel>(relaxed = true)` and keep its
  `every { stats.state } returns MutableStateFlow<StatsRead>(StatsRead.Done(…))`. Then, before
  `val models =`, add

```kotlin
        // The rail's dot reads the chosen profile's achievements from a core of its own; the
        // fixture's viewer is "viewer".
        val achievementsCore = FakeCore().apply { achievementsByProfile = mapOf("viewer" to achievements) }
```

- in `models`, after `StatsViewModel::class.java to stats,` add

```kotlin
                // LibraryFlow resolves the dot's ViewModel through hiltViewModel(), the same
                // reason every entry here exists — and every library test reaches it.
                AchievementDotViewModel::class.java to AchievementDotViewModel(FakeCoreProvider(achievementsCore), stored, achievementsSeen),
```

- imports: `stats.AchievementDotViewModel`, `stats.InMemoryAchievementsSeen`, `stats.NO_ACHIEVEMENTS`, `testing.FakeCore`, `testing.FakeCoreProvider`, `uniffi.mediagram_core.Achievements`.

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :ui-mobile:testDebugUnitTest --tests ui.chrome.StatsDotTest --tests ui.catalog.AchievementItemsTest`
Expected: FAIL — everything compiles (Tasks 7.5–7.6 supply the types), but no node says "New achievement", no "Achievements" text is drawn, and `markAchievementsSeen` is never called.

- [ ] **Step 3: Implement**

`android/core/designsystem/src/main/kotlin/StatusDot.kt`:

```kotlin
package designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The small round mark a row wears: a title held on this device, beside its
 * status line; an achievement not yet seen, on the Stats icon's corner. One
 * size everywhere. [color] is the caller's theme's tertiary — the phone's
 * Material 3 and the television's tv-material each hold their own.
 * [description] is what a screen reader says for it, where the words beside
 * it do not already.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val said = if (description == null) Modifier else Modifier.semantics { contentDescription = description }
    Box(modifier = modifier.size(7.dp).clip(CircleShape).background(color).then(said))
}
```

`ui-mobile/.../ui/settings/SettingsIndex.kt:164` — replace
`Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))` with
`StatusDot(MaterialTheme.colorScheme.tertiary)`; `ui-tv/.../system/TvSettingsIndex.kt:132` — the same
replacement. Import `designsystem.StatusDot` in both; drop any import the file no longer uses
(`CircleShape` at least — check `Box`, `clip`, `background`, `size` before removing).

`ui-mobile/.../ui/chrome/LibraryRail.kt`:
- `RailData` becomes

```kotlin
data class RailData(
    val counts: ChromeCounts,
    val tally: List<String>,
    val onHome: () -> Unit,
    /** Whether something the chosen profile earned has not been shown on this device: the Stats row's dot. */
    val newAchievement: Boolean = false,
) {
    companion object {
        val Empty = RailData(ChromeCounts.Empty, emptyList(), onHome = {})
    }
}

/** What the dot on [item] says to a screen reader, or `null` when it wears none — only Stats ever does. */
internal fun RailData.dotFor(item: RailItem): String? = if (item == RailItem.STATS && newAchievement) NEW_ACHIEVEMENT else null
```

- in `LibraryRail`'s row loop (phase 04's list), pass `dot = rail.dotFor(item)` to `RailRow`.
- `RailRow` gains a last parameter `dot: String? = null,` and its `Icon(…)` becomes:

```kotlin
        // On the icon's corner, as on the web and the television: the dot is there in every width.
        Box {
            Icon(painter = painterResource(item.icon), contentDescription = null, tint = ink, modifier = Modifier.size(21.dp))
            dot?.let {
                StatusDot(MaterialTheme.colorScheme.tertiary, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp), description = it)
            }
        }
```

- imports: `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.offset`, `designsystem.StatusDot`, `stats.NEW_ACHIEVEMENT`.

`ui-mobile/.../ui/chrome/ChromeControls.kt` — `CircleIconButton` gains `dot: String? = null,` after
`selected`, and its `Icon(…)` line is followed (inside the same `Box`) by:

```kotlin
        // The icon's top-right corner: centred, then out by the 21 dp icon's half.
        dot?.let { StatusDot(MaterialTheme.colorScheme.tertiary, Modifier.offset(x = 9.dp, y = (-9).dp), description = it) }
```

(imports: `androidx.compose.foundation.layout.offset`, `androidx.compose.material3.MaterialTheme` if
absent, `designsystem.StatusDot`).

`ui-mobile/.../ui/chrome/CompactLibraryHeader.kt` — at the top of the composable add
`val rail = LocalRailData.current`; in the `for (item in RailItem.entries)` loop, pass
`dot = rail.dotFor(item)` to `CircleIconButton`.

`ui-mobile/.../ui/LibraryFlow.kt` — in `Library`, after the `titlePreloadViewModel` lines:

```kotlin
    // The rail renders in every frame, root and pushed, so its dot is worked out here, once.
    val achievementDot: AchievementDotViewModel = hiltViewModel()
    val newAchievement by achievementDot.newAchievement.collectAsStateWithLifecycle()
```

and the call becomes `LibraryBranches(at, catalogState, catalogViewModel, fetchState, fetchViewModel, menuActions, profileBar, newAchievement)`
(import `stats.AchievementDotViewModel`).

`ui-mobile/.../ui/LibraryFlowBranches.kt` — `LibraryBranches` gains a last parameter
`newAchievement: Boolean,` (the one added line), and `railData` becomes:

```kotlin
    val railData =
        remember(shelves, watch, newAchievement) {
            RailData(chromeCountsOf(shelves, watch), libraryTallyLines(shelves), onHome = { at.toCatalog(); chooseTab(0) }, newAchievement = newAchievement)
        }
```

`android/ui-mobile/src/main/kotlin/ui/catalog/AchievementItems.kt`:

```kotlin
package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import designsystem.Spacing
import stats.AchievementsUi

/**
 * The Stats page's Achievements, between the last thirty days and the
 * history (which has no end): what was earned and on which day, then the
 * closest few to come with how far along each is — the web's
 * `achievementsSection`. Nothing at all while there is neither.
 */
internal fun LazyListScope.achievementItems(achievements: AchievementsUi) {
    if (achievements.earned.isEmpty() && achievements.next.isEmpty()) return
    item(key = "achievements") { Text(text = "Achievements", style = MaterialTheme.typography.titleMedium) }
    items(items = achievements.earned, key = { "achievement:${it.id}" }) { line -> AchievementRow(line.label, line.on) }
    if (achievements.next.isEmpty()) return
    item(key = "achievements-next") {
        Text(text = "Next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(items = achievements.next, key = { "next:${it.id}" }) { line ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
            AchievementRow(line.label, line.progress)
            // The words above already say it; the bar is for the eye alone.
            LinearProgressIndicator(progress = { line.fraction }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
        }
    }
}

@Composable
private fun AchievementRow(
    label: String,
    detail: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

`ui-mobile/.../ui/catalog/StatsScreen.kt` (phase 04's) — after the `item(key = "chart") { … }` block,
before `item(key = "history")`, add:

```kotlin
                achievementItems(state.achievements)
```

`ui-common/src/main/kotlin/ui/RememberStatsPage.kt` (phase 04's) — after
`val read by viewModel.state.collectAsStateWithLifecycle()` add:

```kotlin
    // Composed only while a Stats frame is on screen, phone or television: what the page holds
    // is no longer news on this device, and the rail's dot goes out.
    LaunchedEffect(read) { viewModel.markAchievementsSeen() }
```

(import `androidx.compose.runtime.LaunchedEffect`; the KDoc gains "Marks what it shows as seen
on this device.").

- [ ] **Step 4: Run, expect PASS**

Run: `cd android && ./gradlew -q :core:designsystem:testDebugUnitTest :ui-mobile:testDebugUnitTest`
Expected: green — every existing suite on `LibraryFlowFixture` (they now resolve the dot's
ViewModel), `StatsDotTest` 5, `AchievementItemsTest` 2.

- [ ] **Step 5: Commit**

```bash
git add android/core/designsystem android/ui-common android/ui-mobile android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsIndex.kt
git commit -m "feat(android): achievements on the phone's Stats page, and the dot on its rail"
```

---

## Task 7.8: The television — the dot on the collapsed rail, the section, the page marking it

**Files:**
- Create: `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvAchievementItems.kt`
- Create: `android/ui-tv/src/test/kotlin/ui/tv/TvIndexRowDotTest.kt`
- Create: `android/ui-tv/src/test/kotlin/ui/tv/TvStatsDotTest.kt`
- Create: `android/ui-tv/src/test/kotlin/ui/tv/catalog/TvAchievementItemsTest.kt`
- Modify: `android/ui-tv/src/main/kotlin/ui/tv/TvIndexRow.kt` (`dot`)
- Modify: `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryRail.kt` (`LocalNewAchievement`; the Stats row's dot)
- Modify: `android/ui-tv/src/main/kotlin/ui/tv/TvLibraryBranches.kt` (`TvLibraryHomeFrame` provides it)
- Modify: `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvStatsPage.kt` (the section; `TvStatsStop` internal)
- Modify: `android/ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt` (register the dot's ViewModel; `achievements` parameter; `stats` and `achievementsSeen` properties)

**Interfaces:**
- Produces: `TvIndexRow(…, dot: String? = null)` — drawn on the icon's corner, so it survives `expanded = false`; `internal val LocalNewAchievement = compositionLocalOf { false }`; `LazyListScope.tvAchievementItems(AchievementsUi)`; `internal fun TvStatsStop` (was `private`); `TvAppFixture(…, achievements: Achievements = NO_ACHIEVEMENTS)` with `val stats`, `val achievementsSeen`.
- Why a CompositionLocal: the dot crosses `TvLibraryHomeFrame → TvCatalogRoot → TvCatalogScreen → TvLibraryChrome → TvLibraryRail`, none of which has anything else to do with it — the phone's `LocalRailData` is the precedent.

- [ ] **Step 1: Failing tests**

`android/ui-tv/src/test/kotlin/ui/tv/TvIndexRowDotTest.kt`:

```kotlin
package ui.tv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
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
import stats.NEW_ACHIEVEMENT
import ui.RailItem

/** [TvIndexRow]'s dot: on the icon, so the collapsed rail — icon only — still shows it and says it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvIndexRowDotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null

    @After
    fun close() {
        compose.runOnUiThread { controller?.close() }
        controller = null
    }

    private fun show(
        expanded: Boolean,
        dot: String?,
    ) {
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent {
                TvTheme {
                    TvIndexRow(
                        icon = painterResource(RailItem.STATS.icon),
                        label = "Stats",
                        selected = false,
                        focusRequester = remember { FocusRequester() },
                        onSelect = {},
                        expanded = expanded,
                        contentDescription = "Stats",
                        dot = dot,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aCollapsedRowStillShowsItsDotAndSaysItWithItsName() {
        show(expanded = false, dot = NEW_ACHIEVEMENT)
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Stats").assert(hasContentDescription(NEW_ACHIEVEMENT))
    }

    @Test
    fun anOpenRowShowsItOnTheIconBesideItsLabel() {
        show(expanded = true, dot = NEW_ACHIEVEMENT)
        compose.onNodeWithText("Stats").assertIsDisplayed()
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aRowWithNoDotDrawsNone() {
        show(expanded = false, dot = null)
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertDoesNotExist()
    }
}
```

`android/ui-tv/src/test/kotlin/ui/tv/TvStatsDotTest.kt`:

```kotlin
package ui.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import model.Profile
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import stats.NEW_ACHIEVEMENT
import stats.NO_ACHIEVEMENTS
import ui.tv.catalog.films
import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.EarnedAchievement

/** The new-achievement dot over the real [TvLibrary]: on the collapsed rail's Stats row, out once shown, marked by the page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvStatsDotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    private fun earned(vararg ids: String) = Achievements(earned = ids.map { EarnedAchievement(id = it, earnedAt = 1L) }, next = emptyList())

    private fun open(achievements: Achievements) {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", films(2), achievements = achievements)
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
    fun theCollapsedRailsStatsRowWearsADotForAnAchievementNotYetShown() {
        open(earned("films-1"))
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Stats").assert(hasContentDescription(NEW_ACHIEVEMENT))
    }

    @Test
    fun nothingUnseenWearsNoDot() {
        open(NO_ACHIEVEMENTS)
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun onceShownTheDotGoesOut() {
        open(earned("films-1"))
        compose.runOnUiThread { fixture.achievementsSeen.markSeen("ada", setOf("films-1")) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(NEW_ACHIEVEMENT, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun openingStatsMarksWhatItShowsAsSeen() {
        open(earned("films-1"))
        compose.onNodeWithContentDescription("Stats").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        verify { fixture.stats.markAchievementsSeen() }
    }
}
```

`android/ui-tv/src/test/kotlin/ui/tv/catalog/TvAchievementItemsTest.kt`:

```kotlin
package ui.tv.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stats.AchievementsUi
import stats.EarnedLine
import stats.NextLine
import stats.StatsBar
import stats.StatsLine
import stats.StatsUiState

/** The Achievements section on [TvStatsPage], in the harness every catalogue page's own test uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvAchievementItemsTest : TvScreenStateTest() {
    private val ready =
        StatsUiState.Ready(
            totals = listOf("This week" to "42 min", "This month" to "42 min", "All time" to "42 min"),
            bars = List(30) { StatsBar(fraction = 0f, initial = "M", description = "day $it") },
            history = listOf(StatsLine("STARTED:f1", "Started · Der Pate · today 21:14 · 42 min")),
        )

    @Test
    fun earnedAchievementsShowTheirDayAndTheNextHowFarAlong() {
        show {
            TvStatsPage(
                ready.copy(
                    achievements =
                        AchievementsUi(
                            earned = listOf(EarnedLine(id = "films-1", label = "First film", on = "today")),
                            next = listOf(NextLine(id = "films-10", label = "10 films", progress = "1 of 10 films", fraction = 0.1f)),
                        ),
                ),
            )
        }
        for (text in listOf("Achievements", "First film", "today", "Next", "1 of 10 films", "History")) {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
            compose.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun nothingEarnedAndNothingToComeDrawsNoSection() {
        show { TvStatsPage(ready) }
        compose.onNodeWithText("Achievements").assertDoesNotExist()
    }
}
```

`android/ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt`:
- constructor: after `heldIds: Set<String> = emptySet(),` add `achievements: Achievements = NO_ACHIEVEMENTS,`
- properties: beside `val settings = …` add

```kotlin
    /** What this device has shown the chosen profile; a test marks it to put the dot out. */
    val achievementsSeen = InMemoryAchievementsSeen()

    /** The Stats page's ViewModel — relaxed, so a test can verify what the page asked of it. */
    val stats = mockk<StatsViewModel>(relaxed = true)
```

- in `init`, delete phase 04's `val stats = mockk<StatsViewModel>(relaxed = true)` and keep its
  `every { stats.state } returns MutableStateFlow<StatsRead>(StatsRead.Done(…))`; before
  `val models =` add

```kotlin
        // The rail's dot reads the chosen profile's achievements from a core of its own.
        val achievementsCore = FakeCore().apply { achievementsByProfile = chosenProfileId?.let { mapOf(it to achievements) }.orEmpty() }
```

- in `models`, after `StatsViewModel::class.java to stats,` add

```kotlin
                // TvLibraryHomeFrame resolves the dot's ViewModel through hiltViewModel(), the
                // same reason every entry here exists — and every library test reaches it.
                AchievementDotViewModel::class.java to AchievementDotViewModel(FakeCoreProvider(achievementsCore), viewer, achievementsSeen),
```

- imports: `stats.AchievementDotViewModel`, `stats.InMemoryAchievementsSeen`, `stats.NO_ACHIEVEMENTS`, `testing.FakeCoreProvider`, `uniffi.mediagram_core.Achievements` (`FakeCore` is already imported).

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd android && ./gradlew -q :ui-tv:testDebugUnitTest --tests ui.tv.TvIndexRowDotTest --tests ui.tv.TvStatsDotTest --tests ui.tv.catalog.TvAchievementItemsTest`
Expected: FAIL — `TvIndexRow` has no `dot` parameter.

- [ ] **Step 3: Implement**

`android/ui-tv/src/main/kotlin/ui/tv/TvIndexRow.kt`:
- KDoc: after the paragraph that ends "…instead; expanded, the visible [label] already carries that." add

```kotlin
 *
 * [dot], when given, is a small mark drawn on the icon's top-right corner —
 * not after the label, where [trailing] goes — so the collapsed rail, which
 * keeps only the icon, still shows it; its words are what a screen reader
 * hears for it, merged into the collapsed row's own description.
```

- add the last parameter `dot: String? = null,` after `contentDescription: String? = null,`
- replace the `Image(…)` call with:

```kotlin
        Box {
            Image(
                painter = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(if (focused) Palette.Imprint else baseColor),
                modifier = Modifier.size(22.dp),
            )
            dot?.let {
                StatusDot(MaterialTheme.colorScheme.tertiary, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp), description = it)
            }
        }
```

- imports: `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.offset`, `designsystem.StatusDot`.

`android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryRail.kt` — after `TvWordmarkSize` add:

```kotlin
/**
 * Whether the chosen profile has earned something this device has not shown
 * it yet — the dot on the Stats row. Provided by the library frame that
 * draws the catalogue, and read here rather than threaded through the
 * catalogue screen and its chrome, which have nothing else to do with it.
 */
internal val LocalNewAchievement = compositionLocalOf { false }
```

and in `RailRow`'s `TvIndexRow(…)` call, after `contentDescription = item.label,` add:

```kotlin
        dot = if (item == RailItem.STATS && LocalNewAchievement.current) NEW_ACHIEVEMENT else null,
```

(imports: `androidx.compose.runtime.compositionLocalOf`, `stats.NEW_ACHIEVEMENT`).

`android/ui-tv/src/main/kotlin/ui/tv/TvLibraryBranches.kt` — in `TvLibraryHomeFrame`, before
`saved.SaveableStateProvider(CatalogStateKey) {` add

```kotlin
    // Only this frame draws the rail, so only here is the dot worked out: nothing reads while a
    // title plays.
    val achievementDot: AchievementDotViewModel = hiltViewModel()
    val newAchievement by achievementDot.newAchievement.collectAsStateWithLifecycle()
```

and wrap the whole `saved.SaveableStateProvider(CatalogStateKey) { … }` block in
`CompositionLocalProvider(LocalNewAchievement provides newAchievement) { … }` (imports:
`androidx.compose.runtime.CompositionLocalProvider`, `androidx.compose.runtime.getValue`,
`androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`,
`androidx.lifecycle.compose.collectAsStateWithLifecycle`, `stats.AchievementDotViewModel`,
`ui.tv.chrome.LocalNewAchievement`).

`android/ui-tv/src/main/kotlin/ui/tv/catalog/TvAchievementItems.kt`:

```kotlin
package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import designsystem.LocalCatalogueTones
import designsystem.Spacing
import designsystem.TvTypeScale
import stats.AchievementsUi
import ui.tv.TvFocus

/**
 * The Stats page's Achievements on a television: the phone's section at a
 * television's sizes, with the same strings. Every line is a stop for the
 * remote, as every history line is — the page has nothing to press, but
 * stepping is what scrolls it. Nothing at all while there is neither an
 * earned achievement nor one to come.
 */
internal fun LazyListScope.tvAchievementItems(achievements: AchievementsUi) {
    if (achievements.earned.isEmpty() && achievements.next.isEmpty()) return
    item(key = "achievements") {
        Text(text = "Achievements", style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small))
    }
    items(items = achievements.earned, key = { "achievement:${it.id}" }) { line ->
        TvStatsStop { focused -> TvAchievementLine(line.label, line.on, focused) }
    }
    if (achievements.next.isEmpty()) return
    item(key = "achievements-next") {
        Text(text = "Next", style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow, color = LocalCatalogueTones.current.quiet))
    }
    items(items = achievements.next, key = { "next:${it.id}" }) { line ->
        TvStatsStop { focused ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                TvAchievementLine(line.label, line.progress, focused)
                TvProgressRule(fraction = line.fraction)
            }
        }
    }
}

@Composable
private fun TvAchievementLine(
    label: String,
    detail: String,
    focused: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = TvFocus.textStyle(TvTypeScale.body, focused))
        Text(text = detail, style = TvTypeScale.body.copy(color = LocalCatalogueTones.current.quiet))
    }
}
```

`android/ui-tv/src/main/kotlin/ui/tv/catalog/TvStatsPage.kt` (phase 04's) — `private fun TvStatsStop`
becomes `internal fun TvStatsStop`; after the `item(key = "chart") { … }` block, before
`item(key = "history")`, add:

```kotlin
                tvAchievementItems(state.achievements)
```

`TvStatsFrame` needs nothing: it reads the page through `rememberStatsPage`, which Task 7.7
taught to mark what it shows as seen.

- [ ] **Step 4: Run, expect PASS**

Run: `cd android && ./gradlew -q :ui-tv:testDebugUnitTest`
Expected: green — every existing `TvAppFixture` suite (they now resolve the dot's ViewModel),
`TvIndexRowDotTest` 3, `TvStatsDotTest` 4, `TvAchievementItemsTest` 2, phase 04's
`TvStatsPageTest`/`TvStatsRailTest` unchanged.

- [ ] **Step 5: Commit**

```bash
git add android/ui-tv
git commit -m "feat(android): achievements on the TV's Stats page, and the dot on its collapsed rail"
```

---

## Task 7.9: Full gate, a tablet walk, the release bump

- [ ] **Step 1: Every gate**

```bash
cargo test -p mediagram-core
cargo clippy --all-targets --all-features -- -D warnings
cargo test -p mediagram --test code_standards
cd android && ./gradlew -q :feature:stats:testDebugUnitTest :core:testing:testDebugUnitTest :ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest && cd ..
scripts/check.sh
```

Expected: all green; `check.sh` ends `all checks passed` (it also runs the web suite, which reads
the same two fixtures).

- [ ] **Step 2: The tablet** — navigate only; change no setting; play nothing (memory: test plays
  land in Continue).

```bash
ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/build-android-core.sh
cd android && ANDROID_SERIAL=caad49da ./gradlew :app:installDebug && cd ..
adb -s caad49da shell am start -n com.mediagram.android/.MainActivity
```

On the tablet's "test" profile (EXPANDED width, rail beside the shelves):
1. if that profile has earned anything, the rail's Stats icon wears the sage dot — screenshot it
   into the session's scratchpad: `adb -s caad49da exec-out screencap -p > tablet-dot.png`;
2. tap Stats — "Achievements" between "Last 30 days" and "History", earned lines with a day and no
   clock time, "Next" with three progress lines; screenshot;
3. Back — the dot is gone; relaunch the app — still gone.
Compare the page's earned ids and dates with the web's for the same profile name
(`curl -s 127.0.0.1:<player port>/api/profiles/<id>/stats | jq .achievements` on the real player,
once both have synced) — the same ids, the same days.

The TV box runs the self-updating release build; reaching it means `scripts/release-android.sh`,
which publishes to every TV in the household — only with the user's go-ahead (phase 05 owns the
cross-device walk). If `adb devices` lists the TV emulator `emulator-5554`, optionally:
`cd android && ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug`, and check the collapsed
rail's Stats icon wears the dot.

- [ ] **Step 3: Bump the minor version, by pattern**

```bash
current=$(grep -m1 -oP '^version = "\K[0-9]+\.[0-9]+\.[0-9]+' Cargo.toml)
next=$(echo "$current" | awk -F. '{print $1"."$2+1".0"}')
sed -i -E '0,/^version = "[0-9]+\.[0-9]+\.[0-9]+"$/s//version = "'"$next"'"/' Cargo.toml
sed -i -E 's/^(  "version": ")[0-9]+\.[0-9]+\.[0-9]+(",)$/\1'"$next"'\2/' web/package.json
sed -i -E 's/^(        versionName = ")[0-9]+\.[0-9]+\.[0-9]+(")$/\1'"$next"'\2/' android/app/build.gradle.kts
cargo metadata -q --format-version 1 >/dev/null
grep -m1 '^version' Cargo.toml; grep '"version"' web/package.json; grep 'versionName = ' android/app/build.gradle.kts
```

Expected: all three print `$next`. `versionCode` is derived from `versionName`; never edit it.

- [ ] **Step 4: Commit**

```bash
git add Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts
git commit -m "chore: release $next — achievements on Android"
```

---

## Test matrix

| Layer | What | Where |
|---|---|---|
| Shared fixture (Rust ↔ web) | every rule, kids, earnedAt, offsets/DST, library gone/shrunk, ties, next | `shared_watch_state_fixtures.rs` `achievement_fixtures_match_the_web` (7.1) |
| Shared fixture (Kotlin ↔ web) | labels, progress lines | `AchievementLabelsFixtureTest` (7.5) |
| Catalog adapter (SQLite) | genres from index rows, collections, unplayable out | `catalog_achievements_tests.rs` (7.2) |
| Through the real `Core` | kids from `profiles.kids`, unknown profile, no catalog | `tests/achievements_surface.rs` (7.3); `CoreContract` (7.4) |
| ViewModels (JVM) | dot: unseen, seen, sync after the 15 s settle, no read per save tick, page marks, switch (out before the new read lands), day/offset; page: achievements read and marked | `AchievementDotViewModelTest`, `StatsViewModelTest` (7.6) |
| Persistence (Robolectric) | seen per profile, across a restart, replaced whole | `SharedPreferencesAchievementsSeenTest` (7.6) |
| Compose (Robolectric) | phone rail + compact header dot; section; page marks; TV row collapsed/open; TV rail; TV section | `StatsDotTest`, `AchievementItemsTest` (7.7); `TvIndexRowDotTest`, `TvStatsDotTest`, `TvAchievementItemsTest` (7.8) |
| Manual | tablet walk, web comparison | Task 7.9 Step 2 |

## Rollback

One commit per task; `git revert` newest first. No schema, no stored state but the
`achievements_seen` preferences file, which an older build never reads. Reverting 7.3 needs
7.4–7.8 reverted first (Kotlin calls `achievements`); after reverting Rust changes, rebuild the
native core before installing.

## Risk

| Risk | L × I | Mitigation |
|---|---|---|
| Phase 03/04 code differs from their plans (names, anchors) | M × M | Every edit is anchored on quoted text; the Rust here was compiled against phase 03's layout in a scratch tree; Step 1 of 7.9 runs every gate |
| A library-level ViewModel unregistered in a fixture | M × H | Registered in both fixtures (7.7, 7.8); every existing library suite must stay green |
| The phone re-reads achievements on every 10 s position save while playing | L × L | Reads wait for 15 s of quiet (`DOT_SETTLE_MS`), as the web's dot does; `positionsSavedWhileATitlePlaysAreNotReadOneByOne` |
| Dot offsets look off at some density | L × L | Checked on the tablet in 7.9; it is decoration over a labelled row |
| Genres differ from the Android Genres shelf (sidecar) | L × L | Deliberate (decision 2), so web and Android achievements agree |

## Open questions (also in the hand-off)

1. Genres from the channel index only (not the device-fetched sidecar the Android shelves also use), so both surfaces agree — confirm.
2. The compact-phone ⋮ "Stats" item (pushed frames) wears no dot; the root's compact header does.
3. Freshness ceiling: a sync round that brings only day rows (no position/mark/list change) lights the dot at the next change, on both surfaces (contract §9).

## Success criteria

- `cargo test -p mediagram-core` green with `achievement_fixtures_match_the_web` passing all 19 web cases; clippy and code_standards green.
- `./gradlew :feature:stats:testDebugUnitTest :core:testing:testDebugUnitTest :ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest` green; `scripts/check.sh` green.
- On the tablet: the Stats page shows the section; the rail dot lights for an unseen earned id and goes out once the page has been on screen, across a relaunch; the earned ids and days match the web's for the same profile.
- All three manifests on the same new minor version.
