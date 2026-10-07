# Open questions for the user

Collected during the run; each defaults to "leave as-is / defer" in the
desloppify plans, so nothing blocks on them. Recovered 2026-10-06 21:10 from the
first session's transcript after its scratchpad was wiped.

1. **rustfmt drift:** 125 Rust files on main are not rustfmt-clean (check.sh runs clippy, not `fmt --check`). Mass-format? It would conflict with the concurrent session.
2. **ktlint:** the Spotless/Detekt convention plugins are defined but never applied; ktlint is not installed. The 4,829 stale ktlint items all sit in the excluded generated bindings. Install and format Android, or leave it?
3. **Collection sync parity (Rust core vs web):** Rust truncates a fractional `updatedAt` (`as i64`, collections.rs:61/70), so it re-imports every round; the web stores the stamp as-is. On an equal-stamp import the web applies differing content and Rust skips it. Low severity: only malformed or tied peers.
4. **Upstream:** push the same-package-function Kotlin graph fix to PR #782 (daBOB:fix/kotlin-package-declaration-graph)?
5. **Version bump:** one bump when the branch merges (main moves concurrently), instead of one per commit?
6. **Web 200-line ratchet:** it is blamed for re-export residue, split migrations and transitive-only tests. Keep it, relax it, or fold files? (It is the user's standard; untouched.)
7. **Web Kids / Editor's-choice marks** are gated by the browser only, unlike kids-age, which needs the PIN. Require the PIN, or document this as intended?
8. **Rust UniFFI-surface changes** (~11 issues) need a coordinated Rust+Kotlin branch. Do that, or defer to the next Android release? Also: should the kids-profile rule move into core (who owns it, core or web)?
9. **Rust logging:** move the default log level from ERROR to WARN (this changes what every CLI run prints); unify the eprintln/println/tracing warning channels (streams change); add an Android log sink (new dependency).
10. **Rust `rust_future_proofing`** (`#[non_exhaustive]`): 96 wontfix and 80 open. Disable the detector with a recorded reason, or wontfix all at once?
11. **Rust:** replace the libsql session store? It changes the Telegram session files on every uploader machine; a bad migration means re-login everywhere.
12. **Android:** migrate off the deprecated security-crypto (may force a fresh login on every device), or keep it with an exit plan?
13. **Android:** require LAN-cache pairing before reads? Unpaired readers would stop working.
14. **Android:** keep or drop the material3-adaptive release-candidate pin?
15. **Android PlayerViewModel split** by responsibility: if a clean split can't stay ≤200 lines, accept a larger file?
16. **Library-switch parity:** after the fix (18e5dd18), a failed switch leaves the web player on the old library: it proves and serves the chosen channel before moving the connection, followed channel, listener and `telegram.json`. Android's core (`api/channel/mod.rs:78-82`) deliberately records the followed library first, so a failed install leaves the device following the new library, and a first profile waits on that library's sync round. Document this as deliberate, or change core/Android to match?
17. **Web code-standards test** skips every file whose name starts with "hls" (test/code-standards.test.ts:96), so hls-playback.js (224 lines) is never checked against its 220 ceiling. Close the gap?
18. **ffmpeg failure log:** the dead "see ffmpeg.log" pointer is dropped (4eb9ee6a; the 503 reason changed, docs updated). Still open: log ffmpeg's last lines before cleanup (a new feature)?
19. **Android FakeWatchState.kt:270** still deletes an empty synced preference. Follow once Rust refuses it (41ab9392): an executor task in Android execution.
20. **SECURITY (urgent):** web Settings sign-in responses sent the Telegram session string to the browser. It is fixed on the branch (e9169658), but main still leaks it. Merge soon or cherry-pick? Any sign-in done through web Settings from an untrusted browser or network: consider ending that Telegram session.

21. **Android profile rules (design call made at triage, review if you like):** Manage profiles keeps a hand-coded panel checked against the web's shared fixture file, as the web does, rather than deriving the panel from the rules. All 42 fixture cases pass today.
22. **Android FilmPreloader single-thread rule:** skipped. `limitedParallelism(1)` breaks three test setups under kotlinx-coroutines-test 1.11.0, and the rule is already documented at `FilmPreloader.kt:63` and `:124-127`.
23. **Android Home "Latest courses" goes from 8 to 6**, following the web (`home-shelves.js:66`). This is a visible change, to check on the tablet and the TV box.

24. **desloppify upstream (clippy coverage):** the Rust clippy detector runs `cargo clippy --workspace … -D warnings` without `--keep-going`, so cargo stops at the first crate that errors and only that crate's lints are recorded. Until 2026-10-06 that was always mlib-spec; with mlib-spec clean, the rescan reached mediagram-cache and recorded 277 old warnings (about 250 `unwrap`/`expect` in `*_tests.rs`). Open a PR adding `--keep-going` (or per-package runs)? The 277 go to the next triage, where the earlier precedent is wontfix for test-file restriction lints.

## Left open by executors (not decisions, for the final report)

- **Library switch:** a sign-out during a switch's proof makes `connection.withChannel` throw after the new catalog is already being served (a narrow window, unhandled). A failed switch's reason still lands in the status page's catalog reason while the old catalog is served. A new-index refresh can run alongside a switch attempt; the follow-up refresh corrects it.
- **Part names:** `course/upload.rs` progress lines (`c01l02`) and the `S{:02}E{:02}` formatting in `add_show/mod.rs` are outside the planned steps and unchanged.
- **Clippy:** `missing_panics_doc` still flags `mlib_spec::package::associated_data` (`package/mod.rs:108`).
- **Plan references in code comments** (forbidden by the comment rules), for the Rust docs pass and the Android comments cluster: `crates/mediagram-core/src/catalog_subtitles.rs:39` ("gone once phase 09"), `android/ui-tv/src/androidTest/kotlin/ui/tv/catalog/TvCatalogScreenTest.kt:108` ("phase 01's own choice"), `android/ui-mobile/src/main/kotlin/ui/settings/AppearanceSection.kt:40` ("phase 02 ported").
- **Admin token:** a token file that exists but cannot be read now stops startup instead of being overwritten (14da8e73).
- **Android on-device checks owed** (tablet `caad49da`, TV box `192.168.0.35:5555`; navigate only on the box). The native core must be rebuilt from the merged branch before any install:
  - toggle My List and Editor's choice on a film page and a show page;
  - switch LAN sharing off mid-playback (playback continues; the server stops receiving chunks);
  - Settings → Home cache server: save a valid and an invalid token (on the box, look only);
  - smoke-test Settings and Fetch;
  - Home "Latest courses" shows 6.
- **Android LAN save failures** stay on screen until the next save or reopen (a `saveFailed` flag); the plan's "clear on any snapshot" would have wiped them with the save's own re-read. The phone now asks for local-network access only after a token is accepted. `LanCacheViewModel.kt` is 203 lines.
- **Stale claims left in `docs/code-standards.md`** (outside the docs pass's steps):
  - the Security section names `telegram::client::session_path` and a `restrict_session_permissions` that does not exist (0700/0600 are applied in `paths.rs` and `commands/setup.rs`);
  - testing tier 3 describes `edge_cases_probe_*` suites, which do not exist;
  - the `too_many_arguments` example `index::parts::mark_done` no longer carries the allow.
- **Web twin:** `web/src/range.ts:9` names `crates/mediagram/src/serve/range.rs`; the Range maths is in `crates/mediagram-core/src/range.rs`.
- **Android System's Telegram row** now asks Telegram (a 3 s probe) instead of reading the stored login. On the tablet: open System online, then in airplane mode, and expect "connected", then "disconnected" with "needs attention" (on the box, online only). A live connection flag from the core, like the web's `connected`, would change the UniFFI surface (part of question 8).
- **Android real-core contract run owed:** run `RealCoreContractTest` and `EncryptedSettingsTest` (`connectedDebugAndroidTest`) on the tablet against a native core rebuilt from the merged branch; the new refuse-to-forget case needs it. `SetupViewModel` (224 lines) and `LanCacheViewModel` (207) are over 200.
- **Android device smoke after the playback-bindings move** (66f311a2): play a title, open Settings → LAN cache (read only), start a film preload; catalogue home, tabs and Back on phone and TV. The release baseline profiles still name `AllSetsIndexKt`, `indexById` and `TvCatalogRoot`; regenerate on the box at the next profile run. `CatalogScreen.kt` is 279 lines.
- **Android walk after clusters 10–12** (tablet + box, navigate only): play a title, leave and reopen it (the lock-screen title still updates; on the tablet, change and reopen subtitle/audio/speed/framing/subtitle size and expect each remembered); walk every department pill, Collections, My List and Continue from the rail; tap each Home "See all"; open a title and press Back (same tab returns); rotate the tablet (same tab); Latest courses shows 6 (compare with the web); open a person and a franchise from a title page and from search, then press Back (TV returns to the search entry). The TV now keeps the chosen tab across a refresh that adds a department.
- **Android walk after clusters 13–15** (box: arrows, OK on plates and Back only; never change the accent or any setting):
  - press OK on a plate in a lower row, then Back: focus returns to that row and plate, on Home and every department; in particular Movies with no Featured films and Documentaries with nothing underway;
  - Back from Latest, Genres, Stats, Settings and System lands on that rail row; Back from Search lands on the field, Back from the menu on ⋮;
  - walk Home's bands, a department page, search results, title-page pills, cast and similar rows, collections and profile tiles;
  - focus borders, tabs, pills, the seek bar and the preload bar draw in the box's current accent;
  - tablet: Home during playback opens PiP; expanding keeps playing; dismissing pauses and saves;
  - tablet: opening a person shows "Loading your library…" and then the page, never "Nobody by that number…";
  - both: a cast row scrolled away and back keeps found portraits; a film's Preload label still shows; each department tab keeps its scroll.
  `TvHome.kt` (368 lines) and `LibraryBrowseBranches.kt` (228) remain over 200.
- **Android catalog records:** steps 1, 2 and 4 of catalog-records-mirrored-in-core-model wait on question 8 (the UniFFI surface); three issues stay open. Web twin: `web/public/lib/playback/subtitle-choice.js` and `web/src/state/schema.ts` still cite plan/phase. `app/proguard-rules.pro` keeps Retrofit, Ktor and Paging rules the app no longer uses. The release build was checked with `:core:ffmpeg:verifyFfmpeg` skipped (no ffmpeg .so in the worktree).
- **Cache server:** after the PUT/eviction fix (c2f9ee7e), the cache server still has to reach the home box through the usual release.
