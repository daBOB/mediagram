use super::*;
use crate::index::sets_pending::SkippedKind;

fn ids(n: usize) -> Vec<String> {
    (1..=n).map(|i| format!("set{i}")).collect()
}

/// A merge that found nothing says so in one line: no counts of zero.
#[test]
fn a_merge_that_found_nothing_says_only_that() {
    assert_eq!(lines(&MergeReport::default(), false), ["added 0 set(s)"]);
    assert_eq!(lines(&MergeReport::default(), true), ["would add 0 set(s)"]);
}

/// Hundreds of added sets are named by their first few and counted.
#[test]
fn added_sets_are_named_up_to_five_and_the_rest_counted() {
    let many = MergeReport {
        sets_added: ids(7),
        ..MergeReport::default()
    };
    assert_eq!(
        lines(&many, false),
        [
            "added 7 set(s)",
            "  e.g. set1, set2, set3, set4, set5 and 2 more"
        ]
    );

    let five = MergeReport {
        sets_added: ids(5),
        ..MergeReport::default()
    };
    assert_eq!(
        lines(&five, true),
        ["would add 5 set(s)", "  e.g. set1, set2, set3, set4, set5"]
    );
}

#[test]
fn every_count_that_is_not_zero_gets_its_line_in_order() {
    let report = MergeReport {
        sets_skipped_pending: ids(2),
        sets_skipped_removed: ids(3),
        shows_filled: 4,
        credits_added: 5,
        franchises_added: 6,
        artwork_added: 7,
        anime_overrides_taken: 8,
        categories_taken: 9,
        subtitles_taken: 10,
        ..MergeReport::default()
    };

    assert_eq!(
        lines(&report, false),
        [
            "added 0 set(s)",
            "2 channel set(s) still pending elsewhere, skipped",
            "3 channel set(s) no longer exist in the channel, skipped",
            "0 show(s) added, 4 filled in",
            "5 credit row(s) added",
            "6 franchise(s) added",
            "7 artwork row(s) added",
            "8 anime override(s) taken",
            "9 category row(s) taken",
            "10 subtitle file(s) taken",
        ]
    );
}

/// The one line that asks the operator to act: publish again, or the
/// channel stays without the subtitles an older uploader dropped.
#[test]
fn a_channel_without_subtitle_tables_is_warned_about() {
    let report = MergeReport {
        channel_lacks_subtitles: true,
        ..MergeReport::default()
    };

    let said = lines(&report, false);

    assert_eq!(said.len(), 2);
    assert!(said[1].starts_with("warning:"), "{said:?}");
    assert!(said[1].contains("publish again"), "{said:?}");
}

#[test]
fn no_conflicts_say_nothing() {
    assert!(conflict_lines(0, &ResolveSummary::default()).is_empty());
}

/// How many conflicts were settled, which newer kinds were left alone, and
/// how many captions this build could not read, in that order.
#[test]
fn conflicts_say_how_many_were_settled_and_what_was_left() {
    let summary = ResolveSummary {
        resolved: 2,
        skipped_kinds: vec![SkippedKind {
            kind: "vr".to_string(),
            count: 1,
        }],
        newer_captions: 3,
    };

    let said = conflict_lines(4, &summary);

    assert_eq!(said.len(), 3);
    assert_eq!(said[0], "2 of 4 conflicting set(s) re-read from captions");
    assert!(said[1].contains("kind 'vr'"), "{said:?}");
    assert_eq!(
        said[2],
        "3 caption(s) among the conflicts use a newer #mlib version this build cannot read"
    );
}
