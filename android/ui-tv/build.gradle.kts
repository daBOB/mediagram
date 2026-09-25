// The television renderer of the existing feature ViewModels. Plain deps,
// not a convention plugin: ui-mobile is the only other Compose-screen
// module and duplicating four lines for one consumer is not worth a plugin.
//
// No material3 here, ever — androidx.tv brings its own MaterialTheme, and
// the shared compose bundle (app.android.library.compose) deliberately
// leaves M3 out so this module never sees it on its compile classpath.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.android.library.compose)
}

android {
    namespace = "com.mediagram.android.ui.tv"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":ui-common"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:player"))
    implementation(project(":feature:setup"))
    implementation(project(":feature:system"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:playback"))
    // A show's header takes the index's TitleInfo, core:rust's type, the
    // same one ui-common's rememberTitleInfo returns. Only the bindings'
    // types: nothing here calls into the core, which stays behind the
    // ViewModels.
    implementation(project(":core:rust"))
    implementation(libs.findLibrary("androidx.tv.material").get())
    implementation(libs.findLibrary("coil.compose").get())
    implementation(libs.findLibrary("androidx.lifecycle.runtime.compose").get())
    implementation(libs.findLibrary("androidx.hilt.lifecycle.viewmodel.compose").get())
    implementation(libs.findLibrary("androidx.activity.compose").get())

    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("mockk").get())
    testImplementation(libs.findLibrary("androidx.compose.ui.test.junit4").get())
    // Only to build a real SetupViewModel against a mocked CoreClient in
    // TvAppTest, the way ui-mobile's own MobileAppFixture does; TvApp
    // itself never reaches core:data.
    testImplementation(project(":core:data"))

    // Focus, IME-submit and Back behaviour only run true on a real
    // window manager — Robolectric's tv-material nodes misbehave for
    // exactly this (see TvFocusTest) — so this module's first
    // androidTest set needs the real instrumentation Compose test APIs,
    // not just the JVM ones `testImplementation` above already has.
    androidTestImplementation(libs.findLibrary("androidx.compose.ui.test.junit4").get())
    // Backs the instrumented ComposeTestRule with a real Activity host;
    // without it, createAndroidComposeRule has nothing to launch.
    debugImplementation(libs.findLibrary("androidx.compose.ui.test.manifest").get())
    // TvConfirmDialog hosts its content in its own Dialog window; a Key.Back
    // sent through a Compose node interaction only ever reaches that node's
    // own semantics tree, not the separate window's real back-press
    // handling. Espresso.pressBack() injects the key event at the window
    // manager instead, the same way a physical remote's Back button would.
    androidTestImplementation(libs.findLibrary("androidx.espresso.core").get())
}
