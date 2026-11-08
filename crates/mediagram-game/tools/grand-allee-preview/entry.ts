// A stand-in host for looking at the Grand Allee board without a game: the real board code draws a scene made up here. The preset
// comes in the address (#opening, #midgame, #dice, #auction, #entrance, #north); the host clock stands still at its moment, so a
// screenshot catches a die in the air. `window.settled` turns true once the board is up and the camera has had time to glide.
// Used by preview.mjs and shoot.mjs; nothing here ships.
import type { AnswerColor } from "../../client/src/protocol/AnswerColor";
import type { GrandAlleeScene } from "../../client/src/protocol/GrandAlleeScene";
import type { GrandAlleeTurn } from "../../client/src/protocol/GrandAlleeTurn";
import { startGrandAllee } from "../../client/src/tv/grand-allee/grand-allee-stage";
import { players as cast, sceneOf } from "../../client/tests/grand-allee-fixture";

/** The host time every preset stands still at. */
const T = 1_000_000;
const players = cast(6);
const seats = (fields: number[], cash: number[] = []) =>
  fields.map((field, player) => ({ player, field, cash: cash[player] ?? 24_000 - player * 1_700, worth: 30_000, out: false }));
// hotel, owner, stages built; entrances: field, hotel (each on its hotel's frontage)
const built = [[0, 0, 3], [1, 2, 2], [2, 1, 4], [3, 3, 1], [4, 4, 1], [5, 5, 3], [6, 1, 5], [7, 0, 6], [8, 2, 2], [9, 3, 4]].map(([hotel, owner, levels]) => ({ hotel, owner, levels }));
const doors = [[5, 0], [7, 0], [10, 1], [16, 2], [6, 6], [13, 7], [17, 7], [25, 3], [30, 4], [27, 8], [35, 9], [37, 5]].map(([field, hotel]) => ({ field, hotel }));
const ask = (player: number, prompt: string, hotel: number | null, marks: [number, AnswerColor][] = []): GrandAlleeTurn => ({
  stage: "ask", player, prompt, hotel, marks: marks.map(([field, color]) => ({ field, color })), opened_at: T - 6_000, closes_at: T + 14_000,
});

const PRESETS: Record<string, Partial<GrandAlleeScene>> = {
  opening: { seats: seats([0, 0, 0, 0, 0, 0], Array(6).fill(30_000)), turn: ask(0, "Du bist dran", null) },
  midgame: {
    estates: built, entrances: doors, seats: seats([2, 9, 15, 22, 28, 36]), round: 9, die: 4, permit: "approved",
    turn: { stage: "note", player: 2, text: "Oma Gerda baut Fjordhus: Stufe 4 von 5.", started_at: T - 300 },
  },
  dice: {
    estates: built.slice(0, 5), entrances: doors.slice(0, 4), seats: seats([3, 12, 21, 26, 31, 38]), round: 4,
    turn: { stage: "rolled", player: 3, value: 5, rolled_at: T - 520, lands_at: T + 630 },
  },
  auction: {
    // Riad Atlas and Savanne fully built: their early stages' ground baked dark under the later ones (see the 4c report)
    estates: built.map((e) => (e.hotel === 4 ? { ...e, levels: 4 } : e.hotel === 8 ? { ...e, levels: 5 } : e)), entrances: doors, seats: seats([2, 9, 15, 23, 28, 36], [400, 8_800, 15_200, 6_400, 21_000, 3_100]), round: 14,
    turn: { stage: "auction", auction: { seller: 0, hotel: 7, value: 18_000, opened_at: T - 6_000, closes_at: T + 9_000, bidders: [1, 2, 3, 4, 5], bid: [2, 4], passed: [5] } },
  },
  entrance: {
    estates: built, entrances: doors, seats: seats([2, 9, 15, 22, 34, 31]), round: 10,
    turn: ask(3, "Belle Époque: Eingang für 750 Taler – auf welches Feld?", 9, [[33, "blue"], [34, "orange"], [36, "yellow"]]),
  },
  north: {
    estates: built, entrances: doors, seats: seats([14, 9, 15, 23, 28, 36]), round: 11, die: 6,
    turn: { stage: "walking", player: 0, value: 6, from: 7, to: 14, started_at: T - 3.5 * 280, hop_ms: 280, bank: false },
  },
};

const flag = window as unknown as { ready?: boolean; settled?: boolean; failed?: string; info?: () => unknown };
const name = location.hash.slice(1) || "midgame";
if (!(name in PRESETS)) flag.failed = `no preset "${name}"`;
const scene = sceneOf(PRESETS[name] ?? {}, 6);
startGrandAllee(document.getElementById("stage")!, () => ({ scene, players }), () => T).then(
  (h) => {
    flag.info = h.stats;
    flag.ready = true;
    // the camera glides for about four seconds: wait that long and 50 frames at least (a software GPU draws few a second)
    const t0 = performance.now();
    let frames = 0;
    const tick = () => {
      if (++frames >= 50 && performance.now() - t0 > 4500) flag.settled = true;
      else requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  },
  (e) => {
    flag.failed = String(e?.stack ?? e);
  },
);
