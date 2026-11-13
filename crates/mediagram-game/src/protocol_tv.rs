//! Wire types of the TV view. The TV is allowed to be game specific: each
//! game adds a scene variant.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{
    CardsScene, DreifelderScene, FluechtigScene, GameEntry, GrandAlleeScene, KopfnussScene, LobbySettings, PlayerInfo,
    PosterScene, PreisfrageScene, QuizScene, RaceScene,
};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct TvView {
    pub join_url: String,
    pub players: Vec<PlayerInfo>,
    pub scene: TvScene,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "kind", rename_all = "snake_case")]
#[ts(export)]
pub enum TvScene {
    Lobby {
        min_players: u8,
        settings: LobbySettings,
        leader_name: Option<String>,
        /// Why the chosen game cannot start yet.
        blocked: Option<String>,
        /// The game catalogue, in lobby order.
        games: Vec<GameEntry>,
        /// A drawing board is connected.
        board: bool,
    },
    Quiz { scene: QuizScene },
    Poster { scene: PosterScene },
    Race { scene: RaceScene },
    Cards { scene: CardsScene },
    Kopfnuss { scene: KopfnussScene },
    GrandAllee { scene: GrandAlleeScene },
    Dreifelder { scene: DreifelderScene },
    Fluechtig { scene: FluechtigScene },
    Preisfrage { scene: PreisfrageScene },
}
