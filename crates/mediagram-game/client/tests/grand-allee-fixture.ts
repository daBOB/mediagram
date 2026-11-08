// The Grand Allee board as the host sends it, players and a scene to start from, for the tests and the board's stand-in host
// (tools/grand-allee-preview). Kinds, hotels and stages as in src/grand_allee/board.rs.
import type { GrandAlleeBoard } from "../src/protocol/GrandAlleeBoard";
import type { GrandAlleeFieldKind } from "../src/protocol/GrandAlleeFieldKind";
import type { GrandAlleeScene } from "../src/protocol/GrandAlleeScene";
import type { PlayerInfo } from "../src/protocol/PlayerInfo";
import { boardOf, type Board, type LayoutJson } from "../src/tv/grand-allee/grand-allee-layout";

const KINDS = "B.GEP.S..P.SP.S.P.S.R.GEP.S..P.SP.S.P.S.";
const KIND: Record<string, GrandAlleeFieldKind> = { B: "bank", R: "rathaus", P: "plot", S: "site", E: "free_entrance", G: "free_build", ".": "allee" };
// name, first and last frontage field, land, entrance, [cost, rate] per stage
const HOTELS: [string, number, number, number, number, [number, number][]][] = [
  ["Lagune", 4, 8, 1200, 300, [[1200, 75], [900, 150], [900, 225]]],
  ["Kirschblüte", 9, 13, 2000, 500, [[2000, 100], [1500, 200], [1500, 325], [1200, 425]]],
  ["Fjordhus", 14, 19, 2600, 600, [[2400, 125], [1800, 250], [1800, 400], [1600, 525], [1400, 650]]],
  ["Hacienda Sol", 24, 28, 1000, 300, [[1200, 75], [900, 150], [900, 225]]],
  ["Riad Atlas", 29, 33, 1300, 350, [[1400, 75], [1000, 150], [1000, 225], [800, 300]]],
  ["Alpenrose", 34, 39, 2300, 550, [[2200, 125], [1600, 250], [1600, 375], [1400, 500], [1200, 575]]],
  ["Maharadscha", 4, 11, 3000, 700, [[2800, 125], [2000, 275], [2000, 425], [1800, 575], [1600, 675], [1400, 750]]],
  ["Skyline", 12, 19, 3500, 800, [[2800, 150], [2200, 300], [2200, 425], [1800, 550], [1600, 650], [1400, 750]]],
  ["Savanne", 24, 31, 2800, 650, [[2600, 125], [1900, 250], [1900, 400], [1700, 525], [1500, 650]]],
  ["Belle Époque", 32, 39, 3200, 750, [[2800, 150], [2100, 300], [2100, 425], [1800, 550], [1600, 650], [1400, 750]]],
];

export const boardData = (): GrandAlleeBoard => ({
  fields: [...KINDS].map((k) => KIND[k]),
  hotels: HOTELS.map(([name, first, last, land, entrance, stages], id) => ({ id, name, first, last, land, entrance, stages: stages.map(([cost, rate]) => ({ cost, rate })) })),
});

const CAST: [string, string, string][] = [
  ["Lena", "#e4572e", "fox"], ["Jonas", "#3fae5a", "frog"], ["Oma Gerda", "#f2b631", "granny"], ["Papa", "#3d7be8", "panda"],
  ["Mia", "#b05ce0", "cat"], ["Theo", "#19b5a5", "bear"], ["Ida", "#e8579b", "bunny"], ["Ben", "#8a8f99", "hedgehog"],
];
export const players = (n = 4): PlayerInfo[] =>
  CAST.slice(0, n).map(([name, color, avatar], id) => ({ id, name, color, avatar, score: 0, connected: true, leader: id === 0 }));

/** A scene of `seats` players on the Bank at the start of round 1, with `patch` over it. */
export function sceneOf(patch: Partial<GrandAlleeScene> = {}, seats = 4): GrandAlleeScene {
  return {
    board: boardData(),
    estates: [],
    entrances: [],
    seats: Array.from({ length: seats }, (_, player) => ({ player, field: 0, cash: 30000, worth: 30000, out: false })),
    round: 1,
    rounds: 20,
    turn: { stage: "ask", player: 0, prompt: "Du bist dran", hotel: null, marks: [], opened_at: 0, closes_at: 20000 },
    die: null,
    permit: null,
    nights: null,
    ...patch,
  };
}

/** The client's copy of board_path.py's layout, read from the disk (tests only: bun runs them). */
export const testBoard = async (): Promise<Board> =>
  boardOf((await Bun.file(new URL("../public/grand-allee/layout.json", import.meta.url).pathname).json()) as LayoutJson);
