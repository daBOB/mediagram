// UniFFI bindings and the native .so land here in a later phase; this
// module is deliberately empty until then.
plugins {
    alias(libs.plugins.app.android.library)
}

android {
    namespace = "com.mediagram.android.core.rust"
}
