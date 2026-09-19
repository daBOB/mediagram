# Phase 0: grammers-on-Android spike

**Context:** [plan.md](plan.md) · [spec §6](../../docs/superpowers/specs/2026-09-19-android-foundation-design.md)

## Overview

- **Priority:** Blocking. Every other phase depends on this answer.
- **Status:** Not started.
- **Deliverable:** an answer, not code. Anything built here is throwaway and is labelled so.

Prove that `grammers` cross-compiles for `aarch64-linux-android`, runs on a
real device, authenticates, and reads a byte range that matches ground truth.

**This phase is not TDD.** A spike's output is a finding. Steps are
investigative with decision points, and the gate at the end is go/no-go.

## Key insights

- Android is Linux. A `cargo-ndk` binary can be `adb push`ed to
  `/data/local/tmp` and run directly, so this phase needs **no Gradle project
  and no Android app**.
- `crates/mediagram/Cargo.toml:36` records that `rusqlite` is deliberately not
  `bundled`, because `grammers-session` statically links its own sqlite3 and
  two copies collide at link time. That resolution depends on a system
  `libsqlite3`. The Android NDK exposes none. This is the expected failure.
- `crates/mediagram/src/telegram/string_session.rs` already exports an auth key
  as a portable string, which proves the key is extractable from grammers'
  storage — a hint for resolutions that drop `SqliteSession` entirely.

## Steps 8 and 9 are cancelled

Decided 2026-09-19: **the Android client logs in itself, in the app.** It never
borrows the uploader's auth key, so steps 8 and 9 — which pushed that key to a
device — are withdrawn rather than deferred.

What settled it is measured, and already in this codebase.
`crates/mediagram/src/commands/export_session.rs` records that a player on an
exported session answered 3/3 range requests alone, then 0/3 from the moment
`mediagram serve` started, "and never recovered — not when the other client
stopped, not at all, until the player was restarted". The same file's `run`
calls `Tg::connect`, so `export-session` is itself a client, not a passive
read of a local file. `docs/running-the-player.md` states the rule plainly: a
host running the uploader and a player "needs two keys, not one shared", and
`bun run login` is run "once per player host".

So the runtime proof moves into the app, where it belongs: it happens once the
client can authenticate on its own — the surface in
[phase 1](phase-01-mediagram-core-and-uniffi.md) task 7, the flow in
[phase 3](phase-03-login-and-catalog-mobile.md) task 4. That is the better
test, because it exercises the real client rather than a harness.

This phase therefore answers the linking question only, which was its
architectural purpose. Nothing on the developer's machine needs stopping.

## Related code files

- Create: `crates/mediagram-core/Cargo.toml`, `crates/mediagram-core/src/bin/probe.rs`
- Modify: `Cargo.toml` (workspace members)
- Read for context: `crates/mediagram/src/telegram/client.rs`, `crates/mediagram/src/serve/stream.rs`

## Implementation steps

- [ ] **Step 1: Install the toolchain**

```bash
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
# NDK r27 or later — earlier versions do not default to 16 KB alignment.
sdkmanager --install "ndk;27.2.12479018"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
```

- [ ] **Step 2: Create the crate as a workspace member**

`crates/mediagram-core/Cargo.toml`:

```toml
[package]
name = "mediagram-core"
version.workspace = true
edition.workspace = true
license.workspace = true
rust-version.workspace = true

[dependencies]
mlib-spec = { path = "../mlib-spec" }
grammers-client = "0.10"
grammers-session = "0.10"
grammers-tl-types = "0.10"
glass_pumpkin = "=2.0.0-rc0"
sha2 = { workspace = true }
hex = { workspace = true }
tokio = { version = "1.53.1", features = ["rt-multi-thread", "macros", "io-util", "net", "time"] }
anyhow = "1"
```

Add `"crates/mediagram-core"` to `members` in the root `Cargo.toml`.

- [ ] **Step 3: Write the probe**

`crates/mediagram-core/src/bin/probe.rs` — throwaway. Reads a byte range and
prints its digest.

```rust
//! Throwaway spike binary: proves grammers connects and reads a range on
//! Android. Deleted once phase 0 reports its finding.

use anyhow::{Context, Result};
use sha2::{Digest, Sha256};
use std::env;

#[tokio::main(flavor = "multi_thread", worker_threads = 2)]
async fn main() -> Result<()> {
    let session_path = env::var("PROBE_SESSION").context("PROBE_SESSION")?;
    let api_id: i32 = env::var("PROBE_API_ID")?.parse()?;
    let api_hash = env::var("PROBE_API_HASH")?;
    let chat_id: i64 = env::var("PROBE_CHAT_ID")?.parse()?;
    let message_id: i32 = env::var("PROBE_MESSAGE_ID")?.parse()?;
    let want: u64 = 1024 * 1024;

    let started = std::time::Instant::now();
    let client = connect(&session_path, api_id, &api_hash).await?;
    let bytes = first_bytes(&client, chat_id, message_id, want).await?;

    let mut hasher = Sha256::new();
    hasher.update(&bytes);
    println!("read {} bytes in {} ms", bytes.len(), started.elapsed().as_millis());
    println!("sha256 {}", hex::encode(hasher.finalize()));
    Ok(())
}
```

`connect` and `first_bytes` are written against grammers 0.10 by copying the
client construction in `crates/mediagram/src/telegram/client.rs` and the
download loop in `crates/mediagram/src/serve/stream.rs`. Keep the file under
200 lines; split if it grows.

