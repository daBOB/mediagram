//! The HTTP contract of `mediagram serve`, over a real socket.
//!
//! These assertions are about what goes on the wire, so they are made against
//! a bound listener rather than a handler in isolation: `Content-Length` on a
//! streaming body is exactly the kind of header that is present in the code
//! and absent from the response. ffmpeg cannot seek without it.
//!
//! Telegram is replaced by a source that serves a known synthetic file, so a
//! wrong byte is visible as a wrong byte.

use std::sync::Arc;

use mediagram::index::{db, parts, set_row::SetRow, sets};
use mediagram::serve::routes::router;
use mediagram_core::catalog::PartLocation;
use mediagram_core::range::{CHUNK, Step};
use mediagram_core::transport::stream::{ByteSource, ByteStream};
use mlib_spec::PartRange;
use mlib_spec::caption::{Caption, Kind, Part};
use mlib_spec::ids::ProviderIds;

const SET: &str = "01SET0000000000000000009";
const P0: u64 = 2 * CHUNK + 100;
const P1: u64 = CHUNK + 50;
const TOTAL: u64 = P0 + P1;

fn synthetic(len: u64) -> Vec<u8> {
    (0..len).map(|i| (i % 251) as u8).collect()
}

/// Stands in for Telegram: resolves each planned read against the bytes of a
/// file we hold, exactly as the transport would resolve it against a message.
struct FakeSource {
    file: Vec<u8>,
}

impl ByteSource for FakeSource {
    fn stream(&self, locations: Vec<PartLocation>, steps: Vec<Step>) -> ByteStream {
        let mut out = Vec::new();
        for step in steps {
            let span = locations
                .iter()
                .find(|l| l.span.idx == step.part_idx)
                .expect("a step names a located part")
                .span;
            let from = span.off + step.skip_chunks as u64 * CHUNK + step.head_drop;
            out.extend_from_slice(&self.file[from as usize..(from + step.take) as usize]);
        }
        Box::pin(futures::stream::once(async move {
            Ok(axum::body::Bytes::from(out))
        }))
    }
}

