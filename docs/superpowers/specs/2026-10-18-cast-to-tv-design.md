# Cast to the TV — design

2026-10-18. Status: approved in conversation in five parts, awaiting spec review.

## What it is for

Choosing and steering what plays on the big screen is slow with a TV remote
and a D-pad. Casting lets a viewer do it from the phone, the tablet or the web
player instead: pick a title there, or move what is already playing there, and
the TV plays it while the small screen becomes the remote.

There are three routes, in this order of importance:

1. **The box.** The Google TV box plays the title itself in Mediagram: 4K,
   HDR and Dolby Vision, decoded by the box, with the tablet free. This is the
   main route and the larger part of this spec.
2. **Miracast / AnyView Cast.** For a TV without the box, the phone or tablet
   puts its video on the TV as a second screen. AnyView Cast is Hisense's name
   for its Miracast receiver, so the two are one route.
3. **AirPlay.** The web player in Safari (iPhone, iPad, Mac) sends the stream
   to an AirPlay TV, which Safari already knows how to do.

## Decisions (the user's, 2026-10-18; do not reverse silently)

| # | Question | Answer |
|---|---|---|
| 1 | Which moment | **Both**: pick on the small screen and play on the TV, and move something already playing on the small screen to the TV at the same second. |
| 2 | The small screen afterwards | **Becomes the remote**: play/pause, seek, ±10 s, next episode, subtitles and audio, Stop. It plays nothing itself. A bar brings you back to it while browsing. |
| 3 | When Mediagram is not open on the TV | **Only casts to an open app**, over watch together's channel message. No Google Cast or Cast Connect, no registration. The TV remote is needed anyway to switch the TV on: the box drops off the network while it sleeps. |
| 4 | Whose profile the TV plays under | **The caster's**: their progress, Continue and stats, and a kid's age and daily limits. The TV asks no PIN and returns to its own profile afterwards. |
| 5 | Who can cast | **Phone, tablet and web**, as the parity rule asks. |
| 6 | How many TVs | **One for now**: no picker. |
| 7 | A TV that is already playing | **Ask on the small screen first**: "The TV is playing *X*. Replace it?" |
| 8 | How | **Approach A**: a cast is a watch-together session with a remote in it. The session message moves to `#mlib-together v=2`. Rejected: a separate `#mlib-cast` message (repeats watch together in three languages), and a server on the box found over mDNS (a second transport the browser can't reach; the box's mDNS already misses the cache server). |
| 9 | AnyView Cast, AirPlay 2, Miracast | **Box first, then Miracast** as a second screen on Android, and **AirPlay only through Safari**. Android has no AirPlay, and Apple publishes no sender kit: an Android sender would mean re-implementing Apple's pairing from reverse-engineered notes, and commercial players already struggle with Hisense AirPlay. |

## 1. The message

The watch-together message (`docs/mlib-spec.md` §7b) moves to `v=2`. Section
8's rule holds: every reader accepts both `v=1` and `v=2`, and every writer
writes `v=2`. A `v=1` session decodes as a `v=2` one with no remote members and
`cast: null`. A screen still on an older release does not see `v=2` sessions at
all, which is why all three surfaces ship together.

    #mlib-together v=2
    {"session":"<uuid>","set":"<set id>","playing":false,"pos":312,"rate":1,"at":1791500000123,"by":"<screen id>","members":[{"screen":"<screen id>","label":"Tablet","remote":true}],"ended":false,"cast":{"to":"TV","from":"Tablet","profile":"<profile id>","state":"offer","tv":"","note":"","tracks":{"audio":[],"text":[]},"audio":"","text":""}}

What `v=2` adds:

| Field | Meaning |
|---|---|
| `members[].remote` | `true` for a screen that controls the session but plays nothing. A boolean on every member. |
| `cast` | `null` for plain watch together; otherwise the object below. |
| `cast.to` | The kind of screen asked to play: always `"TV"` in `v=2`. |
| `cast.from` | The caster's surface label (`Phone`, `Tablet`, `Browser`), for "Cast from Tablet". |
| `cast.profile` | The caster's profile id. The TV plays as this profile. Non-empty. |
| `cast.state` | `offer`, `busy`, `refused`, `replace` or `on`; see §2. |
| `cast.tv` | The screen id of the TV that took the cast; `""` until one has. |
| `cast.note` | With `busy`, the title the TV is playing; with `refused`, the reason, written for the viewer. `""` otherwise. |
| `cast.tracks` | `{"audio":[…],"text":[…]}`, each entry `{"id","label"}`: the list the TV's own subtitle and audio menu shows, written once the title is open. Empty arrays before that. |
| `cast.audio` | The id of the audio track playing on the TV; `""` until the TV has written its list. |
| `cast.text` | The id of the subtitle track shown on the TV; `""` for none. |

