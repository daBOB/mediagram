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
}
