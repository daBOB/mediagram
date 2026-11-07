# Game night: party games on the TV box

Status: design, awaiting approval. Branch `feat/spieltisch`, worktree
`../mediagram-spieltisch`. Starting point: the user's draft "Spieltisch" at
`/home/andre/Downloads/spieltisch/` (Rust host + React client, ~3,200 lines).

## Intent

A household evening in the living room: 4–6 people, the Hisense TV with the
mediagram TV box as the table, everyone's phone as their hand. Sometimes it is
only the household, sometimes friends come over who have never seen mediagram.
The TV box already holds the whole library with its posters, stills, cast and
years, so the games can be about the films and series the household owns —
that is what makes this belong in mediagram rather than being a separate app.

Success is the first real game night: friends join in under a minute without
help, the group plays a full quiz and a full poster game, nobody drops out
because of the tech, and with *Nur für Kinder* on nothing above the kids' age
reaches the big screen.

## Goals (first project)

1. The TV box hosts a game night; phones join by scanning a QR code.
2. Game 1: a buzzer quiz with film rounds, general-knowledge rounds, or mixed.
3. Game 2: a poster game — a film or series poster starts heavily pixelated
   and gets sharper every 2 seconds until someone names it or it is restored.
4. A *Nur für Kinder* toggle in the lobby.

## Non-goals (this project)

- The other games the user wants later, each its own project, order decided
  after this one: a Ludo-style race game, a category trivia board, a property /
  money game, a hotel-building game, a life-path game, and a party board with
  minigames (last, it is the biggest).
- Funny 3D avatar images for players (user: "add later").
- HTTPS. Every planned game is tap-only; HTTPS only buys iPhone tilt/shake,
  camera, microphone and home-screen install. Revisit only if a minigame needs
  motion. (The user asked about a domain + Let's Encrypt; that works, Plex-style,
  with router rebind exceptions, a fixed box IP and DNS-01 renewals.)
- The web player hosting a game night. TV box only (see Parity below).
- Sound and music.
- Adding your own questions without a new release.

## Anti-goals

- A copy of an existing game. Every game gets its own name, look and rules;
  no Monopoly, Mario Party, Trivial Pursuit or Buzz names, boards or artwork.
  The traditional Ludo race game is fine with its usual rules.
- An 18+ still or poster on the big screen while *Nur für Kinder* is on.
- Touching anyone's library life: no watch history, Continue, stats, or daily
  watch limit; guests never see a profile picker or the library.
- Anything a friend has to install.

## Decided by the user

- Host: the mediagram app on the TV box ("yes").
- Players: household and sometimes friends; all on the same Wi-Fi as the box.
- First game: the buzzer quiz. Quiz content: "both" — film rounds from the
  library and general knowledge.
- "add a toggle kids only".
- "add a game with movie series guessing where we see a poster highly
  pixelated and every 2 seconds it gets sharper until one answer it or poster
  is restored".
- Mockups of the lobby and the poster round: "looks good" (German game text,
  lobby layout, poster rules as shown).
- *Nur für Kinder* on a kids profile is forced on at the kid's limit and
  locked; on a grown-up profile it starts off, means FSK 12, and the starter
  switches it ("yes").

## The design

### Hosting and joining

- The TV app gets a *Games* entry. Opening it starts the game host inside the
  app process and shows the lobby; leaving the Games screen stops the host and
  closes the port. Nothing runs in the background.
- The host is the draft's Rust `Room` + `Game` design, moved into this
  workspace and linked into `mediagram-core`, started and stopped from Kotlin
  through UniFFI. It serves the phone page, the TV page, the QR code and the
  WebSocket on the LAN.
- The lobby shows a large QR code for `http://<box-ip>:<port>/` and the address
  in text. The box's IP moves with DHCP (it has been .35, .46, .56, .21); the QR
  always shows the current one.
- Phones open a web page, type a name, and are seated with a colour. A phone
  that sleeps or drops rejoins its seat (the draft's reconnect token). Up to 8
  seats.
- The first to join starts (marked ★); the TV remote's OK can start too.
  Everyone else's phone shows who is choosing.
- While the Games screen is open the box must not dim or start its screensaver.

### Lobby

The starter's phone chooses the game (*Buzzer-Quiz* with *Filme / Allgemein /
Gemischt*, or *Poster-Raten*) and the *Nur für Kinder* toggle, then starts. The
TV shows the chosen game and the toggle state as badges. Layout as in the
approved mockup, `2026-11-03-game-night-mockup.html` beside this file (a
fragment written for the brainstorm companion; its poster is the repo's
`wallstreet story.poster.png`).

### Game 1: Buzzer-Quiz

The draft's speed quiz rules: 8 questions; the question shows on the TV, then
everyone's four answer buttons go live at the same moment; 12 seconds to answer;
a right answer earns 1000 down to 500 points by speed; the TV reveals the answer
with everyone's result; a final ranking ends the game. Fairness as in the draft:
taps carry the phone's estimate of host time, trusted at most 400 ms before
arrival.

