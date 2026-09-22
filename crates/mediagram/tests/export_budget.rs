//! The export refuses an oversized package before it downloads anything.
//! Refusing after the work is done wastes an entire run, and the reader's
//! memory ceiling is what makes the limit real.

use mediagram::export::budget::{Verdict, estimate_bytes, verdict_for};

#[test]
fn an_estimate_counts_the_index_and_every_poster() {
    let with_none = estimate_bytes(1_000_000, 0);
    let with_ten = estimate_bytes(1_000_000, 10);
    assert_eq!(with_none, 1_000_000);
    assert!(with_ten > with_none);
    // Ten posters cost ten times one poster, and a poster is a plausible
    // size. The previous form of this assertion was a tautology that held
    // for any implementation.
    let one = estimate_bytes(0, 1);
    assert_eq!(with_ten - with_none, one * 10);
    assert!(
        (8 * 1024..=128 * 1024).contains(&one),
        "poster estimate {one}"
    );
}

#[test]
fn a_typical_library_is_comfortable() {
    // 300 titles, a few megabytes of index.
    let verdict = verdict_for(estimate_bytes(4 * 1024 * 1024, 300));
    assert_eq!(verdict, Verdict::Fine);
}

#[test]
fn a_large_library_warns_before_it_refuses() {
    assert_eq!(
        verdict_for(30 * 1024 * 1024),
        Verdict::Large(30 * 1024 * 1024)
    );
}

#[test]
fn an_oversized_package_is_refused() {
    assert_eq!(
        verdict_for(60 * 1024 * 1024),
        Verdict::TooLarge(60 * 1024 * 1024)
    );
}

/// The export's own limit has to sit below what a reader can hold, since a
/// reader verifies the tag over the whole file before it sees any plaintext.
#[test]
fn the_export_limit_is_below_the_readers_limit() {
    let just_over = verdict_for(mlib_spec::package::MAX_PACKAGE_BYTES);
    assert_eq!(
        just_over,
        Verdict::TooLarge(mlib_spec::package::MAX_PACKAGE_BYTES)
    );
}

#[test]
fn an_empty_library_is_fine() {
    assert_eq!(verdict_for(estimate_bytes(0, 0)), Verdict::Fine);
}

#[test]
fn an_absurd_poster_count_cannot_overflow_the_estimate() {
    let huge = estimate_bytes(u64::MAX, u64::MAX);
    assert_eq!(huge, u64::MAX, "saturates instead of wrapping");
    assert!(matches!(verdict_for(huge), Verdict::TooLarge(_)));
}

/// The warn threshold is inclusive: exactly 24 MB already warns, not just
/// the byte after it.
#[test]
fn the_warn_threshold_includes_its_own_boundary_byte() {
    assert_eq!(verdict_for(24 * 1024 * 1024 - 1), Verdict::Fine);
    assert!(matches!(
        verdict_for(24 * 1024 * 1024),
        Verdict::Large(_)
    ));
    assert!(matches!(
        verdict_for(24 * 1024 * 1024 + 1),
        Verdict::Large(_)
    ));
}

/// Same shape as the warn threshold, one boundary up: exactly 48 MB already
/// refuses.
#[test]
fn the_refuse_threshold_includes_its_own_boundary_byte() {
    assert!(matches!(
        verdict_for(48 * 1024 * 1024 - 1),
        Verdict::Large(_)
    ));
    assert!(matches!(
        verdict_for(48 * 1024 * 1024),
        Verdict::TooLarge(_)
    ));
    assert!(matches!(
        verdict_for(48 * 1024 * 1024 + 1),
        Verdict::TooLarge(_)
    ));
}
