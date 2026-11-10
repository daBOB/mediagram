//! Sending an app release to the channel, against an in-memory channel.

mod support;

use mediagram::app_release::badging::Badging;
use mediagram::app_release::publish::publish;
use mlib_spec::app_caption::{self, MEDIA_APP_TAG, SPIELTISCH_TAG};
use support::channel::FakeChannel;

fn apk(code: i64) -> Badging {
    Badging {
        package: "com.mediagram.android".into(),
        version_code: code,
        version_name: format!("0.{}.0", code / 1000),
    }
}

fn games(code: i64) -> Badging {
    Badging {
        package: "com.mediagram.spieltisch".into(),
        ..apk(code)
    }
}

#[tokio::test]
async fn a_release_is_sent_captioned_and_pinned() {
    let channel = FakeChannel::new();
    let id = publish(&channel, b"apk bytes", None, &apk(93_000), 1_790_900_000)
        .await
        .unwrap();
    channel.with(|c| {
        let message = c.messages.iter().find(|m| m.id == id).unwrap();
        assert!(message.pinned);
        let release = app_caption::parse(MEDIA_APP_TAG, &message.caption).unwrap();
        assert_eq!(
            (release.code, release.bytes, release.version.as_str()),
            (93_000, 9, "0.93.0")
        );
        assert_eq!(
            release.sha256,
            hex::encode(<sha2::Sha256 as sha2::Digest>::digest(b"apk bytes"))
        );
    });
    assert_eq!(channel.document(id), b"apk bytes");
}

#[tokio::test]
async fn the_previous_release_is_unpinned_and_the_index_pin_left_alone() {
    let channel = FakeChannel::new();
    let index = channel.with(|c| {
        c.post(
            "#mlib-index v=2\n{\"pushed_at\":1}".into(),
            Some(vec![1]),
            true,
            true,
        )
    });
    let first = publish(&channel, b"one", None, &apk(92_000), 1)
        .await
        .unwrap();
    let second = publish(&channel, b"two", None, &apk(93_000), 2)
        .await
        .unwrap();
    channel.with(|c| {
        let pinned = |id| c.messages.iter().find(|m| m.id == id).unwrap().pinned;
        assert!(pinned(index), "the index stays pinned");
        assert!(!pinned(first), "the replaced release is unpinned");
        assert!(pinned(second));
    });
}

#[tokio::test]
async fn a_version_code_not_above_the_newest_release_is_refused() {
    let channel = FakeChannel::new();
    publish(&channel, b"one", None, &apk(93_000), 1)
        .await
        .unwrap();
    let sends = channel.with(|c| c.sends);
    let error = publish(&channel, b"same", None, &apk(93_000), 2)
        .await
        .unwrap_err();
    assert!(error.to_string().contains("93000"), "{error}");
    assert_eq!(channel.with(|c| c.sends), sends, "nothing was sent");
}

#[tokio::test]
async fn another_package_is_refused() {
    let channel = FakeChannel::new();
    let other = Badging {
        package: "com.example.other".into(),
        ..apk(93_000)
    };
    assert!(publish(&channel, b"x", None, &other, 1).await.is_err());
    assert_eq!(channel.with(|c| c.sends), 0);
}

#[tokio::test]
async fn a_profile_goes_first_unpinned_and_the_caption_names_it() {
    let channel = FakeChannel::new();
    let id = publish(&channel, b"apk bytes", Some(b"dm bytes"), &apk(93_000), 1)
        .await
        .unwrap();
    let profile = channel.with(|c| {
        assert_eq!(c.sent_names, ["mediagram-0.93.0.dm", "mediagram-0.93.0.apk"]);
        let caption = &c.messages.iter().find(|m| m.id == id).unwrap().caption;
        let release = app_caption::parse(MEDIA_APP_TAG, caption).unwrap();
        let profile = release.profile.unwrap();
        let message = c
            .messages
            .iter()
            .find(|m| i64::from(m.id) == profile.message)
            .unwrap();
        assert!(!message.pinned, "only the APK is pinned");
        assert!(
            message.id < id,
            "the profile is posted before the caption that names it"
        );
        assert_eq!(profile.bytes, 8);
        assert_eq!(
            profile.sha256,
            hex::encode(<sha2::Sha256 as sha2::Digest>::digest(b"dm bytes"))
        );
        profile.message
    });
    assert_eq!(
        channel.document(i32::try_from(profile).unwrap()),
        b"dm bytes"
    );
}

#[tokio::test]
async fn spieltisch_goes_out_under_its_own_tag_and_file_names() {
    let channel = FakeChannel::new();
    let id = publish(&channel, b"games", Some(b"dm"), &games(175_000), 1)
        .await
        .unwrap();
    channel.with(|c| {
        assert_eq!(c.sent_names, ["spieltisch-0.175.0.dm", "spieltisch-0.175.0.apk"]);
        let message = c.messages.iter().find(|m| m.id == id).unwrap();
        assert!(message.pinned);
        assert!(message.caption.starts_with("#spieltisch-app v=1\n"));
        assert_eq!(app_caption::parse(SPIELTISCH_TAG, &message.caption).unwrap().code, 175_000);
        assert!(
            app_caption::parse(MEDIA_APP_TAG, &message.caption).is_none(),
            "a media app must never read it as its own release"
        );
    });
}

/// Each app is held to its own newest release: a Spieltisch build at or below
/// Spieltisch's pinned one is refused, however the media app's versions stand.
#[tokio::test]
async fn a_spieltisch_release_not_above_its_own_newest_is_refused() {
    let channel = FakeChannel::new();
    publish(&channel, b"m1", None, &apk(174_000), 1).await.unwrap();
    publish(&channel, b"g1", None, &games(175_001), 2).await.unwrap();
    let sends = channel.with(|c| c.sends);

    let error = publish(&channel, b"g0", None, &games(175_001), 3).await.unwrap_err();

    assert!(error.to_string().contains("175001"), "{error}");
    assert_eq!(channel.with(|c| c.sends), sends, "nothing was sent");
}

/// Unpinning the other app's release would cut its devices off from updates,
/// and its versionCode must not hold this app's release back.
#[tokio::test]
async fn each_app_replaces_only_its_own_release() {
    let channel = FakeChannel::new();
    let pinned = |id| channel.with(|c| c.messages.iter().find(|m| m.id == id).unwrap().pinned);
    let media_one = publish(&channel, b"m1", None, &apk(174_000), 1).await.unwrap();
    let games_one = publish(&channel, b"g1", None, &games(175_000), 2).await.unwrap();
    assert!(pinned(media_one), "a Spieltisch release leaves the media app's pinned");

    let games_two = publish(&channel, b"g2", None, &games(175_001), 3).await.unwrap();
    assert!(!pinned(games_one), "the replaced Spieltisch release is unpinned");
    assert!(pinned(media_one));

    let media_two = publish(&channel, b"m2", None, &apk(174_001), 4)
        .await
        .expect("a versionCode below Spieltisch's is still the media app's newest");
    assert!(!pinned(media_one), "the replaced media app release is unpinned");
    assert!(pinned(games_two), "a media app release leaves Spieltisch's pinned");
    assert!(pinned(media_two));
}
