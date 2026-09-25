//! Exercises the production retry and delivery loops with raw chunk IO fixtures.

use std::collections::VecDeque;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use anyhow::{Result, anyhow, bail};
use grammers_mtsender::{InvocationError, RpcError};
use tokio::sync::{Notify, mpsc};

use super::{PartIo, fetch_step};
use crate::catalog::PartLocation;
use crate::range::{CHUNK, PartSpan, Step};
use crate::transport::stream::{ChunkSource, StepCursor, pump_chunks};

#[derive(Debug, PartialEq)]
enum Event {
    Resolve(i64, i64),
    Evict(i64, i64),
    Download(usize, u32),
}

struct Chunks {
    values: std::vec::IntoIter<Result<Vec<u8>>>,
    calls: Arc<AtomicUsize>,
}

impl ChunkSource for Chunks {
    async fn next(&mut self) -> Result<Option<Vec<u8>>> {
        self.calls.fetch_add(1, Ordering::SeqCst);
        self.values.next().transpose()
    }
}

struct ScriptedIo {
    attempts: Mutex<VecDeque<Vec<Result<Vec<u8>>>>>,
    events: Mutex<Vec<Event>>,
    resolved: AtomicUsize,
    chunk_calls: Arc<AtomicUsize>,
    fail_resolve: bool,
}

impl ScriptedIo {
    fn new(attempts: Vec<Vec<Result<Vec<u8>>>>) -> Self {
        Self {
            attempts: Mutex::new(attempts.into()),
            events: Mutex::default(),
            resolved: AtomicUsize::new(0),
            chunk_calls: Arc::default(),
            fail_resolve: false,
        }
    }
}

impl PartIo for ScriptedIo {
    type Document = usize;
    type Chunks = Chunks;

    async fn resolve(&self, location: &PartLocation) -> Result<usize> {
        self.events
            .lock()
            .unwrap()
            .push(Event::Resolve(location.chat_id, location.message_id));
        if self.fail_resolve {
            bail!("message unavailable");
        }
        Ok(self.resolved.fetch_add(1, Ordering::SeqCst) + 1)
    }

    fn evict(&self, location: &PartLocation) {
        self.events
            .lock()
            .unwrap()
            .push(Event::Evict(location.chat_id, location.message_id));
    }

    fn download(&self, document: &usize, skip_chunks: u32) -> Chunks {
        self.events
            .lock()
            .unwrap()
            .push(Event::Download(*document, skip_chunks));
        Chunks {
            values: self
                .attempts
                .lock()
                .unwrap()
                .pop_front()
                .expect("a scripted download")
                .into_iter(),
            calls: Arc::clone(&self.chunk_calls),
        }
    }
}

fn location() -> PartLocation {
    PartLocation {
        span: PartSpan {
            idx: 7,
            off: 0,
            len: 4 * CHUNK + 500,
        },
        chat_id: -1001,
        message_id: 21,
    }
}

fn step(skip_chunks: u32, head_drop: u64, take: u64) -> Step {
    Step {
        part_idx: 7,
        skip_chunks,
        head_drop,
        take,
    }
}

fn rpc(name: &str) -> anyhow::Error {
    InvocationError::Rpc(RpcError::from(grammers_tl_types::types::RpcError {
        error_code: 400,
        error_message: name.into(),
    }))
    .into()
}

fn bytes() -> Vec<u8> {
    (0..4 * CHUNK + 500)
        .map(|offset| (offset % 251) as u8)
        .collect()
}

async fn collect(mut receiver: mpsc::Receiver<Result<Vec<u8>>>) -> Vec<u8> {
    let mut received = Vec::new();
    while let Some(chunk) = receiver.recv().await {
        received.extend(chunk.unwrap());
    }
    received
}

