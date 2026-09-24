//! A released wrapper can leave an Arc-owned local operation queued on the
//! blocking pool. Terminal retirement must precede clearing its files.

use std::future::Future;
use std::path::Path;
use std::sync::{Arc, mpsc};
use std::task::{Context, Waker};

use mediagram_core::api::Core;

fn core(path: &Path) -> Arc<Core> {
    Core::new(
        path.display().to_string(),
        1,
        "test-hash".into(),
        "test-device".into(),
    )
}

fn queued_create_cannot_reopen_retired_state(opened: bool) {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("player");
    let runtime = tokio::runtime::Builder::new_current_thread()
        .max_blocking_threads(1)
        .build()
        .unwrap();
    runtime.block_on(async {
        let old = core(&path);
        if opened {
            assert!(
                old.clone()
                    .create_profile("Before reset".into())
                    .await
                    .is_some()
            );
        }
        let (entered, running) = mpsc::channel();
        let (release, blocked) = mpsc::channel();
        let blocker = tokio::task::spawn_blocking(move || {
            entered.send(()).unwrap();
            // Dropping the release sender on an assertion failure also
            // frees the slot before the runtime joins its blocking workers.
            let _ = blocked.recv();
        });
        running.recv().unwrap();

        // Poll the actual API until its spawn_blocking job is queued behind
        // the held slot. That queued job owns its own Arc to the old Core.
        let mut late = Box::pin(old.clone().create_profile("Late old-owner write".into()));
        assert!(
            late.as_mut()
                .poll(&mut Context::from_waker(Waker::noop()))
                .is_pending()
        );
        old.retire_local_state();
        old.retire_local_state();
        drop(old);
        if opened {
            std::fs::remove_dir_all(&path).unwrap();
        }
        assert!(!path.exists());
        release.send(()).unwrap();
        blocker.await.unwrap();
        assert_eq!(late.await, None);
        assert!(
            !path.exists(),
            "a queued old-owner write recreated removed storage"
        );
    });
}

#[test]
fn retired_unopened_core_cannot_create_storage_from_a_queued_write() {
    queued_create_cannot_reopen_retired_state(false);
}

#[test]
fn retired_open_core_cannot_restore_deleted_storage_from_a_queued_write() {
    queued_create_cannot_reopen_retired_state(true);
}

#[tokio::test]
async fn replacing_a_retired_core_preserves_profiles_without_reviving_its_owner() {
    let dir = tempfile::tempdir().unwrap();
    let old = core(dir.path());
    let retained = old.clone().create_profile("Retained".into()).await.unwrap();
    old.retire_local_state();

    let replacement = core(dir.path());
    assert_eq!(replacement.clone().profiles().await, vec![retained.clone()]);
    assert_eq!(old.clone().create_profile("Late".into()).await, None);
    assert_eq!(old.profiles().await, vec![]);
    assert_eq!(replacement.profiles().await, vec![retained]);
}
