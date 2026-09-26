//! Writing posters to disk: downloading each within a size and time limit,
//! into a directory only its owner can read.

use std::io::Write;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::Path;

use anyhow::{Context, Result, bail};

use crate::posters::{POSTER_MAX_BYTES, POSTER_TIMEOUT, PosterRef};

/// Downloads each poster into `dir` as `<key>.jpg`, returning the keys that
/// landed, in the order they were asked for.
///
/// A poster already on disk is left alone: the command that fills a local
/// directory is run again every time the library grows, and refetching what
/// is already held would make the common case the slow one. Delete the
/// directory to fetch it all again.
///
/// A poster that will not download is skipped rather than fatal. The catalog
/// is the product; the artwork is a convenience, and one unreachable image
/// must never cost a run that has already done real work.
pub async fn download_into(
    http: &reqwest::Client,
    refs: &[PosterRef],
    dir: &Path,
) -> Result<Vec<String>> {
    download_each(http, refs, dir, |_| {}).await
}

/// [`download_into`], calling `step(done)` before each image so a caller
/// watching a terminal can say how far it has got.
pub async fn download_each(
    http: &reqwest::Client,
    refs: &[PosterRef],
    dir: &Path,
    mut step: impl FnMut(usize),
) -> Result<Vec<String>> {
    if refs.is_empty() {
        return Ok(Vec::new());
    }
    std::fs::create_dir_all(dir).with_context(|| format!("creating {}", dir.display()))?;
    restrict_dir(dir)?;

    let mut written = Vec::new();
    for (done, poster) in refs.iter().enumerate() {
        step(done);
        // The key becomes a file name, a manifest path and a tar member
        // name, so it is checked here rather than trusted from upstream.
        if !mlib_spec::package::poster_key_is_valid(&poster.key) {
            tracing::warn!(key = %poster.key, "poster key rejected");
            continue;
        }
        let dest = dir.join(format!("{}.jpg", poster.key));
        if dest.exists() {
            written.push(poster.key.clone());
            continue;
        }
        match download(http, &poster.url(), &dest).await {
            Ok(()) => written.push(poster.key.clone()),
            Err(err) => {
                tracing::warn!(key = %poster.key, error = %err, "poster skipped");
            }
        }
    }
    Ok(written)
}

/// How many of `refs` are already on disk in `dir`, so a caller can say what
/// it actually did rather than reporting every poster as freshly fetched.
pub fn already_held(refs: &[PosterRef], dir: &Path) -> usize {
    refs.iter()
        .filter(|p| dir.join(format!("{}.jpg", p.key)).exists())
        .count()
}

async fn download(http: &reqwest::Client, url: &str, dest: &Path) -> Result<()> {
    let response = http
        .get(url)
        .timeout(POSTER_TIMEOUT)
        .send()
        .await
        .context("requesting poster")?;
    let response = response
        .error_for_status()
        .context("poster request failed")?;
    if let Some(len) = response.content_length()
        && len > POSTER_MAX_BYTES
    {
        bail!("poster is {len} bytes, over the {POSTER_MAX_BYTES} byte limit");
    }
    let bytes = response.bytes().await.context("reading poster body")?;
    if bytes.len() as u64 > POSTER_MAX_BYTES {
        bail!("poster body exceeded the {POSTER_MAX_BYTES} byte limit");
    }
    write_private(dest, &bytes)
}

/// Artwork is written for one account's library and nobody else's, so a
/// poster is created owner-only rather than restricted after it lands.
fn write_private(path: &Path, bytes: &[u8]) -> Result<()> {
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o600)
        .open(path)
        .with_context(|| format!("creating {}", path.display()))?;
    // `mode` applies only when the file is created; one left by an earlier
    // run keeps whatever it had.
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600))
        .with_context(|| format!("restricting {}", path.display()))?;
    file.write_all(bytes)
        .with_context(|| format!("writing {}", path.display()))
}

/// The same, for a directory, which needs the execute bit to be enterable.
fn restrict_dir(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o700))
        .with_context(|| format!("restricting {}", path.display()))
}
