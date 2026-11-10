//! Sending a signed APK to the channel as the newest app release (spec §7a).

use anyhow::{Result, bail};
use mlib_spec::app_caption::{self, AppProfile, AppRelease};
use sha2::{Digest, Sha256};

use super::badging::Badging;
use crate::channel_index::remote::ChannelRemote;

/// The apps this command sends, by the package the APK declares: the tag its
/// captions carry, and the name its files go out under.
const APPS: [(&str, &str, &str); 2] = [
    ("com.mediagram.android", app_caption::MEDIA_APP_TAG, "mediagram"),
    ("com.mediagram.spieltisch", app_caption::SPIELTISCH_TAG, "spieltisch"),
];

const APK_MIME: &str = "application/vnd.android.package-archive";

const PROFILE_MIME: &str = "application/octet-stream";

/// Sends `apk`, pins it, and unpins the releases of the same app it
/// replaces; returns its message id. Refuses a package not in [`APPS`], and a
/// versionCode not above the app's newest release already in the channel —
/// Android would refuse to install it over that one anyway.
///
/// `profile`, the APK's dex metadata, goes first as a document of its own,
/// unpinned, which the APK's caption then names.
pub async fn publish(
    remote: &impl ChannelRemote,
    apk: &[u8],
    profile: Option<&[u8]>,
    badging: &Badging,
    now: i64,
) -> Result<i32> {
    let Some(&(_, tag, file)) = APPS.iter().find(|(package, ..)| *package == badging.package) else {
        bail!("this APK is {}, not one of {:?}", badging.package, APPS.map(|(package, ..)| package));
    };
    let candidates = remote.candidates().await?;
    let posted: Vec<(&str, i64)> = candidates
        .iter()
        .filter(|candidate| candidate.own_post)
        .map(|candidate| (candidate.caption.as_str(), i64::from(candidate.id)))
        .collect();
    if let Some((_, newest)) = app_caption::newest(tag, &posted)
        && badging.version_code <= newest.code
    {
        bail!(
            "the channel already has {} (versionCode {}); this APK is {} (versionCode {})",
            newest.version,
            newest.code,
            badging.version_name,
            badging.version_code
        );
    }

    let profile = match profile {
        Some(dm) => {
            let name = format!("{file}-{}.dm", badging.version_name);
            let message = remote.send_document(dm, &name, PROFILE_MIME, "").await?;
            Some(AppProfile {
                message: i64::from(message),
                bytes: dm.len() as u64,
                sha256: hex::encode(Sha256::digest(dm)),
            })
        }
        None => None,
    };
    let release = AppRelease {
        version: badging.version_name.clone(),
        code: badging.version_code,
        bytes: apk.len() as u64,
        sha256: hex::encode(Sha256::digest(apk)),
        published_at: now,
        profile,
    };
    let name = format!("{file}-{}.apk", release.version);
    let id = remote
        .send_document(apk, &name, APK_MIME, &app_caption::render(tag, &release))
        .await?;
    remote.pin(id).await?;

    // A replaced release left pinned is harmless — readers take the highest
    // versionCode — so a refused unpin is reported, not fatal. The other
    // app's release must stay pinned: it is how its devices find updates.
    for old in candidates
        .iter()
        .filter(|c| c.id != id && app_caption::parse(tag, &c.caption).is_some())
    {
        if let Err(err) = remote.unpin(old.id).await {
            tracing::warn!(old_id = old.id, error = %format_args!("{err:#}"), "could not unpin a replaced app release");
        }
    }
    Ok(id)
}
