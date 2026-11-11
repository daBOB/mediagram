package games

import android.util.Log
import data.CoreProvider
import data.settings.TmdbSettings
import data.toMediaSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import uniffi.mediagram_core.CoreInterface
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** A night as Spieltisch hosts it: whether the profile is held to kid-safe titles, and the titles as JSON. */
data class ExportedNight(
    val kidsLocked: Boolean,
    val titles: String,
) {
    companion object {
        /** Nobody to ask about yet: locked, and no titles at all. */
        val Locked = ExportedNight(kidsLocked = true, titles = "[]")
    }
}

/**
 * What this app hands Spieltisch: the active profile's night under its kids and section rules
 * ([gameNightConfig]), and the images of the titles that night holds — no others. A night
 * is served only for the [NightInputs] it was built from; when they differ it is built again.
 */
@Singleton
class GameNightExport
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        private val tmdb: TmdbSettings,
        private val snapshots: NightSnapshotStore,
    ) {
        /** One read or build at a time, so asks that come together build once. */
        private val lock = Mutex()

        /** The night last read or built, held under [lock]. */
        private var kept: NightSnapshot? = null

        /** What `/night` last answered, set under [lock]: a running night keeps the titles it started with, as a night hosted here does. */
        @Volatile
        private var served: NightSnapshot? = null

        /**
         * How long the work under [lock] may take before it answers locked and lets the next ask in:
         * a core that goes away mid-build (a sign-out) must not hold every later ask. A test shortens it.
         */
        internal var lockedWorkLimit = 30.seconds

        /** [ExportedNight.Locked] when nobody is chosen or anything fails: no night unlocks nothing. */
        suspend fun night(): ExportedNight {
            val night = current()
            Log.i(TAG, "answered the night: kids_locked=${night?.kidsLocked ?: true}, ${night?.titles?.length ?: 2} chars")
            return night?.let { ExportedNight(it.kidsLocked, it.titles) } ?: ExportedNight.Locked
        }

        /** The poster or still file of the title [key] names, `null` unless the served night holds it with that image. */
        suspend fun image(
            key: String,
            which: String,
        ): File? {
            if (which != "poster" && which != "still") return null
            val title = servedNight().images[key] ?: return null
            return (if (which == "poster") title.poster else title.still)?.let(::File)
        }

        /** The title's textless poster, fetched with this app's TMDB key; `null` unless the served night holds it. */
        suspend fun textless(key: String): File? {
            if (key !in servedNight().images) return null
            return coreProvider.coreOrNull()?.textlessPoster(key, tmdb.read())?.let(::File)
        }

        /** A process that has served no night yet, having started since, answers one first. */
        private suspend fun servedNight(): NightSnapshot = served ?: night().let { served ?: NightSnapshot.Nothing }

        /** The night to answer, `null` for locked; [served] is set before the next ask can come in. */
        private suspend fun current(): NightSnapshot? =
            lock.withLock {
                val night =
                    try {
                        withTimeout(lockedWorkLimit) { answer() }
                    } catch (e: TimeoutCancellationException) {
                        // Every ask past the limit starts over, so a box that builds slower than this never gets films.
                        Log.w(TAG, "the night took longer than $lockedWorkLimit; answered locked", e)
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        Log.w(TAG, "the night could not be answered", e)
                        null
                    }
                served = night ?: NightSnapshot.Nothing
                night
            }

        /** Under [lock]. */
        private suspend fun answer(): NightSnapshot? {
            val began = System.nanoTime()
            val ms = { (System.nanoTime() - began) / 1_000_000 }
            val core = coreProvider.coreOrNull() ?: return null
            val inputs = core.nightInputs(snapshots.appVersion) ?: return null
            val fits = { night: NightSnapshot? -> night?.takeIf { it.fingerprint == inputs.fingerprint } }
            (fits(kept) ?: fits(snapshots.read()))?.let {
                kept = it
                Log.i(TAG, "night from the snapshot in ${ms()} ms")
                return it
            }
            val built = build(core, inputs)
            // Built from inputs that moved meanwhile, it answers for nobody; asked again, it is built afresh.
            if (core.nightInputs(snapshots.appVersion)?.fingerprint != inputs.fingerprint) {
                Log.i(TAG, "night built in ${ms()} ms, discarded: what it was built from changed")
                return null
            }
            kept = built
            // A snapshot that cannot be kept costs the next fresh process a build, not this night.
            runCatching { snapshots.write(built) }
            Log.i(TAG, "night built in ${ms()} ms")
            return built
        }

        /** From [core], the one the inputs were read from; looking it up again could wait for a sign-in. */
        private suspend fun build(
            core: CoreInterface,
            inputs: NightInputs,
        ): NightSnapshot {
            val config = gameNightConfig(core.listSets().map { it.toMediaSet() }, inputs.profile, inputs.marks)
            // With the lock on, the core leaves out every title that is not kid-safe.
            val titles = core.gameNightTitles(config)
            return NightSnapshot(inputs.fingerprint, config.kidsLocked, titles)
        }

        private companion object {
            const val TAG = "GameNightExport"
        }
    }
