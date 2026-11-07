use super::*;
use crate::bauherr_rules::START_CASH;
use crate::protocol::{BauherrTurn, TvScene};

const ANA: PlayerId = 0;
const BEN: PlayerId = 1;

pub(super) fn game(rolls: &[u8]) -> (BauherrGame, Vec<Player>) {
    let players = vec![Player::for_test(ANA, "Ana", true), Player::for_test(BEN, "Ben", true)];
    let cfg = BauherrConfig { rolls: rolls.to_vec(), seed: Some(1), ..BauherrConfig::default() };
    (BauherrGame::new(cfg, &players, 0.0), players)
}

pub(super) fn tap(g: &mut BauherrGame, p: &mut [Player], who: PlayerId, index: u8, now: f64) -> Result<(), ActionError> {
    g.on_action(&mut GameCtx { now, players: p }, who, &Action::Choose { index, at: now })
}

/// Ticks every 50 ms until `stop` holds (or 60 s pass) and returns the time.
pub(super) fn until(g: &mut BauherrGame, p: &mut [Player], mut now: f64, stop: impl Fn(&BauherrGame) -> bool) -> f64 {
    let end = now + 60_000.0;
    while !stop(g) && now < end {
        now += 50.0;
        g.on_tick(&mut GameCtx { now, players: p });
    }
    assert!(stop(g), "stage never reached, stuck at {:?}", g.stage);
    now
}

pub(super) fn asking(g: &BauherrGame) -> bool {
    matches!(g.stage, Stage::Ask { .. })
}

pub(super) fn rolling(g: &BauherrGame) -> bool {
    matches!(g.stage, Stage::Roll { .. } | Stage::Final)
}

/// Seat `s` stands on `field` and its turn is on.
pub(super) fn place(g: &mut BauherrGame, s: usize, field: u8) {
    g.table.seats[s].field = field;
    g.turn = s;
}

pub(super) fn own(g: &mut BauherrGame, s: usize, plot: u8, levels: u8) {
    g.table.estates[usize::from(plot)] = crate::bauherr_rules::Estate { owner: Some(s), levels };
}

pub(super) fn phone(g: &BauherrGame, p: &[Player], who: PlayerId, now: f64) -> PhoneScreen {
    g.phone_screen(&ViewCtx { now, players: p, leader: Some(ANA) }, who)
}

pub(super) fn options(g: &BauherrGame, p: &[Player], who: PlayerId, now: f64) -> Vec<String> {
    match phone(g, p, who, now) {
        PhoneScreen::Sheet { options, .. } => options.into_iter().map(|o| o.label).collect(),
        other => panic!("not a sheet: {other:?}"),
    }
}

/// Rolls `die` for the player on turn and plays on to the next question or turn.
pub(super) fn roll(g: &mut BauherrGame, p: &mut [Player], who: PlayerId, now: f64) -> f64 {
    tap(g, p, who, 0, now).unwrap();
    until(g, p, now, |g| asking(g) || rolling(g) || matches!(g.stage, Stage::Note { .. }))
}

#[test]
fn a_roll_walks_the_pawn_and_passing_the_bank_pays() {
    let (mut g, mut p) = game(&[4]);
    place(&mut g, 0, 34);
    let now = roll(&mut g, &mut p, ANA, 100.0);
    assert_eq!(g.table.seats[0].field, 2);
    assert_eq!(g.table.seats[0].cash, 14_000);
    assert_eq!(p[0].score, 14_000, "the score is the net worth");
    assert!(matches!(g.stage, Stage::Roll { .. }));
    assert_eq!(g.turn, 1);
    assert!(now > 100.0 + 1150.0 + 900.0 + 4.0 * 280.0);
}

#[test]
fn the_tv_walks_from_where_the_pawn_stood() {
    let (mut g, mut p) = game(&[3]);
    tap(&mut g, &mut p, ANA, 0, 10.0).unwrap();
    until(&mut g, &mut p, 10.0, |g| matches!(g.stage, Stage::Walking { .. }));
    let view = ViewCtx { now: 0.0, players: &p, leader: None };
    let TvScene::Bauherr { scene } = g.tv_scene(&view) else { panic!() };
    assert!(matches!(scene.turn, BauherrTurn::Walking { from: 0, to: 3, value: 3, bonus: false, .. }));
    assert_eq!(scene.seats[0].field, 3);
    assert_eq!((scene.round, scene.rounds), (1, 90));
}

