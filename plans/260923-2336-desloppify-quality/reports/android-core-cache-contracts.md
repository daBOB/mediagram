# Android native lifecycle contracts and cache assembly

Status: DONE for these two findings; the controller retains the broader Android gate.

CoreClient now distinguishes ordinary operational fallback from coroutine
cancellation and invalid/closed native-handle failure. The shared KDoc and
catalogFacts/syncState guarantees match DefaultCoreClient's direct delegation
and the generated binding lifecycle; no new exception-swallowing wrapper or
public signature change was introduced.

PlayerFactoryTest now reads 8,193 patterned bytes starting at offset 73 through
the actual cacheDataSourceFactory and database-backed SimpleCache. It checks
returned length, every byte, EOF, committed disk occupancy, initial upstream
counters and zero initial cached bytes. A fresh DataSource reopens the same
range; bytes remain exact, core.read count and upstream bytes do not increase,
and cached bytes increase by the requested length. Every source closes in
finally, and teardown releases/reset the shared cache. This is new integration
coverage of existing behavior, not a claim of a repaired cache implementation.

Read-only independent review by web_baseline found no defect. It verified the
native/cancellation contract and production factory/cache/counter wiring, then
left execution to the controller. Scoped ktlint formatting passes. The focused
command from android/ passes:

```sh
JAVA_HOME=/home/andre/.local/opt/jdk-21.0.8 ANDROID_HOME=/home/andre/android-sdk \
  ./gradlew :core:playback:testDebugUnitTest --tests playback.PlayerFactoryTest \
  --console=plain -Duser.country=US -Duser.language=en
```

Evidence: `/tmp/android-player-cache-integration.log`, BUILD SUCCESSFUL, 44 tasks,
2 seconds. Existing Compose opt-in configuration warnings remain visible. No
live Telegram transport, user media or credentials were accessed. No mutation
result or full Android-suite result is claimed for this checkpoint.
