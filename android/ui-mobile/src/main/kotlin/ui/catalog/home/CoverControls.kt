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
import androidx.compose.ui.graphics.Brush
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

@Composable
internal fun CoverScrim(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val paper = MaterialTheme.colorScheme.background
    if (compact) {
        // `linear-gradient(0deg, paper 0%, rgba(8,8,9,.82) 30%, rgba(8,8,9,.35) 70%, rgba(8,8,9,.2) 100%)` — 0deg starts at the bottom (`home.css:352-355`).
        Box(
            modifier.background(
                Brush.verticalGradient(
                    0f to Color(0x33080809),
                    0.3f to Color(0x59080809),
                    0.7f to Color(0xD1080809),
                    1f to paper,
                ),
            ),
        )
        return
    }
    // CSS multiple backgrounds paint the *first*-listed layer on top — the
    // web declares the 90deg horizontal gradient first (`home.css:34`),
    // then the paper fade (35), then the top scrim (36), so the horizontal
    // gradient is what actually sits over the picture. Compose has no such
    // rule: each `Box` below draws over the one before it, so they are
    // built back-to-front — top scrim, then paper fade, then the horizontal
    // gradient last — to land in the web's own order. Drawing them in
    // declaration order instead once put the *top scrim* frontmost, which
    // left the button row sitting almost directly on `--paper` in Light
    // theme (contrast around 2:1, against the web's 7-10:1).
    Box(modifier) {
        // `linear-gradient(180deg, rgba(8,8,9,.55) 0%, 0 22%)` (`home.css:36`).
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x8C080809), 0.22f to Color(0x00080809))))
        // `linear-gradient(0deg, paper 0%, transparent 26%)` (`home.css:35`).
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.74f to paper.copy(alpha = 0f), 1f to paper)))
        // `linear-gradient(90deg, rgba(8,8,9,.94) 0%, .78 26%, .24 56%, 0 76%)` (`home.css:34`).
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xF0080809),
                    0.26f to Color(0xC7080809),
                    0.56f to Color(0x3D080809),
                    0.76f to Color(0x00080809),
                ),
            ),
        )
    }
}
