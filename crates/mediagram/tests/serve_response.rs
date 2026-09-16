//! What status and headers a Range request deserves.
//!
//! `Content-Length` is load-bearing rather than cosmetic: ffmpeg cannot seek
//! an HTTP source without it and falls back to reading from byte zero, and
//! Safari rejects a malformed `Content-Range` outright. Both are decided here,
//! away from the transport, so they can be asserted without a network.

use mediagram::serve::range::ByteRange;
use mediagram::serve::response::plan_response;

const TOTAL: u64 = 7_011_563_463;

#[test]
fn no_range_header_means_the_whole_file() {
    let plan = plan_response(None, TOTAL);

    assert_eq!(plan.status, 200);
    assert_eq!(plan.content_length, TOTAL);
    assert_eq!(plan.content_range, None);
    assert_eq!(
        plan.range,
        Some(ByteRange {
            start: 0,
            end: TOTAL - 1
        })
    );
}

#[test]
fn a_range_gets_206_with_a_content_range_naming_the_total() {
    let plan = plan_response(Some("bytes=0-499"), TOTAL);

    assert_eq!(plan.status, 206);
    assert_eq!(plan.content_length, 500);
    assert_eq!(
        plan.content_range.as_deref(),
        Some("bytes 0-499/7011563463")
    );
    assert_eq!(plan.range, Some(ByteRange { start: 0, end: 499 }));
}

/// What a browser sends first: everything from the start, as a range, so it
/// can learn the file is seekable.
#[test]
fn an_open_ended_range_is_still_partial_content() {
    let plan = plan_response(Some("bytes=0-"), TOTAL);

    assert_eq!(plan.status, 206);
    assert_eq!(plan.content_length, TOTAL);
    assert_eq!(
        plan.content_range.as_deref(),
        Some("bytes 0-7011563462/7011563463")
    );
}

#[test]
fn an_unsatisfiable_range_is_416_and_says_what_the_total_is() {
    let plan = plan_response(Some("bytes=99999999999-"), TOTAL);

    assert_eq!(plan.status, 416);
    assert_eq!(plan.range, None);
    assert_eq!(plan.content_range.as_deref(), Some("bytes */7011563463"));
}

/// Answering several ranges at once needs a multipart body. Refusing is
/// allowed, and every browser falls back to single ranges.
#[test]
fn a_multi_range_request_is_refused_as_unsatisfiable() {
    let plan = plan_response(Some("bytes=0-99,200-299"), TOTAL);

    assert_eq!(plan.status, 416);
    assert_eq!(plan.range, None);
}

#[test]
fn a_malformed_range_is_a_bad_request() {
    let plan = plan_response(Some("kilometres=0-99"), TOTAL);

    assert_eq!(plan.status, 400);
    assert_eq!(plan.range, None);
    assert_eq!(plan.content_range, None);
}

/// Whatever the outcome, the length of the body we intend to send is known
/// before the first byte is fetched. Nothing downstream has to guess.
#[test]
fn every_plan_states_a_content_length() {
    for header in [
        None,
        Some("bytes=0-"),
        Some("bytes=-500"),
        Some("bytes=1000-1999"),
        Some("bytes=99999999999-"),
        Some("nonsense"),
    ] {
        let plan = plan_response(header, TOTAL);
        match plan.range {
            Some(range) => assert_eq!(plan.content_length, range.length()),
            None => assert_eq!(plan.content_length, 0),
        }
    }
}
