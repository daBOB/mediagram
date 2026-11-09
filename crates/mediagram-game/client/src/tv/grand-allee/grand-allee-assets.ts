// Loads what the board is made of, side by side: the layout, the ten hotels (glTF, unlit, light baked in), and 4b's board and dice
// when they are there. Until then the board is drawn from the layout and the dice are Bauherr's painted ones. The hotel and board
// atlases are KTX2 (Basis ETC1S with mipmaps): a worker turns them into GPU-compressed textures, an eighth of RGBA's memory, and
// the box builds no mipmaps; the dice's small atlases are WebP.

import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { KTX2Loader } from "three/examples/jsm/loaders/KTX2Loader.js";
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

/** `renderer` tells the KTX2 loader which compressed formats the GPU takes. */
export async function loadAssets(renderer: THREE.WebGLRenderer): Promise<Assets> {
  // Bauherr's transcoder files and setup (bauherr-assets.ts): both move to a shared place before Bauherr is retired
  const ktx = new KTX2Loader().setTranscoderPath("/bauherr/basis/").detectSupport(renderer);
  const loader = new GLTFLoader().setKTX2Loader(ktx), images = new THREE.TextureLoader();
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
  try {
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
  } finally {
    ktx.dispose(); // the transcoder workers are not needed once the models are in, or when one failed
  }
}
