// Media3 lives here, not in feature:player: MlibDataSource, the disk cache
// and the player factory are the only code that touches ExoPlayer directly.
// `api`, not `implementation`, for the media3 artifacts — feature:player and
// the mobile UI both need `ExoPlayer`/`Player`/`PlayerSurface` on their own
// compile classpath, and a project dependency only forwards a module's own
// `api` dependencies to its consumers.
plugins {
    alias(libs.plugins.app.android.library)
}

android {
    namespace = "com.mediagram.android.core.playback"
}

dependencies {
    implementation(project(":core:data"))

    api(libs.findLibrary("androidx.media3.exoplayer").get())
    api(libs.findLibrary("androidx.media3.datasource").get())
    api(libs.findLibrary("androidx.media3.ui.compose").get())
    implementation(libs.findLibrary("androidx.media3.database").get())

    // MlibDataSource builds a real android.net.Uri and PlayerFactory builds
    // a real, database-backed SimpleCache; the plain unit-test android.jar
    // stub throws for both, so these tests run under Robolectric rather
    // than the bare JVM. androidx-junit brings ApplicationProvider, for a
    // real Context the database-backed cache can actually open SQLite
    // against.
    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("androidx.junit").get())
}
