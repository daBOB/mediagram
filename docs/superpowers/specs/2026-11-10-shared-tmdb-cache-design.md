# Shared poster and title-data cache on the home cache server

Status: design, 2026-11-10. Branch `feat/funstation`.

## Why

Every player fetches TMDB on its own today. On the first funstation the TV app
spent minutes on "Looking up titles · 1761" and downloading artwork, and the
web player on the same machine then ran `mediagram posters` and did the same
work again. Every new device in a house, and every new funstation, repeats it,
and each one needs its own TMDB key typed in.

The user (2026-11-10): "the android app the web player should also [use] the
poster metadata cache", and, asked whether a player without its own TMDB key
should still show posters when the cache server holds them: **"only one device
per house"** needs the key.

## What it is

The home cache server (`mediagram_cache`, already holding video chunks for the
Android app and the web player) also holds what TMDB answered: the title lookups
and details (JSON) and the images (posters, backdrops, cast photos). The first
player in a house that has a key fetches from TMDB and stores the result on the
server; every other player reads it from the LAN, with or without a key.

## Goals

- A player with no TMDB key, in a house whose cache server already holds the
  data, shows the same posters, backdrops, descriptions and cast as a keyed one.
- A keyed player asks the cache server before TMDB, and stores what TMDB sends.
- One implementation for both players: the shared Rust crate `mediagram-tmdb`,
  which the Android core (`crates/mediagram-core/src/api/enrich/*`, `credits.rs`,
  `game_posters.rs`) and the uploader CLI (`crates/mediagram/src/commands/posters.rs`,
  which the web player runs) already use for every TMDB request and image.

## Non-goals

- No new TMDB features; the same requests as today, only cached in the house.
- Away from home (no cache server reachable) nothing changes: a keyed device
  fetches TMDB, a keyless one shows what it already holds, or initials.
- No sharing across houses; each house's cache server fills from that house.

## Anti-goals (would make this a failure even if it works)

- A TMDB key leaving the device it was entered on. Keys never go to the cache
  server or into a cached answer.
- A cache server that is down or slow making artwork slower than today: like the
  chunk reads, an unreachable server is skipped and the player goes on as now.
- Anyone on the LAN being able to replace a poster: writes are signed with the
  pairing token, as chunk writes are.

## Decided

- **Where:** the existing cache server, not a new service. Same port, same
  pairing token, same "reads open, writes signed" rule as chunks.
- **Who needs a key:** one device per house (user). In practice funstation: its
  TV app and its web player's `mediagram posters` both run with the key and fill
  the server for the whole index; every other player reads.
- **Order of lookup in a player:** its own disk cache, then the cache server,
  then TMDB (only with a key). A TMDB answer goes to the disk cache and the
  server.
- **Cache keys:** a JSON answer is keyed exactly as `DiskCachedApi` keys it today
  (sha256 of path + sorted query; the credential is added later and is not part
  of the key), so two devices asking the same thing hit the same entry, whatever
  key or key type each has. An image is keyed by its TMDB path and the size
  asked for.
- **Keyless enrichment on Android:** the core runs the same enrichment with a
  TMDB client that can only read the cache server; a miss leaves that title as it
  is today without a key (initials), to be filled when the keyed device has run.

## My calls (not confirmed by the user; easy to change)

- **Filling the server from caches that are already full.** Funstation's TV app
  and CLI have already answered everything from TMDB, so they would only ever
  hit their own disks. A JSON disk hit on a device with the token therefore also
  checks the server (a cheap status request, once per path while the client
  lives) and uploads the entry when the server lacks it. An image on disk is
  never uploaded: its file is named by title, not TMDB path, so its bytes may be
  another resolution's; a device that misses it fetches it from the CDN and
  uploads that. No separate seed command.
- **No eviction by video.** Images and JSON get their own small budget (8 GiB)
  and LRU, apart from the 128 GiB of chunks, so streaming never pushes out
  artwork that the keyed device would not upload again.
- **Images need no key.** TMDB's image CDN is keyless (portraits are already
  fetched that way, `crates/mediagram-core/src/api/credits.rs`). On a server
  miss any device fetches the image from the CDN at its own size and uploads it,
  so each size a house uses is filled by the first device that wants it. Only
  the JSON (title lookups, details, cast) needs the keyed device.
- **Language is normalised before keying** (`de` and `de-DE` are one key), so
  the Android app (library or device language) and the CLI (`tmdb_language`)
  ask the server the same thing.
- **Newer JSON replaces older** on the server (`mediagram metadata --refresh`
  exists); images are keyed by their TMDB path, which changes when the image
  does, so the first upload stands.
- **Keyless Android enrichment** starts on the same triggers as keyed enrichment
  today (`CatalogEnrichmentFetcher.kt` stops when no key is stored), and the
  Settings enrichment card says the art comes from the home cache server instead
  of "No TMDB key is stored." Titles the server lacks are retried on the next
  run.
- **Signing:** blob writes reuse the chunk scheme (`MGC1`, HMAC-SHA256 over
  method, path, a length field and the body's sha256) with the body length in
  the length field. There is no Rust signer outside the cache server crate
  today, so the signing moves into one small shared piece the server, the core
  and the CLI all use (the Kotlin and TypeScript signers stay as they are).
- The Android app passes the cache server address and token it already holds in
  Settings ("Home cache server") to the core when enrichment runs; the CLI reads
  them from its config (`cache_server`, `cache_server_token`), which the
  funstation script writes from the server's token.
- Search answers that came back empty are not stored on the server, matching
  `DiskCachedApi`, so a title added later is found.

## Left to the builder

- Route names and storage layout on the server (e.g. `/v1/tmdb/{sha256}` and
  `/v1/art/{size}/{name}`), and how the store's LRU index learns two new kinds.
- Whether images and JSON go through one generic blob store or two.
- Timeouts and the skip-after-failure window (the web chunk client uses 2 s and
  30 s).
- Test strategy; the shared signing test vector in `docs/running-the-player.md`
  should gain a blob example checked on both sides.
