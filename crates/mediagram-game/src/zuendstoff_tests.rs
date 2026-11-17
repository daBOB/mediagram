use super::*;
use crate::ask::{Ask, HOUSE_GRACE_MS};
use crate::protocol::{GameLength, GameSpeed, TvScene, ZuendstoffStage};
use crate::zuendstoff_config::Catch;

const FUSE: f64 = 30_000.0;

struct Table {
    game: ZuendstoffGame,
    players: Vec<Player>,
    now: ServerTime,
}

impl Table {
    fn new(names: &[&str]) -> Self {
        let players: Vec<Player> = names.iter().zip(1..).map(|(n, id)| Player::for_test(id, n, true)).collect();
        let cfg = ZuendstoffConfig { fuse_min_ms: FUSE, fuse_max_ms: FUSE, seed: Some(1), ..ZuendstoffConfig::default() };
        let game = ZuendstoffGame::new(cfg, &players, 0.0);
        Self { game, players, now: 0.0 }
    }

    fn holder(&self) -> PlayerId {
        match self.game.stage {
            Stage::Hold { ask, .. } => ask.who(),
            other => panic!("nobody holds the bomb: {other:?}"),
        }
    }

    /// Someone the holder may throw to.
    fn other(&self) -> PlayerId {
        self.game.targets(&self.players, self.holder())[0]
    }

    fn lands_at(&self) -> ServerTime {
        match self.game.stage {
            Stage::Flight { lands_at, .. } => lands_at,
            other => panic!("no bomb in the air: {other:?}"),
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

    /// Once the holder may throw, they throw to `to`; returns when it lands.
    fn throw(&mut self, to: PlayerId) -> ServerTime {
        let Stage::Hold { ready_at, .. } = self.game.stage else { panic!("nobody holds the bomb") };
        self.at(ready_at.max(self.now));
        let holder = self.holder();
        let index = self.game.targets(&self.players, holder).iter().position(|&id| id == to).expect("a target");
        self.tap(holder, index as u8).unwrap();
        self.lands_at()
    }

    fn away(&mut self, id: PlayerId, since: ServerTime) {
        let seat = self.players.iter().position(|p| p.id == id).unwrap();
        let name = self.players[seat].name.clone();
        self.players[seat] = Player::for_test(id, &name, false);
        self.players[seat].left_at = Some(since);
    }

    fn view(&self) -> ViewCtx<'_> {
        ViewCtx { now: self.now, players: &self.players, leader: None }
    }

    fn name(&self, id: PlayerId) -> String {
        self.players.iter().find(|p| p.id == id).unwrap().name.clone()
    }
}

#[test]
fn the_holder_throws_to_anyone_else_still_in_once_the_intro_is_over() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let holder = t.holder();
    let targets = t.game.targets(&t.players, holder);
    let others: Vec<PlayerId> = t.players.iter().map(|p| p.id).filter(|&id| id != holder).collect();
    assert_eq!(targets, others, "everyone else, in seat order");
    assert_eq!(t.tap(holder, 0), Err(ActionError::Ignored), "the names go live after the intro");
    let cfg = t.game.cfg.clone();
    t.at(cfg.intro_ms);
    assert_eq!(t.tap(targets[0], 0), Err(ActionError::Ignored), "only the holder throws");
    assert!(matches!(t.tap(holder, 2), Err(ActionError::NotAllowed(_))));
    t.tap(holder, 1).unwrap();
    let thrown_at = cfg.intro_ms;
    let lands_at = thrown_at + cfg.throw_lead_ms + cfg.flight_ms;
    assert_eq!(t.game.stage, Stage::Flight { from: holder, to: targets[1], thrown_at, lands_at, catch: Catch::Pending });
}

#[test]
fn a_tap_in_the_window_catches_and_the_catcher_may_throw_soon_after_even_straight_back() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let cfg = t.game.cfg.clone();
    let from = t.holder();
    let to = t.other();
    let lands_at = t.throw(to);
    t.at(lands_at - cfg.catch_before_ms - cfg.early_tolerance_ms);
    t.tap(to, 0).unwrap();
    t.at(lands_at);
    let ready_at = lands_at + cfg.catch_guard_ms;
    assert_eq!(t.game.stage, Stage::Hold { ask: Ask::open(to, lands_at, None), ready_at, dropped: false });

    let back = t.throw(from);
    assert_eq!(back, ready_at + cfg.throw_lead_ms + cfg.flight_ms, "thrown once the catch guard is over");
    t.now = back + cfg.catch_after_ms;
    t.tap(from, 0).unwrap();
    t.at(t.now);
    assert_eq!(t.holder(), from, "the window's last moment still catches");
    assert!(matches!(t.game.stage, Stage::Hold { dropped: false, .. }));
}

