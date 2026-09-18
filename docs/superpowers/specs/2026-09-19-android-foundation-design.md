# Android foundation: phone, tablet and television

**Date:** 2026-09-19
**Status:** design approved; implementation plan not yet written
**Round goal:** a walking skeleton — one real film plays on all three form
factors, from the channel, with no server involved.

`mlib-spec`'s own doc comment already calls itself "the contract shared by
the Linux uploader and the Android TV player". This is the round that makes
the second half of that sentence true.

## 1. Decisions

Five forks were settled before any design was drawn. They are recorded here
because later work should not silently reverse them.

| Decision | Chosen | Rejected alternative |
|---|---|---|
| Data path | **Standalone** — the app speaks MTProto itself | A thin HTTP client of the Bun player in `web/` |
| Engine | **Rust core**: grammers + `mlib-spec`, bound to Kotlin via UniFFI | TDLib through JNI; a pure-Kotlin MTProto client |
| Round scope | **Walking skeleton** — one film actually plays | Scaffolding against a fake catalog; catalog-only with playback stubbed |
| UI sharing | **Shared core, two UI surfaces, one APK** | One adaptive UI everywhere; two separate app modules |
| Provisioning | **Crude now** — in-app login, pasted package URL and key | QR login and `mediagram pair` in this round |

Two of those deserve their reasoning written down.

**Why standalone rather than a client of the existing player.** The web
player's transcode layer exists because *browsers* refuse Matroska, HEVC and
AC-3 (`docs/system-architecture.md` §7). Media3 decodes all three natively.
On Android the server's main reason to exist evaporates, and what is left —
a host that must be reachable — is a cost rather than a benefit.

**Why a Rust core rather than TDLib.** This repo already contains a working
MTProto client and a specified wire format. Binding them is one
implementation reused; TDLib would be a third, and `mlib-spec` would still
need binding or reimplementation, so the UniFFI work happens either way.

There is no credible maintained pure-Kotlin MTProto client. Kotlogram is
dead. That option was not real.

## 2. Where it lives

One repository. The Android app's data layer *is* a crate in this Cargo
workspace; splitting repositories would mean versioning `mlib-spec` across a
boundary for no gain.

```
crates/
  mlib-spec/         unchanged
  mediagram/         untouched
  mediagram-core/    NEW — the Android-facing library
android/             NEW — the Gradle build
```

## 3. The Rust core and the FFI seam

`mediagram-core` wraps grammers and `mlib-spec` behind one narrow UniFFI
surface. The load-bearing function is:

```
read(set_id: String, offset: u64, len: u32) -> Vec<u8>
```

That is exactly the shape ExoPlayer's `DataSource` wants, and exactly the
job `web/src/range.ts` already does: an offset becomes one or more part
reads, which become Telegram range downloads.

**The split.** Rust owns MTProto, the session, package decryption, catalog
queries, caption parsing and offset-to-part mapping. Kotlin owns everything
above that line.

**Why the line sits there.** Media3 ships `SimpleCache` and
`CacheDataSource`: disk caching, eviction and readahead, already written and
better tested than anything we would write. So `MlibDataSource` stays thin —
it calls `read` and nothing else — and is wrapped in `CacheDataSource`. The
whole of `web/src/cache/` gets no Android counterpart. This is the single
largest piece of work the design avoids.

`DataSource.read()` is blocking and is called on ExoPlayer's loader thread,
so the Kotlin side blocks on the core's async read. That is correct on the
loader thread and must never happen on main.

**The `Episode::Range` blocker does not apply.**
`docs/development-roadmap.md:140` records `caption::Episode::Range([u32; 2])`
(`crates/mlib-spec/src/caption.rs:25`) as something that must be reshaped
before bindings can be generated, because UniFFI rejects fixed-size arrays
in enum variant payloads. That is true of binding `mlib-spec` directly.

