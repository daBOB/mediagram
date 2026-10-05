plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
    alias(libs.plugins.app.android.security.crypto)
}

android {
    namespace = "com.mediagram.android.core.data"
}

dependencies {
    // `api`, not `implementation`: CoreProvider hands out the generated
    // CoreInterface directly (SetSummary, AuthOutcome and the rest of its
    // signatures come straight from the generated bindings), so anything
    // that calls through it needs them on its own classpath.
    api(project(":core:rust"))
    implementation(project(":core:model"))

    androidTestImplementation(libs.findLibrary("kotlinx.coroutines.test").get())

    testImplementation(project(":core:testing"))
    // TelevisionTest and BackdropWidthTest stand in for the system services
    // the surface and width choices read.
    testImplementation(libs.findLibrary("mockk").get())

    // ResumePointFixtureTest reads the web's own resume-point.json as plain
    // JSON — no @Serializable models, so the compiler plugin isn't needed,
    // just the runtime's JsonElement parser.
    testImplementation(libs.findLibrary("kotlinx.serialization").get())
}
