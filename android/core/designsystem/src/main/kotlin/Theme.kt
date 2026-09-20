package designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MediagramDarkColors = darkColorScheme()

/**
 * Dark by default, on a phone, a tablet, or a television, regardless of
 * the system theme: a media library is looked at in the dark.
 */
@Composable
fun MediagramTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MediagramDarkColors, content = content)
}
