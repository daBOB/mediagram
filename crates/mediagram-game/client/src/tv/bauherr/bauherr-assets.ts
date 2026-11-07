// Fetches the baked town: every image and the geometry file at once. Nothing is painted here. The scene can be built, and its
// shaders compiled, on textures that are still empty shells: each fills in, and goes up to the GPU, as its image arrives, and
// `ready` says when all of them have. The big images are KTX2 (Basis ETC1S with mipmaps): a worker turns them into
// GPU-compressed textures, so the box neither decodes millions of pixels on its main thread nor builds mipmaps. The small
// ones (dice, canopy, shadows, the far land) are plain WebP.

import * as THREE from "three";
import { KTX2Loader } from "three/examples/jsm/loaders/KTX2Loader.js";
import { mark } from "./bauherr-timing";
import { parseWorld, type WorldRec } from "./bauherr-data";

const DIR = "/bauherr/";

export interface Assets {
  rec: WorldRec;
  buf: ArrayBuffer;
  ground: THREE.Texture;
  outer: THREE.Texture;
  town: THREE.Texture;
  terrain: THREE.Texture;
  canopy: THREE.Texture;
  ivory: THREE.Texture;
  oak: THREE.Texture;
  hotels: THREE.Texture[];
  shadows: THREE.Texture[];
}

const image = (name: string) =>
  new Promise<HTMLImageElement>((ok, fail) => {
    const i = new Image();
    i.onload = () => {
      mark(`img:${name}`);
      ok(i);
    };
    i.onerror = () => fail(new Error(`image ${name}`));
    i.src = `${DIR}${name}.webp`;
  });
const get = async (name: string) => {
  const r = await fetch(DIR + name);
  if (!r.ok) throw new Error(`${name}: ${r.status}`);
  return r;
};

/**
 * `renderer` tells the KTX2 loader which compressed formats the GPU takes; `hotelStages`: how many stages each hotel has in
 * the rules, checked against the baked file. Resolves with the geometry and the textures as empty shells once the geometry is
 * in; `ready` resolves when every texture holds its image and is on the GPU (and rejects if one does not load).
 */
export async function startAssets(renderer: THREE.WebGLRenderer, hotelStages: number[]): Promise<{ assets: Assets; ready: Promise<void> }> {
  const world = Promise.all([get("world.json").then((r) => r.json()), get("world.bin").then((r) => r.arrayBuffer())]);
  const ktx = new KTX2Loader().setTranscoderPath(`${DIR}basis/`).detectSupport(renderer);
  const loads: Promise<unknown>[] = [];

  // sRGB, mipmapped; the bake's uvs expect flipped images (three's default for an <img>)
  const plain = (name: string, anisotropy: number, mipmaps = true) => {
    const t = new THREE.Texture();
    t.colorSpace = THREE.SRGBColorSpace;
    t.anisotropy = anisotropy;
    if (!mipmaps) {
      t.generateMipmaps = false;
      t.minFilter = THREE.LinearFilter;
    }
    loads.push(image(name).then((img) => {
      t.image = img;
      t.needsUpdate = true;
      renderer.initTexture(t);
    }));
    return t;
  };
  // the transcoded texture's data is copied into the shell the scene already holds
  const compressed = (name: string, anisotropy: number) => {
    const t = new THREE.CompressedTexture([], 4, 4);
    loads.push(ktx.loadAsync(`${DIR}${name}.ktx2`).then((src) => {
      t.copy(src);
      t.anisotropy = anisotropy;
      t.needsUpdate = true;
      renderer.initTexture(t);
      mark(`ktx:${name}`);
    }));
    return t;
  };

  const textures = {
    ground: compressed("ground", 8),
    outer: plain("outer", 2),
    town: compressed("town", 4),
    terrain: compressed("terrain", 4),
    canopy: plain("canopy", 2),
    ivory: plain("die-ivory", 4),
    oak: plain("die-oak", 4),
    hotels: hotelStages.map((_, i) => compressed(`hotel-${i}`, 4)),
    shadows: hotelStages.map((_, i) => plain(`hotel-${i}-shadow`, 1, false)),
  };
  const [json, buf] = await world;
  const assets: Assets = { rec: parseWorld(json, hotelStages), buf, ...textures };
  // the transcoder workers are not needed once the textures are in, or when one failed
  const ready = Promise.all(loads).then(() => undefined).finally(() => ktx.dispose());
  ready.catch(() => undefined); // whoever awaits it gets the error; this one only keeps it from being reported twice
  return { assets, ready };
}
