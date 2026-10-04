package testing

import data.DefaultWatchStateRepository
import data.WatchStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import model.Profile
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * The app's own [DefaultWatchStateRepository] over a stateful [FakeCore] —
 * what a feature test reads and writes watch state through, so every write
 * follows the core's rules ([CoreContract] holds the fake to them against the
 * real core) instead of a test's own copy of those rules, which is how a
 * finish that left its position behind once passed every test.
 *
 * [profiles] are added to [core] and [chosen] becomes its choice before the
 * repository's first reload. A write needs a chosen profile and does nothing
 * without one, so `chosen = null` is how a test checks exactly that. [seed]
 * then runs against [repository] — the writes the app itself makes — so a
 * test starts from state the core produced, stamps and list ids included.
 *
 * Everything runs on [Dispatchers.Unconfined]: a write has landed in [core]
 * and in [WatchStateRepository.snapshot] by the time it returns.
 *
 * - [core] holds the rows. Read it to check what a write left; write it
 *   directly and call `repository.reload()` to stand in for a sync round
 *   that pulled rows in from another device.
 * - [provider] hands [core] out. Its [FakeCoreProvider.beforeCore] is where a
 *   test fails a call (throw), fails a later one (count, then throw) or holds
 *   one open (suspend) — the core's own state writes never throw.
 */
class WatchStateFixture(
    profiles: List<Profile> = listOf(VIEWER),
    chosen: String? = profiles.firstOrNull()?.id,
    val core: FakeCore = FakeCore(),
    seed: suspend WatchStateRepository.() -> Unit = {},
) {
    val provider = FakeCoreProvider(core)
    val repository: WatchStateRepository = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)

    init {
        core.profiles += profiles.map { CoreProfile(it.id, it.name, it.kids) }
        core.chosen = chosen
        runBlocking {
            repository.reload()
            repository.seed()
        }
    }

    companion object {
        /** Who a fixture watches as unless told otherwise: `p1`, not a kids profile. */
        val VIEWER = Profile("p1", "Viewer")
    }
}