This design does not. UniFFI exports only what is annotated. If
`mediagram-core` exposes its own flat DTOs — `episodeFirst`, `episodeLast` —
and keeps `mlib-spec` an ordinary internal dependency, `Episode` never
crosses the boundary and the constraint never binds. Reshaping the enum
becomes optional cleanup rather than a prerequisite, which is worth having:
it keeps the existing Rust crates, and their test suite, off the critical
path of an Android round.

The roadmap entry is not wrong; it assumed a different binding strategy. It
should be amended when this lands.

## 4. Module graph

Presentation logic is shared. Composables are not.

```
android/
  app/                  single APK; picks its surface at launch
  core/
    rust/               UniFFI bindings; loads the .so
    model/              Kotlin types
    data/               repositories over core:rust
    playback/           MlibDataSource, CacheDataSource, player factory
    designsystem/       shared tokens — colour, type scale, spacing
  feature/
    catalog/            ViewModels + UiState only. No composables.
    player/             ViewModels + UiState only. No composables.
  ui-mobile/            touch Compose — Material 3 adaptive, WindowSizeClass
  ui-tv/                10-foot Compose — androidx.tv:tv-material, D-pad focus
```

`ui-mobile` and `ui-tv` both depend on `feature:*` and render the same
`UiState`. A catalog bug is fixed once; only pixels are written twice.

Surface selection is `UiModeManager.currentModeType ==
UI_MODE_TYPE_TELEVISION`, resolved once at launch.

Module naming and dependency direction follow the skill's
`references/modularization.md`: features never depend on each other, and the
direction is always feature → core.

## 5. Toolchain

Pins come from the skill's verified version catalog
(`assets/libs.versions.toml.template`):

| | |
|---|---|
| AGP | 9.3.1 |
| Kotlin / KSP | 2.3.21 / 2.3.10 |
| compileSdk / targetSdk | 37 / 37 |
| minSdk | 24 — covers the old Android TV boxes still in service |
| Compose BOM | 2026.06.01 |
| Media3 | 1.10.1 |
| Hilt | 2.60.1 |
| Navigation3 | 1.1.4 |

Convention plugins, `settings.gradle.kts` and the version catalog are copied
from the skill's `assets/` per its greenfield path, with
`includeBuild("build-logic")` wired in the root settings file.

**Television is not covered by the skill.** No reference file in it mentions
`androidx.tv`, leanback, or 10-foot layout at all. The TV surface is
therefore specified here and its versions are *not* skill-verified:
`androidx.tv:tv-material` is pinned at whatever is current when phase 4
starts rather than guessed at now. TV also needs manifest work the skill
never describes — a `LEANBACK_LAUNCHER` intent filter beside the normal
launcher one, `android.software.leanback` and `android.hardware.touchscreen`
both declared `required="false"`, and a TV banner drawable.

The catalog carries only `media3-exoplayer` and `media3-session`; the UI
artifacts needed for player controls are added to it in phase 2.

**Native library alignment.** The `.so` must be built 16 KB page-aligned or
it will not load at all on Android 15 and later.

## 6. The risk that can invalidate this design

**`grammers` has never been cross-compiled to Android, and there is a
specific, already-identified reason it may resist.**

`crates/mediagram/Cargo.toml:36` documents that `rusqlite` is deliberately
*not* built with `bundled`, because `grammers-session` statically links its
own sqlite3 and two copies collide at link time. That resolution depends on
Linux supplying a system `libsqlite3`. **The Android NDK exposes no such
library.** On Android `rusqlite` must therefore be `bundled` — which
recreates precisely the collision that comment exists to prevent. The core
cannot simply drop SQLite either: the published package's catalog *is* a
`library.db`.

Candidate resolutions, in the order they should be attempted:

1. Avoid `SqliteSession` on Android entirely, so grammers never links sqlite
   and only `rusqlite` does.
2. Make both consumers share a single `libsqlite3-sys`.
3. Read the package catalog without `rusqlite`.