What a reader accepts, beyond `v=1`'s rules: every member has `remote` as a
boolean; `cast` is `null` or an object with every field above at its type;
`to`, `from` and `profile` are non-empty strings, and `state` is one of the five
words. `tv`, `note`, `audio` and `text` may be empty. Ids inside `tracks` are
the TV's own and mean nothing to anyone else. Unknown keys are ignored. Writers
emit the fields in the order shown, as `v=1` already requires, so the web and
Android write the same bytes. The web module is the reference, and the Kotlin
port is held to it by `web/test/fixtures/together/`.

Liveness gains two rules:

- A session whose `cast.state` is anything but `on` is stale **60 s after
  `at`**, so a TV opened later never starts a request nobody is waiting for.
- **Remote members never keep a session alive.** When the last member that is
  not a remote leaves, the session ends (`ended: true`, then the message is
  deleted), as it does today when the last member leaves.
- A session whose `cast.state` is not `on` is a request, not something to
  watch: no screen offers Join for it or shows it in the bar.

## 2. Starting a cast

1. **Play on TV.** The small screen writes a new session:
   - `set` and `pos`: on a title page, what Play/Resume would start, at its
     resume point, playing; in the player, the set and second it is at, with
     its play state;
   - itself as the only member, `remote: true`;
   - `cast.state: "offer"`.

   Moving what's playing, the small screen keeps playing for now.
2. **The TV hears it** through the push path, within milliseconds, on any
   screen: Mediagram already listens to together messages app-wide.
3. **The TV answers:**
   - **Refused**, when the caster's profile may not watch this title (age
     limit) or has no time left today (daily limit): `state: "refused"`, with
     the reason in `note`. The small screen already offers only what its
     profile may watch; the TV checks again because that is where it is
     decided.
   - **Idle** (its player is closed: Home, a title page, settings, the profile
     picker): it accepts. In one edit it adds itself as a member, sets
     `tv` to its screen id and `state: "on"`, and pauses the session at the
     expected position. It opens the title there under the caster's profile.
     If the offer was playing, it writes `playing: true` once its first frame
     shows, so neither a cold start nor a slow buffer skips the opening
     seconds.
   - **Busy** (its player is open, paused or not, in a session or not):
     `state: "busy"`, with what it is playing in `note`. The small screen asks
     "The TV is playing *X*. Replace it?". **Replace** writes
     `state: "replace"` with a fresh `pos` and `at`; the TV leaves whatever it
     was playing (and its own session, if any) and accepts as above.
     **Cancel** deletes the message. A Replace made after the session has gone
     stale sends a new offer instead.
4. **No answer within 5 s:** the small screen deletes the message and says
   "Mediagram isn't open on the TV. Open it there and try again."
5. **Once `on`**, the small screen stops its own playback, if it had any, and
   shows the remote.

A second TV, should one appear, may also accept. The TV named in `tv` keeps the
cast; any other TV that reads a `tv` that is not its own closes its player.

## 3. During a cast

- **Profile.** The TV plays as `cast.profile`: progress, Continue, stats and
  a kid's limits all belong to that profile. The TV's own chosen profile does
  not change and is not written anywhere.
- **The TV remote keeps working.** The TV is an ordinary member, so pausing or
  seeking there is an edit like any other, and the remote shows it.
- **Labels.** The TV's player label reads **Cast from Tablet** (`cast.from`).
  The TV keeps watch together's two-second notices ("Paused by Tablet"); the
  remote shows their mirror image ("Paused on TV").
- **Subtitles and audio.** Once the title is open, the TV writes `tracks`,
  `audio` and `text`. A choice on the remote edits `audio` or `text`, and the
  TV applies it. A choice made on the TV remote is written back the same way,
  so the remote shows it. The first choice is made as the TV makes it today,
  under the caster's profile. Plain watch-together members ignore these
  fields: there, every screen still picks its own subtitles.
- **Next episode** works as it does today: the TV's Up next, or Next episode
  on the remote, edits `set`.
