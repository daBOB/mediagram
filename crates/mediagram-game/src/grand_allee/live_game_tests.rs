use super::live::{GrandAlleeConfig, GrandAlleeGame};
use super::live_tests::{game, pick, ANA, BEN};
use super::live_tv::board;
use crate::game::{Game, GameCtx, ViewCtx};
use crate::protocol::{Action, GrandAlleeTurn, PhoneScreen, PlayerId, TvScene};
use crate::room_seats::Player;

/// Plays a whole game through the `Game` trait with every phone away, so the
/// house decides everything, and checks the views on the way.
fn whole_game(players: usize, seed: u64) -> (GrandAlleeGame, Vec<Player>) {
    let mut p: Vec<Player> = (0..players).map(|i| Player::for_test(i as u8, &format!("P{i}"), false)).collect();
    let mut g = GrandAlleeGame::new(GrandAlleeConfig { seed: Some(seed), ..GrandAlleeConfig::default() }, &p, 0.0);
    let mut now = 0.0;
    // far beyond the longest game the simulator has seen
    while !g.is_finished() && now < 4.0 * 3_600_000.0 {
        now += 50.0;
        let mut ctx = GameCtx { now, players: &mut p };
        let (before, due) = (g.stage.clone(), g.due(&ctx));
        g.on_tick(&mut ctx);
        assert!(g.stage == before || due.is_some_and(|due| due <= now), "moved on before it was due at {now}: {before:?}");
        if now % 5_000.0 == 0.0 {
            let view = ViewCtx { now, players: &p, leader: None };
            serde_json::to_string(&g.tv_scene(&view)).expect("the TV scene serializes");
            for player in &p {
                serde_json::to_string(&g.phone_screen(&view, player.id)).expect("a phone screen serializes");
            }
        }
    }
    assert!(g.is_finished(), "{players} players, seed {seed}: no end after {now} ms, round {} of {}", g.round, g.rounds);
    (g, p)
}

#[test]
fn whole_games_play_to_the_end_with_the_house_deciding() {
    for (players, seed) in [(2, 1), (4, 2), (8, 3)] {
        let (g, p) = whole_game(players, seed);
        let order = g.standings();
        assert_eq!(order.len(), players);
        let last_standing = g.t.active() == 1;
        assert!(last_standing || g.round > g.rounds, "ends by the round limit or with one player left");
        for (s, player) in p.iter().enumerate() {
            assert_eq!(player.score, g.t.net_worth(s));
        }
        let worths: Vec<i32> = order.iter().filter(|&&s| !g.t.seats[s].out).map(|&s| g.t.net_worth(s)).collect();
        assert!(worths.windows(2).all(|w| w[0] >= w[1]), "the richest first: {worths:?}");
    }
}

/// The client's tests check the TV's layout and hotel models against this
/// board, so a rule change here reaches them; written like ts-rs's types.
#[test]
fn the_board_is_written_out_for_the_client_tests() {
    let json = serde_json::to_string_pretty(&board()).expect("the board serializes") + "\n";
    std::fs::write(concat!(env!("CARGO_MANIFEST_DIR"), "/client/tests/grand-allee-board.json"), json).expect("the board JSON is written");
}

/// What `who`'s phone says, and what the TV says, about the mover's question or move.
fn said(g: &GrandAlleeGame, p: &[Player], who: PlayerId) -> (String, String) {
    let view = ViewCtx { now: 0.0, players: p, leader: None };
    let PhoneScreen::Sheet { prompt: Some(phone), .. } = g.phone_screen(&view, who) else { panic!("no sheet") };
    let TvScene::GrandAllee { scene } = g.tv_scene(&view) else { panic!("not grand allee") };
    let tv = match scene.turn {
        GrandAlleeTurn::Ask { prompt, .. } => prompt,
        GrandAlleeTurn::Note { text, .. } => text,
        other => panic!("neither a question nor a note: {other:?}"),
    };
    (phone, tv)
}

#[test]
fn the_movers_phone_says_du_while_the_tv_and_the_others_name_the_mover() {
    let (mut g, mut p) = game(2, &[4]);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    let (phone, tv) = said(&g, &p, ANA);
    assert_eq!((phone.as_str(), tv.as_str()), ("Grundstück: Was kaufst du?", "Ana steht auf einem Grundstück: Was wird gekauft?"));
    g.on_action(&mut GameCtx { now, players: &mut p }, ANA, &Action::Choose { index: 0, at: now }).unwrap();
    let ((mine, tv), (theirs, _)) = (said(&g, &p, ANA), said(&g, &p, BEN));
    assert!(mine.starts_with("Du kaufst ") && tv.starts_with("Ana kauft "), "{mine} / {tv}");
    assert_eq!(theirs, tv, "the other phones read what the TV reads");
}
