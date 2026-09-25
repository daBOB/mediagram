//! Pure request-shape rules, checked before any filesystem call: the id
//! grammar Android and the web player already share, and the chunk length a
//! PUT must carry to be believed.

/// A chunk is this many bytes, except the last one of a set, which is
/// whatever remains of `total`. Also the ceiling axum enforces on a PUT
/// body, so a chunk that is the wrong size is rejected before it is ever
/// read into memory.
pub const CHUNK: u64 = 1 << 20;

/// `^[A-Za-z0-9]{1,64}$`, the shape the web player's `STREAM_PATH` already
/// requires of a set id. No `.`, `/` or `..`, so a value that passes this
/// can be joined onto a store root and never escape it.
pub fn valid_id(id: &str) -> bool {
    !id.is_empty() && id.len() <= 64 && id.bytes().all(|b| b.is_ascii_alphanumeric())
}

/// A plain, non-negative decimal — nothing `u32::from_str` would accept but
/// a path segment should not (a leading `-`, a fractional part). Malformed
/// input is `None` rather than an error: the caller treats it exactly like
/// an id that fails [`valid_id`], a 404 before any filesystem call.
pub fn parse_chunk_num(raw: &str) -> Option<u32> {
    if raw.is_empty() || !raw.bytes().all(|b| b.is_ascii_digit()) {
        return None;
    }
    raw.parse().ok()
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, thiserror::Error)]
pub enum LengthError {
    /// Neither a full `CHUNK` nor exactly what is left of `total`.
    #[error("chunk length does not match a full or final chunk")]
    BadLength,
    /// This chunk starts at or beyond `total`: there is nothing there to
    /// write, whatever length was sent.
    #[error("chunk starts at or past the set's total")]
    PastTotal,
}

/// A chunk at `n` is `len` bytes long, and the set is `total` bytes in all.
///
/// Valid when `len == CHUNK`, or `n*CHUNK + len == total` for the final
/// chunk — and always `n*CHUNK < total`, so a chunk number past the end of
/// the set is refused even when the length would otherwise agree.
pub fn check_length(n: u32, len: u64, total: u64) -> Result<(), LengthError> {
    let start = n as u64 * CHUNK;
    if start >= total {
        return Err(LengthError::PastTotal);
    }
    if len == CHUNK || start + len == total {
        Ok(())
    } else {
        Err(LengthError::BadLength)
    }
}

#[cfg(test)]
#[path = "rules_tests.rs"]
mod tests;
