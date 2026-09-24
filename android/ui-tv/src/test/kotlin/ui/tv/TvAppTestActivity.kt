package ui.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

class TvAppTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val held = fixture
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides held) { TvApp() }
        }
    }

    companion object {
        internal lateinit var fixture: TvAppFixture
    }
}
