use super::*;
use rand::SeedableRng;

fn rng() -> StdRng {
    StdRng::seed_from_u64(11)
}

fn vote(player: PlayerId, option: u8) -> (PlayerId, u8) {
    (player, option)
}

#[test]
fn majority_wins() {
    let votes = [vote(0, 2), vote(1, 2), vote(2, 0)];
    assert_eq!(resolve_category(&votes, &[2], 3, &mut rng()), 2);
}

#[test]
fn a_tie_goes_to_the_last_placed_player() {
    let votes = [vote(0, 0), vote(1, 1)];
    assert_eq!(resolve_category(&votes, &[1], 3, &mut rng()), 1);
    assert_eq!(resolve_category(&votes, &[0], 3, &mut rng()), 0);
}

#[test]
fn a_tie_last_place_stayed_out_of_is_drawn_among_the_tied() {
    let votes = [vote(0, 0), vote(1, 1), vote(2, 2), vote(3, 2), vote(4, 0)];
    // Last place (4) voted 0, which is tied with 2; 1 is out of the tie.
    assert_eq!(resolve_category(&votes, &[4], 3, &mut rng()), 0);
    let votes = [vote(0, 0), vote(1, 1)];
    for seed in 0..20 {
        let c = resolve_category(&votes, &[5], 3, &mut StdRng::seed_from_u64(seed));
        assert!(c == 0 || c == 1, "only the tied options");
    }
    let a = resolve_category(&votes, &[5], 3, &mut rng());
    assert_eq!(a, resolve_category(&votes, &[5], 3, &mut rng()), "deterministic with a seed");
}

#[test]
fn no_votes_picks_one_of_the_offered() {
    for seed in 0..20 {
        assert!(resolve_category(&[], &[0], 3, &mut StdRng::seed_from_u64(seed)) < 3);
    }
}

fn send(at: f64, from: PlayerId, trick: Trick, to: PlayerId) -> Sent {
    Sent { at, from, trick, to }
}

#[test]
fn a_fourth_trick_bounces() {
    let sent = vec![
        send(1.0, 0, Trick::Spiegel, 9),
        send(2.0, 1, Trick::Kleber, 9),
        send(3.0, 2, Trick::Wechselbad, 9),
        send(4.0, 3, Trick::Schneckenpost, 9),
    ];
    let (loads, hits) = resolve_tricks(sent, 3);
    assert_eq!(loads[&9], vec![Trick::Spiegel, Trick::Kleber, Trick::Wechselbad]);
    assert_eq!(hits.iter().map(|h| h.bounced).collect::<Vec<_>>(), [false, false, false, true]);
}

#[test]
fn the_same_trick_twice_bounces() {
    let (loads, hits) = resolve_tricks(vec![send(1.0, 0, Trick::Spiegel, 9), send(2.0, 1, Trick::Spiegel, 9)], 3);
    assert_eq!(loads[&9], vec![Trick::Spiegel]);
    assert!(hits[1].bounced);
}

#[test]
fn tricks_land_in_pick_order() {
    let sent = vec![send(5.0, 0, Trick::Spiegel, 9), send(2.0, 1, Trick::Spiegel, 9)];
    let (_, hits) = resolve_tricks(sent, 3);
    assert_eq!((hits[0].from, hits[0].bounced), (1, false), "the earlier pick lands");
    assert_eq!((hits[1].from, hits[1].bounced), (0, true));
}

fn sheet() -> Sheet {
    Sheet {
        offered: vec![Trick::Spiegel, Trick::Kleber],
        targets: vec![1, 2],
        vote: None,
        trick: None,
        target: None,
        step_at: 0.0,
    }
}

#[test]
fn a_double_tap_cannot_pick_the_next_step_unseen() {
    let mut s = sheet();
    assert!(s.tap(0, 3, 100.0, 400.0, 150.0));
    assert_eq!(s.step_at, 500.0, "the trick step opens a beat later");
    assert!(!s.tap(1, 3, 200.0, 400.0, 150.0), "second tap of a double tap");
    assert_eq!(s.trick, None);
    assert!(s.tap(1, 3, 380.0, 400.0, 150.0), "within the early tolerance");
    assert_eq!(s.trick, Some(Trick::Kleber));
}
