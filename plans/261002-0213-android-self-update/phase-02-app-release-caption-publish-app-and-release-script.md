# Phase 02 — `#mlib-app` caption, `publish-app`, release script

## Context
- Spec § Components › Caption, Uploader. Global Constraints in `plan.md`.
- Template: `crates/mlib-spec/src/index_caption.rs` (+ `_tests.rs`); spec prose `docs/mlib-spec.md` §7 (`:514`), `#mlib-subs` section (`:433`).
- Uploader: `ChannelRemote` (`crates/mediagram/src/channel_index/remote.rs`), `TelegramRemote::new(&tg, max_attempts)`, `upload::lock::acquire_file`, `clock::now_unix`, integration fake `tests/support/channel.rs` (`FakeChannel`, `Channel::post(caption, document, pinned, own_post) -> i32`).
- Pins are safe: the uploader unpins only index pins it recorded (`index/pins.rs`), the core filters by `index_caption::is_index`, the web by `INDEX_MARKER`.
- Every source file under `crates/` stays under 200 lines (`tests/code_standards.rs`).

## Task 2.1: The caption format in `mlib-spec`

**Files:**
- Create: `crates/mlib-spec/src/app_caption.rs`
- Create: `crates/mlib-spec/src/app_caption_tests.rs`
- Modify: `crates/mlib-spec/src/lib.rs` (add `pub mod app_caption;` after `pub mod caption_codec;` — keep the list alphabetical: insert before `pub mod caption;`)
- Modify: `docs/mlib-spec.md` (new section after §7)

**Interfaces:**
- Produces: `mlib_spec::app_caption::{PREFIX, MARKER, AppRelease{version: String, code: i64, bytes: u64, sha256: String, published_at: i64}, render(&AppRelease) -> String, parse(&str) -> Option<AppRelease>, newest(&[(&str, i64)]) -> Option<(usize, AppRelease)>}`

- [ ] **Step 1: Failing tests** — `crates/mlib-spec/src/app_caption_tests.rs`

```rust
use super::*;

fn release(code: i64) -> AppRelease {
    AppRelease {
        version: format!("0.{code}.0"),
        code,
        bytes: 47_185_920,
        sha256: "a".repeat(64),
        published_at: 1_790_900_000,
    }
}

#[test]
fn a_rendered_caption_reads_back() {
    let caption = render(&release(93_000));
    assert!(caption.starts_with("#mlib-app v=1\n"));
    assert_eq!(parse(&caption), Some(release(93_000)));
}

#[test]
fn captions_that_are_not_a_release_are_refused() {
    let good = render(&release(93_000));
    let json = good.split_once('\n').unwrap().1;
    assert_eq!(parse("#mlib-index v=2\n{\"pushed_at\":1}"), None);
    assert_eq!(parse(&format!("#mlib-app v=2\n{json}")), None, "a later format is not read as this one");
    assert_eq!(parse("#mlib-app v=1"), None, "no JSON line");
    assert_eq!(parse("#mlib-app v=1\n{\"version\":\"0.93.0\"}"), None, "missing fields");
    for broken in [
        AppRelease { code: 0, ..release(93_000) },
        AppRelease { bytes: 0, ..release(93_000) },
        AppRelease { sha256: "xyz".into(), ..release(93_000) },
        AppRelease { version: String::new(), ..release(93_000) },
    ] {
        assert_eq!(parse(&render(&broken)), None, "{broken:?}");
    }
}

#[test]
fn newest_is_the_highest_version_code_then_the_highest_message() {
    let older = render(&release(92_000));
    let newer = render(&release(93_000));
    let candidates = [(older.as_str(), 10), ("#mlib-index v=2\n{}", 11), (newer.as_str(), 9)];
    let (index, chosen) = newest(&candidates).unwrap();
    assert_eq!((index, chosen.code), (2, 93_000));

    let again = [(newer.as_str(), 9), (newer.as_str(), 12)];
    assert_eq!(newest(&again).unwrap().0, 1, "a tie goes to the later message");
    assert_eq!(newest(&[("#mlib-index v=2\n{}", 1)]), None);
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cargo test -p mlib-spec app_caption`
Expected: FAIL — `app_caption` not found.

