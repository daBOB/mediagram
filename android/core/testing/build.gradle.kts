plugins {
    alias(libs.plugins.app.android.library)
}

android {
    namespace = "com.mediagram.android.core.testing"
}

dependencies {
    // `api`: FakeCore implements CoreInterface and every fake provider wraps
    // CoreProvider, both declared in core:data — a module depending on this
    // one for its tests needs those types on its own classpath too.
    api(project(":core:data"))
    // WatchStateFixture takes the profiles it seeds as the repository hands
    // them back, so a test compares like with like; FakeProfiles enforces the
    // same `allowed` Manage offers from.
    implementation(project(":core:model"))

    // The contract suite's `@Test` methods and `kotlin.test` assertions live
    // in this module's main source set, not test/ — androidTest in another
    // module runs them too, and only main is visible across a project
    // dependency. `api` so that visibility carries to every consumer.
    api(libs.findLibrary("kotlin.test.junit").get())
}
