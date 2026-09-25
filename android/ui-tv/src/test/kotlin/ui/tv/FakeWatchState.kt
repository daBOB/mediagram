package ui.tv

import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import model.ListOfSets
import model.Profile
import model.WatchSnapshot
import model.Watched

/**
 * The minimum [WatchStateRepository] a test's own `ProfileViewModel` needs:
 * profiles and a chosen id it can read back immediately, with every write a
 * no-op — no `CoreClient`, no Telegram account, the same rule [TvAppFixture]
 * already follows for `SetupViewModel`. Kept separate from the
 * `DefaultWatchStateRepository` that fixture builds for its own setup
 * plumbing: that one is backed by a mocked `CoreClient` stubbed only for the
 * setup calls `SetupViewModel` makes, and `ProfileViewModel.reload()` would
 * reach calls on it nothing there stubs.
 */
internal class FakeWatchStateRepository(
    profiles: List<Profile> = emptyList(),
    chosenProfileId: String? = null,
    watch: WatchSnapshot = WatchSnapshot.Empty,
) : WatchStateRepository {
    private val mutableProfiles = MutableStateFlow(profiles)
    override val profiles: StateFlow<List<Profile>> = mutableProfiles.asStateFlow()

    private val mutableChosenProfileId = MutableStateFlow(chosenProfileId)
    override val chosenProfileId: StateFlow<String?> = mutableChosenProfileId.asStateFlow()

    // Held for "Mark finished" alone: clearing a position and stamping a
    // finish are what it writes, so those two land here and every other
    // write stays a no-op — a title played in a walk must not start
    // appearing on Continue under a test that never asked for it.
    private val mutableSnapshot = MutableStateFlow(watch)
    override val snapshot: StateFlow<WatchSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun chooseProfile(id: String): Boolean {
        if (mutableProfiles.value.none { it.id == id }) return false
        mutableChosenProfileId.value = id
        return true
    }

    override suspend fun createProfile(
        name: String,
        kids: Boolean,
    ): Profile? {
        val created = Profile(id = "created-${mutableProfiles.value.size}", name = name, kids = kids)
        mutableProfiles.value = mutableProfiles.value + created
        return created
    }

    override suspend fun setProgress(
        setId: String,
        at: Double,
        duration: Double?,
    ) = Unit

    override suspend fun clearProgress(setId: String) {
        mutableSnapshot.value = mutableSnapshot.value.let { it.copy(progress = it.progress.filterNot { p -> p.setId == setId }) }
    }

    override suspend fun setWatched(
        setId: String,
        finished: Boolean,
    ) {
        val rest = mutableSnapshot.value.watched.filterNot { it.setId == setId }
        mutableSnapshot.value = mutableSnapshot.value.copy(watched = if (finished) rest + Watched(setId, finishedAt = 1) else rest)
    }

    override suspend fun deleteProfile(id: String): Boolean {
        if (mutableProfiles.value.none { it.id == id }) return false
        mutableProfiles.value = mutableProfiles.value.filterNot { it.id == id }
        if (mutableChosenProfileId.value == id) mutableChosenProfileId.value = null
        return true
    }

    override suspend fun setWatchlisted(
        setId: String,
        listed: Boolean,
    ) = Unit

    override suspend fun setKids(
        setId: String,
        marked: Boolean,
    ) = Unit

    override suspend fun createList(name: String): ListOfSets? = null

    override suspend fun renameList(
        id: String,
        name: String,
    ): Boolean = false

    override suspend fun deleteList(id: String): Boolean = false

    override suspend fun setInList(
        id: String,
        setId: String,
        included: Boolean,
    ): Boolean = false

    override suspend fun reload() = Unit

    override fun invalidate() {
        mutableProfiles.value = emptyList()
        mutableChosenProfileId.value = null
    }
}

/** A [WatchSync] that never talks to a core — `ProfileViewModel.settle()` only needs [awaitFirstRound] to return promptly. */
internal object NoopWatchSync : WatchSync {
    override fun onForeground() = Unit

    override fun onBackground() = Unit

    override fun soon() = Unit

    override suspend fun awaitFirstRound() = Unit
}
