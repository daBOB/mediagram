use super::super::fixture::Fixture;
use super::*;

fn same(a: &SenderPoolFatHandle, b: &SenderPoolFatHandle) -> bool {
    Arc::ptr_eq(&a.session, &b.session)
}

#[tokio::test]
async fn every_caller_shares_the_one_connection() {
    let f = Fixture::new().await;

    let (_, again) = connection(&f.core).await;

    assert!(same(&f.owner, &again));
    assert!(is_current(&f.core, &f.owner).await);
}

/// The receiver is one per sender pool: whoever subscribed it reads it, and
/// a second listener on the same pool would find nothing to read.
#[tokio::test]
async fn a_connections_updates_are_handed_out_once() {
    let f = Fixture::new().await;

    assert!(updates_receiver(&f.core, &f.owner).await.is_some());
    assert!(updates_receiver(&f.core, &f.owner).await.is_none());
}

#[tokio::test]
async fn a_replaced_connection_is_not_current_and_cannot_take_the_new_ones_updates() {
    let f = Fixture::new().await;
    let replacement = f.replace().await;

    assert!(!is_current(&f.core, &f.owner).await);
    assert!(updates_receiver(&f.core, &f.owner).await.is_none());
    assert!(is_current(&f.core, &replacement).await);
    assert!(updates_receiver(&f.core, &replacement).await.is_some());
}

/// A connection whose receiver was consumed can never offer another, so it
/// goes, along with any half-finished sign-in on it. The stored key stays:
/// a dead connection is not a revoked login.
#[tokio::test]
async fn invalidating_the_live_connection_lets_the_next_caller_start_fresh() {
    let f = Fixture::new().await;
    f.core.state.lock().await.login_attempt = Some(Arc::new(()));

    invalidate(&f.core, &f.owner).await;

    {
        let state = f.core.state.lock().await;
        assert!(state.client.is_none());
        assert!(state.login_attempt.is_none());
    }
    let (_, fresh) = connection(&f.core).await;
    assert!(!same(&fresh, &f.owner));
    assert!(updates_receiver(&f.core, &fresh).await.is_some());
    assert!(f.core.is_authorized());
}

#[tokio::test]
async fn invalidating_a_replaced_connection_leaves_the_replacement_alone() {
    let f = Fixture::new().await;
    let replacement = f.replace().await;
    f.core.state.lock().await.login_attempt = Some(Arc::new(()));

    invalidate(&f.core, &f.owner).await;

    assert!(f.core.state.lock().await.login_attempt.is_some());
    f.assert_kept(&replacement, 9).await;
    assert!(updates_receiver(&f.core, &replacement).await.is_some());
}
