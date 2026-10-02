# Phase 00 — Spike: can the app replace itself silently?

**Throwaway.** Nothing here is merged. The output is a results report and a recommendation; the probe app, its branch and its worktree are deleted at the end.

## Context
- Spec: `docs/superpowers/specs/2026-10-02-android-self-update-design.md` (Rollout §1)
- Research: `plans/reports/researcher-261002-0100-android-self-update-packageinstaller-rules-report.md`
- Memory: pin every device command to a serial; TV key/tap scripts stop at the first unexpected screen.

## Questions to answer (per device: tablet `caad49da`, TV box `192.168.0.35:5555`)
1. Does an `adb`-installed app replace itself with a newer build of itself without any prompt (`USER_ACTION_NOT_REQUIRED`)?
2. Also when the session is committed from `onStop` (app just backgrounded)?
3. Is the `REQUEST_INSTALL_PACKAGES` app-op needed on top of `UPDATE_PACKAGES_WITHOUT_USER_ACTION`?
4. Does HyperOS (tablet) show a scan/block dialog?
5. Which Android version/SDK does the TV box run?

## Task 0.1: Probe app on a throwaway branch

**Files (worktree `../mediagram-spike-update`, branch `spike/self-update-probe`, never merged):**
- Create: `android/spike-update/build.gradle.kts`
- Create: `android/spike-update/src/main/AndroidManifest.xml`
- Create: `android/spike-update/src/main/kotlin/probe/ProbeActivity.kt`
- Create: `android/spike-update/src/main/kotlin/probe/ProbeResultReceiver.kt`
- Modify: `android/settings.gradle.kts` (add `include(":spike-update")`)

- [ ] **Step 1: Create the worktree**

```bash
cd /home/andre/Workspace/mediagram
git worktree add -b spike/self-update-probe ../mediagram-spike-update main
cp -r android/core/rust/src/main/jniLibs ../mediagram-spike-update/android/core/rust/src/main/  # gitignored; same crates on main, so safe to copy
cd ../mediagram-spike-update/android
```

- [ ] **Step 2: Module build file** — `android/spike-update/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.app.android.application)
}

android {
    namespace = "com.mediagram.updateprobe"
    defaultConfig {
        applicationId = "com.mediagram.updateprobe"
        val code = providers.gradleProperty("probe.code").orElse("1").get().toInt()
        versionCode = code
        versionName = "probe-$code"
    }
    buildTypes {
        // Non-debuggable like a real release; this machine's key, like a real release.
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
```

Add `include(":spike-update")` after `include(":app")` in `android/settings.gradle.kts`.

- [ ] **Step 3: Manifest** — `android/spike-update/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <application android:label="Update probe">
        <activity android:name="probe.ProbeActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
        <receiver android:name="probe.ProbeResultReceiver" android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 4: Activity** — `android/spike-update/src/main/kotlin/probe/ProbeActivity.kt`

```kotlin
package probe

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import java.io.File

/** Driven by adb: `--es action now` installs at once, `--es action arm` installs at the next onStop. */
class ProbeActivity : Activity() {
    private var armed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val last = getSharedPreferences("probe", MODE_PRIVATE).getString("result", "none")
        setContentView(TextView(this).apply { textSize = 28f; text = "versionCode=$code sdk=${Build.VERSION.SDK_INT}\nlast: $last" })
        Log.i(TAG, "started versionCode=$code sdk=${Build.VERSION.SDK_INT} last=$last")
        when (intent.getStringExtra("action")) {
            "now" -> install(this)
            "arm" -> armed = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (armed) {
            armed = false
            Log.i(TAG, "committing from onStop")
            install(this)
        }
    }

