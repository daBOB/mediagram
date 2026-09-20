# Phase 5: Playback on all three

**Context:** [plan.md](plan.md) · [phase 1](phase-01-mediagram-core-and-uniffi.md) · [phase 3](phase-03-login-and-catalog-mobile.md) · [spec §3](../../docs/superpowers/specs/2026-09-19-android-foundation-design.md)

## Overview

- **Priority:** Highest — this is the round's definition of done.
- **Status:** Blocked by phase 3. Independent of phase 4.
- **Deliverable:** a real film, from the channel, playing and seeking on phone, tablet and television.

## Key insights

- **No transcoding anywhere.** Media3 decodes Matroska, HEVC and AC-3 natively. The whole reason `web/transcode/` exists is that browsers refuse them; that reason does not survive the move to Android.
- **Media3 supplies the cache.** `SimpleCache` + `CacheDataSource` give disk caching, eviction and readahead. `MlibDataSource` stays thin: it calls `read` and accounts for position. `web/src/cache/` gets no counterpart.
- `DataSource.read()` is blocking and runs on ExoPlayer's loader thread. Blocking there is correct. Blocking on main is a bug — and the one place in this codebase where `runBlocking` is allowed.
- The URI is opaque: `mlib://set/<set-id>`. No `chat_id`, no `message_id`, no `doc_id` reaches the player, matching `docs/system-architecture.md` §7.

## Related code files

- Create: `android/core/playback/src/main/kotlin/{MlibDataSource.kt,PlayerFactory.kt,CacheProvider.kt}`, `android/feature/player/src/main/kotlin/PlayerViewModel.kt`, `android/ui-mobile/src/main/kotlin/PlayerScreen.kt`, `android/ui-tv/src/main/kotlin/TvPlayerScreen.kt`
- Modify: `android/gradle/libs.versions.toml`, `android/ui-mobile/src/main/kotlin/MobileApp.kt`, `android/ui-tv/src/main/kotlin/TvApp.kt`

---

### Task 1: The byte source ExoPlayer reads through

**Files:** Create `android/core/playback/src/main/kotlin/MlibDataSource.kt` · Test `android/core/playback/src/test/kotlin/MlibDataSourceTest.kt`

**Interfaces — Consumes:** phase 3 task 2's `CoreClient.totalSize(setId)` and `CoreClient.read(setId, offset, len)` — the interface, never the generated `Core`. **Produces:** `class MlibDataSource(core: CoreClient) : BaseDataSource(true)` and `class MlibDataSourceFactory(core: CoreClient) : DataSource.Factory`, plus `fun setUri(setId: String): Uri` returning `mlib://set/<setId>`.

- [ ] **Step 1:** Add `androidx-media3-exoplayer`, `androidx-media3-datasource` and `androidx-media3-ui-compose` to the catalog and to `:core:playback`.

- [ ] **Step 2: Write the failing test**

```kotlin
private fun coreWithBytes(n: Int) = FakeCore(
    // byte at index i has value (i % 251).toByte() — a pattern that catches
    // both a wrong offset and a wrong length.
    bytesOf = { off, len -> ByteArray(len) { ((off + it) % 251).toByte() } },
    totalSize = n.toLong(),
)

@Test
fun openReportsTheWholeSetWhenNoLengthIsAsked() {
    val source = MlibDataSource(coreWithBytes(1_000))
    val available = source.open(DataSpec(setUri("s1")))
    assertEquals(1_000L, available)
}

@Test
fun aSeekStartsReadingAtTheRequestedOffset() {
    val source = MlibDataSource(coreWithBytes(1_000))
    source.open(DataSpec.Builder().setUri(setUri("s1")).setPosition(400).build())
    val buf = ByteArray(4)
    source.read(buf, 0, 4)
    assertEquals((400 % 251).toByte(), buf[0])
}

@Test
fun readingPastTheEndReportsEndOfInput() {
    val source = MlibDataSource(coreWithBytes(8))
    source.open(DataSpec(setUri("s1")))
    source.read(ByteArray(8), 0, 8)
    assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
}

@Test
fun aShortReadAdvancesOnlyByWhatArrived() {
    val source = MlibDataSource(coreWithShortReads())
    source.open(DataSpec(setUri("s1")))
    val first = source.read(ByteArray(64), 0, 64)
    val buf = ByteArray(4)
    source.read(buf, 0, 4)
    assertEquals((first % 251).toByte(), buf[0])
}
```