Which of these works is unknown. Guessing would be dishonest, so it is
settled empirically before anything is built on top of it.

`glass_pumpkin` stays pinned at `=2.0.0-rc0` (`crates/mediagram/Cargo.toml:25`)
on this path too; it is a known constraint of the workspace, not a new one.

**Consequence for sequencing.** Phase 0 is a spike, not construction:
cross-compile `mediagram-core` for `aarch64-linux-android` with `cargo-ndk`,
then make one authenticated call and one byte-range read from a real device.
If that cannot be made to work, the standalone approach fails and the
fallback is TDLib. Everything from phase 2 onward is cheap to build and
worthless if phase 0 fails, so the Gradle work waits for the spike.

## 7. Definition of done

1. `./gradlew help`, then `:app:assembleDebug`, both green.
2. Installs and launches on a phone, a tablet, and an Android TV device or
   emulator.
3. In-app login completes: phone number, code, 2FA password.
4. The package URL and key are pasted into a settings screen; the catalog
   refreshes; real titles and real posters render on all three surfaces.
5. One real film from the channel plays, and seeks correctly **across a part
   boundary**.
6. A byte-truth gate: the bytes the core feeds ExoPlayer for a full set hash
   to the `parts.sha256` the uploader recorded.

Point 6 follows the discipline `docs/system-architecture.md` §7 already sets
for the web player — a second client is checked against ground truth, not
against itself agreeing with itself. As there, it is run by hand against the
live channel, because there is no channel in CI, and its result is recorded
in `docs/project-changelog.md`.

## 8. Testing

**Rust.** Offset-to-part mapping and package reading are pure and unit
tested directly. The live path gets an `#[ignore]`d test, matching the one
that already exists in the workspace.

**Kotlin.** The shared `feature:*` ViewModels are JVM-tested — that is the
reason presentation logic lives in modules with no composables in them.
`MlibDataSource` is tested against a fake core, so the Range contract and
part-boundary crossing are covered without Telegram.

**CI** builds both and runs both suites. Live gates stay manual, as they are
today.

## 9. Phases

| # | Scope |
|---|---|
| 0 | grammers-on-Android spike: cross-compile, authenticate, read one range |
| 1 | `mediagram-core` crate, UniFFI surface, cargo-ndk build wiring |
| 2 | Gradle skeleton: convention plugins, module graph, CI |
| 3 | Login and package catalog; the mobile surface |
| 4 | The TV surface |
| 5 | Playback: `MlibDataSource`, `CacheDataSource`, Media3, all three devices |
| 6 | The byte-truth gate, and this documentation set |

## 10. Deliberately out of scope

Named so they are not mistaken for oversights:

- **Offline downloads.** The obvious next round, and the one that most wants
  Media3's `DownloadManager`.
- **Watch state, profiles, collections.** The web player keeps these
  server-side (`web/src/state/`). A standalone app has no server, so where
  they live and whether they sync is an unanswered design question, not a
  deferred implementation.
- **Search.** `/api/search` has no standalone counterpart yet.
- **QR login and `mediagram pair`.** The provisioning decision above.
- **Adaptive bitrate.** The web player's `adapt-*.js` loop exists for a
  25 Mbit/s uplink to a browser; whether Android needs an equivalent is
  unknown until real playback is measured.
- **Play distribution.** The app is sideloaded. No signing, tracks, or
  `versionCode` policy is designed here.

## 11. Unresolved questions

1. **Which sqlite resolution works** (§6). Answered by phase 0, and the
   answer may change phase 1's shape.
2. **Where watch state lives** for a client with no server. Needs its own
   brainstorm before the round that implements it.
3. **Whether the pinned `library.db` can replace the package** as a catalog
   source, removing the URL-and-key provisioning step entirely. It carries
   no posters, which is why the package was chosen — but it needs no
   secrets, which on a television is worth something.
