# Phase 05 — Android: model, repository, catalog filter, create dialog

**Context:** spec § Rule, § Where the filter applies, § UI. Mirrors Phase 04's decisions (the web is the reference — see CLAUDE.md § Surface Parity). Files:
- `android/core/model/src/main/kotlin/WatchSnapshot.kt` (`data class Profile` `:39`), `AgeRating.kt` (`kidsVerdict`)
- `android/core/data/src/main/kotlin/CoreClient.kt` (`createProfile` `:131`), `DefaultCoreClient.kt:75`, `WatchStateRepository.kt` (interface `:52`, impl `:211-220`, `toProfile` `:323`)
- `android/feature/catalog/src/main/kotlin/CatalogViewModel.kt`, `CatalogUiState.kt`, `profile/ProfileViewModel.kt` (`add` `:163`)
- `android/ui-mobile/src/main/kotlin/ui/profile/ProfilePickerScreen.kt` (`PickerBody`, `ProfileTile`, `NameDialog`), `ProfileGate.kt:30`, `ui/catalog/CatalogScreen.kt:47`
- Fakes implementing the changed interfaces: `core/data/src/test/kotlin/{WatchStateRepositoryTest,WatchSyncTest,WatchStateOwnershipTest}.kt` (CoreClient), `feature/catalog/src/test/kotlin/{CatalogViewModelTest.kt,profile/ProfileViewModelTest.kt}`, `feature/player/src/test/kotlin/FakeWatchStateRepository.kt` (WatchStateRepository)

Depends on Phase 03 (bindings expose `Profile.kids`, `createProfile(name, kids)`).

Run Gradle from `android/`: `./gradlew testDebugUnitTest lint` (what `scripts/check.sh` runs).

---

### Task 8: Profile carries `kids`; repository creates with it; the filter rule

**Files:**
- Modify: `core/model/src/main/kotlin/WatchSnapshot.kt`, `core/model/src/main/kotlin/AgeRating.kt`
- Modify: `core/data/src/main/kotlin/CoreClient.kt`, `DefaultCoreClient.kt`, `WatchStateRepository.kt`
- Modify (signatures only): the six fakes listed above
- Test: `feature/catalog/src/test/kotlin/AgeRatingTest.kt`, `core/data/src/test/kotlin/WatchStateRepositoryTest.kt`

**Interfaces:**
- Consumes: uniffi `Profile(id, name, kids = false)`, `core.createProfile(name, kids)`.
- Produces:
  - `data class Profile(val id: String, val name: String, val kids: Boolean = false)`
  - `CoreClient.createProfile(name: String, kids: Boolean = false): Profile?` (uniffi `Profile`)
  - `WatchStateRepository.createProfile(name: String, kids: Boolean = false): Profile?`
  - `fun forKidsProfile(sets: List<MediaSet>, marked: Set<String>): List<MediaSet>` in `model`

- [ ] **Step 1: Write the failing tests**

`AgeRatingTest.kt` — add (import `model.forKidsProfile`, `model.MediaSet`, `model.Kind`):

```kotlin
    private fun rated(id: String, fsk: String?, kind: Kind = Kind.MOVIE) =
        MediaSet(
            setId = id, kind = kind, title = id, show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = null,
            durationSecs = null, posterPath = null, totalBytes = 0, fsk = fsk,
        )

    @Test
    fun aKidsProfileSeesRatedForKidsOrMarkedByHandAndNothingElse() {
        val sets = listOf(
            rated("Zero", "0"), rated("Six", "6"), rated("Twelve", "12"),
            rated("Sixteen", "16"), rated("Eighteen", "18"),
            rated("Unrated", null), rated("UnratedMarked", null), rated("SixteenMarked", "16"),
            rated("lesson-1", null, Kind.TUTORIAL), rated("lesson-2", null, Kind.TUTORIAL),
        )
        val marked = setOf("UnratedMarked", "SixteenMarked", "lesson-2")
        assertEquals(
            listOf("Zero", "Six", "Twelve", "UnratedMarked", "lesson-2"),
            forKidsProfile(sets, marked).map { it.setId },
        )
    }
```

