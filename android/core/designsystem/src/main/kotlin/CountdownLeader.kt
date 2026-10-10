package designsystem

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The web's inks over artwork (`styles/theme.css:99-100`): the leader's own, where it stands on a dark ground. */
private val RingInk = Color(0xD1F6F2EA)
private val NumeralInk = Color(0xFFF6F2EA)

/** The drawing's own square (`index.html:101`, `viewBox="0 0 96 96"`), scaled to the leader's size. */
private const val UNITS = 96f

/** One turn of the sweep (`film-leader.css:30`) and the 3·2·1 it counts down through (`:46`). */
private const val TURN_MS = 1000
private const val TURNS = 3

const val COUNTDOWN_LEADER_TEST_TAG = "countdown-leader"

/**
 * One count for waits that hand over to one another, so the leader runs on
 * across them instead of going back to 3 at each: the web's boot is one
 * element through all of them (`index.html:119-121`). The first leader to
 * draw under it sets the start.
 */
class CountdownStart {
    private var startedAt: Long? = null

    /** Where the count is, in turns of the sweep, 0 until [TURNS]; held here so a leader taking over draws it at once. */
    internal val turns = mutableFloatStateOf(0f)

    internal fun advanceTo(frameMillis: Long) {
        val start = startedAt ?: frameMillis.also { startedAt = it }
        turns.floatValue = (frameMillis - start) % (TURN_MS * TURNS) / TURN_MS.toFloat()
    }
}

/**
 * The count a leader follows. None by default: each leader counts on its own
 * from 3, as each of the web's waits does. A wait of its own inside a run
 * that provides one (a page, the stage) provides `null` again.
 */
val LocalCountdownStart = staticCompositionLocalOf<CountdownStart?> { null }

/** The figure the leader shows, for tests: the drawing itself says nothing to a screen reader. */
internal val CountdownNumeral = SemanticsPropertyKey<Int>("CountdownNumeral")
private var SemanticsPropertyReceiver.countdownNumeral by CountdownNumeral

/**
 * A film leader's countdown, the web's (`index.html:100-116`,
 * `styles/film-leader.css`): rings and a crosshair, a wedge sweeping round
 * once a second, and a numeral stepping 3 → 2 → 1 as each turn completes.
 * Decorative: whoever places it gives the wait its words.
 *
 * [ink] draws the rings and the crosshair, [numeralInk] the figure. They
 * default to the web's inks over artwork, for a dark ground; a wait on the
 * page's own ground passes the page's inks, or the drawing vanishes on paper.
 *
 * One clock drives both, turning [TURNS] times a cycle, so the numeral
 * steps on the frame the sweep completes. The sweep reads it only while
 * drawing; the numeral recomposes once a second. The clock is the
 * [LocalCountdownStart] when one is provided, else the leader's own, from 3.
 * Under reduced motion there is no clock: no sweep, the 3 alone, as the
 * web's still default (`film-leader.css:7-9`, `theme.css:180-182`).
 */
@Composable
fun CountdownLeader(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
    ink: Color = RingInk,
    numeralInk: Color = NumeralInk,
) {
    val still = LocalReducedMotion.current
    // Its own is new each time motion returns: a still leader has no count to resume.
    val own = remember(still) { CountdownStart() }
    val clock = LocalCountdownStart.current ?: own
    if (!still) {
        // Linear and restarting, a projector's clock (`film-leader.css:30-32`).
        LaunchedEffect(clock) { while (true) withInfiniteAnimationFrameMillis(clock::advanceTo) }
    }
    val turns = if (still) null else clock.turns
    // 3 for the first second, 2 for the next, 1 for the last (`film-leader.css:46-54`).
    // Read here, so a step recomposes once and the frames between do not.
    val numeral = remember(turns) { derivedStateOf { TURNS - (turns?.floatValue?.toInt() ?: 0).coerceAtMost(TURNS - 1) } }.value
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier
            .size(size)
            .testTag(COUNTDOWN_LEADER_TEST_TAG)
            .semantics { countdownNumeral = numeral }
            .drawWithCache {
                val u = this.size.width / UNITS
                // Hairlines through the centre, the outer ring's width and height (`index.html:109`).
                val cross =
                    Path().apply {
                        moveTo(2 * u, 48 * u)
                        lineTo(94 * u, 48 * u)
                        moveTo(48 * u, 2 * u)
                        lineTo(48 * u, 94 * u)
                    }
                // Fraunces 500 at 44, centred on x 48 with its baseline at 64 (`index.html:112-114`, `film-leader.css:40-45`).
                val figure =
                    measurer.measure(
                        numeral.toString(),
                        TextStyle(
                            color = numeralInk,
                            fontFamily = Display,
                            fontWeight = FontWeight.Medium,
                            fontSize = (44 * u).toSp(),
                            fontFeatureSettings = "tnum, lnum",
                        ),
                    )
                val figureAt = Offset(48 * u - figure.size.width / 2f, 64 * u - figure.firstBaseline)
                onDrawBehind {
                    // Under everything, a sector of the inner ring's radius turning clockwise
                    // from 12 o'clock, tinted so the lines read through it (`index.html:105`, `film-leader.css:20-34`).
                    turns?.let {
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = it.floatValue % 1f * 360f,
                            useCenter = true,
                            topLeft = Offset(10 * u, 10 * u),
                            size = Size(76 * u, 76 * u),
                            alpha = 0.3f,
                        )
                    }
                    // The rings, r 46 at 1.5 and r 38 at 1 (`index.html:106-107`).
                    drawCircle(ink, radius = 46 * u, style = Stroke(1.5f * u))
                    drawCircle(ink, radius = 38 * u, style = Stroke(u))
                    // The faintest line on the frame, at half the rings' ink (`film-leader.css:13-14`).
                    drawPath(cross, ink, alpha = 0.5f, style = Stroke(u))
                    drawText(figure, topLeft = figureAt)
                }
            },
    )
}
