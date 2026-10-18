# Cast to the TV — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From a phone, tablet or the web player, a viewer sends a title (or what is playing) to Mediagram on the TV box, which plays it under the viewer's profile while the small screen becomes the remote. Phones and tablets also put their video on a Miracast/AnyView TV, and Safari sends it over AirPlay.

**Architecture:**
- **A cast is a watch-together session with a remote in it.** The message moves to `#mlib-together v=2`, with `members[].remote` and a `cast` section. The pure rules live once on the web (`web/public/lib/together/`) and are ported to Kotlin in `core/model`, both held to `web/test/fixtures/together/`.
- **The I/O layers do the handshake.** The web server's hub and Android's `TogetherLive` send the offer and wait up to 5 s for the TV's answer, so each sender makes one call and gets one answer.
- **The TV answers in a receiver that runs while Mediagram is in front.** It opens the player under the caster's profile, a "playing as" profile carried through the player.
- **Miracast is an Android `Presentation`; AirPlay is Safari's own picker.** Neither writes to the channel.

**Tech Stack:** Bun/TS server and browser ES modules (teleproto); Kotlin, Compose, media3 1.10.1 (`SimpleBasePlayer`), Hilt; no Rust change.

**Spec:** `docs/superpowers/specs/2026-10-18-cast-to-tv-design.md`, which the executor reads alongside this plan.

## Global Constraints

- **Version:** 0.127.0 in `Cargo.toml`, `web/package.json` and `android/app/build.gradle.kts`, all three at once, by regex (memory: exact-string bumps no-op when another session moved `main`).
- **The web is the reference** for wording, layout and rules (`CLAUDE.md` § Surface Parity). Deliberate differences are spec §8 only.
- **Exact words** are those in `cast.json` "words" (phase 02); never retype them in a surface.
- **Line limits:** each new file stays under 200 lines. A ratcheted file that grows gets its ceiling raised, with a dated sentence (`web/test/code-standards.test.ts`). The Kotlin detekt limits hold.
- **Never pin** a together message. **Never** discover a session through channel search.
- **Commits:** conventional, with no AI references and no plan labels in code, test names or messages. **No push, no release** without the user's word.
- **UI checks:** use the preview harness (`cd web && bun run preview`), never the real player; drive it with gstack `/browse`, never `mcp__claude-in-chrome__*`.
- **Devices:**
  - always pin a serial: the tablet is `ANDROID_SERIAL=caad49da`, the box is `192.168.0.56:5555` (or nmap port 5555 if it has moved);
  - use test profiles only, never a real kid's;
  - navigate only: never OK on a setting, never "Start over";
  - the TV must be switched on first.
- **No Rust change** was verified: `crates/mediagram-core/src/updates.rs:20` matches the unversioned prefix `#mlib-together `, and `api/together.rs:85` passes the text through untouched. If a core change becomes necessary anyway, regenerate the bindings and rebuild the `.so` before any install.
- **Work** on branch `feat/cast-to-tv`, in worktree `../mediagram-cast`. Run `scripts/check.sh` before the merge.

## Review Focus