#[test]
fn a_free_plot_can_be_bought_on_its_bauplatz() {
    let (mut g, mut p) = game(&[1]);
    tap(&mut g, &mut p, ANA, 0, 10.0).unwrap();
    let now = until(&mut g, &mut p, 10.0, asking);
    assert_eq!(options(&g, &p, ANA, now), ["Kastanienhof kaufen · 1.000 Taler", "Lieber nicht"]);
    assert!(options(&g, &p, BEN, now).is_empty());
    tap(&mut g, &mut p, ANA, 0, now + 300.0).unwrap();
    assert_eq!(g.table.owner(0), Some(0));
    assert_eq!(g.table.seats[0].cash, 11_000);
    until(&mut g, &mut p, now, |g| g.turn == 1 && rolling(g));
}

#[test]
fn nothing_is_offered_for_a_plot_that_is_taken_or_too_dear() {
    let (mut g, mut p) = game(&[1, 3]);
    own(&mut g, 1, 0, 0);
    roll(&mut g, &mut p, ANA, 10.0);
    assert_eq!(g.turn, 1, "a taken plot offers nothing");
    g.table.seats[1].cash = 500;
    place(&mut g, 1, 0);
    roll(&mut g, &mut p, BEN, 20_000.0);
    assert_eq!(g.turn, 0, "500 Taler do not buy 2.000 Taler of land");
}

fn at_baustelle(rolls: &[u8]) -> (BauherrGame, Vec<Player>) {
    let (mut g, p) = game(rolls);
    own(&mut g, 0, 0, 1);
    place(&mut g, 0, 33);
    (g, p)
}

#[test]
fn the_permit_die_decides_whether_and_how_cheaply_to_build() {
    // 2 steps from field 33 reach the Baustelle at 35; then the permit die
    for (die, labels) in [
        (1, vec!["Bis Anbau · 900 Taler", "Bis Biergarten · 1.800 Taler", "Lieber nicht"]),
        (5, vec!["Bis Anbau · gratis", "Bis Biergarten · 900 Taler", "Lieber nicht"]),
        (6, vec!["Bis Anbau · 1.800 Taler", "Bis Biergarten · 3.600 Taler", "Lieber nicht"]),
    ] {
        let (mut g, mut p) = at_baustelle(&[2, die]);
        tap(&mut g, &mut p, ANA, 0, 10.0).unwrap();
        let now = until(&mut g, &mut p, 10.0, asking);
        assert_eq!(options(&g, &p, ANA, now), ["Kastanienhof ausbauen", "Lieber nicht"]);
        tap(&mut g, &mut p, ANA, 0, now + 300.0).unwrap();
        assert!(matches!(g.stage, Stage::Permit { .. }));
        let now = until(&mut g, &mut p, now, asking);
        assert_eq!(options(&g, &p, ANA, now), labels, "permit die {die}");
    }
}

#[test]
fn a_rejected_permit_builds_nothing_and_ends_the_turn() {
    let (mut g, mut p) = at_baustelle(&[2, 4]);
    tap(&mut g, &mut p, ANA, 0, 10.0).unwrap();
    let now = until(&mut g, &mut p, 10.0, asking);
    tap(&mut g, &mut p, ANA, 0, now + 300.0).unwrap();
    until(&mut g, &mut p, now, |g| matches!(g.stage, Stage::Note { .. }));
    assert!(matches!(&g.stage, Stage::Note { text, .. } if text.contains("abgelehnt")));
    assert_eq!(g.table.levels(0), 1);
    assert_eq!(g.table.seats[0].cash, START_CASH);
}

