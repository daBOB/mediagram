use crate::catalogue::CATALOGUE;
use crate::protocol::{Action, GameKind, GameLength, GameSpeed, LobbySettings, PhoneScreen, QuizMode, ServerMsg, TvScene, TvView};
use crate::room::tests::{Idle, action, cfg, decode, hello, room, room_with};
use crate::room::{ConnId, Outbox, Room, RoomConfig};
use crate::room_settings::Readiness;
use std::sync::{Arc, Mutex};

fn lobby_of(room: &mut Room, conn: ConnId) -> PhoneScreen {
    match room.view_for(conn, 0.0) {
        Some(ServerMsg::Phone { view }) => view.screen,
        other => panic!("expected a phone view, got {other:?}"),
    }
}

fn configure(room: &mut Room, conn: ConnId, game: GameKind, quiz_mode: QuizMode, kids_only: bool) -> Outbox {
    action(room, conn, Action::Configure { game, quiz_mode, speed: GameSpeed::Normal, length: GameLength::Normal, kids_only })
}

#[test]
fn adult_questions_are_asked_unless_kids_mode_is_on() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    assert!(r.settings().asks_adult_questions(), "a grown-ups' evening by default");
    configure(&mut r, 1, GameKind::Quiz, QuizMode::General, true);
    assert!(!r.settings().asks_adult_questions(), "kids mode leaves them out");
    configure(&mut r, 1, GameKind::Quiz, QuizMode::General, false);
    assert!(r.settings().asks_adult_questions());
}

#[test]
fn a_kids_profile_never_gets_adult_questions() {
    let mut r = room_with(RoomConfig { kids_locked: true, ..cfg(8) });
    hello(&mut r, 1, "Ana", None);
    configure(&mut r, 1, GameKind::Quiz, QuizMode::General, false);
    assert!(r.settings().kids_only && !r.settings().asks_adult_questions());
}

#[test]
fn an_older_page_still_sending_the_adult_switch_is_accepted() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    let out = r.handle(1, r#"{"t":"action","action":{"t":"configure","game":"quiz","quiz_mode":"general","kids_only":true,"adult":true}}"#, 0.0);
    assert!(out.is_empty(), "accepted: {out:?}");
    assert!(r.settings().kids_only && !r.settings().asks_adult_questions());
}

#[test]
fn locked_kids_toggle_stays_on() {
    let mut r = room_with(RoomConfig { kids_locked: true, ..cfg(8) });
    hello(&mut r, 1, "Ana", None);
    assert!(r.settings().kids_only, "starts on");
    configure(&mut r, 1, GameKind::Quiz, QuizMode::Films, false);
    assert!(r.settings().kids_only);
}

#[test]
fn each_game_starts_within_its_seat_range() {
    // The game, its seat range and what the lobby says outside it.
    let table = [
        (GameKind::Race, 2, 4, "Heimlauf braucht mindestens 2 Spieler.", Some("Heimlauf geht mit höchstens 4 Spielern.")),
        (GameKind::GrandAllee, 2, 8, "Grand Allee braucht mindestens 2 Spieler.", None),
        (GameKind::MauMau, 2, 8, "Mau-Mau braucht mindestens 2 Spieler.", None),
        (GameKind::Schwimmen, 2, 8, "Schwimmen braucht mindestens 2 Spieler.", None),
        (GameKind::Durak, 2, 6, "Durak braucht mindestens 2 Spieler.", Some("Durak geht mit höchstens 6 Spielern.")),
        (GameKind::Kopfnuss, 2, 8, "Kopfnuss braucht mindestens 2 Spieler.", None),
        (GameKind::Dreifelder, 2, 8, "Dreifelder braucht mindestens 2 Spieler.", None),
        (GameKind::Fluechtig, 2, 6, "Flüchtig braucht mindestens 2 Spieler.", Some("Flüchtig geht mit höchstens 6 Spielern.")),
    ];
    for (game, min, max, too_few, too_many) in table {
        let mut r = room(8);
        for n in 1..=8u8 {
            hello(&mut r, n.into(), &format!("P{n}"), None);
            configure(&mut r, 1, game, QuizMode::Mixed, false);
            let want = if n < min { Some(too_few) } else if n > max { too_many } else { None };
            let PhoneScreen::Lobby { can_start, blocked, .. } = lobby_of(&mut r, 1) else { panic!() };
            assert_eq!(blocked.as_deref(), want, "{game:?} with {n}");
            assert_eq!(can_start, want.is_none(), "{game:?} with {n}");
        }
    }
}

