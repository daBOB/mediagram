plugins {
    alias(libs.plugins.app.android.library)
}

android {
    namespace = "com.mediagram.android.core.rust"

    lint {
        // The generated bindings choose their cleaner at runtime: they call
        // Class.forName("java.lang.ref.Cleaner") and fall back to JNA's
        // cleaner when it throws, so java.lang.ref.Cleaner is never loaded
        // below API 33. Lint cannot see through a reflective guard and
        // reports the calls inside that branch as NewApi. Scoped to this
        // module, which contains nothing but generated code; every
        // hand-written module still has NewApi on.
        disable += "NewApi"
    }
}

dependencies {
    // The generated bindings call into the native library through JNA; the
    // `@aar` classifier pulls the Android-packaged variant instead of the
    // desktop jar.
    implementation("${libs.findLibrary("jna").get().get()}@aar")
    implementation(libs.findLibrary("kotlinx.coroutines.android").get())
}
