package ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/** The Activity's saved-state registry remains real while ViewModels come from controlled IO fixtures. */
class LibraryFlowTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val held = fixture
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides held) {
                MaterialTheme {
                    CatalogAndPlayer(onStartOver = { held.startOvers++ }, onSignedOut = { held.signedOut++ })
                }
            }
        }
    }

    companion object {
        internal lateinit var fixture: LibraryFlowFixture
    }
}
