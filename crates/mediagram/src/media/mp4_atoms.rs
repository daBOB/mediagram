//! Minimal MP4/ISO-BMFF top-level box reader. We only need to know whether
//! `mdat` (the media data box) appears before `moov` (the movie metadata
//! box), which determines whether `ffmpeg -movflags +faststart` must run
//! before a file can be split and streamed part-by-part.

use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::Path;

use anyhow::{Context, Result, bail};

const BOX_HEADER_LEN: u64 = 8;
const LARGESIZE_LEN: u64 = 8;

/// Returns `true` when `path` is an MP4-family container whose `mdat` box
/// appears before its `moov` box, meaning it needs a faststart remux before
/// it can be split. Non-MP4 containers (mkv, webm, ...) always return
/// `Ok(false)` without reading any box data.
pub fn needs_faststart(path: &Path) -> Result<bool> {
    if !is_mp4_family(path) {
        return Ok(false);
    }
    let mut file =
        File::open(path).with_context(|| format!("opening {} for atom scan", path.display()))?;
    let file_len = file
        .metadata()
        .with_context(|| format!("stat {}", path.display()))?
        .len();

    let mut moov_offset: Option<u64> = None;
    let mut mdat_offset: Option<u64> = None;
    let mut pos: u64 = 0;

    while pos + BOX_HEADER_LEN <= file_len {
        file.seek(SeekFrom::Start(pos))
            .with_context(|| format!("seeking {} at {pos}", path.display()))?;
        let mut header = [0u8; 8];
        file.read_exact(&mut header)
            .with_context(|| format!("reading box header in {} at {pos}", path.display()))?;
        let declared_size = u32::from_be_bytes(header[0..4].try_into().unwrap()) as u64;
        let box_type = &header[4..8];

        let (box_size, header_len) = if declared_size == 1 {
            // 64-bit largesize follows the type field.
            if pos + BOX_HEADER_LEN + LARGESIZE_LEN > file_len {
                bail!(
                    "{}: truncated largesize box at offset {pos}",
                    path.display()
                );
            }
            let mut large = [0u8; 8];
            file.read_exact(&mut large)
                .with_context(|| format!("reading largesize in {} at {pos}", path.display()))?;
            (u64::from_be_bytes(large), BOX_HEADER_LEN + LARGESIZE_LEN)
        } else if declared_size == 0 {
            // Box extends to end of file (only valid for the last box).
            (file_len - pos, BOX_HEADER_LEN)
        } else {
            (declared_size, BOX_HEADER_LEN)
        };

        if box_size < header_len {
            bail!(
                "{}: box at offset {pos} has size {box_size} smaller than its header",
                path.display()
            );
        }

        if box_type == b"moov" && moov_offset.is_none() {
            moov_offset = Some(pos);
        }
        if box_type == b"mdat" && mdat_offset.is_none() {
            mdat_offset = Some(pos);
        }

        // Once both landmarks are known, order is decided; stop scanning.
        if moov_offset.is_some() && mdat_offset.is_some() {
            break;
        }

        pos = pos.checked_add(box_size).ok_or_else(|| {
            anyhow::anyhow!("{}: box size overflows at offset {pos}", path.display())
        })?;
    }

    match (mdat_offset, moov_offset) {
        (Some(mdat), Some(moov)) => Ok(mdat < moov),
        // moov never found before mdat's box (or before EOF): treat as
        // needing remux so the caller re-muxes into a well-formed layout.
        (Some(_), None) => Ok(true),
        _ => Ok(false),
    }
}

fn is_mp4_family(path: &Path) -> bool {
    matches!(
        path.extension()
            .and_then(|e| e.to_str())
            .map(|e| e.to_ascii_lowercase())
            .as_deref(),
        Some("mp4") | Some("m4v") | Some("mov")
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::media::test_fixtures::{
        ffmpeg_required, make_faststart_mp4, make_trailing_moov_mp4,
    };

    #[test]
    fn non_mp4_extension_is_never_scanned() {
        // No file needs to exist: the extension check short-circuits first.
        let path = Path::new("/nonexistent/movie.mkv");
        assert!(!needs_faststart(path).unwrap());
    }

    #[test]
    fn detects_trailing_moov() {
        if !ffmpeg_required("detects_trailing_moov") {
            return;
        }
        let dir = tempfile::tempdir().unwrap();
        let path = make_trailing_moov_mp4(dir.path());
        assert!(needs_faststart(&path).unwrap());
    }

    #[test]
    fn faststart_file_does_not_need_remux() {
        if !ffmpeg_required("faststart_file_does_not_need_remux") {
            return;
        }
        let dir = tempfile::tempdir().unwrap();
        let path = make_faststart_mp4(dir.path());
        assert!(!needs_faststart(&path).unwrap());
    }
}
