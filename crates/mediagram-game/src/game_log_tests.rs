use std::time::{SystemTime, UNIX_EPOCH};

use serde_json::{Value, json};
use tokio::sync::mpsc;

use super::append_log;
use crate::grand_allee::live::{GrandAlleeConfig, GrandAlleeGame};
use crate::protocol::{Action, ClientMsg, Role};
use crate::room::Room;
use crate::room::tests::cfg;
use crate::room_loop::{Event, room_loop};

fn unix_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64
}

fn frame(msg: &ClientMsg) -> String {
    serde_json::to_string(msg).unwrap()
}

#[tokio::test]
async fn a_started_games_lines_are_appended_to_the_file_with_the_wall_clock() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("grand-allee-log.jsonl");
    std::fs::write(&path, "{\"ev\":\"end\"}\n").unwrap();
    let room = Room::new(
        cfg(8),
        Box::new(|ctx, _| Box::new(GrandAlleeGame::new(GrandAlleeConfig { seed: Some(1), ..GrandAlleeConfig::default() }, ctx.players, ctx.now))),
    );
    let (events, rx) = mpsc::unbounded_channel();
    let task = tokio::spawn(room_loop(room, rx, Some(path.clone())));
    let (tx, _outbox) = mpsc::unbounded_channel();
    let before = unix_ms();
    events.send(Event::Connect(1, false, tx)).unwrap();
    let hello = ClientMsg::Hello { role: Role::Phone, name: Some("Ana".into()), token: None, avatar: None };
    events.send(Event::Text(1, frame(&hello))).unwrap();
    events.send(Event::Text(1, frame(&ClientMsg::Action { action: Action::StartGame }))).unwrap();
    events.send(Event::Stop).unwrap();
    task.await.unwrap();

    let lines: Vec<Value> = std::fs::read_to_string(&path).unwrap().lines().map(|l| serde_json::from_str(l).unwrap()).collect();
    assert_eq!(lines.len(), 2, "{lines:?}");
    assert_eq!(lines[0], json!({"ev": "end"}), "an earlier night's lines stay");
    assert_eq!((lines[1]["ev"].as_str(), lines[1]["players"].as_u64()), (Some("start"), Some(1)));
    let stamp = lines[1]["unix_ms"].as_u64().expect("the writer adds the wall-clock time");
    assert!((before..=unix_ms()).contains(&stamp));
}

#[test]
fn a_log_that_cannot_be_written_is_skipped_without_a_panic() {
    let dir = tempfile::tempdir().unwrap();
    // a directory cannot be opened for appending
    append_log(dir.path(), vec![json!({"ev": "start"})]);
    assert!(dir.path().is_dir());
}
