package testing

import uniffi.mediagram_core.ListRow
import uniffi.mediagram_core.PreferenceRow
import uniffi.mediagram_core.ProgressRow
import uniffi.mediagram_core.StateSnapshot
import uniffi.mediagram_core.WatchedRow

/** `preferences.rs`'s `MAX_PREFERENCE`: the cap on each of a preference's three strings. */
private const val MAX_PREFERENCE = 200

/**
 * The core's `SYNCED_NAMES` (`state/record/preference_record.rs`): the names
 * that travel between devices, which `preferences::set` refuses to forget.
 */
private val SYNCED_PREFERENCES = setOf("subtitle", "cue-size", "cue-backing", "cue-offset")

/**
 * A counter that only ever goes up, one tick per call. [FakeCore]'s default
 * clock: every write gets its own later "now" without a test having to wait
 * on the wall clock, and two writes in the same test can never tie the way
 * two calls inside the same real millisecond could.
 */
fun monotonicClock(): () -> Long {
    var last = 0L
    return { ++last }
}

/**
 * [FakeCore]'s watch-state half — progress, watched marks, the watchlist,
 * Kids, the editor's choice, collections and per-show preferences — split
 * into its own file only to keep `FakeCore.kt` under the line limit. Mirrors
 * the rules `crates/mediagram-core/src/state/rows.rs`, `editors_choice.rs`,
 * `lists.rs` and `preferences.rs` apply to the real core's SQLite tables,
 * against [now] standing in for their `now_ms()`.
 *
 * [exists] stands in for the foreign key every per-profile table (all but
 * Kids and the editor's choice) carries to `profiles(id)`: a write for a
 * profile nobody created is dropped here the same way a constraint
 * violation drops it there; [forget] is a profile's own removal cascading
 * the same way `ON DELETE CASCADE` does on the real tables.
 *
 * One instance per [FakeCore]: every public method is `@Synchronized`
 * because `state_db` serializes every call behind one `Mutex`
 * (`state/mod.rs`), a guarantee a test fixture running writes on more than
 * one thread still needs.
 */