(Same cases as the web's `age-rating.test.ts` "what a kids profile sees" — keep the two tables in step.)

`WatchStateRepositoryTest.kt` — the `StateCoreClient` fake's `createProfile` becomes:

```kotlin
    override suspend fun createProfile(name: String, kids: Boolean): CoreProfile {
        val created = CoreProfile("p${profileList.size + 1}", name, kids)
        // …rest unchanged…
```

and add:

```kotlin
    @Test
    fun aKidsProfileIsCreatedAndListedAsOne() =
        runTest {
            val core = StateCoreClient()
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

            val created = repository.createProfile("Mia", kids = true)

            assertEquals(true, created?.kids)
            assertEquals(listOf(Profile(created!!.id, "Mia", kids = true)), repository.profiles.value)
        }
```

- [ ] **Step 2: Run** `cd android && ./gradlew :feature:catalog:testDebugUnitTest :core:data:testDebugUnitTest` — Expected: compile FAIL (`forKidsProfile` unresolved; `createProfile` has no `kids`).

- [ ] **Step 3: Implement**

`WatchSnapshot.kt`:

```kotlin
data class Profile(
    val id: String,
    val name: String,
    /** Sees only titles rated FSK 12 or under, or marked for Kids by hand. */
    val kids: Boolean = false,
)
```

`AgeRating.kt` — append:

```kotlin
/**
 * The catalog a kids profile sees: rated for kids, or unrated and marked by
 * hand — the web player's `forKidsProfile`. A rating decides on its own; a
 * hand mark on a title rated too old does not let it through.
 */
fun forKidsProfile(
    sets: List<MediaSet>,
    marked: Set<String>,
): List<MediaSet> =
    sets.filter {
        when (it.kidsVerdict()) {
            KidsVerdict.SAFE -> true
            KidsVerdict.UNSAFE -> false
            KidsVerdict.UNRATED -> it.setId in marked
        }
    }
```

`CoreClient.kt:131`: `suspend fun createProfile(name: String, kids: Boolean = false): Profile? = null`
`DefaultCoreClient.kt:75`: `override suspend fun createProfile(name: String, kids: Boolean): Profile? = core.createProfile(name, kids)`
`WatchStateRepository.kt`: interface `suspend fun createProfile(name: String, kids: Boolean = false): Profile?`; impl `override suspend fun createProfile(name: String, kids: Boolean): Profile?` with `core.createProfile(name, kids)`; `toProfile`:

```kotlin
private fun toProfile(profile: uniffi.mediagram_core.Profile): Profile = Profile(profile.id, profile.name, profile.kids)
```

Each fake: change its override to `createProfile(name: String, kids: Boolean)` (an override may not restate the default).

- [ ] **Step 4: Run** `cd android && ./gradlew testDebugUnitTest` — Expected: PASS.

- [ ] **Step 5: Commit** `git add android && git commit -m "feat(android-kids): carry the kids flag on profiles and port the filter rule"`

---

### Task 9: Filter the catalog for a kids profile; create dialog switch; tile label

**Files:**
- Modify: `feature/catalog/src/main/kotlin/CatalogViewModel.kt`, `CatalogUiState.kt`
- Modify: `feature/catalog/src/main/kotlin/profile/ProfileViewModel.kt`
- Modify: `ui-mobile/src/main/kotlin/ui/profile/ProfilePickerScreen.kt`, `ProfileGate.kt`
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt` (and any other exhaustive `when` over `CatalogUiState` the compiler reports)
- Modify: `feature/catalog/src/test/kotlin/FakeCatalogRepository.kt` (accept explicit sets; make `fakeSet` `internal`)
- Test: `feature/catalog/src/test/kotlin/CatalogViewModelTest.kt`, `feature/catalog/src/test/kotlin/profile/ProfileViewModelTest.kt`

**Interfaces:**
- Consumes: `Profile.kids`, `forKidsProfile`, `WatchStateRepository.createProfile(name, kids)` (Task 8); existing `watchState.profiles`, `chosenProfileId`, `snapshot` flows; `shelvesOf(sets)`.
- Produces: `CatalogUiState.KidsEmpty`; `ProfileViewModel.add(name: String, kids: Boolean)`; `onAdd: (String, Boolean) -> Unit` in the picker.

- [ ] **Step 1: Write the failing tests**

`FakeCatalogRepository.kt` — add a constructor parameter and fold it in:

```kotlin
    /** Sets given whole, for tests that need a rating or a particular id. */
    private val given: List<MediaSet> = emptyList(),
) : CatalogRepository {
    private val allSets: List<MediaSet> =
        given +
            (0 until movies).map { fakeSet(Kind.MOVIE, "movie-$it") } + …unchanged…
```

and make `fakeSet` `internal`.

`CatalogViewModelTest.kt` — give `FakeCatalogWatchState` a way to start with profiles (its flows are already `MutableStateFlow`s, so tests set `.value`). Add:

```kotlin
    @Test
    fun aKidsProfileSeesOnlyItsTitlesAndSwitchingBackRestoresTheRest() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(
                given = listOf(
                    fakeSet(Kind.MOVIE, "Family").copy(fsk = "6"),
                    fakeSet(Kind.MOVIE, "Grown").copy(fsk = "16"),
                    fakeSet(Kind.MOVIE, "Marked"),
                ),
            )
            val watch = FakeCatalogWatchState(WatchSnapshot.Empty.copy(kids = listOf("Marked")))
            watch.profiles.value = listOf(Profile("k", "Mia", kids = true), Profile("a", "Ana"))
            watch.chosenProfileId.value = "k"
            val vm = catalogViewModel(repository, watch)
            vm.state.test {
                awaitItem()
                val kidsView = awaitItem() as CatalogUiState.Ready
                val ids = kidsView.shelves.flatMap { it.entries }.filterIsInstance<Entry.Film>().map { it.set.setId }
                assertEquals(setOf("Family", "Marked"), ids.toSet())
                val readsBefore = repository.reads

                watch.chosenProfileId.value = "a"
                val adultView = awaitItem() as CatalogUiState.Ready
                val all = adultView.shelves.flatMap { it.entries }.filterIsInstance<Entry.Film>().map { it.set.setId }
                assertEquals(setOf("Family", "Grown", "Marked"), all.toSet())
                assertEquals(readsBefore, repository.reads)
            }
        }

    @Test
    fun aKidsProfileWithNothingAllowedSaysWhatItIsWaitingFor() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeCatalogRepository(given = listOf(fakeSet(Kind.MOVIE, "Grown").copy(fsk = "16")))
            val watch = FakeCatalogWatchState()
            watch.profiles.value = listOf(Profile("k", "Mia", kids = true))
            watch.chosenProfileId.value = "k"
            val vm = catalogViewModel(repository, watch)
            vm.state.test {
                awaitItem()
                assertEquals(CatalogUiState.KidsEmpty, awaitItem())
            }
        }
