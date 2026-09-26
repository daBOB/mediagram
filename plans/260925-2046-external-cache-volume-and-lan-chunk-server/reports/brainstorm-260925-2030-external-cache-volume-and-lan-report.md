# Brainstorm — external cache: volume choice + LAN source

Date: 2026-09-25. Status: design approved by user.

## Problem

"External cache" = two gaps on Android:
1. Cache location hardcoded: `File(context.cacheDir, "mlib")` (`android/core/playback/src/main/kotlin/CacheProvider.kt:155`). Can't use SD/USB.
2. No shared cache: every Android device fetches from Telegram (latency-bound, ~1.4 MB/s, one round trip per read); bytes one device fetched are fetched again by the next.

## State found (scout)

- Plan `plans/260921-1751-android-external-cache/` + spec `docs/superpowers/specs/2026-09-21-android-external-cache-design.md`: all phases "Not started", roadmap says "Planned".
- Budget half already shipped differently in `b91e9e8`: live slider 512 MiB–8 GiB, `AdjustableLruEvictor`, `CacheBudgetSettings.kt` (plain prefs). Spec §4 presets + §7 "apply at restart" for budget are stale.
- `FLAG_IGNORE_CACHE_ON_ERROR` absent → full disk / yanked card kills playback.
- Spec §9 "TV has no source" stale: `feat/android-tv-ui` (54 commits, unmerged) has a TV UI, but no Settings screen.
- Server: `/api/sets/{id}/stream` (`web/src/routes.ts:21`) — Range, reads through server disk cache, no auth. `/cached-stream` is disk-only (thumbs), not for this.
- Android media URI `mlib://set/<id>` (`MlibDataSource.kt:25`) is the local cache key.
- `UnmeteredNetworkCheck` in `PreloadNetwork.kt` reusable.

## Requirements (user answers)

- Scope: both halves, sequenced. **Volume first**, then LAN.
- Done = works on Redmi (adb), Android TV box, phone/tablet with microSD. Fire TV / Chromecast not required.
- Server always on at home.
- Discovery: **mDNS auto** + manual override on phone.
- Keep local cache in front of LAN (two tiers).
- Slider cap **follows chosen volume** (capacity − 1 GiB floor); reverses the fixed 8 GiB cap from `b91e9e8` — user-confirmed 2026-09-25.

## Approaches considered

| Choice | Options | Picked | Why |
|---|---|---|---|
| Discovery | mDNS / manual URL / manual + TV Settings screen | mDNS + phone override | TV branch has no Settings screen; mDNS needs none |
| Order | LAN first / volume first | Volume first (user) | Finish existing plan first; LAN was recommended first for value — noted, user decided |
| Tiers | keep local cache / bypass on LAN | Keep | Seek-back, offline, preload stay uniform; budget bounds storage |
| Location swap | live rebuild / next start | Next start | Player graph is process singleton; set changed ~once |
| Volume access | SAF / MANAGE_EXTERNAL_STORAGE / getExternalCacheDirs | getExternalCacheDirs | SimpleCache needs `File`; no permission |

## Agreed design

### Phase 0 — fix + reconcile
- `FLAG_IGNORE_CACHE_ON_ERROR` on `CacheDataSource.Factory` (`PlayerFactory.kt`).
- Update spec §4/§7/§9, plan phases, roadmap row to match shipped live slider + TV branch reality.

### Phase 1 — volume choice
- `CacheVolumes.kt`: pure fn over `(dirs, stats)`; internal + `getExternalCacheDirs()[1..]`, nulls dropped; thin Context adapter.
- Volume id persisted beside budget in existing `CacheBudgetSettings` prefs (no separate `CacheSettings`).
- Location applies next start; budget stays live.
- Slider max = chosen volume capacity − 1 GiB floor (internal keeps effective 8 GiB unless room allows more — define exact rule in plan).
- Missing volume at start → internal fallback, keep recorded choice, System screen says so. Delete old dir only after new opens. No byte copy.
- UI: "Where" row in existing Settings cache block; reuse disabled-with-reason `MenuItem`.
- No new manifest permission.

