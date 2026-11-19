// Run with `bun test` in crates/mediagram-game/client. Pins what the TV engine looks up by name in the shipped
// Kopfnuss stages (public/kopfnuss/*.glb, from tools/kopfnuss-arena): the baked sets, the six stage cameras,
// every spot the cast stands on, and the effect tags the shaders are chosen by.
import { expect, test } from "bun:test";
import { EFFECTS } from "../src/tv/kopfnuss/arena-fx";
import { ARENA_SET } from "../src/tv/kopfnuss/arena-view";

interface Node {
  name?: string;
  mesh?: number;
  camera?: number;
  children?: number[];
  extras?: { fx?: string; color?: string };
}
interface Gltf {
  nodes: Node[];
  materials: { name?: string; emissiveTexture?: unknown }[];
  images?: unknown[];
}
const DIR = new URL("../public/kopfnuss/", import.meta.url).pathname;
const range = (n: number) => Array.from({ length: n }, (_, i) => i);
const SETS = [...new Set(Object.values(ARENA_SET))];
const pretty = (stage: string) => stage[0].toUpperCase() + stage.slice(1);

async function glb(name: string): Promise<Gltf> {
  const b = new Uint8Array(await Bun.file(`${DIR}${name}.glb`).arrayBuffer());
  return JSON.parse(new TextDecoder().decode(b.subarray(20, 20 + new DataView(b.buffer).getUint32(12, true))));
}

test("the arena carries every set and spot the engine looks up", async () => {
  const names = new Set((await glb("kopfnuss-arena")).nodes.map((n) => n.name));
  const wanted = [
    ...SETS.map((s) => `Set_${s}`),
    "Resolve_Stage",
    "Reveal_Stage",
    "WinnerSpot",
    ...range(6).flatMap((i) => [`CategorySpot_${i}`, `PlayerSpot_${i}`]),
    ...range(11).map((i) => `ClimbSpot_${i}`),
  ];
  expect(wanted.filter((n) => !names.has(n))).toEqual([]);
});

test("every stage has a camera node that is a real camera", async () => {
  const { nodes } = await glb("kopfnuss-arena");
  const missing = Object.keys(ARENA_SET).filter((stage) => {
    const node = nodes.find((n) => n.name === `Camera_${pretty(stage)}`);
    return typeof node?.camera !== "number";
  });
  expect(missing).toEqual([]);
});

test("every mesh hangs under a set or a stage group, so hiding the group hides it", async () => {
  const { nodes } = await glb("kopfnuss-arena");
  const parent = new Map<number, number>();
  nodes.forEach((n, i) => n.children?.forEach((c) => parent.set(c, i)));
  const grouped = (i: number): boolean => {
    for (let at: number | undefined = i; at !== undefined; at = parent.get(at)) {
      if (/^Set_|_Stage$/.test(nodes[at].name ?? "")) return true;
    }
    return false;
  };
  const stray = nodes.filter((n, i) => typeof n.mesh === "number" && !grouped(i)).map((n) => n.name);
  expect(stray).toEqual([]);
});

test("each set ships as one baked picture and nothing of its source pieces", async () => {
  const { materials, images } = await glb("kopfnuss-arena");
  expect(materials.map((m) => m.name).sort()).toEqual(SETS.map((s) => `KN_Baked_${s}`).sort());
  expect(materials.every((m) => m.emissiveTexture)).toBe(true);
  expect(images?.length).toBe(SETS.length);
});

test("every effect mesh names an effect the engine has a shader for, and its colour", async () => {
  const tagged = (await glb("kopfnuss-arena")).nodes.filter((n) => n.extras?.fx);
  expect(tagged.length).toBeGreaterThan(20);
  expect(tagged.filter((n) => !(n.extras!.fx! in EFFECTS) || !/^#[0-9a-f]{6}$/.test(n.extras!.color ?? ""))).toEqual([]);
});

test("the showmaster has the jaw the engine opens", async () => {
  const names = (await glb("showmaster")).nodes.map((n) => n.name);
  expect(names).toContain("Showmaster");
  expect(names).toContain("WalnutJaw");
});
