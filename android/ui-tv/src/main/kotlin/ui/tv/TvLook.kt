package ui.tv

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import designsystem.LocalTvStyle
import designsystem.Palette
import designsystem.TvStyle

/**
 * The neutral roles a TV style sets its colour scheme from: the ground, the
 * plate a card sits on, the sunk fill, text, figures and the two rules. The
 * accent and the two status colours ([Palette.Sage], [Palette.Ochre]) are
 * the same in every style — the accent is the viewer's own choice.
 */
internal data class TvNeutrals(
    val ground: Color,
    val page: Color,
    val sunk: Color,
    val text: Color,
    val figures: Color,
    val rule: Color,
    val ruleStrong: Color,
)

/** How a focused card says so: mediagram's accent border, Apple TV's lift, Google TV's white ring. */
internal enum class TvCardFocus { ACCENT_BORDER, LIFT, RING }

/**
 * Every value a TV style varies besides its layout. Layout — which bar,
 * which cover — is picked by each seam from [TvStyle] directly, since only
 * the chrome, the Home cover and the row cards differ in structure at all.
 */
internal data class TvLook(
    val neutrals: TvNeutrals,
    val cardShape: Shape,
    val cardFocus: TvCardFocus,
    val focusScale: Float,
    /** A focused pill or button turns white with dark ink, rather than gaining a ring. */
    val pillFocusFills: Boolean,
    /** Rows of titles show 16:9 stills rather than posters; walls stay posters in every style. */
    val landscapeRows: Boolean,
)

internal val MediagramLook =
    TvLook(
        neutrals = TvNeutrals(Palette.Ground, Palette.Page, Palette.Sunk, Palette.Text, Palette.Figures, Palette.Rule, Palette.RuleStrong),
        cardShape = RectangleShape,
        cardFocus = TvCardFocus.ACCENT_BORDER,
        focusScale = TvFocus.Scale,
        pillFocusFills = false,
        landscapeRows = false,
    )

/** Apple's dark system greys (gray 6, 5, 4 and the secondary label) on black. */
internal val AppleLook =
    TvLook(
        neutrals =
            TvNeutrals(
                ground = Color(0xFF000000),
                page = Color(0xFF1C1C1E),
                sunk = Color(0xFF2C2C2E),
                text = Color(0xFFFFFFFF),
                figures = Color(0xFFA1A1A6),
                rule = Color(0xFF2C2C2E),
                ruleStrong = Color(0xFF3A3A3C),
            ),
        cardShape = RoundedCornerShape(12.dp),
        cardFocus = TvCardFocus.LIFT,
        focusScale = 1.10f,
        pillFocusFills = true,
        landscapeRows = true,
    )

/** Google's dark greys: grey 900 ground, its two surfaces, grey 200 text, grey 500 figures, grey 700 and 600 rules. */
internal val GoogleLook =
    TvLook(
        neutrals =
            TvNeutrals(
                ground = Color(0xFF202124),
                page = Color(0xFF303134),
                sunk = Color(0xFF3C4043),
                text = Color(0xFFE8EAED),
                figures = Color(0xFF9AA0A6),
                rule = Color(0xFF3C4043),
                ruleStrong = Color(0xFF5F6368),
            ),
        cardShape = RoundedCornerShape(16.dp),
        cardFocus = TvCardFocus.RING,
        focusScale = 1.05f,
        pillFocusFills = true,
        landscapeRows = true,
    )

internal val TvStyle.look: TvLook
    get() =
        when (this) {
            TvStyle.MEDIAGRAM -> MediagramLook
            TvStyle.APPLE -> AppleLook
            TvStyle.GOOGLE -> GoogleLook
        }

/** Only mediagram keeps the library rail; the other two styles put its rows behind the avatar. */
internal val TvStyle.hasRail: Boolean get() = this == TvStyle.MEDIAGRAM

/** The look every composable under [TvTheme] draws in. */
internal val currentTvLook: TvLook
    @Composable
    @ReadOnlyComposable
    get() = LocalTvStyle.current.look
