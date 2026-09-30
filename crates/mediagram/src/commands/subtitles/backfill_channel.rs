//! `mediagram subtitles backfill --channel`: titles whose only copy is the
//! one in the channel. Their subtitles are read from that copy, through the
//! loopback server, by the same process that then sends the bundle.

use anyhow::{Result, bail};
use mediagram_core::transport::source::TelegramSource;
use mlib_spec::caption::Kind;
use rusqlite::Connection;

use super::BackfillArgs;
use super::backfill::{NONE_KEY, Sent, Tally, send_set};
use super::loopback::Loopback;
use super::session::{PUBLISH_EVERY, Session, connected};
use crate::channel_index::remote::ChannelRemote;
use crate::config::Config;
use crate::index::set_row::SetRow;
use crate::index::status::SetStatus;
use crate::index::{db, sets};
use crate::subtitles::Input;

/// Five failures in a row mean the server or the channel is down, not five
/// unlucky files.
const MAX_FAILURES_IN_A_ROW: usize = 5;

/// A set to read from the channel, and whether it is a redo.
#[derive(Debug, PartialEq, Eq)]
pub struct Candidate {
    pub set_id: String,
    pub redo: bool,
    pub mp4: bool,
}

/// Complete, playable sets that declare German or English subtitles and
/// have no bundle and no earlier "none" verdict: MP4 first, since its
/// subtitle samples are range-read while MKV is read whole. Anything but MP4
/// needs `mkv`. A set that still has inline subtitle rows goes through
/// `move-inline` first. Sets named by `redo` come first whatever their state.
pub fn candidates(conn: &Connection, mkv: bool, redo: &[String]) -> Result<Vec<Candidate>> {
    let mut found = Vec::new();
    for set_id in redo {
        match sets::get_set(conn, set_id)? {
            Some(set) if set.status == SetStatus::Complete && set.kind != Kind::Doc => {
                found.push(Candidate {
                    set_id: set_id.clone(),
                    redo: true,
                    mp4: set.container == "mp4",
                });
            }
            _ => eprintln!("warning: --redo {set_id}: not a complete set with a video"),
        }
    }
    let mut stmt = conn.prepare(
        "SELECT * FROM sets s WHERE status = ?1
           AND NOT EXISTS (SELECT 1 FROM subtitle_files f WHERE f.set_id = s.set_id)
           AND NOT EXISTS (SELECT 1 FROM meta m WHERE m.key = ?2 || s.set_id)
           AND NOT EXISTS (SELECT 1 FROM assets a WHERE a.set_id = s.set_id AND a.kind = 'subtitle')
         ORDER BY set_id",
    )?;
    let rows = stmt
        .query_map(
            rusqlite::params![SetStatus::Complete, NONE_KEY],
            SetRow::from_row,
        )?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    let wanted = |set: &&SetRow| {
        set.kind != Kind::Doc
            && set.slang.iter().any(|l| l == "de" || l == "en")
            && (mkv || set.container == "mp4")
            && !redo.contains(&set.set_id)
    };
    let (mp4, other): (Vec<_>, Vec<_>) = rows
        .iter()
        .filter(wanted)
        .partition(|s| s.container == "mp4");
    found.extend(mp4.into_iter().chain(other).map(|set| Candidate {
        set_id: set.set_id.clone(),
        redo: false,
        mp4: set.container == "mp4",
    }));
    Ok(found)
}

pub async fn run(cfg: &Config, args: BackfillArgs) -> Result<()> {
    if args.dry_run {
        let conn = db::open_read_only(&cfg.data_dir()?, "plan a subtitles backfill")?;
        let found = candidates(&conn, args.mkv, &args.redo)?;
        let mp4 = found.iter().filter(|c| c.mp4).count();
        let shown = args.limit.map_or(found.len(), |n| n.min(found.len()));
        println!(
            "dry run: {} set(s) to read from the channel ({mp4} MP4, {} other); a run would take {shown}",
            found.len(),
            found.len() - mp4
        );
        return Ok(());
    }
    connected(cfg, !args.no_push, PUBLISH_EVERY, async |session, tg| {
        let found = candidates(session.conn, args.mkv, &args.redo)?;
        println!("{} candidate set(s)", found.len());
        // A read-only index and the sending client's own session: a second
        // client on the same key would be logged out by Telegram.
        let index = db::open_read_only(&cfg.data_dir()?, "serve the backfill")?;
        let source = TelegramSource::new(tg.client.clone(), tg.channel);
        let server = Loopback::start(index, source).await?;
        let tally = read_and_send(session, |id| server.stream_url(id), found, args.limit).await;
        server.shutdown().await;
        tally.print();
        if tally.aborted {
            bail!("stopped after {MAX_FAILURES_IN_A_ROW} failures in a row; the server or the channel needs a look");
        }
        Ok(())
    })
    .await
}

/// Sends the bundle of each candidate, reading its source from `url_of`.
pub async fn read_and_send<R: ChannelRemote>(
    session: &mut Session<'_, R>,
    url_of: impl Fn(&str) -> String,
    found: Vec<Candidate>,
    limit: Option<usize>,
) -> Tally {
    let mut tally = Tally::default();
    let (mut attempted, mut failing) = (0, 0);
    for c in found {
        if session.stopping()
            || limit.is_some_and(|n| attempted >= n)
            || failing >= MAX_FAILURES_IN_A_ROW
        {
            break;
        }
        let sent = send_set(session, &c.set_id, &Input::Url(url_of(&c.set_id)), c.redo).await;
        if sent.is_err() {
            failing += 1;
        } else {
            failing = 0;
        }
        if matches!(sent, Ok(Sent::Nothing))
            && let Err(err) = db::set_meta(session.conn, &format!("{NONE_KEY}{}", c.set_id), "1")
        {
            eprintln!(
                "warning: {}: could not note that it has no subtitles: {err:#}",
                c.set_id
            );
        }
        let worked = !matches!(sent, Ok(Sent::Skipped));
        tally.add(&c.set_id, sent);
        if worked {
            attempted += 1;
            session.pause().await;
        }
    }
    tally
}
