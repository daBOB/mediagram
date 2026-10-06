//! What the login-step tests share: a sign-in attempt on a real in-memory
//! session, password parameters, and the refusals Telegram answers with.

use grammers_client::client::PasswordToken;
use grammers_mtsender::RpcError;

use super::*;

pub(super) fn rpc(code: i32, message: &str) -> InvocationError {
    InvocationError::Rpc(RpcError::from(grammers_tl_types::types::RpcError {
        error_code: code,
        error_message: message.into(),
    }))
}

/// Password parameters told apart by their hint. They carry no SRP values,
/// so they only ever reach a check the test supplies, never Telegram.
pub(super) fn password_token(hint: &str) -> PasswordToken {
    use grammers_tl_types::{enums, types};
    PasswordToken::new(types::account::Password {
        has_recovery: false,
        has_secure_values: false,
        has_password: false,
        current_algo: None,
        srp_b: None,
        srp_id: None,
        hint: Some(hint.into()),
        email_unconfirmed_pattern: None,
        new_algo: enums::PasswordKdfAlgo::Unknown,
        new_secure_algo: enums::SecurePasswordKdfAlgo::Unknown,
        secure_random: Vec::new(),
        pending_reset_date: None,
        login_email_pattern: None,
    })
}

/// Sender pools connect on demand. These tests create only their in-memory
/// session and never invoke a Telegram request or poll a network response.
/// The session holds `[key; 256]` for datacentre 2, as a finished sign-in
/// would leave it.
pub(super) async fn begin(core: &Core, key: u8) -> Attempt {
    let mut state = core.state.lock().await;
    state.client = Some(session::connect(core));
    let session = state.client.as_ref().unwrap().handle.session.clone();
    let attempt = Attempt::begin(&mut state).unwrap();
    drop(state);
    let mut dc = session.dc_option(2).unwrap().unwrap();
    dc.auth_key = Some([key; 256]);
    session.set_dc_option(&dc).await.unwrap();
    session.set_home_dc_id(2).await.unwrap();
    attempt
}

pub(super) fn assert_stale<T>(result: Result<T, CoreError>) {
    assert!(matches!(result, Err(CoreError::NotAuthorized(message))
        if message == "this sign-in attempt is no longer active"));
}
