package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import ui.FrameKind

/**
 * The screens of the library that open something else — the catalogue, a
 * show or course, one season of a show, a hand-built list. A title page is
 * not one: coming back to it always lands on Play.
 *
 * One of each at most is ever on the way back: the catalogue opens a
 * collection or a list, a collection opens a season, and a season is never
 * opened from anything but its own show. So the screen's kind is enough to
 * name it, without the key of which show or which list it was.
 */
internal enum class TvPlace { Catalog, Collection, Season, List }

/** Which [TvPlace] the screen on top is (`null` for the catalogue itself), or null for one that opens nothing a viewer comes back to. */
internal fun placeOf(top: FrameKind?): TvPlace? =
    when (top) {
        null -> TvPlace.Catalog
        FrameKind.COLLECTION -> TvPlace.Collection
        FrameKind.SEASON -> TvPlace.Season
        FrameKind.LIST -> TvPlace.List
        FrameKind.TITLE, FrameKind.PLAYER, FrameKind.MENU, FrameKind.SEARCH, FrameKind.GENRE -> null
    }

/**
 * What each [TvPlace] last opened, by the key its own wall or rows use —
 * so Back lands the remote on the plate or row that was pressed rather than
 * at the top. A phone has no need for this: a touch screen has no focus to
 * put back. Held here rather than beside the shared positions
 * because it is only a fact about how a television draws those positions,
 * not about where the viewer is.
 *
 * Saved across a process death with the positions themselves: a restored
 * season with the remote back on its first episode would lose exactly what
 * this exists to keep.
 *
 * A place's key is forgotten when the place itself is left, so opening the
 * same show again later is a fresh arrival on its first season, the way it
 * was the first time.
 */
internal class TvRestoreKeys(
    private val state: MutableState<Map<TvPlace, String>>,
) {
    fun of(place: TvPlace): String? = state.value[place]

    /** [place] opened whatever [key] names. */
    fun opened(
        place: TvPlace,
        key: String,
    ) {
        state.value = state.value + (place to key)
    }

    /** Forgets what these places opened: they are being left, or about to be arrived at anew. */
    fun forget(vararg places: TvPlace) {
        state.value = state.value - places.toSet()
    }
}

/** Each key saved as its place's name and the key itself, side by side — a plain list a Bundle can hold. */
private val restoreKeysSaver: Saver<Map<TvPlace, String>, List<String>> =
    Saver(
        save = { keys -> keys.flatMap { (place, key) -> listOf(place.name, key) } },
        restore = { saved -> saved.chunked(2).associate { (place, key) -> TvPlace.valueOf(place) to key } },
    )

@Composable
internal fun rememberTvRestoreKeys(): TvRestoreKeys =
    TvRestoreKeys(rememberSaveable(stateSaver = restoreKeysSaver) { mutableStateOf(emptyMap()) })