#[test]
fn an_early_tap_fumbles_and_the_dropped_bomb_waits_for_the_pick_up() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let cfg = t.game.cfg.clone();
    let to = t.other();
    let lands_at = t.throw(to);
    t.at(lands_at - cfg.catch_before_ms - cfg.early_tolerance_ms - 1.0);
    t.tap(to, 0).unwrap();
    assert!(matches!(t.game.stage, Stage::Flight { catch: Catch::Fumbled, .. }));
    t.at(lands_at);
    let ready_at = lands_at + cfg.pickup_ms;
    assert_eq!(t.game.stage, Stage::Hold { ask: Ask::open(to, lands_at, None), ready_at, dropped: true });
    assert_eq!(t.tap(to, 0), Err(ActionError::Ignored), "still picking it up");
    t.at(ready_at - 1.0);
    assert_eq!(t.tap(to, 0), Err(ActionError::Ignored));
    t.at(ready_at);
    assert!(matches!(t.game.stage, Stage::Hold { dropped: false, .. }));
    t.tap(to, 0).unwrap();
    assert!(matches!(t.game.stage, Stage::Flight { from, .. } if from == to));
}

#[test]
fn no_tap_drops_the_bomb_when_the_window_closes() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let cfg = t.game.cfg.clone();
    let to = t.other();
    let lands_at = t.throw(to);
    t.at(lands_at);
    assert!(matches!(t.game.stage, Stage::Flight { catch: Catch::Pending, .. }), "the window is still open");
    t.at(lands_at + cfg.catch_after_ms);
    let ready_at = lands_at + cfg.pickup_ms;
    assert_eq!(t.game.stage, Stage::Hold { ask: Ask::open(to, lands_at, None), ready_at, dropped: true });
}

#[test]
fn only_the_target_catches_and_only_once() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let from = t.holder();
    let to = t.other();
    let third = t.game.targets(&t.players, from)[1];
    let lands_at = t.throw(to);
    t.at(lands_at);
    assert_eq!(t.tap(from, 0), Err(ActionError::Ignored), "the thrower");
    assert_eq!(t.tap(third, 0), Err(ActionError::Ignored), "a bystander");
    t.tap(to, 0).unwrap();
    assert_eq!(t.tap(to, 0), Err(ActionError::Ignored), "a second tap");
    assert!(matches!(t.game.stage, Stage::Flight { catch: Catch::Caught, .. }));

    // Mashing: a fumble stays a fumble when the window opens.
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let to = t.other();
    let lands_at = t.throw(to);
    t.tap(to, 0).unwrap();
    t.at(lands_at - 100.0);
    assert_eq!(t.tap(to, 0), Err(ActionError::Ignored));
    assert!(matches!(t.game.stage, Stage::Flight { catch: Catch::Fumbled, .. }));
    t.at(lands_at);
    assert!(matches!(t.game.stage, Stage::Hold { dropped: true, .. }));
}

#[test]
fn a_late_catch_after_the_drop_is_ignored() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let cfg = t.game.cfg.clone();
    let to = t.other();
    let lands_at = t.throw(to);
    // Arriving after the window closed but before the tick that drops it.
    t.now = lands_at + cfg.catch_after_ms + 1.0;
    assert_eq!(t.tap(to, 0), Err(ActionError::Ignored));
    t.at(t.now);
    let dropped = t.game.stage;
    assert!(matches!(dropped, Stage::Hold { dropped: true, .. }));
    assert_eq!(t.tap(to, 0), Err(ActionError::Ignored), "after the drop");
    assert_eq!(t.game.stage, dropped);
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
fn a_second_fangen_tap_right_after_the_catch_throws_nothing() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let cfg = t.game.cfg.clone();
    let to = t.other();
    let lands_at = t.throw(to);
    t.now = lands_at + 250.0;
    t.tap(to, 0).unwrap();
    t.at(lands_at + 300.0);
    // the finger lands on the same spot again, now the first name on the catcher's list
    t.now = lands_at + 300.0 + cfg.catch_guard_ms - cfg.early_tolerance_ms - 1.0;
    assert!(matches!(t.tap(to, 0), Err(ActionError::Ignored)));
    assert_eq!(t.holder(), to);
}

#[test]
fn a_throw_tapped_after_the_fuse_ran_out_is_ignored_and_the_holder_is_hit() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let holder = t.holder();
    t.at(FUSE - 30.0);
    t.now = FUSE + 10.0;
    assert!(matches!(t.tap(holder, 0), Err(ActionError::Ignored)));
    t.at(FUSE + 20.0);
    assert!(matches!(t.game.stage, Stage::Boom { victim, .. } if victim == holder));
}

