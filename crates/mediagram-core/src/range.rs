//! Range maths for a set's virtual file.
//!
//! A set's parts are raw byte ranges of one original file, so the virtual file
//! is simply their concatenation in `off` order: no container parsing, no
//! index, just arithmetic. This module is pure, because a mistake here is not
//! an error message but a corrupt video.

/// Bytes per download chunk. Fixed by the transport: `grammers` issues one
/// request per 512 KiB, and `DownloadIter::skip_chunks` advances in these
/// units, so an arbitrary byte offset costs at most one discarded chunk.
pub const CHUNK: u64 = 512 * 1024;

/// One part's place in the virtual file.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PartSpan {
    pub idx: u32,
    pub off: u64,
    pub len: u64,
}

/// An inclusive byte range, as HTTP defines it.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ByteRange {
    pub start: u64,
    pub end: u64,
}

impl ByteRange {
    pub fn length(&self) -> u64 {
        self.end - self.start + 1
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RangeError {
    /// Not a byte range this server understands: 400.
    Malformed,
    /// Understood, but outside the file: 416.
    Unsatisfiable,
    /// Several ranges at once. Allowed by HTTP, refused here: answering needs
    /// a multipart body, and browsers fall back to single ranges.
    MultiRange,
}

/// Total size of the virtual file.
pub fn total_size(parts: &[PartSpan]) -> u64 {
    parts.iter().map(|p| p.len).sum()
}

/// Parses a `Range` header against a known total size.
///
/// Handles the three forms browsers and ffmpeg actually send: `bytes=a-b`,
/// `bytes=a-`, and `bytes=-n` (the last `n` bytes). An end past the file is
/// clamped rather than refused, which is what RFC 9110 requires.
pub fn parse_range(header: &str, total: u64) -> Result<ByteRange, RangeError> {
    let spec = header
        .trim()
        .strip_prefix("bytes=")
        .ok_or(RangeError::Malformed)?;
    if spec.contains(',') {
        return Err(RangeError::MultiRange);
    }
    let (raw_start, raw_end) = spec.split_once('-').ok_or(RangeError::Malformed)?;
    let (raw_start, raw_end) = (raw_start.trim(), raw_end.trim());

    if raw_start.is_empty() {
        // Suffix form: the last n bytes.
        let n: u64 = raw_end.parse().map_err(|_| RangeError::Malformed)?;
        if n == 0 || total == 0 {
            return Err(RangeError::Unsatisfiable);
        }
        let start = total.saturating_sub(n);
        return Ok(ByteRange {
            start,
            end: total - 1,
        });
    }

    let start: u64 = raw_start.parse().map_err(|_| RangeError::Malformed)?;
    let end = if raw_end.is_empty() {
        total.checked_sub(1).ok_or(RangeError::Unsatisfiable)?
    } else {
        raw_end
            .parse::<u64>()
            .map_err(|_| RangeError::Malformed)?
            .min(total.saturating_sub(1))
    };

    // An empty file fails here too: its clamped end is 0, and no start is
    // below a total of 0.
    if start >= total || start > end {
        return Err(RangeError::Unsatisfiable);
    }
    Ok(ByteRange { start, end })
}

/// One read against one part: skip whole chunks, drop the remainder of the
/// first chunk, then take this many bytes.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Step {
    pub part_idx: u32,
    pub skip_chunks: u32,
    pub head_drop: u64,
    pub take: u64,
}

/// The reads that together cover `range` exactly, in order.
pub fn plan_reads(parts: &[PartSpan], range: &ByteRange) -> Vec<Step> {
    let mut steps = Vec::new();
    for part in parts {
        let part_end = part.off + part.len; // exclusive
        if part_end <= range.start || part.off > range.end {
            continue;
        }
        // Where this read starts and ends inside this part.
        let from = range.start.saturating_sub(part.off);
        let to = (range.end - part.off + 1).min(part.len); // exclusive
        // Only a zero-length part lying inside the range gets here with
        // nothing to read; it contributes no step.
        if to <= from {
            continue;
        }
        steps.push(Step {
            part_idx: part.idx,
            skip_chunks: (from / CHUNK) as u32,
            head_drop: from % CHUNK,
            take: to - from,
        });
    }
    steps
}
