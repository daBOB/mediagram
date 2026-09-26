---
phase: 3
title: "mediagram_cache server"
status: done
priority: P1
effort: "1.5d"
dependencies: []
---

# Phase 3: `mediagram_cache` server

## Overview
A new workspace binary: a dumb LAN chunk store. It has no Telegram
session, no index and no UI; set ids are opaque strings to it. It runs as
its own systemd service on the always-on home box. The user chose a
separate server over reusing the web player for **isolation**: its
lifecycle is independent of the web player's.

## Key insights (research report)
- `axum 0.8.9` is a dependency of `crates/mediagram` only (not in
  `[workspace.dependencies]`); `tokio` is a workspace dependency. Promote
  axum to the workspace table so both crates share one version. The new
  dependencies are `mdns-sd` and `hmac`
  (`sha2` 0.11 and `hex` 0.4 are already workspace dependencies).
- mDNS: `mdns-sd` coexists with avahi-daemon. The alternative, if it
  misbehaves on the box, is a static `/etc/avahi/services/mediagram-cache.service`
  file, which is documented as a fallback rather than built.
- The LRU index lives in memory and is rebuilt from a directory scan at
  start, ordered by file mtime (about 100 ms per 100k chunks). A GET
  updates mtime, so the order survives restarts. There is no SQLite.
- Integrity cannot be checked per chunk (the index `sha256` is per
  multi-GB part), so the guards are the pairing token on writes and a
  per-set total that chunk lengths must agree with.

## API (v1)
| Method | Path | Auth | Result |
|---|---|---|---|
| GET | `/v1/sets/{id}/chunks/{n}` | none | 200 bytes / 404 |
| HEAD | same | none | 200 + `Content-Length` / 404 |
| PUT | same, header `X-Set-Total: <bytes>` | signed, see below | 201 stored / 200 already held (first write wins) / 400 bad length / 401 / 409 total mismatch / 413 over 1 MiB |
| GET | `/v1/status` | none | `{"version","held_bytes","budget_bytes","chunks"}` |

- `id` matches `^[A-Za-z0-9]{1,64}$`, the same shape as the web
  player's `STREAM_PATH`. `n` is a `u32`. Anything else gets a 404 before
  any filesystem call, so no path traversal is possible.
- Length rule: `len == CHUNK`, or `n*CHUNK + len == total` for the final
  chunk, and always `n*CHUNK < total`.
- The first PUT of a set records `total` in `root/<id>/total` using
  `create_new` (O_EXCL); a later PUT with a different total gets a 409.
- **Concurrent writes** (a phone preloading while a TV plays the same
  episode):
  - each PUT writes a **unique** temp name (seq or random);
  - publishing is no-overwrite (`hard_link` then unlink the temp, or
    `renameat2(RENAME_NOREPLACE)`), so the loser sees "exists" and returns
    200;
  - a per-key in-process mutex is fine as well.
- A GET whose chunk file has vanished (for example after a manual `rm` of a
  set) returns 404 and drops the index entry. Deleting a set by hand is
  therefore a valid remedy while the server runs.

### Write authentication: signed, token never sent
<!-- Updated: Validation Session 1 - HMAC signing replaces the bearer token -->
- Header `Authorization: MGC1 <hex>`, where the value is
  `HMAC-SHA256(token, "PUT\n/v1/sets/{id}/chunks/{n}\n{X-Set-Total}\n" + hex(sha256(body)))`.
- The server recomputes it and compares in constant time
  (`hmac::Mac::verify_slice`). A mismatch or missing header gets a 401.
- The token never crosses the wire, so cleartext HTTP and a look-alike
  server on another network learn nothing. A replayed PUT can only
  rewrite the identical chunk, which first-write-wins already ignores, so
  no nonce or timestamp is needed.
- The signature also binds the body, so a PUT altered in transit is
  rejected. (Reads stay open and unsigned; see the risks.)

