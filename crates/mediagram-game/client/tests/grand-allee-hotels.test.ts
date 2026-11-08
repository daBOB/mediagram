// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import * as THREE from "three";
import { buildHotel, prefix, stepHotel } from "../src/tv/grand-allee/grand-allee-hotels";
import type { Plot } from "../src/tv/grand-allee/grand-allee-layout";
import { TIMES } from "../src/tv/grand-allee/grand-allee-look";

const plot: Plot = { hotel: 0, name: "test", x: 10, z: -5, w: 14, d: 11.2, rot: 0, scale: 0.7 };
const BOX = 36; // indices of a box: 6 faces, 2 triangles each
const OWNER = "#3d7be8";

/** A hotel as its GLB holds it: stage_<n> empties 4 m apart, each with a body and, where `tints` says so, a tint. */
function model(tints: boolean[]) {
  const root = new THREE.Group(), body = new THREE.MeshBasicMaterial(), tint = new THREE.MeshBasicMaterial();
  tints.forEach((hasTint, i) => {
    const stage = new THREE.Group(), b = new THREE.Mesh(new THREE.BoxGeometry(10, 4, 8).translate(0, 2, 0), body);
    stage.name = `stage_${i + 1}`;
    stage.position.y = i * 4;
    b.name = `stage_${i + 1}_body`;
    stage.add(b);
    if (hasTint) {
      const t = new THREE.Mesh(new THREE.BoxGeometry(2, 1, 1), tint);
      t.name = `stage_${i + 1}_tint`;
      stage.add(t);
    }
    root.add(stage);
  });
  return root;
}
const hotel = (tints = [true, false, true]) => buildHotel(new THREE.Scene(), model(tints), plot, tints.length);

test("stages 1..levels are one draw range from the start", () => {
  const ranges: [number, number][] = [[0, 36], [36, 36], [72, 0]];
  expect(prefix(ranges, 0)).toEqual([0, 0]);
  expect(prefix(ranges, 2)).toEqual([0, 72]);
  expect(prefix(ranges, 9)).toEqual([0, 72]);
});

test("a free plot shows nothing", () => {
  const h = hotel();
  stepHotel(h, 0, null, 0);
  expect(h.body!.mesh.visible).toBe(false);
  expect(h.tint!.mesh.visible).toBe(false);
});

test("an owned hotel shows the stages built, tints only where the model has them, and nothing rising at first sight", () => {
  const h = hotel();
  stepHotel(h, 2, OWNER, 0);
  expect(h.body!.mesh.geometry.drawRange).toEqual({ start: 0, count: 2 * BOX });
  expect(h.tint!.mesh.geometry.drawRange).toEqual({ start: 0, count: BOX }); // stage 2 has no tint
  expect(h.body!.rise.visible).toBe(false);
});

test("the tint takes the owner's colour; the body stays as baked", () => {
  const h = hotel();
  stepHotel(h, 1, OWNER, 0);
  expect(h.tint!.mesh.material.color.getHex()).toBe(new THREE.Color(OWNER).getHex());
  expect(h.body!.mesh.material.color.getHex()).toBe(0xffffff);
  stepHotel(h, 1, "#e4572e", 1);
  expect(h.tint!.mesh.material.color.getHex()).toBe(new THREE.Color("#e4572e").getHex());
  expect(h.tint!.rise.material.color.getHex()).toBe(new THREE.Color("#e4572e").getHex());
});

test("a new stage rises inside its clipping plane, then stands", () => {
  const h = hotel();
  stepHotel(h, 1, OWNER, 0);
  stepHotel(h, 2, OWNER, 1);
  expect(h.rising?.stage).toBe(1);
  expect(h.body!.rise.visible).toBe(true);
  expect(h.body!.rise.geometry.drawRange).toEqual({ start: BOX, count: BOX });
  expect(h.body!.mesh.geometry.drawRange.count).toBe(BOX);
  const [lo, hi] = h.heights[1];
  expect(h.clip.constant).toBeCloseTo(lo);
  stepHotel(h, 2, OWNER, 1 + TIMES.rise / 2);
  expect(h.clip.constant).toBeGreaterThan(lo);
  expect(h.clip.constant).toBeLessThan(hi);
  stepHotel(h, 2, OWNER, 1 + TIMES.rise + 0.01);
  expect(h.rising).toBeNull();
  expect(h.body!.rise.visible).toBe(false);
  expect(h.body!.mesh.geometry.drawRange.count).toBe(2 * BOX);
});

test("sold back to the bank, the hotel is gone at once", () => {
  const h = hotel();
  stepHotel(h, 3, OWNER, 0);
  stepHotel(h, 0, null, 1);
  expect(h.body!.mesh.visible).toBe(false);
  expect(h.rising).toBeNull();
});

test("the hotel stands on its plot at its scale", () => {
  const h = hotel([true]);
  const box = new THREE.Box3().setFromBufferAttribute(h.body!.mesh.geometry.getAttribute("position") as THREE.BufferAttribute);
  expect((box.min.x + box.max.x) / 2).toBeCloseTo(plot.x);
  expect((box.min.z + box.max.z) / 2).toBeCloseTo(plot.z);
  expect(box.max.y).toBeCloseTo(4 * plot.scale);
  expect(h.heights[0]).toEqual([expect.closeTo(-0.35) as unknown as number, expect.closeTo(2.8) as unknown as number]);
});
