# TV box: smooth navigation, heroes that come back

The user reported on 2026-10-08: "the navigation is not smooth, hero elements are not coming back". Everything was measured on the real TV box (192.168.0.46, Android 14, 1080p UI on a 4K Hisense TV).

## Findings (box, 0.117.4 as self-updated)

- **Hero.** A department hero is item 0 and holds no focus stop. The TV's pivot scroll only moves a page to show what takes focus, so after Up or Back to the bar the page stayed scrolled down.
- **Stutter.** The self-update installs the app at dexopt `verify`, and the box's background dexopt had not compiled it two days later. Compiling with its own baseline profile (`speed-profile`), cold start, same walks: Movies median frame 26 → 18 ms, p99 77 → 44 ms, frames over 16.7 ms 75% → 28%; Home p99 133 → 69 ms.
- **Remaining cost is GPU.** Compiled, the UI thread is under 2 ms per frame. The GPU takes ~14 ms per frame navigating Movies and ~22 ms per frame during Home's cover crossfade, which therefore runs at about 30 fps.
- A Gradle install lands as `speed-profile` / `install-dm`, because AGP sends `baselineProfiles/0/app-release.dm` with the APK. The self-update writes only `base.apk`.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 01 | [Hero returns when the remote leaves for the bar](phase-01-hero-returns-on-leaving-for-the-bar.md) | done, verified on box (0.118.2), plus Anime entry |
| 02 | [Releases carry the baseline profile](phase-02-releases-carry-the-baseline-profile.md) | done: 0.119.0 published, box self-updated compiled |
| 03 | [Cheaper cover crossfade](phase-03-cheaper-cover-crossfade.md) | rejected on box numbers, reverted |

## Decisions (user, 2026-10-08)

- Stutter: ship the profile with each release, so it is not compiled by hand.
- Cover fade: prototype a cheaper fade and keep it only if the box numbers improve.

## Constraints

- The caption marker stays `#mlib-app v=1`. Installed apps accept only that exact marker, so a new marker would strand every TV. The profile is an optional JSON field, which v=1 readers ignore.
- The box runs a release build. Ask before `scripts/release-android.sh`, which publishes to every TV.

## Log

- 2026-10-08 15:2x: The "remote presses" that moved focus mid-walk were the other session (mediagram-f1) walking the same box over adb. Both sessions now hand the box over by message.
- 2026-10-08 16:3x: The Anime entry bug came from f1's box walk; fixed here, verified on the box.
- 2026-10-08 16:5x: review (code-reviewer) found a wrong-typed `profile` would drop the release on new readers, and the retry-without-profile relied on the in-memory pointer; both fixed before publishing, plus two minor scroll-to-top cases.
- 2026-10-08 17:00: 0.119.0 published with its profile (message 27408). The box self-updated at 17:02:04 and landed `speed-profile` / `install-dm`. Movies walk warm, two runs: p50 18 ms, p99 42–44 ms, 30% over 16.7 ms (0.117.4 as self-updated: 26 / 77 / 75%).
