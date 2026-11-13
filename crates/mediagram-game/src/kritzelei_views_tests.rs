use super::*;
use crate::game::Game;
use crate::kritzelei::tests::{act, game, pick, players, tick};
use crate::protocol::{BoardAct, Tone};

fn vc(ps: &[Player], now: f64) -> ViewCtx<'_> {
    ViewCtx { now, players: ps, leader: Some(0) }
}

fn json<T: serde::Serialize>(v: &T) -> String {
    serde_json::to_string(v).unwrap()
}

fn phone_json(g: &KritzeleiGame, ps: &[Player], who: PlayerId) -> String {
    json(&g.phone_view(&vc(ps, 0.0), who))
}

fn waits(title: &str, text: Option<&str>) -> PhoneScreen {
    PhoneScreen::Wait { title: title.into(), text: text.map(Into::into) }
}

#[test]
fn the_word_reaches_only_the_board_until_the_reveal() {
    let mut ps = players(3);
    let mut g = game(&ps);
    let word = "Hund";
    let hidden = |g: &KritzeleiGame, ps: &[Player]| {
        for who in 0..3 {
            assert!(!phone_json(g, ps, who).contains(word), "phone {who}");
        }
        assert!(!json(&g.tv_view(&vc(ps, 0.0))).contains(word), "tv");
    };
    hidden(&g, &ps);
    assert!(!json(&g.board_view(&vc(&ps, 0.0))).contains(word), "board in handover");
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    hidden(&g, &ps);
    assert!(json(&g.board_view(&vc(&ps, 0.0))).contains(word), "board drawing");
    act(&mut g, &mut ps, 1_000.0, BoardAct::Guessed).unwrap();
    hidden(&g, &ps);
    assert!(json(&g.board_view(&vc(&ps, 1_000.0))).contains(word), "board picking");
    act(&mut g, &mut ps, 2_000.0, pick(None)).unwrap();
    assert!(json(&g.tv_view(&vc(&ps, 2_000.0))).contains(word), "tv reveal");
}

#[test]
fn the_drawer_is_buzzed_for_the_turn_and_the_guesser_for_the_point() {
    let mut ps = players(3);
    let mut g = game(&ps);
    let all = |g: &KritzeleiGame| (0..3).map(|p| g.buzz(p)).collect::<Vec<_>>();
    let turn = [Some(Buzz::Turn), None, None];
    assert_eq!(all(&g), turn);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    assert_eq!(all(&g), turn);
    act(&mut g, &mut ps, 1_000.0, BoardAct::Guessed).unwrap();
    assert_eq!(all(&g), turn);
    act(&mut g, &mut ps, 2_000.0, pick(Some(2))).unwrap();
    assert_eq!(all(&g), [None, None, Some(Buzz::Accepted)]);
    tick(&mut g, &mut ps, 9_000.0);
    act(&mut g, &mut ps, 9_001.0, BoardAct::Ready).unwrap();
    act(&mut g, &mut ps, 9_002.0, pick(None)).unwrap();
    assert_eq!(all(&g), [None, None, None]);
}

#[test]
fn the_drawer_fetches_the_ipad_and_the_rest_guess_out_loud() {
    let ps = players(3);
    let g = game(&ps);
    let v = vc(&ps, 0.0);
    assert_eq!(g.phone_view(&v, 0), waits("Du zeichnest – hol dir das iPad", None));
    assert_eq!(g.phone_view(&v, 1), waits("Rate laut!", Some("Ana zeichnet.")));
}

#[test]
fn picking_lists_everyone_seated_but_the_drawer() {
    let mut ps = players(4);
    ps[2].conn = None;
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    act(&mut g, &mut ps, 1_000.0, BoardAct::Guessed).unwrap();
    let BoardScreen::Picking { guessers, can_go_back, .. } = g.board_view(&vc(&ps, 1_000.0)) else { panic!("not picking") };
    assert_eq!(guessers.iter().map(|p| p.id).collect::<Vec<_>>(), [1, 2, 3]);
    assert!(can_go_back);
}

