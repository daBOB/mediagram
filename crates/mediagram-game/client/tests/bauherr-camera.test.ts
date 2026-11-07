// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import * as THREE from "three";
import type { BauherrScene } from "../src/protocol/BauherrScene";
import type { BauherrTurn } from "../src/protocol/BauherrTurn";
import { CameraRig, cameraWish, glide, poseAt, type CameraPose } from "../src/tv/bauherr/bauherr-camera";
import { DIE_SIZE } from "../src/tv/bauherr/bauherr-dice";
import { CAMERA, DICE_AT, FIELDS, HUD_BAND, PLAZA, RESULT_PILL, roadPoint } from "../src/tv/bauherr/bauherr-layout";

const seats = [0, 1, 2, 3].map((player) => ({ player, field: player * 9, cash: 1000, worth: 1000, out: false }));
const scene = (turn: BauherrTurn): BauherrScene =>
  ({ board: { tiles: [], plots: [] }, estates: [], entrances: [], seats, round: 3, rounds: 24, turn }) as BauherrScene;
const walking = (player: number, from: number, value: number): BauherrTurn => ({ stage: "walking", player, value, from, to: from + value, started_at: 1000, hop_ms: 800, bonus: false });

// where a world point lands on the 16:9 screen, as fractions from the top left
function project(pose: CameraPose, x: number, y: number, z: number) {
  const cam = new THREE.PerspectiveCamera(CAMERA.fov, 16 / 9, 0.5, 300);
  cam.position.set(...pose.pos);
  cam.lookAt(...pose.at);
  cam.updateMatrixWorld();
  const v = new THREE.Vector3(x, y, z).project(cam);
  return { x: (v.x + 1) / 2, y: (1 - v.y) / 2 };
}
const overview = poseAt(0, 0, 0);

test("glide approaches its target without overshoot and settles", () => {
  const v = { v: 0 };
  let x = 0, prev = 0;
  for (let i = 0; i < 60 * 8; i++) {
    x = glide(x, 1, v, 1, 1 / 60);
    expect(x).toBeLessThanOrEqual(1 + 1e-9);
    expect(x).toBeGreaterThanOrEqual(prev - 1e-9);
    prev = x;
  }
  expect(x).toBeCloseTo(1, 3);
});

test("the camera leans on the pawn while its owner rolls and walks, and is at the overview for everything else", () => {
  const f = (n: number) => roadPoint(n);
  expect(cameraWish(scene({ stage: "roll", player: 2 }), 0)).toEqual({ lean: 1, x: f(18).x, z: f(18).z });
  expect(cameraWish(scene({ stage: "rolled", player: 1, value: 3, rolled_at: 0, lands_at: 1 }), 0)?.lean).toBe(1);
  // mid-walk the focus is the pawn between its fields
  const mid = cameraWish(scene(walking(0, 4, 5)), 1000 + 800 * 2.5)!;
  expect(mid.x).toBeCloseTo(f(6.5).x, 6);
  expect(cameraWish(scene({ stage: "guest", player: 0, owner: 1, plot: 2, nights: 2, amount: 100, rolled_at: 0, lands_at: 1, paid_at: 2 }), 0)).toBeNull();
  expect(cameraWish(scene({ stage: "permit", player: 0, plot: 2, outcome: "approved", rolled_at: 0, lands_at: 1 }), 0)).toBeNull();
  expect(cameraWish(scene({ stage: "ask", player: 0, prompt: "?", plot: null, marks: [{ field: 3, color: "blue" }] }), 0)).toBeNull();
  expect(cameraWish(scene({ stage: "note", player: 0, text: "x", started_at: 0 }), 0)).toBeNull();
  expect(cameraWish(scene({ stage: "final", winner: 0, ranking: [0] }), 0)).toBeNull();
});

