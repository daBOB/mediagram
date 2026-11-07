// Fetches the baked town: the geometry file and every image, in parallel. The TV decodes images; nothing is painted here.

import * as THREE from "three";
import { parseWorld, type WorldRec } from "./bauherr-data";

const DIR = "/bauherr/";

export interface Assets {
  rec: WorldRec;
  buf: ArrayBuffer;
  ground: THREE.Texture;
  outer: THREE.Texture;
  town: THREE.Texture;
  canopy: THREE.Texture;
  ivory: THREE.Texture;
  oak: THREE.Texture;
  hotels: THREE.Texture[];
  shadows: THREE.Texture[];
}

const image = (name: string) =>
  new Promise<HTMLImageElement>((ok, fail) => {
    const i = new Image();
    i.onload = () => ok(i);
    i.onerror = () => fail(new Error(`image ${name}`));
    i.src = `${DIR}${name}.webp`;
  });
// sRGB, mipmapped; the bake's uvs expect flipped images (three's default for an <img>)
const texture = async (name: string, anisotropy: number, mipmaps = true) => {
  const t = new THREE.Texture(await image(name));
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = anisotropy;
  if (!mipmaps) {
    t.generateMipmaps = false;
    t.minFilter = THREE.LinearFilter;
  }
  t.needsUpdate = true;
  return t;
};
const get = async (name: string) => {
  const r = await fetch(DIR + name);
  if (!r.ok) throw new Error(`${name}: ${r.status}`);
  return r;
};

/** `hotelStages`: how many stages each hotel has in the rules, checked against the baked file. */
export async function loadAssets(hotelStages: number[]): Promise<Assets> {
  const [json, buf, ground, outer, town, canopy, ivory, oak, hotels, shadows] = await Promise.all([
    get("world.json").then((r) => r.json()),
    get("world.bin").then((r) => r.arrayBuffer()),
    texture("ground", 8),
    texture("outer", 2),
    texture("town", 4),
    texture("canopy", 2),
    texture("die-ivory", 4),
    texture("die-oak", 4),
    Promise.all(hotelStages.map((_, i) => texture(`hotel-${i}`, 4))),
    Promise.all(hotelStages.map((_, i) => texture(`hotel-${i}-shadow`, 1, false))),
  ]);
  return { rec: parseWorld(json, hotelStages), buf, ground, outer, town, canopy, ivory, oak, hotels, shadows };
}
