# mediagram-cache server — implementation report

Work context: `/home/andre/Workspace/mediagram-cache-server` (worktree, branch `feat/mediagram-cache-server`)
Spec: `plans/260925-2046-external-cache-volume-and-lan-chunk-server/phase-03-mediagram-cache-server.md`

## Summary

Implemented `crates/mediagram-cache` (binary `mediagram_cache`) strictly TDD, following
phase steps 1-10 in order. All 9 success-criteria items met: every API row has a test,
restart keeps chunks/order/token, avahi-browse sees the service, files are all under
200 lines, clippy is clean workspace-wide.

## Files

Created (`crates/mediagram-cache/`):
- `Cargo.toml`, `mediagram-cache.service`
- `src/lib.rs`, `src/main.rs`
- `src/rules.rs` + `src/rules_tests.rs` — id/length pure rules
- `src/store.rs` + `src/store/{index,scan,total}.rs` + `src/store_tests.rs` + `src/store_lru_tests.rs` — chunk store
- `src/token.rs` + `src/token_tests.rs` — MGC1 HMAC pairing
- `src/http.rs` — axum router/handlers
- `src/config.rs` + `src/config_tests.rs` — TOML + env config
- `src/mdns.rs` — mdns-sd advertisement (no unit test, manually verified)
- `tests/api.rs`, `tests/api_rejections.rs`, `tests/common/mod.rs` — tower::oneshot integration tests

Modified: root `Cargo.toml` (workspace members + `axum`/`mdns-sd`/`hmac` in
`[workspace.dependencies]`), `crates/mediagram/Cargo.toml` (axum now `.workspace = true`),
`Cargo.lock`, `docs/system-architecture.md` (§12), `docs/running-the-player.md` ("Home
cache server" section).

## Tests

- `cargo test -p mediagram-cache`: 47 lib tests + 14 integration tests (5 in `api.rs`, 9
  in `api_rejections.rs`) = 61, all pass.
- `cargo test --all` (whole workspace): 0 failures.
- `cargo clippy --all-targets --all-features -- -D warnings` (whole workspace): clean.
- mDNS verified manually: ran the binary on port 17788 against a temp root, saw
  `avahi-browse -rt _mediagram-cache._tcp` resolve `mediagram-cache._mediagram-cache._tcp.local`
  with `port=17788`, `txt=["v=1"]` on the real LAN interface, alongside avahi-daemon.
  Process killed afterward, temp dirs removed.

## Shared HMAC test vector (also in `docs/running-the-player.md` and
`token_tests.rs`)

```
token:     00112233445566778899aabbccddeeff00112233445566778899aabbccddee
method:    PUT
path:      /v1/sets/abc123/chunks/0
total:     5
body:      "hello" (5 ASCII bytes)
signature: 5b6d16159fbd287ed1da02570ef266f83790c52de8ec1eb0d2a1500ed34aef18
```
HMAC key is the ASCII bytes of the hex-encoded token string (not the decoded 32 bytes) —
this is the one design choice the spec left implicit; documented in `token.rs` and pinned
by the vector so Android's implementation must match it, not guess it.

## Deviations from the spec / design choices made

- **mdns-sd version**: spec/research said "0.18+"; latest stable is 0.21.4, used that.
- **hmac version**: spec/research said `hmac = "0.12"`; 0.12 depends on `digest 0.10`,
  which does not unify with workspace `sha2 0.11` (which uses `digest 0.11`). Used
  `hmac = "0.13"` instead, which depends on `digest 0.11` — documented with a comment in
  the workspace `Cargo.toml`.
- **CHUNK size**: spec's phase text doesn't state the byte value directly; resolved via
  cross-referencing phase 2/4 of the same plan (`CHUNK_BYTES = 1 shl 20` on the Android
  side) — used `1 MiB` (1,048,576), distinct from the web player's own unrelated 512 KiB
  `CACHE_CHUNK` for its browser-side disk cache.
- **`tests/*.rs` layout**: spec's architecture block lists `tests/*.rs` for the
  oneshot/API tests; the API test file exceeded 200 lines as one file, so it's split into
  `tests/api.rs` (happy paths) + `tests/api_rejections.rs` (4xx paths) sharing
  `tests/common/mod.rs` — same test count and coverage, just under the line cap.
- **`store.rs` split**: exceeded 200 lines as a single file; split into `store.rs`
  (public `ChunkStore` API) + `store/index.rs` (pure LRU bookkeeping) + `store/scan.rs`
  (startup directory walk) + `store/total.rs` (total-pairing `create_new` logic). Not
  named in the spec's file list, but keeps every file under the cap without changing the
  public surface.
- Everything else (API table, HMAC scheme, directory layout, systemd unit shape,
  DynamicUser + CacheDirectory/StateDirectory) follows the spec exactly.

## Unresolved

None blocking. Note for whoever writes phase 4 (Android client): the HMAC key is the
ASCII bytes of the *hex string* token, not its decoded 32 bytes — match the vector above
exactly to catch a mismatch early.

**Status:** DONE
