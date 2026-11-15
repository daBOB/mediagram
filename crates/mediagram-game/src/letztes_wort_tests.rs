use super::*;
use crate::game::HOUSE_GRACE_MS;

/// The hidden time of every test round: buzzers open at INTRO, time is up at INTRO + TIME.
const INTRO: f64 = 3000.0;
const TIME: f64 = 20_000.0;
pub(crate) const FALSCH: u8 = 255; // the host's Falsch key; `judge` maps it to the prompt's index

pub(crate) struct Table {
    pub game: LetztesWortGame,
    pub players: Vec<Player>,
    pub now: ServerTime,
}

impl Table {
    pub fn new(names: &[&str]) -> Self {
        let players: Vec<Player> = names.iter().zip(1..).map(|(n, id)| Player::for_test(id, n, true)).collect();
        let cfg = LetztesWortConfig { time_min_ms: TIME, time_max_ms: TIME, seed: Some(1), ..LetztesWortConfig::default() };
        let prompts = PROMPTS.iter().take(12).collect();
        let game = LetztesWortGame::new(cfg, prompts, &players, 0.0);
        Self { game, players, now: 0.0 }
    }

    /// The players who play this round (everyone but the host), in seat order.
    pub fn others(&self) -> Vec<PlayerId> {
        self.players.iter().map(|p| p.id).filter(|&p| p != self.game.host).collect()
    }

    pub fn tap(&mut self, player: PlayerId, index: u8) -> Result<(), ActionError> {
        let mut ctx = GameCtx { now: self.now, players: &mut self.players };
        self.game.on_action(&mut ctx, player, &Action::Choose { index, at: self.now })
    }

    pub fn buzz(&mut self, player: PlayerId) -> Result<(), ActionError> {
        self.tap(player, 0)
    }

    /// The host judges: a listed answer, Falsch or Auch richtig.
    pub fn judge(&mut self, verdict: u8) -> Result<(), ActionError> {
        let host = self.game.host;
        let listed = self.game.prompt.answers.len() as u8;
        self.tap(host, if verdict == FALSCH { listed } else { verdict })
    }

    pub fn also_right(&mut self) -> Result<(), ActionError> {
        let listed = self.game.prompt.answers.len() as u8;
        let host = self.game.host;
        self.tap(host, listed + 1)
    }

    pub fn at(&mut self, now: ServerTime) {
        self.now = now;
        let mut ctx = GameCtx { now, players: &mut self.players };
        self.game.on_tick(&mut ctx);
    }

    /// Opens the buzzers (after the intro or the verdict gap).
    pub fn open(&mut self) {
        let Stage::Open { opens_at } = self.game.stage else { panic!("not open: {:?}", self.game.stage) };
        self.at(self.now.max(opens_at));
    }

    /// `player` buzzes and the host takes listed answer `answer` (or FALSCH).
    pub fn answer(&mut self, player: PlayerId, answer: u8) {
        self.open();
        self.buzz(player).unwrap();
        self.think();
        self.judge(answer).unwrap();
    }

    /// The host listens to the spoken answer before judging.
    pub fn think(&mut self) {
        self.at(self.now + 1000.0);
    }

    fn score(&self, id: PlayerId) -> i32 {
        self.players.iter().find(|p| p.id == id).unwrap().score
    }
}

#[test]
fn the_first_correct_answer_takes_the_stick() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, ..] = t.others()[..] else { panic!() };
    assert_eq!(t.buzz(a), Err(ActionError::Ignored), "the buzzers open after the intro");
    t.answer(a, 0);
    assert_eq!(t.game.holder, Some(a));
    assert_eq!(t.game.given, vec![(Some(0), a)]);
}

#[test]
fn another_correct_answer_takes_it_over() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, b, ..] = t.others()[..] else { panic!() };
    t.answer(a, 0);
    t.answer(b, 1);
    assert_eq!(t.game.holder, Some(b));
}

