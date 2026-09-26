package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import catalog.MenuScreen

/**
 * Which kind of screen a [Frame] stands for.
 *
 * [PERSON], [FRANCHISE], [GENRES], [LATEST] and [MOVIES_PAGE] are additive:
 * every existing branch keeps its own value, so a stack encoded by an older
 * build still decodes the same frames it always did — see [encode]/[decode].
 */
enum class FrameKind { PLAYER, MENU, SEARCH, GENRE, TITLE, SEASON, COLLECTION, LIST, PERSON, FRANCHISE, GENRES, LATEST, MOVIES_PAGE }

/**
 * One screen on [LibraryPositions]'s stack: which kind it is, and the one
 * key it needs to be shown again — a set id for the player, a genre's name,
 * and so on. A plain pair rather than a sealed class of its own: every kind
 * carries exactly one string, and encoding one shape is simpler than eight.
 */
private data class Frame(val kind: FrameKind, val payload: String)

private const val FIELD_SEP = "\u001F"
private const val FRAME_SEP = "\u001E"
/** Joins a player frame's own setId to the explicit run it was opened with, when it has one — see [LibraryPositions.run]. */
private const val RUN_SEP = "\u001D"
/** Joins a collection frame's own key to the season its own page is showing, when one was chosen — see [LibraryPositions.collectionSeason]. */
private const val SEASON_SEP = "\u001C"

private fun encode(frames: List<Frame>): String = frames.joinToString(FRAME_SEP) { "${it.kind.name}$FIELD_SEP${it.payload}" }

/**
 * Never throws: a token with no [FIELD_SEP], or a kind this build does not
 * know, is dropped rather than crashing every recomposition. The one way
 * either could happen — a payload that contained [FRAME_SEP] itself, so
 * splitting on it cut a single frame into two broken ones — is guarded at
 * the write side by [LibraryPositions.typeSearch]; this is what stops an
 * already-saved string from an older build, or from a `Bundle` doing
 * something stranger than that, from taking the screen down with it.
 */
