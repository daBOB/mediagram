use super::*;

#[test]
fn paused_it_keeps_what_was_left() {
    let mut c = TurnClock::new(90_000.0);
    c.start(0.0);
    c.pause(10_000.0);
    assert_eq!(c.left(50_000.0), 80_000.0);
}

#[test]
fn starting_twice_does_not_reset_it() {
    let mut c = TurnClock::new(90_000.0);
    c.start(0.0);
    c.start(5_000.0);
    assert_eq!(c.left(10_000.0), 80_000.0);
}

#[test]
fn the_wire_shows_ends_at_only_while_running() {
    let mut c = TurnClock::new(90_000.0);
    c.start(1_000.0);
    assert_eq!(c.wire(90_000.0), Clock { ends_at: Some(91_000.0), left_ms: 90_000.0, total_ms: 90_000.0 });
    c.pause(11_000.0);
    assert_eq!(c.wire(90_000.0), Clock { ends_at: None, left_ms: 80_000.0, total_ms: 90_000.0 });
}

#[test]
fn it_never_goes_below_zero() {
    let mut c = TurnClock::new(1_000.0);
    c.start(0.0);
    assert_eq!(c.left(5_000.0), 0.0);
    c.pause(5_000.0);
    assert_eq!(c.wire(1_000.0).left_ms, 0.0);
    c.start(6_000.0);
    assert_eq!(c.wire(1_000.0).ends_at, None, "nothing left to run");
}
