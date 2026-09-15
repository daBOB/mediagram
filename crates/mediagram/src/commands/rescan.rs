//! `mediagram rescan`: rebuild `library.db` from channel captions when the
//! local index is lost. Reads only; never re-uploads media.

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::config::Config;
use crate::index::db;
use crate::index::rescan::{self, RescanSummary};
use crate::telegram::client::Tg;
use crate::upload::transport::Seen;

/// Messages are folded into `library.db` this many at a time, each inside
/// its own transaction, so a channel with years of history never needs to
/// hold every caption in memory at once.
const BATCH_SIZE: usize = 500;

pub async fn run(cfg: &Config) -> Result<()> {
    let mut conn = db::open(&cfg.data_dir()?)?;
    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let chat_id = tg.chat_id();

    let result = rescan_all(&mut conn, &tg, chat_id).await;
    tg.shutdown().await;
    let summary = result?;

    println!(
        "rescan complete: {} sets seen ({} complete, {} incomplete), {} parts seen, {} duplicates skipped",
        summary.sets_seen,
        summary.sets_complete,
        summary.sets_incomplete,
        summary.parts_seen,
        summary.duplicates_skipped,
    );
    Ok(())
}

/// Pages through the entire channel history, collecting only messages that
/// carry both a document and an mlib part caption, and folds them into the
/// index in fixed-size batches.
async fn rescan_all(conn: &mut Connection, tg: &Tg, chat_id: i64) -> Result<RescanSummary> {
    let mut totals = RescanSummary::default();
    let mut batch: Vec<Seen> = Vec::with_capacity(BATCH_SIZE);
    let mut iter = tg.client.iter_messages(tg.channel);

    while let Some(message) = iter.next().await.context("scanning channel history")? {
        let doc_id = crate::telegram::document::message_document(&message).map(|(_, id)| id);
        let caption = message.text().to_string();
        if doc_id.is_none() || !mlib_spec::caption_codec::is_mlib(&caption) {
            continue;
        }
        batch.push(Seen {
            message_id: i64::from(message.id()),
            doc_id,
            caption,
        });
        if batch.len() >= BATCH_SIZE {
            flush_batch(conn, chat_id, &mut batch, &mut totals)?;
        }
    }
    flush_batch(conn, chat_id, &mut batch, &mut totals)?;

    // Set-level counts come from the library as a whole: a set that straddles
    // two batches would otherwise be counted twice.
    let count = |sql: &str| -> Result<usize> {
        let n: i64 = conn.query_row(sql, [], |row| row.get(0))?;
        Ok(n as usize)
    };
    totals.sets_seen = count("SELECT COUNT(DISTINCT set_id) FROM parts WHERE status = 'done'")?;
    totals.sets_complete = count("SELECT COUNT(*) FROM sets WHERE status = 'complete'")?;
    totals.sets_incomplete = count("SELECT COUNT(*) FROM sets WHERE status != 'complete'")?;

    Ok(totals)
}

/// Applies one batch inside a single transaction and folds its summary into
/// the running totals; a no-op on an empty batch.
fn flush_batch(
    conn: &mut Connection,
    chat_id: i64,
    batch: &mut Vec<Seen>,
    totals: &mut RescanSummary,
) -> Result<()> {
    if batch.is_empty() {
        return Ok(());
    }
    let tx = conn
        .transaction()
        .context("starting rescan batch transaction")?;
    let summary = rescan::apply_seen(&tx, chat_id, batch)?;
    tx.commit().context("committing rescan batch")?;

    totals.parts_seen += summary.parts_seen;
    totals.duplicates_skipped += summary.duplicates_skipped;
    totals.unparsed += summary.unparsed;
    batch.clear();
    Ok(())
}
