//! Verification orchestration against real index rows and streamed fixture bytes.

use std::cell::RefCell;
use std::collections::HashMap;

use anyhow::{Result, bail};
use mlib_spec::part_plan::PartRange;
use rusqlite::Connection;

use super::{SetPlan, verify_with};
use crate::index::{db, parts, sets};
use crate::verify::download_hash::ChunkSource;
use crate::verify::mark_verified;
use crate::verify::source::{RemoteMessage, VerificationSource};

const SET_ID: &str = "01SET0000000000000000001";
const CHAT_ID: i64 = -1001;
const OLD_SUCCESS: i64 = 1_700_000_000;
// SHA-256 of the fixture bytes "abc"; the source supplies bytes, never hashes.
const ABC_SHA256: &str = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

fn fixture(count: u32) -> (tempfile::TempDir, Connection, SetPlan) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'movie', 'mkv', ?2, ?3, 'complete', 0, 1)",
        rusqlite::params![SET_ID, u64::from(count) * 3, count],
    )
    .unwrap();
    let ranges: Vec<_> = (0..count)
        .map(|idx| PartRange {
            idx,
            off: u64::from(idx) * 3,
            len: 3,
        })
        .collect();
    parts::insert_parts(&conn, SET_ID, &ranges).unwrap();
    for idx in 0..count {
        parts::mark_done(
            &conn,
            SET_ID,
            idx,
            &parts::Landed {
                chat_id: CHAT_ID,
                message_id: 100 + i64::from(idx),
                doc_id: 1000 + i64::from(idx),
                sha256: ABC_SHA256.into(),
            },
        )
        .unwrap();
        mark_verified(&conn, SET_ID, idx, OLD_SUCCESS).unwrap();
    }
    let plan = SetPlan {
        set_id: SET_ID.into(),
        row: sets::get_set(&conn, SET_ID).unwrap().unwrap(),
        parts: parts::all_parts(&conn, SET_ID).unwrap(),
    };
    (dir, conn, plan)
}

type Chunk = std::result::Result<Vec<u8>, &'static str>;

struct FixtureChunks(std::vec::IntoIter<Chunk>);

impl ChunkSource for FixtureChunks {
    async fn next(&mut self) -> Result<Option<Vec<u8>>> {
        self.0.next().transpose().map_err(anyhow::Error::msg)
    }
}

struct FixtureSource {
    held: HashMap<i32, RemoteMessage<usize>>,
    streams: Vec<Vec<Chunk>>,
    fetched: RefCell<Vec<Vec<i32>>>,
    downloaded: RefCell<Vec<usize>>,
    fail_fetch: bool,
}

impl FixtureSource {
    fn new(count: usize) -> Self {
        Self {
            held: (0..count)
                .map(|idx| {
                    (
                        100 + idx as i32,
                        RemoteMessage::Document {
                            document: idx,
                            id: 1000 + idx as i64,
                            size: Some(3),
                        },
                    )
                })
                .collect(),
            streams: vec![vec![Ok(b"a".to_vec()), Ok(b"bc".to_vec())]; count],
            fetched: RefCell::default(),
            downloaded: RefCell::default(),
            fail_fetch: false,
        }
    }

    fn set_size(&mut self, message_id: i32, size: Option<u64>) {
        let RemoteMessage::Document { size: held, .. } = self.held.get_mut(&message_id).unwrap()
        else {
            panic!("fixture message must contain a document");
        };
        *held = size;
    }
}

impl VerificationSource for FixtureSource {
    type Document = usize;
    type Chunks = FixtureChunks;

    async fn messages(&self, ids: &[i32]) -> Result<HashMap<i32, RemoteMessage<usize>>> {
        self.fetched.borrow_mut().push(ids.to_vec());
        if self.fail_fetch {
            bail!("message retrieval unavailable");
        }
        Ok(ids
            .iter()
            .filter_map(|message_id| {
                self.held.get(message_id).map(|message| {
                    let copied = match message {
                        RemoteMessage::NoDocument => RemoteMessage::NoDocument,
                        RemoteMessage::Document { document, id, size } => RemoteMessage::Document {
                            document: *document,
                            id: *id,
                            size: *size,
                        },
                    };
                    (*message_id, copied)
                })
            })
            .collect())
    }

    fn download(&self, document: &usize) -> FixtureChunks {
        self.downloaded.borrow_mut().push(*document);
        FixtureChunks(self.streams[*document].clone().into_iter())
    }
}