- **Others can watch along.** The title page still offers Join, and a screen
  that joins plays on its own screen under its own profile, as today.

## 4. Ending

- **Stop on the remote**: `ended: true`, then the message is deleted. The TV
  closes its player.
- **The film or the last episode ends**: as today, the first screen to reach
  the end ends the session. The remote says "Finished".
- **Back out of the player on the TV**: the TV leaves. Unless someone joined
  to watch along, it was the only member playing, so the session ends, and
  the remote says "Stopped on the TV".
- **A kid caster runs out of time**: the TV shows today's time's-up screen,
  leaves, and the session ends.
- **Afterwards, the TV** returns to its own profile's Home, or to the profile
  picker if it had no profile.
- **If the remote goes away** (the app is closed, the tablet sleeps), the TV
  keeps playing. Any phone, tablet or web player picks the cast up again from
  its bar.
- **Known gap:** a TV that sleeps or loses power mid-cast writes nothing, so
  the remote can't tell. Stop still cleans up; otherwise the session goes
  stale when the title would have ended. This is the gap a crashed screen
  already leaves in watch together.

## 5. What the viewer sees

On the phone, the tablet and the web player. The TV gets no new button.

- **Play on TV**
  - On the title page, beside Play/Resume: casts what Play/Resume would start.
  - In the player, beside Watch together (the tablet's tool row, the web's
    control card): moves what is playing.
  - Greyed out, with the reason: "Telegram isn't reachable"; "Leave watch
    together first" while this screen plays in a session.
  - Shown to kids too; their limits apply.
- **After pressing it**: "Asking the TV…" for at most 5 s, then the remote,
  the Replace question, "The TV won't play this: *reason*", or "Mediagram
  isn't open on the TV. Open it there and try again."
