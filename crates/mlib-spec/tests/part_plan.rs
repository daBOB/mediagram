//! Boundary behavior of raw byte-split planning: exact-fit and off-by-one
//! totals, the `MAX_PART_SIZE` edge, and offset lookups on plans of zero or
//! one part.

use mlib_spec::part_plan::{
    MAX_PART_SIZE, MIB, PlanError, part_for_offset, plan_parts, validate_part_size,
};

#[test]
fn a_total_exactly_one_part_size_yields_a_single_full_part() {
    let part_size = 5 * MIB;
    let parts = plan_parts(part_size, part_size).unwrap();
    assert_eq!(parts.len(), 1);
    assert_eq!(parts[0].off, 0);
    assert_eq!(parts[0].len, part_size);
}

#[test]
fn one_byte_over_a_part_size_adds_a_second_one_byte_part() {
    let part_size = 5 * MIB;
    let parts = plan_parts(part_size + 1, part_size).unwrap();
    assert_eq!(parts.len(), 2);
    assert_eq!(parts[0].len, part_size);
    assert_eq!(parts[1], mlib_spec::part_plan::PartRange {
        idx: 1,
        off: part_size,
        len: 1,
    });
}

#[test]
fn a_plan_at_the_max_part_size_still_splits_the_remainder_correctly() {
    let total = MAX_PART_SIZE + MIB;
    let parts = plan_parts(total, MAX_PART_SIZE).unwrap();
    assert_eq!(parts.len(), 2);
    assert_eq!(parts[0].len, MAX_PART_SIZE);
    assert_eq!(parts[1].len, MIB);
}

#[test]
fn part_sizes_that_are_not_a_whole_number_of_mib_are_rejected() {
    for size in [0, 1, MIB - 1] {
        assert!(matches!(validate_part_size(size), Err(PlanError::Unaligned(_))));
    }
}

#[test]
fn part_sizes_up_to_four_mib_in_one_mib_steps_are_accepted() {
    for mult in 1..=4 {
        assert!(validate_part_size(MIB * mult).is_ok(), "{mult} MiB should be accepted");
    }
}

#[test]
fn a_part_size_over_the_max_is_rejected() {
    assert!(matches!(
        validate_part_size(MAX_PART_SIZE + MIB),
        Err(PlanError::TooLarge(_))
    ));
}

#[test]
fn offset_lookup_on_a_single_part_plan_covers_its_whole_range() {
    let parts = plan_parts(10 * MIB, 10 * MIB).unwrap();
    assert_eq!(parts.len(), 1);
    assert_eq!(part_for_offset(&parts, 0), Some(0));
    assert_eq!(part_for_offset(&parts, 5 * MIB), Some(0));
    assert_eq!(part_for_offset(&parts, 10 * MIB - 1), Some(0));
    assert_eq!(part_for_offset(&parts, 10 * MIB), None, "past EOF");
}

#[test]
fn offset_lookup_on_an_empty_plan_is_always_none() {
    assert_eq!(part_for_offset(&[], 0), None);
}