#[test]
fn drawing_and_picking_name_the_drawer() {
    let mut ps = players(3);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    g.on_board_presence(500.0, false);
    g.on_board_presence(900.0, true);
    let BoardScreen::Drawing { drawer, .. } = g.board_view(&vc(&ps, 1_000.0)) else { panic!("not drawing") };
    assert_eq!((drawer.id, drawer.name.as_str()), (0, "Ana"));
    act(&mut g, &mut ps, 1_000.0, BoardAct::Guessed).unwrap();
    let BoardScreen::Picking { drawer, .. } = g.board_view(&vc(&ps, 1_000.0)) else { panic!("not picking") };
    assert_eq!(drawer.id, 0);
}

#[test]
fn after_time_up_the_list_cannot_go_back() {
    let mut ps = players(3);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    tick(&mut g, &mut ps, 90_000.0);
    let BoardScreen::Picking { can_go_back, clock, .. } = g.board_view(&vc(&ps, 90_000.0)) else { panic!("not picking") };
    assert!(!can_go_back);
    assert_eq!((clock.ends_at, clock.left_ms), (None, 0.0));
}

#[test]
fn the_reveal_says_who_got_it() {
    let mut ps = players(3);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    act(&mut g, &mut ps, 1_000.0, pick(Some(1))).unwrap();
    let out = |title: &str, text: &str, tone, points| PhoneScreen::Outcome {
        title: title.into(), text: Some(text.into()), tone, points, keys: vec![], show_score: true,
    };
    let v = vc(&ps, 1_000.0);
    assert_eq!(g.phone_view(&v, 1), out("Richtig!", "Hund", Tone::Good, 2));
    assert_eq!(g.phone_view(&v, 0), out("Hund", "Bo hat's erraten", Tone::Good, 1));
    assert_eq!(g.phone_view(&v, 2), out("Hund", "Bo hat's erraten", Tone::Neutral, 0));
    let BoardScreen::Wait { title, text, can_leave } = g.board_view(&v) else { panic!("not a wait") };
    assert_eq!((title.as_str(), text.as_deref(), can_leave), ("Hund", Some("Bo hat's erraten"), false));

    let mut ps = players(3);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    act(&mut g, &mut ps, 1_000.0, pick(None)).unwrap();
    let v = vc(&ps, 1_000.0);
    assert_eq!(g.phone_view(&v, 0), out("Hund", "Niemand hat's erraten", Tone::Neutral, 0));
    assert_eq!(g.phone_view(&v, 1), out("Hund", "Niemand hat's erraten", Tone::Neutral, 0));
}

#[test]
fn the_final_ranks_by_score() {
    let mut ps = players(2);
    let mut g = game(&ps);
    let mut now = 0.0;
    // Four turns: the first is won by Bo (+2 Bo, +1 Ana), the rest by nobody.
    for i in 0..4 {
        act(&mut g, &mut ps, now + 1.0, BoardAct::Ready).unwrap();
        act(&mut g, &mut ps, now + 2.0, pick((i == 0).then_some(1))).unwrap();
        now += 6_100.0;
        tick(&mut g, &mut ps, now);
    }
    assert!(g.is_finished());
    let v = vc(&ps, now);
    assert_eq!(g.phone_view(&v, 1), PhoneScreen::Final { rank: 1, of: 2, score: 2, can_restart: false });
    assert_eq!(g.phone_view(&v, 0), PhoneScreen::Final { rank: 2, of: 2, score: 1, can_restart: true });
    let BoardScreen::Wait { title, text, can_leave } = g.board_view(&v) else { panic!("not a wait") };
    assert_eq!((title.as_str(), text.as_deref(), can_leave), ("Spiel vorbei", Some("Die Siegerehrung läuft auf dem Fernseher."), false));
}

#[test]
fn the_tv_shows_when_the_board_is_gone() {
    let ps = players(2);
    let mut g = game(&ps);
    let board = |g: &KritzeleiGame| match g.tv_view(&vc(&ps, 0.0)) {
        TvScene::Kritzelei { scene } => scene.board,
        _ => panic!("not Kritzelei"),
    };
    assert!(board(&g));
    g.on_board_presence(0.0, false);
    assert!(!board(&g));
}
