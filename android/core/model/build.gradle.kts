plugins {
    alias(libs.plugins.app.jvm.library)
}

dependencies {
    // MarkdownFixtureTest and ProfileRolesFixtureTest read the web's own
    // fixtures as plain JSON — no @Serializable models, so the compiler
    // plugin isn't needed, just the runtime's JsonElement parser.
    testImplementation(libs.findLibrary("kotlinx.serialization").get())
}

// The shared fixtures live outside this module; declared so an edit to one
// re-runs the tests instead of Gradle calling them up to date.
tasks.named<Test>("test") {
    inputs.file(rootProject.file("../web/test/fixtures/markdown/cases.json"))
    inputs.file(rootProject.file("../web/test/fixtures/watch-state/profile-rules.json"))
}
