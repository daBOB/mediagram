//! Raw byte-split planning. Parts are contiguous, in order, and cover the file
//! exactly once. The part size must be a multiple of 1 MiB so global→local
//! offset mapping never lands inside a Telegram 512 KiB download part.

use thiserror::Error;

pub const MIB: u64 = 1 << 20;
/// 3.5 GiB: comfortably under Telegram Premium's 4 GB cap however it is enforced.
pub const DEFAULT_PART_SIZE: u64 = 3_758_096_384;
/// Largest part size accepted: 4 GiB minus 1 MiB.
pub const MAX_PART_SIZE: u64 = (4 << 30) - MIB;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PartRange {
    pub idx: u32,
    pub off: u64,
    pub len: u64,
}

impl PartRange {
    #[must_use]
    pub fn end(&self) -> u64 {
        self.off + self.len
    }
}

#[derive(Error, Debug, PartialEq, Eq)]
pub enum PlanError {
    #[error("file is empty")]
    EmptyFile,
    #[error("part size {0} is not a multiple of 1 MiB")]
    Unaligned(u64),
    #[error("part size {0} exceeds {MAX_PART_SIZE}")]
    TooLarge(u64),
    #[error("plan requires {0} parts, exceeding the 32-bit caption part count")]
    TooManyParts(u64),
}

/// Checks the alignment and upload-size limits shared by every plan.
///
/// # Errors
/// Rejects zero or unaligned sizes and sizes above [`MAX_PART_SIZE`].
pub fn validate_part_size(part_size: u64) -> Result<(), PlanError> {
    if part_size == 0 || !part_size.is_multiple_of(MIB) {
        return Err(PlanError::Unaligned(part_size));
    }
    if part_size > MAX_PART_SIZE {
        return Err(PlanError::TooLarge(part_size));
    }
    Ok(())
}

/// Contiguous ranges covering a nonempty file exactly once.
///
/// # Errors
/// Rejects invalid part sizes, empty files, and counts exceeding the caption's
/// `u32` part count before allocating the plan.
pub fn plan_parts(total: u64, part_size: u64) -> Result<Vec<PartRange>, PlanError> {
    validate_part_size(part_size)?;
    if total == 0 {
        return Err(PlanError::EmptyFile);
    }
    let count = total.div_ceil(part_size);
    let n = u32::try_from(count).map_err(|_| PlanError::TooManyParts(count))?;
    let parts = (0..n)
        .map(|i| {
            let off = u64::from(i) * part_size;
            PartRange {
                idx: i,
                off,
                len: (total - off).min(part_size),
            }
        })
        .collect();
    Ok(parts)
}

/// Index of the part containing global byte `pos`, for a plan sorted by offset.
#[must_use]
pub fn part_for_offset(parts: &[PartRange], pos: u64) -> Option<usize> {
    let i = parts.partition_point(|p| p.off <= pos);
    (i > 0 && pos < parts[i - 1].end()).then(|| i - 1)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sixty_gb_remux_is_17_parts() {
        let parts = plan_parts(62_914_560_000, DEFAULT_PART_SIZE).unwrap();
        assert_eq!(parts.len(), 17);
        assert_eq!(parts[16].len, 62_914_560_000 - 16 * DEFAULT_PART_SIZE);
        assert_eq!(parts.iter().map(|p| p.len).sum::<u64>(), 62_914_560_000);
        assert!(parts.windows(2).all(|w| w[0].end() == w[1].off));
    }

    #[test]
    fn small_file_is_one_part_and_exact_multiple_has_no_empty_tail() {
        assert_eq!(plan_parts(1 << 30, DEFAULT_PART_SIZE).unwrap().len(), 1);
        assert_eq!(plan_parts(4 * MIB, 2 * MIB).unwrap().len(), 2);
    }

    #[test]
    fn rejects_bad_sizes() {
        assert_eq!(plan_parts(0, MIB), Err(PlanError::EmptyFile));
        assert_eq!(plan_parts(10, MIB + 1), Err(PlanError::Unaligned(MIB + 1)));
        assert_eq!(plan_parts(10, 4 << 30), Err(PlanError::TooLarge(4 << 30)));
    }

    #[test]
    fn offset_lookup() {
        let parts = plan_parts(5 * MIB, 2 * MIB).unwrap();
        assert_eq!(part_for_offset(&parts, 0), Some(0));
        assert_eq!(part_for_offset(&parts, 2 * MIB), Some(1));
        assert_eq!(part_for_offset(&parts, 5 * MIB - 1), Some(2));
        assert_eq!(part_for_offset(&parts, 5 * MIB), None);
    }
}
