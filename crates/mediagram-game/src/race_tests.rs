use super::*;
use crate::protocol::RaceStage;

const ANA: PlayerId = 0;
const BEN: PlayerId = 1;

fn game(rolls: &[u8]) -> (RaceGame, Vec<Player>) {
    let players = vec![Player::for_test(ANA, "Ana", true), Player::for_test(BEN, "Ben", true)];
    let cfg = RaceConfig { rolls: rolls.to_vec(), seed: Some(1), ..RaceConfig::default() };
    (RaceGame::new(cfg, &players, 0.0), players)
}

fn tap(g: &mut RaceGame, players: &mut [Player], who: PlayerId, index: u8, now: f64) -> Result<(), ActionError> {
    g.on_action(&mut GameCtx { now, players }, who, &Action::Choose { index, at: now })
}

/// Ticks every 50 ms from `from` up to `to` and returns `to`.
fn run(g: &mut RaceGame, players: &mut [Player], from: f64, to: f64) -> f64 {
    let mut now = from;
    while now < to {
        now += 50.0;
        g.on_tick(&mut GameCtx { now, players });
    }
    to
}

fn phone(g: &RaceGame, players: &[Player], who: PlayerId, now: f64) -> PhoneScreen {
    g.phone_screen(&ViewCtx { now, players, leader: Some(ANA) }, who)
}

#[test]
fn a_six_brings_a_piece_out_and_rolls_again() {
    let (mut g, mut p) = game(&[6, 3]);
    assert!(matches!(phone(&g, &p, ANA, 0.0), PhoneScreen::Choice { ref options, closes_at: None, .. } if options[0].label == "Würfeln"));
    assert!(matches!(phone(&g, &p, BEN, 0.0), PhoneScreen::Wait { ref title, .. } if title == "Ana ist dran"));
    tap(&mut g, &mut p, ANA, 0, 100.0).unwrap();
    assert!(matches!(phone(&g, &p, ANA, 200.0), PhoneScreen::Wait { ref title, .. } if title == "Der Würfel rollt …"));
    // The only move plays itself once the die has landed and was shown.
    let now = run(&mut g, &mut p, 100.0, 100.0 + 1150.0 + 800.0 + 50.0);
    assert!(matches!(g.stage, Stage::Moving { mv: Move { from: None, to: 0, .. }, .. }));
    // a move starts 200 ms after it is decided, then one 280 ms hop
    let now = run(&mut g, &mut p, now, now + 200.0 + 280.0 + 100.0);
    assert!(matches!(g.stage, Stage::Roll { again: true, .. }), "a 6 rolls again");
    assert_eq!(g.turn, 0);
    tap(&mut g, &mut p, ANA, 0, now).unwrap();
    let now = run(&mut g, &mut p, now, now + 1150.0 + 800.0 + 50.0);
    let now = run(&mut g, &mut p, now, now + 200.0 + 3.0 * 280.0 + 100.0);
    assert_eq!(g.seats[0].pieces[0], Some(3));
    assert_eq!(g.turn, 1, "a 3 passes the turn");
    assert!(matches!(phone(&g, &p, BEN, now), PhoneScreen::Choice { .. }));
}

#[test]
fn three_tries_for_a_six_then_the_turn_passes() {
    let (mut g, mut p) = game(&[1, 2, 4]);
    let mut now = 0.0;
    for (n, prompt) in ["Du bist dran – drei Versuche für eine 6", "Noch zwei Versuche", "Letzter Versuch"].iter().enumerate() {
        assert!(matches!(phone(&g, &p, ANA, now), PhoneScreen::Choice { prompt: Some(ref t), .. } if t == prompt), "try {n}");
        tap(&mut g, &mut p, ANA, 0, now).unwrap();
        now = run(&mut g, &mut p, now, now + 1150.0 + 1600.0 + 50.0);
    }
    assert_eq!(g.turn, 1);
}

#[test]
fn the_player_picks_between_moves_after_the_die_lands() {
    let (mut g, mut p) = game(&[3]);
    g.seats[0].pieces = [Some(5), Some(12), Some(40), Some(41)];
    g.start_turn(0.0, 0, false);
    tap(&mut g, &mut p, ANA, 0, 0.0).unwrap();
    assert_eq!(tap(&mut g, &mut p, ANA, 1, 300.0), Err(ActionError::Ignored), "the die is still in the air");
    assert_eq!(tap(&mut g, &mut p, BEN, 1, 1200.0), Err(ActionError::Ignored), "not Ben's turn");
    let PhoneScreen::Choice { options, opens_at, .. } = phone(&g, &p, ANA, 1200.0) else { panic!("expected a choice") };
    assert_eq!(opens_at, 1150.0);
    let labels: Vec<&str> = options.iter().map(|o| o.label.as_str()).collect();
    assert_eq!(labels, ["Hinterste Figur", "Vorderste Figur"], "40 + 3 would jump over 41, 41 + 3 is past the end");
    tap(&mut g, &mut p, ANA, 1, 1200.0).unwrap();
    assert_eq!(g.seats[0].pieces[1], Some(15));
}