- [ ] **Step 3: Implement** — `crates/mlib-spec/src/app_caption.rs`

```rust
//! The caption on an Android app release (spec §7a): a marker line, then one
//! line of JSON naming the version and how to check the APK it carries.
//!
//! ```text
//! #mlib-app v=1
//! {"version":"0.93.0","code":93000,"bytes":47185920,"sha256":"…","published_at":1790900000}
//! ```
//!
//! Written by `mediagram publish-app` and read by the app's core, so both
//! take the spelling from here.

use serde::{Deserialize, Serialize};

/// What every app release caption starts with, whatever its version.
pub const PREFIX: &str = "#mlib-app";

/// The marker this version of the spec writes, and the only one it reads: a
/// later format may mean something this build would install wrongly.
pub const MARKER: &str = "#mlib-app v=1";

/// One release, as its caption declares it. `code` is the APK's versionCode,
/// `bytes` and `sha256` describe the attached document exactly.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AppRelease {
    pub version: String,
    pub code: i64,
    pub bytes: u64,
    pub sha256: String,
    pub published_at: i64,
}

/// The caption for `release`.
#[must_use]
pub fn render(release: &AppRelease) -> String {
    let json = serde_json::to_string(release).expect("strings and integers always serialize");
    format!("{MARKER}\n{json}")
}

/// The release a caption declares, or `None` for anything that is not a
/// complete, well-formed v1 release caption.
#[must_use]
pub fn parse(caption: &str) -> Option<AppRelease> {
    let (marker, json) = caption.split_once('\n')?;
    if marker.trim_end() != MARKER {
        return None;
    }
    let release: AppRelease = serde_json::from_str(json.trim()).ok()?;
    let sound = !release.version.is_empty()
        && release.code > 0
        && release.bytes > 0
        && crate::subtitle_bundle::valid_sha256(&release.sha256);
    sound.then_some(release)
}

/// Which of `candidates` — `(caption, message id)` — is the newest release:
/// the highest versionCode, a tie going to the later message. Its position
/// in `candidates` and the release itself, or `None` when none is a release.
#[must_use]
pub fn newest(candidates: &[(&str, i64)]) -> Option<(usize, AppRelease)> {
    candidates
        .iter()
        .enumerate()
        .filter_map(|(index, (text, id))| parse(text).map(|release| (index, *id, release)))
        .max_by_key(|(_, id, release)| (release.code, *id))
        .map(|(index, _, release)| (index, release))
}

#[cfg(test)]
#[path = "app_caption_tests.rs"]
mod tests;
```

Add `pub mod app_caption;` to `crates/mlib-spec/src/lib.rs` as the first `pub mod` line (alphabetical order).

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mlib-spec app_caption`
Expected: 3 passed.

- [ ] **Step 5: Spec prose** — in `docs/mlib-spec.md`, after §7 (the index document), add:

```markdown
## 7a. App release

The Android app's own releases travel in the library channel. Each is one
message: the signed APK as a document, captioned

    #mlib-app v=1
    {"version":"0.93.0","code":93000,"bytes":47185920,"sha256":"<64 lowercase hex>","published_at":1790900000}

`code` is the APK's versionCode, `bytes` and `sha256` its exact size and
digest. `mediagram publish-app` pins the newest release and unpins the one it
replaces; it never touches an index pin, and index readers skip this caption
because it does not start with `#mlib-index`. A reader takes the pinned,
channel-posted release with the highest `code` (a tie goes to the later
message), installs it only over a lower versionCode, and only after the
downloaded file matches `bytes` and `sha256`. A caption whose marker is not
exactly `#mlib-app v=1`, or whose JSON lacks a field, is not a release.
```

- [ ] **Step 6: Commit** (no bump yet — the phase bumps once, in Task 2.4)

```bash
git add crates/mlib-spec/src/app_caption.rs crates/mlib-spec/src/app_caption_tests.rs crates/mlib-spec/src/lib.rs docs/mlib-spec.md
git commit -m "feat(spec): the #mlib-app caption for Android app releases"
```

## Task 2.2: Reading an APK's identity with aapt2

**Files:**
- Create: `crates/mediagram/src/app_release/mod.rs`
- Create: `crates/mediagram/src/app_release/badging.rs`
- Create: `crates/mediagram/src/app_release/badging_tests.rs`
- Modify: `crates/mediagram/src/lib.rs` (add `pub mod app_release;`)

**Interfaces:**
- Produces: `mediagram::app_release::badging::{Badging{package: String, version_code: i64, version_name: String}, parse(&str) -> anyhow::Result<Badging>, read(&Path) -> anyhow::Result<Badging>}`

- [ ] **Step 1: Failing tests** — `crates/mediagram/src/app_release/badging_tests.rs`

```rust
use super::*;

