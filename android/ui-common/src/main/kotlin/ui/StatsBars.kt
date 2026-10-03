package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import stats.StatsBar

private val BarGap = 2.dp

/**
 * The Stats page's last thirty days, the same on both surfaces: one bar per
 * day, oldest left and today right, each as tall as its share of the
 * busiest day and labelled for a screen reader ("3 Oct · 42 min"). Thirty
 * bottom-aligned boxes in a row are the whole chart; no chart library.
 * [labelStyle] draws each day's weekday letter under its bar, and `null`
 * leaves the letters out where thirty would not fit.
 */
@Composable
fun StatsBars(
    bars: List<StatsBar>,
    color: Color,
    labelStyle: TextStyle?,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(BarGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (bar in bars) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(bar.fraction)
                            .background(color)
                            .semantics { contentDescription = bar.description },
                )
            }
        }
        if (labelStyle != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(BarGap)) {
                for (bar in bars) {
                    BasicText(text = bar.initial, style = labelStyle.copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