- [ ] **Step 3:** Run `./gradlew :core:playback:testDebugUnitTest`. Expected: FAIL, unresolved reference.

- [ ] **Step 4: Implement**

```kotlin
class MlibDataSource(private val core: CoreClient) : BaseDataSource(true) {
    private var setId: String? = null
    private var position = 0L
    private var remaining = 0L
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val id = dataSpec.uri.lastPathSegment ?: throw IOException("no set in ${dataSpec.uri}")
        setId = id
        position = dataSpec.position
        val total = core.totalSize(id)
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) total - position else dataSpec.length
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val want = minOf(length.toLong(), remaining).toInt()
        // Blocking is correct here: ExoPlayer calls read() on its loader
        // thread and expects it to block until bytes or end of input.
        val bytes = runBlocking { core.read(setId!!, position, want) }
        if (bytes.isEmpty()) return C.RESULT_END_OF_INPUT
        bytes.copyInto(buffer, offset)
        position += bytes.size
        remaining -= bytes.size
        bytesTransferred(bytes.size)
        return bytes.size
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (setId != null) { setId = null; transferEnded() }
    }
}
```

- [ ] **Step 5:** Run the test. Expected: PASS, four tests.
- [ ] **Step 6:** Commit — `feat(android): read a set's bytes into the player`.

---

### Task 2: The cache and the player factory

**Files:** Create `android/core/playback/src/main/kotlin/{CacheProvider.kt,PlayerFactory.kt}` · Test `android/core/playback/src/test/kotlin/PlayerFactoryTest.kt`

**Interfaces — Produces:** `fun buildPlayer(context: Context, core: CoreClient): ExoPlayer` and `fun cacheDataSourceFactory(context: Context, core: CoreClient): DataSource.Factory`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun theCacheWrapsTheMlibSource() {
    val factory = cacheDataSourceFactory(context, FakeCore())
    assertTrue(factory.createDataSource() is CacheDataSource)
}
```

- [ ] **Step 2:** Run `./gradlew :core:playback:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement. One process-wide `SimpleCache` — two instances over one directory throw — in `context.cacheDir/mlib`, with a `LeastRecentlyUsedCacheEvictor` at 2 GiB. Wrap `MlibDataSourceFactory` in `CacheDataSource.Factory`. `buildPlayer` uses `DefaultMediaSourceFactory` with that factory, leaving `DefaultExtractorsFactory` to sniff the container.
- [ ] **Step 4:** Run the test. Expected: PASS.
- [ ] **Step 5:** Commit — `feat(android): cache played bytes on disk`.

---

### Task 3: The player ViewModel

**Files:** Create `android/feature/player/src/main/kotlin/PlayerViewModel.kt` · Test `android/feature/player/src/test/kotlin/PlayerViewModelTest.kt`

**Interfaces — Produces:** `PlayerUiState` = `Preparing`, `Playing(positionMs, durationMs)`, `Paused(positionMs, durationMs)`, `Failed(message)`. Both surfaces render it.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun preparingIsTheFirstStateForASet() = runTest {
    val vm = PlayerViewModel(FakePlayerHandle())
    vm.open("s1")
    assertEquals(PlayerUiState.Preparing, vm.state.value)
}

