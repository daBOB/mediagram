package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import player.PlayerNotes

/** Where the notes column goes, given the window the player has. */
internal enum class NotesPlacement { BESIDE, BELOW, OVER }

/**
 * Beside the picture wherever a landscape window has the height to keep the
 * picture worth watching — the web's own rule, "a column beside the picture,
 * not over it", since notes are screens of text and text over a video hides
 * both. A portrait window puts them below instead, which is the same idea
 * turned on its side.
 *
 * A phone held sideways is the one exception: at that height a column
 * beside the picture would leave a strip of film too small to follow, so the
 * notes come up as a sheet over the right of it. A deliberate difference,
 * because the web never runs in a window that short.
 */
internal fun notesPlacementFor(width: Dp, height: Dp): NotesPlacement = when {
    height > width -> NotesPlacement.BELOW
    height < MIN_BESIDE_HEIGHT -> NotesPlacement.OVER
    else -> NotesPlacement.BESIDE
}

/**
 * The player's stage with [notes] placed by [notesPlacementFor], or the stage
 * alone while there are none, they are closed, or the player has shrunk into
 * picture-in-picture, where there is no room for anything but the picture.
 */
@Composable
internal fun NotesLayout(notes: PlayerNotes?, isInPip: Boolean, onClose: () -> Unit, stage: @Composable () -> Unit) {
    if (notes == null || !notes.open || isInPip) {
        stage()
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val panel = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        when (notesPlacementFor(maxWidth, maxHeight)) {
            NotesPlacement.BESIDE -> Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f - PANEL_SHARE).fillMaxHeight()) { stage() }
                NotesPanel(notes.blocks, onClose, panel.weight(PANEL_SHARE).fillMaxHeight())
            }
            NotesPlacement.BELOW -> Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f - PANEL_SHARE).fillMaxWidth()) { stage() }
                NotesPanel(notes.blocks, onClose, panel.weight(PANEL_SHARE).fillMaxWidth())
            }
            NotesPlacement.OVER -> Box(modifier = Modifier.fillMaxSize()) {
                stage()
                NotesPanel(notes.blocks, onClose, panel.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(OVER_SHARE))
            }
        }
    }
}

/** The web gives its column about 38% of the width; the phone's is a touch wider, for thumbs. */
private const val PANEL_SHARE = 0.4f

private const val OVER_SHARE = 0.6f

/** Below this a landscape window is a phone on its side. */
private val MIN_BESIDE_HEIGHT = 480.dp