#[test]
fn a_capture_tells_the_victim() {
    let (mut g, mut p) = game(&[3]);
    g.seats[0].pieces = [Some(22), None, None, None];
    g.seats[1].pieces = [Some(5), None, None, None]; // Ben sits opposite: his 5 is field 25
    g.start_turn(0.0, 0, false);
    tap(&mut g, &mut p, ANA, 0, 0.0).unwrap();
    run(&mut g, &mut p, 0.0, 2000.0);
    assert_eq!(g.seats[1].pieces[0], None);
    assert!(matches!(phone(&g, &p, BEN, 2000.0), PhoneScreen::Wait { ref title, .. } if title == "Rausgeworfen!"));
    let TvScene::Race { scene } = g.tv_view() else { panic!() };
    assert!(matches!(scene.stage, RaceStage::Moving { ref mv, .. } if mv.captures.as_ref().is_some_and(|c| c.player == BEN)));
}

#[test]
fn an_absent_player_plays_by_itself() {
    let (mut g, mut p) = game(&[2, 6, 2]);
    p[1] = Player::for_test(BEN, "Ben", false);
    g.seats[0].pieces = [Some(10), None, None, None];
    g.start_turn(0.0, 0, false);
    tap(&mut g, &mut p, ANA, 0, 0.0).unwrap();
    let now = run(&mut g, &mut p, 0.0, 2900.0);
    assert_eq!(g.turn, 1);
    run(&mut g, &mut p, now, now + 1500.0 + 1150.0 + 800.0 + 300.0);
    assert_eq!(g.seats[1].pieces[0], Some(0), "Ben's 6 rolled and moved without him");
}

#[test]
fn a_dropout_after_a_long_think_gets_the_full_grace() {
    let (mut g, mut p) = game(&[3]);
    g.seats[0].pieces = [Some(5), Some(12), None, None];
    g.start_turn(0.0, 0, false);
    tap(&mut g, &mut p, ANA, 0, 0.0).unwrap();
    // Ana thinks over her two pieces for five seconds, then her phone drops.
    let now = run(&mut g, &mut p, 0.0, 6150.0);
    p[0].conn = None;
    p[0].left_at = Some(now);
    let now = run(&mut g, &mut p, now, now + 1400.0);
    assert!(matches!(g.stage, Stage::Rolled { .. }), "no move made for her straight after the drop");
    run(&mut g, &mut p, now, now + 200.0);
    assert!(matches!(g.stage, Stage::Moving { .. }), "played for her 1.5 s after she left");
}

#[test]
fn the_last_piece_home_wins() {
    let (mut g, mut p) = game(&[2]);
    g.seats[0].pieces = [Some(41), Some(42), Some(43), Some(38)];
    g.seats[1].pieces = [Some(40), Some(9), None, None];
    g.start_turn(0.0, 0, false);
    tap(&mut g, &mut p, ANA, 0, 0.0).unwrap();
    run(&mut g, &mut p, 0.0, 5000.0);
    assert!(g.is_finished());
    assert_eq!(p[0].score, 4);
    assert!(matches!(phone(&g, &p, ANA, 5000.0), PhoneScreen::Final { rank: 1, of: 2, score: 4, can_restart: true }));
    assert!(matches!(phone(&g, &p, BEN, 5000.0), PhoneScreen::Final { rank: 2, score: 1, can_restart: false, .. }));
    let TvScene::Race { scene } = g.tv_view() else { panic!() };
    assert_eq!(scene.stage, RaceStage::Final { winner: ANA, ranking: vec![ANA, BEN] });
}

#[test]
fn the_tv_sees_when_the_die_lands() {
    let (mut g, mut p) = game(&[4]);
    tap(&mut g, &mut p, ANA, 0, 500.0).unwrap();
    let TvScene::Race { scene } = g.tv_view() else { panic!() };
    assert_eq!(scene.die, Some(4));
    assert!(matches!(scene.stage, RaceStage::Rolled { rolled_at: 500.0, lands_at: 1650.0, ref moves, .. } if moves.is_empty()));
    assert_eq!(scene.seats.iter().map(|s| s.corner).collect::<Vec<_>>(), [0, 2]);
}
