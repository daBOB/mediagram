# Phase 6: Byte-truth gate and documentation

**Context:** [plan.md](plan.md) · [phase 5](phase-05-playback-media3.md) · [spec §7](../../docs/superpowers/specs/2026-09-19-android-foundation-design.md)

## Overview

- **Priority:** Required. A client that plays is not a client that is correct.
- **Status:** Blocked by phases 4 and 5.
- **Deliverable:** proof that the bytes Android delivers are the bytes the uploader stored, and a documentation set that matches reality.

## Key insights

`docs/system-architecture.md` §7 sets the standard a second client is held to:
its bytes are checked against ground truth, "rather than against it agreeing
with itself". The web player earned that check. This one has not yet.

The ground truth is `parts.sha256` in the index — recorded by the uploader
when it split the file, independent of any player.

Run it on a **small multi-part set** — a tutorial lesson or a short episode.
The point is crossing a part boundary, not moving terabytes.

## Related code files

- Create: `android/core/playback/src/androidTest/kotlin/ByteTruthTest.kt`, `scripts/export-part-digests.sh`
- Modify: `docs/system-architecture.md`, `docs/development-roadmap.md`, `docs/project-changelog.md`, `README.md`

---

### Task 1: Export the ground truth

**Files:** Create `scripts/export-part-digests.sh`

**Interfaces — Produces:** a JSON fixture `[{"i":0,"offset":0,"length":…,"sha256":"…"}, …]`.

- [ ] **Step 1:** Write the script:

```bash
#!/usr/bin/env bash
# Digests the uploader recorded for one set, as the Android gate's fixture.
set -euo pipefail
set_id="$1"
db="${MEDIAGRAM_DATA_DIR:-$HOME/.local/share/mediagram}/library.db"
sqlite3 -readonly -json "$db" \
  "SELECT i, byte_offset AS offset, byte_length AS length, sha256
     FROM parts WHERE set_id = '$set_id' ORDER BY i;"
```

Read-only, like every other non-uploader consumer of the index.

- [ ] **Step 2:** Run it against a small multi-part set. Expected: at least two rows.
- [ ] **Step 3:** Commit — `chore(android): export recorded part digests for the byte gate`.

---

### Task 2: The gate

**Files:** Create `android/core/playback/src/androidTest/kotlin/ByteTruthTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
/**
 * The bytes the player is handed must be the bytes the uploader stored.
 * Digests come from the index, not from this client, so the check cannot
 * pass by the client agreeing with itself.
 */
@Test
fun everyPartHashesToTheDigestTheUploaderRecorded() {
    val expected = loadFixture()           // pushed to the device before the run
    val source = MlibDataSource(realCore())
    source.open(DataSpec(setUri(SET_ID)))

    for (part in expected) {
        val digest = MessageDigest.getInstance("SHA-256")
        var read = 0L
        val buf = ByteArray(512 * 1024)
        while (read < part.length) {
            val want = minOf(buf.size.toLong(), part.length - read).toInt()
            val n = source.read(buf, 0, want)
            assertNotEquals(C.RESULT_END_OF_INPUT, n)
            digest.update(buf, 0, n)
            read += n
        }
        assertEquals("part ${part.i}", part.sha256, digest.digest().toHexString())
    }
    assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
}

/** The same bytes, fetched as separate ranges, must hash the same. */
@Test
fun aPartFetchedInTwoSeeksHashesTheSameAsOneRead() {
    val part = loadFixture().first()
    val whole = readRange(part.offset, part.length)
    val half = part.length / 2
    val split = readRange(part.offset, half) + readRange(part.offset + half, part.length - half)
    assertContentEquals(whole, split)
}
```

- [ ] **Step 2:** Push the fixture: `adb push digests.json /sdcard/Android/data/<applicationId>/files/`.
- [ ] **Step 3:** Run `./gradlew :core:playback:connectedDebugAndroidTest` against the **real** channel on a real device. Expected: PASS.
- [ ] **Step 4:** If a digest mismatches, stop. Do not adjust the test. The offset arithmetic is wrong and phase 5 task 1 is where it lives.
- [ ] **Step 5:** Commit — `test(android): hash played bytes against the recorded digests`.

---

### Task 3: Make the documentation true

**Files:** Modify `docs/system-architecture.md`, `docs/development-roadmap.md`, `docs/project-changelog.md`, `README.md`

- [ ] **Step 1:** `docs/system-architecture.md` §7 — there are now three clients, not two, and only two MTProto implementations, because Android shares the Rust one. Say so, and say that Android needs no transcoding because Media3 decodes what browsers refuse.
- [ ] **Step 2:** Same file, "Consumers of the index" — add the Android app, reading a package's `library.db`, writing nothing. It is the fifth consumer.
- [ ] **Step 3:** Same file, §8 — "A future Android TV app can read `library.db`…" is no longer future. Replace it with what was made, and **correct the UniFFI paragraph**: the `Episode::Range` reshape was not required, because `mediagram-core` exposes flat DTOs and `Episode` never crosses the boundary.
- [ ] **Step 4:** Same file, §2 — the module map gains `mediagram-core`, and `serve/` no longer lists the modules that moved.
- [ ] **Step 5:** `docs/development-roadmap.md` — "Later: Android TV app round" becomes a completed round with a phase table. Amend the "Known UniFFI friction" block to record that the blocker was sidestepped rather than resolved, so nobody fixes a problem that no longer exists.
- [ ] **Step 6:** `README.md` — a section on the Android app: what it is, that it needs no server, how it is compiled, and how a device is provisioned.
- [ ] **Step 7:** `docs/project-changelog.md` — the round, with the gate's results: the digests compared, the device, the seconds to first frame, and whether any title failed to decode.
- [ ] **Step 8:** Verify no stale claim survives:

```bash
grep -rn "future Android\|Android TV app is the planned\|two clients" docs/
```

Expected: no stale hits.

- [ ] **Step 9:** Commit — `docs: record the Android client and correct the UniFFI note`.

## Todo list

- [ ] Digest fixture exported read-only from the index
- [ ] Every part hashes to its recorded digest, on a real device
- [ ] A part split across two seeks matches one read
- [ ] `system-architecture.md` §2, §7, §8 and the consumers table updated
- [ ] Roadmap round closed and the UniFFI note corrected
- [ ] README and changelog updated
- [ ] No stale "future Android" claims remain

## Success criteria

On a real device against the real channel, every part of a multi-part set
hashes to the `parts.sha256` the uploader recorded, and a part fetched as two
ranges is byte-identical to the same part fetched as one. The documentation
describes what exists.

## Risk assessment

| Risk | Mitigation |
|---|---|
| A digest mismatch is "fixed" by loosening the test | Step 4 forbids it. A mismatch is a phase 5 bug. |
| The gate is run on a single-part set and proves nothing | Task 1 step 2 requires at least two rows before proceeding. |
| Docs updated from the plan rather than from what shipped | Every claim is checked against the code as merged; step 8 greps for survivors. |
| The roadmap keeps the stale UniFFI blocker | Step 5 names it explicitly. |

## Security considerations

The fixture holds set ids and digests — no channel id, no message ids. Delete
it from the device afterwards. The changelog records results, never
credentials.

## Next steps

Round complete. Candidates for the next one, from spec §10: offline
downloads, where watch state lives for a client with no server, search, and
QR pairing to replace the typed credentials.
