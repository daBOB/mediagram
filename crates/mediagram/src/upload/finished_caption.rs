//! The caption a part is sent with once its bytes are through: the part's
//! real hash, which is only known after the reader is drained, rendered into
//! the caption text. Every transport sends this text, so it lives outside
//! any one of them.

use anyhow::{Context, Result, bail};
use mlib_spec::caption::{Caption, Part};

use super::part_reader::PartReader;

/// `caption` with the hash of the bytes `reader` delivered, as caption text.
///
/// Refuses a part that came up short: a file that shrank mid-upload would
/// otherwise be recorded as complete under the hash of fewer bytes than the
/// caption promises.
pub fn finished_caption_text(
    caption: &Caption,
    human: &str,
    reader: &PartReader,
    len: u64,
) -> Result<String> {
    if reader.bytes_read() != len {
        bail!(
            "source shrank during upload: read {} of {len} planned bytes",
            reader.bytes_read()
        );
    }
    let finished = caption.with_part(Part {
        sha256: reader.finalize(),
        ..caption.part.clone()
    });
    mlib_spec::to_text(&finished, human).context("rendering part caption")
}

#[cfg(test)]
#[path = "finished_caption_tests.rs"]
mod tests;