```

(`WatchSnapshot.kids` is `List<String>` — `WatchSnapshot.kt:55` — the device-wide marked list.)

`ProfileViewModelTest.kt` — its `FakeWatchStateRepository` already records names in `created`; add a parallel `val createdKids = mutableListOf<Boolean>()`, and change its override to:

```kotlin
    override suspend fun createProfile(name: String, kids: Boolean): Profile? {
        createFailure?.let { throw it }
        if (refuseWrites) return null
        created += name
        createdKids += kids
        val made = Profile("new-${created.size}", name, kids)
        profiles.value = profiles.value + made
        return made
    }
```

Add, set up like the file's existing tests (`:134-137`):

```kotlin
    @Test
    fun addingAKidsProfilePassesTheFlagThrough() =
        runTest {
            val repository = FakeWatchStateRepository(emptyList())
            val vm = ProfileViewModel(repository, FakeWatchSync())
            vm.add("Mia", kids = true)
            advanceUntilIdle()
            assertEquals(listOf(true), repository.createdKids)
            assertEquals(true, repository.profiles.value.single().kids)
        }
```

Existing `vm.add("…")` calls in this file become `vm.add("…", kids = false)`.

- [ ] **Step 2: Run** `cd android && ./gradlew :feature:catalog:testDebugUnitTest` — Expected: FAIL (no filtering; `KidsEmpty` and `add(name, kids)` do not exist).

- [ ] **Step 3: Implement**

`CatalogUiState.kt` — beside `Empty`:

```kotlin
    /** A kids profile over a library with nothing rated for kids yet. */
    data object KidsEmpty : CatalogUiState
