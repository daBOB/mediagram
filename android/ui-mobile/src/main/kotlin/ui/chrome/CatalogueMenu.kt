package ui.chrome

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import designsystem.entrance

/** The web's menu-in: 320ms on `--ease`, from 4px up (`title-page.css:46,50`). */
private const val MENU_IN_MS = 320
private val MENU_IN_FROM = (-4).dp

/** Material's own list padding and window margin, kept so each menu sits and measures where it did. */
private val LIST_PADDING = 8.dp
private val WINDOW_MARGIN = 48.dp

/**
 * A ⋯ menu's list, in place of Material's `DropdownMenu`, which scales and
 * fades both ways. The web's catalogue menu fades in and drops 4px over
 * 320ms, then closes at once (`title-page.css:46,50`: an animation on the way
 * in, none on the way out). A [still] menu opens and closes with no motion at
 * all: the web's player menus are shown and hidden (`player-card.css:89-119`).
 *
 * Everything else is what `DropdownMenu` gave, so only the motion changes:
 * placed against the layout it is called in ([UnderAnchor]); Material's menu
 * surface, shape, shadow, padding and intrinsic width; a focusable popup that
 * an outside tap or Back dismisses; and [CatalogueMenuItem] rows, which are
 * Material's own items, so a screen reader hears what it heard before.
 */
@Composable
internal fun CatalogueMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    still: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val margin = with(LocalDensity.current) { WINDOW_MARGIN.roundToPx() }
    Popup(
        popupPositionProvider = remember(margin) { UnderAnchor(margin) },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        // The menu is the page's own surface, wherever it opens from: over the
        // video (the player's Kids menu) its presses still tint in the page's ink.
        IndicationInk(MaterialTheme.colorScheme.onSurface) {
            Surface(
                modifier = if (still) Modifier else Modifier.entrance(MENU_IN_MS, fromY = MENU_IN_FROM),
                shape = MenuDefaults.shape,
                color = MenuDefaults.containerColor,
                tonalElevation = MenuDefaults.TonalElevation,
                shadowElevation = MenuDefaults.ShadowElevation,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = LIST_PADDING).width(IntrinsicSize.Max).verticalScroll(rememberScrollState()),
                    content = content,
                )
            }
        }
    }
}

/**
 * One row of a [CatalogueMenu]: Material's own item, so its size, colours and
 * what a screen reader hears are unchanged. It draws the theme's indication on
 * its own interaction source, because Material's items hard-code a ripple the
 * theme has turned off.
 */
@Composable
internal fun CatalogueMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = Modifier.indication(source, LocalIndication.current),
        enabled = enabled,
        interactionSource = source,
    )
}

/**
 * Where `DropdownMenu` puts its list:
 * - across: start edges lined up with the anchor, else end edges, else
 *   against the nearer side of the window;
 * - down: below the anchor, else above it, else centred on its top, else
 *   against the nearer end of the window, always [margin] clear of the
 *   window's top and bottom (centred when the list is too tall for that).
 *
 * The anchor is the layout the menu is called in, as before.
 */
internal data class UnderAnchor(val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val ltr = layoutDirection == LayoutDirection.Ltr
        val xs =
            listOf(
                if (ltr) anchorBounds.left else anchorBounds.right - width,
                if (ltr) anchorBounds.right - width else anchorBounds.left,
                if (anchorBounds.center.x < windowSize.width / 2) 0 else windowSize.width - width,
            )
        val ys =
            listOf(
                anchorBounds.bottom,
                anchorBounds.top - height,
                anchorBounds.top - height / 2,
                when {
                    height >= windowSize.height - 2 * margin -> (windowSize.height - height) / 2
                    anchorBounds.center.y < windowSize.height / 2 -> margin
                    else -> windowSize.height - height - margin
                },
            )
        val x = xs.firstOrNull { it >= 0 && it + width <= windowSize.width } ?: xs.last()
        val y = ys.firstOrNull { it >= margin && it + height <= windowSize.height - margin } ?: ys.last()
        return IntOffset(x, y)
    }
}