#[tokio::test]
async fn a_partial_stale_download_refreshes_once_and_delivers_the_exact_remaining_bytes() {
    for stale in ["FILE_REFERENCE_EXPIRED", "FILE_REFERENCE_INVALID"] {
        let file = bytes();
        let chunk = CHUNK as usize;
        let io = ScriptedIo::new(vec![
            vec![Ok(file[chunk..2 * chunk].to_vec()), Err(rpc(stale))],
            vec![
                Ok(file[2 * chunk..3 * chunk].to_vec()),
                Ok(file[3 * chunk..4 * chunk].to_vec()),
            ],
        ]);
        let (sender, receiver) = mpsc::channel(8);

        fetch_step(&io, &location(), step(1, 300, 2 * CHUNK + 42), &sender)
            .await
            .unwrap();
        drop(sender);

        assert_eq!(collect(receiver).await, file[chunk + 300..3 * chunk + 342]);
        assert_eq!(
            *io.events.lock().unwrap(),
            vec![
                Event::Resolve(-1001, 21),
                Event::Download(1, 1),
                Event::Evict(-1001, 21),
                Event::Resolve(-1001, 21),
                Event::Download(2, 2),
            ]
        );
        assert_eq!(io.chunk_calls.load(Ordering::SeqCst), 4);
    }
}

#[tokio::test]
async fn a_stale_reference_before_any_bytes_preserves_the_original_head_drop() {
    let file = bytes();
    let chunk = CHUNK as usize;
    let io = ScriptedIo::new(vec![
        vec![Err(rpc("FILE_REFERENCE_EXPIRED"))],
        vec![Ok(file[chunk..2 * chunk].to_vec())],
    ]);
    let (sender, receiver) = mpsc::channel(2);

    fetch_step(&io, &location(), step(1, 300, 42), &sender)
        .await
        .unwrap();
    drop(sender);

    assert_eq!(collect(receiver).await, file[chunk + 300..chunk + 342]);
    assert_eq!(
        *io.events.lock().unwrap(),
        vec![
            Event::Resolve(-1001, 21),
            Event::Download(1, 1),
            Event::Evict(-1001, 21),
            Event::Resolve(-1001, 21),
            Event::Download(2, 1),
        ]
    );
}

#[tokio::test]
async fn a_second_stale_reference_is_returned_without_another_refresh() {
    let file = bytes();
    let chunk = CHUNK as usize;
    let io = ScriptedIo::new(vec![
        vec![
            Ok(file[chunk..2 * chunk].to_vec()),
            Err(rpc("FILE_REFERENCE_EXPIRED")),
        ],
        vec![Err(rpc("FILE_REFERENCE_INVALID"))],
    ]);
    let (sender, receiver) = mpsc::channel(2);

    let error = fetch_step(&io, &location(), step(1, 300, 2 * CHUNK), &sender)
        .await
        .unwrap_err();
    drop(sender);

    assert!(format!("{error:#}").contains("FILE_REFERENCE_INVALID"));
    assert_eq!(collect(receiver).await, file[chunk + 300..2 * chunk]);
    assert_eq!(
        *io.events.lock().unwrap(),
        vec![
            Event::Resolve(-1001, 21),
            Event::Download(1, 1),
            Event::Evict(-1001, 21),
            Event::Resolve(-1001, 21),
            Event::Download(2, 2),
        ]
    );
}

#[tokio::test]
async fn other_download_errors_propagate_without_eviction_or_retry() {
    for (error, cause) in [
        (rpc("FLOOD_WAIT_30"), "FLOOD_WAIT"),
        (anyhow!("connection reset"), "connection reset"),
    ] {
        let io = ScriptedIo::new(vec![vec![Ok(b"abc".to_vec()), Err(error)]]);
        let (sender, receiver) = mpsc::channel(2);

        let error = fetch_step(&io, &location(), step(0, 1, 4), &sender)
            .await
            .unwrap_err();
        drop(sender);

        assert!(format!("{error:#}").contains(cause));
        assert_eq!(collect(receiver).await, b"bc");
        assert_eq!(
            *io.events.lock().unwrap(),
            vec![Event::Resolve(-1001, 21), Event::Download(1, 0)]
        );
    }
}