### Phase 2 — `mediagram_cache` server (Rust, no Telegram session)
User decision 2026-09-25: own server instead of reusing web player (reason: **isolation** — independent lifecycle from web player). Earlier recommendation (reuse web server `/api/sets/{id}/stream`) declined. Web player keeps its own cache for now.
- New crate `crates/mediagram-cache`, binary `mediagram_cache`, own systemd unit on always-on home box.
- Dumb LAN byte store: no Telegram, no index, no UI; set ids opaque strings.
- API: `GET/HEAD/PUT /v1/sets/{id}/chunks/{n}`, `GET /v1/status` (held, budget, version).
- Fixed 1 MiB chunks (= 2 Telegram 512 KiB chunks), final chunk shorter; file per chunk `root/<set>/<n>`, temp+rename atomic; LRU eviction under configured budget.
- mDNS advertise `_mediagram-cache._tcp`.
- Write auth: **pairing token** (user choice). Server generates secret on first start, shows it as QR/text; PUT requires it; reads open.

### Phase 3 — Android LAN tier + discovery
- Android keeps Telegram session; uses server like internal cache (user: "the android app still handles the telegram session and uses mediagram_cache like the internal cache storage").
- `MlibDataSource.fetch` becomes chunk-aligned (fetch chunk containing `position`, serve offset). Today reads 1 MiB from arbitrary position (`MlibDataSource.kt:126`).
- `LanChunkStore` in front of `core.read`: GET hit → serve; miss → `core.read` → serve + async PUT (bounded queue). Short timeout, 60s down-flag, unmetered only.
- Local SimpleCache stays in front (two tiers). URI unchanged → cache keys, offline untouched. Series preload fills LAN server for free (same upstream).
- Discovery `NsdManager` + manual URL override + token entry in phone Settings. TV: automatic discovery after TV branch merges; token pairing on TV = open question.
- System row "Source: LAN (host) / Telegram".

## Risks

0. Chunk integrity unverifiable: index `sha256` is per part (GBs, `crates/mlib-spec/src/schema.rs:72`) → pairing token on PUT is the guard; a buggy client with the token can still poison. Consider size check vs set `total`.
1. Cleartext HTTP blocked by default (targetSdk 37); `network_security_config` can't scope to private IP ranges → likely app-wide cleartext permit. Other traffic is MTProto, not HTTP.
2. Possible Android 17 local-network permission for targetSdk 37 — verify at plan time; may need runtime prompt (LAN half only).
3. Set ids must match between Android and server index — shared channel index says yes; 404 → Telegram fallback.
4. Multicast-blocking routers → phone override; TV falls back to Telegram.
5. Volume half cannot be verified on the Redmi (no removable volume) → Robolectric + pure tests; real check on microSD phone / TV box.

## Success criteria

- Phase 0: yanked/full cache during playback → playback continues.
- Phase 1: on microSD device, cache on card, slider max reflects card; card removed → internal fallback + System screen notice; card back → choice restored.
- Phase 2: server unit tests: put/get/range of chunks, atomic write, LRU eviction under budget, token rejection, restart keeps index.
- Phase 3: Redmi on home Wi-Fi: second play of a title after clearing local cache = 0 Telegram fetches (counters) and System row says LAN; server stopped → Telegram within ~1s, no stall loop; mobile data → Telegram; seek-back still local-cache hit.
- Version bump computed at merge against main (all three manifests).

## Unresolved questions

- Exact slider-cap rule for internal storage (8 GiB fixed vs capacity-derived) — settle in plan.
- Android 17 local-network permission applicability — research in plan.
- Rust HTTP + mDNS crate choice (axum/hyper; `mdns-sd`) — check workspace deps in plan.
- Chunk index persistence: sqlite (rusqlite already in workspace?) vs rebuild from dir scan on start.
- TV token pairing flow (no TV Settings screen) — pair via phone, QR, or defer.
- Offline Android device with token revoked/rotated — rotation flow.
