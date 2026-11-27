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
        /** The last read and when it was made: a card's pictures are asked right after its row. */
        @Volatile
        private var recent: Pair<Long, Pair<HomeRows, Map<String, MediaSet>>>? = null

        /** Who is watching here, from the chosen profile alone: asked often, so the library is not read for it. */
        suspend fun person(): HomePerson? {
            val core = coreProvider.coreOrNull() ?: return null
            val id = core.chosenProfile() ?: return null
            return core.profiles().find { it.id == id }?.toModel()?.let { HomePerson(it.id, it.name, it.kids) }
        }

        /** The chosen person's rows, read afresh; [HomeRows.None] with nobody chosen or no core. */
        suspend fun rows(): HomeRows = read().also { recent = System.currentTimeMillis() to it }.first

        /** The [which] (`poster` or `still`) file of [setId], only while that title is on the rows. */
        suspend fun image(
            setId: String,
            which: String,
        ): File? {
            val kept = recent?.takeIf { System.currentTimeMillis() - it.first < PICTURES_FOLLOW_ROWS_MS }?.second
            val (rows, sets) = kept ?: read().also { recent = System.currentTimeMillis() to it }
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
    
        private companion object {
            /** How long a read answers the pictures asked after it: a Home refresh asks them within a second or two. */
            const val PICTURES_FOLLOW_ROWS_MS = 10_000L
        }
    }