#[test]
fn lobby_views_carry_the_game_catalogue() {
    let mut r = room(8);
    r.connect(9, true);
    r.handle(9, r#"{"t":"hello","role":"tv","name":null,"token":null}"#, 0.0);
    hello(&mut r, 1, "Ana", None);
    let PhoneScreen::Lobby { games, .. } = lobby_of(&mut r, 1) else { panic!() };
    assert_eq!(games, CATALOGUE);
    let Some(ServerMsg::Tv { view: TvView { scene: TvScene::Lobby { games, .. }, .. } }) = r.view_for(9, 0.0) else { panic!() };
    assert_eq!(games, CATALOGUE);
}

#[test]
fn poster_game_needs_posters() {
    let mut r = room_with(RoomConfig { readiness: Readiness { poster: true, poster_kids: false, ..Readiness::default() }, ..cfg(8) });
    hello(&mut r, 1, "Ana", None);
    configure(&mut r, 1, GameKind::Poster, QuizMode::Mixed, true);
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: false, blocked: Some(_), .. }));
    let out = action(&mut r, 1, Action::StartGame);
    let ServerMsg::Error { message, .. } = decode(&out[0].1) else { panic!("expected an error") };
    assert_eq!(message, "Nicht genug Poster für dieses Spiel.");
    assert!(!r.in_game());
    configure(&mut r, 1, GameKind::Poster, QuizMode::Mixed, false);
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: true, blocked: None, .. }));
}

#[test]
fn tv_lobby_carries_the_same_blocked_text() {
    let mut r = room_with(RoomConfig { readiness: Readiness { poster: true, poster_kids: false, ..Readiness::default() }, ..cfg(8) });
    r.connect(9, true);
    r.handle(9, r#"{"t":"hello","role":"tv","name":null,"token":null}"#, 0.0);
    hello(&mut r, 1, "Ana", None);
    let tv_blocked = |r: &Room| match r.view_for(9, 0.0) {
        Some(ServerMsg::Tv { view: TvView { scene: TvScene::Lobby { blocked, .. }, .. } }) => blocked,
        other => panic!("expected a TV lobby, got {other:?}"),
    };
    configure(&mut r, 1, GameKind::Poster, QuizMode::Mixed, true);
    let PhoneScreen::Lobby { blocked: phone_blocked, .. } = lobby_of(&mut r, 1) else { panic!() };
    assert!(phone_blocked.is_some());
    assert_eq!(tv_blocked(&r), phone_blocked);
    configure(&mut r, 1, GameKind::Poster, QuizMode::Mixed, false);
    assert_eq!(tv_blocked(&r), None);
}

#[test]
fn only_the_leader_configures() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    hello(&mut r, 2, "Ben", None);
    let before = r.settings().clone();
    assert!(!configure(&mut r, 2, GameKind::Poster, QuizMode::Films, true).is_empty());
    assert_eq!(*r.settings(), before);
}

