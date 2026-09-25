# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Users

A household, several viewers, including children. Phones and tablets today; a
television box is owned and is the next surface to be built, once the phone
reaches parity with the web player. Viewers are not
administrators: one person uploads and publishes the library, everyone else
only ever finds something and watches it. The same person often moves between
a laptop browser and a phone in the same evening.

## Product Purpose

mediagram keeps a household's own media library in a private Telegram channel
and plays it back. A Rust CLI inspects, remuxes, uploads and indexes; a web
player and an Android app read that index and stream the bytes. Success is a
viewer finding something they already own and watching it, on whichever
surface is to hand, without ever thinking about where the bytes live.

## Positioning

Nothing here is sold, ranked, or recommended by an algorithm. The library is a
catalogue of things already owned, and the product's job is retrieval, not
discovery. Telegram is the storage layer, which is what makes a multi-terabyte
library free to host and reachable anywhere the account is.

The index is deliberately backend-agnostic: `grammers_*` appears nowhere in the
modules a second client reuses, and a grep enforces it. That is why three
consumers read one schema without reparsing anything.

## Operating Context

925 sets as of 2026-09-25: 581 films, 182 series episodes and 162 course
lessons. Course documents are the fourth kind; none are in the index today. A private channel holds the media, the pinned index snapshots, and
the posters.

The web player runs on a machine in the household and is reached over the LAN
or a tailnet; it transcodes what browsers refuse and serves HTTP Range over a
set's concatenated parts. The Android app has no server at all: `mediagram-core`
(Rust, grammers, bound through UniFFI) is itself the Telegram client, decodes
natively, and never transcodes.

## Capabilities and Constraints

- Four caption kinds: `movie`, `ep`, `tut`, `doc`. A surface that recognises
  fewer hides part of the library.
- The web player reads a published encrypted package; the phone reads the
  newest index snapshot the channel holds. The channel index carries no
  artwork, so the phone fetches its own posters and synopses from TMDB.
- Both surfaces keep watch state per profile: positions, finished titles,
  watchlist, collections and kids profiles live in the core's state and sync
  through the channel, so a title started on one device continues on the
  other. The phone's start page carries Continue and Next up, with Watchlist
  and Collections beside them.
- A kids profile sees only titles rated FSK 12 or under, plus unrated titles
  marked for Kids by hand. The rule is ported from the web player, and there is
  no separate Kids shelf on either surface.
- Deliberate differences, recorded in `docs/system-architecture.md`: profiles
  cannot be renamed or deleted on the phone, and sync is on by default there
  where the web player needs `MEDIAGRAM_SYNC_STATE`.
- The phone has no audio-track or subtitle selection (reaching 312 and 206 sets
  respectively), no search, and no notes.
- `:ui-tv` is a registered Gradle module with no source. Television devices
  install the app and get a placeholder. It is the next surface after phone
  parity.
- Playback on the phone is latency-bound, not throughput-bound: the link
  outruns the bitrate and what costs is the round trip per read.
- minSdk 24, targetSdk 37, Compose with Material 3, media3 for playback.
  Secrets live in `EncryptedSharedPreferences`.
- A viewer is identified by profile on both surfaces, chosen from a picker.

## Brand Commitments

The web player's identity is binding on the Android app **in spirit, native in
execution**: the same voice, with Android conventions winning where they
genuinely conflict.

That identity, stated in `web/public/style.css` and chosen by the user from
four options on 2026-09-18, is not to be reversed silently:

- **A printed catalogue of things already owned, not a storefront.** It borrows
  from a press catalogue rather than a streaming service.
- Typographic rather than card-based. Hairline rules instead of cards; artwork
  set as plates with the caption beside them, not captions inside boxes.
- One restrained accent, an imprint red, for the shelf you are on and nothing
  else. An ochre for the only thing the catalogue warns about, a sage green for
  the only good news it has.
- **The player is black, on purpose, and carries its own palette.** A poster
  reads best against a page; a picture reads best against nothing.
- Faces: Fraunces for display, Newsreader for text. Both variable, both with an
  optical-size axis, shipped on both surfaces.
- The Android app is dark on every device regardless of system theme, because
  "a media library is looked at in the dark": the same catalogue set in ink
  rather than on paper. Its palette and type are recorded in `DESIGN.md`.

## Evidence on Hand

- A real library of 566 sets on a real Telegram Premium account's private
  channel, verified byte-for-byte against recorded hashes.
- A real Android phone on `adb`, signed in, used for device validation.
- `web/public/font/` holds both families as variable woff2, latin and latin-ext;
  the Android app ships the same two families.
- No iOS surface exists and none is planned. No television surface exists yet;
  it is next after phone parity.
- No user research, analytics, or usage data of any kind. Future work must not
  invent any.

## Product Principles

1. **Two surfaces, one library.** A viewer who uses both should not have to
   learn it twice. A deliberate difference is fine; a silent one is a defect in
   the newer surface.
2. **The web player is the reference.** Where it has already decided something,
   the phone reads that decision rather than making it again.
3. **A catalogue, not a storefront.** Retrieval over discovery. Nothing is
   ranked, promoted, or sold.
4. **Say what is true, including what is missing.** An unavailable action states
   its reason under its own label; a row with no honest source is left out
   rather than filled with a plausible number; a title that cannot be opened is
   shown and explained rather than hidden.
5. **Binding in spirit, native in execution.** The voice carries across
   surfaces; the mechanics do not. Touch targets, back behaviour, system bars
   and dynamic type follow the platform.

## Accessibility & Inclusion

No formal standard has been set. What is factually true today: children are
among the viewers, and the web player carries kids marks that are shared across
profiles; icon-only controls carry content descriptions; a disabled action
states its reason rather than only greying out. Contrast has never been
measured on either surface, and the phone's type does not yet respond to
dynamic type settings.
