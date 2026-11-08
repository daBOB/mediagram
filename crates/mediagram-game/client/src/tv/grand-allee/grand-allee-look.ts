// What the Grand Allee board looks like on the TV beyond the layout itself: the camera, sizes, colours and the times of the
// TV's own animations. The server's clocks (dice flight and show, hops, questions, auctions) come with the scene; these are the TV's.

import type { GrandAlleeFieldKind } from "../../protocol/GrandAlleeFieldKind";

/** The overview (user, 2026-11-08): 55 degrees down from the south onto three's ground point `at` [x, z], `dist` metres away. */
export const CAMERA = { pitch: 55, dist: 142, at: [0, 1] as [number, number], fov: 35 };
/** Leaning in, at full lean: how far the view moves from the overview's centre towards the focus, and how much closer it comes. */
export const LEAN = { pan: 0.45, dolly: 0.35 };
/** On the north road the tall park hotels (Maharadscha, Skyline) stand between the TV and the pawn: closer and steeper, over them. */
export const DEEP = { pan: 0.6, dolly: 0.5, pitch: 72, hotels: [6, 7] };
/** Glide times, seconds (a critically damped follow settles in about four times this). */
export const GLIDE = { in: 1.0, out: 1.4, focus: 0.7 };

/** Pawn sprite height, base disc radius and hop height, metres. */
export const PAWN = { height: 6, base: 1.4, hop: 2.6 };
/** The dice: edge in metres, and where each rests, board metres from the pond's centre (the park's waist, south of the pond). */
export const DIE = { size: 3.2, number: [-3.7, -6.3] as [number, number], permit: [1.7, -6.3] as [number, number] };
/** A guest's coins: one per `per` Taler (three at least, `max` at most), each flying `flyMs`, `arc` metres high. */
export const COINS = { max: 10, per: 150, flyMs: 520, arc: 9 };

/** Seconds: a stage rising, an entrance dropping in, a plot changing hands, a pawn going broke, a cash change on its tag. */
export const TIMES = { rise: 2.4, place: 0.8, pulse: 1.8, topple: 1.2, flash: 2.6 };

/** The stand-in board's field colours (board_layout.py's), until board.glb exists. */
export const KIND_COLORS: Record<GrandAlleeFieldKind, string> = {
  bank: "#e9c46a", rathaus: "#e76f51", plot: "#bfb7a8", site: "#8d99ae", free_entrance: "#90be6d", free_build: "#4ea8de", allee: "#d8d2c4",
};
export const GROUND = { grass: "#6d8f45", water: "#3b7fb8", road: "#6f665b", edge: "#5a4330", side: "#46331f", table: "#22170f", free: "#9a9184" };
/** Behind the board: the page's own background. */
export const SKY = "#1a110b";
/** Towards the sun the hotels were baked under, low from the left front: it shades the small pieces and casts the blobs. */
export const SUN: [number, number, number] = [-0.8, 0.34, 0.5];
