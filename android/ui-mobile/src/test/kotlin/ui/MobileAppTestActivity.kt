package ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

class MobileAppTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val held = fixture
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides held) { MobileApp() }
        }
    }

    companion object {
        internal lateinit var fixture: MobileAppFixture
    }
}
