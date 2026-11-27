package homerows

import data.CoreProvider
import data.toMediaSet
import data.toModel
import model.MediaSet
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The TV home screen's rows, read from the core itself on every ask — as Spieltisch's night is —
 * so a person switched on another screen or by sync is seen at once. Nothing on failure.
 */
@Singleton
class HomeRowsSource
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
    ) {
        /** The chosen person's rows; [HomeRows.None] with nobody chosen or no core. */
        suspend fun rows(): HomeRows = read().first

        /** The [which] (`poster` or `still`) file of [setId], only while that title is on the rows. */
        suspend fun image(
            setId: String,
            which: String,
        ): File? {
            val (rows, sets) = read()
            if (setId !in rows.imageIds) return null
            val set = sets[setId] ?: return null
            val path =
                when (which) {
                    "poster" -> set.posterPath
                    "still" -> set.backdropPath
                    else -> null
                }
            return path?.let(::File)?.takeIf(File::isFile)
        }

        private suspend fun read(): Pair<HomeRows, Map<String, MediaSet>> {
            val core = coreProvider.coreOrNull() ?: return HomeRows.None to emptyMap()
            val id = core.chosenProfile() ?: return HomeRows.None to emptyMap()
            val person = core.profiles().find { it.id == id }?.toModel() ?: return HomeRows.None to emptyMap()
            val sets = core.listSets().map { it.toMediaSet() }
            return homeRowsOf(person, sets, core.snapshot(id).toModel()) to sets.associateBy(MediaSet::setId)
        }
    }
