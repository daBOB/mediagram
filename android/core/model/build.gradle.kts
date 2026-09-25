plugins {
    alias(libs.plugins.app.jvm.library)
}

dependencies {
    // MarkdownFixtureTest reads the web's own cases.json as plain JSON — no
    // @Serializable models, so the compiler plugin isn't needed, just the
    // runtime's JsonElement parser.
    testImplementation(libs.findLibrary("kotlinx.serialization").get())
}

// The shared fixture lives outside this module; declared so an edit to it
// re-runs the test instead of Gradle calling it up to date.
tasks.named<Test>("test") {
    inputs.file(rootProject.file("../web/test/fixtures/markdown/cases.json"))
}
