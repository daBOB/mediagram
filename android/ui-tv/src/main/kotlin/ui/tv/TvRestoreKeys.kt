package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * What each open screen of the library last opened, by the key its own
 * wall, rows or links use — so Back lands the remote on the plate, row or
 * link that was pressed rather than at the top. A phone has no need for
 * this: a touch screen has no focus to put back. Held here rather than
 * beside the shared positions because it is only a fact about how a
 * television draws those positions, not about where the viewer is.
 *
 * A screen is named by its depth on the positions' stack — `0` for the
 * catalogue — not by its kind: a genre page opens titles whose own genre
 * links open genre pages, so the same kind can sit at several depths at
 * once, each with its own place to come back to.
 *
 * Saved across a process death with the positions themselves: a restored
 * season with the remote back on its first episode would lose exactly what
 * this exists to keep.
 */
internal class TvRestoreKeys(
    private val state: MutableState<Map<Int, String>>,
) {
    fun of(depth: Int): String? = state.value[depth]

    /**
     * The screen at [depth] opened whatever [key] names. Anything deeper is
     * forgotten with it, so what opens next is a fresh arrival — the same
     * show opened again later lands on its first season, as it did the
     * first time.
     */
    fun opened(
        depth: Int,
        key: String,
    ) {
        state.value = state.value.filterKeys { it < depth } + (depth to key)
    }

    /** Forgets what the screen at [depth], and anything deeper, opened: it is being left, or shown anew. */
    fun forget(depth: Int) {
        state.value = state.value.filterKeys { it < depth }
    }
}

/** Each key saved as its depth and the key itself, side by side — a plain list a Bundle can hold. */
private val restoreKeysSaver: Saver<Map<Int, String>, List<String>> =
    Saver(
        save = { keys -> keys.flatMap { (depth, key) -> listOf(depth.toString(), key) } },
        restore = { saved -> saved.chunked(2).mapNotNull { (depth, key) -> depth.toIntOrNull()?.let { it to key } }.toMap() },
    )

@Composable
internal fun rememberTvRestoreKeys(): TvRestoreKeys =
    TvRestoreKeys(rememberSaveable(stateSaver = restoreKeysSaver) { mutableStateOf(emptyMap()) })