#[tokio::test]
async fn full_verification_hashes_chunks_and_persists_a_fresh_success() {
    let (_dir, conn, plan) = fixture(1);
    let source = FixtureSource::new(1);
    let started = crate::clock::now_unix();

    let report = verify_with(&conn, &source, CHAT_ID, &plan, true, None)
        .await
        .unwrap();

    assert!(!report.failed());
    assert_eq!(report.set_id, SET_ID);
    assert_eq!(report.parts[0].hash_ok, Some(true));
    let verified_at = report.parts[0].verified_at.unwrap();
    assert!((started..=crate::clock::now_unix()).contains(&verified_at));
    assert_eq!(
        parts::all_parts(&conn, SET_ID).unwrap()[0].verified_at,
        Some(verified_at)
    );
    assert_eq!(*source.fetched.borrow(), vec![vec![100]]);
    assert_eq!(*source.downloaded.borrow(), vec![0]);
}

#[tokio::test]
async fn wrong_or_unknown_size_clears_stale_success_without_downloading() {
    for size in [Some(2), None] {
        let (_dir, conn, plan) = fixture(1);
        let mut source = FixtureSource::new(1);
        source.set_size(100, size);

        let report = verify_with(&conn, &source, CHAT_ID, &plan, true, None)
            .await
            .unwrap();

        assert!(report.failed());
        assert!(
            report.parts[0]
                .failure
                .as_deref()
                .unwrap()
                .contains("size mismatch")
        );
        assert_eq!(report.parts[0].hash_ok, None);
        assert_eq!(report.parts[0].verified_at, None);
        assert_eq!(
            parts::all_parts(&conn, SET_ID).unwrap()[0].verified_at,
            None
        );
        assert_eq!(*source.fetched.borrow(), vec![vec![100]]);
        assert!(source.downloaded.borrow().is_empty());
    }
}

#[tokio::test]
async fn different_bytes_of_the_same_size_fail_the_hash_and_clear_stale_success() {
    let (_dir, conn, plan) = fixture(1);
    let mut source = FixtureSource::new(1);
    source.streams[0] = vec![Ok(b"abd".to_vec())];

    let report = verify_with(&conn, &source, CHAT_ID, &plan, true, None)
        .await
        .unwrap();

    assert!(report.failed());
    assert!(report.parts[0].size_ok);
    assert_eq!(report.parts[0].hash_ok, Some(false));
    assert!(
        report.parts[0]
            .failure
            .as_deref()
            .unwrap()
            .contains("hash mismatch")
    );
    assert_eq!(report.parts[0].verified_at, None);
    assert_eq!(
        parts::all_parts(&conn, SET_ID).unwrap()[0].verified_at,
        None
    );
    assert_eq!(*source.fetched.borrow(), vec![vec![100]]);
    assert_eq!(*source.downloaded.borrow(), vec![0]);
}

#[tokio::test]
async fn a_download_failure_clears_stale_success_and_the_next_part_is_still_verified() {
    let (_dir, conn, plan) = fixture(2);
    let mut source = FixtureSource::new(2);
    source.streams[0] = vec![Ok(b"a".to_vec()), Err("connection reset")];

    let report = verify_with(&conn, &source, CHAT_ID, &plan, true, None)
        .await
        .unwrap();

    assert!(report.failed());
    assert_eq!(report.parts.len(), 2);
    assert_eq!(
        report.parts[0].failure.as_deref(),
        Some("download failed: connection reset")
    );
    assert_eq!(report.parts[0].hash_ok, None);
    assert_eq!(report.parts[0].verified_at, None);
    assert_eq!(report.parts[1].idx, 1);
    assert!(!report.parts[1].failed());
    assert_eq!(report.parts[1].hash_ok, Some(true));
    let stored = parts::all_parts(&conn, SET_ID).unwrap();
    assert_eq!(stored[0].verified_at, None);
    assert_eq!(stored[1].verified_at, report.parts[1].verified_at);
    assert!(stored[1].verified_at.unwrap() > OLD_SUCCESS);
    assert_eq!(*source.fetched.borrow(), vec![vec![100, 101]]);
    assert_eq!(*source.downloaded.borrow(), vec![0, 1]);
}

