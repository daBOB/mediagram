package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import model.Progress
import model.WatchSnapshot

/**
 * What every plate and row reads its progress rule and its watched tick
 * from: where each started title stopped, by set id, and which titles are
 * finished. Looked up once per snapshot here rather than rebuilt beside
 * every wall that draws a plate.
 */
internal data class WatchMarks(
    val positions: Map<String, Progress>,
    val watchedIds: Set<String>,
)

@Composable
internal fun rememberWatchMarks(watch: WatchSnapshot): WatchMarks =
    remember(watch) {
        WatchMarks(
            positions = watch.progress.associateBy { it.setId },
            watchedIds = watch.watched.mapTo(HashSet()) { it.setId },
        )
    }
