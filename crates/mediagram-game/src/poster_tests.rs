use super::*;
use crate::poster_config::{PosterConfig, points_at_step, poster_ready, step_at};
use crate::protocol::{PhoneScreen, Tone};
use crate::room_seats::Player;
use crate::titles::tests::title;
use crate::titles::{GameTitle, TitleKind, TitlePool};

const ANA: PlayerId = 0;
const BEN: PlayerId = 1;

fn with_poster(key: &str, kind: TitleKind) -> GameTitle {
    GameTitle { kind, poster: Some(format!("/posters/{key}.jpg").into()), ..title(key, true) }
}

fn pool_of(films: usize, series: usize) -> TitlePool {
    let films = (0..films).map(|n| with_poster(&format!("film{n}"), TitleKind::Film));
    let series = (0..series).map(|n| with_poster(&format!("show{n}"), TitleKind::Series));
    TitlePool::new(films.chain(series).collect(), false)
}

fn cfg(rounds: usize) -> PosterConfig {
    PosterConfig { rounds, seed: Some(3), ..PosterConfig::default() }
}

fn game_with(pool: &TitlePool, rounds: usize, at: f64) -> (PosterGame, Vec<Player>) {
    let mut used = HashSet::new();
    let g = PosterGame::new(cfg(rounds), pool, &pool.eligible(false), &mut used, at);
    (g, vec![Player::for_test(ANA, "Ana", true), Player::for_test(BEN, "Ben", true)])
}

fn game_started_at(at: f64) -> (PosterGame, Vec<Player>) {
    game_with(&pool_of(6, 0), 2, at)
}

fn right(g: &PosterGame) -> u8 {
    g.rounds[g.index].correct
}

fn wrong(g: &PosterGame) -> u8 {
    (right(g) + 1) % 4
}

fn tap(g: &mut PosterGame, players: &mut [Player], now: f64, player: PlayerId, index: u8, at: f64) {
    let _ = g.on_action(&mut GameCtx { now, players }, player, &Action::Choose { index, at });
}

fn tick(g: &mut PosterGame, players: &mut [Player], now: f64) {
    g.on_tick(&mut GameCtx { now, players });
}

fn is_reveal(g: &PosterGame) -> bool {
    matches!(g.stage, Stage::Reveal { .. })
}

#[test]
fn points_fall_from_1000_to_500_over_8_steps() {
    let c = PosterConfig::default();
    assert_eq!(points_at_step(&c, 0), 1000);
    assert_eq!(points_at_step(&c, 3), 786);
    assert_eq!(points_at_step(&c, 7), 500);
}

#[test]
fn sharpens_every_2_seconds() {
    let c = PosterConfig::default();
    assert_eq!([0.0, 1999.0, 2000.0, 14_000.0, 99_000.0].map(|t| step_at(&c, 0.0, t)), [0, 0, 1, 7, 7]);
}

#[test]
fn earliest_claimed_right_tap_wins() {
    // Ana claims 5000, arrives 5300. Ben claims 4900, arrives 5350 → accepted 4950.
    let (mut g, mut players) = game_started_at(0.0);
    let r = right(&g);
    tap(&mut g, &mut players, 5300.0, ANA, r, 5000.0);
    tick(&mut g, &mut players, 5320.0);
    assert!(g.winner().is_none(), "Ana's claim window is still open");
    tap(&mut g, &mut players, 5350.0, BEN, r, 4900.0);
    tick(&mut g, &mut players, 5360.0);
    assert_eq!(g.winner(), Some((BEN, 857)));
    assert_eq!(players[usize::from(BEN)].score, 857);
    assert_eq!(players[usize::from(ANA)].score, 0);
}

#[test]
fn lone_right_tap_wins_once_its_window_has_passed() {
    let (mut g, mut players) = game_started_at(0.0);
    let r = right(&g);
    tap(&mut g, &mut players, 100.0, ANA, r, 100.0);
    tick(&mut g, &mut players, 499.0);
    assert!(g.winner().is_none());
    tick(&mut g, &mut players, 500.0);
    assert_eq!(g.winner(), Some((ANA, 1000)));
    assert!(is_reveal(&g));
}

#[test]
fn wrong_tap_locks_the_player_out() {
    let (mut g, mut players) = game_started_at(0.0);
    let (r, w) = (right(&g), wrong(&g));
    tap(&mut g, &mut players, 1000.0, ANA, w, 1000.0);
    tap(&mut g, &mut players, 1500.0, ANA, r, 1500.0);
    tick(&mut g, &mut players, 3000.0);
    assert!(g.winner().is_none(), "a locked player's later tap is ignored");
    let view = ViewCtx { now: 3000.0, players: &players, leader: Some(ANA) };
    assert!(matches!(
        g.phone_screen(&view, ANA),
        PhoneScreen::Outcome { ref title, tone: Tone::Bad, points: 0, .. } if title == "Falsch – diesmal bist du raus."
    ));
    assert!(matches!(g.phone_screen(&view, BEN), PhoneScreen::Choice { .. }));
}