const LINE: &str = "package: name='com.mediagram.android' versionCode='93000' versionName='0.93.0' platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'\nsdkVersion:'24'\n";

#[test]
fn the_package_line_is_read() {
    let badging = parse(LINE).unwrap();
    assert_eq!(badging.package, "com.mediagram.android");
    assert_eq!(badging.version_code, 93_000);
    assert_eq!(badging.version_name, "0.93.0");
}

#[test]
fn output_without_a_usable_package_line_is_an_error() {
    assert!(parse("sdkVersion:'24'\n").is_err());
    assert!(parse("package: name='x' versionCode='abc' versionName='1'\n").is_err());
    assert!(parse("package: name='x' versionName='1'\n").is_err());
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cargo test -p mediagram badging`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement**

`crates/mediagram/src/app_release/mod.rs`:
```rust
//! Publishing the Android app's own releases to the library channel (spec §7a).

pub mod badging;
pub mod publish;
```
(`publish` arrives in Task 2.3; until then write only `pub mod badging;` and add the line there.)

`crates/mediagram/src/app_release/badging.rs`:
```rust
//! What `aapt2 dump badging` says about an APK: the package, versionCode and
//! versionName Android itself will read when it installs it.

use std::path::Path;

use anyhow::{Context, Result, bail};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Badging {
    pub package: String,
    pub version_code: i64,
    pub version_name: String,
}

/// Runs `aapt2 dump badging` on `apk`. `aapt2` comes from the Android SDK's
/// build-tools; `scripts/release-android.sh` puts them on `PATH`.
pub fn read(apk: &Path) -> Result<Badging> {
    let output = std::process::Command::new("aapt2")
        .args(["dump", "badging"])
        .arg(apk)
        .output()
        .context("running aapt2 (put the Android SDK's build-tools on PATH)")?;
    if !output.status.success() {
        bail!("aapt2 could not read {}: {}", apk.display(), String::from_utf8_lossy(&output.stderr));
    }
    parse(&String::from_utf8_lossy(&output.stdout))
}

/// The first `package:` line of aapt2's output. Keys are matched with a
/// leading space, so `versionName` never matches inside
/// `platformBuildVersionName`, nor `name` inside `compileSdkVersionCodename`.
pub fn parse(output: &str) -> Result<Badging> {
    let line = output
        .lines()
        .find(|line| line.starts_with("package:"))
        .context("aapt2 printed no package line")?;
    let field = |key: &str| -> Result<String> {
        let marker = format!(" {key}='");
        let start = line.find(&marker).with_context(|| format!("no {key} in: {line}"))? + marker.len();
        let end = line[start..].find('\'').with_context(|| format!("unterminated {key} in: {line}"))?;
        Ok(line[start..start + end].to_string())
    };
    Ok(Badging {
        package: field("name")?,
        version_code: field("versionCode")?.parse().context("versionCode is not a number")?,
        version_name: field("versionName")?,
    })
}

#[cfg(test)]
#[path = "badging_tests.rs"]
mod tests;
```
Add `pub mod app_release;` to `crates/mediagram/src/lib.rs` (alphabetical position).

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mediagram badging`
Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram/src/app_release crates/mediagram/src/lib.rs
git commit -m "feat(uploader): read an APK's package and version with aapt2"
```

## Task 2.3: Publishing a release to the channel

**Files:**
- Create: `crates/mediagram/src/app_release/publish.rs`
- Create: `crates/mediagram/tests/app_release_publish.rs`
- Modify: `crates/mediagram/src/app_release/mod.rs` (add `pub mod publish;`)

**Interfaces:**
- Consumes: `mlib_spec::app_caption::{render, parse, newest, AppRelease}`; `Badging`; `ChannelRemote::{candidates, send_document, pin, unpin}`.
- Produces: `mediagram::app_release::publish::{APP_PACKAGE, publish(remote: &impl ChannelRemote, apk: &[u8], badging: &Badging, now: i64) -> anyhow::Result<i32>}`

- [ ] **Step 1: Failing integration tests** — `crates/mediagram/tests/app_release_publish.rs`

```rust
//! Sending an app release to the channel, against an in-memory channel.

mod support;

use mediagram::app_release::badging::Badging;
use mediagram::app_release::publish::publish;
use mlib_spec::app_caption;
use support::channel::FakeChannel;

fn apk(code: i64) -> Badging {
    Badging { package: "com.mediagram.android".into(), version_code: code, version_name: format!("0.{}.0", code / 1000) }
}

#[tokio::test]
async fn a_release_is_sent_captioned_and_pinned() {
    let channel = FakeChannel::new();
    let id = publish(&channel, b"apk bytes", &apk(93_000), 1_790_900_000).await.unwrap();
    channel.with(|c| {
        let message = c.messages.iter().find(|m| m.id == id).unwrap();
        assert!(message.pinned);
        let release = app_caption::parse(&message.caption).unwrap();
        assert_eq!((release.code, release.bytes, release.version.as_str()), (93_000, 9, "0.93.0"));
        assert_eq!(release.sha256, hex::encode(<sha2::Sha256 as sha2::Digest>::digest(b"apk bytes")));
    });
    assert_eq!(channel.document(id), b"apk bytes");
}

#[tokio::test]
async fn the_previous_release_is_unpinned_and_the_index_pin_left_alone() {
    let channel = FakeChannel::new();
    let index = channel.with(|c| c.post("#mlib-index v=2\n{\"pushed_at\":1}".into(), Some(vec![1]), true, true));
    let first = publish(&channel, b"one", &apk(92_000), 1).await.unwrap();
    let second = publish(&channel, b"two", &apk(93_000), 2).await.unwrap();
    channel.with(|c| {
        let pinned = |id| c.messages.iter().find(|m| m.id == id).unwrap().pinned;
        assert!(pinned(index), "the index stays pinned");
        assert!(!pinned(first), "the replaced release is unpinned");
        assert!(pinned(second));
    });
}

#[tokio::test]
async fn a_version_code_not_above_the_newest_release_is_refused() {
    let channel = FakeChannel::new();
    publish(&channel, b"one", &apk(93_000), 1).await.unwrap();
    let sends = channel.with(|c| c.sends);
    let error = publish(&channel, b"same", &apk(93_000), 2).await.unwrap_err();
    assert!(error.to_string().contains("93000"), "{error}");
    assert_eq!(channel.with(|c| c.sends), sends, "nothing was sent");
}

#[tokio::test]
async fn another_package_is_refused() {
    let channel = FakeChannel::new();
    let other = Badging { package: "com.example.other".into(), ..apk(93_000) };
    assert!(publish(&channel, b"x", &other, 1).await.is_err());
    assert_eq!(channel.with(|c| c.sends), 0);
}
```
(`hex` and `sha2` are already dependencies of `crates/mediagram`; `apk(93_000)` names version `0.93.0`.)

- [ ] **Step 2: Run, expect a compile failure**

Run: `cargo test -p mediagram --test app_release_publish`
Expected: FAIL — `publish` not found.

- [ ] **Step 3: Implement** — `crates/mediagram/src/app_release/publish.rs`

```rust
//! Sending a signed APK to the channel as the newest app release (spec §7a).

use anyhow::{Result, bail};
use mlib_spec::app_caption::{self, AppRelease};
use sha2::{Digest, Sha256};

use super::badging::Badging;
use crate::channel_index::remote::ChannelRemote;

/// The only package this command sends: the app that will install it.
pub const APP_PACKAGE: &str = "com.mediagram.android";

const APK_MIME: &str = "application/vnd.android.package-archive";

/// Sends `apk`, pins it, and unpins the releases it replaces; returns its
/// message id. Refuses another package, and a versionCode not above the
/// newest release already in the channel — Android would refuse to install
/// it over that one anyway.
pub async fn publish(remote: &impl ChannelRemote, apk: &[u8], badging: &Badging, now: i64) -> Result<i32> {
    if badging.package != APP_PACKAGE {
        bail!("this APK is {}, not {APP_PACKAGE}", badging.package);
    }
    let candidates = remote.candidates().await?;
    let posted: Vec<(&str, i64)> = candidates
        .iter()
        .filter(|candidate| candidate.own_post)
        .map(|candidate| (candidate.caption.as_str(), i64::from(candidate.id)))
        .collect();
    if let Some((_, newest)) = app_caption::newest(&posted) {
        if badging.version_code <= newest.code {
            bail!(
                "the channel already has {} (versionCode {}); this APK is {} (versionCode {})",
                newest.version, newest.code, badging.version_name, badging.version_code
            );
        }
    }

    let release = AppRelease {
        version: badging.version_name.clone(),
        code: badging.version_code,
        bytes: apk.len() as u64,
        sha256: hex::encode(Sha256::digest(apk)),
        published_at: now,
    };
    let name = format!("mediagram-{}.apk", release.version);
    let id = remote.send_document(apk, &name, APK_MIME, &app_caption::render(&release)).await?;
    remote.pin(id).await?;

    // A replaced release left pinned is harmless — readers take the highest
    // versionCode — so a refused unpin is reported, not fatal.
    for old in candidates.iter().filter(|c| c.id != id && app_caption::parse(&c.caption).is_some()) {
        if let Err(err) = remote.unpin(old.id).await {
            tracing::warn!(old_id = old.id, error = %err, "could not unpin a replaced app release");
        }
    }
    Ok(id)
}
```
Add `pub mod publish;` to `crates/mediagram/src/app_release/mod.rs`. If `channel_index::remote` is not `pub` from the crate root, use the path the `channel_index` tests use (`mediagram::channel_index::remote::ChannelRemote` — `pub mod remote;` is at `channel_index/mod.rs:20`).

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mediagram --test app_release_publish`
Expected: 4 passed.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram/src/app_release crates/mediagram/tests/app_release_publish.rs
git commit -m "feat(uploader): send an app release to the channel, pinned, replacing the previous one"
```

## Task 2.4: `mediagram publish-app` and `scripts/release-android.sh`

**Files:**
- Create: `crates/mediagram/src/commands/publish_app.rs`
- Create: `scripts/release-android.sh`
- Modify: `crates/mediagram/src/cli.rs` (new `Cmd` variant), `crates/mediagram/src/main.rs` (dispatch arm), `crates/mediagram/src/commands/mod.rs` (`pub mod publish_app;`)
- Modify: `README.md` § "The Android app" (`:432-447`)

- [ ] **Step 1: The command** — `crates/mediagram/src/commands/publish_app.rs`

```rust
//! `mediagram publish-app <apk>`: the newest Android app release, sent to the
//! library channel and pinned, for every installed release build to pick up.

use std::path::Path;

use anyhow::{Context, Result};

use crate::app_release::{badging, publish::publish};
use crate::channel_index::TelegramRemote;
use crate::clock::now_unix;
use crate::config::Config;
use crate::telegram::client::Tg;
use crate::upload::lock;

/// Two releases from this machine at once would race for the pin.
const LOCK_FILE: &str = "publish-app.lock";

pub async fn run(cfg: &Config, apk: &Path) -> Result<()> {
    let badging = badging::read(apk)?;
    let bytes = std::fs::read(apk).with_context(|| format!("reading {}", apk.display()))?;
    let _lock = lock::acquire_file(&cfg.data_dir()?.join(LOCK_FILE), || {
        println!("waiting for another publish-app on this machine to finish");
    })
    .await?;

    let tg = Tg::connect(cfg).await?;
    let remote = TelegramRemote::new(&tg, cfg.max_attempts);
    let result = publish(&remote, &bytes, &badging, now_unix()).await;
    tg.shutdown().await;
    let id = result?;
    println!(
        "published {} (versionCode {}, {} bytes) as message {id}, pinned",
        badging.version_name, badging.version_code, bytes.len()
    );
    Ok(())
}
```

- [ ] **Step 2: Wire it.** In `crates/mediagram/src/cli.rs`, add to `Cmd` after `PushIndex(PushIndexArgs),`:

```rust
    /// Send a signed Android APK to the channel as the newest app release, pinned
    PublishApp {
        /// The release APK; `scripts/release-android.sh` builds and checks it first
        apk: std::path::PathBuf,
    },
```
In `crates/mediagram/src/main.rs`, after the `Cmd::PushIndex` arm:
```rust
        Cmd::PublishApp { apk } => commands::publish_app::run(&cfg, &apk).await,
```
In `crates/mediagram/src/commands/mod.rs`: `pub mod publish_app;` (alphabetical).

- [ ] **Step 3: Build and see the help**

Run: `cargo run -q -p mediagram -- publish-app --help`
Expected: usage line `mediagram publish-app <APK>`.

- [ ] **Step 4: The release script** — `scripts/release-android.sh` (then `chmod +x`)

```bash
#!/usr/bin/env bash
# Builds the Android release APK, proves it carries this machine's release
# key, and publishes it to the library channel, where every installed
# release build picks it up on its own. Only this machine holds the key.
set -euo pipefail
cd "$(dirname "$0")/.."

EXPECTED_CERT=5840181d3da5f44c9f0185bf4ac3345e5fe08786347350db04aaa6e69df4f576
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
BUILD_TOOLS=$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)
export PATH="$BUILD_TOOLS:$PATH"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$SDK/ndk/28.2.13676358}"

