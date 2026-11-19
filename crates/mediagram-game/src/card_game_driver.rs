//! Plays any card game in tests the way the room does, through the `Game`
//! trait: seated players, taps, time passing, and what the phones and TV show.

use crate::cards::{Card, Rank, Suit};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, CardDesk, CardsScene, PhoneScreen, PlayerId, TvScene};
use crate::room_seats::Player;

pub(crate) const ANA: PlayerId = 0;
pub(crate) const BEN: PlayerId = 1;
pub(crate) const CEM: PlayerId = 2;

pub(crate) fn c(suit: Suit, rank: Rank) -> Card {
    Card::new(suit, rank)
}

/// `n` players, all connected, Ana leading the room.
pub(crate) fn players(n: usize) -> Vec<Player> {
    ["Ana", "Ben", "Cem", "Dana", "Eli", "Fay", "Gus", "Hal"][..n]
        .iter()
        .enumerate()
        .map(|(i, name)| Player::for_test(i as PlayerId, name, true))
        .collect()
}

/// The player's phone drops out at `at`.
pub(crate) fn leave(player: &mut Player, at: f64) {
    player.conn = None;
    player.left_at = Some(at);
}

/// `who` taps `button` with hand and table cards picked by their place on the desk.
pub(crate) fn act(g: &mut impl Game, p: &mut [Player], who: PlayerId, button: &str, hand: &[u8], table: Option<u8>, now: f64) -> Result<(), ActionError> {
    g.on_action(&mut GameCtx { now, players: p }, who, &Action::Cards { button: button.into(), hand: hand.to_vec(), table })
}

pub(crate) fn tick(g: &mut impl Game, p: &mut [Player], now: f64) {
    g.on_tick(&mut GameCtx { now, players: p });
}

pub(crate) fn screen(g: &impl Game, p: &[Player], who: PlayerId, now: f64) -> PhoneScreen {
    g.phone_screen(&ViewCtx { now, players: p, leader: Some(ANA) }, who)
}

pub(crate) fn desk(g: &impl Game, p: &[Player], who: PlayerId, now: f64) -> CardDesk {
    match screen(g, p, who, now) {
        PhoneScreen::Cards { desk } => desk,
        other => panic!("expected a desk, got {other:?}"),
    }
}

pub(crate) fn scene(g: &impl Game, p: &[Player], now: f64) -> CardsScene {
    match g.tv_scene(&ViewCtx { now, players: p, leader: Some(ANA) }) {
        TvScene::Cards { scene } => scene,
        other => panic!("expected a card scene, got {other:?}"),
    }
}
