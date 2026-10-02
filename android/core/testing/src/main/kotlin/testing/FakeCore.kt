package testing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import uniffi.mediagram_core.AccountSummary
import uniffi.mediagram_core.AppRelease
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.CoreException
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.FranchiseRecord
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.ListRow
import uniffi.mediagram_core.PeopleHitRecord
import uniffi.mediagram_core.PersonRecord
import uniffi.mediagram_core.PreferenceRow
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.SearchHit
import uniffi.mediagram_core.SessionSummary
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.StateSnapshot
import uniffi.mediagram_core.SyncOutcome
import uniffi.mediagram_core.TitleCreditsRecord
import uniffi.mediagram_core.TitleInfo

/**
 * The one fake of the generated core every test in this app builds on.
 * Where a test never touches a call, its answer is the harmless default the
 * old per-module fakes agreed on (empty list, `null`, `false`, `Unit`).
 * Where a test does touch one, the answer matches what the real core
 * promises rather than what is convenient: [CoreException.NotFound] past a
 * set's end or for an unknown set id (see [knownSetIds]), the same
 * exception for `revokeSession("0")` (see
 * `crates/mediagram-core/src/api/sessions.rs`).
 *
 * Every *configurable* failure field ([listFailure] and the rest below the
 * constructor's main block) takes a plain `Throwable`, not specifically a
 * [CoreException] — a test injects whichever type its own case is about,
 * `CoreException` included where `coreSentence()` is what is under test.
 * The few failures this fake raises on its own rather than being told to —
 * [refreshFails], [requestCodeFails], [signInFailures]/[passwordFailures]
 * running out, and [accountAnswer] left `null` — are a plain
 * [IllegalStateException] via [error], the same as the per-module fakes
 * this one replaces raised for the same cases.
 *
 * `close()` is [AutoCloseable]'s, not [CoreInterface]'s: the generated
 * `Core` carries both, and `CoreProvider` (core:data) closes through the
 * second interface now that the signed `CoreClient` wrapper is gone.
 *
 * Every constructor parameter is also a `var` — a test that needs to change
 * an answer mid-run (a listing that fails once then succeeds, a session
 * that authorizes after `signIn`) reassigns the field directly rather than
 * standing up a second fake.
 */
