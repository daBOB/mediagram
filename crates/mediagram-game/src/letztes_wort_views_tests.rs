use crate::letztes_wort::tests::{FALSCH, Table};
use crate::letztes_wort_config::PROMPTS;
use crate::protocol::{LetztesWortPhone, PhoneScreen, PlayerId};

fn phone(t: &Table, player: PlayerId) -> PhoneScreen {
    let view = crate::game::ViewCtx { now: t.now, players: &t.players, leader: None };
    t.game.phone_view(&view, player)
}

fn tv_json(t: &Table) -> String {
    let view = crate::game::ViewCtx { now: t.now, players: &t.players, leader: None };
    serde_json::to_string(&t.game.tv_view(&view)).unwrap()
}

#[test]
fn the_tv_never_carries_an_answer() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, b, ..] = t.others()[..] else { panic!() };
    let mut scenes = vec![tv_json(&t)];
    t.answer(a, 0);
    scenes.push(tv_json(&t));
    t.open();
    t.buzz(b).unwrap();
    scenes.push(tv_json(&t));
    t.think();
    t.judge(1).unwrap();
    t.at(60_000.0);
    scenes.push(tv_json(&t));
    let every_answer: Vec<&str> = PROMPTS.iter().flat_map(|p| p.answers.iter().map(String::as_str)).collect();
    for scene in &scenes {
        // Quoted, so an answer that is also a word of a question (a number, a country) is not counted.
        for answer in &every_answer {
            assert!(!scene.contains(&format!("\"{answer}\"")), "the TV shows {answer}: {scene}");
        }
    }
}

#[test]
fn the_host_sees_the_answers_and_who_gave_them() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, b, ..] = t.others()[..] else { panic!() };
    t.answer(a, 1);
    t.open();
    t.buzz(b).unwrap();
    let PhoneScreen::LetztesWort { phone: LetztesWortPhone::Host { answers, floor, .. } } = phone(&t, t.game.host()) else { panic!() };
    assert_eq!(answers.len(), t.game.prompt.answers.len());
    assert_eq!(answers[1].given_by.as_deref(), Some(t.players.iter().find(|p| p.id == a).unwrap().name.as_str()));
    assert_eq!(answers[0].given_by, None);
    assert_eq!(floor.as_deref(), Some(t.players.iter().find(|p| p.id == b).unwrap().name.as_str()));
}

#[test]
fn a_player_sees_a_live_buzzer_only_when_allowed() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, b, ..] = t.others()[..] else { panic!() };
    let live = |t: &Table, p| matches!(phone(t, p), PhoneScreen::LetztesWort { phone: LetztesWortPhone::Buzzer { live: true, .. } });
    assert!(live(&t, a));
    t.open();
    t.buzz(a).unwrap();
    assert!(!live(&t, b), "someone else has the floor");
    let PhoneScreen::LetztesWort { phone: LetztesWortPhone::Buzzer { button, .. } } = phone(&t, a) else { panic!() };
    assert_eq!(button, "Du bist dran!");
}

#[test]
fn the_holder_sees_the_stick() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, ..] = t.others()[..] else { panic!() };
    t.answer(a, 0);
    let PhoneScreen::LetztesWort { phone: LetztesWortPhone::Stick { title, .. } } = phone(&t, a) else { panic!() };
    assert_eq!(title, "Du hast den Stab!");
}

#[test]
fn an_out_player_sees_the_outcome() {
    let mut t = Table::new(&["Ada", "Bo", "Cy", "Dee"]);
    let [a, ..] = t.others()[..] else { panic!() };
    t.answer(a, FALSCH);
    let PhoneScreen::Outcome { title, .. } = phone(&t, a) else { panic!() };
    assert_eq!(title, "Falsch – du bist raus");
}
