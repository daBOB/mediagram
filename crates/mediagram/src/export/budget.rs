//! How big the package will be, decided before anything is downloaded.
//!
//! A reader verifies the authentication tag over the whole file before it can
//! trust any plaintext, so the package has to fit in a modest device's heap
//! twice over. The export therefore refuses well below the reader's own
//! ceiling, and it refuses up front: discovering the problem after fetching
//! hundreds of posters would throw away the entire run.

/// Assumed size of one `w342` poster. Measured posters land near 25 KB; the
/// estimate rounds up so the check errs toward refusing early.
const POSTER_ESTIMATE: u64 = 32 * 1024;

/// Past this, the export says the package is getting large.
pub const WARN_BYTES: u64 = 24 * 1024 * 1024;
/// Past this, the export refuses. Comfortably under the reader's limit.
pub const REFUSE_BYTES: u64 = 48 * 1024 * 1024;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Verdict {
    Fine,
    Large(u64),
    TooLarge(u64),
}

/// Bytes the finished package is expected to occupy. Saturating throughout:
/// a corrupt index reporting an impossible size should refuse, not wrap.
pub fn estimate_bytes(index_bytes: u64, posters: u64) -> u64 {
    index_bytes.saturating_add(posters.saturating_mul(POSTER_ESTIMATE))
}

pub fn verdict_for(estimate: u64) -> Verdict {
    if estimate >= REFUSE_BYTES {
        Verdict::TooLarge(estimate)
    } else if estimate >= WARN_BYTES {
        Verdict::Large(estimate)
    } else {
        Verdict::Fine
    }
}
