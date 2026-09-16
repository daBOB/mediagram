//! Range maths for the player's virtual file.
//!
//! A set's parts are raw byte ranges of one original file, so the virtual file
//! is their concatenation in `off` order. Resolving a Range request means
//! finding which parts overlap it and, within each, how many 512 KiB download
//! chunks to skip before the first useful byte.
//!
//! The fixture is the real film uploaded and verified against the channel:
//! two parts, 3,758,096,384 + 3,253,467,079 = 7,011,563,463 bytes.

use mediagram::serve::range::{
    ByteRange, CHUNK, PartSpan, RangeError, parse_range, plan_reads, total_size,
};

const P0: u64 = 3_758_096_384;
const P1: u64 = 3_253_467_079;
const TOTAL: u64 = P0 + P1;

fn film() -> Vec<PartSpan> {
    vec![
        PartSpan {
            idx: 0,
            off: 0,
            len: P0,
        },
        PartSpan {
            idx: 1,
            off: P0,
            len: P1,
        },
    ]
}

#[test]
fn the_virtual_file_is_the_sum_of_its_parts() {
    assert_eq!(total_size(&film()), TOTAL);
    assert_eq!(TOTAL, 7_011_563_463, "the real film's size");
}

#[test]
fn an_open_ended_range_runs_to_the_end() {
    let range = parse_range("bytes=0-", TOTAL).unwrap();
    assert_eq!(
        range,
        ByteRange {
            start: 0,
            end: TOTAL - 1
        }
    );
}

#[test]
fn a_closed_range_is_inclusive_as_http_defines_it() {
    let range = parse_range("bytes=0-499", TOTAL).unwrap();
    assert_eq!(range, ByteRange { start: 0, end: 499 });
    assert_eq!(range.length(), 500);
}

#[test]
fn a_suffix_range_counts_back_from_the_end() {
    let range = parse_range("bytes=-500", TOTAL).unwrap();
    assert_eq!(
        range,
        ByteRange {
            start: TOTAL - 500,
            end: TOTAL - 1
        }
    );
}

#[test]
fn a_suffix_larger_than_the_file_clamps_to_the_whole_file() {
    let range = parse_range(&format!("bytes=-{}", TOTAL + 10), TOTAL).unwrap();
    assert_eq!(range.start, 0);
}

#[test]
fn an_end_past_the_file_clamps_rather_than_failing() {
    let range = parse_range(&format!("bytes=0-{}", TOTAL + 1000), TOTAL).unwrap();
    assert_eq!(range.end, TOTAL - 1);
}

#[test]
fn a_start_past_the_end_of_the_file_is_unsatisfiable() {
    assert_eq!(
        parse_range("bytes=99999999999-", TOTAL),
        Err(RangeError::Unsatisfiable)
    );
    assert_eq!(
        parse_range(&format!("bytes={TOTAL}-"), TOTAL),
        Err(RangeError::Unsatisfiable)
    );
}

#[test]
fn a_backwards_range_is_unsatisfiable() {
    assert_eq!(
        parse_range("bytes=500-100", TOTAL),
        Err(RangeError::Unsatisfiable)
    );
}

#[test]
fn malformed_headers_are_rejected_as_malformed_not_unsatisfiable() {
    for bad in [
        "",
        "bytes=",
        "bytes=abc-def",
        "items=0-1",
        "bytes=-",
        "0-100",
    ] {
        assert_eq!(
            parse_range(bad, TOTAL),
            Err(RangeError::Malformed),
            "`{bad}` should be malformed"
        );
    }
}

/// A player asking for several ranges at once would need a multipart body.
/// Refusing is allowed and far simpler than implementing it; browsers fall
/// back to single ranges.
#[test]
fn a_multi_range_request_is_refused() {
    assert_eq!(
        parse_range("bytes=0-99,200-299", TOTAL),
        Err(RangeError::MultiRange)
    );
}

#[test]
fn a_range_inside_the_first_part_reads_only_that_part() {
    let range = ByteRange {
        start: 1_000_000,
        end: 1_999_999,
    };
    let steps = plan_reads(&film(), &range);

    assert_eq!(steps.len(), 1);
    assert_eq!(steps[0].part_idx, 0);
    assert_eq!(steps[0].take, 1_000_000);
    // 1,000,000 / 524,288 = 1 whole chunk, so skip one and drop the remainder.
    assert_eq!(steps[0].skip_chunks, 1);
    assert_eq!(steps[0].head_drop, 1_000_000 - CHUNK);
}

