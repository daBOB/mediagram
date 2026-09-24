use std::cell::RefCell;
use std::collections::VecDeque;
use std::sync::Arc;

use grammers_mtsender::RpcError;

use super::*;

const BODY: &str =
    "{ \"format\": 1, \"device\": \"device-42\", \"writtenAt\": 7, \"profiles\": [] }\n";
const CAPTION: &str = "#mlib-state v=1 device=device-42";
const UPLOADED: i32 = 81;
const EXISTING: i32 = 23;
const SENT: i32 = 47;

#[derive(Debug, PartialEq)]
enum Call {
    Upload(String),
    Edit(i32, String, i32),
    Send(String, i32),
    Pin(i32),
    Delete(i32),
}

type Step = (Call, Result<i32, InvocationError>);

/// Only the IO boundary is scripted; `put` chooses every operation and its order.
struct ScriptedWriter(RefCell<VecDeque<Step>>);

impl ScriptedWriter {
    fn new(steps: Vec<Step>) -> Self {
        Self(RefCell::new(steps.into()))
    }

    fn invoke(&self, call: Call) -> Result<i32, InvocationError> {
        let (expected, result) = self
            .0
            .borrow_mut()
            .pop_front()
            .unwrap_or_else(|| panic!("unexpected call: {call:?}"));
        assert_eq!(call, expected);
        result
    }

    fn assert_finished(&self) {
        assert!(
            self.0.borrow().is_empty(),
            "not every expected call happened"
        );
    }
}

impl DocumentWriter for ScriptedWriter {
    type Upload = i32;

    async fn upload(&self, body: &str) -> Result<i32, CoreError> {
        self.invoke(Call::Upload(body.into()))
            .map_err(CoreError::network("test upload refused"))
    }

    async fn edit(&self, id: i32, caption: String, uploaded: i32) -> Result<(), InvocationError> {
        self.invoke(Call::Edit(id, caption, uploaded)).map(|_| ())
    }

    async fn send(&self, caption: String, uploaded: i32) -> Result<i32, InvocationError> {
        self.invoke(Call::Send(caption, uploaded))
    }

    async fn pin(&self, id: i32) -> Result<(), InvocationError> {
        self.invoke(Call::Pin(id)).map(|_| ())
    }

    async fn delete(&self, id: i32) -> Result<(), InvocationError> {
        self.invoke(Call::Delete(id)).map(|_| ())
    }
}

fn rpc(code: i32, message: &str) -> InvocationError {
    InvocationError::Rpc(RpcError::from(grammers_tl_types::types::RpcError {
        error_code: code,
        error_message: message.into(),
    }))
}

fn upload() -> Step {
    (Call::Upload(BODY.into()), Ok(UPLOADED))
}

fn edit(result: Result<i32, InvocationError>) -> Step {
    (Call::Edit(EXISTING, CAPTION.into(), UPLOADED), result)
}

fn send(result: Result<i32, InvocationError>) -> Step {
    (Call::Send(CAPTION.into(), UPLOADED), result)
}

fn assert_network(error: CoreError, expected: &str) {
    let CoreError::Network(message) = error else {
        panic!("expected a network failure, got {error:?}");
    };
    assert_eq!(message, expected);
}

#[tokio::test]
async fn an_existing_document_is_edited_without_sending_or_pinning() {
    let dir = tempfile::tempdir().unwrap();
    let writer = ScriptedWriter::new(vec![upload(), edit(Ok(0))]);

    let id = put(&Core::at(dir.path()), &writer, BODY.into(), Some(EXISTING))
        .await
        .unwrap();

    assert_eq!(id, EXISTING);
    writer.assert_finished();
}

#[tokio::test]
async fn a_first_document_is_sent_and_pinned_once() {
    let dir = tempfile::tempdir().unwrap();
    let writer = ScriptedWriter::new(vec![upload(), send(Ok(SENT)), (Call::Pin(SENT), Ok(0))]);

    let id = put(&Core::at(dir.path()), &writer, BODY.into(), None)
        .await
        .unwrap();

    assert_eq!(id, SENT);
    writer.assert_finished();
}

