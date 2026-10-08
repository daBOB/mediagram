//! The caption on an Android app release (spec §7a): a marker line, then one
//! line of JSON naming the version and how to check the APK it carries.
//!
//! ```text
//! #mlib-app v=1
//! {"version":"0.93.0","code":93000,"bytes":47185920,"sha256":"…","published_at":1790900000,
//!  "profile":{"message":4201,"bytes":30412,"sha256":"…"}}
//! ```
//!
//! `profile` is optional and names the APK's dex metadata: a separate
//! channel document Android takes beside the APK to compile the app at
//! install. A reader that predates it skips the field and installs the APK
//! alone, so it never needed a new marker.
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

/// The caption for `release`.
///
/// # Panics
/// Never, in practice: the body is only strings and integers, which always
/// serialize.
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
