// Run with `bun test` in crates/mediagram-game/client. Pins what the TV relies on in the shipped models
// (public/grand-allee/*.glb, from tools/grand-allee-models): unlit materials, one stage_<n> per stage of the rules, the dice's
// face_* empties, and the atlas budgets in that tool's README.
import { expect, test } from "bun:test";
import * as THREE from "three";
import { FACE_UP } from "../src/tv/bauherr/bauherr-dice";
import { faceTurn, faceValue } from "../src/tv/grand-allee/grand-allee-dice";
import { boardData, testBoard } from "./grand-allee-fixture";

interface Gltf {
  nodes: { name?: string; translation?: number[] }[];
  materials: { extensions?: Record<string, unknown> }[];
  images?: { bufferView: number }[];
  bufferViews: { byteOffset?: number; byteLength: number }[];
}
const DIR = new URL("../public/grand-allee/", import.meta.url).pathname;

async function glb(name: string): Promise<{ json: Gltf; bin: Uint8Array }> {
  const b = new Uint8Array(await Bun.file(`${DIR}${name}.glb`).arrayBuffer()), n = new DataView(b.buffer).getUint32(12, true);
  return { json: JSON.parse(new TextDecoder().decode(b.subarray(20, 20 + n))), bin: b.subarray(28 + n) };
}
/** Width and height of a WebP: extended (VP8X), lossless (VP8L) or lossy (VP8). */
function webpSize(b: Uint8Array): [number, number] {
  const v = new DataView(b.buffer, b.byteOffset, b.byteLength), tag = new TextDecoder().decode(b.subarray(12, 16));
  if (tag === "VP8X") return [1 + (v.getUint32(24, true) & 0xffffff), 1 + (v.getUint32(27, true) & 0xffffff)];
  if (tag === "VP8L") return [1 + (v.getUint32(21, true) & 0x3fff), 1 + ((v.getUint32(21, true) >> 14) & 0x3fff)];
  return [v.getUint16(26, true) & 0x3fff, v.getUint16(28, true) & 0x3fff];
}
const atlases = ({ json, bin }: { json: Gltf; bin: Uint8Array }) =>
  (json.images ?? []).map((i) => {
    const bv = json.bufferViews[i.bufferView], at = bv.byteOffset ?? 0;
    return webpSize(bin.subarray(at, at + bv.byteLength));
  });
const unlit = (json: Gltf) => json.materials.every((m) => "KHR_materials_unlit" in (m.extensions ?? {}));

test("every hotel is unlit, has a stage_<n> with its body for each stage the rules give it and no more, within its atlas budget", async () => {
  const rules = boardData();
  for (const plot of (await testBoard()).plots) {
    const g = await glb(plot.name), names = new Set(g.json.nodes.map((n) => n.name)), stages = rules.hotels[plot.hotel].stages.length;
    expect([plot.name, unlit(g.json)]).toEqual([plot.name, true]);
    for (let k = 1; k <= stages; k++) expect([plot.name, k, names.has(`stage_${k}`), names.has(`stage_${k}_body`)]).toEqual([plot.name, k, true, true]);
    expect([plot.name, names.has(`stage_${stages + 1}`)]).toEqual([plot.name, false]);
    // 1024² for the outer plots, 2048² for the wide park plots
    for (const [w, h] of atlases(g)) expect([plot.name, Math.max(w, h) <= (plot.hotel < 6 ? 1024 : 2048)]).toEqual([plot.name, true]);
  }
});

test("the board is unlit and within its atlas budget: a 2048 x 1536 ground and two 1024² atlases (parts, town)", async () => {
  const g = await glb("board"), sizes = atlases(g);
  expect(unlit(g.json)).toBe(true);
  expect(Math.max(...sizes.flat())).toBeLessThanOrEqual(2048);
  expect(sizes.reduce((a, [w, h]) => a + w * h, 0)).toBeLessThanOrEqual(2048 * 1536 + 2 * 1024 * 1024);
});

const DICE = { "die-number": ["1", "2", "3", "4", "5", "6"], "die-permit": ["approved", "approved", "approved", "double", "free", "rejected"] };
/** FACE_UP's order of box faces. */
const BOX_FACES = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];

test("each die is unlit, 256² at most, and marks every face with face_<value> (the permit's values as the protocol names them)", async () => {
  for (const [name, values] of Object.entries(DICE)) {
    const g = await glb(name), faces = g.json.nodes.flatMap((n) => faceValue(n.name ?? "") ?? []);
    expect([name, unlit(g.json), faces.sort()]).toEqual([name, true, values]);
    for (const [w, h] of atlases(g)) expect([name, Math.max(w, h)]).toEqual([name, Math.min(256, Math.max(w, h))]);
  }
});

test("every face of both dice is turned up the way FACE_UP turns that box face, the one pointing down included", async () => {
  for (const name of Object.keys(DICE)) {
    for (const node of (await glb(name)).json.nodes.filter((n) => faceValue(n.name ?? "") !== null)) {
      const dir = new THREE.Vector3(...(node.translation ?? [0, 0, 0])).normalize(), q = faceTurn(dir);
      const k = BOX_FACES.findIndex((f) => new THREE.Vector3(...f).dot(dir) > 0.99);
      expect([node.name, k >= 0]).toEqual([node.name, true]);
      expect(q.angleTo(new THREE.Quaternion().setFromEuler(new THREE.Euler(...FACE_UP[k])))).toBeCloseTo(0);
      expect(dir.applyQuaternion(q).y).toBeCloseTo(1);
    }
  }
});
