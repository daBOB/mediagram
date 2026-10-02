# Phase 01 — Release key, release build, derived versionCode

## Context
- Spec § Components › Build and signing; decisions 3, 4, 6.
- `android/app/build.gradle.kts` today: `versionCode = 18` by hand; `release` unsigned and not minified; `benchmark` = `initWith(release)` + minified + debug-signed.
- `scripts/build-android-core.sh` builds four ABIs; packaging is filtered per build type below, the script is untouched.

## Task 1.1: Release keystore outside the repo (operator step, this machine only)

**Files:** none in the repo.
- Create: `~/.config/mediagram/release.keystore` (copy of `~/.android/debug.keystore`)
- Modify: `~/.gradle/gradle.properties`

- [ ] **Step 1: Copy the key and pin its identity**

```bash
mkdir -p ~/.config/mediagram && chmod 700 ~/.config/mediagram
cp ~/.android/debug.keystore ~/.config/mediagram/release.keystore && chmod 600 ~/.config/mediagram/release.keystore
keytool -list -v -keystore ~/.config/mediagram/release.keystore -storepass android | grep 'SHA256:'
```
Expected: `SHA256: 58:40:18:1D:3D:A5:F4:4C:9F:01:85:BF:4A:C3:34:5E:5F:E0:87:86:34:73:50:DB:04:AA:A6:E6:9D:F4:F5:76`. Anything else: stop.

- [ ] **Step 2: Gradle properties** — append to `~/.gradle/gradle.properties` (create if missing):

```properties
mediagram.signing.storeFile=/home/andre/.config/mediagram/release.keystore
mediagram.signing.storePassword=android
mediagram.signing.keyAlias=androiddebugkey
mediagram.signing.keyPassword=android
```

- [ ] **Step 3: Tell the user** to back up `~/.config/mediagram/release.keystore` somewhere off this machine (losing it means one reinstall + Telegram login per device).

## Task 1.2: Derived versionCode, signed arm-only release, flag per build type

**Files:**
- Modify: `android/app/build.gradle.kts` (top-level function, `defaultConfig`, `signingConfigs`, `buildTypes`)
- Modify: `CLAUDE.md` (§ Versioning paragraph on versionCode; § Changelog)

**Interfaces:**
- Produces: app resource `R.bool.self_update` (`true` only in `release`) — read by Task 4.5; release APK `android/app/build/outputs/apk/release/app-release.apk` — read by Task 2.4.

- [ ] **Step 1: Write the build changes.** In `android/app/build.gradle.kts`, add above `android {`:

```kotlin
/**
 * The versionCode Android compares before installing an update, derived from
 * versionName so a release can never forget to raise it and two machines
 * can never hand out the same one: 0.93.0 → 93000.
 */
fun versionCodeOf(name: String): Int {
    val parts = name.split(".").map { it.toIntOrNull() ?: error("versionName $name is not major.minor.patch") }
    require(parts.size == 3) { "versionName $name is not major.minor.patch" }
    val (major, minor, patch) = parts
    require(minor < 1000 && patch < 1000) { "versionName $name: minor and patch must stay below 1000" }
    return major * 1_000_000 + minor * 1_000 + patch
}

/** This machine's release key, from ~/.gradle/gradle.properties; absent elsewhere, so a release built there is unsigned. */
val releaseStoreFile: String? = providers.gradleProperty("mediagram.signing.storeFile").orNull
```

Replace the `defaultConfig { … }` block with:

```kotlin
    defaultConfig {
        applicationId = "com.mediagram.android"
        versionName = "0.92.2"
        versionCode = versionCodeOf(versionName!!)
        // Only a release build updates itself; debug and benchmark builds are installed by adb.
        resValue("bool", "self_update", "false")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("mediagram.signing.storePassword").get()
                keyAlias = providers.gradleProperty("mediagram.signing.keyAlias").get()
                keyPassword = providers.gradleProperty("mediagram.signing.keyPassword").get()
            }
        }
    }
```
(Keep whatever `versionName` value is current — the bump in Step 5 moves it.)

Replace the `release { … }` build type with:

```kotlin
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The two ABIs real devices have: the tablet (arm64) and the TV box (armv7 only).
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            resValue("bool", "self_update", "true")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
```

