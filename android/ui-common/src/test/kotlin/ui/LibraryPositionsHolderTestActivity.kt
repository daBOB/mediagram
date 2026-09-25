package ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Hosts [rememberLibraryPositions] alone, so the one saveable value it
 * holds can be exercised through a real `Bundle` save/restore cycle rather
 * than only a fresh composition.
 */
class LibraryPositionsHolderTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            holder = rememberLibraryPositions()
        }
    }

    companion object {
        internal lateinit var holder: LibraryPositionsHolder
    }
}
