use super::*;

fn job(index: usize, total: usize, duration: f64) -> Job {
    Job {
        index,
        total,
        name: "S01E03.mkv".to_string(),
        duration,
    }
}

fn at(minutes: f64, bytes: u64) -> Position {
    Position {
        out_time_us: (minutes * 60.0 * 1e6) as u64,
        total_size: bytes,
    }
}

#[test]
fn a_block_is_drawn_only_once_it_is_complete() {
    let mut position = Position::default();
    assert!(!position.feed("frame=125"));
    assert!(!position.feed("out_time_us=4000000"));
    assert!(!position.feed("total_size=1200000000"));
    assert!(position.feed("progress=continue"));
    assert_eq!(position.out_time_us, 4_000_000);
    assert_eq!(position.total_size, 1_200_000_000);
}

#[test]
fn an_unknown_field_leaves_the_last_known_position_standing() {
    let mut position = Position::default();
    position.feed("out_time_us=4000000");
    position.feed("out_time_us=N/A");
    assert_eq!(position.out_time_us, 4_000_000);
}

#[test]
fn one_file_of_a_folder_says_which_one_and_how_far_it_has_to_go() {
    // Four minutes of a 45 minute episode, done in 12 seconds: 20x, so
    // the 41 minutes still to copy take another two.
    let line = render(
        &job(2, 10, 2700.0),
        &at(4.0, 1_200_000_000),
        Duration::from_secs(12),
    );
    assert_eq!(
        line,
        "  [3/10] S01E03.mkv · 4.0 of 45.0 min (9%) · 1.20 GB · 20x · eta 2m03s"
    );
}

#[test]
fn a_single_file_is_not_counted_against_itself() {
    let line = render(
        &job(0, 1, 2700.0),
        &at(4.0, 1_200_000_000),
        Duration::from_secs(12),
    );
    assert!(line.starts_with("  S01E03.mkv · 4.0 of 45.0 min"));
}

#[test]
fn a_source_of_unknown_length_reports_a_position_and_promises_nothing() {
    let line = render(
        &job(0, 1, 0.0),
        &at(4.0, 1_200_000_000),
        Duration::from_secs(12),
    );
    assert_eq!(line, "  S01E03.mkv · 4.0 min · 1.20 GB · 20x");
}

#[test]
fn nothing_written_yet_is_left_off_rather_than_shown_as_zero() {
    let line = render(&job(0, 1, 2700.0), &at(0.0, 0), Duration::from_millis(300));
    assert_eq!(line, "  S01E03.mkv · 0.0 of 45.0 min (0%)");
}

#[cfg(unix)]
mod processes {
    use super::*;
    use std::path::PathBuf;

    struct ChildFixture {
        dir: tempfile::TempDir,
    }

    impl ChildFixture {
        fn new() -> Self {
            Self {
                dir: tempfile::tempdir().unwrap(),
            }
        }

        fn pid_file(&self) -> PathBuf {
            self.dir.path().join("child.pid")
        }

        fn command(&self, script: &str) -> Command {
            let mut command = Command::new("sh");
            command
                .arg("-c")
                .arg(format!("printf '%s' \"$$\" > \"$1\"; {script}"))
                .arg("ffmpeg-fixture")
                .arg(self.pid_file());
            command
        }

        async fn pid(&self) -> i32 {
            tokio::time::timeout(Duration::from_secs(5), async {
                loop {
                    if let Ok(text) = std::fs::read_to_string(self.pid_file()) {
                        if let Ok(pid) = text.parse() {
                            return pid;
                        }
                    }
                    tokio::time::sleep(Duration::from_millis(10)).await;
                }
            })
            .await
            .expect("child writes its pid")
        }
    }

    impl Drop for ChildFixture {
        fn drop(&mut self) {
            let pid = std::fs::read_to_string(self.pid_file())
                .ok()
                .and_then(|text| text.parse::<i32>().ok());
            if let Some(pid) = pid {
                // A failing regression test must still clean up its own child.
                unsafe {
                    if libc::kill(pid, 0) == 0 {
                        libc::kill(pid, libc::SIGKILL);
                        libc::waitpid(pid, std::ptr::null_mut(), 0);
                    }
                }
            }
        }
    }

    async fn assert_reaped(pid: i32) {
        tokio::time::timeout(Duration::from_secs(5), async {
            loop {
                // Signal 0 still succeeds for zombies: ESRCH proves reaping,
                // rather than just observing that the process stopped working.
                if unsafe { libc::kill(pid, 0) } == -1
                    && std::io::Error::last_os_error().raw_os_error() == Some(libc::ESRCH)
                {
                    return;
                }
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
        })
        .await
        .expect("child must be killed and reaped");
    }

    #[tokio::test]
    async fn invalid_progress_kills_and_reaps_the_child_before_returning() {
        let fixture = ChildFixture::new();
        let command = fixture.command("printf '\\377\\n'; exec sleep 60");
        let error = tokio::time::timeout(Duration::from_secs(5), run(command, &job(0, 1, 1.0)))
            .await
            .unwrap()
            .unwrap_err();
        assert!(format!("{error:#}").contains("reading ffmpeg's progress"));
        let pid = fixture.pid().await;
        assert_eq!(
            unsafe { libc::kill(pid, 0) },
            -1,
            "child remains after error return"
        );
        assert_eq!(
            std::io::Error::last_os_error().raw_os_error(),
            Some(libc::ESRCH)
        );
    }

    #[tokio::test]
    async fn cancelling_a_run_kills_and_reaps_the_child() {
        let fixture = ChildFixture::new();
        let command =
            fixture.command("printf 'diagnostic before cancellation\\n' >&2; exec sleep 60");
        let task = tokio::spawn(async move { run(command, &job(0, 1, 1.0)).await });
        let pid = fixture.pid().await;
        task.abort();
        assert!(task.await.unwrap_err().is_cancelled());
        assert_reaped(pid).await;
    }

    #[tokio::test]
    async fn failed_exit_retains_the_last_diagnostic_even_with_non_utf8_stderr() {
        let fixture = ChildFixture::new();
        let command = fixture.command("printf 'first\\ncodec \\377 refused\\n' >&2; exit 7");
        let error = run(command, &job(0, 1, 1.0)).await.unwrap_err();
        let text = error.to_string();
        assert!(text.contains("ffmpeg exited"), "{text}");
        assert!(text.ends_with("codec � refused"), "{text}");
        assert_reaped(fixture.pid().await).await;
    }

    #[tokio::test]
    async fn success_drains_more_than_a_pipe_of_stderr_without_deadlocking() {
        let fixture = ChildFixture::new();
        let command = fixture.command(
            "i=0; while [ $i -lt 10000 ]; do printf 'a diagnostic line\\n' >&2; i=$((i + 1)); done; printf 'progress=end\\n'",
        );
        tokio::time::timeout(Duration::from_secs(5), run(command, &job(0, 1, 1.0)))
            .await
            .unwrap()
            .unwrap();
        assert_reaped(fixture.pid().await).await;
    }
}
