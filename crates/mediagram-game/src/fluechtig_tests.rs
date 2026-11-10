use super::*;
use crate::protocol::{FluechtigOption, FluechtigPhone, FluechtigSeen, FluechtigTurn};

const NAMES: [&str; 6] = ["Ana", "Ben", "Cem", "Dana", "Eli", "Fay"];

fn table(n: u8, connected: bool) -> Vec<Player> {
    (0..n).map(|i| Player::for_test(i, NAMES[usize::from(i)], connected)).collect()
}

fn game(players: &[Player], seed: u64) -> FluechtigGame {
    FluechtigGame::new(FluechtigConfig { seed: Some(seed), ..FluechtigConfig::default() }, players, 0.0)
}

fn tap(g: &mut FluechtigGame, players: &mut [Player], who: PlayerId, index: usize, now: f64) -> Result<(), ActionError> {
    g.on_action(&mut GameCtx { now, players }, who, &Action::Choose { index: index as u8, at: now })
}

/// What `who`'s phone shows.
fn phone(g: &FluechtigGame, players: &[Player], who: PlayerId) -> FluechtigPhone {
    match g.phone_screen(&ViewCtx { now: 0.0, players, leader: Some(0) }, who) {
        PhoneScreen::Fluechtig { phone } => phone,
        other => panic!("expected the Flüchtig phone, got {other:?}"),
    }
}

fn button(g: &FluechtigGame, pick: Pick) -> usize {
    g.picks().iter().position(|&p| p == pick).unwrap_or_else(|| panic!("{pick:?} not offered: {:?}", g.picks()))
}

/// Plays `mv` for the mover by its taps: the field, then the ticket if asked.
/// Returns the time after.
fn play(g: &mut FluechtigGame, players: &mut [Player], mv: Move, now: f64) -> f64 {
    let who = g.mover().unwrap();
    let field = button(g, Pick::Go(mv.to));
    tap(g, players, who, field, now).unwrap();
    let mut now = now + g.cfg.chain_ms;
    if g.ask.step == Step::Ticket(mv.to) {
        let ticket = button(g, Pick::Ride(mv.ticket));
        tap(g, players, who, ticket, now).unwrap();
        now += g.cfg.chain_ms;
    }
    now
}

fn scene(g: &FluechtigGame) -> crate::protocol::FluechtigScene {
    match g.tv_view() {
        TvScene::Fluechtig { scene } => scene,
        other => panic!("expected the Flüchtig scene, got {other:?}"),
    }
}

#[test]
fn one_player_runs_and_the_others_share_the_hunters() {
    for (n, pieces, each) in [(2, 4, 4), (3, 4, 2), (6, 5, 1)] {
        let g = game(&table(n, true), 3);
        assert_eq!(g.owners.len(), pieces, "{n} players");
        assert!(!g.owners.contains(&g.fugitive));
        for id in (0..n).filter(|&id| id != g.fugitive) {
            assert_eq!(g.owners.iter().filter(|&&o| o == id).count(), each, "{n} players, player {id}");
        }
    }
}

#[test]
fn the_fugitive_picks_the_field_then_the_ticket_and_back_undoes_the_field() {
    let mut p = table(3, true);
    let mut g = game(&p, 1);
    let runner = g.fugitive;
    let mv = g.chase.moves()[0];
    let ph = phone(&g, &p, runner);
    assert_eq!(ph.hiding, Some(g.chase.fugitive.at));
    let ask = ph.ask.expect("the fugitive moves first");
    assert_eq!(ask.prompt, "Wohin fährst du?");
    assert_eq!(ask.options.last(), Some(&FluechtigOption::Double { left: 1 }));
    let go = button(&g, Pick::Go(mv.to));
    let FluechtigOption::Field { ways, .. } = &ask.options[go] else { panic!("a field") };
    assert!(ways.contains(&mv.ticket) && ways.contains(&Ticket::Schatten), "{ways:?}");

    tap(&mut g, &mut p, runner, go, 0.0).unwrap();
    let ask = phone(&g, &p, runner).ask.unwrap();
    assert_eq!(ask.prompt, format!("Nach Feld {} – womit?", mv.to));
    assert!(ask.options.contains(&FluechtigOption::Ticket { ticket: Ticket::Schatten, left: 3 }));
    assert_eq!(ask.options.last(), Some(&FluechtigOption::Back));
    tap(&mut g, &mut p, runner, ask.options.len() - 1, 400.0).unwrap();
    assert_eq!(g.ask.step, Step::Field);

    play(&mut g, &mut p, mv, 800.0);
    assert_eq!(g.chase.turn, Turn::Hunter(0));
    assert_eq!(g.chase.fugitive.at, mv.to);
}

/// One tap or two must not tell the table which line the fugitive rides,
/// so the fugitive is asked for the ticket even when only one gets there.
#[test]
fn the_fugitive_is_asked_for_the_ticket_even_when_only_one_gets_there() {
    let mut p = table(3, true);
    let mut g = game(&p, 1);
    g.chase.fugitive.tickets.schatten = 0;
    let runner = g.fugitive;
    let mv = g.chase.moves().into_iter().find(|m| g.ways(m.to).len() == 1).expect("a one-way field");

    let go = button(&g, Pick::Go(mv.to));
    tap(&mut g, &mut p, runner, go, 0.0).unwrap();

    assert_eq!(g.ask.step, Step::Ticket(mv.to));
    assert_eq!(g.chase.turn, Turn::Fugitive);
    assert_eq!(g.picks(), vec![Pick::Ride(mv.ticket), Pick::Back]);
}

