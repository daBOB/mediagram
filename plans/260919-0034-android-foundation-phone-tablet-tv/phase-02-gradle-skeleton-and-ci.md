# Phase 2: Gradle skeleton, module graph, CI

**Context:** [plan.md](plan.md) · [spec §4, §5](../../docs/superpowers/specs/2026-09-19-android-foundation-design.md)

## Overview

- **Priority:** High. Everything Kotlin sits on this.
- **Status:** Blocked by phase 0 reporting PASS. Independent of phase 1.
- **Deliverable:** an APK that builds, installs and launches on a phone and on a television, showing a placeholder screen on each.

## Key insights

- The skill's greenfield path supplies the templates: `assets/settings.gradle.kts.template`, `assets/libs.versions.toml.template`, `assets/convention/`, `assets/proguard-rules.pro.template`.
- The catalog carries **no** `androidx.tv` entry and only `media3-exoplayer` / `media3-session`. TV artifacts are added in phase 4, player UI artifacts in phase 5.
- Generated UniFFI bindings are **committed**, and CI regenerates and diffs them. Otherwise every Android build needs an NDK and a Rust toolchain, which is a heavy tax for UI work.
- `./gradlew help` before `assembleDebug` after any toolchain or module change — the skill requires both.

## Related code files

- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle/libs.versions.toml`, `android/build-logic/**`, `android/app/**`, `android/core/{rust,model,data,designsystem}/**`, `android/feature/{catalog,player}/**`, `android/ui-mobile/**`, `android/ui-tv/**`
- Create: `.github/workflows/android.yml`
- Modify: `.gitignore`

---

### Task 1: Root build, version catalog, convention plugins

**Files:** Create `android/settings.gradle.kts`, `android/gradle/libs.versions.toml`, `android/build-logic/settings.gradle.kts`, `android/build-logic/convention/**`, `android/gradle.properties`

- [ ] **Step 1:** Copy `assets/libs.versions.toml.template` → `android/gradle/libs.versions.toml` and `assets/settings.gradle.kts.template` → `android/settings.gradle.kts`. Copy every file from `assets/convention/` into `android/build-logic/convention/src/main/kotlin/`, and create `android/build-logic/settings.gradle.kts` per `assets/convention/QUICK_REFERENCE.md`. Copy `assets/detekt.yml.template` → `android/config/detekt/detekt.yml` and enable the Compose rules — task 5 runs `detekt` in CI and it needs a config to run against.
- [ ] **Step 2:** Add `includeBuild("build-logic")` to `android/settings.gradle.kts`, and the plugin entries from `QUICK_REFERENCE.md` to the catalog.
- [ ] **Step 3:** Confirm the pins match the plan's Global Constraints exactly: `agp = "9.3.1"`, `kotlin = "2.3.21"`, `ksp = "2.3.10"`, `compileSdk = "37"`, `targetSdk = "37"`, `minSdk = "24"`.
- [ ] **Step 4:** Run `cd android && ./gradlew help`. Expected: BUILD SUCCESSFUL, no plugin resolution errors.
- [ ] **Step 5:** Commit — `build(android): add the Gradle build with convention plugins`.

---

### Task 2: The module graph

**Files:** Create `build.gradle.kts` for each of `app`, `core:rust`, `core:model`, `core:data`, `core:designsystem`, `feature:catalog`, `feature:player`, `ui-mobile`, `ui-tv`; add each to `android/settings.gradle.kts`

**Interfaces — Produces:** the Gradle paths phases 3–5 depend on, exactly: `:core:rust`, `:core:model`, `:core:data`, `:core:designsystem`, `:feature:catalog`, `:feature:player`, `:ui-mobile`, `:ui-tv`.

- [ ] **Step 1:** Create the nine modules, each applying the matching convention plugin. Dependency direction, enforced by review: `ui-* → feature:* → core:data → core:rust`, everything may depend on `core:model`, and **no feature depends on another feature**.
- [ ] **Step 2:** `core:model` is a pure Kotlin (JVM) module — no Android plugin. It holds the Kotlin mirrors of the DTOs.
- [ ] **Step 3:** Run `./gradlew projects`. Expected: all nine listed.
- [ ] **Step 4:** Run `./gradlew help && ./gradlew :app:assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit — `build(android): lay out the core, feature and surface modules`.

---

### Task 3: Package the native library and its bindings

**Files:** Create `android/core/rust/src/main/jniLibs/**` (gitignored), `android/core/rust/src/main/kotlin/**` (generated, committed), `scripts/generate-android-bindings.sh`; modify `.gitignore`

**Interfaces — Produces:** the generated Kotlin `Core` class from phase 1 task 7, importable by `:core:data`.

- [ ] **Step 1:** Write `scripts/generate-android-bindings.sh`, which runs `scripts/build-android-core.sh` (phase 1 task 8) and then `uniffi-bindgen generate … --language kotlin --out-dir android/core/rust/src/main/kotlin`.
- [ ] **Step 2:** Run it. Expected: a Kotlin file declaring `Core` with `suspend fun read`, and `.so` files under `jniLibs/arm64-v8a/` and `jniLibs/x86_64/`.
- [ ] **Step 3:** Gitignore the `.so` files; commit the generated Kotlin.
- [ ] **Step 4:** Add `implementation("net.java.dev.jna:jna:…@aar")` to `:core:rust` — UniFFI's Kotlin bindings need it at runtime. Pin the version the generated bindings ask for.
- [ ] **Step 5:** Write the smoke test `android/core/rust/src/androidTest/kotlin/CoreLoadsTest.kt`:

```kotlin
@Test
fun theNativeLibraryLoadsAndReportsNoSession() {
    val dir = ApplicationProvider.getApplicationContext<Context>().filesDir
    val core = Core(dir.absolutePath)
    assertFalse(core.isAuthorized())
}
```

- [ ] **Step 6:** Run `./gradlew :core:rust:connectedDebugAndroidTest` on an emulator. Expected: PASS. A `ClassNotFoundException` or `UnsatisfiedLinkError` here means the ABI or the alignment is wrong, not the test.
- [ ] **Step 7:** Commit — `feat(android): load the native core and its generated bindings`.

---

### Task 4: The app module and surface selection

**Files:** Create `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/kotlin/**/MainActivity.kt`, `android/app/src/main/kotlin/**/MediagramApp.kt`, `android/app/proguard-rules.pro`

**Interfaces — Produces:** `fun isTelevision(context: Context): Boolean`, consumed by phase 3 and 4.

- [ ] **Step 1:** Copy `assets/proguard-rules.pro.template` to `android/app/proguard-rules.pro`.
- [ ] **Step 2:** Manifest — both launchers, and the hardware declarations that let one APK install on a television:

```xml
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<uses-permission android:name="android.permission.INTERNET" />

