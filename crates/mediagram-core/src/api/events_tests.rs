use super::*;

fn pinned(pinned: bool) -> tl::enums::Update {
    tl::types::UpdatePinnedChannelMessages {
        pinned,
        channel_id: 3816522481,
        messages: vec![3123],
        pts: 1,
        pts_count: 1,
    }
    .into()
}

#[test]
fn a_pin_keeps_its_channel_and_direction() {
    let update = channel_update(&pinned(true)).expect("a pin is a channel update");
    assert_eq!(update.kind, UpdateKind::Pinned);
    assert_eq!(update.channel, 3816522481);
    assert!(update.pinned);
    assert!(!channel_update(&pinned(false)).expect("an unpin too").pinned);
}

#[test]
fn kinds_no_rule_acts_on_are_not_passed_along() {
    let deleted: tl::enums::Update = tl::types::UpdateDeleteChannelMessages {
        channel_id: 3816522481,
        messages: vec![3121],
        pts: 1,
        pts_count: 1,
    }
    .into();
    assert!(channel_update(&deleted).is_none());
}