#[test]
fn building_pays_the_chosen_stages_in_order() {
    let (mut g, mut p) = at_baustelle(&[2, 5]);
    tap(&mut g, &mut p, ANA, 0, 10.0).unwrap();
    let now = until(&mut g, &mut p, 10.0, asking);
    tap(&mut g, &mut p, ANA, 0, now + 300.0).unwrap();
    let now = until(&mut g, &mut p, now, asking);
    tap(&mut g, &mut p, ANA, 1, now + 300.0).unwrap(); // up to the Biergarten, the first stage free
    assert_eq!(g.table.levels(0), 3);
    assert_eq!(g.table.seats[0].cash, START_CASH - 900);
    assert_eq!(p[0].score, START_CASH - 900 + 1000 + 1200 + 900 + 900);
}

#[test]
fn building_is_not_offered_without_money_for_the_next_stage() {
    let (mut g, mut p) = at_baustelle(&[2]);
    g.table.seats[0].cash = 800;
    roll(&mut g, &mut p, ANA, 10.0);
    assert_eq!(g.turn, 1);
}

#[test]
fn a_free_entrance_needs_a_built_hotel() {
    let (mut g, mut p) = game(&[3]);
    place(&mut g, 0, 6);
    roll(&mut g, &mut p, ANA, 10.0); // field 9, but no hotel yet
    assert_eq!(g.turn, 1, "nothing to place an entrance at");
}

#[test]
fn a_free_entrance_goes_on_a_free_field_of_the_built_hotel() {
    let (mut g, mut p) = game(&[3]);
    own(&mut g, 0, 0, 1);
    place(&mut g, 0, 6);
    tap(&mut g, &mut p, ANA, 0, 10.0).unwrap();
    let now = until(&mut g, &mut p, 10.0, asking);
    assert_eq!(options(&g, &p, ANA, now), ["Feld 2", "Feld 3", "Feld 36", "Feld 35", "Lieber nicht"]);
    let TvScene::Bauherr { scene } = g.tv_scene(&ViewCtx { now, players: &p, leader: None }) else { panic!() };
    let BauherrTurn::Ask { marks, plot, .. } = scene.turn else { panic!() };
    assert_eq!((marks.len(), plot), (4, Some(0)));
    tap(&mut g, &mut p, ANA, 1, now + 300.0).unwrap();
    assert_eq!(g.table.entrances[2], Some(0));
    assert_eq!(g.table.seats[0].cash, START_CASH, "free");
    assert_eq!(g.table.free_fields(0), [1, 35, 34], "one entrance per field");
}

#[test]
fn a_paid_entrance_at_the_rathaus_costs_the_hotels_entrance_price() {
    let (mut g, mut p) = game(&[1]);
    own(&mut g, 0, 0, 1);
    place(&mut g, 0, 17);
    tap(&mut g, &mut p, ANA, 0, 10.0).unwrap();
    let now = until(&mut g, &mut p, 10.0, asking);
    assert_eq!(options(&g, &p, ANA, now), ["Hotel ausbauen", "Eingang kaufen", "Lieber nicht"]);
    tap(&mut g, &mut p, ANA, 1, now + 300.0).unwrap();
    assert!(options(&g, &p, ANA, now).first().is_some_and(|l| l.starts_with("Feld") && l.ends_with("300 Taler")));
    tap(&mut g, &mut p, ANA, 0, now + 900.0).unwrap();
    assert_eq!(g.table.seats[0].cash, START_CASH - 300);
}

#[test]
fn a_guest_pays_nights_times_the_current_rate_to_the_owner() {
    let (mut g, mut p) = game(&[1, 3]);
    own(&mut g, 1, 0, 2); // Kastanienhof, rate 300 with two stages
    g.table.place_entrance(1, 0, 1, 0);
    tap(&mut g, &mut p, ANA, 0, 10.0).unwrap();
    until(&mut g, &mut p, 10.0, rolling);
    assert_eq!(g.table.seats[0].cash, START_CASH - 900);
    assert_eq!(g.table.seats[1].cash, START_CASH + 900);
    assert_eq!(g.turn, 1);
}

