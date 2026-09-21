//! TMDB accepts two kinds of credential, and they are sent differently.
//!
//! A v3 API key is 32 hex characters and goes in an `api_key` query
//! parameter. A v4 "API Read Access Token" is a JWT and goes in an
//! `Authorization: Bearer` header; sent as a query parameter it answers 401,
//! which is what prompted this.
//!
//! TMDB's settings page offers both, and people paste whichever they see
//! first, so the client works out which it was given rather than demanding
//! one.

use mediagram_tmdb::tmdb_client::{Credential, classify};

/// Shaped like a real token, with nothing real in it.
const JWT: &str = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ4In0.c2lnbmF0dXJl";

#[test]
fn a_read_access_token_is_sent_as_a_bearer_header() {
    assert_eq!(classify(JWT), Credential::Bearer);
}

#[test]
fn a_v3_api_key_stays_a_query_parameter() {
    assert_eq!(
        classify("0123456789abcdef0123456789abcdef"),
        Credential::QueryParam
    );
}

/// Whitespace from a copy-and-paste must not change the verdict, or a token
/// with a trailing newline would silently be sent the wrong way.
#[test]
fn surrounding_whitespace_does_not_change_the_verdict() {
    assert_eq!(classify(&format!("  {JWT}\n")), Credential::Bearer);
    assert_eq!(
        classify(" 0123456789abcdef0123456789abcdef "),
        Credential::QueryParam
    );
}

/// Anything unrecognised goes the v3 way: that is what the client has always
/// done, and a wrong guess there fails loudly with a 401 rather than quietly.
#[test]
fn something_unrecognised_falls_back_to_the_query_parameter() {
    for odd in ["", "not-a-key", "eyJ-but-not-a-jwt"] {
        assert_eq!(classify(odd), Credential::QueryParam, "{odd:?}");
    }
}

/// A JWT has three dot-separated parts; two is not one.
#[test]
fn a_truncated_token_is_not_treated_as_a_bearer() {
    assert_eq!(
        classify("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ4In0"),
        Credential::QueryParam
    );
}
