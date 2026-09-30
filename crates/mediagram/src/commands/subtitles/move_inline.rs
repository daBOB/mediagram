//! `mediagram subtitles move-inline`: lessons kept their `.vtt` in the index
//! as inline rows; this gives each such set a bundle like every other title
//! and drops the rows, but only once the bundle read back from the channel
//! is exactly what was sent.

use anyhow::{Context, Result, bail};
use mlib_spec::subtitle_bundle::{
    BUNDLE_VERSION, Bundle, BundleTrack, MAX_COMPRESSED_BYTES, SUBS_MIME_TYPE, bundle_file_name,
    decode, encode, render_caption,
};
use sha2::{Digest, Sha256};

use super::MoveInlineArgs;
use super::session::{Session, connected};
use crate::channel_index::remote::ChannelRemote;
use crate::config::Config;
use crate::index::subtitles::{FileRef, bundle_message, record};
use crate::index::{assets, db};
use crate::media::classify::lang_code;
use crate::subtitles::track_label;

/// Three bundles that did not survive the round trip mean the channel, not
/// the sets, is the problem.
const MAX_MISMATCHES: usize = 3;

#[derive(Debug, Default)]
pub struct Moved {
    pub moved: usize,
    pub mismatched: Vec<String>,
}

pub async fn run(cfg: &Config, args: MoveInlineArgs) -> Result<()> {
    if args.dry_run {
        let conn = db::open_read_only(&cfg.data_dir()?, "plan subtitles move-inline")?;
        let sets = assets::sets_with_inline_subtitles(&conn)?;
        let rows: usize = sets
            .iter()
            .map(|s| assets::inline_subtitles(&conn, s).map(|r| r.len()))
            .sum::<Result<_>>()?;
        println!(
            "dry run: {} set(s), {rows} inline subtitle row(s) would move into bundles",
            sets.len()
        );
        return Ok(());
    }
    connected(cfg, !args.no_push, usize::MAX, async |session, _| {
        let sets = assets::sets_with_inline_subtitles(session.conn)?;
        println!("moving the inline subtitles of {} set(s)", sets.len());
        let done = move_sets(session, &sets).await?;
        println!("moved {}, mismatched {}", done.moved, done.mismatched.len());
        if done.mismatched.len() >= MAX_MISMATCHES {
            bail!("stopped after {MAX_MISMATCHES} bundles that did not read back the same: {:?}", done.mismatched);
        }
        Ok(())
    })
    .await
}

/// Moves each set's inline rows into a verified bundle. A set whose
/// read-back differs keeps its rows, is reported, and three of those end the
/// run.
pub async fn move_sets(
    session: &mut Session<'_, impl ChannelRemote>,
    sets: &[String],
) -> Result<Moved> {
    let mut done = Moved::default();
    for set_id in sets {
        if session.stopping() || done.mismatched.len() >= MAX_MISMATCHES {
            break;
        }
        let rows = assets::inline_subtitles(session.conn, set_id)?;
        if rows.is_empty() || bundle_message(session.conn, set_id)?.is_some() {
            continue;
        }
        if move_one(session, set_id, rows).await? {
            done.moved += 1;
            session.recorded().await;
        } else {
            eprintln!("warning: {set_id}: the bundle read back differs; its rows stay");
            done.mismatched.push(set_id.clone());
        }
        session.pause().await;
    }
    Ok(done)
}

/// `true` when the bundle verified and was recorded.
async fn move_one(
    session: &Session<'_, impl ChannelRemote>,
    set_id: &str,
    rows: Vec<(String, String)>,
) -> Result<bool> {
    let tracks: Vec<BundleTrack> = rows
        .into_iter()
        .map(|(lang, vtt)| {
            // The rows keep the language as the lesson's upload wrote it
            // (`deu`); a bundle names it as the readers do.
            let lang = lang_code(Some(&lang)).unwrap_or(lang);
            BundleTrack {
                label: track_label(&lang, false, false),
                lang,
                forced: false,
                sdh: false,
                source: "sidecar".into(),
                codec: "vtt".into(),
                vtt,
            }
        })
        .collect();
    let bytes = encode(&Bundle {
        v: BUNDLE_VERSION,
        set: set_id.to_string(),
        tracks: tracks.clone(),
    });
    if bytes.len() > MAX_COMPRESSED_BYTES {
        anyhow::bail!(
            "the bundle of {set_id} is {} bytes, over what a reader accepts",
            bytes.len()
        );
    }
    let remote = session.remote;
    let message_id = remote
        .send_document(
            &bytes,
            &bundle_file_name(set_id),
            SUBS_MIME_TYPE,
            &render_caption(set_id),
        )
        .await
        .with_context(|| format!("sending the bundle of {set_id}"))?;
    let back = remote
        .download(message_id)
        .await
        .with_context(|| format!("reading back the bundle of {set_id}"))?;
    let same = back.as_deref().is_some_and(|back| {
        Sha256::digest(back) == Sha256::digest(&bytes)
            && decode(back).is_ok_and(|b| b.set == set_id && b.tracks == tracks)
    });
    if !same {
        // Best effort: the orphan costs nothing but a stray document.
        let _ = remote.delete_message(message_id).await;
        return Ok(false);
    }
    let file = FileRef {
        chat_id: remote.chat_id(),
        message_id,
        bytes: bytes.len() as u64,
        sha256: hex::encode(Sha256::digest(&bytes)),
    };
    record(session.conn, set_id, &file, &tracks)?;
    Ok(true)
}
