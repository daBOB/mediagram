// Composable lifecycle glue shared by ui-mobile and ui-tv: Compose runtime,
// foundation and lifecycle only. Neither surface's component library
// belongs here — a dependency on one would make the other drag it in too.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.android.library.compose)
}

android {
    namespace = "com.mediagram.android.ui.common"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":feature:catalog"))
    implementation(project(":feature:player"))
    implementation(project(":feature:setup"))
    // rememberTitleInfo's TitleInfo return type is core:rust's, re-exposed
    // by core:data as `api`. feature:catalog only takes core:data as
    // `implementation`, which does not flow through to this module's own
    // compile classpath — ui-mobile carries the same direct dependency for
    // the same reason.
    implementation(project(":core:data"))
    // The library's position stack resolves its keys to catalogue values —
    // a title, a list, a watch snapshot — which are core:model's types.
    implementation(project(":core:model"))
    // The picture both players draw: media3's PlayerSurface and its
    // presentation state, which core:playback exposes as `api`. Media3's
    // Compose surface is not either surface's component library, so it
    // belongs here with the rest of the player glue both share.
    implementation(project(":core:playback"))
    implementation(libs.findLibrary("androidx.lifecycle.runtime.compose").get())
    implementation(libs.findLibrary("androidx.hilt.lifecycle.viewmodel.compose").get())

    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("mockk").get())
    testImplementation(libs.findLibrary("androidx.compose.ui.test.junit4").get())
    // Only the test host Activities need ComponentActivity.setContent; no
    // production code here reaches for Activity at all.
    testImplementation(libs.findLibrary("androidx.activity.compose").get())
}
