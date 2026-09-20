plugins {
    alias(libs.plugins.app.android.library)
}

android {
    namespace = "com.mediagram.android.core.rust"
}

dependencies {
    // The generated bindings call into the native library through JNA; the
    // `@aar` classifier pulls the Android-packaged variant instead of the
    // desktop jar.
    implementation("${libs.findLibrary("jna").get().get()}@aar")
    implementation(libs.findLibrary("kotlinx.coroutines.android").get())
}
