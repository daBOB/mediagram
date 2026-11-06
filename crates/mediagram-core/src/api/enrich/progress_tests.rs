use super::*;

#[test]
fn each_step_counts_its_own_items_from_zero() {
    let meter = FetchMeter::default();
    let _run = meter.run();
    meter.start(FetchStep::LookingUp, 3);
    meter.set_done(2);
    let looking = meter.snapshot().unwrap();
    assert_eq!((looking.step, looking.done, looking.total), (FetchStep::LookingUp, 2, 3));

    meter.start(FetchStep::Downloading, 10);
    let downloading = meter.snapshot().unwrap();
    assert_eq!((downloading.step, downloading.done, downloading.total), (FetchStep::Downloading, 0, 10));
}

#[test]
fn done_never_passes_the_total() {
    let meter = FetchMeter::default();
    meter.start(FetchStep::Describing, 2);
    meter.set_done(5);
    assert_eq!(meter.snapshot().unwrap().done, 2);
}

#[test]
fn the_meter_is_empty_before_and_after_a_run() {
    let meter = FetchMeter::default();
    assert!(meter.snapshot().is_none());
    {
        let _run = meter.run();
        meter.start(FetchStep::LookingUp, 1);
        assert!(meter.snapshot().is_some());
    }
    assert!(meter.snapshot().is_none(), "a run that ended, or was cancelled, leaves nothing behind");
}
