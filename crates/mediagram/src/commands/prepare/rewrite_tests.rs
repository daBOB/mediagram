//! Real ffmpeg output must be checked and published before any source is deleted.

use std::path::PathBuf;

use super::{Candidate, PrepareArgs, plan_prepare, rewrite::rewrite_all};
use crate::media::prepare::paths::working_path;
use crate::media::streams::{self, StreamKind};
use crate::media::test_fixtures::{ffmpeg_required, make_faststart_mp4};

struct Fixture {
    _dir: tempfile::TempDir,
    candidate: Candidate,
    args: PrepareArgs,
    dest: PathBuf,
    original: Vec<u8>,
}

impl Fixture {
    async fn new(replace: bool) -> Self {
        let dir = tempfile::tempdir().unwrap();
        let source_dir = dir.path().join("source");
        std::fs::create_dir(&source_dir).unwrap();
        let source = make_faststart_mp4(&source_dir);
        let original = std::fs::read(&source).unwrap();
        let probed = streams::probe(&source).await.unwrap();
        let candidate = Candidate {
            file: source.clone(),
            size: original.len() as u64,
            duration: probed.duration,
            plan: plan_prepare(
                &probed.streams,
                original.len() as u64,
                probed.duration,
                &["und"],
                &[],
                1,
            ),
        };
        let out = (!replace).then(|| dir.path().join("output"));
        let dest = out.as_ref().map_or_else(
            || source.clone(),
            |out| out.join(source.file_name().unwrap()),
        );
        let args = PrepareArgs {
            path: source,
            replace,
            audio: "und".into(),
            subs: String::new(),
            limit: Some(1),
            mp4: true,
            out,
            delete_source: !replace,
        };
        Self {
            _dir: dir,
            candidate,
            args,
            dest,
            original,
        }
    }

    async fn run(&self) -> anyhow::Result<()> {
        rewrite_all(
            &self.args,
            &super::split_languages(&self.args.audio),
            &[&self.candidate],
        )
        .await
    }

    fn assert_source_unchanged(&self) {
        assert_eq!(std::fs::read(&self.candidate.file).unwrap(), self.original);
    }

    fn seed_destination(&self) {
        if !self.args.replace {
            std::fs::create_dir_all(self.dest.parent().unwrap()).unwrap();
            std::fs::write(&self.dest, &self.original).unwrap();
        }
    }
}

#[tokio::test]
async fn ffmpeg_failure_preserves_source_and_existing_destination() {
    if !ffmpeg_required("prepare process failure") {
        return;
    }
    for replace in [false, true] {
        let mut fixture = Fixture::new(replace).await;
        fixture.seed_destination();
        fixture.candidate.plan.keep[0].index = 999;

        assert!(fixture.run().await.is_err());

        fixture.assert_source_unchanged();
        assert_eq!(std::fs::read(&fixture.dest).unwrap(), fixture.original);
        assert!(!working_path(&fixture.dest).exists());
    }
}

#[tokio::test]
async fn successfully_written_output_without_video_is_rejected_before_replacement() {
    if !ffmpeg_required("prepare rejected output") {
        return;
    }
    for replace in [false, true] {
        let mut fixture = Fixture::new(replace).await;
        fixture.seed_destination();
        fixture
            .candidate
            .plan
            .keep
            .retain(|stream| stream.kind == StreamKind::Audio);
        assert!(!fixture.candidate.plan.keep.is_empty());

        assert!(fixture.run().await.is_err());

        fixture.assert_source_unchanged();
        assert_eq!(std::fs::read(&fixture.dest).unwrap(), fixture.original);
        assert!(!working_path(&fixture.dest).exists());
    }
}

#[tokio::test]
async fn failed_final_rename_preserves_the_source_requested_for_deletion() {
    if !ffmpeg_required("prepare rename failure") {
        return;
    }
    let fixture = Fixture::new(false).await;
    std::fs::create_dir_all(&fixture.dest).unwrap();
    let marker = fixture.dest.join("keep");
    std::fs::write(&marker, b"existing directory").unwrap();

    assert!(fixture.run().await.is_err());

    fixture.assert_source_unchanged();
    assert_eq!(std::fs::read(&marker).unwrap(), b"existing directory");
    // The transcode and verification succeeded; only publication was refused.
    assert!(
        streams::probe(&working_path(&fixture.dest))
            .await
            .unwrap()
            .streams
            .iter()
            .any(|s| s.kind == StreamKind::Video)
    );
}

#[tokio::test]
async fn source_is_deleted_only_after_the_validated_output_is_published() {
    if !ffmpeg_required("prepare successful output") {
        return;
    }
    let fixture = Fixture::new(false).await;

    fixture.run().await.unwrap();

    assert!(!fixture.candidate.file.exists());
    assert!(!working_path(&fixture.dest).exists());
    let result = streams::probe(&fixture.dest).await.unwrap();
    assert!(result.streams.iter().any(|s| s.kind == StreamKind::Video));
    assert!((result.duration - fixture.candidate.duration).abs() <= 1.0);
}

#[tokio::test]
async fn successful_in_place_replacement_contains_the_selected_streams() {
    if !ffmpeg_required("prepare in-place replacement") {
        return;
    }
    let mut fixture = Fixture::new(true).await;
    fixture.args.audio.clear();
    fixture
        .candidate
        .plan
        .keep
        .retain(|stream| stream.kind == StreamKind::Video);

    fixture.run().await.unwrap();

    assert!(!working_path(&fixture.dest).exists());
    let result = streams::probe(&fixture.dest).await.unwrap();
    assert!(result.streams.iter().any(|s| s.kind == StreamKind::Video));
    assert!(result.streams.iter().all(|s| s.kind != StreamKind::Audio));
    assert_ne!(std::fs::read(&fixture.dest).unwrap(), fixture.original);
}