#[test]
fn the_holder_and_the_host_cannot_buzz() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, b, ..] = t.others()[..] else { panic!() };
    t.answer(a, 0);
    t.open();
    assert_eq!(t.buzz(a), Err(ActionError::Ignored), "the holder");
    assert!(matches!(t.game.stage, Stage::Open { .. }));
    // The host's choose is a verdict, and there is nobody to judge.
    assert_eq!(t.judge(1), Err(ActionError::Ignored));
    t.buzz(b).unwrap();
}

#[test]
fn a_wrong_answer_puts_the_player_out_for_the_round_only() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, b, ..] = t.others()[..] else { panic!() };
    t.answer(a, FALSCH);
    assert!(t.game.out.contains(&a));
    t.open();
    assert_eq!(t.buzz(a), Err(ActionError::Ignored));
    t.answer(b, 0);
    t.at(INTRO + TIME);
    t.at(INTRO + TIME + 5000.0);
    assert_eq!(t.game.round, 2);
    assert!(t.game.out.is_empty(), "back in for the next question");
}

#[test]
fn a_repeated_answer_counts_as_wrong() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, b, ..] = t.others()[..] else { panic!() };
    t.answer(a, 2);
    t.answer(b, 2);
    assert_eq!(t.game.holder, Some(a));
    assert!(t.game.out.contains(&b));
}

#[test]
fn also_right_takes_the_stick_without_using_a_listed_answer() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, ..] = t.others()[..] else { panic!() };
    t.open();
    t.buzz(a).unwrap();
    t.think();
    t.also_right().unwrap();
    assert_eq!(t.game.holder, Some(a));
    assert_eq!(t.game.given, vec![(None, a)]);
    assert!(!t.game.is_given(0));
}

#[test]
fn the_holder_wins_when_the_hidden_time_runs_out() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, ..] = t.others()[..] else { panic!() };
    t.answer(a, 0);
    t.at(INTRO + TIME - 1.0);
    assert!(matches!(t.game.stage, Stage::Open { .. }));
    t.at(INTRO + TIME);
    assert_eq!(t.game.stage, Stage::RoundEnd { winner: Some(a), ends_at: INTRO + TIME + 5000.0 });
    assert_eq!(t.score(a), 1);
}

#[test]
fn nobody_wins_when_nobody_held_the_stick() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    t.at(INTRO + TIME);
    assert!(matches!(t.game.stage, Stage::RoundEnd { winner: None, .. }));
    assert!(t.players.iter().all(|p| p.score == 0));
}

#[test]
fn the_round_ends_when_every_answer_is_given() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let others = t.others();
    let listed = t.game.prompt.answers.len() as u8;
    for i in 0..listed {
        t.answer(others[usize::from(i) % 2], i);
    }
    let last = others[usize::from(listed - 1) % 2];
    assert!(matches!(t.game.stage, Stage::RoundEnd { winner: Some(w), .. } if w == last));
}

#[test]
fn the_round_ends_when_only_the_holder_could_buzz() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let [a, b] = t.others()[..] else { panic!() };
    t.answer(a, 0);
    t.answer(b, FALSCH);
    assert!(matches!(t.game.stage, Stage::RoundEnd { winner: Some(w), .. } if w == a));
}

#[test]
fn all_out_and_no_holder_ends_without_a_winner() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    let [a, b] = t.others()[..] else { panic!() };
    t.answer(a, FALSCH);
    t.answer(b, FALSCH);
    assert!(matches!(t.game.stage, Stage::RoundEnd { winner: None, .. }));
}

#[test]
fn time_up_during_floor_waits_for_the_verdict() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, ..] = t.others()[..] else { panic!() };
    t.at(INTRO + TIME - 10.0);
    t.buzz(a).unwrap();
    t.at(INTRO + TIME + 3000.0);
    assert!(matches!(t.game.stage, Stage::Floor { player, .. } if player == a), "the buzz came in time");
    t.judge(0).unwrap();
    assert!(matches!(t.game.stage, Stage::RoundEnd { winner: Some(w), .. } if w == a));
}

#[test]
fn a_buzz_after_time_up_is_ignored() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, ..] = t.others()[..] else { panic!() };
    t.now = INTRO + TIME; // the tick has not run yet
    assert_eq!(t.buzz(a), Err(ActionError::Ignored));
}

