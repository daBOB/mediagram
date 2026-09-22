//! Sending one part: streamed from the source, hashed on the way, with its
//! progress visible to this terminal and to other processes.

use std::path::Path;
use std::time::Instant;

use anyhow::Result;
use mlib_spec::caption::{Caption, Part};

use super::part_reader::PartReader;
use super::progress;
use super::progress_line;
use super::transport::Transport;
use crate::index::parts;
use crate::index::set_row::SetRow;

/// What stays the same for every part of one set's upload.
pub(super) struct SetUpload<'a, T> {
    pub(super) transport: &'a T,
    pub(super) template: &'a Caption,
    pub(super) set: &'a SetRow,
    pub(super) source_path: &'a Path,
    pub(super) started: Instant,
    pub(super) data_dir: Option<&'a Path>,
}

impl<T: Transport> SetUpload<'_, T> {
    /// Streams one part through the transport, hashing as it goes, and says
    /// where it landed. `bytes_done` is how much of the set was already in
    /// the channel, for the progress a watcher sees.
    pub(super) async fn send(&self, part: &parts::PartRow, bytes_done: u64) -> Result<parts::Landed> {
        let Self {
            transport,
            template,
            set,
            source_path,
            started,
            data_dir,
        } = *self;
        let total_parts = set.part_count;
        let reader = PartReader::open(source_path, part.byte_offset, part.byte_length)
            .await
            .map_err(|err| {
                anyhow::anyhow!(
                    "opening part {} of {}: {err}",
                    part.idx,
                    source_path.display()
                )
            })?;
        // Watched while it is read, so another process can see a part move
        // rather than waiting for it to land. The reporter stops when it drops
        // at the end of this function, whether the part succeeded or not.
        let counter = std::sync::Arc::new(std::sync::atomic::AtomicU64::new(0));
        let shape = progress::Progress {
            set_id: set.set_id.clone(),
            part: part.idx,
            parts: total_parts,
            bytes_sent: 0,
            part_bytes: part.byte_length,
            bytes_done,
            set_bytes: set.total,
            updated_at: crate::clock::now_unix(),
        };
        let _reporter = data_dir.map(|dir| {
            progress::Reporter::start(dir, std::sync::Arc::clone(&counter), shape.clone())
        });
        // The same counter also drives the line on the terminal, for whoever is
        // sitting in front of this one; it draws only when there is a terminal.
        let _line = progress_line::Line::start(std::sync::Arc::clone(&counter), shape);
        let mut reader = reader.watched_by(std::sync::Arc::clone(&counter));
        let base_name = mlib_spec::part_name::base_name(template);
        let name =
            mlib_spec::part_name::part_file_name(&base_name, &set.container, part.idx, total_parts);
        let caption = template.with_part(Part {
            i: part.idx,
            n: total_parts,
            off: part.byte_offset,
            len: part.byte_length,
            sha256: String::new(),
        });
        let human = format!(
            "{} — part {}/{}",
            template.display_name(),
            part.idx + 1,
            total_parts
        );

        let sent = transport
            .send_part(
                name,
                mime_for(&set.container),
                &caption,
                &human,
                &mut reader,
                part.byte_length,
            )
            .await?;
        let sha256 = reader.finalize();

        let mb = part.byte_length as f64 / (1024.0 * 1024.0);
        tracing::info!(
            idx = part.idx,
            total = total_parts,
            mb,
            elapsed_s = started.elapsed().as_secs_f64(),
            "uploaded part"
        );

        Ok(parts::Landed {
            chat_id: transport.chat_id(),
            message_id: sent.message_id,
            doc_id: sent.doc_id,
            sha256,
        })
    }
}

fn mime_for(container: &str) -> &'static str {
    match container {
        "mkv" => "video/x-matroska",
        "mp4" => "video/mp4",
        _ => "application/octet-stream",
    }
}
