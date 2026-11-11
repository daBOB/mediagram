package games

import android.net.Uri
import android.os.ParcelFileDescriptor
import data.settings.InMemoryTmdbSettings
import org.robolectric.Robolectric
import testing.FakeCore
import testing.FakeCoreProvider
import testing.setSummary
import uniffi.mediagram_core.Profile
import java.io.File
import java.io.FileNotFoundException
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * This app as Spieltisch's provider sees it, under [root]: a grown-up, a kid limited to 6 and one
 * limited to 12, Alien (not kid-safe, poster and still) and Bambi (kid-safe, poster only), and the
 * core answering as the real one does — with the lock on, only kid-safe titles.
 */
internal class NightFixture(
    private val root: File,
) {
    val grownUp = Profile("a", "Ada")
    val kid = Profile("k", "Kim", kids = true, kidsAge = 6u)
    val olderKid = Profile("t", "Tom", kids = true, kidsAge = 12u)
    val core =
        FakeCore().apply {
            profiles = listOf(grownUp, kid, olderKid)
            chosen = "a"
            // A change in the catalog is a new installed index, so a test moves `publishedAt` with it.
            sets = listOf(movie("f0", "0"), movie("f16", "16"))
        }
    var coreProvider = FakeCoreProvider(core)
    val tmdb = InMemoryTmdbSettings()
    val snapshots = File(root, "game-night")
    val alien = title("A", poster = file("alien.jpg", "alien"), still = file("alien-still.jpg", "alien still"), kidsOk = false)
    val bambi = title("B", poster = file("bambi.jpg", "bambi"), still = null, kidsOk = true)

    init {
        core.nightTitles = { config -> if (config.kidsLocked) "[$bambi]" else "[$alien,$bambi]" }
    }

    fun file(
        name: String,
        text: String,
    ): File = File(root, name).apply { writeText(text) }

    private fun title(
        key: String,
        poster: File?,
        still: File?,
        kidsOk: Boolean,
    ) = """{"key":"$key","kind":"film","title":"$key","year":null,"poster":${poster?.let { "\"${it.path}\"" }},""" +
        """"still":${still?.let { "\"${it.path}\"" }},"directors":[],"cast":[],"kids_ok":$kidsOk}"""

    /** As one process of this app at [version] holds it: its own memory, the snapshot on disk shared with every other. */
    fun export(version: Long = 1) = GameNightExport(coreProvider, tmdb, NightSnapshotStore(snapshots, version))

    /** A provider in a fresh process, unless it is handed [export] to share one. */
    fun provider(export: GameNightExport = export()) =
        Robolectric.buildContentProvider(GameNightProvider::class.java).create(GameNightProvider.AUTHORITY).get().also { it.export = export }

    fun uri(path: String): Uri = Uri.parse("content://${GameNightProvider.AUTHORITY}/$path")

    /** The night row: kids_locked and the titles' JSON. */
    fun night(provider: GameNightProvider = provider()): Pair<Int, String> =
        checkNotNull(provider.query(uri("night"), null, null, null, null)).use {
            assertTrue(it.moveToFirst())
            it.getInt(it.getColumnIndexOrThrow(GameNightProvider.KIDS_LOCKED)) to it.getString(it.getColumnIndexOrThrow(GameNightProvider.TITLES))
        }

    fun read(
        path: String,
        provider: GameNightProvider = provider(),
    ) = ParcelFileDescriptor.AutoCloseInputStream(provider.openFile(uri(path), "r")).bufferedReader().readText()

    fun refused(
        path: String,
        provider: GameNightProvider = provider(),
    ) {
        assertFailsWith<FileNotFoundException>(path) { provider.openFile(uri(path), "r") }
    }
}

/** A film in the catalog as the core lists it. */
internal fun movie(
    id: String,
    fsk: String?,
    anime: Boolean = false,
) = setSummary(setId = id, kind = "movie", title = id, fsk = fsk, anime = anime)