#[tokio::test]
async fn truncated_chunks_are_a_download_failure_and_clear_stale_success() {
    let (_dir, conn, plan) = fixture(1);
    let mut source = FixtureSource::new(1);
    source.streams[0] = vec![Ok(b"ab".to_vec())];

    let report = verify_with(&conn, &source, CHAT_ID, &plan, true, None)
        .await
        .unwrap();

    assert!(report.failed());
    assert_eq!(
        report.parts[0].failure.as_deref(),
        Some("download failed: download ended after 2 of 3 bytes")
    );
    assert_eq!(report.parts[0].hash_ok, None);
    assert_eq!(report.parts[0].verified_at, None);
    assert_eq!(
        parts::all_parts(&conn, SET_ID).unwrap()[0].verified_at,
        None
    );
    assert_eq!(*source.fetched.borrow(), vec![vec![100]]);
    assert_eq!(*source.downloaded.borrow(), vec![0]);
}

#[tokio::test]
async fn since_skips_hashing_at_the_cutoff_but_still_checks_metadata() {
    let (_dir, conn, plan) = fixture(2);
    let mut source = FixtureSource::new(2);
    source.set_size(101, Some(4));

    let report = verify_with(&conn, &source, CHAT_ID, &plan, true, Some(OLD_SUCCESS))
        .await
        .unwrap();

    assert!(report.failed());
    assert!(!report.parts[0].failed());
    assert_eq!(report.parts[0].hash_ok, None);
    assert_eq!(report.parts[0].verified_at, Some(OLD_SUCCESS));
    assert!(
        report.parts[1]
            .failure
            .as_deref()
            .unwrap()
            .contains("size mismatch")
    );
    assert_eq!(report.parts[1].verified_at, None);
    let stored = parts::all_parts(&conn, SET_ID).unwrap();
    assert_eq!(stored[0].verified_at, Some(OLD_SUCCESS));
    assert_eq!(stored[1].verified_at, None);
    assert_eq!(*source.fetched.borrow(), vec![vec![100, 101]]);
    assert!(source.downloaded.borrow().is_empty());
}

#[tokio::test]
async fn metadata_only_verification_keeps_a_prior_success_without_downloading() {
    let (_dir, conn, plan) = fixture(1);
    let source = FixtureSource::new(1);

    let report = verify_with(&conn, &source, CHAT_ID, &plan, false, None)
        .await
        .unwrap();

    assert!(!report.failed());
    assert_eq!(report.parts[0].hash_ok, None);
    assert_eq!(report.parts[0].verified_at, Some(OLD_SUCCESS));
    assert_eq!(parts::all_parts(&conn, SET_ID).unwrap(), plan.parts);
    assert_eq!(*source.fetched.borrow(), vec![vec![100]]);
    assert!(source.downloaded.borrow().is_empty());
}

#[tokio::test]
async fn missing_media_wrong_chat_and_pending_parts_do_not_download() {
    let (_dir, conn, mut plan) = fixture(4);
    conn.execute("UPDATE parts SET chat_id = -1002 WHERE idx = 2", [])
        .unwrap();
    conn.execute("UPDATE parts SET status = 'pending' WHERE idx = 3", [])
        .unwrap();
    plan.parts = parts::all_parts(&conn, SET_ID).unwrap();
    let mut source = FixtureSource::new(4);
    source.held.remove(&100);
    source.held.insert(101, RemoteMessage::NoDocument);

    let report = verify_with(&conn, &source, CHAT_ID, &plan, true, None)
        .await
        .unwrap();

    let failures = [
        "message not found",
        "no usable document",
        "recorded in chat -1002, verifying chat -1001",
        "no upload recorded",
    ];
    assert_eq!(report.parts.len(), failures.len());
    for (verdict, expected) in report.parts.iter().zip(failures) {
        assert!(verdict.failure.as_deref().unwrap().contains(expected));
        assert_eq!(verdict.hash_ok, None);
        assert_eq!(verdict.verified_at, None);
    }
    assert!(
        parts::all_parts(&conn, SET_ID)
            .unwrap()
            .iter()
            .all(|part| part.verified_at.is_none())
    );
    assert_eq!(*source.fetched.borrow(), vec![vec![100, 101]]);
    assert!(source.downloaded.borrow().is_empty());
}

#[tokio::test]
async fn message_retrieval_failure_preserves_previous_verification() {
    let (_dir, conn, plan) = fixture(2);
    let mut source = FixtureSource::new(2);
    source.fail_fetch = true;

    let error = verify_with(&conn, &source, CHAT_ID, &plan, true, None)
        .await
        .unwrap_err();

    assert_eq!(error.to_string(), "message retrieval unavailable");
    assert_eq!(parts::all_parts(&conn, SET_ID).unwrap(), plan.parts);
    assert_eq!(*source.fetched.borrow(), vec![vec![100, 101]]);
    assert!(source.downloaded.borrow().is_empty());
}
