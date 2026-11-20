---
title: "Audiobooks are their own kind, play as plain MP3 everywhere, and sit outside the kids daily limit"
author: Andre
date: 2026-11-20
branch: feat/audiobooks
id: l-e93cf33d
scope: repo
status: active
src: agent
touches:
  - crates/mlib-spec/src/kind_spelling.rs
  - crates/mlib-spec/src/category_key.rs
  - web/public/lib/playable.js
  - web/public/lib/daily-limit-gate.js
  - android/feature/player/src/main/kotlin/DailyLimitGate.kt
---

## Decided
- 2026-11-20, the user: a new Audiobooks section from MP3 files, using each file's embedded cover and its genre tag; "everything should handle mp3 native" (no conversion on any surface); screen-locked listening on phone/tablet; Kinderbücher books visible to kids without hand marks; listening does not count against a kid's daily limit and a book opens past it; iPhone listeners use the web player; update every app.
- A new set kind `book`, built like a standalone documentary: title only, artwork and category keyed by `title-{slug(title)}`, category department `audiobooks`. Sharing the documentary kind was ruled out (books would sit in Documentaries and share its switch and kids rule).
- One card per file; no author, narrator, series grouping, speed or sleep timer.
- Books add to a title's own time but never to the day total, because the day table the limit reads has no set or kind to subtract by. Day hours and streaks therefore leave listening out.

## Tried and ruled out
- A separate `<audio>` element for books on the web: the player fetches its one `<video>` once and hands it to 13 modules. Kept the one element plus lock-screen metadata; switch only if a real iPhone pauses a locked book.

## Watch out
- An MP3's cover arrives from ffprobe as an `mjpeg` video stream with `attached_pic`; without skipping it a book reads as 640x640 video.
- The source MP3s carry no Xing/Info header; seeking and duration rest on constant bitrate (ExoPlayer falls back to its CBR seeker).
- Android's section key is `Department.name.lowercase()`: the enum must be `AUDIOBOOKS`.
- Casting or watch-together waited for a first rendered frame an MP3 never sends.

In the code: design `docs/superpowers/specs/2026-11-20-audiobooks-design.md`, plan `plans/261120-2227-audiobooks-section/plan.md`; source folder `/media/DBI/HiDrive/HB` (118 files, 16 GB, 13 genres).
