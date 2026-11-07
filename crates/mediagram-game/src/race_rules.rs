//! Rules of the race game (Heimlauf): where pieces stand, which moves a roll
//! allows, and what a move does. Plain data and pure functions, so every rule
//! is tested without a room.
//!
//! A piece's place is its progress from its own start field: `None` while it
//! waits at home, 0..=39 on the shared track, 40..=43 in its own goal.

use crate::protocol::PlayerId;

pub const TRACK: u8 = 40;
/// The deepest goal field.
pub const GOAL_END: u8 = TRACK + 3;
pub const PIECES: usize = 4;

pub type Progress = Option<u8>;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Seat {
    pub player: PlayerId,
    /// Board corner 0..=3; its start field is track field `corner * 10`.
    pub corner: u8,
    pub pieces: [Progress; PIECES],
}

impl Seat {
    pub fn new(player: PlayerId, corner: u8) -> Self {
        Self { player, corner, pieces: [None; PIECES] }
    }

    /// The shared track field (0..=39) a progress on the track stands on.
    pub fn field(&self, progress: u8) -> Option<u8> {
        (progress < TRACK).then(|| (self.corner * 10 + progress) % TRACK)
    }

    pub fn in_goal(&self) -> usize {
        self.pieces.iter().filter(|p| p.is_some_and(|v| v >= TRACK)).count()
    }

    pub fn finished(&self) -> bool {
        self.in_goal() == PIECES
    }

    /// Fields walked by all pieces together, to rank players who did not finish.
    pub fn walked(&self) -> u32 {
        self.pieces.iter().map(|p| p.map_or(0, |v| u32::from(v) + 1)).sum()
    }

    /// Nothing on the track and every goal piece packed at the far end: only
    /// a 6 can do anything, so the player gets three tries.
    pub fn three_tries(&self) -> bool {
        self.pieces.iter().all(|p| match *p {
            None => true,
            Some(v) if v >= TRACK => (v + 1..=GOAL_END).all(|q| self.holds(q)),
            Some(_) => false,
        })
    }

    fn holds(&self, progress: u8) -> bool {
        self.pieces.contains(&Some(progress))
    }
}

/// Board corners for a table of `n` players; two sit opposite each other.
pub fn corners(n: usize) -> &'static [u8] {
    match n {
        0..=2 => &[0, 2],
        3 => &[0, 1, 2],
        _ => &[0, 1, 2, 3],
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Move {
    pub piece: u8,
    pub from: Progress,
    pub to: u8,
    /// The opponent piece this move sends home: (seat index, piece index).
    pub captures: Option<(usize, u8)>,
}

/// The moves seat `me` may make with `roll`.
pub fn legal_moves(seats: &[Seat], me: usize, roll: u8) -> Vec<Move> {
    let seat = &seats[me];
    let waiting = seat.pieces.iter().position(Option::is_none);
    // A 6 must bring a piece out while the own start field is free of own pieces.
    if roll == 6
        && let Some(piece) = waiting
        && !seat.holds(0)
    {
        return vec![step(seats, me, piece, None, 0)];
    }
    let moves: Vec<Move> = (0..PIECES)
        .filter_map(|i| {
            let from = seat.pieces[i]?;
            target(seat, from, roll).map(|to| step(seats, me, i, Some(from), to))
        })
        .collect();
    // While pieces wait at home, an own piece on the start field must clear it, if it can.
    if waiting.is_some()
        && let Some(clear) = moves.iter().find(|m| m.from == Some(0))
    {
        return vec![*clear];
    }
    moves
}

fn target(seat: &Seat, from: u8, roll: u8) -> Option<u8> {
    let to = from + roll;
    if to > GOAL_END {
        return None;
    }
    if to < TRACK {
        return (!seat.holds(to)).then_some(to);
    }
    // Into or inside the goal: exact count, never over or onto an own piece.
    (from.max(TRACK - 1) + 1..=to).all(|q| !seat.holds(q)).then_some(to)
}

fn step(seats: &[Seat], me: usize, piece: usize, from: Progress, to: u8) -> Move {
    let captures = seats[me].field(to).and_then(|f| {
        seats.iter().enumerate().filter(|(j, _)| *j != me).find_map(|(j, s)| {
            let hit = s.pieces.iter().position(|p| p.and_then(|v| s.field(v)) == Some(f))?;
            Some((j, hit as u8))
        })
    });
    Move { piece: piece as u8, from, to, captures }
}

pub fn apply(seats: &mut [Seat], me: usize, mv: &Move) {
    seats[me].pieces[usize::from(mv.piece)] = Some(mv.to);
    if let Some((j, piece)) = mv.captures {
        seats[j].pieces[usize::from(piece)] = None;
    }
}

/// Seat indices from first to last place: the winner, then by pieces in the
/// goal, then by fields walked.
pub fn standings(seats: &[Seat], winner: usize) -> Vec<usize> {
    let mut order: Vec<usize> = (0..seats.len()).collect();
    order.sort_by_key(|&i| (i != winner, std::cmp::Reverse((seats[i].in_goal(), seats[i].walked()))));
    order
}

#[cfg(test)]
#[path = "race_rules_tests.rs"]
mod tests;