- **The remote** is the existing player screen with the title's backdrop,
  dimmed, where the video would be, and the same control card:
  - the title or episode, and the label **Playing on TV**;
  - play/pause, the seek bar at the TV's position, −10/+10 s and speed
    (`rate` is already in the session);
  - Next episode, Subtitles & audio (the TV's list), and **Stop**.

  Left out: picture-in-picture, the system information panel, Report, and
  volume, which stays on the TV remote. A screen that opens the remote adds
  itself as a remote member, so the TV's notices can name it.
- **The bar.** Backing out of the remote returns to browsing; the cast goes
  on. While a session with `cast.state: "on"` is live, a bar sits at the
  bottom: **Playing on TV · *Title***, with play/pause. Tapping it opens the
  remote. It shows on every phone, tablet and web player of the household, not
  only the caster's. For a cast session it takes the place of Home's
  "Watching together" notice.
- **On the TV**, only the label **Cast from Tablet** and the notices.

## 6. Miracast / AnyView Cast (phone and tablet)

- **Connecting** is Android's job. With AnyView Cast switched on at the
  Hisense, the viewer connects from the tablet's own Cast tile; an app can
  neither start that connection nor list the TVs. Until something plays, the
  TV mirrors the tablet, as Android does already.
- **Playing.** While a wireless display is connected (Android reports it as
  a presentation display), Mediagram's player puts the video full screen on
  it, subtitles included, through an Android `Presentation`. The phone or
  tablet shows the remote from §5, labelled **Playing on *display name***
  (the name Android gives the display), driving its own player instead of a
  session. It happens without asking: being connected says where the picture
  should go.
- **Sound** goes to the TV, as Android routes it while connected.
- **Profile and channel.** It plays as the small screen's own profile, because
  the small screen is the one playing. Nothing is written to the channel.
- **Limits:** at most 1080p and no HDR or Dolby Vision; the small screen
  decodes and must stay on with Mediagram in front. Leaving Mediagram or
  turning the screen off pauses (picture-in-picture is off while a wireless
  display is in use); coming back puts the picture back on the TV.
- **The connection drops:** the video comes back to the small screen, paused.

## 7. AirPlay (web player in Safari)

- When Safari reports an AirPlay TV nearby, the web control card shows
  Safari's AirPlay button. Choosing a TV hands the stream's address to it:
  the TV fetches the stream itself from the web server's network address
  (the player is opened over a LAN address already). The web player stays the
  remote with its usual controls, labelled **Playing on AirPlay** (Safari does
  not give the device's name).
- **Transcoded titles.** Safari normally gets these through Media Source
  Extensions (hls.js over `ManagedMediaSource`), which AirPlay can't hand to
  a TV. For AirPlay, the player gives Safari the HLS playlist itself.
- This is independent of the box cast: no channel message, the browser's own
  profile.
- **Uncertain until the probe:** whether a direct stream needs anything the
  TV's request lacks, and whether subtitles reach a Hisense over AirPlay.

## 8. Deliberate differences

- **Miracast is Android-only.** A browser cannot start or address a wireless
  display.
- **AirPlay is web-only (Safari).** Android has no AirPlay, and Apple
  publishes no sender kit.
- **The TV casts nothing.** It is the receiver.

## 9. Where it lives

- **Format:** `docs/mlib-spec.md` §7b, rewritten for `v=2`.
- **Shared logic**, web first, because the web is the reference:
  - `web/public/lib/together/session.js` parses and writes `v=2` (and still
    reads `v=1`);
  - a new `web/public/lib/together/cast.js`: the TV's answer (accept, busy,
    refused, ignore), stale offers, and the end rule for remote members;
  - fixtures in `web/test/fixtures/together/`: `v=2` format cases next to the
    `v=1` ones, and a new `cast.json`;
  - the Kotlin port in `android/core/model/src/main/kotlin/together/`, held to
    the same fixtures.
- **Rust core:** it classifies by the `#mlib-together ` prefix and moves text,
  so it should need no change; the plan confirms that before relying on it.
- **Web:**
  - server: `POST /api/together` gains `offer`, `replace`, `stop` and
    `tracks`;
  - page: Play on TV, the player page's remote mode, the bar, and the AirPlay
    button with the playlist handoff.
- **Android:**
  - `core/data`: the together sender and live state gain the cast edits;
  - the TV's cast receiver, app-wide: it answers offers, opens the player
    under the caster's profile, and returns afterwards;
  - a **"playing as" profile** carried through the player, so progress,
    stats and kid limits go to the caster rather than to the TV's chosen
    profile. This is the largest internal change;
  - `feature/player`: the remote mode, and Miracast's second screen;
  - phone and tablet UI: Play on TV, the asking and Replace dialogs, the bar;
  - TV UI: the **Cast from Tablet** label.

## 10. Failures

- **Telegram unreachable:** Play on TV is greyed out with the reason.
- **An edit fails:** retried, and a lost move is followed from the channel,
  as watch together already does.
- **Two casts at once:** the second sees `busy` and gets the Replace
  question.
- **No answer, the TV sleeping, the Miracast connection dropping:** §2, §4
  and §6.

## 11. Testing

- **Fixtures** in JavaScript and Kotlin: `v=2` parse and write, `v=1` still
  read; the TV's answer for idle, busy, refused, replace, stale and
  another-TV cases; the remote-member end rule.
- **Android unit tests:** the TV receiver (accept, busy, replace, refuse,
  return to its own profile), the "playing as" profile reaching progress and
  stats, the remote, and moving the video to and from a presentation display.
- **Web:** the preview harness (stubs, no Telegram) for the buttons, the
  remote and the bar.
- **Device walks** on the tablet and the box, with the test profile and
  navigation only (no selecting settings, never a real kid's profile); then
  Miracast on the Hisense; then AirPlay with the user's Apple device in
  Safari.

## 12. Before building: two probes

1. **Miracast.** Connect the tablet to the Hisense from its Cast tile and
   check that Android reports a presentation display (`dumpsys display`). If
   Xiaomi's Cast only mirrors without one, §6 shrinks to keeping Mediagram's
   player full screen while mirrored, and that is reported back before more
   is built.
2. **AirPlay.** From the user's Apple device in Safari, AirPlay one direct
   stream and one transcoded title to the Hisense, with subtitles on. Note
   what plays, what doesn't, and what the TV requested.

## Build order

Probes; the shared format and logic; the web; the Android TV receiver and the
"playing as" profile; the Android sender, remote and bar; Miracast; AirPlay;
docs, version and device walks.

## Out of scope

- Google Cast and Cast Connect; AirPlay from Android.
- A TV picker; volume from the remote.
- Waking the TV, or a listener on the box while Mediagram is closed.
- DLNA; Report on the remote; casting from the TV.

## Release

A new feature: the next minor, **0.127.0**, in all three manifests. That is
also above the box's sideloaded 0.126.1, so the box takes it from the channel.
Docs to update: `docs/mlib-spec.md` §7b, `docs/system-architecture.md`,
`docs/web-player.md` and the changelog.
