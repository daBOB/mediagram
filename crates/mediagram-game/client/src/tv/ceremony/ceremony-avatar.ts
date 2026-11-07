// One contender on the podium: the flat figure (or a pawn) that stands there
// from the first frame, and the moving 3D avatar that replaces it once loaded.

import * as THREE from "three";
import type { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";

/** What each place does after the avatar appears, then the idle. */
const INTRO = ["victory", "Cheer_With_One_Hand_Up", "Shrug"];
/** A calm idle that keeps facing the room (hips within -3..21 degrees for every avatar); Meshy's plain "Idle"
 *  wanders up to 81 degrees and left the third place standing side-on or with its back to the room. */
const IDLE = "Idle_15";
const JUMP = "Happy_Jump";
const FADE = 0.3;

/** Plays the place's clip, then the idle; the winner jumps now and then. A missing clip is skipped. */
export class Performer {
  readonly mixer: THREE.AnimationMixer;
  private readonly idle: THREE.AnimationAction | null;
  private readonly jump: THREE.AnimationAction | null;
  private current: THREE.AnimationAction | null = null;
  private nextJump = 0;

  constructor(root: THREE.Object3D, clips: THREE.AnimationClip[], place: number, t: number) {
    this.mixer = new THREE.AnimationMixer(root);
    const action = (name: string) => {
      const clip = THREE.AnimationClip.findByName(clips, name);
      return clip ? this.mixer.clipAction(clip) : null;
    };
    this.idle = action(IDLE);
    this.jump = place === 0 ? action(JUMP) : null;
    this.mixer.addEventListener("finished", (e) => {
      if (e.action === this.current && this.current !== this.idle) this.switchTo(this.idle, false);
    });
    this.nextJump = t + 6 + Math.random() * 2;
    const intro = action(INTRO[place]);
    this.switchTo(intro ?? this.idle, intro !== null);
  }

  private switchTo(next: THREE.AnimationAction | null, once: boolean) {
    if (!next) return;
    next.reset();
    next.setLoop(once ? THREE.LoopOnce : THREE.LoopRepeat, once ? 1 : Infinity);
    next.clampWhenFinished = once;
    if (this.current && this.current !== next) next.crossFadeFrom(this.current, FADE, false);
    next.play();
    this.current = next;
  }

  update(dt: number, t: number) {
    if (this.jump && t >= this.nextJump && this.current === this.idle) {
      this.nextJump = t + 6 + Math.random() * 2;
      this.switchTo(this.jump, true);
    }
    this.mixer.update(dt);
  }

  dispose() {
    this.mixer.stopAllAction();
    this.mixer.uncacheRoot(this.mixer.getRoot());
  }
}

/** The loaded model with each material replaced by `dress(old)`, scaled to `height` and standing on y = 0. */
export async function loadAvatar(loader: GLTFLoader, id: string, height: number, dress: (old: THREE.MeshStandardMaterial) => THREE.Material) {
  const gltf = await loader.loadAsync(`/game-avatars/${id}.glb`);
  const root = gltf.scene;
  root.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    mesh.frustumCulled = false; // a jump leaves the bind-pose bounds
    const old = mesh.material as THREE.MeshStandardMaterial;
    mesh.material = dress(old);
    if (mesh.material !== old) old.dispose();
  });
  root.updateMatrixWorld(true);
  const box = new THREE.Box3().setFromObject(root);
  const size = box.getSize(new THREE.Vector3()), mid = box.getCenter(new THREE.Vector3());
  if (!(size.y > 0)) throw new Error(`avatar ${id} has no height`);
  const s = height / size.y;
  root.scale.setScalar(s);
  root.position.set(-mid.x * s, -box.min.y * s, -mid.z * s);
  const holder = new THREE.Group().add(root);
  return { holder, root, clips: gltf.animations };
}

/** Loads the flat picture as a texture; null when it is missing. */
export const loadFigure = (url: string) =>
  new THREE.TextureLoader().loadAsync(url).then(
    (t) => ((t.colorSpace = THREE.SRGBColorSpace), t),
    () => null,
  );

/** The stand-in: the flat figure as a sprite, or a pawn in the player's colour without one. */
export function flatFigure(map: THREE.Texture | null, color: string, height: number): THREE.Object3D {
  if (map) {
    const img = map.image as { width: number; height: number };
    const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map, alphaTest: 0.04 }));
    sprite.scale.set((height * img.width) / img.height, height, 1);
    sprite.center.set(0.5, 0);
    return sprite;
  }
  const mat = new THREE.MeshLambertMaterial({ color });
  const pawn = new THREE.Group();
  const body = new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.55, height * 0.65, 24), mat);
  body.position.y = height * 0.325;
  const head = new THREE.Mesh(new THREE.SphereGeometry(0.4, 24, 16), mat);
  head.position.y = height * 0.8;
  return pawn.add(body, head);
}
