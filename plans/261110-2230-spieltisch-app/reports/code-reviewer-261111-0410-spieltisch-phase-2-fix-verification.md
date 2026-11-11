# Phase 2: checking the fixes for R1–R5 (third review)

This covers the uncommitted changes on top of `1cc843a9`, branch `feat/spieltisch-app`. I only reviewed the fixes and what they touch.

`rereview-check.log` (04:05) was written after the last fixed file (02:12) and ends with `all checks passed`. I ran no extra tests; I read the code and the tests.

## Verdicts

| # | Verdict | Evidence |
|---|---|---|
| R2 app version | **Fixed** | `GameNightModule.kt:28` reads `longVersionCode`. It is written into the header at `NightSnapshotStore.kt:60` and checked at `:50` (a different version reads as `null`, so the night is rebuilt). It is also hashed into the fingerprint (`NightInputs.kt:26`). Test: `aSnapshotFromAnotherVersionOfThisAppIsNotServed`. |
| R1 descriptions | **Fixed** | See "R1 in detail" below. Tests: `every_description_written_moves_the_digest` and `aFetchedDescriptionThatMovesATitleIntoAHiddenSectionIsNeverServedTheOldNight`. |
| R3 slow textless misses | **Fixed** | `media_images.rs:51-58`. When the 300 ms timeout fires (`poster_lookup.rs:20`), the `JoinHandle` is dropped, which detaches the task rather than cancelling it, so the miss is still recorded. Test: `a_textless_miss_slower_than_the_wait_is_still_remembered`. The optional "pending" marker was not added (see N3). |
| R4 sign-out holds the lock | **Fixed** | `GameNightExport.kt:132` lists the sets from the core already in hand, with the shared `toMediaSet` (identical to the code removed from `CatalogRepository`). `:89` puts the 30 s limit around fingerprint, read, build, re-check and write. Every core call in there is a uniffi `suspend` (`suspendCancellableCoroutine`), so the limit can cancel it, and `withLock` unlocks in a `finally`. Tests: `anAskThatNeverFinishesLetsTheNextOneInAfterTheLimit` and `aSignOutDuringTheAskNeitherHoldsItNorWaitsForTheNextSignIn`. |
| R5 `served` order | **Fixed** | `GameNightExport.kt:98` sets it inside `withLock`. It is `@Volatile` for the reads outside the lock (`:71`, `:77`, `:82`). |
| Catalog version unreadable | **Fixed** | `NightInputs.kt:46`: a version that cannot be read while `sets > 0` gives `null`, which answers locked. Test: `aCatalogWhoseVersionCannotBeReadAnswersLocked`. |

### R1 in detail

The digest is `details.rs:72-97`, read at `store.rs:153` and included in the marker at `NightInputs.kt:47`.

- **Order is deterministic.** `ORDER BY source, kind, id` is the upsert's `ON CONFLICT` key (`shows/mod.rs:47`), so no two rows tie.
- **It covers what the night reads.** Every column of `shows` is hashed. That is the only sidecar table `list_sets` reads (`genres.rs:18`, `facts.rs:50`).
- **No two stores hash alike by accident.** Each value is hashed with a type tag, and text and blobs carry their length, so different contents cannot run together into the same input.
- **The bindings match.** The new field comes last in both the Rust record and the generated Kotlin reader and writer, with default 0 (`dto.rs:81`).
- **It is cheap.** One indexed scan per `catalogFacts()`. The catalog refresh (`CatalogRepository.kt:131,133`) and the System screen also pay for it, though they don't read the field. That is milliseconds.
- **The special values hold up.** `u64::MAX` is distinct from 0. It differs from a real digest only by chance, at 2⁻⁶⁴, which is fine. If `DefaultHasher` changes in a new toolchain, the cost is one rebuild.

## Cancellation, the write, and error paths

- **A cancelled build cannot write.** The write (`:122`) comes after the last suspension point (`:116`) and contains none itself. If the timeout lands after the write, only the answer is thrown away (it answers locked); `kept` and the file still match their fingerprint.
- **The write is atomic.** Temp file in the same directory, then `renameTo` (`NightSnapshotStore.kt:57,62`). There is no fsync. After a power loss the reader finds either the old file, which the fingerprint check rejects, or a torn new one, which fails to parse and is rebuilt. Both fail closed.
- **Every failure answers locked with `[]`.** That covers `null` from `answer()`, an exception (`:92-97`), the timeout, and the provider's `runCatching` (`GameNightProvider.kt:48`).

## New findings

**N1 – Low-Medium: a build slower than 30 s now never completes, and the logs don't show it.**
- **Scenario:** on a TV box with a large catalog, the build takes 35 s.
  1. Each ask, the warm-up included, is cancelled at 30 s and answers locked with `[]`.
  2. Nothing is kept, so the next ask starts the build over.
  3. Spieltisch never gets films on that device.
  4. Each cancelled build keeps running in Rust (`spawn_blocking` in `Core::blocking`), so builds overlap.
  5. The only log line is `answered the night: kids_locked=true, 2 chars` (`:61`), the same line as "nobody chosen".
- **Before the fix:** such a build finished once and was kept.
- **Fix:** log when the timeout fires, for example by using `withTimeout` and catching `TimeoutCancellationException` before the generic `CancellationException` catch. Then measure the box's build time against 30 s in the box run that is still open.
- **Severity:** it fails closed, so it does not block the commit.

**N2 – Low: the limit cannot cut work that doesn't suspend.**
- `CoreProvider.coreOrNull` retries a pending close inside `NonCancellable` (`CoreProvider.kt:123`).
- It also builds the core inside `withContext`, which waits for its block even after cancellation.
- Either one still holds the export's lock for as long as it takes.
- The R4 scenario no longer goes through this path. Note it only.

**N3 – Low: a slow miss that was only a passing failure is now remembered for the whole night.**
- **Scenario:**
  1. The internet connection hangs mid-fetch.
  2. The media app's TMDB lookup gives up after 20 s (`game_posters.rs:35`) and writes no `.none` marker.
  3. Its `None` now lands in `misses`.
  4. That title shows its lettered poster for the rest of the night, while the media app's own host would retry.
- Before the fix, slow misses were never remembered. That is an acceptable trade-off; it is noted here so it is a decision, not an accident.
- Without a pending marker, requests for the same key that come in together still each go to the media app: the prefetch (`factory.rs:77`) plus the image route. There are only as many as the requests made during the first ask, and the fix does not make this worse.
- A spawned task only holds `Arc` clones. If it finishes after the host has stopped, it writes into a set nobody reads any more. That is safe.

**N4 – Informational: the "unreadable" value is narrower than its doc comment says.**
- An existing store that cannot be *opened* gives 0, the same as no store (`details.rs:73`), not `u64::MAX`.
- That is harmless: `list_sets` merges nothing from it in either case, so the night is the same.
- `details.db` uses a rollback journal and no `busy_timeout`. A `SQLITE_BUSY` during a write therefore gives one night built without the fetched genres. The media app's own library looks the same at that moment, and the next ask after the write rebuilds the night.
- Optional: set a `busy_timeout` on the read-only connection.

**Nit:** a `CancellationException` that does not come from the timeout is rethrown at `:91` and skips the `served` update. It still answers locked through the provider. Nothing can trigger it today, because the provider's `runBlocking` is never cancelled.

## Verdict

**Commit-ready.** R1–R5 and the unreadable-version case are fixed. N1 should get its log line before the box run, but it fails closed and is not a blocker.
