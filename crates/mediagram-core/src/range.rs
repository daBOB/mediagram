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
    /// Inclusive length, for `start <= end` and a length representable in `u64`.
    /// In particular, `0..=u64::MAX` is not representable. [`parse_range`]
    /// produces valid bounds; callers constructing public fields must do so too.
    /// Invalid bounds are not checked: arithmetic panics with overflow checks
    /// enabled and otherwise wraps.
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

/// Total size of the virtual file, provided the sum of lengths fits in `u64`.
/// Does not validate offsets or coverage. An overflowing sum panics with
/// overflow checks enabled and otherwise wraps.
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

impl Step {
    /// What is left of this step once `sent` of its bytes are delivered: a
    /// download interrupted midway resumes where it stopped, not from the
    /// top, which would send the delivered bytes twice.
    ///
    /// Requires `sent <= self.take`. The resumed part-relative offset
    /// `skip_chunks * CHUNK + head_drop + sent` must fit in `u64`, and its
    /// whole-chunk quotient must fit in `u32`. The result aligns that offset
    /// into whole chunks and a remainder smaller than [`CHUNK`]. Delivering
    /// exactly `take` bytes returns an empty step, subject to the same bounds.
    /// These preconditions are unchecked: arithmetic overflow/underflow panics
    /// with overflow checks enabled and otherwise wraps; an oversized chunk
    /// quotient truncates when cast to `u32`.
    pub fn after(&self, sent: u64) -> Step {
        let from = u64::from(self.skip_chunks) * CHUNK + self.head_drop + sent;
        Step {
            part_idx: self.part_idx,
            skip_chunks: (from / CHUNK) as u32,
            head_drop: from % CHUNK,
            take: self.take - sent,
        }
    }
}

/// The reads that together cover `range` exactly, in order.
///
/// Requires parts ordered by `off`, with contiguous, nonoverlapping coverage
/// of the entire inclusive range. Zero-length parts are ignored. The range
/// must satisfy [`ByteRange::length`]'s bounds; every `off + len` (exclusive
/// part end) and `range.end - off + 1` for an overlapping part must fit in
/// `u64`. Each part-relative starting offset divided by [`CHUNK`] must fit
/// in `u32`. Resuming a returned step also requires [`Step::after`]'s bounds.
///
/// This helper neither sorts nor validates its inputs and returns no error.
/// Gaps or missing parts yield incomplete coverage (empty input yields no
/// steps); overlaps or unordered parts may duplicate or reorder bytes.
/// Arithmetic overflow/underflow panics with overflow checks enabled and
/// otherwise wraps, while oversized chunk quotients truncate to `u32`.
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
