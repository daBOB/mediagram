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
    if let Some((_, newest)) = app_caption::newest(&posted)
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
            tracing::warn!(old_id = old.id, error = %format_args!("{err:#}"), "could not unpin a replaced app release");
        }
    }
    Ok(id)
}