```

`CatalogViewModel.kt`:

```kotlin
        /** The sets behind the last Ready state, before any profile's filter. */
        private var lastSets: List<MediaSet> = emptyList()

        /**
         * The marked-by-hand set when the chosen profile is a kids profile,
         * `null` otherwise. Distinct, so progress updates — which also move
         * `snapshot` — do not regroup the shelves.
         */
        private val kidsFilter: Flow<Set<String>?> =
            combine(watchState.profiles, watchState.chosenProfileId, watchState.snapshot) { profiles, chosen, watch ->
                if (profiles.firstOrNull { it.id == chosen }?.kids == true) watch.kids.toSet() else null
            }.distinctUntilChanged()
```

In `readCatalog` and `regrouped`, read the sets once, remember them, and group all of them as today:

```kotlin
                    val sets = repository.sets()
                    lastSets = sets
                    val shelves = shelvesOf(sets)
```

(In `regrouped`, set `lastSets = sets` only when `shelves.isNotEmpty()` alongside the `show(...)`.)

The `state` combine gains the filter; a kids profile's shelves are regrouped from the filtered sets:

```kotlin
                combine(catalog, updates.refreshing, watchState.snapshot, kidsFilter) { shown, refreshing, watch, kids ->
                    when {
                        shown is CatalogUiState.Ready -> {
                            // One place the filter applies: every wall and title page
                            // on the phone is built from these shelves.
                            val shelves = if (kids == null) shown.shelves else shelvesOf(forKidsProfile(lastSets, kids))
                            if (kids != null && shelves.isEmpty()) {
                                CatalogUiState.KidsEmpty
                            } else {
                                shown.copy(shelves = shelves, refreshing = refreshing, watch = watch)
                            }
                        }
                        refreshing -> CatalogUiState.Loading
                        else -> shown
                    }
                }.collect { send(it) }