#[test]
fn a_guest_who_cannot_pay_is_out_and_the_last_player_wins() {
    let (mut g, mut p) = game(&[1, 6]);
    own(&mut g, 1, 0, 3);
    g.table.place_entrance(1, 0, 1, 0);
    g.table.seats[0].cash = 100;
    tap(&mut g, &mut p, ANA, 0, 10.0).unwrap();
    until(&mut g, &mut p, 10.0, |g| g.stage == Stage::Final);
    assert!(g.table.seats[0].out);
    assert!(g.is_finished());
    let ranking = match g.tv_view() {
        TvScene::Bauherr { scene } => match scene.turn {
            BauherrTurn::Final { winner, ranking } => (winner, ranking),
            other => panic!("{other:?}"),
        },
        _ => panic!(),
    };
    assert_eq!(ranking, (BEN, vec![BEN, ANA]));
    assert!(matches!(phone(&g, &p, ANA, 0.0), PhoneScreen::Final { rank: 2, of: 2, .. }));
    assert!(matches!(phone(&g, &p, ANA, 0.0), PhoneScreen::Final { can_restart: true, .. }));
}

#[test]
fn the_game_ends_after_the_last_round_and_the_richest_wins() {
    let (mut g, mut p) = game(&[2, 2, 2, 2]);
    g.rounds = 2;
    g.table.seats[1].cash += 500;
    let mut now = 1000.0;
    for who in [ANA, BEN, ANA, BEN] {
        assert!(!g.is_finished());
        now = roll(&mut g, &mut p, who, now);
    }
    assert!(g.is_finished());
    assert_eq!(g.table.standings(), [1, 0]);
    assert!(matches!(phone(&g, &p, BEN, 0.0), PhoneScreen::Final { rank: 1, score: 12_500, .. }));
}

#[test]
fn an_absent_player_rolls_and_buys_while_keeping_a_reserve() {
    let (mut g, mut p) = game(&[1]);
    p[0].conn = None;
    p[0].left_at = Some(0.0);
    until(&mut g, &mut p, 0.0, |g| g.turn == 1 && rolling(g));
    assert_eq!(g.table.owner(0), Some(0), "bought the plot by itself");
    // too little left to buy: it passes
    let (mut g, mut p) = game(&[1]);
    g.table.seats[0].cash = 2500;
    p[0].conn = None;
    p[0].left_at = Some(0.0);
    until(&mut g, &mut p, 0.0, |g| g.turn == 1 && rolling(g));
    assert_eq!(g.table.owner(0), None);
}

#[test]
fn an_early_or_foreign_tap_is_ignored() {
    let (mut g, mut p) = game(&[1]);
    assert_eq!(tap(&mut g, &mut p, BEN, 0, 100.0), Err(ActionError::Ignored));
    assert_eq!(tap(&mut g, &mut p, ANA, 1, 100.0), Err(ActionError::Ignored));
    tap(&mut g, &mut p, ANA, 0, 100.0).unwrap();
    assert_eq!(tap(&mut g, &mut p, ANA, 0, 100.0), Err(ActionError::Ignored), "the die is already in the air");
    let now = until(&mut g, &mut p, 100.0, asking);
    // a tap that began before the question appeared answered the roll, not this
    let Stage::Ask { since, .. } = g.stage else { panic!() };
    let early = since - 1000.0;
    assert_eq!(g.on_action(&mut GameCtx { now, players: &mut p }, ANA, &Action::Choose { index: 0, at: early }), Err(ActionError::Ignored));
    assert_eq!(tap(&mut g, &mut p, ANA, 9, now), Err(ActionError::NotAllowed("Das gibt es nicht.")));
}

#[test]
fn the_phone_sheet_shows_cash_rounds_and_hotels_to_everyone() {
    let (mut g, p) = game(&[]);
    own(&mut g, 1, 3, 2);
    let PhoneScreen::Sheet { lines, prompt, options, .. } = phone(&g, &p, BEN, 0.0) else { panic!() };
    let lines: Vec<_> = lines.into_iter().map(|l| (l.label, l.value)).collect();
    assert_eq!(lines[0], ("Taler".into(), "12.000".into()));
    assert_eq!(lines[1], ("Runde".into(), "1 von 90".into()));
    assert_eq!(lines[2], ("Stadtpalais".into(), "Stufe 2 von 6 · 600 Taler pro Nacht".into()));
    assert_eq!(prompt.as_deref(), Some("Ana ist dran."));
    assert!(options.is_empty());
    let PhoneScreen::Sheet { options, .. } = phone(&g, &p, ANA, 0.0) else { panic!() };
    assert_eq!(options.len(), 1);
}
