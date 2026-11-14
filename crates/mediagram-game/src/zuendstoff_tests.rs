use rand::rngs::StdRng;

use super::*;
use crate::quiz_config::Question;
use crate::game::HOUSE_GRACE_MS;
use crate::protocol::{TvScene, ZuendstoffStage};

const FUSE: f64 = 30_000.0;

fn deck() -> QuestionDeck {
    let mut n = 0;
    Box::new(move |_: &mut StdRng| {
        n += 1;
        Question {
            category: "Test".into(),
            text: format!("Frage {n}"),
            correct: "Ja".into(),
            wrong: ["A".into(), "B".into(), "C".into()],
            image: None,
        }
    })
}

struct Table {
    game: ZuendstoffGame,
    players: Vec<Player>,
    now: ServerTime,
}

impl Table {
    fn new(names: &[&str]) -> Self {
        let players: Vec<Player> = names.iter().zip(1..).map(|(n, id)| Player::for_test(id, n, true)).collect();
        let cfg = ZuendstoffConfig { fuse_min_ms: FUSE, fuse_max_ms: FUSE, seed: Some(1), ..ZuendstoffConfig::default() };
        let game = ZuendstoffGame::new(cfg, deck(), &players, 0.0);
        Self { game, players, now: 0.0 }
    }

    fn holder(&self) -> PlayerId {
        match self.game.stage {
            Stage::Lit { holder, .. } => holder,
            other => panic!("nobody holds the bomb: {other:?}"),
        }
    }

    fn tap(&mut self, player: PlayerId, index: u8) -> Result<(), ActionError> {
        let mut ctx = GameCtx { now: self.now, players: &mut self.players };
        self.game.on_action(&mut ctx, player, &Action::Choose { index, at: self.now })
    }

    fn at(&mut self, now: ServerTime) {
        self.now = now;
        let mut ctx = GameCtx { now, players: &mut self.players };
        self.game.on_tick(&mut ctx);
    }

    fn right(&self) -> u8 {
        self.game.question.correct
    }

    fn view(&self) -> ViewCtx<'_> {
        ViewCtx { now: self.now, players: &self.players, leader: None }
    }
}

#[test]
fn only_the_holder_answers_and_a_right_answer_lets_them_throw_to_whom_they_pick() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let holder = t.holder();
    let other = t.game.targets(&t.players, holder)[0];
    assert_eq!(t.tap(holder, t.right()), Err(ActionError::Ignored), "the keys open after the intro");
    t.at(2600.0);
    assert_eq!(t.tap(other, t.right()), Err(ActionError::Ignored));
    t.tap(holder, t.right()).unwrap();
    assert!(matches!(t.game.stage, Stage::Lit { step: Step::Throw, .. }));
    let targets = t.game.targets(&t.players, holder);
    assert_eq!(targets.len(), 2);
    t.tap(holder, 1).unwrap();
    assert_eq!(t.holder(), targets[1]);
    assert_eq!(t.game.thrown, Some((holder, targets[1])));
    let Stage::Lit { step: Step::Answer { opens_at, wrong }, .. } = t.game.stage else { panic!() };
    assert_eq!((opens_at, wrong), (2600.0 + t.game.cfg.catch_ms, false));
}

#[test]
fn a_wrong_answer_brings_the_next_question_after_a_pause() {
    let mut t = Table::new(&["Ada", "Bo"]);
    let holder = t.holder();
    t.at(2600.0);
    let first = t.game.question.text.clone();
    t.tap(holder, (t.right() + 1) % 4).unwrap();
    assert_ne!(t.game.question.text, first);
    assert_eq!(t.tap(holder, t.right()), Err(ActionError::Ignored), "locked for the pause");
    t.at(2600.0 + t.game.cfg.wrong_wait_ms);
    t.tap(holder, t.right()).unwrap();
    assert!(matches!(t.game.stage, Stage::Lit { step: Step::Throw, .. }));
}

#[test]
fn the_fuse_costs_the_holder_a_life_and_they_start_the_next_round() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let holder = t.holder();
    t.at(FUSE);
    assert_eq!(t.game.stage, Stage::Boom { victim: holder, ends_at: FUSE + t.game.cfg.boom_ms });
    assert_eq!(t.game.lives_of(holder), 2);
    t.at(FUSE + t.game.cfg.boom_ms);
    assert_eq!((t.holder(), t.game.round), (holder, 2));
}

#[test]
fn a_player_out_of_lives_is_no_target_and_the_last_one_left_wins() {
    let mut t = Table::new(&["Ada", "Bo"]);
    let loser = t.holder();
    let winner = t.game.targets(&t.players, loser)[0];
    let mut now = 0.0;
    for _ in 0..3 {
        now += FUSE;
        t.at(now);
        now += t.game.cfg.boom_ms;
        t.at(now);
    }
    assert!(t.game.is_finished());
    assert_eq!(t.game.order, vec![winner, loser]);
    let score = |id| t.players.iter().find(|p| p.id == id).unwrap().score;
    assert!(score(winner) > score(loser));

    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let out = t.holder();
    t.game.lives.insert(out, 1);
    t.at(FUSE);
    t.at(FUSE + t.game.cfg.boom_ms);
    assert_eq!(t.game.out, vec![out]);
    assert_ne!(t.holder(), out, "someone still in starts");
    assert!(!t.game.targets(&t.players, t.holder()).contains(&out));
}

#[test]
fn the_house_throws_for_a_holder_who_is_away() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let holder = t.holder();
    let seat = t.players.iter().position(|p| p.id == holder).unwrap();
    let name = t.players[seat].name.clone();
    t.players[seat] = Player::for_test(holder, &name, false);
    t.players[seat].left_at = Some(0.0);
    t.at(HOUSE_GRACE_MS - 1.0);
    assert_eq!(t.holder(), holder);
    t.at(HOUSE_GRACE_MS);
    assert_ne!(t.holder(), holder);
    assert_eq!(t.game.thrown.map(|(from, _)| from), Some(holder));
}

#[test]
fn the_holder_sees_the_question_and_the_others_wait_for_the_bomb() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let holder = t.holder();
    let other = t.game.targets(&t.players, holder)[0];
    let view = t.view();
    let PhoneScreen::Choice { prompt, options, .. } = t.game.phone_view(&view, holder) else { panic!("no question") };
    assert_eq!((prompt.as_deref(), options.len()), (Some(t.game.question.text.as_str()), 4));
    let PhoneScreen::Wait { title, .. } = t.game.phone_view(&view, other) else { panic!("no wait") };
    assert_eq!(title, format!("{} hat die Bombe", t.players.iter().find(|p| p.id == holder).unwrap().name));
    assert_eq!(t.game.phone_buzz(&view, holder), Some(Buzz::Turn));
    assert_eq!(t.game.phone_buzz(&view, other), None);
    let TvScene::Zuendstoff { scene } = t.game.tv_view(&view) else { panic!("no scene") };
    assert!(matches!(scene.stage, ZuendstoffStage::Answer { holder: h, thrown_by: None, .. } if h == holder));
    assert_eq!(scene.seats.len(), 3);

    t.at(FUSE);
    let view = t.view();
    assert!(matches!(t.game.phone_view(&view, holder), PhoneScreen::Outcome { ref title, .. } if title == "BUMM!"));
    assert_eq!(t.game.phone_buzz(&view, holder), Some(Buzz::Wrong));
}