- [ ] **Step 4: Prove the probe on the host first**

Do not debug Android and the probe at the same time.

```bash
PROBE_SESSION=~/.local/share/mediagram/session.sqlite \
PROBE_API_ID=... PROBE_API_HASH=... PROBE_CHAT_ID=... PROBE_MESSAGE_ID=... \
cargo run -p mediagram-core --bin probe
```

Expected: a byte count, an elapsed time and a digest.

- [ ] **Step 5: Cross-compile, and record exactly what breaks**

```bash
cargo ndk -t arm64-v8a build -p mediagram-core --bin probe --release
```

Expected: a link failure naming sqlite3 symbols, duplicate or undefined.
**Record the verbatim error in the report** — it is the finding.

- [ ] **Step 6: Work the resolutions in order, stopping at the first that links**

1. **Drop `SqliteSession`.** Find whether `grammers-session` 0.10 offers a
   non-sqlite store, or whether a `Session` can be built from an auth key
   directly (`crates/mediagram/src/telegram/string_session.rs` shows the key
   is extractable, so the reverse may be constructible). If so, the probe
   holds its session as a plain file and links no sqlite at all.
2. **One sqlite for both.** Make `rusqlite`'s `bundled` copy the only one, so
   `grammers-session` links against it rather than its own.
3. **No `rusqlite` in the core.** Read the package catalog without it.

Record which one worked and why the others did not.

- [ ] **Step 7: Check 16 KB alignment**

```bash
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" \
  -lW target/aarch64-linux-android/release/probe | grep LOAD
```

Expected: `Align` of `0x4000`. If it is `0x1000`, add to
`.cargo/config.toml`:

```toml
[target.aarch64-linux-android]
rustflags = ["-C", "link-arg=-Wl,-z,max-page-size=16384"]
```

- [ ] **Step 8: Run it on a real device**

```bash
adb push target/aarch64-linux-android/release/probe /data/local/tmp/
adb push ~/.local/share/mediagram/session.sqlite /data/local/tmp/   # if step 6 kept a session file
adb shell chmod 755 /data/local/tmp/probe
adb shell "PROBE_SESSION=/data/local/tmp/session.sqlite PROBE_API_ID=... \
  PROBE_API_HASH=... PROBE_CHAT_ID=... PROBE_MESSAGE_ID=... /data/local/tmp/probe"
```

If execution from `/data/local/tmp` is blocked on the device, fall back to a
minimal Android app that loads the code as a library. Record which was needed.

Delete the pushed session afterwards: `adb shell rm /data/local/tmp/session.sqlite`.

- [ ] **Step 9: Compare against ground truth**

The device's digest must match the same range served by the verified Rust
path on the host:

```bash
mediagram serve &                      # on the host
curl -s -r 0-1048575 http://127.0.0.1:PORT/api/sets/<set-id>/stream | sha256sum
```

Both digests must be identical. A match is the finding; a mismatch is a bug
to chase before anything is declared to work.

- [ ] **Step 10: Write the report and delete the probe**

Report to
`plans/260919-0034-android-foundation-phone-tablet-tv/reports/spike-260919-grammers-on-android-report.md`:
the verbatim link error, which resolution worked, the alignment result, the
two digests, the elapsed time, and the go/no-go.

```bash
git rm crates/mediagram-core/src/bin/probe.rs
git add plans/260919-0034-android-foundation-phone-tablet-tv/reports/
git commit -m "docs(android): record the grammers-on-Android spike finding"
```

Keep `crates/mediagram-core/Cargo.toml` and the workspace entry — phase 1
builds on them.

## Todo list

- [ ] Toolchain installed, NDK r27+
- [ ] Crate created, workspace member added
- [ ] Probe written and passing on the host
- [ ] Cross-compile attempted, error recorded verbatim
- [ ] sqlite resolution found and recorded
- [ ] 16 KB alignment confirmed
- [ ] Probe runs on a real device
- [ ] Device digest matches host ground truth
- [ ] Report written, probe deleted

## Success criteria

A `mediagram-core` binary built for `aarch64-linux-android`, 16 KB aligned,
runs on a real Android device, authenticates against the real channel, and
reads a 1 MiB range whose SHA-256 equals the one the verified host path
produces for the same range.

## Risk assessment

| Risk | Mitigation |
|---|---|
| No sqlite resolution links | Three candidates in step 6. If all fail: **STOP**. The standalone approach is dead; re-brainstorm against TDLib. Do not start phase 2. |
| `/data/local/tmp` execution blocked | Fall back to a minimal library-loading app. Costs a day, changes nothing architecturally. |
| grammers' TLS or crypto misbehaves on bionic | Surfaces here rather than after the Gradle build exists — the whole point of running phase 0 first. |
| Auth key conflict corrupts an upload | Step "Safety": stop uploads, do not run the web player concurrently. |

## Security considerations

The probe handles the account's auth key in plaintext on a device and in
environment variables. It is throwaway and deleted in step 10, and the pushed
session is removed from the device in step 8. Never commit a session file, an
`api_hash`, or a chat id.

## Next steps

PASS → [phase 1](phase-01-mediagram-core-and-uniffi.md) and
[phase 2](phase-02-gradle-skeleton-and-ci.md), which are independent of each
other. FAIL → stop; the fallback needs its own brainstorm.
