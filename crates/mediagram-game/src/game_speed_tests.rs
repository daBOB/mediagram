use super::GameSpeed::{self, Normal, Relaxed, Speedrun};
use crate::catalogue::CATALOGUE;
use crate::dreifelder_config::DreifelderConfig;
use crate::grand_allee::live::GrandAlleeConfig;
use crate::kopfnuss_config::KopfnussConfig;
use crate::kritzelei_config::KritzeleiConfig;
use crate::poster_config::PosterConfig;
use crate::preisfrage_config::PreisfrageConfig;
use crate::protocol::GameKind;
use crate::quiz_config::QuizConfig;
use crate::spruchreif::spruchreif_config;
use crate::zuendstoff_config::ZuendstoffConfig;
use crate::letztes_wort_config::LetztesWortConfig;

/// Each timed game's thinking clocks at `speed`, in a fixed order.
fn clocks(speed: GameSpeed) -> Vec<f64> {
    let quiz = QuizConfig::default().at(speed);
    let poster = PosterConfig::default().at(speed);
    let kopfnuss = KopfnussConfig::default().at(speed);
    let zuendstoff = ZuendstoffConfig::default().at(speed);
    let allee = GrandAlleeConfig::default().at(speed);
    vec![
        quiz.answer_ms,
        poster.step_ms,
        kopfnuss.pick_ms,
        kopfnuss.climb_ms,
        kopfnuss.quiz.answer_ms,
        DreifelderConfig::default().at(speed).quiz.answer_ms,
        KritzeleiConfig::default().at(speed).draw_ms,
        zuendstoff.fuse_min_ms,
        zuendstoff.fuse_max_ms,
        PreisfrageConfig::default().at(speed).guess_ms,
        allee.ask_ms,
        allee.auction_ms,
        spruchreif_config().at(speed).answer_ms,
        LetztesWortConfig::default().at(speed).time_min_ms,
        LetztesWortConfig::default().at(speed).time_max_ms,
    ]
}

/// What a tempo must leave alone: shows, animations and the fair-tap margins.
fn untouched(speed: GameSpeed) -> Vec<f64> {
    let quiz = QuizConfig::default().at(speed);
    let poster = PosterConfig::default().at(speed);
    let allee = GrandAlleeConfig::default().at(speed);
    vec![quiz.intro_ms, quiz.reveal_ms, quiz.max_claim_lag_ms, quiz.early_tolerance_ms, poster.grace_ms, poster.reveal_ms, allee.hop_ms, allee.early_ms]
}

#[test]
fn relaxed_doubles_every_clock_and_speedrun_halves_it() {
    let normal = clocks(Normal);
    assert_eq!(normal, clocks(GameSpeed::default()), "normal is the default");
    assert_eq!(clocks(Relaxed), normal.iter().map(|ms| ms * 2.0).collect::<Vec<_>>());
    assert_eq!(clocks(Speedrun), normal.iter().map(|ms| ms / 2.0).collect::<Vec<_>>());
    for speed in [Relaxed, Speedrun] {
        assert_eq!(untouched(speed), untouched(Normal), "{speed:?}");
    }
}

#[test]
fn the_lobby_offers_a_tempo_for_the_games_with_a_clock() {
    let untimed: Vec<GameKind> = CATALOGUE.iter().filter(|g| !g.timed).map(|g| g.kind).collect();
    use GameKind::*;
    assert_eq!(untimed, [Race, Fluechtig, MauMau, Schwimmen, Durak], "turn-based: nobody races a clock");
}
