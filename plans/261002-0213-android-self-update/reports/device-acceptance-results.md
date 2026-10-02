# Device acceptance — Android self-update (2026-10-02, evening)

## Devices

| Device | Android | Before | After |
|---|---|---|---|
| TV box `192.168.0.35:5555` (Google TV) | 14 | benchmark 0.92.0 (versionCode 18) | release 0.95.2 (95002), updated itself twice |
| Tablet `caad49da` (Redmi Pad Pro, HyperOS) | 16 | debug 0.92.2 (18) | unchanged — debug builds and tablets never self-update |

## Switch-over (TV box)

- `adb install -r` of the release 0.95.0 APK over the benchmark build: success, data kept.
- `appops set … REQUEST_INSTALL_PACKAGES allow`, `cmd package compile -m speed -f`.
- Launch: signed in, profile "TV test", catalog loads (912 films, 79 series). Settings › System shows the Updates row.
- Release APK: release-key cert `5840181d…f576`, ABIs `arm64-v8a` + `armeabi-v7a` only, 32.3 MB.

## Publishes observed

| Release | Channel msg | Published | Ready on TV | Installed | Publish → installed |
|---|---|---|---|---|---|
| 0.95.1 (95001) | 16548 | ~22:08:45 | by 22:10:40 (check on fresh launch 22:08:50) | 22:10:55, 4 s after Home | ~2 min (incl. the manual relaunch to bypass the hourly throttle) |
| 0.95.2 (95002) | 16556 | 22:14:46 | 22:17:21 → seen 22:22:06 | 22:22:11, 1 s after Home | gated by the playback check below |

After each install: launcher in front, no prompt or dialog of any kind, relaunch signed in with the catalog.

## Playback check (0.95.2)

1. Fresh launch 22:14:56 → the launch check finds 0.95.2 and starts downloading.
2. "Watch now" pressed as soon as the catalog drew; playing at 22:15:20.
3. Home while playing (playback continues in the background) → **no install** for 45 s, still 0.95.1, playback position advancing.
4. Back to the app, Back out of the player → Updates row: **"not checked yet"**, i.e. the download was dropped when playback began (`downloadWhileIdle` → `lastCheckAtMs = null`; `coreOrNull` builds the core whenever credentials exist, so the launch check did run).
5. Home → foreground with nothing playing → downloads → "0.95.2 ready · installs when you leave the app" → Home → installed 1 s later.

## Not verified on device

- Corrupted download refused: cannot be forced on a non-debuggable build (no `run-as`); covered by the core tests (plan's spec deviation note).
- Tablet's hidden Updates row: not opened on screen; the tablet stayed on 0.92.2 through two publishes and has no install permission.

## Side observations (not caused by the updater, as far as seen)

- TV box Settings › System › Cache: "Internal storage (the chosen volume could not be used)", 79 MB of an 8.0 GB budget. The USB stick `6BBF-D2D8` is mounted (460 GB, 5.5 GB used — likely the old cache), but `ls` of its `Android/data` from the shell gives "I/O error". No `CacheProvider` fallback warning in logcat, so the stick is probably missing from the app's volume list rather than failing to open. Unknown whether this predates the switch to the release build.
- The System index line read "0.95.0 · all current" while an update was ready; by design it reports catalogue/session health only, not updates.
- A test play of *Romeo + Julia* (~1 min) on the "TV test" profile is now on that profile's Continue shelf.

## Unresolved questions

- Was the TV cache already on internal storage before tonight? If not, the benchmark → release reinstall lost access to the stick's app folder.