#[test]
fn the_fuse_going_off_while_the_bomb_is_still_in_the_throwing_hand_hits_the_thrower() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let from = t.holder();
    let to = t.other();
    t.at(FUSE - t.game.cfg.throw_lead_ms + 100.0);
    t.throw(to);
    t.at(FUSE);
    assert!(matches!(t.game.stage, Stage::Boom { victim, .. } if victim == from));
    assert_eq!((t.game.lives_of(from), t.game.lives_of(to)), (2, 3));
}

#[test]
fn the_fuse_going_off_mid_flight_hits_the_target() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let from = t.holder();
    let to = t.other();
    t.at(FUSE - 1000.0);
    t.throw(to);
    t.at(FUSE);
    assert!(matches!(t.game.stage, Stage::Boom { victim, .. } if victim == to));
    assert_eq!((t.game.lives_of(from), t.game.lives_of(to)), (3, 2));
}

#[test]
fn fuse_at_landing_hits_the_target() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let cfg = t.game.cfg.clone();
    let to = t.other();
    t.at(FUSE - cfg.throw_lead_ms - cfg.flight_ms);
    assert_eq!(t.throw(to), FUSE);
    t.at(FUSE - 100.0);
    t.tap(to, 0).unwrap();
    t.at(FUSE);
    assert_eq!(t.game.stage, Stage::Boom { victim: to, ends_at: FUSE + cfg.boom_ms }, "caught, and hit all the same");
    assert_eq!(t.game.lives_of(to), 2);
}

#[test]
fn a_player_out_of_lives_is_no_target_and_the_last_one_left_wins() {
    let mut t = Table::new(&["Ada", "Bo"]);
    let loser = t.holder();
    let winner = t.other();
    let mut now = 0.0;
    for _ in 0..3 {
        now += FUSE;
        t.at(now);
        now += t.game.cfg.boom_ms;
        t.at(now);
    }
    assert!(t.game.ranking(&t.players).is_some());
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
fn two_players_last_life_ends_the_game() {
    let mut t = Table::new(&["Ada", "Bo"]);
    let cfg = t.game.cfg.clone();
    let first = t.holder();
    let second = t.other();
    t.game.lives.insert(first, 1);
    t.game.lives.insert(second, 1);
    assert_eq!(t.game.targets(&t.players, first), vec![second]);
    let lands_at = t.throw(second);
    t.at(lands_at);
    t.at(lands_at + cfg.catch_after_ms);
    assert_eq!(t.game.targets(&t.players, t.holder()), vec![first], "a throw always has a target");
    t.at(FUSE);
    assert!(matches!(t.game.stage, Stage::Boom { victim, .. } if victim == second));
    t.at(FUSE + cfg.boom_ms);
    assert!(t.game.ranking(&t.players).is_some());
    assert_eq!(t.game.order, vec![first, second]);
}

#[test]
fn the_house_throws_for_a_holder_who_is_away() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let holder = t.holder();
    t.away(holder, 5000.0);
    t.at(5000.0 + HOUSE_GRACE_MS - 1.0);
    assert_eq!(t.holder(), holder);
    t.at(5000.0 + HOUSE_GRACE_MS);
    assert!(matches!(t.game.stage, Stage::Flight { from, to, .. } if from == holder && to != holder));

    // Away from the start: the house still waits for the intro.
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let holder = t.holder();
    t.away(holder, 0.0);
    t.at(t.game.cfg.intro_ms - 1.0);
    assert_eq!(t.holder(), holder);
    t.at(t.game.cfg.intro_ms);
    assert!(matches!(t.game.stage, Stage::Flight { from, .. } if from == holder));
}

#[test]
fn an_away_target_never_catches_and_the_house_throws_once_it_is_picked_up() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let cfg = t.game.cfg.clone();
    let to = t.other();
    let lands_at = t.throw(to);
    t.away(to, lands_at - 1000.0);
    t.at(lands_at + cfg.catch_after_ms);
    assert!(matches!(t.game.stage, Stage::Hold { dropped: true, .. }));
    t.at(lands_at + cfg.pickup_ms - 1.0);
    assert_eq!(t.holder(), to);
    t.at(lands_at + cfg.pickup_ms);
    assert!(matches!(t.game.stage, Stage::Flight { from, .. } if from == to));
}

