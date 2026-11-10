//! The caption on an Android app release (spec §7a): a marker line, then one
//! line of JSON naming the version and how to check the APK it carries.
//!
//! ```text
//! #mlib-app v=1
//! {"version":"0.93.0","code":93000,"bytes":47185920,"sha256":"…","published_at":1790900000,
//!  "profile":{"message":4201,"bytes":30412,"sha256":"…"}}
//! ```
//!
//! The marker's tag names the app: `#mlib-app` the media app, `#spieltisch-app`
//! the games. Readers and writers name the tag they mean, and a caption with
//! another tag is not a release to them.
//!
//! `profile` is optional and names the APK's dex metadata: a separate
//! channel document Android takes beside the APK to compile the app at
//! install. A reader that predates it skips the field and installs the APK
//! alone, so it never needed a new marker.
//!
//! Written by `mediagram publish-app` and read by the app's core, so both
//! take the spelling from here.

use serde::{Deserialize, Serialize};

/// The media app's tag. Media apps already on devices read only this one and
/// take the highest versionCode among its posts, so no other app may use it.
pub const MEDIA_APP_TAG: &str = "#mlib-app";

/// Spieltisch's tag, its own so a media app never takes a games release for
/// its update.
pub const SPIELTISCH_TAG: &str = "#spieltisch-app";

/// What follows the tag in the marker this version of the spec writes, and
/// the only one it reads: a later format may mean something this build would
/// install wrongly.
const VERSION: &str = " v=1";

/// One release, as its caption declares it. `code` is the APK's versionCode,
/// `bytes` and `sha256` describe the attached document exactly.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AppRelease {
    pub version: String,
    pub code: i64,
    pub bytes: u64,
    pub sha256: String,
    pub published_at: i64,
    #[serde(default, skip_serializing_if = "Option::is_none", deserialize_with = "any_profile")]
    pub profile: Option<AppProfile>,
}

/// A release's dex-metadata document: its message in the same channel, and
/// how to check it, as for the APK.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AppProfile {
    pub message: i64,
    pub bytes: u64,
    pub sha256: String,
}

/// A `profile` of any shape reads as `None` rather than failing the whole
/// caption: the APK is the release, the profile only speeds up its install.
fn any_profile<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<Option<AppProfile>, D::Error> {
    let value = serde_json::Value::deserialize(deserializer)?;
    Ok(serde_json::from_value(value).ok())
}

/// The caption for `release` under the app's `tag`.
///
/// # Panics
/// Never, in practice: the body is only strings and integers, which always
/// serialize.
#[must_use]
pub fn render(tag: &str, release: &AppRelease) -> String {
    let json = serde_json::to_string(release).expect("strings and integers always serialize");
    format!("{tag}{VERSION}\n{json}")
}

/// The release a caption declares, or `None` for anything that is not a
/// complete, well-formed v1 release caption under the app's `tag`.
#[must_use]
pub fn parse(tag: &str, caption: &str) -> Option<AppRelease> {
    let (marker, json) = caption.split_once('\n')?;
    if marker.trim_end().strip_prefix(tag) != Some(VERSION) {
        return None;
    }
    let mut release: AppRelease = serde_json::from_str(json.trim()).ok()?;
    // An unusable profile costs only the install-time compile, never the release.
    release.profile = release
        .profile
        .filter(|p| p.message > 0 && p.bytes > 0 && crate::subtitle_bundle::valid_sha256(&p.sha256));
    let sound = !release.version.is_empty()
        && release.code > 0
        && release.bytes > 0
        && crate::subtitle_bundle::valid_sha256(&release.sha256);
    sound.then_some(release)
}

/// Which of `candidates` — `(caption, message id)` — is the app's newest
/// release under `tag`: the highest versionCode, a tie going to the later
/// message. Its position in `candidates` and the release itself, or `None`
/// when none is a release of that app.
#[must_use]
pub fn newest(tag: &str, candidates: &[(&str, i64)]) -> Option<(usize, AppRelease)> {
    candidates
        .iter()
        .enumerate()
        .filter_map(|(index, (text, id))| parse(tag, text).map(|release| (index, *id, release)))
        .max_by_key(|(_, id, release)| (release.code, *id))
        .map(|(index, _, release)| (index, release))
}

#[cfg(test)]
#[path = "app_caption_tests.rs"]
mod tests;
