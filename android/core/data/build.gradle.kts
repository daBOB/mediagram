plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
    alias(libs.plugins.app.android.security.crypto)
}

android {
    namespace = "com.mediagram.android.core.data"
}

dependencies {
    // `api`, not `implementation`: CoreClient's own signatures (SetSummary,
    // AuthOutcome) come straight from the generated bindings, so anything
    // that implements or calls CoreClient needs them on its own classpath.
    api(project(":core:rust"))
    implementation(project(":core:model"))

    androidTestImplementation(libs.findLibrary("kotlinx.coroutines.test").get())

    // ResumePointFixtureTest reads the web's own resume-point.json as plain
    // JSON — no @Serializable models, so the compiler plugin isn't needed,
    // just the runtime's JsonElement parser.
    testImplementation(libs.findLibrary("kotlinx.serialization").get())
}