    companion object {
        const val TAG = "Probe"

        fun install(context: Context) {
            val apk = File(context.cacheDir, "v2.apk")
            context.assets.open("v2.apk").use { input -> apk.outputStream().use { input.copyTo(it) } }
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = try {
                installer.createSession(params)
            } catch (e: Exception) {
                Log.e(TAG, "createSession refused", e)
                context.getSharedPreferences("probe", MODE_PRIVATE).edit().putString("result", "createSession: $e").apply()
                return
            }
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, 0, Intent(context, ProbeResultReceiver::class.java), flags)
                session.commit(pending.intentSender)
                Log.i(TAG, "committed session $sessionId")
            }
        }
    }
}
```

- [ ] **Step 5: Receiver** — `android/spike-update/src/main/kotlin/probe/ProbeResultReceiver.kt`

```kotlin
package probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class ProbeResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val line = "status=$status message=$message"
        Log.i(ProbeActivity.TAG, "result $line")
        context.getSharedPreferences("probe", Context.MODE_PRIVATE).edit().putString("result", line).apply()
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            try {
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Log.i(ProbeActivity.TAG, "confirm screen started")
            } catch (e: Exception) {
                Log.e(ProbeActivity.TAG, "confirm screen refused", e)
            }
        }
    }
}
```

- [ ] **Step 6: Build version 2, embed it in version 1**

```bash
cd ../mediagram-spike-update/android
./gradlew -q :spike-update:assembleRelease -Pprobe.code=2
mkdir -p spike-update/src/main/assets
cp spike-update/build/outputs/apk/release/spike-update-release.apk spike-update/src/main/assets/v2.apk
./gradlew -q :spike-update:assembleRelease -Pprobe.code=1
cp spike-update/build/outputs/apk/release/spike-update-release.apk /tmp/claude-1000/probe-v1.apk
```
Expected: both builds succeed; `aapt2 dump badging /tmp/claude-1000/probe-v1.apk | head -1` shows `versionCode='1'`.

## Task 0.2: Run the matrix on each device

- [ ] **Step 1: Connect and record the OS**

```bash
adb connect 192.168.0.35:5555
for S in caad49da 192.168.0.35:5555; do echo "$S $(adb -s $S shell getprop ro.build.version.release) sdk $(adb -s $S shell getprop ro.build.version.sdk)"; done
```

- [ ] **Step 2: One case = fresh v1, set the app-op, trigger, read the result.** Run for each device `S` and each case:

| Case | app-op `REQUEST_INSTALL_PACKAGES` | trigger |
|---|---|---|
| A | default | `--es action now` |
| B | default | `--es action arm`, then `input keyevent KEYCODE_HOME` |
| C | allow | `--es action now` |
| D | allow | `--es action arm`, then `input keyevent KEYCODE_HOME` |

```bash
P=com.mediagram.updateprobe
adb -s $S uninstall $P >/dev/null 2>&1; adb -s $S install /tmp/claude-1000/probe-v1.apk
adb -s $S shell appops set $P REQUEST_INSTALL_PACKAGES default   # or: allow
adb -s $S logcat -c
adb -s $S shell am start -n $P/probe.ProbeActivity --es action now   # or: arm; then: adb -s $S shell input keyevent KEYCODE_HOME
sleep 15
adb -s $S logcat -d -s Probe:* PackageInstaller:* | tail -20
adb -s $S shell dumpsys package $P | grep -m1 versionCode
adb -s $S exec-out screencap -p > /tmp/claude-1000/probe-$S-$CASE.png   # look for any dialog
```
Expected for a silent success: `result status=0`, `versionCode=2`, no dialog on the screenshot.

- [ ] **Step 3: Clean up the devices**

```bash
for S in caad49da 192.168.0.35:5555; do adb -s $S uninstall com.mediagram.updateprobe; done
```

## Task 0.3: Report and delete the spike

- [ ] **Step 1:** Write `plans/261002-0213-android-self-update/reports/spike-silent-self-update-results.md`: OS per device, the 8-cell result table (status, versionCode after, dialog seen), the answer to each question above, and a recommendation (keep or drop `REQUEST_INSTALL_PACKAGES`; whether Task 4.7 is needed; any device that cannot update silently).
- [ ] **Step 2:** Remove the spike: `git worktree remove ../mediagram-spike-update --force && git branch -D spike/self-update-probe`.
- [ ] **Step 3:** Commit the report on `main`: `git add plans/261002-0213-android-self-update/reports/ && git commit -m "docs(plan): self-update spike results"`.
- [ ] **Step 4: Gate.** Show the user the table. Any device without a silent path → user decides before Phase 01.

## Success criteria
Every question above answered for both devices with log evidence; probe uninstalled; spike branch gone.
