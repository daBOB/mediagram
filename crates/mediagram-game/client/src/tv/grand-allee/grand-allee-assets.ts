// Loads what the board is made of, side by side: the layout, the ten hotels (glTF, unlit, light baked in), the two dice and the
// board, which is drawn from the layout while board.glb is missing. The hotel and board atlases are KTX2 (Basis ETC1S with
// mipmaps): a worker turns them into GPU-compressed textures, an eighth of RGBA's memory, and the box builds no mipmaps; the
// dice's small atlases are WebP.

import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { KTX2Loader } from "three/examples/jsm/loaders/KTX2Loader.js";
import { mark } from "./grand-allee-timing";
import { loadLayout, type Board } from "./grand-allee-layout";

const DIR = "/grand-allee/";

export interface Assets {
  board: Board;
  /** By hotel id. */
  hotels: THREE.Object3D[];
  boardModel: THREE.Object3D | null;
  dice: { number: THREE.Object3D; permit: THREE.Object3D };
}

/** `renderer` tells the KTX2 loader which compressed formats the GPU takes. */
export async function loadAssets(renderer: THREE.WebGLRenderer): Promise<Assets> {
  // three's Basis transcoder (examples/jsm/libs/basis), copied as it is
  const ktx = new KTX2Loader().setTranscoderPath("/basis/").detectSupport(renderer);
  const loader = new GLTFLoader().setKTX2Loader(ktx);
  const glb = (name: string) =>
    loader.loadAsync(`${DIR}${name}.glb`).then((g) => {
      mark(`glb:${name}`);
      return g.scene;
    });
  try {
    const board = await loadLayout(`${DIR}layout.json`);
    const [hotels, boardModel, number, permit] = await Promise.all([
      Promise.all(board.plots.map((plot) => glb(plot.name))),
      // a missing board.glb, or a dev server's fallback page, fails to load or parse: the layout's stand-in is drawn instead
      glb("board").catch(() => null),
      glb("die-number"),
      glb("die-permit"),
    ]);
    return { board, hotels, boardModel, dice: { number, permit } };
  } finally {
    ktx.dispose(); // the transcoder workers are not needed once the models are in, or when one failed
  }
}
