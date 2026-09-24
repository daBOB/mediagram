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
    assert!(matches!(
        refused(&rpc(420, "FLOOD_WAIT_60")),
        CoreError::Network(_)
    ));
    assert!(matches!(
        refused(&rpc(500, "INTERNAL")),
        CoreError::Network(_)
    ));
}

/// With no login under way, the later steps refuse before reaching Telegram.
#[tokio::test]
async fn signing_in_with_no_login_under_way_is_not_authorized() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let err = sign_in(&core, "nope".into(), "12345".into())
        .await
        .unwrap_err();
    assert!(matches!(err, CoreError::NotAuthorized(_)));
    let err = check_password(&core, "secret".into()).await.unwrap_err();
    assert!(matches!(err, CoreError::NotAuthorized(_)));
}

fn password_token(hint: &str) -> PasswordToken {
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
async fn begin(core: &Core, key: u8) -> Attempt {
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

fn assert_stale<T>(result: Result<T, CoreError>) {
    assert!(matches!(result, Err(CoreError::NotAuthorized(message))
        if message == "this sign-in attempt is no longer active"));
}

async fn delayed_password(
    core: std::sync::Arc<Core>,
    attempt: Attempt,
) -> (
    tokio::sync::oneshot::Sender<Result<(), SignInError>>,
    tokio::task::JoinHandle<Result<(), CoreError>>,
) {
    let (reply, response) = tokio::sync::oneshot::channel();
    let (started, waiting) = tokio::sync::oneshot::channel();
    let task = tokio::spawn(async move {
        finish_password(&core, attempt, async {
            started.send(()).unwrap();
            response.await.unwrap()
        })
        .await
    });
    waiting.await.unwrap();
    (reply, task)
}

#[tokio::test]
async fn password_success_after_sign_out_does_not_persist_or_panic() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let attempt = begin(&core, 1).await;
    let (reply, task) = delayed_password(core.clone(), attempt).await;

    core.sign_out().await.unwrap();
    reply.send(Ok(())).unwrap();

    assert_stale(task.await.expect("a late login result must not panic"));
    assert!(!core.is_authorized());
    assert!(core.state.lock().await.client.is_none());
}

#[tokio::test]
async fn refused_password_after_sign_out_cannot_restore_the_password_step() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let attempt = begin(&core, 1).await;
    let (reply, task) = delayed_password(core.clone(), attempt).await;

    core.sign_out().await.unwrap();
    reply
        .send(Err(SignInError::InvalidPassword(password_token("old"))))
        .unwrap();

    assert_stale(task.await.unwrap());
    assert!(core.state.lock().await.pending_password.is_none());
    assert!(!core.is_authorized());
}

#[tokio::test]
async fn an_older_password_response_cannot_replace_a_new_attempts_token() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let attempt = begin(&core, 1).await;
    let (reply, task) = delayed_password(core.clone(), attempt).await;
    {
        let mut state = core.state.lock().await;
        Attempt::begin(&mut state).unwrap();
        state.pending_password = Some(PendingPassword(password_token("new")));
    }
    reply
        .send(Err(SignInError::InvalidPassword(password_token("old"))))
        .unwrap();

    assert_stale(task.await.unwrap());
    let state = core.state.lock().await;
    assert_eq!(
        state.pending_password.as_ref().unwrap().0.hint(),
        Some("new")
    );
}

#[tokio::test]
async fn an_old_client_cannot_persist_over_a_replacement_even_with_the_same_attempt_marker() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let attempt = begin(&core, 1).await;
    let marker = core.state.lock().await.login_attempt.clone();
    let (reply, task) = delayed_password(core.clone(), attempt).await;
    let replacement = begin(&core, 2).await;
    replacement.persist(&core).unwrap();
    core.state.lock().await.login_attempt = marker;
    reply.send(Ok(())).unwrap();

    assert_stale(task.await.unwrap());
    assert_eq!(session::load_auth_key(dir.path()), Some((2, [2; 256])));
}

#[tokio::test]
async fn a_revoked_login_invalidates_an_in_flight_completion() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let attempt = begin(&core, 1).await;
    let (reply, task) = delayed_password(core.clone(), attempt).await;
    super::super::revoked::unless_revoked(
        &core,
        &rpc(401, "SESSION_REVOKED"),
        CoreError::Network("offline".into()),
    )
    .await;
    reply.send(Ok(())).unwrap();

    assert_stale(task.await.unwrap());
    assert!(!core.is_authorized());
}

#[tokio::test]
async fn the_current_password_step_can_retry_then_persist_its_own_session() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let attempt = begin(&core, 7).await;
    let result = finish_password(&core, attempt, async {
        Err::<(), _>(SignInError::InvalidPassword(password_token("retry")))
    })
    .await;
    assert!(matches!(result, Err(CoreError::NotAuthorized(message))
        if message == "the password was not accepted"));
    let attempt = {
        let mut state = core.state.lock().await;
        let pending = state
            .pending_password
            .take()
            .expect("wrong passwords retain their token");
        assert_eq!(pending.0.hint(), Some("retry"));
        Attempt::resume(&state).unwrap()
    };

    finish_password(&core, attempt, async { Ok(()) })
        .await
        .unwrap();

    assert_eq!(session::load_auth_key(dir.path()), Some((2, [7; 256])));
}

#[tokio::test]
async fn every_login_result_is_checked_before_it_can_be_interpreted() {
    for result in [
        Ok(()),
        Err(SignInError::InvalidCode),
        Err(SignInError::PasswordRequired(password_token("two factor"))),
        Err(SignInError::InvalidPassword(password_token("retry"))),
        Err(SignInError::SignUpRequired),
        Err(SignInError::Other(rpc(500, "SERVER_FAILURE"))),
    ] {
        let dir = tempfile::tempdir().unwrap();
        let core = Core::at(dir.path());
        let attempt = begin(&core, 1).await;
        Attempt::begin(&mut *core.state.lock().await).unwrap();
        assert_stale(attempt.complete(&core, async { result }).await);
        assert!(core.state.lock().await.pending_password.is_none());
        assert!(!core.is_authorized());
    }
}

#[tokio::test]
async fn a_new_code_request_discards_the_previous_password_step() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    begin(&core, 1).await;
    let mut state = core.state.lock().await;
    state.pending_password = Some(PendingPassword(password_token("old")));

    Attempt::begin(&mut state).unwrap();

    assert!(state.pending_password.is_none());
}

#[tokio::test]
async fn a_current_code_refusal_leaves_the_attempt_available_for_retry() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let attempt = begin(&core, 1).await;

    let (state, result) = attempt
        .complete(&core, async { Err::<(), _>(SignInError::InvalidCode) })
        .await
        .unwrap();

    assert!(matches!(result, Err(SignInError::InvalidCode)));
    assert!(Attempt::resume(&state).is_ok());
    assert!(!core.is_authorized());
}
