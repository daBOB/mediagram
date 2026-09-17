# Lessons

Corrections worth not repeating. One entry per lesson: what happened, the rule
it produced. Written after a correction, per CLAUDE.md §3; when the same lesson
shows up in a second project, §4 says propose it as a CLAUDE.md rule.

Newest first.

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
