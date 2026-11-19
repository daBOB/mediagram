import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { KTX2Loader } from "three/addons/loaders/KTX2Loader.js";
import { MeshoptDecoder } from "three/addons/libs/meshopt_decoder.module.js";
import { avatarFigureUrl } from "../../ui/Token";
import { Performer, flatFigure, loadAvatar, loadFigure } from "../ceremony/ceremony-avatar";
import type { StudioLook } from "../ceremony/ceremony-studio-look";
import { blobShadow, shadowTexture } from "./arena-fx";
import type { ArenaView } from "./arena-view";

type CastMember = { holder: THREE.Group; performer: Performer | null; avatar: string | null };

/** Frees a subtree's geometry and materials; with `textures` their pictures too, for when nothing will draw them again. */
export function disposeTree(root: THREE.Object3D, textures = false) {
  root.traverse((o) => {
    if ((o as THREE.Sprite).isSprite) {
      const sprite = o as THREE.Sprite;
      sprite.material.map?.dispose();
      sprite.material.dispose();
      return;
    }
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    mesh.geometry.dispose();
    for (const mat of Array.isArray(mesh.material) ? mesh.material : [mesh.material]) {
      if (textures) {
        const pictured = mat as THREE.MeshMatcapMaterial;
        pictured.map?.dispose();
        pictured.normalMap?.dispose();
      }
      mat.dispose();
    }
  });
}

/** The players on stage: loads each one's avatar, keeps the cast in step with the view, and stands every
 *  member on their spot of the set. */
export function arenaCast(scene: THREE.Scene, renderer: THREE.WebGLRenderer, dress: StudioLook["dress"]) {
  const ktx2 = new KTX2Loader().setTranscoderPath("/basis/").detectSupport(renderer);
  const avatars = new GLTFLoader().setKTX2Loader(ktx2).setMeshoptDecoder(MeshoptDecoder);
  const shade = shadowTexture();
  const cast = new Map<number, CastMember>();
  let castKey = "";
  let alive = true;

  const drop = (member: CastMember) => {
    member.performer?.dispose();
    member.holder.removeFromParent();
    disposeTree(member.holder);
  };

  /** Brings the cast in line with who the view shows; the fact shows the winner alone. */
  const sync = async (view: ArenaView) => {
    const desired = view.kind === "fact" ? view.players.filter((p) => p.id === view.winner) : view.players;
    const key = desired.map((p) => `${p.id}:${p.avatar}`).join(",");
    if (key === castKey) return;
    castKey = key;
    for (const [id, member] of cast) {
      if (!desired.some((p) => p.id === id)) {
        drop(member);
        cast.delete(id);
      }
    }
    await Promise.all(desired.map(async (p, i) => {
      const old = cast.get(p.id);
      if (old?.avatar === p.avatar) return;
      if (old) drop(old);
      const holder = new THREE.Group();
      holder.add(blobShadow(shade));
      const member: CastMember = { holder, performer: null, avatar: p.avatar };
      cast.set(p.id, member);
      scene.add(holder);
      try {
        if (!p.avatar) throw new Error("no avatar");
        const asset = await loadAvatar(avatars, p.avatar, 1.9, dress);
        if (!alive || cast.get(p.id) !== member) {
          disposeTree(asset.holder);
          return;
        }
        holder.add(asset.holder);
        member.performer = new Performer(asset.root, asset.clips, i, 0);
      } catch {
        const map = p.avatar ? await loadFigure(avatarFigureUrl(p.avatar)) : null;
        if (!alive || cast.get(p.id) !== member) return;
        holder.add(flatFigure(map, p.color, 1.9));
      }
    }));
  };

  /** Stands every member on their spot of `stage` for this view, turned to the camera, and moves them on by `dt`. */
  const place = (view: ArenaView, stage: THREE.Object3D, camera: THREE.Camera, dt: number, seconds: number) => {
    const point = (name: string, fallback: THREE.Vector3) => {
      const obj = stage.getObjectByName(name);
      return obj ? obj.getWorldPosition(new THREE.Vector3()) : fallback;
    };
    // climbers who share a tier stand side by side on it
    const tiers = new Map<number, number[]>();
    if (view.kind === "climb") for (const p of view.players) tiers.set(p.step, [...(tiers.get(p.step) ?? []), p.id]);
    for (const [id, member] of cast) {
      const index = view.players.findIndex((p) => p.id === id);
      const p = view.players[index];
      if (!p) continue;
      let pos: THREE.Vector3;
      if (view.kind === "fact") pos = point("WinnerSpot", new THREE.Vector3(0, 0.7, 6.2));
      else if (view.kind === "climb") {
        const peers = tiers.get(p.step) ?? [id];
        pos = point(`ClimbSpot_${p.step}`, new THREE.Vector3(0, 0.3 + p.step * 0.6, 9 - p.step * 1.7));
        pos.x += (peers.indexOf(id) - (peers.length - 1) / 2) * 0.58;
      } else if (view.kind === "pick" || view.kind === "resolve") pos = point(`CategorySpot_${index}`, new THREE.Vector3((index - 2.5) * 1.8, 0.1, -0.2));
      else pos = point(`PlayerSpot_${index}`, new THREE.Vector3((index - 2.5) * 1.6, 0.3, 7));
      member.holder.position.copy(pos);
      member.holder.rotation.y = Math.atan2(camera.position.x - pos.x, camera.position.z - pos.z);
      member.performer?.update(dt, seconds);
    }
  };

  /** Frees the cast and everything it put on the GPU. */
  const dispose = () => {
    alive = false;
    for (const member of cast.values()) {
      member.performer?.dispose();
      disposeTree(member.holder, true);
    }
    shade.dispose();
    ktx2.dispose();
  };

  return { sync, place, dispose };
}
