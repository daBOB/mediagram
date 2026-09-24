//! How a published package file is named: the export date and enough of the
//! ciphertext digest to make the name unique without any local counter.

use super::{FILE_PREFIX, FILE_SUFFIX, NAME_HASH_BYTES};

/// `prebuilt_mediagram_db_YYYYMMDD-<hash>.tar.gz.enc`, where the hash is the
/// start of the ciphertext's sha256. Two exports on one day cannot collide,
/// and the name depends on nothing but the bytes being published, so a
/// cleaned output directory can never cause a silent overwrite.
#[must_use]
pub fn package_file_name(created_at: i64, ciphertext_sha256: &[u8; 32]) -> String {
    // The digest arrives as bytes rather than text so no caller can put a
    // path separator, a `..`, or anything else unprintable into a file name.
    let hash = hex::encode(&ciphertext_sha256[..NAME_HASH_BYTES]);
    // A timestamp before the epoch would render as a negative, variable-width
    // year and break the fixed `YYYYMMDD` shape; it cannot arise from a real
    // export, so it is clamped rather than given its own error path.
    let (y, m, d) = civil_from_unix(created_at.max(0));
    format!("{FILE_PREFIX}_{y:04}{m:02}{d:02}-{hash}{FILE_SUFFIX}")
}

/// Civil date (UTC) from a Unix timestamp, by Howard Hinnant's `civil_from_days`.
/// Implemented here so the spec crate stays dependency-free on time handling.
fn civil_from_unix(secs: i64) -> (i64, i64, i64) {
    let days = secs.div_euclid(86_400);
    let z = days + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let day = doy - (153 * mp + 2) / 5 + 1;
    let month = if mp < 10 { mp + 3 } else { mp - 9 };
    let year = yoe + era * 400 + i64::from(month <= 2);
    (year, month, day)
}
