# Research: Telegram API + Premium — what else could help mediagram

Date: 2026-09-22 22:12 · Scope: MTProto API (layer 227, what grammers 0.10 / teleproto ship) + Premium perks, judged against what mediagram already does.

## Executive summary

Premium perks the app can actually use, it already uses: 4 GB files (3.5 GiB parts) and no premium speed throttle. None of the headline Telegram video features apply. Server-side transcoding (`alt_documents`) is only for **big channels** and only for **real videos**. Our library is a private channel of raw byte-split parts. CDN, takeout, forum topics, the Bot API and transcription don't apply either.

What's left is small and specific:

1. **Bug (do now):** the Android core caches each part's `Document`, including its file reference, for as long as a set is open. Nothing ever evicts it. Telegram expires file references, so a film paused long enough can fail every read with a generic network error until another set is opened. Fix: on `FILE_REFERENCE_EXPIRED`, drop the cached entry, resolve the part again, retry once.
2. **Worth planning:** push updates (`updateNewChannelMessage`, `updatePinnedChannelMessages`) to replace manual "Update library" and the watch-state polling. Medium value, medium cost (gap recovery).
3. **Small add-on to the pending Settings plan:** `account.getAuthorizations` / `resetAuthorization` to list and revoke device sessions.
4. **Optional:** upload single-part sets as streamable videos with a TMDB `video_cover`. Official Telegram apps could then play the off-site mirror directly.
5. **Conditional:** keep several `upload.getFile` requests in flight at once. The 2026-09-20 measurements don't justify it yet. Revisit for high-bitrate 4K or slow seeks.

## Methodology

- 5 web searches + 1 fetch of core.telegram.org/api/files. Checked grammers-client 0.10 source and its `api.tl` (layer 227), plus repo code.
- Terms: alt_documents/HLS, FLOOD_PREMIUM_WAIT, file references/CDN, takeout, 2025–26 video features.

## What mediagram uses today (verified)

| Surface | Library | Calls |
|---|---|---|
| Uploader `crates/mediagram` | grammers 0.10 | `upload_stream` (4 parallel `saveBigFilePart` workers, `files.rs:408`), `send_message`, `pin_message`, `iter_messages`, `edit/delete`, `iter_download` (verify) |
| Android core `crates/mediagram-core` | grammers 0.10 | `iter_download` (512 KiB chunks, sequential, `cdn_supported:false`), `get_messages_by_id`, `auth.acceptLoginToken` |
| Web player `web/src/telegram` | teleproto 1.229 | `iterDownload`, `channels.getMessages`, pinned `#mlib-state` docs |

## Key findings

### 1. Premium: already fully exploited
- Premium removes `FLOOD_PREMIUM_WAIT_X` on upload **and** download. Free accounts only hit that throttle after "tens of GB" (core.telegram.org/api/files).
- Part cap: `upload_max_fileparts_premium` (8000 × 512 KiB ≈ 3.9 GiB). A 3.5 GiB part leaves headroom. Raising it to ~3.9 GiB saves about one part in ten on big films. Not worth a format change.
- Caveat: the throttle depends on **which account a device signs in with**. A family phone on a non-Premium account would be throttled after tens of GB. See Unresolved.
- Other Premium perks don't touch this app: transcription (voice/round messages only), translation, 4 accounts, 1000 channels, tags, stories, emoji.

### 2. Server-side video qualities (`alt_documents`): **not applicable**
- "When sending videos to **big channels**, Telegram will automatically convert them… multiple qualities" → shows up as `messageMediaDocument.alt_documents`. Clients must ignore it when `video_ignore_alt_documents` is set.
- Ruled out twice over: (a) a private one-member channel isn't "big"; (b) our parts are raw byte splits, not videos. Local ffmpeg transcoding (`web/src/transcode`) stays the only route to adaptive quality.

### 3. File references: **real gap**
- File references expire. Clients must catch `FILE_REFERENCE_EXPIRED`, fetch the message again and retry (core.telegram.org/api/file-references).
- Web: resolves the message on every read (`client.ts:84` `partMedia`), so it's safe.
- Rust `telegram.rs:56` resolves per response for the same reason. That's correct.
- **Android `api/read.rs:92` `document_for`** caches per set (a deliberate 546→443 ms win). The cache only clears when the set changes, and no error path evicts it. Nothing in the repo handles `FILE_REFERENCE_EXPIRED`.
- Scenario: pause a film for hours, resume. Every read fails as "the part could not be resolved"/Network, until another title is opened. How long a reference lives isn't documented, so this is plausible, not reproduced.

