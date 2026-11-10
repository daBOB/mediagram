// The Grand Allee board as the host sends it, players and a scene to start from, for the tests and the board's stand-in host
// (tools/grand-allee-preview). The board is the host's own: `cargo test` writes it to grand-allee-board.json.
import type { GrandAlleeBoard } from "../src/protocol/GrandAlleeBoard";
import type { GrandAlleeScene } from "../src/protocol/GrandAlleeScene";
import type { PlayerInfo } from "../src/protocol/PlayerInfo";
import { boardOf, type Board, type LayoutJson } from "../src/tv/grand-allee/grand-allee-layout";
import board from "./grand-allee-board.json";

export const boardData = (): GrandAlleeBoard => structuredClone(board) as GrandAlleeBoard;

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
