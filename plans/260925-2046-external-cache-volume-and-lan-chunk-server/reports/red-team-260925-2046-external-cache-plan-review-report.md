# Red-team review: external cache volume + LAN chunk server plan

Date: 2026-09-25. Scope: plan.md and phase-00..04, checked against the live code.
Claims below were checked by grep or read, not taken from the plan text.

## Verified OK (no action)
- The `ACCESS_LOCAL_NETWORK` and `CHANGE_WIFI_MULTICAST_STATE` constants exist in `~/android-sdk/platforms/android-37.0/android.jar`. `targetSdk = "37"` (`android/gradle/libs.versions.toml:65`).
- The local cache key does not change. CacheDataSource keys on `dataSpec.key ?: uri`, and `setUri` is unchanged (`MlibDataSource.kt:25`). The LAN tier sits below the key.
- `X-Set-Total` has a reliable source: `open()` already calls `core.totalSize` (`MlibDataSource.kt:65`, `DefaultCoreClient.kt:62`). Pass that value through to `chunk()`.
- Set ids are fresh ULIDs per upload (`crates/mediagram/src/upload/prepare_set.rs:77`, `record_document.rs:59`). So chunk contents are immutable per id, and ids cannot be guessed. An open GET exposes no more than the web player's unauthenticated `/api/sets/{id}/stream` (`web/src/routes.ts:21`). The user's "reads open" decision stands.
- The file sizes the plan quotes are correct: `SettingsScreen.kt` 200, `MlibDataSource.kt` 185.

## Findings (ranked)

### 1. High: Phase 0's error flag also silences the preloader, which then downloads whole episodes into nothing and reports them held
- Evidence: `CacheDataSourceWriter.kt:41` builds its CacheWriter from the same `cacheDataSourceFactory` that phase 0 changes. `SeriesPreloader.kt:168-169` emits `heldEvents` as soon as `write` returns.
- Scenario: the disk is full or the card is gone. With `FLAG_IGNORE_CACHE_ON_ERROR`, or with `ForgivingCacheDataSink`, `CacheWriter.cache()` still reads the whole set from upstream: GBs over Telegram, and after phase 4, PUTs to the LAN. It writes nothing locally, returns normally, and the episode gets a "held" badge that is false.
- Plan edit: give `cacheDataSourceFactory` a parameter such as `forgiving: Boolean`, and set it for the player only. The preload writer keeps failing loudly. Add a phase-0 test: a preload over a throwing cache ends in `SeriesPreloader`'s catch branch and emits no held event.

### 2. High: Aligned fetches re-download the same 1 MiB for every cache hole and every re-open
- Evidence: `open()` resets `held` (`MlibDataSource.kt:78`). CacheDataSource opens upstream once per hole, with a length bounded by the next cached span. Today's `fetch(min(READ_AHEAD, remaining))` (`:106`) sizes each request to the hole. Phase 2 says the change is "transparent" (phase-02:84-86), which holds for bytes but not for cost.
- Scenario: a partly cached film has several small holes in one chunk, left by seek-back or by evicted spans. Each hole's open fetches the whole chunk again, from Telegram or the LAN. The "at most once per sequential read" test in step 2 cannot catch this, because it covers only one open. Step 2 is also not red on today's code for a read from position 0: those offsets (0, 1M, 2M…) are already aligned. Only the unaligned-start case is red.
- Plan edit: keep the last chunk across `open()` on the same instance, keyed by `(setId, index)`, and drop it when `setId` changes. Add a test: two bounded opens inside one chunk make exactly one `core.read`. Make step 2's red case explicitly the unaligned start.

### 3. High: "Stale dir deleted only after the open succeeds" cannot detect a failed open
- Evidence: phase-01:113-114, 168-169. The media3 `SimpleCache` constructor does not throw when initialisation fails. It stores the error, and `checkInitialization()` or the first `startReadWrite` rethrows it.
- Scenario: the card is present but not writable (read-only mount, corrupt FS). The constructor "succeeds", and the plan then deletes the internal `mlib`, the only good cache. After that every read errors and phase 0 bypasses the cache, so playback continues with no cache at all.
- Plan edit: call `cache.checkInitialization()` right after construction. If it throws, release that cache and open internal with `fellBack=true`. The step-5 "open that throws" test drives this path.

