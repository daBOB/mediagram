//! Trimming a download to exactly the bytes a range asked for.
//!
//! Telegram hands back whole 512 KiB chunks. A range almost never starts or
//! ends on a chunk boundary, so the first chunk has a head to discard and the
//! last a tail to cut. Getting either wrong shifts every following byte, which
//! is not an error message but a corrupt video, so this is checked against a
//! synthetic file whose bytes are known.

use mediagram_core::range::{ByteRange, CHUNK, PartSpan, Step, plan_reads};
use mediagram_core::stream::StepCursor;

fn step(skip_chunks: u32, head_drop: u64, take: u64) -> Step {
    Step {
        part_idx: 0,
        skip_chunks,
        head_drop,
        take,
    }
}

/// A file whose byte at offset `i` is derived from `i`, so a misplaced byte
/// is visible rather than plausible.
fn synthetic(len: u64) -> Vec<u8> {
    (0..len).map(|i| (i % 251) as u8).collect()
}

/// What Telegram would deliver for one part: whole chunks, a possibly short
/// last one.
fn chunks_of(part: &[u8]) -> Vec<Vec<u8>> {
    part.chunks(CHUNK as usize).map(|c| c.to_vec()).collect()
}

#[test]
fn a_cursor_with_nothing_to_drop_passes_a_chunk_through() {
    let mut cursor = StepCursor::new(&step(0, 0, 1000));
    let chunk = vec![7u8; 1000];

    assert_eq!(cursor.take(&chunk), &chunk[..]);
    assert!(cursor.is_done());
}

#[test]
fn the_head_of_the_first_chunk_is_dropped_once_not_every_time() {
    let mut cursor = StepCursor::new(&step(0, 10, 30));
    let first: Vec<u8> = (0..20u8).collect();
    let second: Vec<u8> = (100..120u8).collect();

    assert_eq!(
        cursor.take(&first),
        &first[10..],
        "first chunk loses its head"
    );
    assert_eq!(cursor.take(&second), &second[..], "later chunks do not");
    assert!(cursor.is_done());
}

#[test]
fn the_tail_past_the_requested_length_is_cut() {
    let mut cursor = StepCursor::new(&step(0, 0, 5));
    let chunk: Vec<u8> = (0..20u8).collect();

    assert_eq!(cursor.take(&chunk), &chunk[..5]);
    assert!(cursor.is_done());
    assert_eq!(cursor.remaining(), 0);
}

/// A head drop spanning more than the chunk in hand would mean the plan and
/// the download disagree; consume the chunk rather than emit wrong bytes.
#[test]
fn a_head_drop_larger_than_the_chunk_emits_nothing_yet() {
    let mut cursor = StepCursor::new(&step(0, 30, 10));
    let chunk = vec![1u8; 20];

    assert!(cursor.take(&chunk).is_empty());
    assert!(!cursor.is_done());
    assert_eq!(cursor.take(&[2u8; 20])[..], [2u8; 10][..]);
}

#[test]
fn a_finished_cursor_takes_nothing_more() {
    let mut cursor = StepCursor::new(&step(0, 0, 4));
    assert_eq!(cursor.take(&[9u8; 4]).len(), 4);
    assert!(cursor.take(&[9u8; 4]).is_empty());
}

/// The whole point, end to end without Telegram: for any range of a two-part
/// file, planning the reads and running the cursors over the chunks Telegram
/// would return must reproduce the source bytes exactly.
#[test]
fn planned_reads_reproduce_the_source_bytes_exactly() {
    const P0: u64 = 3 * CHUNK + 1234;
    const P1: u64 = 2 * CHUNK + 77;
    let file = synthetic(P0 + P1);
    let parts = vec![
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
    ];
    let part_bytes: Vec<Vec<Vec<u8>>> = vec![
        chunks_of(&file[..P0 as usize]),
        chunks_of(&file[P0 as usize..]),
    ];

    for (start, end) in [
        (0u64, 0u64),
        (0, file.len() as u64 - 1),
        (1, CHUNK),
        (CHUNK - 1, CHUNK + 1),
        (P0 - 5, P0 + 5),
        (P0, P0),
        (CHUNK + 999, 4 * CHUNK + 3),
        (file.len() as u64 - 2, file.len() as u64 - 1),
    ] {
        let range = ByteRange { start, end };
        let mut served = Vec::new();
        for step in plan_reads(&parts, &range) {
            let mut cursor = StepCursor::new(&step);
            for chunk in part_bytes[step.part_idx as usize]
                .iter()
                .skip(step.skip_chunks as usize)
            {
                served.extend_from_slice(cursor.take(chunk));
                if cursor.is_done() {
                    break;
                }
            }
            assert!(cursor.is_done(), "step {step:?} ran out of chunks");
        }
        assert_eq!(
            served,
            file[start as usize..=end as usize],
            "range {start}-{end} served the wrong bytes"
        );
    }
}