In `create("benchmark") { … }`, after `initWith(getByName("release"))`, add:

```kotlin
            // Measuring builds are installed by adb and never replace themselves; any ABI, for emulators too.
            resValue("bool", "self_update", "false")
            ndk { abiFilters.clear() }
```
(Its existing `isMinifyEnabled`, `isShrinkResources`, `isDebuggable = false`, debug `signingConfig` and `matchingFallbacks` lines stay.) Update the benchmark comment's sentence "`release` above is not minified yet, so what this measures is the ceiling a minified release would reach, not what today's release delivers." to "`release` above is minified the same way, so this measures what a release delivers."

- [ ] **Step 2: Build and verify the release APK**

```bash
cd android && ./gradlew -q :app:assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
BT=$(ls -d ~/android-sdk/build-tools/*/ | sort -V | tail -1)
${BT}aapt2 dump badging $APK | head -1
${BT}apksigner verify --print-certs $APK | grep 'SHA-256'
unzip -l $APK | grep -o 'lib/[^/]*/' | sort -u
```
Expected: `versionCode='92002' versionName='0.92.2'` (or the current version); digest `5840181d3da5f44c9f0185bf4ac3345e5fe08786347350db04aaa6e69df4f576`; only `lib/arm64-v8a/` and `lib/armeabi-v7a/`.

- [ ] **Step 3: Verify benchmark and debug are unchanged in kind**

```bash
./gradlew -q :app:assembleBenchmark :app:assembleDebug
unzip -l app/build/outputs/apk/benchmark/app-benchmark.apk | grep -o 'lib/[^/]*/' | sort -u
```
Expected: four ABI directories for benchmark; both builds succeed.

- [ ] **Step 4: Rewrite the versionCode paragraph in `CLAUDE.md` § Versioning.** Show the user this diff before writing (CLAUDE.md § 4 — the user chose derivation on 2026-10-02). Replace:

```
`versionCode` beside it is not part of this. It counts builds for Android's
own upgrade check and only ever goes up by one; tying it to semver would mean
inventing an integer from a dotted string, and the two answer different
questions.
```
with:
```
`versionCode` beside it is never edited by hand: `android/app/build.gradle.kts`
derives it from `versionName` as `major·1,000,000 + minor·1,000 + patch`
(0.93.0 → 93000). The app updates itself from the channel, and Android
installs an update only over a lower versionCode; deriving it means a release
can never forget to raise it and two machines can never hand out the same
one. Minor and patch stay below 1000.
```
Append to `## Changelog`:
```
- 2026-10-02: § Versioning: versionCode is derived from versionName instead of counted by hand. The Android app now updates itself from the channel, which Android allows only to a higher versionCode; the hand-kept counter had sat at 18 since 0.81. The user chose derivation over a counter bumped at publish time.
```

- [ ] **Step 5: Bump (patch) by pattern, check, commit**

```bash
cd /home/andre/Workspace/mediagram
V=$(grep -m1 -oE '^version = "[0-9.]+"' Cargo.toml | grep -oE '[0-9.]+' | awk -F. '{print $1"."$2"."$3+1}')
sed -i -E '0,/^version = "[0-9.]+"/s//version = "'$V'"/' Cargo.toml
sed -i -E '0,/"version": "[0-9.]+"/s//"version": "'$V'"/' web/package.json
sed -i -E 's/versionName = "[0-9.]+"/versionName = "'$V'"/' android/app/build.gradle.kts
cargo update --workspace --offline -q
scripts/check.sh
git add android/app/build.gradle.kts CLAUDE.md Cargo.toml Cargo.lock web/package.json
git commit -m "build(android): release builds are minified, arm-only and signed with this machine's key; versionCode follows versionName; release $V"
```
Expected: `scripts/check.sh` green.

## Success criteria
Release APK signed with the pinned digest, arm-only, versionCode derived; benchmark/debug still build; CLAUDE.md updated with the user's sign-off.

## Risks
- R8 on `release` strips something `benchmark` never exercised → same proguard rules as benchmark, which already runs on the TV box; Phase 05 smoke-tests the release build on both devices before any publish.
