use super::GameLength::{self, Long, Normal, Short};
use crate::catalogue::CATALOGUE;
use crate::dreifelder_config::DreifelderConfig;
use crate::grand_allee::live::GrandAlleeConfig;
use crate::grenzfall::{grenzfall_config, tiers};
use crate::spruchreif::spruchreif_config;
use crate::kopfnuss_config::KopfnussConfig;
use crate::poster_config::PosterConfig;
use crate::preisfrage_config::PreisfrageConfig;
use crate::quiz_config::QuizConfig;
use crate::zuendstoff_config::ZuendstoffConfig;
use crate::letztes_wort_config::LetztesWortConfig;

#[test]
fn short_halves_and_long_adds_half_rounding_up() {
    assert_eq!([1, 3, 6, 7, 8, 10, 12].map(|n| Short.scale(n)), [1, 2, 3, 4, 4, 5, 6]);
    assert_eq!([1, 3, 6, 7, 8, 10, 12].map(|n| Long.scale(n)), [2, 5, 9, 11, 12, 15, 18]);
    assert_eq!(GameLength::default(), Normal);
}

/// Each sized game's count at `length`, in a fixed order.
fn counts(length: GameLength) -> Vec<usize> {
    let dreifelder = DreifelderConfig::default().sized(length);
    let allee = GrandAlleeConfig::default().sized(length).rules;
    let kopfnuss = KopfnussConfig::default().sized(length);
    vec![
        QuizConfig::default().sized(length).rounds,
        PosterConfig::default().sized(length).rounds,
        grenzfall_config().sized(length).rounds,
        kopfnuss.rounds,
        kopfnuss.quiz.rounds,
        dreifelder.quiz.rounds,
        dreifelder.films,
        PreisfrageConfig::default().sized(length).rounds,
        ZuendstoffConfig::default().sized(length).lives.into(),
        allee.round_turns as usize,
        allee.min_rounds as usize,
        allee.max_rounds as usize,
        spruchreif_config().sized(length).rounds,
        LetztesWortConfig::default().sized(length).laps,
    ]
}

#[test]
fn every_sized_game_follows_the_rule_and_kopfnuss_keeps_three_questions_a_round() {
    const PER_ROUND: usize = 4; // Kopfnuss's questions per round, which no length changes
    let normal = counts(Normal);
    for length in [Short, Long] {
        let want: Vec<usize> = normal.iter().enumerate().map(|(i, &n)| if i == PER_ROUND { n } else { length.scale(n) }).collect();
        assert_eq!(counts(length), want, "{length:?}");
    }
    assert_eq!(normal[..4], [8, 10, 10, 7], "the normal game is unchanged");
}

#[test]
fn grenzfall_keeps_its_easy_middle_hard_shape_at_every_length() {
    assert_eq!(tiers(10), [1, 1, 1, 2, 2, 2, 2, 3, 3, 3]);
    assert_eq!(tiers(5), [1, 1, 2, 3, 3]);
    assert_eq!(tiers(15), [1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3]);
}

#[test]
fn the_lobby_offers_a_length_for_the_games_with_a_count() {
    use crate::protocol::GameKind::*;
    let unsized_games: Vec<_> = CATALOGUE.iter().filter(|g| !g.sized).map(|g| g.kind).collect();
    assert_eq!(unsized_games, [Race, Fluechtig, MauMau, Schwimmen, Durak, TienLen], "played to their end");
}