#[test]
fn the_thrower_leaving_mid_flight_changes_nothing() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let from = t.holder();
    let to = t.other();
    let lands_at = t.throw(to);
    t.away(from, lands_at - 1500.0);
    t.at(lands_at - 1000.0);
    t.at(lands_at - 100.0);
    t.tap(to, 0).unwrap();
    t.at(lands_at);
    let ready_at = lands_at + t.game.cfg.catch_guard_ms;
    assert_eq!(t.game.stage, Stage::Hold { ask: Ask::open(to, lands_at, None), ready_at, dropped: false });
    assert_eq!(t.game.lives_of(from), 3);
}

#[test]
fn tempo_stretches_only_the_fuse_and_length_the_lives() {
    let normal = ZuendstoffConfig::default();
    let relaxed = ZuendstoffConfig::default().at(GameSpeed::Relaxed);
    assert_eq!((relaxed.fuse_min_ms, relaxed.fuse_max_ms), (36_000.0, 80_000.0));
    assert_eq!((relaxed.flight_ms, relaxed.pickup_ms, relaxed.catch_before_ms), (normal.flight_ms, normal.pickup_ms, normal.catch_before_ms));
    let lives = |length| ZuendstoffConfig::default().sized(length).lives;
    assert_eq!((lives(GameLength::Short), lives(GameLength::Normal), lives(GameLength::Long)), (2, 3, 5));
}

#[test]
fn the_phones_show_names_to_the_holder_fangen_to_the_target_and_waits_to_the_rest() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let holder = t.holder();
    let to = t.other();
    let third = t.game.targets(&t.players, holder)[1];
    let view = t.view();
    let PhoneScreen::Choice { options, opens_at, .. } = t.game.phone_view(&view, holder) else { panic!("no names") };
    let names: Vec<String> = options.into_iter().map(|o| o.label).collect();
    assert_eq!((names, opens_at), (vec![t.name(to), t.name(third)], t.game.cfg.intro_ms));
    let PhoneScreen::Wait { title, .. } = t.game.phone_view(&view, to) else { panic!("no wait") };
    assert_eq!(title, format!("{} hat die Bombe", t.name(holder)));
    assert_eq!((t.game.phone_buzz(&view, holder), t.game.phone_buzz(&view, to)), (Some(Buzz::Turn), None));
    let TvScene::Zuendstoff { scene } = t.game.tv_view(&view) else { panic!("no scene") };
    assert_eq!(scene.stage, ZuendstoffStage::Hold { holder, ready_at: t.game.cfg.intro_ms, dropped: false });
    assert_eq!(scene.seats.len(), 3);

    let lands_at = t.throw(to);
    let thrown_at = t.now;
    let view = t.view();
    let PhoneScreen::Choice { options, opens_at, closes_at, .. } = t.game.phone_view(&view, to) else { panic!("no catch") };
    assert_eq!(options.len(), 1);
    assert_eq!((options[0].label.as_str(), opens_at, closes_at), ("Fangen!", thrown_at, None));
    assert_eq!(t.game.phone_buzz(&view, to), Some(Buzz::Turn));
    let PhoneScreen::Wait { title, .. } = t.game.phone_view(&view, third) else { panic!("no wait") };
    assert_eq!(title, format!("{} wirft zu {}", t.name(holder), t.name(to)));
    let TvScene::Zuendstoff { scene } = t.game.tv_view(&view) else { panic!("no scene") };
    assert_eq!(scene.stage, ZuendstoffStage::Flight { from: holder, to, thrown_at, lands_at, caught: None });

    t.at(lands_at + t.game.cfg.catch_after_ms);
    let view = t.view();
    let PhoneScreen::Wait { title, .. } = t.game.phone_view(&view, to) else { panic!("no pick-up") };
    assert_eq!(title, "Heb die Bombe auf …");
    let TvScene::Zuendstoff { scene } = t.game.tv_view(&view) else { panic!("no scene") };
    let ready_at = lands_at + t.game.cfg.pickup_ms;
    assert_eq!(scene.stage, ZuendstoffStage::Hold { holder: to, ready_at, dropped: true });

    t.at(FUSE);
    let view = t.view();
    assert!(matches!(t.game.phone_view(&view, to), PhoneScreen::Outcome { ref title, .. } if title == "BUMM!"));
    assert_eq!(t.game.phone_buzz(&view, to), Some(Buzz::Wrong));
}

#[test]
fn the_tv_tells_a_catch_from_a_fumble_before_the_landing() {
    let mut t = Table::new(&["Ada", "Bo"]);
    let to = t.other();
    let lands_at = t.throw(to);
    t.at(lands_at - 200.0);
    t.tap(to, 0).unwrap();
    let TvScene::Zuendstoff { scene } = t.game.tv_view(&t.view()) else { panic!("no scene") };
    assert!(matches!(scene.stage, ZuendstoffStage::Flight { caught: Some(true), .. }));
}