### 4. Parallel / pipelined downloads: **not yet**
- Telegram recommends a call queue with X parts in flight, capped by `large_queue_max_active_operations_count` per DC.
- Measured 2026-09-20: playback is latency-bound. 1.4 MB/s is sustained against ~1 MB/s of playback, and a 4 MiB blocking read-ahead made the first frame slower. Keeping 2–4 × 512 KiB requests in flight would cut seek and cold-start time without that penalty. There's no evidence yet that anyone needs it.
- Trigger to revisit: 4K/remux bitrates above ~10 Mbit/s, or seek-to-frame over ~3 s.

### 5. Push updates: **candidate**
- MTProto pushes `updateNewChannelMessage` (new parts, new index) and `updatePinnedChannelMessages` (index / `#mlib-state` pins change). Gaps are recovered with `updates.getChannelDifference`.
- Gains: the library refreshes itself after an upload finishes; watch state from another device arrives within seconds.
- Cost: a long-lived update loop on both cores, gap handling, and Android background limits (only while foregrounded). grammers has `next_update`; teleproto has event handlers.

### 6. Session management: fits the Settings plan
- `account.getAuthorizations` lists devices; `account.resetAuthorization(hash)` revokes one. That's a revoke-a-lost-device control for `plans/260922-2105-settings-menu-telegram-and-cache`. It's small because sign-in/out is already scoped there.

### 7. Streamable video + cover: **optional**
- Layer 227: `documentAttributeVideo{supports_streaming, preload_prefix_size, video_codec}`, `inputMediaUploadedDocument{video_cover, video_timestamp}`.
- Only single-part sets (films ≤ 3.5 GiB) are whole videos. Sending those with the video attribute plus a TMDB poster as `video_cover` makes the channel browsable and playable in official Telegram apps. That's a real fallback player for the "off-site mirror". The mlib caption and index are unaffected.
- Risks: Telegram may re-thumbnail or treat the message differently. Multi-part sets stay opaque, so the fallback is partial. Needs a test upload before committing.

### 8. Rejected

| API / feature | Why not |
|---|---|
| CDN DCs (`getCdnFile`) | Serves popular public files only; grammers hardcodes `cdn_supported:false` |
| Takeout session | Lowers flood limits for bulk export; no flood waits observed on rescan/verify at this library size |
| `messages.search` / hashtags | Already measured: new messages never found, `#mlib-state` matches `#mlib` (`state-channel.ts:14`) |
| Forum topics / multiple channels | The index already organises the library; no reader needs it |
| Bot API | 20 MB download cap (2 GB with a local server); strictly worse than MTProto |
| Native video progress / `video_timestamp` resume | Client-side in official apps, not exposed as sync state; our `#mlib-state` already does this across devices |
| Raise part size to ~3.9 GiB | Marginal gain; changes split math for new uploads only |

## Recommendations (ordered)

1. **Fix Android file-reference expiry.** In `document_for` / the read path, on a download error whose RPC name is `FILE_REFERENCE_EXPIRED`, evict `(set_id, message_id)`, re-resolve, retry once. Add a unit test on `DocumentCache` eviction. ~1–2 h. Patch bump.
2. **Settings plan:** add session list/revoke to its Telegram phase (web + Android, for parity).
3. **Brainstorm push updates** as its own plan; decide whether foreground-only on Android is enough.
4. **Optional experiment:** one smoke upload of a single-part film with `supports_streaming` + `video_cover`; check it plays in the official Android app and that `rescan`/adopt still parse it.
5. **Park** pipelined downloads until a measurement asks for them.

## Sources

- [Uploading and Downloading Files](https://core.telegram.org/api/files) — premium throttle, alt_documents, getFile rules, parallelism
- [File references](https://core.telegram.org/api/file-references)
- [messageMediaDocument](https://core.telegram.org/constructor/messageMediaDocument)
- [Encrypted CDNs](https://core.telegram.org/cdn) · [upload.getCdnFile](https://core.telegram.org/method/upload.getCdnFile)
- [Takeout API](https://core.telegram.org/api/takeout)
- [TechCrunch, Feb 2025 — video covers/timestamps](https://techcrunch.com/2025/02/13/telegram-releases-improved-sticker-search-and-video-consumption-features)
- [tdesktop #24700 — premium speed reports](https://github.com/telegramdesktop/tdesktop/issues/24700)
- Local: grammers-client 0.10 `src/client/files.rs`, grammers-tl-types 0.10 `tl/api.tl` (LAYER 227)

## Unresolved questions

- How long do file references on channel messages live in practice? It decides how urgent fix 1 is; a long-pause repro on the device would settle it.
- Does every device sign in with the Premium account? If not, does grammers/teleproto surface `FLOOD_PREMIUM_WAIT_X` as a retryable flood wait, or as a hard error?
- Does teleproto `iterDownload` pipeline requests internally? Not checked (node_modules is blocked by a hook).
