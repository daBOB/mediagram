use super::*;

fn core(dir: &std::path::Path) -> Arc<Core> {
    Core::new(
        dir.display().to_string(),
        1,
        "test-hash".into(),
        "test-device".into(),
    )
}

#[tokio::test(flavor = "current_thread")]
async fn local_work_runs_off_the_async_executor_and_returns_its_value() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    let executor_thread = std::thread::current().id();
    let expected_directory = dir.path().to_path_buf();
    let (worker_thread, actual_directory) = core
        .blocking(|core| (std::thread::current().id(), core.data_dir.clone()))
        .await;
    assert_ne!(worker_thread, executor_thread);
    assert_eq!(actual_directory, expected_directory);
}

#[tokio::test]
async fn worker_panics_retain_the_original_payload_at_the_async_boundary() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    let failure =
        tokio::spawn(async move { core.blocking(|_| std::panic::panic_any(42_u32)).await })
            .await
            .unwrap_err();
    assert!(failure.is_panic());
    assert_eq!(*failure.into_panic().downcast::<u32>().unwrap(), 42);
}