#[test]
fn a_range_inside_the_second_part_offsets_from_that_parts_start() {
    let start = P0 + 1_048_576; // exactly two chunks into part 1
    let range = ByteRange {
        start,
        end: start + 999,
    };
    let steps = plan_reads(&film(), &range);

    assert_eq!(steps.len(), 1);
    assert_eq!(steps[0].part_idx, 1);
    assert_eq!(steps[0].skip_chunks, 2);
    assert_eq!(
        steps[0].head_drop, 0,
        "a chunk-aligned offset wastes nothing"
    );
    assert_eq!(steps[0].take, 1000);
}

/// The case that only exists because files are split: a read that crosses
/// from one Telegram message into the next.
#[test]
fn a_range_spanning_the_part_boundary_reads_both_parts() {
    let range = ByteRange {
        start: P0 - 100,
        end: P0 + 99,
    };
    let steps = plan_reads(&film(), &range);

    assert_eq!(steps.len(), 2);
    assert_eq!(steps[0].part_idx, 0);
    assert_eq!(steps[0].take, 100, "the tail of part 0");
    assert_eq!(steps[1].part_idx, 1);
    assert_eq!(steps[1].skip_chunks, 0);
    assert_eq!(steps[1].head_drop, 0);
    assert_eq!(steps[1].take, 100, "the head of part 1");
    assert_eq!(steps.iter().map(|s| s.take).sum::<u64>(), range.length());
}

#[test]
fn a_whole_file_request_reads_every_part_completely() {
    let range = ByteRange {
        start: 0,
        end: TOTAL - 1,
    };
    let steps = plan_reads(&film(), &range);

    assert_eq!(steps.len(), 2);
    assert_eq!(steps[0].take, P0);
    assert_eq!(steps[1].take, P1);
    assert_eq!(steps.iter().map(|s| s.take).sum::<u64>(), TOTAL);
}

#[test]
fn the_last_byte_of_the_file_is_reachable() {
    let range = ByteRange {
        start: TOTAL - 1,
        end: TOTAL - 1,
    };
    let steps = plan_reads(&film(), &range);

    assert_eq!(steps.len(), 1);
    assert_eq!(steps[0].part_idx, 1);
    assert_eq!(steps[0].take, 1);
}

/// Whatever the range, the planned reads must cover it exactly: no missing
/// bytes, no extra. A wrong byte here is a corrupt video, not an error.
#[test]
fn planned_reads_always_cover_the_request_exactly() {
    let parts = film();
    for (start, end) in [
        (0u64, 0u64),
        (0, CHUNK - 1),
        (CHUNK - 1, CHUNK),
        (P0 - 1, P0),
        (P0, P0),
        (P0 - CHUNK, P0 + CHUNK),
        (12_345_678, 87_654_321),
        (TOTAL - 2, TOTAL - 1),
    ] {
        let range = ByteRange { start, end };
        let steps = plan_reads(&parts, &range);
        let covered: u64 = steps.iter().map(|s| s.take).sum();
        assert_eq!(
            covered,
            range.length(),
            "range {start}-{end} planned {steps:?}"
        );
        for step in &steps {
            let part = parts.iter().find(|p| p.idx == step.part_idx).unwrap();
            let read_start = step.skip_chunks as u64 * CHUNK + step.head_drop;
            assert!(
                read_start + step.take <= part.len,
                "step {step:?} reads past the end of part {}",
                part.idx
            );
        }
    }
}

/// A single-part set is the common case: 162 of the 163 uploaded sets.
#[test]
fn a_single_part_set_works_like_a_plain_file() {
    let parts = vec![PartSpan {
        idx: 0,
        off: 0,
        len: 69_136_013,
    }];
    assert_eq!(total_size(&parts), 69_136_013);

    let range = parse_range("bytes=0-", 69_136_013).unwrap();
    let steps = plan_reads(&parts, &range);
    assert_eq!(steps.len(), 1);
    assert_eq!(steps[0].take, 69_136_013);
}

#[test]
fn an_empty_set_has_no_size_and_plans_nothing() {
    assert_eq!(total_size(&[]), 0);
    assert!(plan_reads(&[], &ByteRange { start: 0, end: 0 }).is_empty());
}