```

Imports: `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.distinctUntilChanged`, `model.MediaSet`, `model.forKidsProfile`.

`CatalogScreen.kt` (and every other exhaustive `when` the compiler flags): `CatalogUiState.KidsEmpty -> CenteredMessage("Nothing rated FSK 12 or under yet.")`.

`ProfileViewModel.kt`: `fun add(name: String, kids: Boolean)` and `repository.createProfile(name, kids)`. `ProfileGate.kt:30` stays `onAdd = viewModel::add` (the type is now `(String, Boolean) -> Unit`).

`ProfilePickerScreen.kt`:
- `onAdd: (String, Boolean) -> Unit` in `ProfilePickerScreen` and `PickerBody`; `NameDialog(onConfirm = { name, kids -> naming = false; onAdd(name, kids) }, …)`.
- `ProfileTile(name = profile.name, kids = profile.kids, onClick = …)`; inside, below the name:

```kotlin
        if (kids) {
            Text(
                "KIDS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
```

- `NameDialog`:

```kotlin
@Composable
private fun NameDialog(
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(NAME_PROMPT) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.medium).toggleable(value = kids, role = Role.Switch, onValueChange = { kids = it }),
                ) {
                    Text("Kids profile — only FSK 12 and under", modifier = Modifier.weight(1f))
                    Switch(checked = kids, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, kids) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

(imports: `androidx.compose.foundation.layout.Row`, `androidx.compose.foundation.selection.toggleable`, `androidx.compose.material3.Switch`.)

- [ ] **Step 4: Run** `cd android && ./gradlew testDebugUnitTest lint` — Expected: PASS, lint clean.

- [ ] **Step 5: Commit** `git add android && git commit -m "feat(android-kids): filter the catalog for kids profiles and create them from the picker"`

---

### Task 9b: The phone's player offers no Kids mark on a kids profile

The web hides its player's Kids mark on a kids profile (Task 6); the phone must match (CLAUDE.md § Surface Parity).

**Files:**
- Modify: `feature/player/src/main/kotlin/PlayerMarksState.kt` (add a field)
- Modify: `feature/player/src/main/kotlin/PlayerViewModel.kt` (`marks` combine `:83-94`, `toggleKids` `:181-190`)
- Modify: `ui-mobile/src/main/kotlin/ui/player/PlayerMarks.kt` (the Kids `MarkButton`)
- Test: `feature/player/src/test/kotlin/PlayerMarksTest.kt`

**Interfaces:**
- Consumes: `Profile.kids` (Task 8); `repository.profiles`, `repository.chosenProfileId` (existing flows; the player fake exposes both as `MutableStateFlow`, `FakeWatchStateRepository.kt:23-24`).
- Produces: `PlayerMarksState.canMarkKids: Boolean = true`.

- [ ] **Step 1: Write the failing test** (in `PlayerMarksTest.kt`, set up like `toggleKidsMarksAndUnmarksTheOpenTitle` at `:79`)

```kotlin
    @Test
    fun aKidsProfileCannotMarkTitlesForKids() =
        runTest {
            installMainDispatcher()
            val repository = FakeWatchStateRepository()
            repository.profiles.value = listOf(Profile("p1", "Mia", kids = true))
            val vm = viewModel(repository)

            vm.marks.test {
                assertNull(awaitItem())
                vm.open("s1")
                assertEquals(false, awaitItem()?.canMarkKids)
                vm.toggleKids()
                advanceUntilIdle()
                assertEquals(false, expectMostRecentItem()?.kids)
            }
        }
```

(`FakeWatchStateRepository()` chooses `"p1"` by default — `profileChosen = true`, `FakeWatchStateRepository.kt:20`.)

- [ ] **Step 2: Run** `cd android && ./gradlew :feature:player:testDebugUnitTest` — Expected: FAIL, `canMarkKids` unresolved.

- [ ] **Step 3: Implement**

`PlayerMarksState` — add:

```kotlin
    /** False on a kids profile: a child does not approve titles for themselves. */
    val canMarkKids: Boolean = true,
```

`PlayerViewModel` — a flow of whether the chosen profile is a kids profile, joined into `marks`:

```kotlin
        private val onKidsProfile =
            combine(repository.profiles, repository.chosenProfileId) { profiles, chosen ->
                profiles.firstOrNull { it.id == chosen }?.kids == true
            }
```

```kotlin
            combine(openSetId, openFsk, repository.snapshot, onKidsProfile) { setId, fsk, snapshot, kidsProfile ->
                setId?.let {
                    PlayerMarksState(
                        …existing fields…,
                        canMarkKids = !kidsProfile,
                    )
                }
            }
```

`toggleKids` — first line after reading `setId`:

```kotlin
            if (marks.value?.canMarkKids == false) return
```

`PlayerMarks.kt` — wrap the Kids `MarkButton` in `if (marks.canMarkKids) { … }`.

- [ ] **Step 4: Run** `cd android && ./gradlew :feature:player:testDebugUnitTest :ui-mobile:testDebugUnitTest` — Expected: PASS.

- [ ] **Step 5: Commit** `git add android && git commit -m "feat(android-kids): hide the player's Kids mark on a kids profile"`

## Success criteria

On the phone a kids profile's walls hold only allowed titles; switching profile regroups without re-reading the catalog; a kids profile can be created and is labelled; an empty kids library says so.