#[test]
fn everyone_out_reveals_early() {
    let (mut g, mut players) = game_started_at(0.0);
    let w = wrong(&g);
    tap(&mut g, &mut players, 1000.0, ANA, w, 1000.0);
    tick(&mut g, &mut players, 1010.0);
    assert!(!is_reveal(&g));
    tap(&mut g, &mut players, 1100.0, BEN, w, 1100.0);
    tick(&mut g, &mut players, 1110.0);
    assert!(is_reveal(&g));
    assert!(g.winner().is_none());
}

#[test]
fn absent_player_does_not_block_everyone_out() {
    let (mut g, mut players) = game_started_at(0.0);
    players[usize::from(BEN)] = Player::for_test(BEN, "Ben", false);
    let w = wrong(&g);
    tap(&mut g, &mut players, 1000.0, ANA, w, 1000.0);
    tick(&mut g, &mut players, 1010.0);
    assert!(is_reveal(&g));
}

#[test]
fn restored_poster_closes_after_three_seconds() {
    // The last step shows at 14 000; 3 s later nobody is left to guess.
    let (mut g, mut players) = game_started_at(0.0);
    tick(&mut g, &mut players, 16_999.0);
    assert!(!is_reveal(&g));
    tick(&mut g, &mut players, 17_000.0);
    assert!(is_reveal(&g));
    assert!(g.winner().is_none());
}

#[test]
fn rounds_use_only_titles_with_posters_and_same_kind_options() {
    let mut titles: Vec<GameTitle> = (0..5).map(|n| with_poster(&format!("film{n}"), TitleKind::Film)).collect();
    titles.extend((0..4).map(|n| with_poster(&format!("show{n}"), TitleKind::Series)));
    titles.push(GameTitle { poster: None, ..title("bare", true) });
    let pool = TitlePool::new(titles, false);
    let (g, _) = game_with(&pool, 9, 0.0);
    assert_eq!(g.rounds.len(), 9);
    for r in &g.rounds {
        let t = pool.get(r.title);
        assert!(t.poster.is_some());
        assert_eq!(r.options[usize::from(r.correct)], t.title);
        let mut names = r.options.clone();
        names.sort();
        names.dedup();
        assert_eq!(names.len(), 4, "options are distinct");
        for o in &r.options {
            let other = (0..pool.eligible(false).len()).map(|i| pool.get(i)).find(|x| x.title == *o).unwrap();
            assert!(other.poster.is_some() && other.kind == t.kind);
        }
    }
}

#[test]
fn rounds_never_repeat_a_title_and_history_resets_only_poster_keys() {
    let pool = pool_of(6, 0);
    let mut used: HashSet<String> = ["year:film0".to_string(), "Frage?".to_string()].into();
    let first = PosterGame::new(cfg(4), &pool, &pool.eligible(false), &mut used, 0.0);
    let second = PosterGame::new(cfg(4), &pool, &pool.eligible(false), &mut used, 0.0);
    // Six films, four per night: the second night finds only two unused and starts over.
    assert_eq!(first.rounds.len() + second.rounds.len(), 8);
    assert!(used.contains("year:film0") && used.contains("Frage?"));
    let mut keys: Vec<_> = first.rounds.iter().map(|r| r.title).collect();
    keys.sort();
    keys.dedup();
    assert_eq!(keys.len(), 4);
}

#[test]
fn phones_never_see_the_image() {
    let (mut g, mut players) = game_started_at(0.0);
    let mut seen = String::new();
    let mut now = 0.0;
    while !g.is_finished() && now < 120_000.0 {
        if now == 1000.0 {
            let r = right(&g);
            tap(&mut g, &mut players, now, ANA, r, now);
        }
        tick(&mut g, &mut players, now);
        let view = ViewCtx { now, players: &players, leader: Some(ANA) };
        for p in [ANA, BEN] {
            seen += &serde_json::to_string(&g.phone_screen(&view, p)).unwrap();
        }
        now += 50.0;
    }
    assert!(g.is_finished());
    assert!(!seen.is_empty() && !seen.contains("/img/") && !seen.contains(".jpg"));
    let TvScene::Poster { .. } = g.tv_scene(&ViewCtx { now, players: &players, leader: None }) else {
        panic!("poster scene")
    };
}

#[test]
fn readiness_reflects_the_pool() {
    let few = pool_of(3, 0);
    assert!(!poster_ready(&few, &few.eligible(false)));
    let enough = pool_of(4, 0);
    assert!(poster_ready(&enough, &enough.eligible(false)));
    let split = pool_of(3, 3);
    assert!(!poster_ready(&split, &split.eligible(false)), "kinds are not mixed");
}