scripts/build-android-core.sh
(cd android && ./gradlew -q :app:assembleRelease)
APK=android/app/build/outputs/apk/release/app-release.apk

CERT=$(apksigner verify --print-certs "$APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
if [ "$CERT" != "$EXPECTED_CERT" ]; then
  echo "refusing to publish: $APK is signed by '${CERT:-nothing}', not the release key" >&2
  exit 1
fi
aapt2 dump badging "$APK" | head -1

# From source, so the command always matches this checkout (the installed
# uploader can lag main).
cargo run -q --release -p mediagram -- publish-app "$APK"
```

- [ ] **Step 5: README** — in "The Android app" section, after the `installDebug` instructions, add:

```markdown
### Releasing to every device

    scripts/release-android.sh

builds the release APK (minified, arm64 + armv7), checks it is signed with
this machine's release key (`~/.config/mediagram/release.keystore`, set up
once — see `plans/261002-0213-android-self-update/phase-01-…`), and sends it
to the library channel pinned as `#mlib-app`. Every device running a release
build downloads it on its own and installs it the next time the app goes to
the background. Debug and benchmark builds never update themselves.
```

- [ ] **Step 6: Bump (minor) by pattern, check, commit**

```bash
V=$(grep -m1 -oE '^version = "[0-9.]+"' Cargo.toml | grep -oE '[0-9.]+' | awk -F. '{print $1"."$2+1".0"}')
sed -i -E '0,/^version = "[0-9.]+"/s//version = "'$V'"/' Cargo.toml
sed -i -E '0,/"version": "[0-9.]+"/s//"version": "'$V'"/' web/package.json
sed -i -E 's/versionName = "[0-9.]+"/versionName = "'$V'"/' android/app/build.gradle.kts
cargo update --workspace --offline -q
scripts/check.sh
git add crates/mediagram scripts/release-android.sh README.md Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts
git commit -m "feat(uploader): publish-app sends the Android release to the channel; release-android.sh builds, checks the key and publishes; release $V"
```

## Success criteria
`cargo test -p mlib-spec -p mediagram` green; `publish-app --help` works; the script refuses an APK signed with another key (try it once with the benchmark APK path swapped in — expect the refusal message and no send).
