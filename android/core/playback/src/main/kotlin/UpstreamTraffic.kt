package playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/** What the byte path is moving right now, for the statistics overlay. */
data class TrafficSnapshot(
    val foregroundBitsPerSecond: Long = 0,
    val foregroundInFlight: Int = 0,
    val backgroundBitsPerSecond: Long = 0,
    /** A preload fetch is holding off because playback is fetching. */
    val backgroundWaiting: Boolean = false,
)

/**
 * Who goes first on the way upstream, and how fast each side is arriving.
 *
 * The player's chunk fetches go through [foreground] and never wait. A
 * preload's go through [background], and each one starts only while no
 * foreground fetch is running — the rule the web server's download gate
 * already keeps (`web/src/telegram/download-gate.ts`), so a preload lives
 * on the gaps a full buffer leaves and never on a buffer that is filling.
 * Without it the series preload took 11.6 Mbit/s of a TV box's ~20 the
 * moment an episode opened, and the episode, which needed 15, got 9.9.
 *
 * A background fetch already running when playback asks is let finish:
 * one round trip, and cancelling it would waste the bytes already on
 * their way. One that is waiting holds its preload's thread until
 * playback pauses fetching, so a cancelled preload stops after that fetch.
 *
 * Both wrap the upstream beneath a factory's [ChunkMemo], so only real
 * fetches — LAN or Telegram — are counted or held back, never memo hits.
 * `SharedPlaybackModule` builds the one instance the player's factory and
 * the preload writer share; a caller that passes none gets a private one,
 * which holds nothing back because nothing else fetches through it.
 */
class UpstreamTraffic(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val playing = MutableStateFlow(0)
    private val waiting = AtomicInteger()
    private val foregroundRate = RateWindow()
    private val backgroundRate = RateWindow()

    fun foreground(upstream: SetChunkSource): SetChunkSource =
        SetChunkSource { setId, index, totalSize ->
            playing.update { it + 1 }
            try {
                upstream.chunk(setId, index, totalSize).also { foregroundRate.add(it.size, clock()) }
            } finally {
                playing.update { it - 1 }
            }
        }

    fun background(upstream: SetChunkSource): SetChunkSource =
        SetChunkSource { setId, index, totalSize ->
            if (playing.value > 0) {
                waiting.incrementAndGet()
                try {
                    playing.first { it == 0 }
                } finally {
                    waiting.decrementAndGet()
                }
            }
            upstream.chunk(setId, index, totalSize).also { backgroundRate.add(it.size, clock()) }
        }

    fun snapshot(): TrafficSnapshot {
        val now = clock()
        return TrafficSnapshot(
            foregroundBitsPerSecond = foregroundRate.bitsPerSecond(now),
            foregroundInFlight = playing.value,
            backgroundBitsPerSecond = backgroundRate.bitsPerSecond(now),
            backgroundWaiting = waiting.get() > 0,
        )
    }
}

/** Seconds a rate is averaged over, plus the one still filling. */
private const val BUCKETS = 5

/**
 * Bytes that arrived, as a rate over the last few whole seconds.
 *
 * The second still in progress is left out: it is partly counted, and a
 * readout that dipped at the start of every second would flicker. So the
 * rate lags by up to a second, which a number read by eye never notices.
 */
internal class RateWindow {
    private val buckets = LongArray(BUCKETS)
    private var newest = 0L

    @Synchronized
    fun add(
        bytes: Int,
        nowMs: Long,
    ) {
        roll(nowMs)
        buckets[(newest % BUCKETS).toInt()] += bytes.toLong()
    }

    @Synchronized
    fun bitsPerSecond(nowMs: Long): Long {
        roll(nowMs)
        val whole = buckets.sum() - buckets[(newest % BUCKETS).toInt()]
        return whole * 8 / (BUCKETS - 1)
    }

    /** Empties every bucket whose second has passed, up to the current one. */
    private fun roll(nowMs: Long) {
        val second = nowMs / 1000
        if (second <= newest) return
        val passed = minOf(second - newest, BUCKETS.toLong())
        for (step in 1..passed) buckets[((newest + step) % BUCKETS).toInt()] = 0
        newest = second
    }
}
