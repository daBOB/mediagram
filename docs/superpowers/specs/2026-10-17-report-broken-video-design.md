# Report a broken video — design

2026-10-17. Status: approved in conversation, awaiting spec review.

## What it is for

A viewer who hits a video that won't play, or plays wrong, can tell the person
who runs the library from the player itself, in a few taps, on the web, the
phone, the tablet and the TV. The report reaches that person as a Telegram
notification on their phone and says enough to find and fix the title
(re-upload, `mediagram verify --full`).

## Decisions (the user's, 2026-10-17)

- **Form:** a reason plus an optional note. Reasons: *Won't play*, *Picture or
  sound wrong*, *Subtitles wrong*, *Wrong title or poster*.
- **Destination:** a reminder in the account's **Saved Messages**. Every screen
  is signed in to the same Telegram account (the uploader approves the
  player's QR login), and Telegram never notifies an account of its own
  messages, so a plain channel post would arrive silently. A message
  *scheduled* into Saved Messages is a Telegram reminder and does notify:
  verified 2026-10-17 by scheduling one 60 s ahead from the uploader's
  session (grammers `InputMessage::schedule_date`); the phone notified.
- **Technical detail:** attached automatically, never shown to the viewer.
- **Who:** everyone, kids profiles included.

## Not built

An in-app list of reports, a "fixed" state (the admin deletes the message),
retrying a report later when offline, a report entry on the title page (for a
series it would not know which episode), automatic reports without a press.

## Where the viewer finds it

1. **Player top bar**, beside Notes, on every surface: web `index.html`
   top bar, phone `PlayerTopBar.kt`, TV `TvPlayerTopBar.kt`. A plain button
   labelled *Report*.
2. **Failure screens**, beside Retry: phone `PlayerFailure.kt`, TV
   `TvPlayerFailure.kt`. Opens the sheet with *Won't play* chosen. The web has
   no separate failure screen: a conversion that stops writes into the
   player's note line, and the top-bar button stays reachable.

The sheet: the four reasons (one chosen; *Won't play* by default only from a
failure screen, otherwise none until picked), an optional note (on TV the
keyboard opens only if the viewer moves onto the field and presses OK),
*Send* and *Cancel*. Note capped at 200 characters.

After a send the top-bar button reads *Reported* and is inactive until another
title opens or the player closes, so one sitting cannot send a stream of
notifications. If
Telegram cannot be reached the sheet says *Couldn't send. Try again later.*
and stays open; nothing is queued.

## The message

Plain text, scheduled 60 s ahead into Saved Messages, not silent:

```
#mlib-report
Won't play: Hot Shots!
"stops right after the intro"
TV kids · TV · Android 0.126.0 · at 0:12:34
Played: direct, from cache · Error: ERROR_CODE_DECODER_INIT_FAILED
Check: mediagram verify --full <set id>
```

- Line 2: reason, then the player's title line (show · episode · title for an
  episode; the title alone for a film, no year) — the same `titleLine` both players show; the set id on the last line is what identifies the title.
- Line 3: the note in quotes; the line is absent when there is no note.
- Line 4: profile name · screen label (the label each surface already gives
  itself in watch together; Android's is *TV*, *Tablet* or *Phone*) plus the
  device model on Android · surface and app version · position as `h:mm:ss`.
- Line 5: how it played (*direct*, *converted*, *copied*; *, from cache*
  when the bytes were held locally) · the last error, absent when none.
- Line 6: the set id in the command that checks it.

The text is built by one rule per surface, held to shared fixtures in
`web/test/fixtures/report/` (input facts → exact text), checked by the web
and Kotlin tests, as the daily limit and watch together are. The web rule is
the reference.

## How it is sent

- **Web:** the browser posts the facts (set id, reason, note, position,
  play mode, held, last error, profile id) to `POST /api/report`. The server
  validates them (known set, known reason, note ≤ 200, numbers finite),
  builds the text with the shared rule (plain JS under `web/public/lib/`,
  imported by the server as watch together's rules are), and sends it to
  `"me"` with a schedule date 60 s ahead, as plain text (no Markdown parse).
  Answers 204, 400 for bad facts, 503 when Telegram is unreachable.
- **Android:** the Kotlin port builds the text; the Rust core gains
  `report_send(handle, text)`, which resolves the account itself
  (`get_me().to_ref()`, as the verification did) and sends
  `InputMessage::new().text(text).schedule_date(now + 60 s)`.

No sync, no schema change, no new stored data.

## Collecting the technical detail

- **Web:** direct play has no `error` listener today, so a broken file only
  ever shows as a stall. Add one on the `<video>` that records
  `MediaError.code` and message. The conversion and HLS fatal errors the
  player already words into the note are recorded too. Play mode comes from
  `playbackMeta` (`lib/playback/playback-report.js`), held from `askHeld`.
- **Android:** `PlayerHandleListener.onPlayerError` keeps the
  `PlaybackException.errorCodeName` (today only "Playback failed" reaches
  the UI). Position from the handle, held from `PlayerViewModel.held`.

The last error is per opened title and cleared when another title opens.

## Testing

- Shared fixtures for the text: TS and Kotlin tests.
- Web route test: validation, the 503 path, the text handed to the sender.
- No Rust unit test: grammers keeps `InputMessage`'s fields crate-private, so the
  schedule and peer cannot be inspected; verified by hand and by the device walk.
- Web sheet in the preview harness (no real send: the harness has no
  Telegram).
- Device walk on the tablet and the TV box — sends real reports to Saved
  Messages, so only with the user's go-ahead.

Version: 0.126.0 (a feature).
