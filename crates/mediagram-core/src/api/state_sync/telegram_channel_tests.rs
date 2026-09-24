use std::cell::{Cell, RefCell};
use std::rc::Rc;
use std::sync::Arc;

use grammers_mtsender::{RpcError, SenderPool};
use grammers_session::storages::MemorySession;
use grammers_session::types::{PeerAuth, PeerId};
use grammers_tl_types::{enums, types};

use super::*;

struct Scripted<T> {
    responses: std::vec::IntoIter<Result<T, InvocationError>>,
    polls: Rc<Cell<usize>>,
}

impl<T> Scripted<T> {
    fn new(responses: Vec<Result<T, InvocationError>>) -> Self {
        Self {
            responses: responses.into_iter(),
            polls: Rc::default(),
        }
    }
}

impl<T> Responses for Scripted<T> {
    type Item = T;
    async fn next_response(&mut self) -> Result<Option<T>, InvocationError> {
        self.polls.set(self.polls.get() + 1);
        self.responses.next().transpose()
    }
}

fn rpc(code: i32, name: &str) -> InvocationError {
    InvocationError::Rpc(RpcError::from(types::RpcError {
        error_code: code,
        error_message: name.into(),
    }))
}

fn offline_client() -> Client {
    let pool = SenderPool::new(Arc::new(MemorySession::default()), 1);
    // The runner is never started: these real grammers message values cannot connect.
    Client::new(pool.handle)
}

fn media(document: Option<enums::Document>) -> Option<enums::MessageMedia> {
    Some(
        types::MessageMediaDocument {
            nopremium: false,
            spoiler: false,
            video: false,
            round: false,
            voice: false,
            document,
            alt_documents: None,
            video_cover: None,
            video_timestamp: None,
            ttl_seconds: None,
        }
        .into(),
    )
}

fn document(id: i64) -> Option<enums::MessageMedia> {
    media(Some(
        types::Document {
            id,
            access_hash: 0,
            file_reference: vec![],
            date: 0,
            mime_type: "application/json".into(),
            size: 1,
            thumbs: None,
            video_thumbs: None,
            dc_id: 2,
            attributes: vec![],
        }
        .into(),
    ))
}

fn message(client: &Client, id: i32, caption: &str, media: Option<enums::MessageMedia>) -> Message {
    Message::from_raw_short_updates(
        client,
        types::UpdateShortSentMessage {
            out: false,
            id,
            pts: 0,
            pts_count: 0,
            date: 0,
            media,
            entities: None,
            ttl_period: None,
        },
        InputMessage::new().text(caption),
        PeerRef {
            id: PeerId::channel(1).unwrap(),
            auth: PeerAuth::from_hash(0),
        },
    )
}

#[tokio::test]
async fn listing_filters_real_message_media_and_keeps_valid_siblings_in_order() {
    let dir = tempfile::tempdir().unwrap();
    let client = offline_client();
    let caption = "#mlib-state v=1 device=laptop";
    let pinned = Scripted::new(vec![
        Ok(message(&client, 1, "#mlib-index v=2 sets=1", document(1))),
        Ok(message(
            &client,
            2,
            "mentions #mlib-state device=x",
            document(2),
        )),
        Ok(message(&client, 3, "#mlib-state v=1 device=", document(3))),
        Ok(message(&client, 4, caption, None)),
        Ok(message(&client, 5, caption, media(None))),
        Ok(message(
            &client,
            6,
            caption,
            media(Some(types::DocumentEmpty { id: 6 }.into())),
        )),
        Ok(message(&client, 7, caption, document(7))),
        Ok(message(&client, 8, caption, document(8))),
        Ok(message(
            &client,
            9,
            "#mlib-state v=1 device=phone",
            document(9),
        )),
        Ok(message(&client, 10, caption, document(10))),
    ]);
    let downloads = RefCell::new(Vec::new());
    let results = list_pinned(&Core::at(dir.path()), pinned, |doc| {
        downloads.borrow_mut().push(doc.id());
        let chunks = match doc.id() {
            7 => vec![Ok(vec![0xff])],
            8 => vec![
                Ok(vec![b'x'; MAX_STATE_DOC_BYTES + 1]),
                Err(InvocationError::Dropped),
            ],
            9 => vec![Ok(b"{\"device\":\"phone\"}".to_vec())],
            10 => vec![Ok(b"{}".to_vec())],
            _ => panic!("non-state or unusable media must not be downloaded"),
        };
        Scripted::new(chunks)
    })
    .await
    .unwrap();

    assert_eq!(*downloads.borrow(), vec![7, 8, 9, 10]);
    assert_eq!(results.len(), 2);
    assert_eq!(results[0].message_id, 9);
    assert_eq!(results[0].device, "phone");
    assert_eq!(results[0].text, "{\"device\":\"phone\"}");
    assert_eq!(results[1].message_id, 10);
    assert_eq!(results[1].device, "laptop");
    assert_eq!(results[1].text, "{}");
}

