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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import catalog.profileInitial
import designsystem.CatalogueType
import ui.tv.TvFocus
import ui.tv.profile.TvChosenProfile

/**
 * One department pill on [TvDepartmentsBar] — [TvFocus.PillShape], the
 * accent ring only while focused; [active] fills it at ink@12% instead of
 * ringing it, the tablet's own `Pill` treatment (`ChromeControls.kt`), so a
 * chosen department still reads as chosen once the remote has moved on to
 * something else on the bar. Set as the web's `.departments a` at a
 * 960px window: Geist 500 at 14px, padded 10 by 12, at least 44 tall
 * (`shell.css:39-50,181`).
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
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = fill,
                contentColor = ink,
                focusedContainerColor = fill,
                focusedContentColor = ink,
                pressedContainerColor = ink.copy(alpha = 0.12f),
                pressedContentColor = ink,
            ),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.surfaceBorder(shape = TvFocus.PillShape),
        glow = TvFocus.surfaceGlow(),
    ) {
        Box(
            modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = title, style = CatalogueType.labelLarge)
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
        modifier = modifier.size(44.dp).semantics { contentDescription = description },
        shape = TvFocus.surfaceShape(TvFocus.PillShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = ink,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = ink,
            ),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.surfaceBorder(shape = TvFocus.PillShape),
        glow = TvFocus.surfaceGlow(),
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(ink),
                modifier = Modifier.size(21.dp),
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
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp).semantics { contentDescription = "Menu, watching as ${profile.name}" },
        shape = TvFocus.surfaceShape(TvFocus.PillShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.onBackground,
                focusedContentColor = MaterialTheme.colorScheme.background,
            ),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.surfaceBorder(shape = TvFocus.PillShape),
        glow = TvFocus.surfaceGlow(),
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Text(text = profileInitial(profile.name), style = CatalogueType.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp))
        }
    }
}
