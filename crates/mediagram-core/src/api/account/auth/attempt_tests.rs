use grammers_client::SignInError;

use super::super::PendingPassword;
use super::super::fixture::*;
use super::*;

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

/// Every later step resumes the attempt `request_code` began, on its
/// connection; with either missing there is nothing to resume.
#[tokio::test]
async fn an_attempt_cannot_begin_or_resume_without_a_connection() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let mut state = core.state.lock().await;

    assert_stale(Attempt::begin(&mut state));
    assert_stale(Attempt::resume(&state));
}

#[tokio::test]
async fn a_connection_with_no_attempt_under_way_has_nothing_to_resume() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let mut state = core.state.lock().await;
    state.client = Some(session::connect(&core));

    assert_stale(Attempt::resume(&state));
}

/// The attempt marker alone is not enough: a response from a connection
/// that has since been replaced must not restore a token or persist a key
/// onto the replacement, whatever attempt it claims to answer.
#[tokio::test]
async fn a_response_from_a_replaced_connection_is_stale_for_the_same_attempt() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let attempt = begin(&core, 1).await;

    core.state.lock().await.client = Some(session::connect(&core));

    assert_stale(attempt.complete(&core, async {}).await);
}

/// The verdict must be applied under the lock that checked it, or a sign-out
/// slipping in between could see a token restored after it cleared them.
#[tokio::test]
async fn a_current_response_comes_back_with_the_state_still_locked() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let attempt = begin(&core, 1).await;

    let (state, answer) = attempt.complete(&core, async { "accepted" }).await.unwrap();

    assert_eq!(answer, "accepted");
    assert!(core.state.try_lock().is_err());
    drop(state);
    assert!(core.state.try_lock().is_ok());
}

#[tokio::test]
async fn persisting_stores_the_key_the_connection_now_holds() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let attempt = begin(&core, 5).await;
    assert!(!core.is_authorized());

    attempt.persist(&core).unwrap();

    assert_eq!(session::load_auth_key(dir.path()), Some((2, [5; 256])));
}

/// A connection that never finished a sign-in has no key to keep, and
/// writing nothing must not read back as being signed in.
#[tokio::test]
async fn a_connection_without_an_auth_key_is_not_persisted() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let attempt = {
        let mut state = core.state.lock().await;
        state.client = Some(session::connect(&core));
        Attempt::begin(&mut state).unwrap()
    };

    let err = attempt.persist(&core).unwrap_err();

    assert!(matches!(err, CoreError::NotAuthorized(message)
        if message == "sign-in did not yield an auth key"));
    assert!(!core.is_authorized());
}
