package ui.catalog

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import uniffi.mediagram_core.TitleInfo

/**
 * What the index records about the title a poster key names, looked up once
 * per key.
 *
 * Null both while the answer is on its way and when there is no answer, and
 * the screen renders the same either way: a title with no provider entry is
 * the ordinary case, so a spinner over the blocks it would fill would
 * promise something that is never coming.
 */
@Composable
fun rememberTitleInfo(
    posterKey: String?,
    lookup: suspend (String) -> TitleInfo?,
): TitleInfo? {
    var info by remember(posterKey) { mutableStateOf<TitleInfo?>(null) }
    LaunchedEffect(posterKey) {
        try {
            info = posterKey?.let { lookup(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w("CatalogMetadata", "Could not load title details", e)
        }
    }
    return info
}

/**
 * The local file for a poster key, looked up once per key.
 *
 * The same shape as [rememberTitleInfo], generalised to artwork: a wall
 * opens several of these at once, one per plate, where a title screen only
 * ever asks for one synopsis.
 */
@Composable
fun rememberPosterPath(
    key: String?,
    lookup: suspend (String) -> String?,
): String? {
    var path by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key) {
        try {
            path = key?.let { lookup(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Log.w("CatalogMetadata", "Could not load season poster", e)
        }
    }
    return path
}
