package games

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A title's image files, as the night's titles name them. */
data class TitleImages(
    val poster: String?,
    val still: String?,
)

/** A night as built: the [NightInputs.fingerprint] it was built from, whether locked, and the titles' JSON. */
class NightSnapshot(
    val fingerprint: String,
    val kidsLocked: Boolean,
    val titles: String,
) {
    /** Each title's images by key, parsed once per snapshot. */
    val images: Map<String, TitleImages> by lazy {
        val list = JSONArray(titles)
        (0 until list.length()).map(list::getJSONObject).associate { it.getString("key") to TitleImages(it.path("poster"), it.path("still")) }
    }

    private fun JSONObject.path(name: String): String? = if (isNull(name)) null else getString(name)

    companion object {
        /** What a night that could not be answered allows: nothing. */
        val Nothing = NightSnapshot(fingerprint = "", kidsLocked = true, titles = "[]")
    }
}

/**
 * The last night built, kept in one file so a fresh process can answer without building it: a
 * header line (`app_version`, `fingerprint`, `kids_locked`) and then the titles' JSON as the core
 * wrote it. A file another version of this app wrote is not read: its rules may have been different.
 */
class NightSnapshotStore(
    private val dir: File,
    /** This app's version code, written into the file and required of it. */
    val appVersion: Long,
) {
    internal val file = File(dir, "night.snapshot")

    /** `null` with no file, or one that does not read as a night: the caller builds the night instead. */
    fun read(): NightSnapshot? =
        runCatching {
            val (head, titles) = file.readText().split('\n', limit = 2)
            val header = JSONObject(head)
            check(header.getLong("app_version") == appVersion) { "written by another version of this app" }
            NightSnapshot(header.getString("fingerprint"), header.getBoolean("kids_locked"), titles).also { it.images }
        }.getOrNull()

    /** Written whole and then moved into place, so a reader never sees half a night. */
    fun write(snapshot: NightSnapshot) {
        dir.mkdirs()
        val part = File.createTempFile("night", ".part", dir)
        try {
            val header =
                JSONObject().put("app_version", appVersion).put("fingerprint", snapshot.fingerprint).put("kids_locked", snapshot.kidsLocked)
            part.writeText("$header\n${snapshot.titles}")
            check(part.renameTo(file)) { "the night snapshot could not be moved into place" }
        } finally {
            part.delete()
        }
    }
}
