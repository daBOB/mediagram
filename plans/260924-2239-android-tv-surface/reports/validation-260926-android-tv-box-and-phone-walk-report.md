# Validation — TV box and phone walk (2026-09-26)

Branch `feat/android-tv-ui` at 83f20c4e, rebased on main 9b65c6a7. Version 0.59.0 (bump owed at merge).

## Automated
- `scripts/check.sh` with `ANDROID_HOME=/home/andre/android-sdk`: all four steps ran and passed
  (clippy, cargo test, bun test, gradle test + lint).

## Real TV box — 192.168.0.35:5555 (UHD Google TV STB, Android 14)
Build: `benchmark` (minified, not debuggable), `cmd package compile -m speed -f`. Signed in; no pm clear.

| Check | Result |
|---|---|
| USB stick (512 GB) as **portable** storage, `6BBF-D2D8` "android" | Listed in Settings → Where, 460 GB free, chosen |
| Adopted (internal-format) stick | Never listed — `getExternalCacheDirs` omits adopted volumes. Reformatted as removable |
| Cache opens on the stick | `…/6BBF-D2D8/Android/data/com.mediagram.android/cache/mlib` created |
| Cache fills the stick | 0 → 50 MB after 25 s of *Das ist das Ende* |
| DTS film with sound | Same title (h264 · dts · 3 parts): video on screen; app's AudioTrack started, 5.1 PCM 48 kHz (FFmpeg decode). Audible sound not confirmed by ear |
| Home cache server block | Status "Searching", switch, address/token rows render; token question opens with IME; Back returns focus to its row |
| Back from player/title | Lands on the Continue card that was opened; position saved (9:47 → 10:26) |
| Movies wall scroll, 30 rows | 644 frames, 6 missed deadline (0.93%); GPU p90 7 ms |
| Back on a wall | Focus to the masthead tab |

Profile: walked on `TV test`; switched back to `andre` afterwards. Only preference touched on `TV test`: the
Continue position of *Das ist das Ende*. Cache budget on the box is 256 GB (set by the user earlier).

## Phone — caad49da
Debug build installed over 0.55.0 debug, data kept. Launch → Movies → *12 Monkeys* → Back: all fine, posters and
backdrop shown (the phone has a TMDB key; the box has none, hence initials there).

## Open
- Emulator walk from a fresh `pm clear` (setup → sign-in → profile): needs a Telegram login code from the user.
- Sound by ear on the DTS title.
- Home cache server connected state: no `mediagram_cache` server was found on the LAN during the walk.
- Code review over the branch diff; version bump at merge.