<activity android:name=".MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity>
```

Set `android:banner` on `<application>` — a television requires one.

- [ ] **Step 3: Write the failing test** `android/app/src/test/kotlin/SurfaceSelectionTest.kt`:

```kotlin
@Test
fun aTelevisionUiModeSelectsTheTvSurface() {
    val context = mockContextWithUiMode(Configuration.UI_MODE_TYPE_TELEVISION)
    assertTrue(isTelevision(context))
}

@Test
fun aPhoneUiModeSelectsTheTouchSurface() {
    val context = mockContextWithUiMode(Configuration.UI_MODE_TYPE_NORMAL)
    assertFalse(isTelevision(context))
}
```

- [ ] **Step 4:** Run `./gradlew :app:testDebugUnitTest`. Expected: FAIL, `isTelevision` unresolved.
- [ ] **Step 5: Implement**

```kotlin
fun isTelevision(context: Context): Boolean =
    (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)
        .currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
```

`MainActivity` branches on it once, in `setContent`, and renders a placeholder for each surface until phases 3 and 4 fill them.

- [ ] **Step 6:** Run `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 7:** Install on a phone and on a TV emulator; confirm each shows its placeholder and the TV one appears in the leanback launcher.
- [ ] **Step 8:** Commit — `feat(android): pick the touch or television surface at launch`.

---

### Task 5: Hilt and CI

**Files:** Modify `android/app/**` for Hilt; create `.github/workflows/android.yml`

- [ ] **Step 1:** Apply the Hilt convention plugin to `:app`, `:core:data`, `:feature:catalog`, `:feature:player`; annotate `MediagramApp` with `@HiltAndroidApp` and `MainActivity` with `@AndroidEntryPoint`.
- [ ] **Step 2:** Run `./gradlew help && ./gradlew :app:assembleDebug`. Expected: BUILD SUCCESSFUL. KSP errors here are DI wiring, not Gradle.
- [ ] **Step 3:** Write `.github/workflows/android.yml` running, on push and pull request: `./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug`, plus a step that runs `scripts/generate-android-bindings.sh` and fails if `git diff --exit-code android/core/rust/src/main/kotlin` is dirty.
- [ ] **Step 4:** Push the branch; confirm the workflow is green.
- [ ] **Step 5:** Commit — `ci(android): build, lint and test the Android app`.

## Todo list

- [ ] Convention plugins and catalog in place, `./gradlew help` green
- [ ] Nine modules created, dependency direction respected
- [ ] `.so` and generated bindings packaged; native smoke test passes on device
- [ ] One APK installs on phone and television, correct surface each time
- [ ] Hilt wired; CI green, binding drift detected

## Success criteria

`./gradlew help` then `:app:assembleDebug` succeed. The APK installs on a
phone and on an Android TV emulator, launching the right placeholder on each,
and `:core:rust:connectedDebugAndroidTest` proves the native library loads.

## Risk assessment

| Risk | Mitigation |
|---|---|
| `androidx.hilt`, Compose or nav3 force an AGP floor | The catalog already pins AGP 9.3.1, above the 9.2.0 floor those libraries require. |
| `UnsatisfiedLinkError` on device | Task 3 step 6 catches it before any UI exists; the cause is ABI coverage or 16 KB alignment, both checked in phase 1 task 8. |
| Binding drift between Rust and committed Kotlin | CI regenerates and diffs (task 5 step 3). |
| R8 strips UniFFI's reflective entry points in release | Out of scope for a debug-only skeleton; add keep rules when a release build is first attempted, per the skill's R8 guidance. |

## Security considerations

`android/core/rust/src/main/jniLibs/` is gitignored — never commit binaries.
No credential is introduced in this phase; the app has `INTERNET` and nothing
else.

## Next steps

[Phase 3](phase-03-login-and-catalog-mobile.md).
