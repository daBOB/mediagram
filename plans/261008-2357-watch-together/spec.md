# Watch together — design spec

2026-10-08. Agreed in conversation in four sections. This file is the written form for review.

## Goal

Screens in one household play the same title in step. When any screen plays, pauses, seeks, changes speed or moves on to the next episode, every other screen does the same within about a second. The screens are usually in different rooms. The feature runs on the TV, the phone/tablet and the web player.

## User decisions (2026-10-08, do not reverse silently)

| # | Question | Answer |
|---|---|---|
| 1 | Who watches together | **The same household**: screens signed in to the same library channel. There are no invites and no other accounts. |
| 2 | Surfaces | **TV, phone/tablet and web.** The web is the reference. |
| 3 | Who controls | **Anyone.** Every screen in the session can play, pause, seek, change speed and pick the next episode. |
| 4 | How tight | **Different rooms.** Screens stay within about half a second of each other. Same-room, lip-sync tightness is not a goal. |
| 5 | Transport | **A: one channel message per session**, edited in place. There is no server or relay of our own. |

## 1. The session message

Each session is one **unpinned** text message in the library channel:

    #mlib-together v=1
    {"session":"<uuid>","set":"<set id>","playing":true,"pos":1234.5,"rate":1,"at":1791500000123,"by":"<screen id>","members":[{"screen":"<screen id>","label":"TV"}],"ended":false}

| Field | Meaning |
|---|---|
| `session` | A random UUID, made when the session starts. |
| `set` | The set id of the title playing: a film, or one episode or lesson. |
| `playing` | `true` while playing, `false` while paused. |
| `pos` | The position in seconds at the moment `at`. |
| `rate` | The playback speed. It is shared. |
| `at` | The writer's wall clock in epoch milliseconds, when `pos` was true. |
| `by` | The screen that made this change. |
| `members` | The screens in the session. `label` is the surface kind: `TV`, `Phone`, `Tablet` or `Browser`. |
| `ended` | `true` once the session is over. This is written just before the message is deleted. |

- **Screen id.** A screen id is random and lasts for one player instance: one player on an Android device, or one browser tab. It is not the device id. One web server can serve several tabs, and each tab is its own screen.
- **The message is pinned nowhere.** Telegram's pin flood limit stays out of the way, and index readers already skip any caption that doesn't start with `#mlib-index`.
- **Writing.** Every change is an edit to the same message, and the last edit wins. A seek scrub sends its edit 300 ms after the last movement, never one edit per frame.
- **Finding a session.** A screen reads the newest 100 messages of the channel (one history read) and takes the newest live session among them. It also keeps any session it heard about through a push. A message whose marker isn't exactly `#mlib-together v=1`, or whose JSON lacks a field, is not a session. Telegram search is not used: it has been measured never to find a freshly sent message (`web/src/telegram/state-channel.ts:18-26`), and it reads `#mlib-together` as the hashtag `#mlib`, which matches every part caption.
- **What stays on each screen:** the profile, the audio track, subtitles, volume and framing. Each screen also records Continue and stats for its own profile, as normal playback does.
- **Kids.** A kids profile is offered Join only for a title its age limit allows, which is the catalog's own visibility rule. If the session moves on to a title that profile may not see, that screen leaves.
- **One live session per library.** While a session is live, the player's Watch together button becomes Join for that session. No second session can be started next to it.

### Liveness (changed after approval; see the note at the end)

A session is **live** when `ended` is false and:

- it is **playing**, and the expected position (§2) is no more than 2 minutes past the title's duration. If the duration is unknown, this becomes "no more than 6 hours after `at`".
- or it is **paused**, and `at` is under 3 hours old.

A session that is not live is **stale**. Whoever reads a stale session next deletes it.

### Joining and leaving

- **Start.** Watch together in the player sends a new message: this screen is the only member, with its current title, position, play state and speed.
- **Join.** The screen adds itself to `members` with an edit, opens the session's title at the expected position, and follows from there.
- **Leave.** Back out of the player, or "Leave watch together", removes the screen from `members`. If nobody is left, the screen writes `ended: true` and deletes the message.
- **What doesn't leave.** PiP and going to the background keep a screen in the session.
- **The end of a film, or of the last episode.** The first screen to reach the end writes `ended: true` and deletes the message.

## 2. Keeping in step

- **Expected position:** `pos + (now − at) / 1000 × rate` while playing, and `pos` while paused.
- **On each edit**, a screen acts only on what differs from its own state:
  - a different `set`: open that title at the expected position;
  - a different play state: play or pause;
  - more than 0.5 s from the expected position: seek to it;
  - a different `rate`: set the speed.

  A screen ignores an edit whose `by` is its own: its echo arrives after it has already moved on. Changes a screen makes **because it is following** are never sent; only the viewer's own actions are.
- **Drift check.** Every 2 s while playing, a screen compares its own position with the expected one. More than 0.5 s apart, it seeks. Nobody waits for a screen that is buffering: when it comes back, the drift check catches it up.
- **Next episode.** A screen that plays on to the next episode, by autoplay or because the viewer picked it, edits `set` to that episode at `pos: 0`. The other screens follow, because their `set` now differs. A screen that is counting down its own Up next cancels the countdown when it follows. Two screens that send the same move at once agree, so the race does no harm.
- **Clocks.** `at` comes from the writer's clock, and devices are assumed to be NTP-synced. A screen whose clock is off by a constant amount ends up that far out of step once. It does not seek in a loop, because its own position and its view of the expected one advance together.
  - The browser never compares against the server machine's clock. Every `together` event the server sends carries `serverNow`, and the browser corrects for its own clock from that.

