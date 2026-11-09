// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import * as THREE from "three";
import { FRAME, GATE_EDGE, Grounds, inset } from "../src/tv/grand-allee/grand-allee-plots";
import { sceneOf, testBoard } from "./grand-allee-fixture";

const BLUE = "#3d7be8";
const square = [{ x: 0, z: 0 }, { x: 10, z: 0 }, { x: 10, z: 10 }, { x: 0, z: 10 }];
const vertex = (g: Grounds, j: number) => new THREE.Vector3().fromBufferAttribute(g.frames.geometry.getAttribute("position") as THREE.BufferAttribute, j);

test("a frame runs a width inside its polygon, whichever way the polygon winds", () => {
  const inner = [{ x: 1, z: 1 }, { x: 9, z: 1 }, { x: 9, z: 9 }, { x: 1, z: 9 }];
  inset(square, 1).forEach((p, i) => expect([p.x, p.z]).toEqual([expect.closeTo(inner[i].x), expect.closeTo(inner[i].z)] as unknown as number[]));
  inset([...square].reverse(), 1).forEach((p, i) => expect([p.x, p.z]).toEqual([expect.closeTo(inner[3 - i].x), expect.closeTo(inner[3 - i].z)] as unknown as number[]));
});

test("an owned plot's frame lies along its land, a free one's under the board", async () => {
  const b = await testBoard(), g = new Grounds(new THREE.Scene(), b);
  g.update(sceneOf({ estates: [{ hotel: 7, owner: 1, levels: 0 }] }), () => BLUE, new Map(), new Map(), 0);
  const [first] = g.ranges[7];
  b.plots[7].land.forEach((q, i) => {
    const v = vertex(g, first + 2 * i);
    expect([v.x, v.y, v.z]).toEqual([expect.closeTo(q.x), expect.closeTo(FRAME.y), expect.closeTo(q.z)] as unknown as number[]);
  });
  expect(vertex(g, g.ranges[6][0]).y).toBe(FRAME.hidden);
});

test("an entrance's gate stands at the road's edge on its hotel's side, also on a curved field", async () => {
  const b = await testBoard(), g = new Grounds(new THREE.Scene(), b), m = new THREE.Matrix4(), at = new THREE.Vector3();
  // field 13 is Skyline's (park, right of the walk), field 5 Lagune's (outside the loop, left of it)
  g.update(sceneOf({ entrances: [{ field: 13, hotel: 7 }, { field: 5, hotel: 0 }] }), () => BLUE, new Map(), new Map(), 0);
  [[13, 7], [5, 0]].forEach(([field, hotel], i) => {
    const f = b.fields[field], right = Math.sin(f.rot) * (at.setFromMatrixPosition((g.gates.getMatrixAt(i, m), m)).x - f.x) + Math.cos(f.rot) * (at.z - f.z);
    expect(right).toBeCloseTo(GATE_EDGE * b.plots[hotel].side);
  });
});

test("while the scene stands and nothing flashes, nothing is uploaded again", async () => {
  const b = await testBoard(), g = new Grounds(new THREE.Scene(), b), s = sceneOf({ estates: [{ hotel: 2, owner: 0, levels: 1 }] });
  const pulses = new Map([[2, 0]]), version = () => g.frames.geometry.getAttribute("color").version;
  g.update(s, () => BLUE, pulses, new Map(), 0);
  const flashing = version();
  g.update(s, () => BLUE, pulses, new Map(), 0.5);
  expect(version()).toBeGreaterThan(flashing);
  g.update(s, () => BLUE, pulses, new Map(), 10); // the flash is over: one last pass
  const resting = version();
  g.update(s, () => BLUE, pulses, new Map(), 11);
  expect(version()).toBe(resting);
});
