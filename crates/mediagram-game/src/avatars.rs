//! The avatar catalogue: every player owns one, first come first served.

use rand::seq::IndexedRandom;
use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::PlayerId;
use crate::room_seats::Player;

/// Ids are the folder names of the rendered images (`/avatars/<id>-head.webp`).
pub const AVATARS: [&str; 22] = [
    "android", "armour-hero", "bear", "bodybuilder", "bunny", "cat", "dark-knight", "fashion-doll", "fox",
    "frog", "ghost", "granny", "hedgehog", "mummy", "panda", "piglet", "princess", "raccoon", "samurai",
    "vampire", "witch", "yeti",
];

/// One catalogue entry as a phone sees it.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct AvatarSlot {
    pub id: String,
    /// Owned by someone other than the viewer.
    pub taken: bool,
}

/// The catalogue id for `id`, if it names an avatar.
pub(crate) fn known(id: &str) -> Option<&'static str> {
    AVATARS.iter().copied().find(|a| *a == id)
}

fn is_free(players: &[Player], avatar: &str) -> bool {
    !players.iter().any(|p| p.avatar == Some(avatar))
}

/// The wanted avatar if it exists and is free, else a random free one.
pub(crate) fn assign(players: &[Player], wanted: Option<&str>) -> Option<&'static str> {
    wanted.and_then(known).filter(|a| is_free(players, a)).or_else(|| {
        let free: Vec<_> = AVATARS.iter().copied().filter(|a| is_free(players, a)).collect();
        free.choose(&mut rand::rng()).copied()
    })
}

/// Gives `id` the avatar `wanted` if it exists and nobody else has it.
pub(crate) fn pick(players: &mut [Player], id: PlayerId, wanted: &str) -> bool {
    let Some(avatar) = known(wanted) else {
        return false;
    };
    if players.iter().any(|p| p.id != id && p.avatar == Some(avatar)) {
        return false;
    }
    players.iter_mut().find(|p| p.id == id).map(|p| p.avatar = Some(avatar)).is_some()
}

pub(crate) fn slots_for(players: &[Player], viewer: PlayerId) -> Vec<AvatarSlot> {
    AVATARS
        .iter()
        .map(|a| AvatarSlot {
            id: (*a).into(),
            taken: players.iter().any(|p| p.id != viewer && p.avatar == Some(*a)),
        })
        .collect()
}
