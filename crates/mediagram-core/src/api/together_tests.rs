//! What an edit's answer means to a session, matched to the web's
//! `TelegramTogetherChannel.edit`, and what a read by id answers.

use grammers_mtsender::RpcError;
use grammers_tl_types::{self as tl, Deserializable, Serializable};

use super::*;

fn rpc(code: i32, message: &str) -> InvocationError {
    InvocationError::Rpc(RpcError::from(tl::types::RpcError {
        error_code: code,
        error_message: message.into(),
    }))
}

#[test]
fn a_landed_edit_is_ok() {
    assert_eq!(edit_outcome(Ok(())).unwrap(), TogetherEdit::Ok);
}

/// Two screens writing the same state: Telegram keeps it either way.
#[test]
fn an_edit_that_changes_nothing_is_ok() {
    let outcome = edit_outcome(Err(rpc(400, "MESSAGE_NOT_MODIFIED")));
    assert_eq!(outcome.unwrap(), TogetherEdit::Ok);
}

#[test]
fn an_edit_of_a_deleted_message_is_gone() {
    let outcome = edit_outcome(Err(rpc(400, "MESSAGE_ID_INVALID")));
    assert_eq!(outcome.unwrap(), TogetherEdit::Gone);
}

#[test]
fn a_flood_wait_is_a_wait_for_its_seconds() {
    let outcome = edit_outcome(Err(rpc(420, "FLOOD_WAIT_90")));
    assert_eq!(outcome.unwrap(), TogetherEdit::Wait { seconds: 90 });
}

#[test]
fn any_other_refusal_stays_an_error() {
    let outcome = edit_outcome(Err(rpc(400, "CHAT_WRITE_FORBIDDEN")));
    assert!(
        matches!(outcome, Err(InvocationError::Rpc(rpc)) if rpc.name == "CHAT_WRITE_FORBIDDEN")
    );
    assert!(edit_outcome(Err(InvocationError::Dropped)).is_err());
}

/// A channel answers a deleted id with an empty message rather than none at
/// all; a session read must say it is gone, not that its text is blank.
#[test]
fn a_deleted_message_has_no_text() {
    let deleted = tl::types::MessageEmpty {
        id: 901,
        peer_id: None,
    };
    assert_eq!(live_text(&deleted.into()), None);
}

#[test]
fn a_live_message_reads_as_its_text() {
    let text = "#mlib-together v=1\n{}";
    let mut wire = Vec::new();
    wire.extend(0x7600b9d3_u32.to_le_bytes()); // message, every flag clear
    wire.extend([0; 8]); // flags, flags2
    wire.extend(901_i32.to_le_bytes()); // id
    wire.extend(0xa2a5371e_u32.to_le_bytes()); // peerChannel
    wire.extend(3816522481_i64.to_le_bytes());
    wire.extend(0_i32.to_le_bytes()); // date
    text.to_owned().serialize(&mut wire);
    let message = tl::enums::Message::from_bytes(&wire).unwrap();
    assert_eq!(live_text(&message).as_deref(), Some(text));
}
