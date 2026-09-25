//! Serial resumption of pending sets with a summary of committed progress.

use std::path::Path;

use anyhow::Result;
use rusqlite::Connection;

use super::finish::{available_source, finish_from};
use super::transport::Transport;
use crate::index::set_row::SetRow;
use crate::index::status::SetStatus;
use crate::index::{db, sets};

#[derive(Default)]
pub struct Summary {
    pub completed: usize,
    pub blocked: usize,
    pub stopped: Option<anyhow::Error>,
}

/// Reports each attempted set as it finishes. A transport or database failure
/// stops the run while preserving the number of sets already completed.
pub async fn pending(
    conn: &Connection,
    transport: &impl Transport,
    throttle_ms: u64,
    sets: &[SetRow],
    data_dir: &Path,
    mut report: impl FnMut(&str, &Result<bool>),
) -> Summary {
    let mut summary = Summary::default();
    for set in sets {
        let recorded = match db::get_meta(conn, &db::source_key(&set.set_id)) {
            Ok(recorded) => recorded,
            Err(error) => {
                summary.stopped = Some(error);
                break;
            }
        };
        let source = match available_source(set, recorded.as_deref()).await {
            Ok(source) => source,
            Err(error) => {
                report(&set.set_id, &Err(error));
                summary.blocked += 1;
                continue;
            }
        };
        let result = finish_from(conn, transport, throttle_ms, set, data_dir, &source).await;
        report(&set.set_id, &result);
        match result {
            Ok(true) => summary.completed += 1,
            Ok(false) => {}
            Err(error) => {
                // Completion can commit before source-metadata cleanup fails.
                // Publish that durable work while still reporting the error.
                if sets::get_set(conn, &set.set_id)
                    .is_ok_and(|row| row.is_some_and(|row| row.status == SetStatus::Complete))
                {
                    summary.completed += 1;
                }
                summary.stopped = Some(error);
                break;
            }
        }
    }
    summary
}