### 4. High: Budget clamping breaks the Settings selection and invites the wrong API
- Evidence: the evictor is built with its budget before the cache opens (`CacheProvider.kt:326`), but `heldHere` is only known after it opens (`cacheSpace`). `CacheProvider.setBudget` persists the value (`:308`). The block marks the selected row with `bytes == current.budgetBytes` (`CacheBudgetBlock.kt:55`).
- Scenario: the cap is `free + held − 1 GiB`, for example 13.7 GiB. The evictor gets 13.7 GiB, which is not on the ladder, so no radio button is selected. If the implementer clamps through `CacheProvider.setBudget`, prefs are overwritten, which breaks "restored if space returns".
- Plan edit: (a) clamp to the largest ladder step ≤ cap, not to the cap itself; (b) state the order: build with the stored value, open, compute the cap, then `evictor.setBudget(clamped, cache)` directly (never `CacheProvider.setBudget`); (c) test that one radio is selected after a clamp.

### 5. Medium: A volume-sized internal cache meets Android's cache quota (needs a user decision, not a silent change)
- Evidence: phase-01:96-97; the Redmi check at step 8 expects "cap above 8 GiB" on internal storage. Files under `context.cacheDir` are cleared by the system under storage pressure, apps over `StorageManager.getCacheQuotaBytes()` first.
- Scenario: a 40 GiB internal cache is the first thing the OS wipes, with span files removed under an open `SimpleCache`. Phase 0 keeps playback alive, but the chosen budget means nothing and the cache silently empties.
- Plan edit: surface this to the user; do not apply it. The user confirmed "cap follows volume" (brainstorm, 2026-09-25). Options: bound the internal cap by `getCacheQuotaBytes`, keep 8 GiB on internal only, or accept it and note it.

### 6. Medium: Stale-dir deletion leaks index tables and blocks first play
- Evidence: the cache uses a shared `StandaloneDatabaseProvider(context)` (`CacheProvider.kt:331`). `get()` is on the player-build path (`PlaybackModule.provideExoPlayerDeferred`).
- Scenario: a recursive delete leaves that cache's `ExoPlayerCacheIndex<uid>` and `ExoPlayerCacheFileMetadata<uid>` tables behind, one more pair on each switch. Deleting GBs of span files on a slow SD card inside `get()` also delays the first frame.
- Plan edit: use `SimpleCache.delete(dir, databaseProvider)`, and run it on a background coroutine after `get()` returns. The new dir is already locked, so there is no race with the preloader or `CacheDataSourceWriter`: both go through the same instance.

### 7. Medium: The server's first-write-wins and set total are racy as specified
- Evidence: phase-03:53 says "temp in root/.tmp then rename" and "first write wins". `std::fs::rename` overwrites, and the temp name is not specified.
- Scenario: a phone preloading and a TV playing the same episode is exactly the shared case. They PUT the same `(id, n)` at once. With a key-derived temp name the two bodies interleave into one file of the right length that renames in as garbage. Two first PUTs can also both see "no total" and both write it.
- Plan edit: unique temp names (seq or random); an in-process per-key mutex, or `renameat2(RENAME_NOREPLACE)`/`hard_link` to publish; `total` via `create_new` (O_EXCL). Tests: concurrent same-key PUT and concurrent first total. Also treat a missing chunk file on GET as a 404 and drop its index entry. That makes a manual `rm` of a set a valid remedy while the server runs.

### 8. Medium: Phases 3 and 4 disagree on what happens to a bad chunk
- Evidence: phase-03:106-108 says "phase 4's fallback retries from Telegram". Phase 4 falls back only on 404, IOException or a wrong length (phase-04:157-160), and accepts that a poisoned chunk errors playback (phase-04:247-250).
- Scenario: a torn write or bit rot on the box, not only a malicious writer, gives a right-length bad chunk. Every device fails at the same point forever, and the bad bytes are also written into each local SimpleCache.
- Plan edit: make the two phases agree. Cheapest guard against non-malicious corruption: the server records a sha256 at PUT, checks it on GET, and returns 404 and deletes on mismatch. Document the manual remedy (stop, `rm -r root/<id>`, start) until DELETE exists.

### 9. Medium: A stale server address on another network gets the token sent to it in cleartext
- Evidence: phase-04:164-166 says `StateFlow<LanServer?>` and a manual override. Nothing binds the server to the network it was found on. The token goes in a cleartext `Authorization` header (phase-04:161-162), and cleartext is allowed app-wide by `base-config` (phase-04:138-141).
- Scenario: the phone moves to another unmetered Wi-Fi. The manual override `192.168.1.10:7788` still applies, so whatever host answers there receives the Bearer token. Where nothing answers, loader threads take a 1 s connect stall every 60 s.
- Plan edit: key `LanServer` to the `Network` it was verified on, and clear it on `onLost` or network change. Add a random `server_id` to `/v1/status` and to the mDNS TXT record, pinned when pairing. Send `Authorization` only after `/v1/status` on the current network returns that id.

