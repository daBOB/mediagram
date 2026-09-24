use super::*;
use crate::media::test_fixtures;

#[tokio::test]
async fn probing_a_season_keeps_only_files_needing_conversion_in_input_order() {
    if !test_fixtures::ffmpeg_required("show conversion survey") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let playable = test_fixtures::make_faststart_mp4(dir.path());
    let blocked = dir.path().join("Show.S01E02.mkv");
    let status = tokio::process::Command::new("ffmpeg")
        .args(["-v", "error", "-y", "-i"])
        .arg(&playable)
        .args(["-c:v", "copy", "-c:a", "flac"])
        .arg(&blocked)
        .status()
        .await
        .unwrap();
    assert!(status.success());
    let other_blocked = dir.path().join("Show.S01E04.mkv");
    std::fs::copy(&playable, &other_blocked).unwrap();
    let episodes: Vec<_> = [
        playable,
        blocked,
        dir.path().join("missing.mp4"),
        other_blocked,
    ]
    .into_iter()
    .zip(1..)
    .map(|(path, episode)| Episode {
        path,
        season: 1,
        episode,
    })
    .collect();
    let result = survey(&episodes).await;
    assert_eq!(
        result.blockers,
        [
            vec![
                Blocker::Container("mkv".into()),
                Blocker::Audio("flac".into())
            ],
            vec![Blocker::Container("mkv".into())],
        ]
    );
    assert_eq!(result.failures.len(), 1);
    assert!(result.failures[0].contains("checking browser compatibility of"));
    assert!(result.failures[0].contains("missing.mp4"));
    assert!(result.failures[0].contains("ffprobe exited"));
    assert!(result.needs_confirmation());
    assert!(!survey(&[]).await.needs_confirmation());
}

#[tokio::test]
async fn a_probe_failure_alone_still_requires_confirmation() {
    let dir = tempfile::tempdir().unwrap();
    let result = survey(&[Episode {
        path: dir.path().join("missing.mp4"),
        season: 1,
        episode: 1,
    }])
    .await;
    assert!(result.blockers.is_empty());
    assert_eq!(result.failures.len(), 1);
    assert!(result.needs_confirmation());
}

#[test]
fn grouped_reasons_are_distinct_and_stable_across_probe_order() {
    let video = Blocker::Video("hevc".into());
    let audio = Blocker::Audio("flac".into());
    let container = Blocker::Container("mkv".into());
    assert_eq!(
        reasons(&[&video, &audio, &video, &container, &audio]),
        ["Matroska container", "flac audio", "hevc video"]
    );
}
