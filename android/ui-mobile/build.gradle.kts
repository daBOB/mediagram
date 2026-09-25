plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.android.library.compose)
    alias(libs.plugins.app.android.mobile.screen)
}

android {
    namespace = "com.mediagram.android.ui.mobile"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":feature:catalog"))
    implementation(project(":feature:player"))
    implementation(project(":feature:setup"))
    implementation(project(":feature:system"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    // PlayerScreen renders the ViewModel's own ExoPlayer through
    // PlayerSurface directly; feature:player exposes the player but keeps
    // it an implementation dependency, so this module needs its own.
    implementation(project(":core:playback"))
    implementation(libs.findLibrary("androidx.activity.compose").get())
    implementation(libs.findLibrary("androidx.compose.material.icons.core").get())
    // WindowCompat/WindowInsetsControllerCompat for ImmersiveEffect — the
    // app convention plugin only adds this to :app, and this module reaches
    // the Activity window directly rather than through it.
    implementation(libs.findLibrary("androidx.core").get())
    // PipRationalBoundsTest constructs a real android.util.Rational; the
    // plain unit-test android.jar stub leaves it zeroed rather than
    // throwing for it (isReturnDefaultValues), which is worse — a wrong
    // answer a test could pass by accident — so this runs under
    // Robolectric instead, same reasoning as DefaultPlayerHandleTest's
    // own android.net.Uri.
    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("mockk").get())
    testImplementation(libs.findLibrary("androidx.compose.ui.test.junit4").get())
}
