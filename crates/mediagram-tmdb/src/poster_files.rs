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
/// A backdrop is held only at the width it was asked for or wider, because
/// its key carries no width: without a record of what landed, a device that
/// now wants a sharper backdrop would keep the blurry one for ever.
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
    step: impl FnMut(usize),
) -> Result<Vec<String>> {
    fetch_each(http, refs, dir, step, PosterRef::url).await
}

/// [`download_each`] with the URL of each image supplied by the caller, so a
/// test can serve images from a local socket instead of the CDN.
async fn fetch_each(
    http: &reqwest::Client,
    refs: &[PosterRef],
    dir: &Path,
    mut step: impl FnMut(usize),
    url_of: impl Fn(&PosterRef) -> String,
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
        if is_held(poster, dir) {
            written.push(poster.key.clone());
            continue;
        }
        match download(http, &url_of(poster), &dest).await {
            Ok(()) => {
                if let Some(width) = poster.backdrop_width
                    && let Err(err) =
                        write_private(&width_record(poster, dir), width.to_string().as_bytes())
                {
                    // Without the record the next run fetches it again,
                    // which costs a download and nothing else.
                    tracing::warn!(key = %poster.key, error = %err, "backdrop width not recorded");
                }
                written.push(poster.key.clone());
            }
            // A narrower image already here still serves; the record is
            // left as it was so the next run tries again.
            Err(err) if dest.exists() => {
                tracing::warn!(key = %poster.key, error = %err, "wider image not fetched, keeping the one held");
                written.push(poster.key.clone());
            }
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
    refs.iter().filter(|p| is_held(p, dir)).count()
}

/// The file recording the width a backdrop was asked for. The width asked
/// for, not the pixels received: TMDB's original may be narrower than the
/// request, and recording what arrived would refetch it on every run.
fn width_record(poster: &PosterRef, dir: &Path) -> std::path::PathBuf {
    dir.join(format!("{}.width", poster.key))
}

/// Whether `dir` already holds `poster` well enough to skip it. One rule for
/// the download walk and the count, so the two cannot disagree.
fn is_held(poster: &PosterRef, dir: &Path) -> bool {
    if !dir.join(format!("{}.jpg", poster.key)).exists() {
        return false;
    }
    let Some(wanted) = poster.backdrop_width else {
        return true;
    };
    std::fs::read_to_string(width_record(poster, dir))
        .ok()
        .and_then(|held| held.trim().parse::<u32>().ok())
        .is_some_and(|held| held >= wanted)
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
///
/// Written beside `path` and renamed into place: an image that is replaced
/// may be on screen or mid-read, and must never be seen half-written. The
/// process id keeps two runs from sharing one temporary file.
fn write_private(path: &Path, bytes: &[u8]) -> Result<()> {
    let name = path.file_name().and_then(|n| n.to_str()).unwrap_or("image");
    let tmp = path.with_file_name(format!(".{name}.{}.tmp", std::process::id()));
    let written = (|| -> Result<()> {
        // The temporary file is always new to this call, so `mode` applies.
        let mut file = std::fs::OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .mode(0o600)
            .open(&tmp)
            .with_context(|| format!("creating {}", tmp.display()))?;
        file.write_all(bytes)
            .with_context(|| format!("writing {}", tmp.display()))?;
        std::fs::rename(&tmp, path).with_context(|| format!("replacing {}", path.display()))
    })();
    if written.is_err() {
        let _ = std::fs::remove_file(&tmp);
    }
    written
}

/// The same, for a directory, which needs the execute bit to be enterable.
fn restrict_dir(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o700))
        .with_context(|| format!("restricting {}", path.display()))
}

#[cfg(test)]
#[path = "poster_files_tests.rs"]
mod tests;
