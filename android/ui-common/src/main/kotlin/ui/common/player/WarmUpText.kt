package ui.common.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import player.warmUpLine

/**
 * The warm-up line, `Warming up · 0:12 of 0:50`, read again twice a second
 * from [aheadMs] while it is on screen — the same pace the autoplay gate
 * polls at, so the line and the gate never disagree for long.
 */
@Composable
fun warmUpText(aheadMs: () -> Long): String {
    val ahead by produceState(aheadMs(), aheadMs) {
        while (true) {
            value = aheadMs()
            delay(WARM_UP_TICK_MS)
        }
    }
    return warmUpLine(ahead)
}

private const val WARM_UP_TICK_MS = 500L
