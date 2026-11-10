//! Writing posters to disk: downloading each within a size and time limit,
//! into a directory only its owner can read.

use std::path::Path;

use anyhow::{Context, Result, bail};

use crate::home_cache::{HomeCache, art_path};
use crate::posters::{POSTER_MAX_BYTES, POSTER_TIMEOUT, PosterRef};
use crate::private_files::{restrict_dir, write_private};

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
/// With a home cache server, an image it holds is read from it instead of the
/// CDN, an image fetched from the CDN is uploaded to it, and an image already
/// on disk is uploaded if the server lacks it, so one download serves every
/// player in the house.
///
/// A poster that will not download is skipped rather than fatal. The catalog
/// is the product; the artwork is a convenience, and one unreachable image
/// must never cost a run that has already done real work.
pub async fn download_into(
    http: &reqwest::Client,
    refs: &[PosterRef],
    dir: &Path,
    home: Option<&HomeCache>,
) -> Result<Vec<String>> {
    download_each(http, refs, dir, home, |_| {}).await
}

/// [`download_into`], calling `step(done)` before each image so a caller
/// watching a terminal can say how far it has got.
pub async fn download_each(
    http: &reqwest::Client,
    refs: &[PosterRef],
    dir: &Path,
    home: Option<&HomeCache>,
    step: impl FnMut(usize),
) -> Result<Vec<String>> {
    fetch_each(http, refs, dir, home, step, PosterRef::url).await
}

/// [`download_each`] with the URL of each image supplied by the caller, so a
/// test can serve images from a local socket instead of the CDN.
async fn fetch_each(
    http: &reqwest::Client,
    refs: &[PosterRef],
    dir: &Path,
    home: Option<&HomeCache>,
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
        let art = art_path(&poster.size(), &poster.path);
        if is_held(poster, dir) {
            // Uploaded under the width the file has, not the one asked for:
            // the server keeps the first write, so a wider image filed under
            // a narrower name would serve there for good.
            let held_size = match poster.width {
                None => Some(poster.size()),
                Some(_) => held_width(poster, dir).map(|w| format!("w{w}")),
            };
            if let (Some(home), Some(size)) = (home, held_size) {
                let held_art = art_path(&size, &poster.path);
                if !home.has(&held_art).await
                    && let Ok(bytes) = std::fs::read(&dest)
                {
                    home.put(&held_art, &bytes).await;
                }
            }
            written.push(poster.key.clone());
            continue;
        }
        let from_server = match home {
            Some(home) => home
                .get(&art)
                .await
                .filter(|b| !b.is_empty() && b.len() as u64 <= POSTER_MAX_BYTES),
            None => None,
        };
        let stored = match &from_server {
            Some(bytes) => Ok(bytes.clone()),
            None => download(http, &url_of(poster)).await,
        }
        .and_then(|bytes| write_private(&dest, &bytes).map(|()| bytes));
        match stored {
            Ok(bytes) => {
                if let Some(width) = poster.width
                    && let Err(err) =
                        write_private(&width_record(poster, dir), width.to_string().as_bytes())
                {
                    // Without the record the next run fetches it again,
                    // which costs a download and nothing else.
                    tracing::warn!(key = %poster.key, error = %format_args!("{err:#}"), "image width not recorded");
                }
                if let (Some(home), None) = (home, &from_server) {
                    home.put(&art, &bytes).await;
                }
                written.push(poster.key.clone());
            }
            // A narrower image already here still serves; the record is
            // left as it was so the next run tries again.
            Err(err) if dest.exists() => {
                tracing::warn!(key = %poster.key, error = %format_args!("{err:#}"), "wider image not fetched, keeping the one held");
                written.push(poster.key.clone());
            }
            Err(err) => {
                tracing::warn!(key = %poster.key, error = %format_args!("{err:#}"), "poster skipped");
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

/// The file recording the width an image was asked for. The width asked
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
    let Some(wanted) = poster.width else {
        return true;
    };
    held_width(poster, dir).is_some_and(|held| held >= wanted)
}

/// The width recorded for the image held, if any.
fn held_width(poster: &PosterRef, dir: &Path) -> Option<u32> {
    std::fs::read_to_string(width_record(poster, dir))
        .ok()
        .and_then(|held| held.trim().parse().ok())
}

async fn download(http: &reqwest::Client, url: &str) -> Result<Vec<u8>> {
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
    Ok(bytes.to_vec())
}

#[cfg(test)]
#[path = "poster_files_tests.rs"]
mod tests;

#[cfg(test)]
#[path = "poster_files_home_tests.rs"]
mod home_tests;