private fun decode(raw: String): List<Frame> {
    if (raw.isEmpty()) return emptyList()
    return raw.split(FRAME_SEP).mapNotNull { token ->
        val parts = token.split(FIELD_SEP, limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val kind = runCatching { FrameKind.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
        Frame(kind, parts[1])
    }
}

/**
 * Where in the library a viewer currently is, as a stack of screens each
 * opened over the one beneath it — a title over the collection it was
 * opened from, a genre page over the title whose chip opened it, search
 * over whatever it was opened from, however deep any of them goes. [top]
 * alone decides which is on screen; [pop] leaves it, uncovering whatever
 * is next.
 *
 * Every field below is derived from the stack rather than held beside it:
 * [titleId] is the payload of the most recent [FrameKind.TITLE] frame,
 * wherever in the stack it sits, not a value shared by every title the
 * stack has ever held. That is what lets the same kind recur — a title
 * opened from a genre page opened from another title — without the inner
 * one overwriting the outer one's own key once it is no longer on top.
 *
 * The stack is kept as one string, encoded with control characters no
 * title, genre or query is ever going to contain, because a plain `String`
 * is what a `Bundle` already knows how to carry across a killed process
 * with no `Saver` of its own to write — the same reason every payload
 * below is a key rather than a tree. The Activity is fully destroyed and
 * recreated on rotation (there is no `android:configChanges`), and the
 * singleton player survives that regardless — without this, rotating away
 * from an open set would drop back to the catalog while the film kept
 * playing underneath it.
 */
class LibraryPositions(frames: MutableState<String>) {
    private var raw: String by frames
    private val stack: List<Frame> get() = decode(raw)
    private fun setStack(next: List<Frame>) {
        raw = encode(next)
    }

    /** Which screen is on top, or `null` for the catalog itself. */
    val top: FrameKind? get() = stack.lastOrNull()?.kind

    /**
     * How many screens are open over the catalog — `0` for the catalog
     * itself. Names a screen by where it sits rather than by its kind, for
     * a caller that remembers something per screen while the same kind can
     * sit at more than one depth: a title opened from a genre page opened
     * from another title.
     */
    val depth: Int get() = stack.size

    private fun payloadOf(kind: FrameKind): String? = stack.lastOrNull { it.kind == kind }?.payload

    val setId: String? get() = payloadOf(FrameKind.PLAYER)?.substringBefore(RUN_SEP)

    /**
     * The explicit run the open title was started on — a hand-built list,
     * the only caller of [openPlayer] that passes one. `null` everywhere else, where the player works out its
     * own run from the catalog instead (`catalog.runFor`).
     */
    val run: List<String>?
        get() = payloadOf(FrameKind.PLAYER)?.let { payload ->
            val at = payload.indexOf(RUN_SEP)
            if (at == -1) null else payload.substring(at + 1).split(RUN_SEP)
        }

    val titleId: String? get() = payloadOf(FrameKind.TITLE)
    val collection: String? get() = payloadOf(FrameKind.COLLECTION)?.substringBefore(SEASON_SEP)

    /**
     * Which season a show's own page is showing, by its title — the
     * collection frame's own payload carries it alongside the key, the same
     * way a player frame's carries its run, so opening a title from that
     * page (Similar, Cast, an episode) and coming back still finds it. Web's
     * own counterpart is the season named right in the URL,
     * `#/series/Show/Season N`; a phone has no URL bar, so this rides the
     * same frame instead. `null` until [setCollectionSeason] first writes
     * one — the page picks its own first default (a resume point, or the
     * first season) and is not asked to agree on it up front.
     */
    val collectionSeason: String?
        get() =
            payloadOf(FrameKind.COLLECTION)?.let { payload ->
                val at = payload.indexOf(SEASON_SEP)
                if (at == -1) null else payload.substring(at + 1)
            }
    val season: String? get() = payloadOf(FrameKind.SEASON)
    /** Which hand-built list is open, by its own id — the Collections tab's counterpart to [collection]. */
    val listId: String? get() = payloadOf(FrameKind.LIST)
    /** The search field's own text, or `null` while it is closed. */
    val search: String? get() = payloadOf(FrameKind.SEARCH)
    /** The genre a chip opened, by its own name. */
    val genre: String? get() = payloadOf(FrameKind.GENRE)
    val menuScreen: MenuScreen? get() = payloadOf(FrameKind.MENU)?.let { runCatching { MenuScreen.valueOf(it) }.getOrNull() }
    /** The person a cast row or a search result opened, by their id. */
    val personId: String? get() = payloadOf(FrameKind.PERSON)
    /** The franchise a collection card opened, by its id. */
    val franchiseId: String? get() = payloadOf(FrameKind.FRANCHISE)

    private fun push(kind: FrameKind, payload: String) = setStack(stack + Frame(kind, payload))

    /** @param run The explicit run [id] belongs to (a list or the Kids marked-by-hand wall); `null` everywhere else. */
    fun openPlayer(id: String, run: List<String>? = null) {
        push(FrameKind.PLAYER, playerPayload(id, run))
    }

    /**
     * An in-player switch to a different title (up-next's autoplay or "Play
     * now") — replaces the top PLAYER frame in place rather than pushing a
     * new one, so back still leaves to whatever opened the player, not to
     * the title that just finished; and so a rotation or process restore
     * reopens the title actually playing, not the one that ended. A no-op
     * with the player not on top, which should not happen.
     */
    fun replacePlayer(id: String, run: List<String>) {
        val current = stack
        if (current.lastOrNull()?.kind != FrameKind.PLAYER) return
        setStack(current.dropLast(1) + Frame(FrameKind.PLAYER, playerPayload(id, run)))
    }

    private fun playerPayload(id: String, run: List<String>?) =
        if (run.isNullOrEmpty()) id else "$id$RUN_SEP${run.joinToString(RUN_SEP)}"
    fun openSearch() = push(FrameKind.SEARCH, "")
    fun openGenre(name: String) = push(FrameKind.GENRE, name)
    fun openTitle(id: String) = push(FrameKind.TITLE, id)
    fun openSeason(name: String) = push(FrameKind.SEASON, name)
    fun openCollection(key: String) = push(FrameKind.COLLECTION, key)
    fun openList(id: String) = push(FrameKind.LIST, id)
    fun openPerson(id: String) = push(FrameKind.PERSON, id)
    fun openFranchise(id: String) = push(FrameKind.FRANCHISE, id)
    /** The Genres index — every department, not [openGenre]'s one shelf. */
    fun openGenresIndex() = push(FrameKind.GENRES, "")
    fun openLatest() = push(FrameKind.LATEST, "")
    /** "All N films": the Movies department's own paged shelf, one step in from its front page. */
    fun openMoviesPage() = push(FrameKind.MOVIES_PAGE, "")

    /**
     * Moves between menu screens rather than stacking them — asking for the
     * key screen from the system screen replaces it, the same as it always
     * has: asking for a screen is a move, not an addition. Opened from
     * anywhere else, it goes on top of whatever was already showing, the
     * same as every other `openX`.
     */
    fun openMenu(screen: MenuScreen) {
        val current = stack
        val base = if (current.lastOrNull()?.kind == FrameKind.MENU) current.dropLast(1) else current
        setStack(base + Frame(FrameKind.MENU, screen.name))
    }

    /**
     * Updates the open search field's own text in place — typing is not a
     * new screen. ISO control characters are stripped first: [FIELD_SEP]
     * and [FRAME_SEP] are two of them, and a query holding either would
     * corrupt the very frame it is saved into the instant it round-trips
     * through [encode] and [decode]. A viewer typing loses nothing real —
     * a search query has no legitimate use for a control character.
     */
    fun typeSearch(text: String) {
        val current = stack
        if (current.lastOrNull()?.kind != FrameKind.SEARCH) return
        val sanitized = text.filterNot(Character::isISOControl)
        setStack(current.dropLast(1) + Frame(FrameKind.SEARCH, sanitized))
    }

    /**
     * Remembers which season a show's own page is showing — see
     * [collectionSeason]. A no-op with the collection frame not on top,
     * which should not happen: only that page's own season picker calls this.
     */
    fun setCollectionSeason(name: String) {
        val current = stack
        val top = current.lastOrNull() ?: return
        if (top.kind != FrameKind.COLLECTION) return
        val key = top.payload.substringBefore(SEASON_SEP)
        setStack(current.dropLast(1) + Frame(FrameKind.COLLECTION, "$key$SEASON_SEP$name"))
    }

    /** Leaves whichever screen is on top. A no-op with nothing open. */
    fun pop() {
        val current = stack
        if (current.isNotEmpty()) setStack(current.dropLast(1))
    }

    /**
     * Back to the shelves from wherever, all at once. Asked for by an
     * action whose result is the shelves themselves: a viewer who requests
     * the library from a screen that cannot show it has to be shown it.
     */
    fun toCatalog() = setStack(emptyList())
}

@Composable
fun rememberLibraryPositions(): LibraryPositions =
    LibraryPositions(rememberSaveable { mutableStateOf("") })
