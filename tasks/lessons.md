# Lessons

Corrections worth not repeating. One entry per lesson: what happened, the rule
it produced. Written after a correction, per CLAUDE.md §3; when the same lesson
shows up in a second project, §4 says propose it as a CLAUDE.md rule.

Newest first.

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