test("a turn eases the camera in, follows the hops and eases out again, never jumping", () => {
  const rig = new CameraRig();
  let pose = rig.update(scene({ stage: "roll", player: 0 }), 0, 0), last = pose, now = 0, worst = 0;
  const step = (s: BauherrScene, seconds: number) => {
    for (let i = 0; i < Math.round(seconds * 60); i++) {
      now += 1000 / 60;
      pose = rig.update(s, now, 1 / 60);
      worst = Math.max(worst, Math.hypot(pose.pos[0] - last.pos[0], pose.pos[1] - last.pos[1], pose.pos[2] - last.pos[2]));
      last = pose;
    }
  };
  step(scene({ stage: "roll", player: 0 }), 5);
  const leaned = poseAt(1, roadPoint(0).x, roadPoint(0).z);
  expect(pose.pos[0]).toBeCloseTo(leaned.pos[0], 1);
  expect(pose.pos[1]).toBeLessThan(overview.pos[1]);
  // the walk: the focus moves along the road with the pawn
  const w = walking(0, 0, 9);
  now = 1000;
  step({ ...scene(w), turn: { ...w, started_at: now } }, 9 * 0.8 + 1);
  step(scene({ stage: "ask", player: 0, prompt: "?", plot: null, marks: [] }), 8);
  expect(Math.abs(pose.pos[0] - overview.pos[0]) + Math.abs(pose.pos[1] - overview.pos[1]) + Math.abs(pose.pos[2] - overview.pos[2])).toBeLessThan(0.02);
  // calm: no frame moved the camera more than a few hundredths of a unit (a pawn crosses the board in seconds)
  expect(worst).toBeLessThan(0.09);
});

test("with the camera leaned on any field the dice stay on screen between the banner and the player tags", () => {
  const checks = [poseAt(0, 0, 0), ...FIELDS.map((f) => poseAt(1, f.x, f.z))];
  for (const pose of checks) {
    for (const [dx, dz] of [DICE_AT.number, DICE_AT.permit]) {
      // the top of the die, and the bottom of its front edge (the tags' rounded corners leave a sliver of room)
      for (const [y, z, limit] of [[DIE_SIZE, dz, HUD_BAND.bottom], [0, dz + DIE_SIZE / 2, HUD_BAND.bottom + 0.012]]) {
        const p = project(pose, dx, y, z);
        expect(p.x).toBeGreaterThan(0.05);
        expect(p.x).toBeLessThan(0.95);
        expect(p.y).toBeGreaterThan(HUD_BAND.top);
        expect(p.y).toBeLessThan(limit);
      }
    }
  }
});

test("the overview shows the whole road, and the plaza is a small pad under it", () => {
  for (const f of FIELDS) {
    const p = project(overview, f.x, 0, f.z);
    expect(p.x).toBeGreaterThan(0);
    expect(p.x).toBeLessThan(1);
    expect(p.y).toBeGreaterThan(HUD_BAND.top);
    expect(p.y).toBeLessThan(HUD_BAND.bottom);
  }
  const a = project(overview, PLAZA.x0, 0, PLAZA.z0), b = project(overview, PLAZA.x1, 0, PLAZA.z0), c = project(overview, PLAZA.x1, 0, PLAZA.z1);
  expect(b.x - a.x).toBeLessThan(0.4);
  // its near edge is behind the tags or off the screen, its far edge in the bottom quarter
  expect(a.y).toBeGreaterThan(0.74);
  expect(c.y - a.y).toBeLessThan(0.2);
});

test("no pawn walks behind the die's result pill, at the overview or leaned on any field", () => {
  for (const pose of [poseAt(0, 0, 0), ...FIELDS.map((f) => poseAt(1, f.x, f.z))]) {
    FIELDS.forEach((f, i) => {
      for (const y of [0, 1.25]) {   // the pawn's foot and its head
        const p = project(pose, f.x, y, f.z);
        const inside = p.x > RESULT_PILL.left && p.x < RESULT_PILL.right && p.y > RESULT_PILL.top && p.y < RESULT_PILL.bottom;
        expect(inside, `field ${i} at height ${y}`).toBe(false);
      }
    });
  }
});
