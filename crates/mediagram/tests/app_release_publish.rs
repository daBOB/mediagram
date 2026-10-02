//! Sending an app release to the channel, against an in-memory channel.

mod support;

use mediagram::app_release::badging::Badging;
use mediagram::app_release::publish::publish;
use mlib_spec::app_caption;
use support::channel::FakeChannel;

fn apk(code: i64) -> Badging {
    Badging { package: "com.mediagram.android".into(), version_code: code, version_name: format!("0.{}.0", code / 1000) }
}

#[tokio::test]
async fn a_release_is_sent_captioned_and_pinned() {
    let channel = FakeChannel::new();
    let id = publish(&channel, b"apk bytes", &apk(93_000), 1_790_900_000).await.unwrap();
    channel.with(|c| {
        let message = c.messages.iter().find(|m| m.id == id).unwrap();
        assert!(message.pinned);
        let release = app_caption::parse(&message.caption).unwrap();
        assert_eq!((release.code, release.bytes, release.version.as_str()), (93_000, 9, "0.93.0"));
        assert_eq!(release.sha256, hex::encode(<sha2::Sha256 as sha2::Digest>::digest(b"apk bytes")));
    });
    assert_eq!(channel.document(id), b"apk bytes");
}

#[tokio::test]
async fn the_previous_release_is_unpinned_and_the_index_pin_left_alone() {
    let channel = FakeChannel::new();
    let index = channel.with(|c| c.post("#mlib-index v=2\n{\"pushed_at\":1}".into(), Some(vec![1]), true, true));
    let first = publish(&channel, b"one", &apk(92_000), 1).await.unwrap();
    let second = publish(&channel, b"two", &apk(93_000), 2).await.unwrap();
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
    publish(&channel, b"one", &apk(93_000), 1).await.unwrap();
    let sends = channel.with(|c| c.sends);
    let error = publish(&channel, b"same", &apk(93_000), 2).await.unwrap_err();
    assert!(error.to_string().contains("93000"), "{error}");
    assert_eq!(channel.with(|c| c.sends), sends, "nothing was sent");
}

#[tokio::test]
async fn another_package_is_refused() {
    let channel = FakeChannel::new();
    let other = Badging { package: "com.example.other".into(), ..apk(93_000) };
    assert!(publish(&channel, b"x", &other, 1).await.is_err());
    assert_eq!(channel.with(|c| c.sends), 0);
}
