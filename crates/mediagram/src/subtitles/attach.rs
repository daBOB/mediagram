//! Giving a finished set its subtitle bundle: probe, pick, extract, send,
//! record.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use mlib_spec::subtitle_bundle::{
    BUNDLE_VERSION, Bundle, MAX_COMPRESSED_BYTES, SUBS_MIME_TYPE, bundle_file_name, encode,
    render_caption,
};
use rusqlite::Connection;
use sha2::{Digest, Sha256};

use super::arrange::{Extracted, Origin, arrange};
use super::sidecars::{self, Payload};
use super::{extract, select};
use crate::channel_index::remote::ChannelRemote;
use crate::index::subtitles::{FileRef, record};
use crate::media::classify::lang_code;
use crate::media::streams::{self, StreamKind};

/// What the tracks are read from. A URL lets the backfill read the copy in
/// the channel through the player's loopback server; only a file has a
/// folder where sidecars can sit.
#[derive(Debug, Clone)]
pub enum Input {
    File(PathBuf),
    Url(String),
}

impl Input {
    /// ffprobe and ffmpeg take either in the same place.
    fn target(&self) -> &Path {
        match self {
            Input::File(path) => path,
            Input::Url(url) => Path::new(url),
        }
    }
}

/// Nothing to send; only a picture track that could not be read is worth
/// saying.
fn nothing_sent(pictures: usize) -> Option<Attached> {
    (pictures > 0).then_some(Attached {
        labels: Vec::new(),
        pictures,
    })
}

/// What was recorded for the set. No labels means nothing was sent.
#[derive(Debug)]
pub struct Attached {
    pub labels: Vec<String>,
    /// German or English picture tracks that could not be read.
    pub pictures: usize,
}

/// Sends the set's bundle and records it. `None` when there is nothing to
/// send (no German or English text track, embedded or beside the video) and
/// no picture track went unread.
pub async fn attach(
    conn: &Connection,
    remote: &impl ChannelRemote,
    set_id: &str,
    input: &Input,
) -> Result<Option<Attached>> {
    let probed = streams::probe(input.target()).await?;
    let found = select::find_wanted(&probed.streams);
    let audio = probed
        .streams
        .iter()
        .filter(|s| s.kind == StreamKind::Audio)
        .find_map(|s| lang_code(s.language.as_deref()));
    let beside = match input {
        Input::File(video) => sidecars::discover(video, audio.as_deref()),
        Input::Url(_) => Vec::new(),
    };
    if found.wanted.is_empty() && beside.is_empty() {
        return Ok(nothing_sent(found.pictures));
    }

    let dir = tempfile::tempdir().context("creating a scratch folder for the tracks")?;
    let indexes: Vec<u32> = found.wanted.iter().map(|w| w.index).collect();
    let mut texts = extract::embedded(
        input.target(),
        &indexes,
        dir.path(),
        matches!(input, Input::File(_)),
    )
    .await;
    let mut tracks: Vec<Extracted> = found
        .wanted
        .into_iter()
        .filter_map(|w| {
            Some(Extracted {
                origin: Origin::Embedded,
                lang: w.lang,
                forced: w.forced,
                sdh: w.sdh,
                default: w.default,
                codec: w.codec,
                vtt: texts.remove(&w.index)?,
            })
        })
        .collect();
    for (n, sidecar) in beside.into_iter().enumerate() {
        let (codec, vtt) = match sidecar.payload {
            Payload::Vtt(text) => ("vtt", Ok(text)),
            Payload::Srt(path) => ("srt", extract::srt_to_vtt(&path, dir.path(), n).await),
        };
        match vtt {
            Ok(vtt) => tracks.push(Extracted {
                origin: Origin::Sidecar,
                lang: sidecar.lang,
                forced: sidecar.forced,
                sdh: sidecar.sdh,
                default: false,
                codec: codec.to_string(),
                vtt,
            }),
            Err(err) => tracing::warn!("subtitles: a sidecar was skipped: {err:#}"),
        }
    }

    let tracks = arrange(tracks, probed.duration);
    if tracks.is_empty() {
        return Ok(nothing_sent(found.pictures));
    }
    let bytes = encode(&Bundle {
        v: BUNDLE_VERSION,
        set: set_id.to_string(),
        tracks: tracks.clone(),
    });
    if bytes.len() > MAX_COMPRESSED_BYTES {
        bail!(
            "the bundle is {} bytes, over what a reader accepts",
            bytes.len()
        );
    }
    let message_id = remote
        .send_document(
            &bytes,
            &bundle_file_name(set_id),
            SUBS_MIME_TYPE,
            &render_caption(set_id),
        )
        .await
        .context("sending the bundle")?;
    let file = FileRef {
        chat_id: remote.chat_id(),
        message_id,
        bytes: bytes.len() as u64,
        sha256: hex::encode(Sha256::digest(&bytes)),
    };
    record(conn, set_id, &file, &tracks).with_context(|| {
        format!("the bundle was sent as message {message_id} but not recorded; delete that message")
    })?;
    Ok(Some(Attached {
        labels: tracks.into_iter().map(|t| t.label).collect(),
        pictures: found.pictures,
    }))
}

/// [`attach`] for the upload path: says what it did, and never fails. A set
/// without a bundle is still complete.
pub async fn attach_and_report(
    conn: &Connection,
    remote: &impl ChannelRemote,
    set_id: &str,
    input: &Input,
) {
    match attach(conn, remote, set_id, input).await {
        Ok(Some(done)) if done.labels.is_empty() => {
            println!(
                "subtitles: none to attach ({} picture track(s) skipped)",
                done.pictures
            );
        }
        Ok(Some(done)) => {
            let gap = match done.pictures {
                0 => String::new(),
                n => format!(" ({n} picture track(s) skipped)"),
            };
            println!("subtitles: {}{gap}", done.labels.join(", "));
        }
        Ok(None) => println!("subtitles: none to attach"),
        Err(err) => println!("warning: no subtitles attached to {set_id}: {err:#}"),
    }
}