## 3. What the viewer sees

The wording and layout come from the web, and the two Android surfaces match it.

- **Starting.** A **Watch together** button in the player:
  - phone/tablet: the card's tool row;
  - TV: the player menu;
  - web: the control card.

  While a session is live, the button reads **Join** instead. On a screen that is already in the session, it reads **Leave watch together**.
- **Joining.**
  - Home's first row is a **Join notice**: "Watching together: *Title* · TV, Tablet". On TV it is a focusable plate.
  - That title's page shows **Join** in place of Play/Resume.
- **In a session.**
  - The player shows a label: **Together · TV, Tablet**.
  - A two-second notice says what another screen did: "Paused by TV", "Skipped to 41:20 by Tablet", "Next episode by Browser".
  - The menu has **Leave watch together**.
- **When it can't run.**
  - Telegram unreachable: Watch together and Join are greyed out, with the reason ("Telegram isn't reachable").
  - The session ends under a screen: a notice says "Watch together ended", and the screen keeps playing alone.

## 4. Where it lives

**Format.** `docs/mlib-spec.md` gains a §7b for `#mlib-together v=1`.

**Shared logic** is written once per language and pinned by fixtures. The web module is the reference, and the core ports it. On the web it is plain JavaScript in `web/public/lib/together/` with `.d.ts` files beside it, because the browser loads it directly and the server imports it, the way it already imports `playable.js`. Both are checked against `web/test/fixtures/together/`, which covers:

- parse and write, including rejected markers and missing fields;
- the expected position;
- `decide(own state, session, own screen, now)` → open, play, pause, seek, set speed, or nothing, including the echo case;
- liveness, playing and paused, with and without a duration;
- join and leave on `members`, including the last screen to leave.

**Web**
- `web/src/telegram/together-channel.ts`: send, edit, delete and search through teleproto.
- The push path:
  - `updates.ts` classifies `#mlib-together` new and edit updates as `together`, and the `channel-updates` fixtures gain those cases;
  - `channel-events.ts` hands them on **straight away, skipping the 5 s Debouncer**, with the message id and text.
- Routes: `GET /api/together` returns the live session, and `POST /api/together` takes `start`, `join`, `act` and `leave`.
- The existing `/api/events` stream (`web/src/catalog-events.ts`) gains a `together` event carrying the session and `serverNow`.
- Browser:
  - `web/public/lib/playback/together.js` drives the `<video>`, the button, the label and the notices;
  - the catalog gets Home's Join notice and Join on the title page.

**Core (Android, through UniFFI)**
- `crates/mediagram-core/src/together/`: the port, checked by `tests/shared_together_fixtures.rs`.
- `api/together.rs`: start, join, act, leave and the latest session, over grammers.
- `updates.rs` classifies `together` as well. The events listener delivers it at once as `LibraryEvent::Together { message_id, text }`, never debounced.
- The UniFFI surface changes, so the native core `.so` and the Kotlin bindings land in the same change.

**Android**
- `android/feature/together`: a controller that drives ExoPlayer through the existing player handle, shared by phone and TV.
- `ui-mobile` and `ui-tv`: the button, the label, the notices, Home's Join row and Join on the title page.

## 5. Failures

| What happens | What the screen does |
|---|---|
| Telegram unreachable | Watch together and Join are greyed out, with the reason. |
| An edit refused by flood-wait | The screen keeps its own state and sends the latest one once the wait ends. Earlier ones are dropped. |
| The message is deleted, or `ended: true` arrives | The session is over: a notice, and the screen plays on alone. |
| Connection lost mid-session | The screen plays on alone. When it reconnects, it re-reads the message by id: if live, it follows; if gone or stale, the session is over. |
| An edit lost to a simultaneous edit from another screen | Last edit wins. The losing screen sees an edit `by` someone else and follows it. |

## 6. Testing

- The shared fixtures, run on both the web and the core.
- Unit tests on each surface for the controller: it follows edits, never re-sends a followed change, ignores its echo, and handles flood-wait and deletion.
- A web player test of the browser follower in the stub harness (`bun run preview`). It never touches the real player.
- **A device walk:** the TV box, the tablet and a browser in one session. It covers play, pause, seek, speed, the next episode, joining late, leaving, the last screen leaving, and a stale session.

On the box, follow the standing rule: navigate only, never change a setting.

## 7. Before building: a short probe

The first plan phase checks two assumptions against the real channel, using a throwaway message that is never pinned and deleted afterwards:

- a history read of the newest 100 messages includes a message sent a second earlier;
- other sessions of the account hear an edit to an unpinned text message as fast as they hear the `#mlib-state` edits today.

## Out of scope

- Other accounts, invite links, chat, voice and reactions.
- Same-room tightness (under 100 ms).
- The VR player.
- Accurate membership when a screen crashes. It stays listed until the session ends or goes stale.
- A second simultaneous session.

## Release

A new feature, so the version moves to the next minor: 0.120.0, in all three manifests. Docs to update:

- `docs/mlib-spec.md` §7b;
- `docs/system-architecture.md` (a Watch together section next to Sync);
- `docs/web-player.md`;
- the changelog.

## Changed after approval

Section 1 as approved said a session is live if it was **edited within the last 10 minutes**. While writing this up I found that a film nobody pauses sends no edits, so after 10 minutes it would look stale, and the next screen to read it would delete it mid-film. Each screen could instead send an edit every few minutes to stay live, but that costs edits against Telegram's flood limit, and a keep-alive can overwrite a real action sent at the same moment. Liveness now comes from the state itself, as described under Liveness: a playing session lasts until shortly after its title ends, and a paused one for 3 hours.