class FakeCore(
    var sets: List<SetSummary> = emptyList(),
    /** What [refreshLibrary] and [refreshCatalog] answer with by default, converted to the interface's unsigned type. */
    var refreshResult: Long = 0L,
    var libraries: List<LibraryChoice> = emptyList(),
    /**
     * What each reading of [catalogFacts] says the installed snapshot was
     * pushed at, in order. A refresh takes one reading either side of
     * itself, so two entries are a push that landed; the last entry stands
     * for every reading after it, so one entry is a library that never
     * changes. Unused once [catalogFactsAnswer] is replaced directly.
     */
    var publishedAt: List<Long?> = listOf(null),
    /** The message [refreshLibrary] raises a plain [IllegalStateException] with, or `null` to succeed. */
    var refreshFails: String? = null,
    /** Whether [refreshLibrary] is cancelled rather than finishing or failing. */
    var refreshCancels: Boolean = false,
    /** What each wait in [nextLibraryEvent] answers, in order; past the end, a wait waits for ever, as a quiet channel does. */
    var events: List<Result<LibraryEvent>> = emptyList(),
    /** What [account] answers when [accountFailure] is unset. */
    var accountAnswer: AccountSummary? = AccountSummary("A Viewer", "viewer"),
    /** What [account] throws instead of [accountAnswer], or `null` to answer normally. */
    var accountFailure: Throwable? = null,
    var searchHits: List<SearchHit> = emptyList(),
    var creditsAnswer: TitleCreditsRecord = TitleCreditsRecord(cast = emptyList(), crew = emptyList()),
    /** What [person] answers, keyed by the id asked. */
    var people: Map<Long, PersonRecord> = emptyMap(),
    var franchiseRecords: List<FranchiseRecord> = emptyList(),
    var peopleHits: List<PeopleHitRecord> = emptyList(),
    /** What [fetchPortrait] answers, keyed by the id asked. */
    var portraits: Map<Long, String> = emptyMap(),
    /**
     * Whether a login has completed. Most of this app's tests build a core
     * that already is; feature/setup's own fixture defaults this `false`,
     * since a device mid setup is what most of its tests are about.
     */
    var authorized: Boolean = true,
    /** What [totalSize] answers for a known set; also what a [read] request is clamped against. */
    var totalSize: Long = 0L,
    /** How [read] renders a known set's bytes at an offset, after clamping to [totalSize]. */
    var bytesOf: (offset: Long, len: Int) -> ByteArray = { _, len -> ByteArray(len) },
    /**
     * Which set ids [read]/[totalSize] recognise at all — exact membership
     * once set. Left `null`, [isKnownSet] derives it instead: from [sets]
     * when any are configured, or otherwise from whether [totalSize] itself
     * is (a test that never names a catalog but does configure bytes is
     * asking for one implicit set, reachable by whatever id it reads under
     * — the shape every playback test that reads through this fake uses).
     * A completely unconfigured fake therefore has no known set at all,
     * matching the real core's own empty catalog.
     */
    var knownSetIds: Set<String>? = null,
    var signInOutcome: AuthOutcome = AuthOutcome.DONE,
    var requestCodeFails: Boolean = false,
    /** How many times in a row [signIn] rejects the code before accepting it. */
    var signInFailures: Int = 0,
    /** How many times in a row [checkPassword] rejects the password before accepting it. */
    var passwordFailures: Int = 0,
    var datacenter: Int? = 4,
    var sessionsAnswer: List<SessionSummary> = emptyList(),
    /** What [fetchMissing] answers when [failure] is unset. */
    var report: FetchReport = FetchReport(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u),
    /** What [fetchMissing] throws instead of answering [report], or `null` to answer normally. */
    var failure: Throwable? = null,
    /** Holds [fetchMissing] suspended until completed — the only way to prove a second call while one is in flight is refused. */
    var gate: CompletableDeferred<Unit>? = null,
    /** What [close] throws instead of succeeding, or `null` — the provider's close fence retries on failure. */
    var closeFailure: Throwable? = null,
    /** What [retireLocalState] throws instead of succeeding, or `null` — the provider's close fence retries on failure. */
    var retireLocalStateFailure: Throwable? = null,
    // Failures a test turns on mid-run and usually back off again; one per
    // call this app's tests actually inject a failure into.
    var listFailure: Throwable? = null,
    var installFailure: Throwable? = null,
    var sessionsFailure: Throwable? = null,
    var revokeFailure: Throwable? = null,
    var requestFailure: Throwable? = null,
    var signInFailure: Throwable? = null,
    var passwordFailure: Throwable? = null,
    var profilesFailure: Throwable? = null,
    /**
     * What [FakeWatchState] reads as "now" for every progress, watched,
     * watchlist, Kids, editor's choice and collection write. Reassignable
     * mid-test the same as every other field above — a test that must
     * control ordering across two writes replaces it directly; everything
     * else gets a plain monotonic count, which needs no test to move it and
     * can never tie the way two writes inside the same real millisecond
     * could. Two writes given equal timestamps sort by insertion order here;
     * the real core's order for a tie is unspecified.
     */
    var clock: () -> Long = monotonicClock(),
) : FakeCoreHandle {
    // Wrapped rather than passed straight through: `clock` is a `var`, and a
    // lambda over it keeps reading whatever it holds now, including after a
    // test reassigns it mid-run. `exists` reads `profiles` below the same
    // way — a lambda, not the list itself, so it always sees this fake's
    // current profiles rather than whatever the list held when this field
    // was built (before `profiles` even has its own initial value).
    private val watchState = FakeWatchState(now = { clock() }, exists = { id -> profiles.any { it.id == id } })

    /** Every profile this fake knows about. Written directly to seed a test, or grown through [createProfile]. */
    var profiles: List<Profile> = emptyList()

    /** Who this fake reports as chosen, read back by [chosenProfile]. */
    var chosen: String? = null

    var signedOut: Boolean = false
        private set

    /** How many times [signOut] actually ran. */
    var signOutCalls: Int = 0
        private set

    var searchedFor: String? = null
        private set

    /** How many times [nextLibraryEvent] has been waited on, and for which handle last. */
    var eventCalls: Int = 0
        private set
    var eventHandle: String? = null
        private set

    /** Which handle the last refresh was asked for, or `null` if none was. */
    var refreshedHandle: String? = null
        private set

    /** How many times [listSets] actually ran — a test's way of proving a caller did not list the whole catalog to find one set. */
    var listSetsCalls: Int = 0
        private set

    /** Every id [mediaSet] was asked for, in order. */
    val mediaSetCalls: MutableList<String> = mutableListOf()

    /** Every offset [read] was actually asked for, in order — what a chunk-alignment test checks against. */
    val requestedOffsets: MutableList<Long> = mutableListOf()

    /** How many times [read] was actually asked, which is the cost a chunking test counts. */
    var reads: Int = 0
        private set

    var requestCodeCalls: Int = 0
        private set

    /** Every phone number [requestCode] was actually asked for, in order. */
    val requestedPhones: MutableList<String> = mutableListOf()

    private var signInAttempts = 0
    private var passwordAttempts = 0

    /** Every id [revokeSession] actually revoked, in order — a refused id ("0") never lands here. */
    val revokedIds: MutableList<String> = mutableListOf()

    var datacenterReads: Int = 0
        private set

    /** How many times [isAuthorized] was actually asked. */
    var authorizedReads: Int = 0
        private set

    /** How many times [fetchMissing] actually ran — a call an in-flight guard refused never increments this. */
    var fetchCalls: Int = 0
        private set
    var lastKey: String? = null
        private set
    var lastLanguage: String? = null
        private set

    private var readings = 0

    /**
     * What [catalogFacts] actually answers. Defaults to reading [publishedAt]
     * in order — the shape every push-time test relies on — a test that
     * must fail, hang, or change its answer mid-run replaces this directly.
     */
    var catalogFactsAnswer: suspend () -> CatalogFacts = {
        CatalogFacts("channel", 0uL, 0uL, 0u, publishedAt[minOf(readings++, publishedAt.lastIndex)])
    }

    /** How many times [catalogFacts] actually ran, regardless of how many times [catalogFactsAnswer] was replaced. */
    var catalogFactsCalls: Int = 0
        private set

    /** What [requestCode] answers once call-counting and the failure fields above have run. */
    var requestCodeAnswer: (phone: String) -> String = { "token" }

    /**
     * What [signIn] answers once call-counting and [signInFailure]/
     * [signInFailures] have run. Overridable for a fixture that must also
     * flip [authorized] on a successful sign-in, which this default never
     * does on its own.
     */
    var signInAnswer: suspend (token: String, code: String) -> AuthOutcome = { _, _ -> signInOutcome }

    /** As [signInAnswer], for [checkPassword]. */
    var checkPasswordAnswer: suspend (password: String) -> Unit = {}

    /**
     * What [refreshLibrary] answers once call-counting and the failure
     * fields above have run. Overridable for a fixture that must await
     * something before answering, such as a picker holding an install open.
     */
    var refreshLibraryAnswer: suspend (handle: String) -> Long = { refreshResult }

    override fun isAuthorized(): Boolean {
        authorizedReads++
        return authorized
    }

    override suspend fun requestCode(phone: String): String {
        requestCodeCalls++
        requestedPhones += phone
        requestFailure?.let { throw it }
        if (requestCodeFails) error("could not request a code")
        return requestCodeAnswer(phone)
    }

    override suspend fun signIn(token: String, code: String): AuthOutcome {
        signInAttempts++
        signInFailure?.let { throw it }
        if (signInAttempts <= signInFailures) error("the code was not accepted")
        return signInAnswer(token, code)
    }

    override suspend fun checkPassword(password: String) {
        passwordAttempts++
        passwordFailure?.let { throw it }
        if (passwordAttempts <= passwordFailures) error("the password was not accepted")
        checkPasswordAnswer(password)
    }

    /** How many times [listLibraries] actually ran — a test's way of proving a re-derivation did not go back to Telegram. */
    var listCalls: Int = 0
        private set

    override suspend fun listLibraries(): List<LibraryChoice> {
        listCalls++
        listFailure?.let { throw it }
        return libraries
    }

    override suspend fun refreshLibrary(handle: String): ULong {
        refreshedHandle = handle
        if (refreshCancels) throw CancellationException("the flow that asked was dropped")
        refreshFails?.let { error(it) }
        installFailure?.let { throw it }
        return refreshLibraryAnswer(handle).toULong()
    }

    /** What [latestAppRelease] answers, or throws when [releaseFailure] is set. */
    var latestRelease: AppRelease? = null
    var releaseFailure: Throwable? = null
    var releaseChecks = 0

    /** Bytes [downloadAppRelease] writes to the path it is given, or the failure it throws instead. */
    var releaseApk: ByteArray = ByteArray(0)
    var downloadFailure: Throwable? = null
    val downloadedPaths = mutableListOf<String>()

    /** When set, [downloadAppRelease] suspends on it before writing, so a test can cancel a download in flight. */
    var downloadGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun latestAppRelease(handle: String): AppRelease? {
        releaseChecks++
        releaseFailure?.let { throw it }
        return latestRelease
    }

    override suspend fun downloadAppRelease(release: AppRelease, path: String) {
        downloadedPaths += path
        downloadFailure?.let { throw it }
        downloadGate?.await()
        java.io.File(path).writeBytes(releaseApk)
    }

    override suspend fun refreshCatalog(pointerUrl: String, keyB64: String): ULong = refreshResult.toULong()

    override suspend fun listSets(): List<SetSummary> {
        listSetsCalls++
        return sets
    }

    /**
     * The real core's own indexed lookup, not a listing searched afterwards
     * — [mediaSetCalls] is how a test proves a caller asked for one set this
     * way rather than falling back to [listSets] and searching its answer.
     */
    override suspend fun mediaSet(setId: String): SetSummary? {
        mediaSetCalls += setId
        return sets.find { it.setId == setId }
    }

    override suspend fun titleInfo(posterKey: String): TitleInfo? = null

    /** See [knownSetIds]. */
    private fun isKnownSet(setId: String): Boolean =
        knownSetIds?.let { setId in it }
            ?: if (sets.isNotEmpty()) sets.any { it.setId == setId } else totalSize > 0

    override suspend fun totalSize(setId: String): ULong {
        if (!isKnownSet(setId)) throw CoreException.NotFound("no such set: $setId")
        return totalSize.toULong()
    }

    override suspend fun catalogFacts(): CatalogFacts {
        catalogFactsCalls++
        return catalogFactsAnswer()
    }

    override suspend fun read(setId: String, offset: ULong, len: UInt): ByteArray {
        reads++
        val offsetAsLong = offset.toLong()
        requestedOffsets += offsetAsLong
        if (!isKnownSet(setId)) throw CoreException.NotFound("no such set: $setId")
        if (offsetAsLong >= totalSize) throw CoreException.NotFound("offset $offset is at or past the end")
        val clampedLen = minOf(len.toLong(), totalSize - offsetAsLong).toInt()
        return bytesOf(offsetAsLong, clampedLen)
    }

    override suspend fun fetchMissing(tmdbKey: String, language: String, backdropWidth: UInt): FetchReport {
        fetchCalls++
        lastKey = tmdbKey
        lastLanguage = language
        gate?.await()
        failure?.let { throw it }
        return report
    }

    override suspend fun account(): AccountSummary {
        accountFailure?.let { throw it }
        return accountAnswer ?: error("no account behind this core")
    }

    override fun dcId(): Int? {
        datacenterReads++
        return datacenter
    }

    override suspend fun signOut() {
        signOutCalls++
        signedOut = true
        authorized = false
    }

    override suspend fun sessions(): List<SessionSummary> {
        sessionsFailure?.let { throw it }
        return sessionsAnswer
    }

    override suspend fun revokeSession(id: String) {
        revokeFailure?.let { throw it }
        // The real core refuses this one id — sign out ends it instead.
        if (id == "0") throw CoreException.NotAuthorized("sign out to end this device's own session")
        revokedIds += id
    }

    override suspend fun titleCredits(key: String): TitleCreditsRecord = creditsAnswer

    override suspend fun person(personId: ULong): PersonRecord? = people[personId.toLong()]

    override suspend fun franchises(): List<FranchiseRecord> = franchiseRecords

    override suspend fun searchPeople(query: String): List<PeopleHitRecord> = peopleHits

    override suspend fun fetchPortrait(personId: ULong): String? = portraits[personId.toLong()]

    override suspend fun search(query: String): List<SearchHit> {
        searchedFor = query
        return searchHits
    }

    override suspend fun nextLibraryEvent(handle: String, ownDevice: String): LibraryEvent {
        eventHandle = handle
        val next = events.getOrNull(eventCalls++) ?: awaitCancellation()
        return next.getOrThrow()
    }

    /** How many times [profiles] actually ran. */
    var profilesCalls: Int = 0
        private set

    override suspend fun profiles(): List<Profile> {
        profilesCalls++
        profilesFailure?.let { throw it }
        return profiles
    }

    // Monotonic rather than derived from the current list's size: a create
    // after a delete must not reissue an id a still-live row once had —
    // crates/mediagram-core/src/state/profiles.rs mints a fresh ULID per
    // row for the same reason, just not one this fake needs to match. Also
    // skipped past any id a test seeded directly into `profiles` — a seeded
    // "p1" must not be handed out again to a second, distinct profile.
    private var nextProfileId = 1

    override suspend fun createProfile(name: String, kids: Boolean): Profile? {
        val cleanName = cleanProfileName(name) ?: return null
        while (profiles.any { it.id == "p$nextProfileId" }) nextProfileId++
        val created = Profile("p${nextProfileId++}", cleanName, kids)
        profiles = profiles + created
        return created
    }

    /**
     * Trims and collapses internal whitespace, answering `null` when
     * nothing is left — the same rule `clean_name`
     * (crates/mediagram-core/src/state/profiles.rs:145) applies before a
     * name ever reaches storage.
     */
    private fun cleanProfileName(name: String): String? =
        name
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .takeIf { it.isNotEmpty() }

    // Checked against `profiles` on every read, not trusted from whatever
    // was last written — the same reason `profiles::chosen` re-checks on
    // the real core: a profile named here can have been deleted since.
    override suspend fun chosenProfile(): String? = chosen?.takeIf { id -> profiles.any { it.id == id } }

    override suspend fun chooseProfile(id: String): Boolean {
        if (profiles.none { it.id == id }) return false
        chosen = id
        return true
    }

    override suspend fun deleteProfile(id: String): Boolean {
        if (profiles.none { it.id == id }) return false
        profiles = profiles.filterNot { it.id == id }
        if (chosen == id) chosen = null
        watchState.forget(id)
        return true
    }

    override suspend fun snapshot(profileId: String): StateSnapshot = watchState.snapshot(profileId)

    override suspend fun setProgress(profileId: String, setId: String, at: Double, duration: Double?) =
        watchState.setProgress(profileId, setId, at, duration)

    override suspend fun clearProgress(profileId: String, setId: String) = watchState.clearProgress(profileId, setId)

    override suspend fun setWatched(profileId: String, setId: String, finished: Boolean) =
        watchState.setWatched(profileId, setId, finished)

    override suspend fun setWatchlisted(profileId: String, setId: String, listed: Boolean) =
        watchState.setWatchlisted(profileId, setId, listed)

    override suspend fun setKids(setId: String, marked: Boolean) = watchState.setKids(setId, marked)

    override suspend fun editorsChoice(): String? = watchState.editorsChoice()

    override suspend fun setEditorsChoice(setId: String, marked: Boolean) = watchState.setEditorsChoice(setId, marked)

    override suspend fun createCollection(profileId: String, name: String): ListRow? = watchState.createCollection(profileId, name)

    override suspend fun renameCollection(profileId: String, id: String, name: String): Boolean =
        watchState.renameCollection(profileId, id, name)

    override suspend fun deleteCollection(profileId: String, id: String): Boolean = watchState.deleteCollection(profileId, id)

    override suspend fun setInCollection(profileId: String, id: String, setId: String, included: Boolean): Boolean =
        watchState.setInCollection(profileId, id, setId, included)

    override suspend fun preferences(profileId: String): List<PreferenceRow> = emptyList()

    override suspend fun setPreference(profileId: String, scope: String, name: String, value: String?): Boolean = false

    override suspend fun setText(setId: String, kind: String, lang: String): String? = null

    override suspend fun subtitleText(setId: String, track: UInt): String? = null

    override suspend fun holdSubtitles(setId: String): Boolean = false

    override suspend fun holdCourseSubtitles(setId: String) = Unit

    override suspend fun stateDeviceId(): String = ""

    override suspend fun syncState(handle: String): SyncOutcome = SyncOutcome(0uL, false, null)

    /** How many times [retireLocalState] actually ran, successfully or not. */
    var retireCalls: Int = 0
        private set

    override fun retireLocalState() {
        retireCalls++
        retireLocalStateFailure?.let { throw it }
    }

    /** How many times [close] actually ran, successfully or not. */
    var closeCalls: Int = 0
        private set

    /** Whether [close] has completed at least once without throwing. */
    var closed: Boolean = false
        private set

    override fun close() {
        closeCalls++
        closeFailure?.let { throw it }
        closed = true
    }
}
