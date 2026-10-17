# Lessons

Corrections worth not repeating. One entry per lesson: what happened, the rule
it produced. Written after a correction, per CLAUDE.md §3; when the same lesson
shows up in a second project, §4 says propose it as a CLAUDE.md rule.

Newest first.

---

## 2026-09-29 — Decouple media preparation from the upload queue to prevent idle upload bandwidth

**What happened.** When ingesting bulk TV series with `mediagram`, preparation (`mediagram prepare --mp4`) was coupled in lockstep with uploading per show (`prepare show A -> upload show A -> prepare show B -> upload show B`). Preparing 4K or multi-season shows with ffmpeg on network mounts took hours during which upload network bandwidth sat completely idle and nothing moved to Telegram. Furthermore, `--delete-source` was missing from `add-show`, leaving prepared files on disk after upload.

**Rule.** Decouple heavy local media preprocessing (remuxing, transcoding, container conversion) from upload queues. Run bulk preparation across the catalog upfront (with `prepare --delete-source` to prune original containers), and always pass `--delete-source` to `add-show` so uploaded files are purged incrementally as each part reaches the channel.

---

## 2026-09-23 — A schema bump checked the web reader and missed the CLI one

**What happened.** Moving the index to v7, the web player was kept reading v6
so a not-yet-upgraded uploader's channel snapshots still played. But
`mediagram posters --index <snapshot>`, which the web player runs after every
new snapshot, went through the uploader's strict opener and refused every v6
snapshot. New titles silently stopped getting covers; the user found it in
the player's log.

**Rule.** When the schema moves, list every reader of a snapshot *someone else
wrote* — web catalog, package refresh, channel install, and any CLI handed
`--index` — and hold each to `OLDEST_READABLE_SCHEMA`, not `SCHEMA_VERSION`.
Only the uploader's own index is held to the current schema, because only
that one can be migrated. Run the new code against a real older snapshot
before calling the bump done.

---

## 2026-09-18 — A fix applied to one of two parallel implementations

**What happened.** RFC 9110 §14.2 says a server must ignore a `Range` header in
a unit it does not understand. The player was fixed (1858b32); `mediagram serve`,
which decides the same thing in `serve/response.rs`, was not, and kept answering
400 for another day. Two tests asserted the wrong behaviour, so nothing failed.

**Rule.** The Rust `serve/` and the TypeScript `web/src/` implement the same
decisions twice, on purpose (`docs/system-architecture.md` §7). When a change
lands in one, grep the other for the same decision before calling it done — and
where the answer must match, pin it with a test that reads both, the way
`shared_playable_sql.rs` does for `PLAYABLE_SQL`.

---

## 2026-09-22 — A safety check that printed a live client and was run past anyway

**What happened.** Before a real-Telegram check, a `pgrep` listed the dev player
(`bun --watch run src/index.ts`) holding the `web/.env` key. The same command
went on to start a listener on that key, so two clients shared one auth key for
~30 s. The player survived (fresh fetch OK, 0 failed reads), but the measured
failure mode for a shared key is permanent until restart.

**Rule.** A pre-flight check must be able to stop the run: `pgrep … && exit 1`,
never a `pgrep` whose output is only read afterwards. And `bun --watch` means the
player is live and reloads on every edit under `web/src/`, so editing the player
while it runs already changes what the viewer is using.

## 2026-09-25 — Keep a reference design's labels meaning what they say
Mapped the reference's "Editor's Choice / Trending / Staff Pick" strip onto three genre
shelves because genres were the easiest real data to hand. The user rejected it: those
labels are *kinds of curation*, each spotlighting one title. Rule: when translating a
reference, keep each label's meaning and find a real signal that honestly backs it
(a user pin, TMDB popularity, rating). Don't swap in a different concept because the
data is convenient. If no honest signal exists, ask.

## 2026-10-09 — "The TV UI is ugly" asks for parity, not a redesign

I answered "the tv ui is still crazy ugly" by offering three new design directions and
building mockups. The user stopped it: they want the gaps between the current design
(web player, tablet) and the TV filled, not a new look. Rule: a look complaint about one
surface is a parity defect in that surface (CLAUDE.md § Surface Parity). Audit it screen by
screen against the reference surface and fix within the existing tokens, fonts and layouts,
adapting only for 10-foot/D-pad needs. Offer new directions only when asked for a redesign.

## 2026-10-18 — Kotlin regex passes JVM tests and crashes on Android

0.126.0's `ReportText.kt` used `Regex("(?U)\\s+")` to match JavaScript's Unicode `\s`. The
shared-fixture tests passed on the desktop JVM; on the tablet the first Send crashed the app,
because Android's regex is ICU, which rejects the `(?U)` flag, and a top-level `val` failing
takes the whole file down at first use. Rule: in Android code, write character classes out
(`[\s\x0B\p{Z}﻿]` is exactly JavaScript's `\s`) and avoid embedded flags other than
`(?i)`/`(?m)`/`(?s)`; JVM unit tests do not exercise Android's regex engine, so a device walk
is the check for any new pattern.