#[tokio::test]
async fn capped_download_accepts_the_exact_limit_and_utf8_split_across_chunks() {
    let dir = tempfile::tempdir().unwrap();
    let mut first = vec![b'x'; MAX_STATE_DOC_BYTES - 2];
    first.push(0xc3);
    let result = download_capped(
        &Core::at(dir.path()),
        Scripted::new(vec![Ok(first), Ok(vec![0xa9])]),
    )
    .await
    .unwrap()
    .unwrap();
    assert_eq!(result.len(), MAX_STATE_DOC_BYTES);
    assert!(result.ends_with('é'));
}

#[tokio::test]
async fn oversized_download_stops_polling_as_soon_as_the_limit_is_crossed() {
    let dir = tempfile::tempdir().unwrap();
    let chunks = Scripted::new(vec![
        Ok(vec![b'x'; MAX_STATE_DOC_BYTES]),
        Ok(vec![b'x']),
        Err(InvocationError::Dropped),
    ]);
    let polls = Rc::clone(&chunks.polls);
    assert!(
        download_capped(&Core::at(dir.path()), chunks)
            .await
            .unwrap()
            .is_none()
    );
    assert_eq!(polls.get(), 2);
}

#[tokio::test]
async fn incomplete_utf8_is_skipped_and_empty_documents_are_returned_unchanged() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    assert!(
        download_capped(&core, Scripted::new(vec![Ok(vec![0xc3])]))
            .await
            .unwrap()
            .is_none()
    );
    assert_eq!(
        download_capped(&core, Scripted::new(vec![])).await.unwrap(),
        Some(String::new())
    );
}

#[tokio::test]
async fn a_download_failure_discards_partial_results_and_stops_listing_without_retry() {
    let dir = tempfile::tempdir().unwrap();
    let client = offline_client();
    let caption = "#mlib-state device=x";
    let pinned = Scripted::new(
        (1..=3)
            .map(|id| Ok(message(&client, id, caption, document(id.into()))))
            .collect(),
    );
    let polls = Rc::clone(&pinned.polls);
    let downloads = RefCell::new(Vec::new());
    let error = list_pinned(&Core::at(dir.path()), pinned, |doc| {
        downloads.borrow_mut().push(doc.id());
        if doc.id() == 1 {
            Scripted::new(vec![Ok(b"{}".to_vec())])
        } else {
            Scripted::new(vec![Ok(b"{".to_vec()), Err(rpc(420, "FLOOD_WAIT_30"))])
        }
    })
    .await
    .unwrap_err();
    assert!(
        matches!(error, CoreError::Network(ref message) if message == "a state document could not be downloaded")
    );
    assert_eq!(*downloads.borrow(), vec![1, 2]);
    assert_eq!(polls.get(), 2);
}

#[tokio::test]
async fn a_pin_listing_failure_is_reported_without_downloading_or_retrying() {
    let dir = tempfile::tempdir().unwrap();
    let pinned = Scripted::new(vec![Err(InvocationError::Dropped)]);
    let polls = Rc::clone(&pinned.polls);
    let result = list_pinned(&Core::at(dir.path()), pinned, |_| -> Scripted<Vec<u8>> {
        panic!("a refused listing has no document to download")
    })
    .await;
    assert!(matches!(result, Err(CoreError::Network(_))));
    assert_eq!(polls.get(), 1);
}

#[tokio::test]
async fn revocation_during_either_listing_or_download_forgets_the_session() {
    for during_download in [false, true] {
        let dir = tempfile::tempdir().unwrap();
        let core = Core::at(dir.path());
        let session = dir.path().join("session.key");
        let mut key = vec![7; 260];
        key[..4].copy_from_slice(&2_i32.to_be_bytes());
        std::fs::write(&session, key).unwrap();
        assert!(core.is_authorized());
        let before = core.state.lock().await.documents.clone();
        let client = offline_client();
        let response = if during_download {
            Ok(message(&client, 1, "#mlib-state device=x", document(1)))
        } else {
            Err(rpc(401, "SESSION_REVOKED"))
        };
        let result = list_pinned(&core, Scripted::new(vec![response]), |_| {
            Scripted::new(vec![Err(rpc(401, "AUTH_KEY_UNREGISTERED"))])
        })
        .await;
        assert!(matches!(result, Err(CoreError::NotAuthorized(_))));
        assert!(!session.exists());
        assert!(!core.is_authorized());
        assert!(!Arc::ptr_eq(&before, &core.state.lock().await.documents));
    }
}
