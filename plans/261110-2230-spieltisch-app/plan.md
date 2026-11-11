# Spieltisch: the games as their own Android app

The games leave the media app for an app of their own, **Spieltisch**
(`com.mediagram.spieltisch`). It has its own version line and its own updates,
so a game change no longer rebuilds, re-releases and re-installs the media app.

User, 2026-11-10: "should we decouple mediagram-games from mediagram ? when
updating a game we dont need to update the whole app" → "lets do this".

## Decisions (user, 2026-11-10; do not reverse without asking)

1. Spieltisch owns the shelf, the TV page and Back. The media app's Games row only opens it.
2. Spieltisch has its own version line, starting at 0.175.0 (the media app's next is 0.174.1). The phone controller joins it. CLAUDE.md § Versioning says so (diff shown and approved).
3. Kids lock: Spieltisch asks the media app for the active profile's lock. If the media app can't answer, Spieltisch stays locked.
4. The app is called "Spieltisch".
5. It stays in this repo (shared protocol types, build-logic, check.sh).

## Design in one paragraph

A new crate `crates/spieltisch-core` (cdylib, uniffi) wraps `mediagram-game` and is
built into the new Android app `:spieltisch`. That app takes over `feature/games` and
`ui-tv/games`. The media app keeps the library and the profiles. It answers Spieltisch
through one ContentProvider, guarded by a signature permission: the night (kids lock and
the titles as JSON in `GameTitle`'s shape), the poster and still files, textless posters,
and Spieltisch's newest release and its APK. Spieltisch installs that APK itself, on the
self-update path the 2026-10-02 spike proved on the TV box. Spieltisch's release posts
carry their own tag (`#spieltisch-app`), which media apps ignore.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [Spieltisch app on its own](phase-01-spieltisch-app-on-its-own.md): crate, app module, shelf from the catalogue, version line | done |
| 2 | [The media app answers Spieltisch](phase-02-media-app-provider.md): night, titles, images, kids lock | done: `/night` from a fingerprinted snapshot, ~1 s from a cold media app |
| 3 | [Releases and updates per app](phase-03-releases-and-updates.md): own release tag, publish, Spieltisch updates itself | done, except the channel dry run: a real publish waits for the user's go |
| 4 | [The media app lets go](phase-04-media-app-lets-go.md): Games row opens Spieltisch, game code and assets leave | done: APK 64.3 -> 34.8 MB |
| 5 | [Box run](phase-05-box-run.md): both apps on the TV box, a night, an update | pending |

The order matters. Until phase 4, the media app keeps its own games, so main stays
shippable at every step. Phase 5 needs the box. Coordinate with the session that owns
`mediagram-games` first: its test night runs on the box.

## Rules for every phase

- `scripts/check.sh` passes in full, Gradle included, before a phase is done.
- Files under 200 lines; no plan references in code; never push.
- The media app's version bumps as CLAUDE.md says. Spieltisch's bumps on its own line from phase 1.
- The game crate's rules, host and client move as they are; only their entry point changes.
  This keeps clear of the games session's planned refactors (screen builders, the ask, board life).

## Key risks

- A cold media app process starting behind a provider call can slow the night's start. Measure it in phase 2 on an emulator and in phase 5 on the box.
- Silent install: Spieltisch only ever updates itself (proven path). The first install is by `adb` at device setup, like the media app's.
- Media apps already out there take the highest versionCode among `#mlib-app` posts, whatever their package. If a Spieltisch post were one of them, they could pick it, refuse to install it, and never see their own updates again. Spieltisch's posts therefore use their own tag, `#spieltisch-app`, which those apps never read.

## Unresolved

- `feat/fluechtig-ferry` (spieltisch-ff, from main) is a Flüchtig-only change, but it bumps the media manifests to 0.175.0 under main's old one-number rule. Whichever branch reaches main second moves that bump onto the Spieltisch line, at the next free number. No file overlap otherwise (it doesn't touch host.rs, server.rs, catalogue.rs or lib.rs).
