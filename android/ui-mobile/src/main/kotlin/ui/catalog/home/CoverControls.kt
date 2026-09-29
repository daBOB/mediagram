package ui.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Filled, on the artwork's own light — "Watch now" (`.pill-solid`, `home.css:73-74`). */
@Composable
internal fun SolidPill(
    text: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(OnImage)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // `.pill` is Geist, not the ambient `bodyLarge` a bare `Text` falls
        // back to on this catalogue — that default is Newsreader, the web's
        // reading face, wrong for a button (`title-page.css:14`).
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp), color = Color(0xFF0A0A0B))
    }
}

/** Outlined over the artwork — "+ My List" (`.pill-line`, `home.css:75-76`). */
@Composable
internal fun LinePill(
    text: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(Color(0x47080809))
                .border(1.dp, Color(0x66F6F2EA), CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp), color = OnImage)
    }
}

/** The bottom-right pause button and one bar per film — `.cover-pager`/`.cover-dot` (`home.css:108-126`), a bar rather than the web's round dot: `PageHead`'s own tab bars on this catalogue are already bars, not dots. */
@Composable
internal fun CoverPager(
    count: Int,
    current: Int,
    rotates: Boolean,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (rotates) {
            Box(
                // 48dp, the touch-target guideline — was 32dp, the pause
                // circle's own visual size, with nothing enlarging the
                // target beyond it.
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0x59F6F2EA), CircleShape)
                        .background(Color(0x4D080809))
                        .clickable(role = Role.Button, onClick = onTogglePause)
                        .semantics { contentDescription = if (paused) "Play the cover stories" else "Pause the cover stories" },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (paused) "▶" else "‖", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp), color = OnImage)
            }
        }
        for (index in 0 until count) {
            Box(
                // Also 48dp — was 28dp, centred here rather than padded to
                // it so the visual bar stays the same 20x4dp.
                modifier =
                    Modifier
                        .size(48.dp)
                        .clickable(role = Role.Button, onClick = { onPick(index) })
                        .semantics {
                            contentDescription = "Cover story ${index + 1} of $count"
                            // The web marks the current slide with
                            // `aria-pressed` (`home-cover.js`'s own pager);
                            // `selected` is Compose's nearest match for "this
                            // one, of a set", which TalkBack announces as
                            // "selected" on the current dot.
                            selected = index == current
                        },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 20.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (index == current) OnImage else Color(0x52F6F2EA)),
                )
            }
        }
    }
}

/** [ui.catalog.CoverScrim] (ui-common), reading this module's own `MaterialTheme` for the paper colour it has no way to reach on its own. */
@Composable
internal fun CoverScrim(
    compact: Boolean,
    modifier: Modifier = Modifier,
) = ui.catalog.CoverScrim(compact = compact, paper = MaterialTheme.colorScheme.background, modifier = modifier)