@Test
fun aPlayerErrorSurfacesAsFailed() = runTest {
    val handle = FakePlayerHandle()
    val vm = PlayerViewModel(handle)
    vm.open("s1")
    handle.emitError("decoder init failed")
    assertTrue(vm.state.value is PlayerUiState.Failed)
}
```

- [ ] **Step 2:** Run the test. Expected: FAIL.
- [ ] **Step 3:** Implement behind a `PlayerHandle` interface so the ViewModel is JVM-testable without an `ExoPlayer`.
- [ ] **Step 4:** Run the test. Expected: PASS, both.
- [ ] **Step 5:** Commit — `feat(android): track what the player is doing`.

---

### Task 4: The player screens

**Files:** Create `android/ui-mobile/src/main/kotlin/PlayerScreen.kt`, `android/ui-tv/src/main/kotlin/TvPlayerScreen.kt` · Modify `MobileApp.kt`, `TvApp.kt`

- [ ] **Step 1:** `PlayerScreen` hosts a `PlayerSurface` from `media3-ui-compose` with touch controls, and keeps the screen awake while playing.
- [ ] **Step 2:** `TvPlayerScreen` maps the remote: centre toggles play/pause, left and right seek, back exits. Controls fade and are overscan-safe.
- [ ] **Step 3:** Route to both from the catalog's `onOpen`.
- [ ] **Step 4: Play a real film on a phone.** Confirm it starts, and note the seconds to first frame.
- [x] **Step 5: Seek across a part boundary.** Pick a set with more than one part, read the first part's `byte_length` from the index, seek to just before and just after it, and confirm playback continues without stalling or artefacts. This is the case that breaks if offset accounting is wrong. Phone only; tablet and television wait for phase 4. Observed: "Blade: Trinity" (`01M2N2A4QM5K93WDD7R3KJEZRK`), 2 parts, part 0 ending at byte 3,758,096,384 of 7,011,563,463. Even bitrate puts that at 3935 s, but the file's own byte-to-time mapping puts it near 3785 s (1:03:05) — two minutes earlier, which is why the estimate is only ever a starting point. Against a cleared `cache/mlib`: playback ran 1:00:18 to 1:04:02 untouched and at real-time speed while the reads crossed 3,758,096,384 into part 1, with no stall, no artefact and no drop to the catalogue; a seek to 1:06:06 landed on part-1 bytes never read before (3,954,333,612) and started; skipping back ten seconds at a time to 1:02:15 crossed into part 0 and kept playing; a seek to 1:52:38 started. Offset accounting holds in both directions.
  - Noted while the gate ran, not a failure of it: every seek that cancels an in-flight load logs `E LoadTask: Unexpected exception loading stream / java.lang.InterruptedException` from `MlibDataSource.fetch`, thirteen times over the run. `fetch` wraps `CoreException` as `IOException` so the loader can retry, but `runBlocking` answers media3's cancelling interrupt with `InterruptedException`, which media3 reads as an unexpected loader failure rather than the clean cancel it is. Playback recovered every time and the error screen never appeared, so this is a log-level wrong answer on a path seeking now makes ordinary. Worth its own piece of work.
- [ ] **Step 6:** Repeat steps 4 and 5 on a tablet and on a television.
- [ ] **Step 7:** Confirm no conversion happened anywhere: no ffmpeg, no HLS, no transcode directory. If a title will not play, record the codec rather than reaching for a converter — that is a finding for phase 6, not a feature.
- [ ] **Step 8:** Commit — `feat(android): play a set on every surface`.

## Todo list

- [ ] `MlibDataSource` with offset, short-read and end-of-input behaviour tested
- [ ] `CacheDataSource` wrapping it, one `SimpleCache` per process
- [ ] Player ViewModel tested without an `ExoPlayer`
- [ ] A real film plays on phone, tablet and television
- [ ] A seek across a part boundary is clean on all three

## Success criteria

A real film from the channel plays on all three form factors and seeks
correctly across a part boundary, with no transcoding involved.

## Risk assessment

| Risk | Mitigation |
|---|---|
| `runBlocking` ends up on main and freezes the UI | Only `MlibDataSource.read` may block, and ExoPlayer calls it on its loader thread. Any other `runBlocking` in `:core:playback` fails review. |
| A short read desynchronises position | Tested explicitly in task 1, `aShortReadAdvancesOnlyByWhatArrived`. |
| Two `SimpleCache` instances over one directory | One process-wide instance in `CacheProvider`; the second throws at construction, so the bug cannot ship quietly. |
| `Vec<u8>` copying per read costs throughput | Measure at task 4 step 4 before changing the surface. Evidence first, per the skill's performance guidance. |
| A codec Media3 will not decode | Record it in phase 6. Do not introduce transcoding in this round. |

## Security considerations

The URI carries a set id and nothing else. No `chat_id`, `message_id` or
`doc_id` may appear in a URI, a log line or an error message.

## Next steps

[Phase 6](phase-06-byte-truth-gate-and-docs.md).
