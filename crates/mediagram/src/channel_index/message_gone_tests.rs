use super::*;
use grammers_mtsender::InvocationError;

/// An RPC error as `with_retry` hands it back: the grammers error, as is.
fn rpc(code: i32, message: &str) -> anyhow::Error {
    anyhow::Error::new(InvocationError::Rpc(
        grammers_tl_types::types::RpcError {
            error_code: code,
            error_message: message.to_string(),
        }
        .into(),
    ))
}

#[test]
fn a_message_that_no_longer_exists_is_gone() {
    assert!(message_is_gone(&rpc(400, "MESSAGE_ID_INVALID")));
    assert!(message_is_gone(&rpc(400, "MESSAGE_NOT_MODIFIED")));
}

/// These arrive in the same 400 class and say nothing about the message.
/// Reading them as "gone" would drop the id and strand the pin.
#[test]
fn a_channel_level_refusal_is_not_the_message_being_gone() {
    assert!(!message_is_gone(&rpc(400, "PEER_ID_INVALID")));
    assert!(!message_is_gone(&rpc(400, "CHANNEL_INVALID")));
    assert!(!message_is_gone(&rpc(400, "CHAT_WRITE_FORBIDDEN")));
}

#[test]
fn a_server_error_or_a_flood_wait_is_never_gone() {
    assert!(!message_is_gone(&rpc(500, "MESSAGE_ID_INVALID")));
    assert!(!message_is_gone(&rpc(420, "FLOOD_WAIT_31")));
}

/// Only Telegram's own answer counts: a lost connection, or an error that
/// merely mentions the name, says nothing about the message.
#[test]
fn an_error_that_is_not_telegram_s_answer_is_never_gone() {
    let io = InvocationError::Io(std::io::Error::other("connection reset"));
    assert!(!message_is_gone(&anyhow::Error::new(io)));
    assert!(!message_is_gone(&anyhow::anyhow!("MESSAGE_ID_INVALID")));
}