**Film questions**, generated on the box from the library:
- a still (TMDB backdrop) on the TV: which title is this?
- which year was it released?
- who directed it?
- who plays in it?

Wrong options come from the same library and kind, so they are plausible (years
near the right one). *Gemischt* alternates film and general questions.

**General questions**: a German pack of about 300 family-friendly questions
across categories, written for this project, each with a source it was checked
against, shipped inside the app.

### Game 2: Poster-Raten

- 10 rounds. Each shows one film or series poster on the TV, heavily pixelated,
  in 8 sharpness steps, one step every 2 seconds.
- Phones show four titles. The first right answer ends the round. A wrong tap
  puts that player out for this poster.
- Points: 1000 at the first step down to 500 at the last.
- When the poster is fully sharp there are 3 more seconds, then the answer is
  shown either way.
- Phones never receive the image, so nobody can zoom in.
- Poster titles become readable before the last step; use a textless poster
  when TMDB has one, the normal one otherwise.
- Known tuning risk: with a lockout only, a blind first-second guess has a 25 %
  chance at 1000 points. If play shows people guessing blind, add a penalty —
  decided after the first real night, not now.

### Which titles the games use

- Films and series only. Series count as one show, never per episode (the same
  grouping the web player uses). No courses or documents.
- *Nur für Kinder* depends on the TV box's current profile (decided by the
  user):
  - **Kids profile:** always on, at that kid's own limit (FSK 6 or 12), and no
    phone can switch it off — kids profiles have no PIN, so an open toggle
    would be a way past it. The lobby shows the toggle as locked.
  - **Grown-up profile:** starts off; on means **FSK 12**; the starter can
    switch it either way.

  Either way it is the kids-profile rule — rated at or under the limit, or
  unrated and marked for kids from that age or under. The rule lives in
  `android/core/model/src/main/kotlin/AgeRating.kt` (`forKidsProfile`) and
  `web/public/lib/age-rating.js`; it is used, not re-implemented in Rust.
- A title needs the data its question uses (a still for still questions, a
  poster for the poster game, credits for cast/director questions).

### Separate from the library

A game night writes nothing to profile state: no watch history, positions,
stats, or daily-limit time. Players are names only; no account, no profile.

### Language

Everything inside the game — TV and phone pages, questions — is German. The
*Games* entry in the TV app follows the app's language.

### Look

The draft's look carries over for now (TV and phones share one page design);
restyling, together with the 3D avatars, is a later project.

## Parity

Deliberate difference: game night exists on the TV box only. The web player is
not the table in the living room, and phones join through a browser page, not
the mediagram app. Record this where the parity notes live
(`docs/system-architecture.md`, "Television differs from the web player").

## Constraints

- Repo rules: version bump in all three manifests (`Cargo.toml`,
  `web/package.json`, `android/app/build.gradle.kts`); `scripts/check.sh`
  passes (clippy `-D warnings`, cargo, bun, gradle); docs updated
  (`docs/system-architecture.md`, changelog).
- `main` carries ~68 uncommitted files from another session (0.142.2); expect
  to renumber the version at merge.
- Draft vs workspace: edition 2021 → 2024; axum 0.7 → the workspace's 0.8.9
  (needs its WebSocket feature); new crates `ts-rs`, `rust-embed`, `qrcode`,
  `local-ip-address`, `rand 0.8` — reuse what the workspace already has where it
  can (e.g. `getrandom` for tokens).
- The draft is an untrusted download until reviewed; nothing from it is built
  or run before it is read.
- Device testing follows the house rules: pin `ANDROID_SERIAL`, walk the box
  without changing settings.

## Calls I made (user to check)

1. The TV app shows the game's TV page in a built-in browser view instead of a
   native Compose screen — one page design for box, phones and any TV browser.
   Redraw natively only if it stutters on the box.
2. Games is available on kids profiles too (with the locked toggle above).
3. A wrong tap in the poster game is a lockout only, no point loss (as mocked).
4. Rounds: quiz 8, poster game 10.
5. Leaving the Games screen ends the night; Back mid-game returns to the lobby.
6. One way in for everyone (browser), including household members with the app.
7. The general pack is written by us, ~300 questions, each source-checked.
   The pack is a plain file the user can skim before release; that skim does
   not block the build.

## Left to the builder

Crate layout and names; how Kotlin hands the host the eligible titles and their
data; how the box's LAN IP and port are found; where images are served from
(the box's artwork cache or TMDB URLs); how the web client is built and
embedded; question-pack file format; avoiding repeats within an evening; test
strategy (the draft's deterministic `GameCtx.now` tests and its bot smoke test
are a good base); where the Games entry sits; series without a director;
image sizes; finding textless posters; ending a poster round early once
everyone is out.

Check early, before building on them: the box's WebView version can run the
draft's TV page smoothly, and the box has the TMDB access the film questions
need.
