// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { GrandAlleeTurn } from "../src/protocol/GrandAlleeTurn";
import { CameraRig, cameraWish, deepField, diceSpot, poseAt } from "../src/tv/grand-allee/grand-allee-camera";
import { fieldPoint } from "../src/tv/grand-allee/grand-allee-layout";
import { CAMERA, DEEP } from "../src/tv/grand-allee/grand-allee-look";
import { sceneOf, testBoard } from "./grand-allee-fixture";

const deg = (rad: number) => (rad * 180) / Math.PI;
const pitchOf = (p: ReturnType<typeof poseAt>) => deg(Math.atan2(p.pos[1] - p.at[1], p.pos[2] - p.at[2]));
const distOf = (p: ReturnType<typeof poseAt>) => Math.hypot(p.pos[1] - p.at[1], p.pos[2] - p.at[2]);
const walk = (from: number, to: number): GrandAlleeTurn => ({ stage: "walking", player: 0, value: to - from, from, to, started_at: 0, hop_ms: 280, bank: false });

test("the overview looks 55 degrees down at the board's centre, wherever the focus is", () => {
  const p = poseAt(0, 0, 40, -30);
  expect(pitchOf(p)).toBeCloseTo(CAMERA.pitch);
  expect(p.at).toEqual([CAMERA.at[0], 0, CAMERA.at[1]]);
  expect(distOf(p)).toBeCloseTo(CAMERA.dist);
});

test("leaning in comes closer; the deep lean closer still and steeper", () => {
  const near = poseAt(1, 0, 0, 0), deep = poseAt(1, 1, 0, 0);
  expect(distOf(near)).toBeLessThan(CAMERA.dist);
  expect(distOf(deep)).toBeLessThan(distOf(near));
  expect(pitchOf(deep)).toBeCloseTo(DEEP.pitch);
});

test("between turns and at the end, the overview", async () => {
  const b = await testBoard();
  expect(cameraWish(sceneOf({ turn: { stage: "note", player: 0, text: "", started_at: 0 } }), b, 0)).toBeNull();
  expect(cameraWish(sceneOf({ turn: { stage: "final", winner: 0, ranking: [0] } }), b, 0)).toBeNull();
});

test("a walk on the north road behind the tall park hotels gets the deep lean, elsewhere the plain one", async () => {
  const b = await testBoard();
  expect(DEEP.hotels.every((h) => deepField(sceneOf(), sceneOf().board.hotels[h].first))).toBe(true);
  const north = cameraWish(sceneOf({ turn: walk(7, 12) }), b, 0)!;
  expect(north).toMatchObject({ lean: 1, deep: 1, ...fieldPoint(b, 7) });
  expect(cameraWish(sceneOf({ turn: walk(26, 30) }), b, 0)).toMatchObject({ lean: 1, deep: 0 });
});

test("a die throw keeps the pawn and the dice in view; an auction looks at its hotel", async () => {
  const b = await testBoard(), dice = diceSpot(b), pawn = fieldPoint(b, 0);
  const rolled = cameraWish(sceneOf({ turn: { stage: "rolled", player: 0, value: 3, rolled_at: 0, lands_at: 1150 } }), b, 0)!;
  expect(rolled.x).toBeCloseTo((pawn.x + dice.x) / 2);
  expect(rolled.z).toBeCloseTo((pawn.z + dice.z) / 2);
  const auction = cameraWish(sceneOf({ turn: { stage: "auction", auction: { seller: 0, hotel: 7, value: 9000, opened_at: 0, closes_at: 15000, bidders: [1], bid: [], passed: [] } } }), b, 0)!;
  expect(auction).toMatchObject({ x: b.plots[7].x, z: b.plots[7].z });
});

test("the rig glides to the wish and back out without jumping", async () => {
  const b = await testBoard(), rig = new CameraRig(), s = sceneOf({ turn: walk(26, 30) });
  let last = rig.update(s, b, 0, 0);
  for (let i = 0; i < 300; i++) {
    const p = rig.update(s, b, 0, 1 / 60);
    expect(Math.abs(p.pos[2] - last.pos[2])).toBeLessThan(2);
    last = p;
  }
  const wish = cameraWish(s, b, 0)!;
  expect(last.at[0]).toBeCloseTo(poseAt(1, 0, wish.x, wish.z).at[0], 1);
});
