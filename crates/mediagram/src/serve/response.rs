//! Deciding the status and headers of a stream response.
//!
//! Kept apart from the router because two of these decisions are easy to get
//! subtly wrong and expensive to debug through a video player: an absent
//! `Content-Length` makes ffmpeg read from byte zero instead of seeking, and a
//! `Content-Range` that disagrees with the body makes Safari refuse to play.

use super::range::{ByteRange, RangeError, parse_range};

/// The answer to a stream request, decided before a single byte is fetched.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ResponsePlan {
    pub status: u16,
    /// The bytes to send, or `None` when there is no body to send.
    pub range: Option<ByteRange>,
    /// Always stated, including on a 206 and on an error.
    pub content_length: u64,
    pub content_range: Option<String>,
}

/// Plans the response to a `Range` header (or its absence) against a set's
/// total size.
pub fn plan_response(range_header: Option<&str>, total: u64) -> ResponsePlan {
    let Some(header) = range_header else {
        let range = total.checked_sub(1).map(|end| ByteRange { start: 0, end });
        return ResponsePlan {
            status: 200,
            range,
            content_length: total,
            content_range: None,
        };
    };

    match parse_range(header, total) {
        Ok(range) => ResponsePlan {
            status: 206,
            content_length: range.length(),
            content_range: Some(format!("bytes {}-{}/{}", range.start, range.end, total)),
            range: Some(range),
        },
        // Understood but outside the file, or several ranges at once, which
        // this server refuses rather than answering with a multipart body.
        Err(RangeError::Unsatisfiable | RangeError::MultiRange) => ResponsePlan {
            status: 416,
            range: None,
            content_length: 0,
            content_range: Some(format!("bytes */{total}")),
        },
        // Anything we could not parse is ignored, as RFC 9110 14.2 requires of
        // a unit we do not understand — which is to say, answered exactly as a
        // request carrying no Range at all. The client asked for less than we
        // sent, which every client copes with; refusing outright would break
        // playback over a header it did not need us to honour.
        Err(RangeError::Malformed) => plan_response(None, total),
    }
}