#[test]
fn the_second_buzz_is_ignored() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, b, ..] = t.others()[..] else { panic!() };
    t.open();
    t.buzz(a).unwrap();
    assert_eq!(t.buzz(b), Err(ActionError::Ignored));
    assert!(matches!(t.game.stage, Stage::Floor { player, .. } if player == a));
    assert_eq!(t.game.buzzes, 1);
}

#[test]
fn a_verdict_without_a_floor_is_ignored() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, ..] = t.others()[..] else { panic!() };
    t.answer(a, 0);
    // A double tap on the host's desk: the second lands after the verdict.
    assert_eq!(t.judge(1), Err(ActionError::Ignored));
    assert_eq!(t.game.given.len(), 1);
}

#[test]
fn the_host_rotates_in_seat_order_and_everyone_hosts_equally() {
    let mut t = Table::new(&["Ada", "Bo", "Cy"]);
    assert_eq!(t.game.rounds, 6, "two laps of three");
    let mut hosts = vec![t.game.host];
    let mut now = 0.0;
    while t.game.stage != Stage::Final {
        now += 1000.0;
        t.at(now);
        if matches!(t.game.stage, Stage::Open { .. }) && hosts.len() < usize::from(t.game.round) {
            hosts.push(t.game.host);
        }
    }
    assert_eq!(hosts.len(), 6);
    assert_eq!(hosts[..3], hosts[3..], "the second lap repeats the first");
    let ids: Vec<PlayerId> = t.players.iter().map(|p| p.id).collect();
    let at = ids.iter().position(|&p| p == hosts[0]).unwrap();
    assert_eq!(hosts[1], ids[(at + 1) % 3], "seat order");
}

#[test]
fn an_away_host_hands_the_round_on() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let host = t.game.host;
    let first = t.game.prompt.q.clone();
    let seat = t.players.iter().position(|p| p.id == host).unwrap();
    let name = t.players[seat].name.clone();
    t.players[seat] = Player::for_test(host, &name, false);
    t.players[seat].left_at = Some(0.0);
    t.at(HOUSE_GRACE_MS - 1.0);
    assert_eq!(t.game.host, host);
    t.at(HOUSE_GRACE_MS);
    let next = t.players[(seat + 1) % 4].id;
    assert_eq!((t.game.host, t.game.round), (next, 1), "the next seat hosts the same round");
    assert_ne!(t.game.prompt.q, first, "with a new question");
}

#[test]
fn the_game_ends_after_laps_times_players_rounds() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let mut now = 0.0;
    for _ in 0..1000 {
        now += 1000.0;
        t.at(now);
    }
    assert_eq!((t.game.stage, t.game.round), (Stage::Final, 8));
    assert!(t.game.is_finished());
}

#[test]
fn a_verdict_right_after_a_buzz_is_ignored() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, b, ..] = t.others()[..] else { panic!() };
    t.answer(a, 0);
    // The host's double tap lands just after the next buzz; it must not judge the new player.
    t.open();
    t.buzz(b).unwrap();
    t.now += 100.0;
    assert_eq!(t.judge(0), Err(ActionError::Ignored));
    assert!(!t.game.out.contains(&b));
    t.think();
    t.judge(1).unwrap();
    assert_eq!(t.game.holder, Some(b));
}

#[test]
fn an_away_next_host_is_passed_over_at_once() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let next = t.game.next_host().unwrap();
    let seat = t.players.iter().position(|p| p.id == next).unwrap();
    let name = t.players[seat].name.clone();
    t.players[seat] = Player::for_test(next, &name, false);
    t.at(INTRO + TIME);
    t.at(INTRO + TIME + 5000.0);
    assert_eq!(t.game.round, 2);
    assert_eq!(t.game.host, t.players[(seat + 1) % 4].id);
}

#[test]
fn away_players_do_not_hold_a_round_open() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, b, c] = t.others()[..] else { panic!() };
    let seat = t.players.iter().position(|p| p.id == c).unwrap();
    t.players[seat] = Player::for_test(c, "Cy", false);
    t.answer(a, 0);
    t.answer(b, FALSCH);
    assert!(matches!(t.game.stage, Stage::RoundEnd { winner: Some(w), .. } if w == a));
}