#[test]
fn the_tv_picks_the_game_but_keeps_the_kids_lock() {
    let mut r = room_with(RoomConfig { kids_locked: true, ..cfg(8) });
    r.connect(9, true);
    r.handle(9, r#"{"t":"hello","role":"tv","name":null,"token":null}"#, 0.0);
    assert!(configure(&mut r, 9, GameKind::Durak, QuizMode::Mixed, false).is_empty());
    assert_eq!(r.settings().game, GameKind::Durak);
    assert!(r.settings().kids_only);
}

#[test]
fn the_tv_changes_only_the_game_whatever_else_it_sends() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    r.connect(9, true);
    r.handle(9, r#"{"t":"hello","role":"tv","name":null,"token":null}"#, 0.0);
    // the leader turns kids mode on while the TV's pick, made before it, is on its way
    configure(&mut r, 1, GameKind::Quiz, QuizMode::Films, true);
    assert!(configure(&mut r, 9, GameKind::Race, QuizMode::Mixed, false).is_empty());
    let s = r.settings();
    assert_eq!((s.game, s.quiz_mode, s.kids_only), (GameKind::Race, QuizMode::Films, true));
}

#[test]
fn the_leader_sets_the_tempo_the_tv_keeps_it_and_an_older_client_means_normal() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    r.connect(9, true);
    r.handle(9, r#"{"t":"hello","role":"tv","name":null,"token":null}"#, 0.0);
    assert_eq!(r.settings().speed, GameSpeed::Normal, "normal at the start of a night");
    let tempo = |speed| Action::Configure { game: GameKind::Preisfrage, quiz_mode: QuizMode::Mixed, speed, length: GameLength::Normal, kids_only: false };
    assert!(action(&mut r, 1, tempo(GameSpeed::Relaxed)).is_empty());
    assert_eq!(r.settings().speed, GameSpeed::Relaxed);
    assert!(action(&mut r, 9, tempo(GameSpeed::Speedrun)).is_empty());
    assert_eq!(r.settings().speed, GameSpeed::Relaxed, "the TV's pick changes only the game");
    let out = r.handle(1, r#"{"t":"action","action":{"t":"configure","game":"preisfrage","quiz_mode":"mixed","kids_only":false}}"#, 0.0);
    assert!(out.is_empty(), "accepted: {out:?}");
    assert_eq!(r.settings().speed, GameSpeed::Normal);
}

#[test]
fn the_leader_sets_the_length_the_tv_keeps_it_and_an_older_client_means_normal() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    r.connect(9, true);
    r.handle(9, r#"{"t":"hello","role":"tv","name":null,"token":null}"#, 0.0);
    assert_eq!(r.settings().length, GameLength::Normal, "normal at the start of a night");
    let length = |length| Action::Configure { game: GameKind::Quiz, quiz_mode: QuizMode::Mixed, speed: GameSpeed::Normal, length, kids_only: false };
    assert!(action(&mut r, 1, length(GameLength::Long)).is_empty());
    assert_eq!(r.settings().length, GameLength::Long);
    assert!(action(&mut r, 9, length(GameLength::Short)).is_empty());
    assert_eq!(r.settings().length, GameLength::Long, "the TV's pick changes only the game");
    let out = r.handle(1, r#"{"t":"action","action":{"t":"configure","game":"quiz","quiz_mode":"mixed","kids_only":false}}"#, 0.0);
    assert!(out.is_empty(), "accepted: {out:?}");
    assert_eq!(r.settings().length, GameLength::Normal);
}

#[test]
fn settings_survive_a_game() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    configure(&mut r, 1, GameKind::Quiz, QuizMode::Films, true);
    let chosen = r.settings().clone();
    action(&mut r, 1, Action::StartGame);
    assert!(r.in_game());
    assert!(!configure(&mut r, 1, GameKind::Quiz, QuizMode::Mixed, false).is_empty(), "not mid-game");
    r.back_to_lobby();
    assert_eq!(*r.settings(), chosen);
}

#[test]
fn factory_sees_the_settings() {
    let seen: Arc<Mutex<Option<LobbySettings>>> = Arc::default();
    let sink = seen.clone();
    let mut r = Room::new(
        cfg(8),
        Box::new(move |_, s| {
            *sink.lock().unwrap() = Some(s.clone());
            Box::new(Idle)
        }),
    );
    hello(&mut r, 1, "Ana", None);
    configure(&mut r, 1, GameKind::Quiz, QuizMode::Films, true);
    action(&mut r, 1, Action::StartGame);
    assert_eq!(seen.lock().unwrap().as_ref(), Some(r.settings()));
    assert_eq!(r.settings().quiz_mode, QuizMode::Films);
}

#[test]
fn kopfnuss_is_not_for_kids_mode() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    hello(&mut r, 2, "Ben", None);
    configure(&mut r, 1, GameKind::Kopfnuss, QuizMode::Mixed, true);
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: false, blocked: Some(b), .. } if b == "Kopfnuss gibt es nicht im Kindermodus."));
}

#[test]
fn dreifelder_needs_two_players_and_no_kids_mode() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    configure(&mut r, 1, GameKind::Dreifelder, QuizMode::Mixed, false);
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: false, blocked: Some(b), .. } if b == "Dreifelder braucht mindestens 2 Spieler."));
    hello(&mut r, 2, "Ben", None);
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: true, blocked: None, .. }));
    configure(&mut r, 1, GameKind::Dreifelder, QuizMode::Mixed, true);
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: false, blocked: Some(b), .. } if b == "Dreifelder gibt es nicht im Kindermodus."));
}

#[test]
fn preisfrage_needs_articles_and_no_kids_mode() {
    let mut r = room_with(RoomConfig { readiness: Readiness { preisfrage: false, ..Readiness::default() }, ..cfg(8) });
    hello(&mut r, 1, "Ana", None);
    configure(&mut r, 1, GameKind::Preisfrage, QuizMode::Mixed, false);
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: false, blocked: Some(b), .. } if b == crate::room_settings::PREISFRAGE_NEEDS_ARTICLES));
    configure(&mut r, 1, GameKind::Preisfrage, QuizMode::Mixed, true);
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: false, blocked: Some(b), .. } if b == "Preisfrage gibt es nicht im Kindermodus."), "kids mode says so first");

    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    configure(&mut r, 1, GameKind::Preisfrage, QuizMode::Mixed, false);
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: true, blocked: None, .. }), "one player plays alone");
}

