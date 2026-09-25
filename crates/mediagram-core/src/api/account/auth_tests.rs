use grammers_client::client::PasswordToken;
use grammers_mtsender::RpcError;

use super::password::finish_password;
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

/// The parameters a password step holds, by the hint the fixture gave them.
fn hint(pending: &PendingPassword) -> Option<&str> {
    match pending {
        PendingPassword::Ready(token) => token.hint(),
        PendingPassword::Refetch => None,
    }
}

async fn fetch_fresh() -> Result<PasswordToken, CoreError> {
    Ok(password_token("fresh"))
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
        let check = |_token| async {
            started.send(()).unwrap();
            response.await.unwrap()
        };
        let pending = PendingPassword::Ready(Box::new(password_token("sent")));
        finish_password(&core, attempt, pending, check, fetch_fresh).await
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
        state.pending_password = Some(PendingPassword::Ready(Box::new(password_token("new"))));
    }
    reply
        .send(Err(SignInError::InvalidPassword(password_token("old"))))
        .unwrap();

    assert_stale(task.await.unwrap());
    let state = core.state.lock().await;
    assert_eq!(hint(state.pending_password.as_ref().unwrap()), Some("new"));
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
    let owner = session::connection(&core).await.1;
    super::super::revoked::unless_revoked_for(
        &core,
        &owner,
        &rpc(401, "SESSION_REVOKED"),
        CoreError::Network("offline".into()),
    )
    .await;
    reply.send(Ok(())).unwrap();

    assert_stale(task.await.unwrap());
    assert!(!core.is_authorized());
}

/// Runs one password check against whatever step is pending, the way
/// `check_password` does once it has taken the step out of the state.
async fn retry<C, F>(
    core: &Core,
    check: impl FnOnce(PasswordToken) -> C,
    fetch: impl Fn() -> F,
) -> Result<(), CoreError>
where
    C: std::future::Future<Output = Result<(), SignInError>>,
    F: std::future::Future<Output = Result<PasswordToken, CoreError>>,
{
    let (attempt, pending) = {
        let mut state = core.state.lock().await;
        let pending = state
            .pending_password
            .take()
            .expect("the password step is still pending");
        (Attempt::resume(&state).unwrap(), pending)
    };
    finish_password(core, attempt, pending, check, fetch).await
}

async fn pending_hint(core: &Core) -> Option<String> {
    let state = core.state.lock().await;
    let pending = state.pending_password.as_ref().expect("the step is kept");
    hint(pending).map(str::to_owned)
}

fn unreachable_fetch() -> impl Fn() -> std::future::Ready<Result<PasswordToken, CoreError>> {
    || panic!("no fresh parameters are needed here")
}

async fn begin_password_step(core: &Core, key: u8) {
    begin(core, key).await;
    core.state.lock().await.pending_password =
        Some(PendingPassword::Ready(Box::new(password_token("first"))));
}

#[tokio::test]
async fn a_wrong_password_then_the_right_one_signs_in_with_fresh_parameters() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    begin_password_step(&core, 7).await;

    let result = retry(
        &core,
        |token| async move {
            assert_eq!(token.hint(), Some("first"));
            // grammers hands back the parameters it just spent.
            Err(SignInError::InvalidPassword(token))
        },
        fetch_fresh,
    )
    .await;
    assert!(matches!(result, Err(CoreError::NotAuthorized(message))
        if message == "the password was not accepted"));
    assert_eq!(pending_hint(&core).await.as_deref(), Some("fresh"));

    retry(
        &core,
        |token| async move {
            assert_eq!(token.hint(), Some("fresh"), "spent parameters were reused");
            Ok(())
        },
        unreachable_fetch(),
    )
    .await
    .unwrap();

    assert_eq!(session::load_auth_key(dir.path()), Some((2, [7; 256])));
}

#[tokio::test]
async fn a_spent_srp_id_keeps_the_step_open_with_fresh_parameters() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    begin_password_step(&core, 1).await;

    let result = retry(
        &core,
        |_token| async { Err(SignInError::Other(rpc(400, "SRP_ID_INVALID"))) },
        fetch_fresh,
    )
    .await;

    assert!(matches!(result, Err(CoreError::NotAuthorized(message))
        if message.contains("SRP_ID_INVALID")));
    assert_eq!(pending_hint(&core).await.as_deref(), Some("fresh"));
    assert!(!core.is_authorized());
}

#[tokio::test]
async fn a_network_fault_keeps_the_step_and_refetches_on_the_next_check() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    begin_password_step(&core, 3).await;

    let result = retry(
        &core,
        |_token| async { Err(SignInError::Other(rpc(500, "INTERNAL"))) },
        unreachable_fetch(),
    )
    .await;
    assert!(matches!(result, Err(CoreError::Network(_))));
    assert!(matches!(
        core.state.lock().await.pending_password,
        Some(PendingPassword::Refetch)
    ));

    retry(
        &core,
        |token| async move {
            assert_eq!(token.hint(), Some("fresh"));
            Ok(())
        },
        fetch_fresh,
    )
    .await
    .unwrap();
    assert_eq!(session::load_auth_key(dir.path()), Some((2, [3; 256])));
}

#[tokio::test]
async fn failing_to_fetch_fresh_parameters_still_keeps_the_step() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    begin_password_step(&core, 1).await;
    let offline = || async { Err(CoreError::Network("offline".into())) };

    let result = retry(
        &core,
        |token| async move { Err(SignInError::InvalidPassword(token)) },
        offline,
    )
    .await;
    assert!(matches!(result, Err(CoreError::NotAuthorized(message))
        if message == "the password was not accepted"));

    // Still offline: the check is never sent without fresh parameters.
    let result = retry(
        &core,
        |_token| async { panic!("checked against spent parameters") },
        offline,
    )
    .await;
    assert!(matches!(result, Err(CoreError::Network(_))));
    assert!(matches!(
        core.state.lock().await.pending_password,
        Some(PendingPassword::Refetch)
    ));
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
    state.pending_password = Some(PendingPassword::Ready(Box::new(password_token("old"))));

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