### 10. Medium: LAN timeouts on the loader thread are the wrong measure of "down"
- Evidence: connect 1 s, read 5 s, and the down flag is set only on IOException (phase-04:146-147, 160). All of this runs inside `runBlocking` in `fetch` (`MlibDataSource.kt:126`).
- Scenario: a slow but alive server (HDD spin-up, weak Wi-Fi, busy with PUTs) at about 3 s per MiB is never marked down, and is slower than Telegram's ~0.7 s per MiB (brainstorm: ~1.4 MB/s), so playback rebuffers. Blocking `HttpURLConnection` inside `runBlocking` also ignores ExoPlayer's cancel.
- Plan edit: set a per-chunk deadline that counts as a miss, for example 1.5 s total; mark the server down when a GET is slower than the Telegram baseline. Keep the down flag in an `AtomicLong` shared by the player and the preload threads. Use `runInterruptible` around blocking I/O. Add tests for the slow-hit and cancel cases.

### 11. Medium: Wrong facts and names across the phases
- Phase 4 hosts `LanCacheBlock` in `CacheSection.kt`, which phase 1 creates, but it lists `dependencies: [2, 3]` (phase-04:6, plan.md:25). Add 1.
- No `EncryptedPreferences` exists. Encrypted stores are per-setting classes in `:core:data` `settings/` (`EncryptedPackageSettings` etc., security-crypto via `AndroidSecurityCryptoConventionPlugin`). Put the token store in `android/core/data/src/main/kotlin/settings/LanCacheSettings.kt`, not in `:core:playback` (phase-04:186, 190-193).
- "axum 0.8.9 and tokio are already workspace dependencies" (phase-03:20) is only half right. `axum` is crate-local (`crates/mediagram/Cargo.toml:15`) and absent from `[workspace.dependencies]` (`Cargo.toml:14-50`). Either lift it or pin the same version in the new crate.
- Phase 0 docs call the budget UI a "live 512 MiB slider" (phase-00:57). It is a radio list of 512M/1G/2G/4G/8G (`CacheBudgetViewModel.kt:29`, `CacheBudgetBlock.kt:50`). The brainstorm repeats this error.

### 12. Medium: Phase 0's write-failure test hits the wrong call
- Evidence: phase-00:47-49 uses "`startFile` throws" to simulate a full disk.
- Scenario: `startFile` runs when the sink opens, so the error surfaces at open like a read error. A real full disk fails in `FileOutputStream.write`, on flush in `close()`, or in `commitFile`. The test can pass while the real failure still kills playback.
- Plan edit: add a case where the wrapper's `commitFile` throws, and one where `startFile` returns a file under a directory made read-only mid-read. Re-open the **same** CacheDataSource instance: `seenCacheError` is per instance, which is what ExoPlayer's Loader retry relies on.

### 13. Low: Unauthenticated clients can make the server buffer PUT bodies
- An axum handler reads its body before a later extractor rejects the request. Put the bearer check before the `Bytes` extractor, or in middleware, and cap concurrent PUTs, so a client without the token cannot make the server hold 1 MiB each.
- Evicting a set's last chunk leaves `root/<id>/total` and the directory behind. That is harmless because ids are immutable, but the startup scan should remove empty set directories.

### 14. Low: The volume setting departs slightly from the design
- The brainstorm says "no separate CacheSettings". Phase 1 adds a `CacheVolumeSettings` interface over the same prefs file. That is acceptable, but step 4's "not clobbering the budget" test must write both keys through both classes against one real prefs file.

## Positive observations
- The fallback keeps the recorded choice, the location change waits for a restart, and the budget stays live. Together these respect the process-singleton constraint.
- Rejecting ids by regex before any filesystem call removes path traversal outright.
- The token lives in StateDirectory, apart from the cache, so clearing the cache does not unpair every device.

**Status:** DONE_WITH_CONCERNS
**Summary:** 14 findings. The top four (preloader silenced by phase 0's error flag, re-fetch on every re-open, a failed cache open that goes undetected, and budget clamping that breaks the Settings selection) should be fixed in the plan before implementation.
**Concerns:** Finding 5 touches a user-confirmed decision (the cap follows the volume) and needs the user's call. The research could not confirm whether NSD also needs `NEARBY_WIFI_DEVICES`; check it on the device in phase 4.
