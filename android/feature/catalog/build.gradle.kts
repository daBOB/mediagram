// ViewModels and UiState only, no composables — ui-mobile and ui-tv render
// this module's state independently.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.feature.catalog"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))

    // NextUpFixtureTest reads the web's own next-up.json as plain JSON — no
    // @Serializable models, so the compiler plugin isn't needed, just the
    // runtime's JsonElement parser. Mirrors core:data's ResumePointFixtureTest.
    testImplementation(libs.findLibrary("kotlinx.serialization").get())
}
