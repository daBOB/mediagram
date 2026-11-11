// The app replacing itself with the newest release its ReleaseSource knows. A
// process-lifetime service like WatchSync, so it lives in core and the System
// feature reads it without importing another feature. No composables: the System
// screen renders its status line. No Hilt and no media core: Spieltisch uses it
// too, and its APK carries neither, so each app builds its own source and updater.
plugins {
    alias(libs.plugins.app.android.library)
}

android {
    namespace = "com.mediagram.android.core.update"
}
