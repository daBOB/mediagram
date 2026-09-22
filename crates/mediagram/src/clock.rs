//! Wall-clock time as the index records it: whole seconds since the epoch.

use std::time::{SystemTime, UNIX_EPOCH};

/// Now, in whole seconds. A clock set before 1970 reads as 0 rather than
/// failing: every caller stores or compares the value, and none of them has
/// anything better to do with the error.
pub fn now_unix() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}
