package ui.tv.chrome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import catalog.profileInitial
import designsystem.TvType
import designsystem.tvDp
import designsystem.tvSp
import ui.tv.TvFocus
import ui.tv.currentTvLook
import ui.tv.pillBorder
import ui.tv.pillColors
import ui.tv.profile.TvChosenProfile

/**
 * One department pill on [TvDepartmentsBar] — [TvFocus.PillShape], the
 * accent ring only while focused — or, in Apple TV and Google TV styles, a white
 * fill with dark ink ([TvFocus.pillColors]); [active] fills it at ink@12% instead of
 * ringing it, the tablet's own `Pill` treatment (`ChromeControls.kt`), so a
 * chosen department still reads as chosen once the remote has moved on to
 * something else on the bar. Set as the web's `.departments a` at the
 * TV's 1280px reading: Geist 500 at 14px, padded 10 by 16, at least 44 tall
 * (`shell.css:39-50,90`).
 */
@Composable
internal fun TvPill(
    title: String,
    active: Boolean,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = if (active) ink.copy(alpha = 0.12f) else Color.Transparent
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { role = Role.Tab; selected = active },
        shape = TvFocus.surfaceShape(TvFocus.PillShape),
        colors = TvFocus.pillColors(container = fill, content = ink, pressed = ink.copy(alpha = 0.12f)),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.pillBorder(),
        glow = TvFocus.surfaceGlow(),
    ) {
        Box(
            modifier = Modifier.heightIn(min = 44.tvDp).padding(horizontal = 16.tvDp, vertical = 10.tvDp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = title, style = TvType.labelLarge)
        }
    }
}

/**
 * The round icon button both the departments bar and, later, a cover
 * button draw from — [TvFocus.PillShape], 44dp, the tablet's own
 * `CircleIconButton` sizing (`ChromeControls.kt`).
 */
@Composable
internal fun TvRoundIconButton(
    icon: Painter,
    description: String,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.tvDp).semantics { contentDescription = description },
        shape = TvFocus.surfaceShape(TvFocus.PillShape),
        colors = TvFocus.pillColors(container = Color.Transparent, content = ink),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.pillBorder(),
        glow = TvFocus.surfaceGlow(),
    ) {
        Box(modifier = Modifier.size(44.tvDp), contentAlignment = Alignment.Center) {
            Image(
                painter = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(LocalContentColor.current),
                modifier = Modifier.size(21.tvDp),
            )
        }
    }
}

/**
 * The viewer as an initial in a circle — the bar's own `#who`, the tablet's
 * [ui.chrome.ChromeAvatar] in tv-material. [onClick] opens the menu, which
 * is where switching profile lives on television: see [TvDepartmentsBar].
 */
@Composable
internal fun TvAvatar(
    profile: TvChosenProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fills = currentTvLook.pillFocusFills
    val container = if (fills) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onBackground
    val content = if (fills) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.background
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.tvDp).semantics { contentDescription = "Menu, watching as ${profile.name}" },
        shape = TvFocus.surfaceShape(TvFocus.PillShape),
        colors = TvFocus.pillColors(container = container, content = content),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.pillBorder(),
        glow = TvFocus.surfaceGlow(),
    ) {
        Box(modifier = Modifier.size(44.tvDp), contentAlignment = Alignment.Center) {
            Text(text = profileInitial(profile.name), style = TvType.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.tvSp))
        }
    }
}
