//! What the phones and the TV show during the race game.

use crate::game::{ViewCtx, name};
use crate::protocol::{
    AnswerColor, ChoiceOption, PhoneScreen, PlayerId, RaceCapture, RaceMove, RaceScene, RaceSeat, RaceStage, TvScene,
};
use crate::race::{RaceGame, Stage};
use crate::race_rules::{Move, PIECES, TRACK, standings};

impl RaceGame {
    fn seat_of(&self, player: PlayerId) -> Option<usize> {
        self.seats.iter().position(|s| s.player == player)
    }

    /// Button text for each move. Plain moves are told apart by how far along
    /// the piece is; the TV marks the same pieces in the button colours.
    fn labels(&self, view: &ViewCtx, moves: &[Move]) -> Vec<String> {
        let plain: Vec<u8> = moves.iter().filter(|m| is_plain(m)).filter_map(|m| m.from).collect();
        moves
            .iter()
            .map(|m| match (m.from, m.captures) {
                (None, None) => "Neue Figur raus".into(),
                (_, Some((seat, _))) => format!("{} rauswerfen", name(view.players, self.player_at(seat))),
                (Some(from), None) if from >= TRACK => "Im Ziel vorrücken".into(),
                (Some(_), None) if m.to >= TRACK => "Ins Ziel".into(),
                (Some(from), None) => {
                    let ahead = plain.iter().filter(|&&p| p > from).count();
                    match (plain.len(), ahead) {
                        (1, _) => "Figur ziehen".into(),
                        (_, 0) => "Vorderste Figur".into(),
                        (n, a) if a + 1 == n => "Hinterste Figur".into(),
                        (_, 1) => "Zweite Figur".into(),
                        _ => "Dritte Figur".into(),
                    }
                }
            })
            .collect()
    }

    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let wait = |title: String, text: Option<String>| PhoneScreen::Wait { title, text };
        let Some(me) = self.seat_of(player) else {
            return wait("Heimlauf läuft".into(), Some("Du spielst in der nächsten Runde mit.".into()));
        };
        if let Stage::Final { winner } = self.stage {
            let order = standings(&self.seats, winner);
            return PhoneScreen::Final {
                rank: order.iter().position(|&s| s == me).map_or(1, |r| r as u8 + 1),
                of: self.seats.len() as u8,
                score: self.seats[me].in_goal() as i32,
                can_restart: view.leader == Some(player),
            };
        }
        let mover = name(view.players, self.player_at(self.turn));
        if me != self.turn {
            // A wait screen, not an outcome: the outcome screen adds a points line, and this game has no points.
            if let Stage::Moving { mv: Move { captures: Some((victim, _)), .. }, .. } = self.stage
                && victim == me
            {
                return wait("Rausgeworfen!".into(), Some(format!("{mover} hat deine Figur geschlagen.")));
            }
            let home = self.seats[me].in_goal();
            return wait(format!("{mover} ist dran"), Some(format!("{home} von {PIECES} Figuren im Ziel")));
        }
        match &self.stage {
            Stage::Roll { since, tries, again } => PhoneScreen::Choice {
                prompt: Some(roll_prompt(self.seats[me].three_tries(), *tries, *again).into()),
                options: vec![ChoiceOption { label: "Würfeln".into(), color: AnswerColor::Blue }],
                opens_at: *since,
                closes_at: None,
                picked: None,
                tricks: vec![],
                numbered: false,
                status: None,
            },
            Stage::Rolled { at, .. } if view.now < self.lands_at(*at) => wait("Der Würfel rollt …".into(), None),
            Stage::Rolled { value, at, moves, tries } => match moves.len() {
                0 if *tries > 1 && *value != 6 => wait(format!("Eine {value}"), Some("Kein Zug – du darfst nochmal.".into())),
                0 => wait(format!("Eine {value}"), Some("Kein Zug möglich.".into())),
                1 => wait(format!("Eine {value}!"), self.labels(view, moves).pop()),
                _ => PhoneScreen::Choice {
                    prompt: Some(format!("Eine {value}! Welche Figur?")),
                    options: self
                        .labels(view, moves)
                        .into_iter()
                        .zip(AnswerColor::ALL)
                        .map(|(label, color)| ChoiceOption { label, color })
                        .collect(),
                    opens_at: self.lands_at(*at),
                    closes_at: None,
                    picked: None,
                    tricks: vec![],
                    numbered: false,
                    status: None,
                },
            },
            Stage::Moving { mv, .. } => match mv.captures {
                Some((victim, _)) => wait("Erwischt!".into(), Some(format!("Du wirfst {} raus.", name(view.players, self.player_at(victim))))),
                None => wait("Deine Figur läuft".into(), None),
            },
            Stage::Final { .. } => unreachable!("handled above"),
        }
    }

    pub(crate) fn tv_view(&self) -> TvScene {
        let player = self.player_at(self.turn);
        let stage = match &self.stage {
            Stage::Roll { again, .. } => RaceStage::Roll { player, again: *again },
            Stage::Rolled { value, at, moves, .. } => RaceStage::Rolled {
                player,
                value: *value,
                rolled_at: *at,
                lands_at: self.lands_at(*at),
                moves: moves.iter().map(|m| self.wire_move(m)).collect(),
            },
            Stage::Moving { value, mv, at, .. } => RaceStage::Moving {
                player,
                value: *value,
                mv: self.wire_move(mv),
                started_at: *at,
                hop_ms: self.cfg.hop_ms,
            },
            Stage::Final { winner } => RaceStage::Final {
                winner: self.player_at(*winner),
                ranking: standings(&self.seats, *winner).into_iter().map(|s| self.player_at(s)).collect(),
            },
        };
        let seats = self
            .seats
            .iter()
            .map(|s| RaceSeat { player: s.player, corner: s.corner, pieces: s.pieces.to_vec() })
            .collect();
        TvScene::Race { scene: RaceScene { seats, stage, die: self.die } }
    }

    fn wire_move(&self, m: &Move) -> RaceMove {
        RaceMove {
            piece: m.piece,
            from: m.from,
            to: m.to,
            captures: m.captures.map(|(seat, piece)| RaceCapture { player: self.player_at(seat), piece }),
        }
    }
}

fn is_plain(m: &Move) -> bool {
    m.captures.is_none() && m.from.is_some_and(|f| f < TRACK) && m.to < TRACK
}

fn roll_prompt(three_tries: bool, tries: u8, again: bool) -> &'static str {
    match (three_tries, tries, again) {
        (_, _, true) => "Eine 6 – nochmal würfeln!",
        (true, 3, _) => "Du bist dran – drei Versuche für eine 6",
        (true, 2, _) => "Noch zwei Versuche",
        (true, _, _) => "Letzter Versuch",
        _ => "Du bist dran",
    }
}
