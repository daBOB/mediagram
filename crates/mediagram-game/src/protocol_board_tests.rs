use crate::protocol::{Action, ClientMsg, PhoneView, Role, ServerMsg};
use crate::protocol_board::{BoardAct, BoardPlayer, BoardScreen, Clock, DrawOp};

fn roundtrip<T>(value: &T, json: &str)
where
    T: serde::Serialize + serde::de::DeserializeOwned + PartialEq + std::fmt::Debug,
{
    assert_eq!(serde_json::to_string(value).unwrap(), json);
    assert_eq!(&serde_json::from_str::<T>(json).unwrap(), value);
}

fn ada() -> BoardPlayer {
    BoardPlayer { id: 0, name: "Ada".into(), color: "#ef476f".into(), avatar: None }
}

#[test]
fn board_acts_have_the_shapes_the_page_and_the_app_send() {
    roundtrip(&BoardAct::Ready, r#"{"t":"ready"}"#);
    roundtrip(&BoardAct::KeepDrawing, r#"{"t":"keep_drawing"}"#);
    roundtrip(&BoardAct::Pick { player: Some(3) }, r#"{"t":"pick","player":3}"#);
    roundtrip(&BoardAct::Pick { player: None }, r#"{"t":"pick","player":null}"#);
    let line = DrawOp::Line { color: 0, width: 1, points: vec![10, 20, 30, 40] };
    roundtrip(&BoardAct::Draw { op: line }, r#"{"t":"draw","op":{"t":"line","color":0,"width":1,"points":[10,20,30,40]}}"#);
    roundtrip(&BoardAct::Draw { op: DrawOp::More { points: vec![50, 60] } }, r#"{"t":"draw","op":{"t":"more","points":[50,60]}}"#);
    roundtrip(&BoardAct::Draw { op: DrawOp::Undo }, r#"{"t":"draw","op":{"t":"undo"}}"#);
}

#[test]
fn a_board_hello_and_action_parse() {
    let hello: ClientMsg = serde_json::from_str(r#"{"t":"hello","role":"board","name":null,"token":null}"#).unwrap();
    assert!(matches!(hello, ClientMsg::Hello { role: Role::Board, .. }));
    let json = r#"{"t":"action","action":{"t":"board","act":{"t":"draw","op":{"t":"clear"}}}}"#;
    let msg: ClientMsg = serde_json::from_str(json).unwrap();
    assert_eq!(msg, ClientMsg::Action { action: Action::Board { act: BoardAct::Draw { op: DrawOp::Clear } } });
}

#[test]
fn draw_and_board_messages_have_their_shapes() {
    roundtrip(&ServerMsg::Draw { turn: 2, from: 0, ops: vec![] }, r#"{"t":"draw","turn":2,"from":0,"ops":[]}"#);
    let drawer = r##"{"id":0,"name":"Ada","color":"#ef476f","avatar":null}"##;
    let handover = ServerMsg::Board { screen: BoardScreen::Handover { turn: 1, drawer: ada() } };
    roundtrip(&handover, &format!(r#"{{"t":"board","screen":{{"kind":"handover","turn":1,"drawer":{drawer}}}}}"#));
    let clock = Clock { ends_at: None, left_ms: 90000.0, total_ms: 90000.0 };
    let drawing = ServerMsg::Board { screen: BoardScreen::Drawing { turn: 1, drawer: ada(), word: "Hund".into(), clock } };
    let json = format!(
        r#"{{"t":"board","screen":{{"kind":"drawing","turn":1,"drawer":{drawer},"word":"Hund","clock":{{"ends_at":null,"left_ms":90000.0,"total_ms":90000.0}}}}}}"#
    );
    roundtrip(&drawing, &json);
}

#[test]
fn a_phone_view_without_buzz_still_parses() {
    let json = r##"{"me":{"id":1,"name":"Bo","color":"#fff","score":0,"connected":true,"leader":false,"avatar":null},"screen":{"kind":"wait","title":"x","text":null}}"##;
    let view: PhoneView = serde_json::from_str(json).unwrap();
    assert_eq!(view.buzz, None);
}