#[tokio::test]
async fn a_deleted_document_is_replaced_and_pinned_using_the_same_upload() {
    let dir = tempfile::tempdir().unwrap();
    let writer = ScriptedWriter::new(vec![
        upload(),
        edit(Err(rpc(400, "MESSAGE_ID_INVALID"))),
        send(Ok(SENT)),
        (Call::Pin(SENT), Ok(0)),
    ]);

    let id = put(&Core::at(dir.path()), &writer, BODY.into(), Some(EXISTING))
        .await
        .unwrap();

    assert_eq!(id, SENT);
    writer.assert_finished();
}

#[tokio::test]
async fn other_edit_failures_never_replace_the_document() {
    for failure in [
        rpc(403, "CHAT_WRITE_FORBIDDEN"),
        rpc(420, "FLOOD_WAIT_30"),
        rpc(500, "INTERNAL"),
        InvocationError::Dropped,
    ] {
        let dir = tempfile::tempdir().unwrap();
        let writer = ScriptedWriter::new(vec![upload(), edit(Err(failure))]);

        let error = put(&Core::at(dir.path()), &writer, BODY.into(), Some(EXISTING))
            .await
            .unwrap_err();

        assert_network(error, "the state document could not be edited");
        writer.assert_finished();
    }
}

async fn pin_refused(delete_result: Result<i32, InvocationError>) {
    let dir = tempfile::tempdir().unwrap();
    let writer = ScriptedWriter::new(vec![
        upload(),
        send(Ok(SENT)),
        (Call::Pin(SENT), Err(rpc(420, "FLOOD_WAIT_633"))),
        (Call::Delete(SENT), delete_result),
    ]);

    let error = put(&Core::at(dir.path()), &writer, BODY.into(), None)
        .await
        .unwrap_err();

    assert_network(error, "the state document's pin was refused");
    writer.assert_finished();
}

#[tokio::test]
async fn a_refused_pin_deletes_the_sent_message_and_fails_the_put() {
    pin_refused(Ok(0)).await;
}

#[tokio::test]
async fn a_failed_cleanup_still_returns_the_original_pin_failure() {
    pin_refused(Err(rpc(403, "MESSAGE_DELETE_FORBIDDEN"))).await;
}

#[tokio::test]
async fn a_failed_send_never_pins_or_deletes_anything() {
    for message_id in [None, Some(EXISTING)] {
        let dir = tempfile::tempdir().unwrap();
        let mut steps = vec![upload()];
        if message_id.is_some() {
            steps.push(edit(Err(rpc(400, "MESSAGE_ID_INVALID"))));
        }
        steps.push(send(Err(rpc(500, "INTERNAL"))));
        let writer = ScriptedWriter::new(steps);

        let error = put(&Core::at(dir.path()), &writer, BODY.into(), message_id)
            .await
            .unwrap_err();

        assert_network(error, "the state document could not be sent");
        writer.assert_finished();
    }
}

#[tokio::test]
async fn a_revoked_edit_forgets_authorization_and_resets_state_without_replacing() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    // A synthetic, structurally valid key; this test never opens a connection.
    let mut key = vec![7; 260];
    key[..4].copy_from_slice(&2_i32.to_be_bytes());
    let session = dir.path().join("session.key");
    std::fs::write(&session, key).unwrap();
    assert!(core.is_authorized());
    let documents = core.state.lock().await.documents.clone();
    let writer = ScriptedWriter::new(vec![upload(), edit(Err(rpc(401, "SESSION_REVOKED")))]);

    let error = put(&core, &writer, BODY.into(), Some(EXISTING))
        .await
        .unwrap_err();

    assert!(matches!(error, CoreError::NotAuthorized(_)));
    assert!(!core.is_authorized());
    assert!(!session.exists());
    assert!(!Arc::ptr_eq(&documents, &core.state.lock().await.documents));
    writer.assert_finished();
}