## Architecture
```
crates/mediagram-cache/
  src/main.rs      config load → token ensure → store open (scan) → mdns register → axum serve; SIGTERM: stop accepting, finish in-flight PUTs
  src/config.rs    TOML (--config) + MEDIAGRAM_CACHE_{LISTEN,ROOT,BUDGET,MDNS}; defaults 0.0.0.0:7788, $CACHE_DIRECTORY or ~/.cache/mediagram-cache, 64 GiB, mdns=true
  src/store.rs     ChunkStore { get, head, put, status }, LRU BTreeMap<(mtime,seq),Key>, evict-to-budget after put; temp in root/.tmp then rename
  src/rules.rs     id/n validation, length rule, total check (pure)
  src/token.rs     ensure(root/token 0600, 32 random bytes hex via getrandom), `verify(sig, method, path, total, body)` via hmac; `mediagram_cache token` prints the token for pairing
  src/http.rs      axum Router, DefaultBodyLimit(1 MiB), signature check on PUT (needs the full body, which is ≤ 1 MiB and already buffered)
  src/mdns.rs      register _mediagram-cache._tcp.local., TXT v=1
  tests/*.rs       via tower::ServiceExt::oneshot on the Router + temp dir store
  mediagram-cache.service   systemd: DynamicUser=yes, CacheDirectory=mediagram-cache, StateDirectory for token
```
The token lives in `StateDirectory` rather than `CacheDirectory`, so
clearing the cache does not unpair every device.

## Related code files
- Create: everything above; add `crates/mediagram-cache` to workspace `members`.
- Modify: root `Cargo.toml` (`[workspace.dependencies]` `mdns-sd`, `hmac`, and `axum` promoted; dev `tower` util).
- Modify: `scripts/check.sh` only if it lists crates explicitly (it runs `--all`, so probably not).
- Modify: `docs/system-architecture.md`, `docs/running-the-player.md` (a "Home cache server" section: install, token, avahi fallback).

## Implementation steps (TDD, new code)
1. Scaffold the crate, an empty `main`, and the workspace membership.
   `cargo build -p mediagram-cache` passes.
2. `rules.rs` red then green: valid and invalid ids (traversal `..`, 65
   chars, `/`), and the length rule for full, final, oversize and
   past-total chunks.
3. `store.rs` red then green (temp dir):
   - put then get round trips;
   - a second put is a no-op, not an overwrite;
   - **two concurrent puts of the same key** with different bodies give
     one intact body, never interleaved;
   - **two concurrent first puts** with different totals give one 201 and
     one 409;
   - a chunk file removed behind the store's back → GET 404 and the index
     entry is dropped;
   - a put with a different total gets a 409;
   - a crash mid-write (temp file left in `.tmp`) is not visible and is
     removed on open;
   - eviction goes to budget with the oldest first, and a get refreshes
     the order;
   - reopening rebuilds `held_bytes` and the order from disk.
4. `token.rs` red then green: created with mode 0600 and stable across
   `ensure` calls. A signature over a known vector verifies; a changed
   body, path, total or key is rejected; a missing header is rejected.
   Publish **one shared test vector** (token, request, expected hex) in
   the docs, which the Android test also asserts, so the two sides cannot
   drift.
5. `http.rs` red then green (oneshot): each row of the API table, including
   the 401/409/413/400/404 paths; a HEAD has no body; a PUT of exactly
   1 MiB succeeds and 1 MiB + 1 gets a 413.
6. `config.rs` red then green: env overrides TOML, and the defaults apply.
7. `mdns.rs`: a thin wrapper with no unit test. Verify manually with
   `avahi-browse -rt _mediagram-cache._tcp` on the dev box, alongside
   avahi-daemon.
8. Service file plus docs.
9. **Regression gate:** `cargo clippy --all-targets --all-features -- -D warnings`,
   `cargo test --all`, and the rest of `scripts/check.sh`.
10. Commit: `feat(cache-server): a LAN chunk store Android devices share`.

## Success criteria
- [x] Every API row covered by a test.
- [x] A restart keeps chunks, order and the token.
- [x] `avahi-browse` sees the service while avahi-daemon runs.
- [x] Files under 200 lines, and clippy is clean.

## Risk assessment
- **Poisoning by a token holder with a bug.** Bounded by the length and
  total rules. A right-length wrong-bytes chunk is **not** detected:
  Android plays it and errors (see phase 4). The remedy is removing the
  set's directory on the server. Accepted.
- **PUT flood by a token holder** evicts everything. Only paired devices
  can write, so this is accepted.
- **Budget default of 64 GiB** could be too large for the box. It is set
  in config and shown in status.
- **mdns-sd vs avahi conflicts:** the static avahi service file is the
  documented fallback, and `mdns=false` turns ours off.
