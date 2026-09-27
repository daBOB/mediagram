use super::*;

/// A moment after every stamp these tests write.
const NOW: i64 = 1_800_000_000;

/// Captions with ascending ids, which is the order a channel hands them
/// back in; the ids only ever break a tie.
fn found<'a>(texts: &[&'a str]) -> Vec<(&'a str, i64)> {
    texts
        .iter()
        .enumerate()
        .map(|(i, text)| (*text, i as i64))
        .collect()
}

#[test]
fn what_is_written_reads_back() {
    let caption = render(1_781_568_000, 538);
    assert!(is_index(&caption));
    assert!(caption.starts_with(MARKER));
    assert_eq!(pushed_at(&caption), Some(1_781_568_000));
    assert!(caption.ends_with(&format!(
        "{{\"pushed_at\":1781568000,\"schema\":{},\"sets\":538}}",
        crate::schema::SCHEMA_VERSION
    )));
}

#[test]
fn a_part_caption_is_not_an_index() {
    assert!(!is_index("#mlib v=2\n{}"));
}

#[test]
fn the_one_index_among_other_messages_is_the_one_chosen() {
    let index = render(1_781_568_000, 538);
    assert_eq!(
        newest(&found(&["a note", &index, "another note"]), NOW),
        Some(1)
    );
}

/// The prefix, not the exact marker: a snapshot written by a later
/// uploader is still the index this channel holds.
#[test]
fn a_later_snapshot_version_is_still_recognised_as_the_index() {
    assert_eq!(newest(&found(&["#mlib-index v=3\n{}"]), NOW), Some(0));
}

#[test]
fn nothing_that_is_an_index_chooses_nothing() {
    assert_eq!(newest(&[], NOW), None);
    assert_eq!(newest(&found(&["welcome to the channel"]), NOW), None);
}

/// Two machines publishing to one channel is an ordinary state, not an
/// impasse: the later snapshot is the library, and refusing to choose
/// between them is how a reader ends up on the older one.
#[test]
fn the_later_snapshot_wins_however_the_pins_fell() {
    let (old, new) = (render(1_781_568_000, 538), render(1_789_946_371, 538));
    assert_eq!(newest(&found(&[&old, &new]), NOW), Some(1));
    assert_eq!(newest(&found(&[&new, &old]), NOW), Some(0));
}

/// A snapshot whose timestamp this build cannot read is still an index,
/// but it cannot outrank one that says when it was made.
#[test]
fn a_dated_snapshot_outranks_an_undated_one() {
    let dated = render(1_781_568_000, 538);
    assert_eq!(newest(&found(&["#mlib-index v=9", &dated]), NOW), Some(1));
    assert_eq!(newest(&found(&[&dated, "#mlib-index v=9"]), NOW), Some(0));
}

/// Two snapshots pushed in the same second still resolve the same way on
/// every reader, or two of them disagree about what the library is.
#[test]
fn snapshots_of_one_second_are_broken_by_the_later_message() {
    let same = render(1_789_946_371, 538);
    assert_eq!(newest(&[(&same, 10), (&same, 11)], NOW), Some(1));
    assert_eq!(newest(&[(&same, 11), (&same, 10)], NOW), Some(0));
}

/// A snapshot dated far ahead would otherwise win every later choice. Its
/// stamp is not believed, so it loses to a real one like any caption whose
/// time cannot be read.
#[test]
fn a_stamp_from_the_future_is_not_believed() {
    let real = render(NOW - 60, 538);
    let future = render(NOW + 400 * 24 * 60 * 60, 538);
    assert_eq!(newest(&found(&[&future, &real]), NOW), Some(1));
    assert_eq!(newest(&found(&[&real, &future]), NOW), Some(0));
    assert_eq!(believed_pushed_at(&future, NOW), None);
    assert_eq!(
        believed_pushed_at(&render(NOW + 60, 538), NOW),
        Some(NOW + 60)
    );
}