1. **An offer lands while a watch-together session is running.** No screen in that session may leave it. Today a newer session makes followers leave (`together.js:143`, `TogetherFollowing.kt:28`), so requests must never become the shown session. Tests: phase 04 hub test 2 and the together-now test; phase 06 `requestsAreNeverShown`.
2. **The TV's answer arrives while the caster's push stream is down.** At the 5 s mark the caster re-reads the message by id before it says "isn't open". Tests: phase 04 `together-cast-desk.test.ts` test 4 and phase 06 `offerReadsTheMessageBeforeGivingUp`.
3. **A title with many long subtitle tracks.** The tracks are capped and labels cut, so the message stays under Telegram's 4096 characters. Tests: `cast.json` `tracked` cases and phase 06 `tracksStayUnderTheMessageLimit`.
4. **The TV playing as a kid caster.** Progress, stats and the daily limit go to the kid. The TV's own Continue, chosen profile and saved places stay as they were. Tests: phase 07 `castProgressGoesToTheCaster` and `tvProfileIsUntouchedByACast`.
5. **Stop on the remote while the TV is still opening the title** (the accept has landed, the first frame hasn't). The TV must close, and must write no `playing:true` after `ended`. Test: phase 07 `stopBeforeFirstFrameClosesWithoutPlaying`.

## Phases

| # | Phase | Depends on | Status |
|---|---|---|---|
| 01 | [Probes: Miracast display, AirPlay from Safari](phase-01-probes-miracast-and-airplay.md) | none | pending |
| 02 | [Shared format v=2 and cast rules (web reference)](phase-02-shared-format-and-cast-rules.md) | none | pending |
| 03 | [Kotlin port of the format and rules](phase-03-kotlin-port-of-format-and-rules.md) | 02 | pending |
| 04 | [Web server: hub, routes, preview stub](phase-04-web-server-hub-routes-preview.md) | 02 | pending |
| 05 | [Web page: Play on TV, ask, remote, bar](phase-05-web-play-on-tv-remote-and-bar.md) | 04 | pending |
| 06 | [Android data: cast edits and requests](phase-06-android-data-cast-edits-and-requests.md) | 03 | pending |
| 07 | [Android TV: playing-as profile and receiver](phase-07-android-tv-playing-as-and-receiver.md) | 06 | pending |
| 08 | [Android phone/tablet: Play on TV, remote, bar](phase-08-android-sender-remote-and-bar.md) | 06, 05 | pending |
| 09 | [Android: Miracast second screen](phase-09-android-miracast-second-screen.md) | 01 | pending |
| 10 | [Web: AirPlay in Safari](phase-10-web-airplay-in-safari.md) | 01 | pending |
| 11 | [Docs, version, checks, review, device walks](phase-11-docs-version-review-and-walks.md) | all | pending |

Order: phase 01 can run while 02 and 03 are built. Then 04 and 06 run in parallel, then 05 with 07, then 08. Phases 09 and 10 each start only once their probe has come back positive.

## Decisions this plan makes where the spec is silent or the code differs (for the user to confirm)

1. **A request is never the shown session.** `live` on every surface skips sessions whose `cast.state` isn't `on`, and the TV sees requests through a separate `requests` view. Without this, an offer would push every screen out of a running watch-together session.
2. **A cast that goes `on` while another session is live becomes the one everyone sees.** Screens in the older session leave it with "Watch together ended", as they do today when a newer session is found. Keeping two sessions in view would mean tracking several on every surface.
3. **The caster waits for the answer inside the I/O call.** The web `offer` POST is held for up to 5 s; Android's `TogetherLive.offer` suspends for up to 5 s.
4. **Stop is the existing `end` op.** Cancel is a new `cancel` op, which deletes the message. The spec's "stop" route op is therefore `end`.
5. **Refusal reasons, exact copy:** "it's rated above *Name*'s age limit", "*Name*'s time is up for today", "the TV's library doesn't have it yet" and "the TV doesn't know this profile yet". The last two are cases the spec didn't foresee.
6. **Tracks caps:** at most 8 audio and 24 subtitle entries, with labels cut to 32 characters, because a Telegram message holds 4096 characters.
7. **A kid running out of time mid-cast.** Android has no time's-up screen, and the open title plays to its end on every screen today. So the next episode is refused with the existing "That's all the watching for today…" notice, and the cast ends with "Finished". The spec's "time's-up screen" doesn't exist.
8. **During a cast the TV hides watchlist, kids and add-to-list.** They would write to the TV's own profile, which the spec says a cast never touches. The TV's episode-list watched dots stay the TV profile's; that is cosmetic and left as is.
9. **The web remote is its own dialog, styled with the control card's classes.** It is not a mode inside `player.js`, which sits at its 1018-line ceiling and is bound to the `<video>`. Android's remote reuses `PlayerControlCard` over a `SimpleBasePlayer` that mirrors the session.
10. **The bar shows only for a title in this profile's catalog**, as Join does. A kid never sees a title their profile hides.
11. **AirPlay for transcoded titles** first tries WebKit's own route: hls.js over `ManagedMediaSource`, with the playlist as an alternate `<source>`. Native HLS is the fallback. The probe decides.

## Review

(Filled in at close-out.)
