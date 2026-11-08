use super::live::{GrandAlleeConfig, GrandAlleeGame};
use crate::game::{Game, GameCtx, ViewCtx};
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
        g.on_tick(&mut GameCtx { now, players: &mut p });
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
