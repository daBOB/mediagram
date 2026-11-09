use std::collections::HashSet;
use std::sync::{Arc, Mutex};

use rand::SeedableRng;

use super::*;
use crate::dreifelder::tests::players;
use crate::game::{GameCtx, ViewCtx};
use crate::general::general_pack;
use crate::protocol::{GameKind, LobbySettings, QuizMode, TvScene};
use crate::titles::TitlePool;

#[test]
fn twelve_questions_at_most_three_films_and_none_repeated_tonight() {
    let used = Arc::new(Mutex::new(HashSet::new()));
    let film = |i: usize| Question { category: "Film".into(), text: format!("Film {i}?"), correct: "a".into(), wrong: ["b".into(), "c".into(), "d".into()], image: None };
    let cfg = DreifelderConfig::default();
    let mut rng = StdRng::seed_from_u64(1);
    let mut supply = QuestionSupply::new(Arc::new(general_pack()), (0..5).map(film).collect(), used.clone(), cfg.quiz.rounds);
    let first = dreifelder_questions(&mut supply, &cfg, &mut rng);
    assert_eq!(first.len(), 12);
    assert_eq!(first.iter().filter(|q| q.category == FILMS).count(), 3);
    let texts: HashSet<String> = first.iter().map(|q| q.text.clone()).collect();
    assert_eq!(texts.len(), 12);
    let mut supply = QuestionSupply::new(Arc::new(general_pack()), vec![], used, cfg.quiz.rounds);
    let second = dreifelder_questions(&mut supply, &cfg, &mut rng);
    assert_eq!(second.len(), 12, "no films: all general");
    assert!(second.iter().all(|q| !texts.contains(&q.text)), "a later game tonight repeats nothing");
}

#[test]
fn the_factory_builds_dreifelder() {
    let mut factory = crate::factory::game_factory(Arc::new(TitlePool::new(vec![], false)), Arc::new(general_pack()), None);
    let mut ps = players(2);
    let settings = LobbySettings { game: GameKind::Dreifelder, quiz_mode: QuizMode::Mixed, kids_only: false, kids_locked: false };
    let game = factory(&mut GameCtx { now: 0.0, players: &mut ps }, &settings);
    let TvScene::Dreifelder { scene } = game.tv_scene(&ViewCtx { now: 0.0, players: &ps, leader: Some(0) }) else {
        panic!("not Dreifelder")
    };
    assert_eq!((scene.round, scene.rounds), (1, 12));
}