#[test]
fn a_hunter_with_one_ticket_to_the_field_goes_in_one_tap() {
    let mut p = table(3, true);
    let mut g = game(&p, 1);
    let mv = g.chase.moves()[0];
    play(&mut g, &mut p, mv, 0.0);
    let hunter = g.owners[0];
    let ask = phone(&g, &p, hunter).ask.expect("Jäger 1 moves");
    assert_eq!(ask.prompt, "Wohin fährt Jäger 1?");
    let single = ask.options.iter().position(|o| matches!(o, FluechtigOption::Field { ways, .. } if ways.len() == 1)).expect("a one-way field");
    let FluechtigOption::Field { field, .. } = ask.options[single] else { unreachable!() };
    tap(&mut g, &mut p, hunter, single, 1000.0).unwrap();
    assert_eq!(g.chase.hunters[0].at, field);
    assert_eq!(g.chase.turn, Turn::Hunter(1));
}

#[test]
fn a_double_tap_cannot_answer_the_question_its_first_tap_opened() {
    let mut p = table(2, true);
    let mut g = game(&p, 1);
    let runner = g.fugitive;
    tap(&mut g, &mut p, runner, 0, 1000.0).unwrap();
    assert_eq!(tap(&mut g, &mut p, runner, 0, 1080.0), Err(ActionError::Ignored));
    assert!(matches!(g.ask.step, Step::Ticket(_)));
    assert_eq!(g.chase.turn, Turn::Fugitive);
}

#[test]
fn only_the_mover_may_tap() {
    let mut p = table(3, true);
    let mut g = game(&p, 1);
    let hunter = g.owners[0];
    assert_eq!(tap(&mut g, &mut p, hunter, 0, 0.0), Err(ActionError::Ignored));
    let ph = phone(&g, &p, hunter);
    assert_eq!(ph.ask, None);
    let mover = ph.mover.unwrap();
    assert_eq!((mover.name.as_str(), mover.piece, mover.you), (NAMES[usize::from(g.fugitive)], None, false));
    assert_eq!(ph.next.unwrap().piece, Some(1));
}

#[test]
fn an_absent_mover_moves_by_itself_after_a_while() {
    let mut p = table(3, true);
    let mut g = game(&p, 1);
    let runner = usize::from(g.fugitive);
    p[runner] = Player::for_test(g.fugitive, NAMES[runner], false);
    g.on_tick(&mut GameCtx { now: 1400.0, players: &mut p });
    assert_eq!(g.chase.turn, Turn::Fugitive);
    g.on_tick(&mut GameCtx { now: 1550.0, players: &mut p });
    assert_eq!(g.chase.turn, Turn::Hunter(0));
}

/// A whole game played by the house: the TV and the hunters' phones never
/// carry the fugitive's field until the end; then the route and scores do.
#[test]
fn a_whole_game_keeps_the_fugitive_hidden_until_the_end() {
    for seed in 1..=4 {
        let mut p = table(4, false);
        let mut g = game(&p, seed);
        let mut now = 0.0;
        loop {
            now += 50.0;
            g.on_tick(&mut GameCtx { now, players: &mut p });
            if g.chase.winner().is_some() {
                break;
            }
            let s = scene(&g);
            assert_eq!(s.route, None);
            if matches!(s.turn, FluechtigTurn::Fugitive { .. }) {
                assert!(s.targets.is_empty());
            }
            for e in &s.log {
                assert_eq!(e.seen_at.is_some(), s.reveal_rounds.contains(&e.round), "round {}", e.round);
            }
            for &h in &g.owners {
                let ph = phone(&g, &p, h);
                assert_eq!(ph.hiding, None);
                assert_eq!(ph.ask.is_some(), g.mover() == Some(h));
            }
            assert!(now < 400_000.0, "seed {seed}: the chase must end");
        }
        let s = scene(&g);
        assert_eq!(s.route.as_ref().and_then(|r| r.first()).copied(), Some(g.chase.start));
        let FluechtigTurn::Over { winner, .. } = s.turn else { panic!("over") };
        let winners: Vec<PlayerId> = p.iter().filter(|pl| pl.score == 1).map(|pl| pl.id).collect();
        match winner {
            Side::Fugitive => assert_eq!(winners, vec![g.fugitive]),
            Side::Hunters => assert!(!winners.is_empty() && !winners.contains(&g.fugitive)),
        }
        let final_of = |id| g.phone_screen(&ViewCtx { now, players: &p, leader: Some(0) }, id);
        assert!(matches!(final_of(winners[0]), PhoneScreen::Final { rank: 1, score: 1, .. }));
    }
}

#[test]
fn hunters_see_their_pieces_and_the_last_reveal_never_the_field() {
    let mut p = table(3, true);
    let mut g = game(&p, 2);
    let mut now = 0.0;
    // two rounds by the first legal move, then round 3 is the first reveal
    while g.chase.round < 3 || g.chase.turn != Turn::Hunter(0) {
        let mv = g.chase.moves()[0];
        now = play(&mut g, &mut p, mv, now);
    }
    let seen = g.chase.fugitive.at;
    let ph = phone(&g, &p, g.owners[0]);
    assert_eq!(ph.seen, Some(FluechtigSeen { field: seen, round: 3 }));
    assert_eq!(ph.hiding, None);
    assert_eq!(ph.pieces.iter().map(|x| x.number).collect::<Vec<_>>(), vec![1, 3]);
    assert_eq!(ph.ask.unwrap().prompt, "Wohin fährt Jäger 1?");
}
