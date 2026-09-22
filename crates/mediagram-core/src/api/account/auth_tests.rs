use grammers_mtsender::RpcError;

use super::*;

fn rpc(code: i32, message: &str) -> InvocationError {
    InvocationError::Rpc(RpcError::from(grammers_tl_types::types::RpcError {
        error_code: code,
        error_message: message.into(),
    }))
}

#[test]
fn a_refused_number_is_not_authorized_and_names_the_reason() {
    let CoreError::NotAuthorized(said) = refused(&rpc(400, "PHONE_NUMBER_BANNED")) else {
        panic!("a refusal is not a network fault");
    };
    assert!(said.contains("PHONE_NUMBER_BANNED"));
}

#[test]
fn a_flood_wait_or_server_fault_is_a_network_error() {
    assert!(matches!(refused(&rpc(420, "FLOOD_WAIT_60")), CoreError::Network(_)));
    assert!(matches!(refused(&rpc(500, "INTERNAL")), CoreError::Network(_)));
}

/// With no login under way, the later steps refuse before reaching Telegram.
#[tokio::test]
async fn signing_in_with_no_login_under_way_is_not_authorized() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::new(dir.path().to_string_lossy().into_owned(), 1, "hash".into());
    let err = sign_in(&core, "nope".into(), "12345".into()).await.unwrap_err();
    assert!(matches!(err, CoreError::NotAuthorized(_)));
    let err = check_password(&core, "secret".into()).await.unwrap_err();
    assert!(matches!(err, CoreError::NotAuthorized(_)));
}
