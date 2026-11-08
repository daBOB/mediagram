// Loads what the board is made of, side by side: the layout, the ten hotels (glTF, unlit, light baked in; three decodes their
// WebP atlases off the main thread), and 4b's board and dice when they are there. Until then the board is drawn from the layout
// and the dice are Bauherr's painted ones.

import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { mark } from "../bauherr/bauherr-timing";
import { loadLayout, type Board } from "./grand-allee-layout";

const DIR = "/grand-allee/";

export interface Assets {
  board: Board;
  /** By hotel id. */
  hotels: THREE.Object3D[];
  boardModel: THREE.Object3D | null;
  dice: { number: THREE.Object3D | null; permit: THREE.Object3D | null };
  /** Bauherr's die images, for a die without its model. */
  ivory: THREE.Texture | null;
  oak: THREE.Texture | null;
}

export async function loadAssets(): Promise<Assets> {
  const loader = new GLTFLoader(), images = new THREE.TextureLoader();
  const glb = (name: string) =>
    loader.loadAsync(`${DIR}${name}.glb`).then((g) => {
      mark(`glb:${name}`);
      return g.scene;
    });
  // 4b's models: drawn without until they exist (a missing file, or a dev server's fallback page, fails to load or parse)
  const optional = (name: string) => glb(name).catch(() => null);
  const painted = async (model: Promise<THREE.Object3D | null>, image: string) => {
    if (await model) return null;
    const t = await images.loadAsync(`/bauherr/${image}.webp`);
    t.colorSpace = THREE.SRGBColorSpace;
    return t;
  };
  const board = await loadLayout(`${DIR}layout.json`);
  const number = optional("die-number"), permit = optional("die-permit");
  const [hotels, boardModel, n, p, ivory, oak] = await Promise.all([
    Promise.all(board.plots.map((plot) => glb(plot.name))),
    optional("board"),
    number,
    permit,
    painted(number, "die-ivory"),
    painted(permit, "die-oak"),
  ]);
  return { board, hotels, boardModel, dice: { number: n, permit: p }, ivory, oak };
}
