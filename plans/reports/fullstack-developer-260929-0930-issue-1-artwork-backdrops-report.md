# Issue #1 — Android backdrops from the artwork table

## Root cause

`crates/mediagram-core/src/api/store/editorial.rs:177` (pre-fix) called
`resolve_cached(..., &backdrop_key, artwork_keys, false)` — `materialize =
false` — while the poster and season-poster calls two lines above/below
passed `true`. `resolve_with` (`crates/mediagram-core/src/api/store/
resolve.rs:127`, pre-fix) used that flag to skip the `artwork` table
entirely on a disk miss: `if !materialize || !artwork_keys.contains(key) {
return None; }`. The web's `has()` (`web/src/catalog/routes.ts`) has no such
gate — `posters.has(key) || artwork.has(key)` — so a backdrop the table
alone carries shows on the web and not on Android. Confirmed against the
live index: `sqlite3 ~/.local/share/mediagram/library.db "select key from
artwork where key like 'title-%-bg'"` returns exactly the 12 rows the issue
names, none with a matching file under the uploader's local `posters/`.

## Change

- `resolve.rs`: dropped the `materialize: bool` parameter from `resolve_with`
  and `resolve_cached` — every caller (poster, backdrop, season poster,
  portrait in `credits.rs`) passed `true` once the backdrop call did too, so
  the parameter was dead weight, not a knob anyone still turned.
- `editorial.rs`: the backdrop's `resolve_cached` call now takes the same
  form as poster/season-poster's. Same connection, same pre-read
  `artwork_keys` set, same per-key memoization (`resolved`) — no new query
  shape, just the existing one applied to one more key per set.
- `credits.rs`: dropped the now-removed trailing argument at its one call
  site.
- Doc comments updated where they described the old disk-only rule as
  intentional: `resolve_artwork`'s own doc, `resolve_with`'s, and
  `SetSummary::backdrop_path`'s field doc (`dto/summary.rs`).

## Test

`editorial_tests.rs`'s existing
`a_backdrop_held_only_in_the_artwork_table_does_not_resolve` was asserting
the bug as a documented gap. Flipped in place to
`a_backdrop_held_only_in_the_artwork_table_materialises`, mirroring
`a_poster_missing_on_disk_materialises_from_the_artwork_table`: inserts a
set with a backdrop only in `artwork`, no file on disk, asserts
`backdrop_path` resolves and the bytes land on disk.

Verified fails-before/passes-after directly: extracted just this test's
diff, applied it on top of the pre-fix tree (`git apply` against `HEAD` with
none of the source changes), ran it — `left: None, right:
Some(".../tmdb-movie-550-bg.jpg")`, i.e. `assert_eq!` failed. Restored the
full fix (`git stash apply` + drop, verified by SHA before dropping) and
reran: passes. Full `editorial` suite: 24/24 pass with the fix in.

## Bindings

Changed. Rebuilt all 4 ABIs (`ANDROID_NDK_HOME=... scripts/
build-android-core.sh`), then ran the same `uniffi-bindgen generate` +
trailing-whitespace-strip steps `scripts/generate-android-bindings.sh` uses,
against the fresh `arm64-v8a` `.so`. Diff against the committed Kotlin file
is exactly one KDoc comment, on `SetSummary.backdropPath`, matching the
`dto/summary.rs` field doc I rewrote — uniffi-rs 0.32 carries Rust doc
comments into the generated KDoc. No field, method, or type signature
changed. Committed the regenerated `mediagram_core.kt` alongside the Rust
change.

## Checks

- `cargo metadata --locked --offline`: OK after bumping `Cargo.toml` and the
  5 workspace crates in `Cargo.lock` (mediagram, mediagram-cache,
  mediagram-core, mediagram-tmdb, mlib-spec) to 0.82.2, and `web/package.json`
  / `android/app/build.gradle.kts` `versionName` to match. `versionCode` (18)
  untouched.
- `scripts/check.sh`: full run, exit 0 — clippy clean (`-D warnings`), every
  `cargo test --all` suite green (mediagram-core's included, 417 in its lib
  target), Kotlin `testDebugUnitTest`/`:core:model:test`/`lint`/
  `compileDebugAndroidTestKotlin` all green (ANDROID_HOME was set; no device
  touched — Kotlin-only tasks, no Rust toolchain, no adb).
- `crates/mediagram/tests/code_standards.rs`'s 200-line check: all touched
  non-test files are well under (largest is `editorial.rs` at 182); the
  200+-line `editorial_tests.rs` is exempt (`_tests.rs`).

## The 12 titles to check on the tablet

Matched each `title-{slug}-bg` key against the index's actual `show`/`title`
text by reproducing `mlib_spec::slug::slug` (ASCII alphanumerics only,
everything else collapsed to one dash — a `–` or umlaut drops out, which is
why some slugs look truncated):

1. China – Wie eine Nation entstand
2. Die amerikanische Revolution – Geburtsstunde der USA
3. Die Geschichte Chinas
4. Die Inquisition
5. Frauen und Männer der Steinzeit – Gleicher als gedacht?
6. Geldhochschule
7. Kapitalismus made in USA – Reichtum als Kult
8. Mao – Chinas roter Kaiser
9. Pekinger Frühling
10. Venedig retten
11. Versailles – Palast des Sonnenkönigs
12. Wall Street Story

Any one of these should now show a backdrop image on the tablet where it
previously showed none (plain background/no backdrop), matching the web
player.

## Concerns

None outstanding. The `materialize` parameter removal widens the diff
slightly beyond the single `false`→(gone) the issue asked for, but every one
of its four call sites already passed `true` after the fix — keeping a
boolean nobody ever sets to `false` would just be a config value that never
changes.

**Status:** DONE
**Summary:** Backdrop resolution now materialises from the `artwork` table on a miss, same as poster/season poster; dead `materialize` flag removed; test flipped and verified fail-before/pass-after; bindings regenerated (KDoc-only diff) and committed; check.sh green at 0.82.2.
**Concerns/Blockers:** None.