#[tokio::test]
async fn failed_document_resolution_does_not_start_a_download() {
    let mut io = ScriptedIo::new(vec![]);
    io.fail_resolve = true;
    let (sender, receiver) = mpsc::channel(1);

    let error = fetch_step(&io, &location(), step(0, 0, 4), &sender)
        .await
        .unwrap_err();
    drop(sender);

    assert_eq!(error.to_string(), "message unavailable");
    assert!(collect(receiver).await.is_empty());
    assert_eq!(*io.events.lock().unwrap(), vec![Event::Resolve(-1001, 21)]);
}

#[tokio::test]
async fn premature_eof_reports_the_bytes_still_owed_without_retrying() {
    let io = ScriptedIo::new(vec![vec![Ok(b"abcd".to_vec())]]);
    let (sender, receiver) = mpsc::channel(1);

    let error = fetch_step(&io, &location(), step(0, 1, 6), &sender)
        .await
        .unwrap_err();
    drop(sender);

    assert_eq!(
        error.to_string(),
        "download ended with 3 bytes of part 7 still owed"
    );
    assert_eq!(collect(receiver).await, b"bcd");
    assert_eq!(
        *io.events.lock().unwrap(),
        vec![Event::Resolve(-1001, 21), Event::Download(1, 0)]
    );
}

#[tokio::test]
async fn a_satisfied_step_stops_before_the_next_chunk_or_error() {
    let io = ScriptedIo::new(vec![vec![
        Ok(b"abcdef".to_vec()),
        Err(anyhow!("not needed")),
    ]]);
    let (sender, receiver) = mpsc::channel(1);

    fetch_step(&io, &location(), step(0, 1, 3), &sender)
        .await
        .unwrap();
    drop(sender);

    assert_eq!(collect(receiver).await, b"bcd");
    assert_eq!(io.chunk_calls.load(Ordering::SeqCst), 1);
}

#[tokio::test]
async fn a_closed_receiver_stops_before_requesting_a_chunk() {
    let io = ScriptedIo::new(vec![vec![Err(anyhow!("must not download"))]]);
    let (sender, receiver) = mpsc::channel(1);
    drop(receiver);

    fetch_step(&io, &location(), step(0, 0, 3), &sender)
        .await
        .unwrap();

    assert_eq!(io.chunk_calls.load(Ordering::SeqCst), 0);
}

struct PendingChunks(Arc<Notify>);

impl ChunkSource for PendingChunks {
    async fn next(&mut self) -> Result<Option<Vec<u8>>> {
        self.0.notify_one();
        std::future::pending().await
    }
}

#[tokio::test]
async fn closing_the_receiver_cancels_an_inflight_chunk_request() {
    let started = Arc::new(Notify::new());
    let (sender, receiver) = mpsc::channel(1);
    let step = step(0, 0, 3);
    let mut cursor = StepCursor::new(&step);

    let (result, ()) = tokio::join!(
        tokio::time::timeout(
            Duration::from_millis(200),
            pump_chunks(
                PendingChunks(Arc::clone(&started)),
                &step,
                &mut cursor,
                &sender,
            )
        ),
        async {
            started.notified().await;
            drop(receiver);
        },
    );

    result
        .expect("closed receiver must cancel the chunk wait")
        .unwrap();
    assert_eq!(cursor.remaining(), 3);
}

#[tokio::test]
async fn closing_a_backpressured_receiver_stops_without_fetching_more_chunks() {
    let io = ScriptedIo::new(vec![vec![
        Ok(b"a".to_vec()),
        Ok(b"b".to_vec()),
        Err(anyhow!("not needed")),
    ]]);
    let (sender, mut receiver) = mpsc::channel(1);
    let location = location();

    let (result, ()) = tokio::join!(
        biased;
        fetch_step(&io, &location, step(0, 0, 3), &sender),
        async {
            assert_eq!(receiver.recv().await.unwrap().unwrap(), b"a");
            drop(receiver);
        },
    );

    result.unwrap();
    assert_eq!(io.chunk_calls.load(Ordering::SeqCst), 2);
}