class FakeWatchState(
    private val now: () -> Long = monotonicClock(),
    private val exists: (String) -> Boolean = { true },
) {
    private class Progress(var at: Double, var duration: Double?, var updatedAt: Long)

    private class Watched(var finishedAt: Long, var removedAt: Long?)

    /**
     * A mark that can be taken back: set, and possibly later removed — a
     * watchlist entry and a Kids mark are this same shape; only a Kids mark
     * says it is [fromSix].
     */
    private class Mark(val at: Long, var removedAt: Long? = null, val fromSix: Boolean = false)

    private class Collection(var name: String, val items: MutableList<String> = mutableListOf(), var deleted: Boolean = false)

    private val progressByProfile = mutableMapOf<String, LinkedHashMap<String, Progress>>()
    private val watchedByProfile = mutableMapOf<String, LinkedHashMap<String, Watched>>()
    private val watchlistByProfile = mutableMapOf<String, LinkedHashMap<String, Mark>>()
    private val collectionsByProfile = mutableMapOf<String, LinkedHashMap<String, Collection>>()

    /** Keyed by `scope` then `name`, the real table's primary key after `profile_id`. */
    private val preferencesByProfile = mutableMapOf<String, LinkedHashMap<Pair<String, String>, String>>()

    // Not scoped to any profile, like the tables they mirror.
    private val kidsMarks = linkedMapOf<String, Mark>()
    private var editorsChoiceSetId: String? = null

    private var nextListId = 1

    @Synchronized
    fun snapshot(profileId: String): StateSnapshot =
        StateSnapshot(
            progress = progressFor(profileId),
            watched = watchedFor(profileId),
            watchlist = watchlistFor(profileId),
            kids = liveKids(),
            collections = collectionsFor(profileId),
            editorsChoice = editorsChoiceSetId,
            kidsFromSix = liveKids(fromSixOnly = true),
        )

    /** Drops everything a deleted profile owned — the same cascade `ON DELETE CASCADE` runs on the real tables. Kids and the editor's choice outlive it; neither belongs to any profile. */
    @Synchronized
    fun forget(profileId: String) {
        progressByProfile.remove(profileId)
        watchedByProfile.remove(profileId)
        watchlistByProfile.remove(profileId)
        collectionsByProfile.remove(profileId)
        preferencesByProfile.remove(profileId)
    }

    private fun progressFor(profileId: String): List<ProgressRow> =
        (progressByProfile[profileId] ?: emptyMap())
            .entries
            .sortedByDescending { it.value.updatedAt }
            .map { (setId, row) -> ProgressRow(setId, row.at, row.duration, row.updatedAt) }

    @Synchronized
    fun setProgress(profileId: String, setId: String, at: Double, duration: Double?) {
        if (!exists(profileId)) return
        progressByProfile.getOrPut(profileId) { linkedMapOf() }[setId] = Progress(at.coerceAtLeast(0.0), duration, now())
    }

    @Synchronized
    fun clearProgress(profileId: String, setId: String) {
        progressByProfile[profileId]?.remove(setId)
    }

    private fun watchedFor(profileId: String): List<WatchedRow> =
        (watchedByProfile[profileId] ?: emptyMap())
            .entries
            .filter { it.value.removedAt == null }
            .sortedByDescending { it.value.finishedAt }
            .map { (setId, row) -> WatchedRow(setId, row.finishedAt) }

    /**
     * Finishing always re-stamps, even a title already finished — a fake
     * that treated a repeat "true" as a no-op would hide the same regression
     * `set_watched`'s `MAX` clamp exists to prevent in the real core. A
     * genuinely first finish stores `now()` plain, same as the real
     * `INSERT`; the clamp only applies once a row — live or tombstoned —
     * already exists, same as the real `ON CONFLICT`. Taking a mark back
     * never touches [progressByProfile]; only finishing does.
     */
    @Synchronized
    fun setWatched(profileId: String, setId: String, finished: Boolean) {
        if (!exists(profileId)) return
        val forProfile = watchedByProfile.getOrPut(profileId) { linkedMapOf() }
        if (finished) {
            val existing = forProfile[setId]
            val finishedAt = existing?.let { maxOf(now(), (it.removedAt ?: 0L) + 1) } ?: now()
            forProfile[setId] = Watched(finishedAt, removedAt = null)
            progressByProfile[profileId]?.remove(setId)
        } else {
            val existing = forProfile[setId] ?: return
            if (existing.removedAt != null) return
            existing.removedAt = maxOf(now(), existing.finishedAt + 1)
        }
    }

    private fun watchlistFor(profileId: String): List<String> =
        (watchlistByProfile[profileId] ?: emptyMap())
            .entries
            .filter { it.value.removedAt == null }
            .sortedByDescending { it.value.at }
            .map { it.key }

    @Synchronized
    fun setWatchlisted(profileId: String, setId: String, listed: Boolean) {
        if (!exists(profileId)) return
        val forProfile = watchlistByProfile.getOrPut(profileId) { linkedMapOf() }
        markOrUnmark(forProfile, setId, listed) { Mark(now()) }
    }

    private fun liveKids(fromSixOnly: Boolean = false): List<String> =
        kidsMarks.entries
            .filter { it.value.removedAt == null && (it.value.fromSix || !fromSixOnly) }
            .sortedByDescending { it.value.at }
            .map { it.key }

    /**
     * As `rows::set_kids`: [age] 6 marks "from 6", any other "from 12" — the
     * stricter reading — and `null` takes the mark off. Marking again at the
     * age a live mark has changes nothing; a new age is a new mark.
     */
    @Synchronized
    fun setKids(setId: String, age: UByte?) {
        val fromSix = age?.toInt() == 6
        val live = kidsMarks[setId]?.takeIf { it.removedAt == null }
        if (age != null && live?.fromSix != fromSix) {
            kidsMarks[setId] = Mark(now(), fromSix = fromSix)
        } else if (age == null && live != null) {
            live.removedAt = now()
        }
    }

    /**
     * [setWatchlisted]'s shape: adding is idempotent (a live mark does not
     * move for a second `true`, matching the `WHERE removed_at IS NOT NULL`
     * guard on the real table's upsert — only a tombstoned or absent mark is
     * (re)created), and only a live mark can be taken back.
     */
    private fun markOrUnmark(marks: MutableMap<String, Mark>, setId: String, marked: Boolean, onNew: () -> Mark) {
        if (marked) {
            val existing = marks[setId]
            if (existing == null || existing.removedAt != null) marks[setId] = onNew()
        } else {
            val existing = marks[setId] ?: return
            if (existing.removedAt == null) existing.removedAt = now()
        }
    }

    @Synchronized
    fun editorsChoice(): String? = editorsChoiceSetId

    /** Pinning replaces whichever pick was live; unpinning clears it. Only one pick is ever live in this fake, so there is no other row to retire. */
    @Synchronized
    fun setEditorsChoice(setId: String, marked: Boolean) {
        editorsChoiceSetId = if (marked) setId else null
    }

    private fun collectionsFor(profileId: String): List<ListRow> =
        (collectionsByProfile[profileId] ?: emptyMap())
            .filterValues { !it.deleted }
            .map { (id, collection) -> ListRow(id, collection.name, collection.items.toList()) }

    @Synchronized
    fun createCollection(profileId: String, name: String): ListRow? {
        if (!exists(profileId)) return null
        val clean = cleanListName(name) ?: return null
        val id = "list-${nextListId++}"
        collectionsByProfile.getOrPut(profileId) { linkedMapOf() }[id] = Collection(clean)
        return ListRow(id, clean, emptyList())
    }

    @Synchronized
    fun renameCollection(profileId: String, id: String, name: String): Boolean {
        if (!exists(profileId)) return false
        val clean = cleanListName(name) ?: return false
        val collection = ownedCollection(profileId, id) ?: return false
        collection.name = clean
        return true
    }

    @Synchronized
    fun deleteCollection(profileId: String, id: String): Boolean {
        if (!exists(profileId)) return false
        val collection = ownedCollection(profileId, id) ?: return false
        collection.deleted = true
        return true
    }

    @Synchronized
    fun setInCollection(profileId: String, id: String, setId: String, included: Boolean): Boolean {
        if (!exists(profileId)) return false
        val collection = ownedCollection(profileId, id) ?: return false
        if (included) {
            if (setId !in collection.items) collection.items += setId
        } else {
            collection.items -= setId
        }
        return true
    }

    private fun ownedCollection(profileId: String, id: String): Collection? =
        collectionsByProfile[profileId]?.get(id)?.takeUnless { it.deleted }

    /**
     * Sorted by scope, then name: `list_for` names no order, but SQLite answers
     * it through the table's primary-key index, which is that order — so a
     * test comparing several rows sees the same list from either core.
     */
    @Synchronized
    fun preferences(profileId: String): List<PreferenceRow> =
        (preferencesByProfile[profileId] ?: emptyMap())
            .map { (key, value) -> PreferenceRow(key.first, key.second, value) }
            .sortedWith(compareBy({ it.scope }, { it.name }))

    /**
     * As `preferences::set`: `false` when [scope] or [name] trims to nothing,
     * or when a value is written for a profile nobody created (the real insert
     * breaks the foreign key, which the API answers as `false`).
     *
     * Forgetting — a `null` or blank [value] — answers `false` and keeps the
     * row for a synced name ([SYNCED_PREFERENCES]): that row carries no
     * tombstone, so a delete would come back from every device still holding
     * it. Any other name is a plain delete, which no foreign key refuses, so
     * it answers `true` for any profile.
     */
    @Synchronized
    fun setPreference(profileId: String, scope: String, name: String, value: String?): Boolean {
        val key = (shortPreference(scope) ?: return false) to (shortPreference(name) ?: return false)
        val clean = value?.let(::shortPreference)
        if (clean == null) {
            if (key.second in SYNCED_PREFERENCES) return false
            preferencesByProfile[profileId]?.remove(key)
            return true
        }
        if (!exists(profileId)) return false
        preferencesByProfile.getOrPut(profileId) { linkedMapOf() }[key] = clean
        return true
    }

    /** As `preferences::short`: trimmed and capped, never whitespace-collapsed — the player parses these back. */
    private fun shortPreference(value: String): String? = value.trim().take(MAX_PREFERENCE).takeIf { it.isNotEmpty() }

    /** As `clean_name` (`crates/mediagram-core/src/state/profiles.rs`), reused there for both profile and list names. */
    private fun cleanListName(name: String): String? =
        name.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ").takeIf { it.isNotEmpty() }
}