fn index() -> (tempfile::TempDir, rusqlite::Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let caption = Caption {
        t: Kind::Movie,
        ids: ProviderIds {
            tmdb: Some(603),
            tvdb: None,
            imdb: None,
        },
        cid: None,
        show: None,
        chap: None,
        path: None,
        title: Some("The Matrix".into()),
        year: Some(1999),
        s: None,
        e: None,
        abs: None,
        q: Some("1080p".into()),
        hdr: None,
        container: "mkv".into(),
        vcodec: Some("hevc".into()),
        acodec: Some("ac3".into()),
        alang: vec!["eng".into()],
        slang: vec![],
        dur: Some(8160),
        variant: None,
        set: SET.into(),
        part: Part {
            i: 0,
            n: 2,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total: TOTAL,
    };
    let row = SetRow::from_caption(&caption, 1_700_000_000);
    sets::insert_set(&conn, &row).unwrap();
    parts::insert_parts(
        &conn,
        SET,
        &[
            PartRange {
                idx: 0,
                off: 0,
                len: P0,
            },
            PartRange {
                idx: 1,
                off: P0,
                len: P1,
            },
        ],
    )
    .unwrap();
    for idx in 0..2u32 {
        parts::mark_done(
            &conn,
            SET,
            idx,
            &parts::Landed {
                chat_id: -1001,
                message_id: 100 + idx as i64,
                doc_id: 900 + idx as i64,
                sha256: "a".repeat(64),
            },
        )
        .unwrap();
    }
    sets::set_hash_and_complete(&conn, SET, &"b".repeat(64)).unwrap();
    (dir, conn)
}

/// Starts the real server on an ephemeral port and returns its base URL.
async fn start() -> (tempfile::TempDir, String, Vec<u8>) {
    let (dir, conn) = index();
    let file = synthetic(TOTAL);
    let app = router(conn, Arc::new(FakeSource { file: file.clone() }));
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    tokio::spawn(async move {
        axum::serve(listener, app).await.unwrap();
    });
    (dir, format!("http://{addr}"), file)
}

fn length_of(response: &reqwest::Response) -> u64 {
    response
        .headers()
        .get("content-length")
        .unwrap_or_else(|| panic!("no Content-Length on {}", response.status()))
        .to_str()
        .unwrap()
        .parse()
        .unwrap()
}

#[tokio::test]
async fn the_catalog_lists_what_is_playable() {
    let (_d, base, _file) = start().await;

    let response = mediagram_core::http::client()
        .unwrap()
        .get(format!("{base}/sets"))
        .send()
        .await
        .unwrap();
    assert_eq!(response.status(), 200);
    let body: serde_json::Value = response.json().await.unwrap();

    let listed = body.as_array().expect("an array of sets");
    assert_eq!(listed.len(), 1);
    assert_eq!(listed[0]["set_id"], SET);
    assert_eq!(listed[0]["title"], "The Matrix");
    assert_eq!(listed[0]["total"], TOTAL);
    assert_eq!(listed[0]["vcodec"], "hevc");
}

#[tokio::test]
async fn a_request_without_a_range_streams_the_whole_file() {
    let (_d, base, file) = start().await;

    let response = mediagram_core::http::client()
        .unwrap()
        .get(format!("{base}/sets/{SET}/stream"))
        .send()
        .await
        .unwrap();

    assert_eq!(response.status(), 200);
    assert_eq!(response.headers()["accept-ranges"], "bytes");
    assert_eq!(length_of(&response), TOTAL);
    assert_eq!(response.bytes().await.unwrap().to_vec(), file);
}

#[tokio::test]
async fn an_open_ended_range_is_partial_content_with_the_whole_remainder() {
    let (_d, base, file) = start().await;

    let response = mediagram_core::http::client()
        .unwrap()
        .get(format!("{base}/sets/{SET}/stream"))
        .header("Range", "bytes=0-")
        .send()
        .await
        .unwrap();

    assert_eq!(response.status(), 206);
    assert_eq!(
        response.headers()["content-range"],
        format!("bytes 0-{}/{TOTAL}", TOTAL - 1).as_str()
    );
    assert_eq!(length_of(&response), TOTAL);
    assert_eq!(response.bytes().await.unwrap().to_vec(), file);
}

/// The case that only exists because a file is split across messages.
#[tokio::test]
async fn a_range_across_the_part_boundary_returns_exactly_those_bytes() {
    let (_d, base, file) = start().await;
    let (start_byte, end_byte) = (P0 - 1000, P0 + 999);

    let response = mediagram_core::http::client()
        .unwrap()
        .get(format!("{base}/sets/{SET}/stream"))
        .header("Range", format!("bytes={start_byte}-{end_byte}"))
        .send()
        .await
        .unwrap();

    assert_eq!(response.status(), 206);
    assert_eq!(length_of(&response), 2000);
    assert_eq!(
        response.headers()["content-range"],
        format!("bytes {start_byte}-{end_byte}/{TOTAL}").as_str()
    );
    assert_eq!(
        response.bytes().await.unwrap().to_vec(),
        file[start_byte as usize..=end_byte as usize]
    );
}

#[tokio::test]
async fn a_suffix_range_returns_the_end_of_the_file() {
    let (_d, base, file) = start().await;

    let response = mediagram_core::http::client()
        .unwrap()
        .get(format!("{base}/sets/{SET}/stream"))
        .header("Range", "bytes=-500")
        .send()
        .await
        .unwrap();

    assert_eq!(response.status(), 206);
    assert_eq!(length_of(&response), 500);
    assert_eq!(
        response.bytes().await.unwrap().to_vec(),
        file[file.len() - 500..]
    );
}

#[tokio::test]
async fn an_unsatisfiable_range_is_refused_with_the_total_size() {
    let (_d, base, _file) = start().await;

    let response = mediagram_core::http::client()
        .unwrap()
        .get(format!("{base}/sets/{SET}/stream"))
        .header("Range", "bytes=99999999999-")
        .send()
        .await
        .unwrap();

    assert_eq!(response.status(), 416);
    assert_eq!(
        response.headers()["content-range"],
        format!("bytes */{TOTAL}").as_str()
    );
    assert_eq!(length_of(&response), 0);
}

/// RFC 9110 14.2: an origin server MUST ignore a Range header field that
/// contains a range unit it does not understand. Ignoring it means serving the
/// whole representation, which every client can use; refusing it means breaking
/// playback over a header the client did not need us to honour.
#[tokio::test]
async fn a_range_in_units_we_do_not_speak_is_ignored_not_refused() {
    let (_d, base, _file) = start().await;

    let response = mediagram_core::http::client()
        .unwrap()
        .get(format!("{base}/sets/{SET}/stream"))
        .header("Range", "kilometres=0-99")
        .send()
        .await
        .unwrap();

    assert_eq!(response.status(), 200);
    assert_eq!(length_of(&response), TOTAL);
    assert!(!response.headers().contains_key("content-range"));
}

#[tokio::test]
async fn a_byte_range_we_cannot_parse_is_ignored_the_same_way() {
    let (_d, base, _file) = start().await;

    let response = mediagram_core::http::client()
        .unwrap()
        .get(format!("{base}/sets/{SET}/stream"))
        .header("Range", "bytes=abc-def")
        .send()
        .await
        .unwrap();

    assert_eq!(response.status(), 200);
    assert_eq!(length_of(&response), TOTAL);
}

/// What a player asks before it asks for bytes.
#[tokio::test]
async fn head_reports_the_size_and_that_ranges_are_supported() {
    let (_d, base, _file) = start().await;

    let response = mediagram_core::http::client()
        .unwrap()
        .head(format!("{base}/sets/{SET}/stream"))
        .send()
        .await
        .unwrap();

    assert_eq!(response.status(), 200);
    assert_eq!(length_of(&response), TOTAL);
    assert_eq!(response.headers()["accept-ranges"], "bytes");
    assert!(response.bytes().await.unwrap().is_empty());
}

#[tokio::test]
async fn a_set_that_is_not_playable_is_not_found() {
    let (_d, base, _file) = start().await;

    let response = mediagram_core::http::client()
        .unwrap()
        .get(format!("{base}/sets/01NOSUCHSET00000000000001/stream"))
        .send()
        .await
        .unwrap();

    assert_eq!(response.status(), 404);
    assert_eq!(length_of(&response), 0);
}