fn blocked_text(r: &mut Room, conn: ConnId) -> Option<String> {
    match lobby_of(r, conn) {
        PhoneScreen::Lobby { blocked, .. } => blocked,
        other => panic!("expected the lobby, got {other:?}"),
    }
}

#[test]
fn kritzelei_waits_for_the_board() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    hello(&mut r, 2, "Ben", None);
    configure(&mut r, 1, GameKind::Kritzelei, QuizMode::Mixed, false);
    assert_eq!(blocked_text(&mut r, 1).as_deref(), Some("Kritzelei braucht das iPad als Zeichenbrett."));
    r.connect(3, false);
    r.handle(3, r#"{"t":"hello","role":"board"}"#, 0.0);
    assert_eq!(blocked_text(&mut r, 1), None);
}

#[test]
fn kritzelei_plays_in_kids_mode() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    hello(&mut r, 2, "Ben", None);
    r.connect(3, false);
    r.handle(3, r#"{"t":"hello","role":"board"}"#, 0.0);
    configure(&mut r, 1, GameKind::Kritzelei, QuizMode::Mixed, true);
    assert_eq!(blocked_text(&mut r, 1), None);
}

#[test]
fn kritzelei_needs_two_players() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    r.connect(3, false);
    r.handle(3, r#"{"t":"hello","role":"board"}"#, 0.0);
    configure(&mut r, 1, GameKind::Kritzelei, QuizMode::Mixed, false);
    assert_eq!(blocked_text(&mut r, 1).as_deref(), Some("Kritzelei braucht mindestens 2 Spieler."));
}

#[test]
fn zuendstoff_plays_in_kids_mode() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    hello(&mut r, 2, "Ben", None);
    configure(&mut r, 1, GameKind::Zuendstoff, QuizMode::Mixed, true);
    assert_eq!(blocked_text(&mut r, 1), None);
}

#[test]
fn the_general_quiz_is_not_for_kids_mode() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    configure(&mut r, 1, GameKind::Quiz, QuizMode::General, true);
    assert_eq!(blocked_text(&mut r, 1).as_deref(), Some(crate::room_settings::GENERAL_QUIZ_NOT_FOR_KIDS));
    let out = action(&mut r, 1, Action::StartGame);
    let ServerMsg::Error { message, .. } = decode(&out[0].1) else { panic!("expected an error") };
    assert_eq!(message, "Allgemeine Fragen gibt es nicht im Kindermodus.");
    assert!(!r.in_game());
    configure(&mut r, 1, GameKind::Quiz, QuizMode::General, false);
    assert_eq!(blocked_text(&mut r, 1), None);
}

#[test]
fn a_kids_quiz_needs_a_kids_film_question_for_every_round_of_its_length() {
    // Eight kid-safe titles that make a question: Kurz (4) and Normal (8) play, Lang (12) does not.
    let mut r = room_with(RoomConfig { readiness: Readiness { kids_quiz: 8, ..Readiness::default() }, ..cfg(8) });
    hello(&mut r, 1, "Ana", None);
    let set = |r: &mut Room, quiz_mode, length, kids_only| {
        action(r, 1, Action::Configure { game: GameKind::Quiz, quiz_mode, speed: GameSpeed::Normal, length, kids_only });
    };
    for mode in [QuizMode::Films, QuizMode::Mixed] {
        for (length, want) in [(GameLength::Short, None), (GameLength::Normal, None), (GameLength::Long, Some(crate::room_settings::NOT_ENOUGH_KIDS_FILMS))] {
            set(&mut r, mode, length, true);
            assert_eq!(blocked_text(&mut r, 1).as_deref(), want, "{mode:?} {length:?}");
            // A grown-ups' quiz fills from the general pack, so the library never blocks it.
            set(&mut r, mode, length, false);
            assert_eq!(blocked_text(&mut r, 1), None, "{mode:?} {length:?} without kids mode");
        }
    }
    set(&mut r, QuizMode::Mixed, GameLength::Long, true);
    let out = action(&mut r, 1, Action::StartGame);
    let ServerMsg::Error { message, .. } = decode(&out[0].1) else { panic!("expected an error") };
    assert_eq!(message, "Nicht genug Kinderfilme für ein Quiz dieser Dauer.");
    assert!(!r.in_game());
}
