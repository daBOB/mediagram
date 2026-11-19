//! How a car moves after a spin: field by field along the graph, collecting a
//! salary at every payday it enters (passing or landing, as in the original),
//! halting early at a STOP, at a fork (the player picks a branch, then the walk
//! goes on with the steps left) or at a retirement home.

use super::board::{Board, Kind};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Halt {
    /// Every step taken; the car stands on `Walk::to`.
    Landed,
    /// A STOP field ends the move, whatever was spun.
    Stop,
    /// The car stands where the path splits with `left` steps still to go.
    Fork { left: u8 },
    /// A retirement home: the car is out of the race.
    Home,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Walk {
    /// Every field entered, the last is where the car stands.
    pub path: Vec<usize>,
    pub to: usize,
    pub paydays: u32,
    pub halt: Halt,
}

/// Walks `steps` fields from `from`. At a fork the walk halts unless `branch`
/// says which way (it is used for the first fork only); `skip` is a STOP this
/// car drives through.
pub fn walk(board: &Board, from: usize, steps: u8, mut branch: Option<usize>, skip: Option<usize>) -> Walk {
    let (mut at, mut path, mut paydays) = (from, Vec::new(), 0);
    for taken in 0..steps {
        let next = &board.fields[at].next;
        at = match (next.len(), branch.take()) {
            (0, _) => break,
            (1, _) => next[0],
            (_, Some(b)) => next[b.min(next.len() - 1)],
            (_, None) => return Walk { to: at, path, paydays, halt: Halt::Fork { left: steps - taken } },
        };
        path.push(at);
        let field = &board.fields[at];
        if field.kind == Kind::Payday {
            paydays += 1;
        }
        if board.is_end(at) {
            return Walk { to: at, path, paydays, halt: Halt::Home };
        }
        if field.kind == Kind::Stop && skip != Some(at) {
            return Walk { to: at, path, paydays, halt: Halt::Stop };
        }
    }
    Walk { to: at, path, paydays, halt: Halt::Landed }
}
