package ui.tv.player

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import designsystem.TvType
import designsystem.tvSp

/**
 * The player overlay's text. The card and the bar above it are set as the
 * web's player sets them (`player-card.css`, `playback.css`), each px through
 * [designsystem.TvScale], in the faces the tablet draws them in: Fraunces for
 * what is playing, the interface face (Geist) for every label, figure and
 * glyph. The rest of the overlay — the episode list, notes, up next, the
 * menus — keeps [title] and [body], the sizes every television screen had
 * before the library took the web's roles, until a pass measures those
 * against the web the same way.
 */
internal object TvPlayerType {
    val title: TextStyle =
        TextStyle(fontFamily = TvType.headlineSmall.fontFamily, fontWeight = FontWeight.Medium, fontSize = 34.sp, letterSpacing = (-0.1).sp)
    val body: TextStyle = TextStyle(fontFamily = TvType.bodyMedium.fontFamily, fontSize = 18.sp)

    /** `.hud .what` (`playback.css:72-83`): what is playing, Fraunces 500 at 1.15rem. */
    val barTitle: TextStyle = TvType.titleSmall.copy(fontSize = 18.4.tvSp, lineHeight = 1.5.em)

    /** `.ghost` (`playback.css:85-98`): My List, Kids, Add to list and Notes, 0.8125rem. */
    val ghost: TextStyle = TvType.labelLarge.copy(fontSize = 13.tvSp, lineHeight = 1.5.em)

    /** `.ends` (`playback.css:164-170`): the card's quiet figures, here the bar's file line too. */
    val figures: TextStyle = TvType.labelLarge.copy(fontSize = 12.tvSp, lineHeight = 1.5.em, letterSpacing = 0.04.em, fontFeatureSettings = TABULAR)

    /** `.control-card .at` (`player-card.css:57-63`): where the film is and how long it runs. */
    val time: TextStyle = TvType.labelLarge.copy(fontSize = 13.12.tvSp, lineHeight = 1.5.em, letterSpacing = 0.04.em, fontFeatureSettings = TABULAR)

    /** `.tool` (`player-card.css:66-80`): CC, ▾, the speed, Audio and the framing, 0.875rem. */
    val tool: TextStyle = TvType.labelLarge.copy(fontSize = 14.tvSp, lineHeight = 1.5.em, fontFeatureSettings = TABULAR)

    /**
     * The transport's characters — ↺, −15, +15, ⓘ, ☰ — as the tablet draws
     * them, at `.tsp svg`'s 1.25rem (`playback.css:199`) as a character:
     * `TransportIcon` draws a shape at 4/3 of the size it is handed, so a
     * 15px character stands as tall as the web's 20px icon beside it, and
     * ⏮/⏭ take this same size.
     */
    val glyph: TextStyle = TvType.labelLarge.copy(fontSize = 15.tvSp, lineHeight = 1.5.em, letterSpacing = 0.06.em, fontFeatureSettings = TABULAR)

    /** `.tsp.big svg`'s 1.75rem (`playback.css:205`), play and pause, as [glyph] reads the smaller icons. */
    val bigGlyph: TextStyle = glyph.copy(fontSize = 21.tvSp)
}

/** Figures in tabular numerals, the web's `font-variant-numeric: tabular-nums`. */
private const val TABULAR = "tnum"
