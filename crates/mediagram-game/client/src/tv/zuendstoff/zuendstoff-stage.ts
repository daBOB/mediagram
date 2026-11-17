// Entry of Zündstoff's lazily loaded chunk: a round felt table with a wooden rim in a dark room under a spot, the players'
// avatars in a circle on it, the bomb, the HUD and the sounds, on the shared TV stage. Table, light and camera A are the
// approved render's (plans/261115-2340-zuendstoff-3d/reports/circle-camera-a.jpg).

import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { MeshoptDecoder } from "three/examples/jsm/libs/meshopt_decoder.module.js";
import type { ZuendstoffScene } from "../../protocol/ZuendstoffScene";
import { loadAvatar } from "../ceremony/ceremony-avatar";
import { BOARD_SOUNDS } from "../table/board-sounds";
import { startStage, type Live, type StageHandle } from "../table/stage-host";
import { Bomb } from "./zuendstoff-bomb";
import { AVATAR_H, CIRCLE_R, buildCircle, disposeCircle, poseCircle, setOut, type Avatar } from "./zuendstoff-circle";
import { ZuendstoffHud } from "./zuendstoff-hud";
import { frameAt, zuendstoffCues, type Flight } from "./zuendstoff-motion";

const ROOM = "#07150f";
const TABLE_R = 7.2;
/** Camera A, the high overview the user picked. */
const CAMERA = { pos: new THREE.Vector3(0, 13.5, 14.5), at: new THREE.Vector3(0, 0.9, 0.4), fov: 34 };

function buildTable(scene: THREE.Scene) {
  scene.background = new THREE.Color(ROOM);
  scene.fog = new THREE.Fog(ROOM, 18, 34);
  const felt = new THREE.Mesh(new THREE.CylinderGeometry(TABLE_R, TABLE_R, 0.3, 96), new THREE.MeshStandardMaterial({ color: "#0f5a43", roughness: 0.95 }));
  felt.position.y = -0.15;
  felt.receiveShadow = true;
  const rim = new THREE.Mesh(new THREE.TorusGeometry(TABLE_R, 0.28, 16, 128), new THREE.MeshStandardMaterial({ color: "#5a3a22", roughness: 0.6 }));
  rim.rotation.x = Math.PI / 2;
  rim.position.y = 0.02;
  const ring = new THREE.Mesh(new THREE.RingGeometry(CIRCLE_R, CIRCLE_R + 0.08, 128), new THREE.MeshBasicMaterial({ color: "#1c7258" }));
  ring.rotation.x = -Math.PI / 2;
  ring.position.y = 0.005;
  const spot = new THREE.SpotLight("#fff1d6", 260, 40, Math.PI / 4.2, 0.5, 1.6);
  spot.position.set(2, 16, 6);
  spot.castShadow = true;
  spot.shadow.mapSize.set(1024, 1024);
  spot.shadow.bias = -0.0004;
  scene.add(felt, rim, ring, new THREE.HemisphereLight("#fff6e0", "#0a2a1f", 1.1), spot, spot.target);
}

/** Every seat's avatar, or null where there is none or it failed (the flat figure stands in). */
function loadAvatars(live: Live<ZuendstoffScene>): Promise<(Avatar | null)[]> {
  const { scene, players } = live(), avatarOf = new Map(players.map((p) => [p.id, p.avatar]));
  // the shipped files are meshopt-compressed with WebP textures
  const loader = new GLTFLoader().setMeshoptDecoder(MeshoptDecoder);
  // front faces only: Meshy marks the closed figures double-sided
  const dress = (m: THREE.MeshStandardMaterial) => ((m.side = THREE.FrontSide), m);
  return Promise.all(scene.seats.map((s) => {
    const id = avatarOf.get(s.player);
    return id ? loadAvatar(loader, id, AVATAR_H, dress).catch((e) => (console.warn(`avatar ${id}`, e), null)) : null;
  }));
}

// frees geometries, materials and textures (the avatars' too) outside the GL context
function disposeScene(scene: THREE.Scene) {
  scene.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!(o as THREE.Sprite).isSprite) mesh.geometry?.dispose();
    for (const material of [mesh.material ?? []].flat()) {
      for (const value of Object.values(material)) if (value instanceof THREE.Texture) value.dispose();
      material.dispose();
    }
  });
}

/** Draws the table into a canvas of its own inside `container`; `live()` returns the latest scene and players, `now()` the server clock. */
export function startZuendstoff(container: HTMLElement, live: Live<ZuendstoffScene>, now: () => number): Promise<StageHandle> {
  return startStage(container, live, now, {
    name: "zuendstoff",
    canvasClass: "tv-race-canvas",
    sounds: BOARD_SOUNDS,
    fontWeights: ["700", "800"],
    figures: true,
    uploadTextures: true,
    load: () => loadAvatars(live),
    build({ renderer: r, looks, assets }) {
      r.toneMapping = THREE.ACESFilmicToneMapping;
      r.shadowMap.enabled = true;
      r.shadowMap.type = THREE.PCFSoftShadowMap;
      const scene = new THREE.Scene(), camera = new THREE.PerspectiveCamera(CAMERA.fov, 16 / 9, 0.5, 60);
      camera.position.copy(CAMERA.pos);
      camera.lookAt(CAMERA.at);
      buildTable(scene);
      const seats = buildCircle(scene, looks, assets), bomb = new Bomb(scene);
      const hud = new ZuendstoffHud(r, seats, looks.map((l) => l.name));
      // the last throw seen (its follow-through outlasts its stage) and when this TV first saw the round's blast
      let last: Flight | null = null, boomAt = 0, boomRound = -1, prev = 0;
      return {
        scene,
        camera,
        reveal(on) {
          bomb.reveal(on);
          // one avatar see-through, so both looks compile now; play sets the real ones
          if (seats.length > 1) setOut(seats[0], on);
          if (!on) hud.compile();
        },
        layout: () => hud.layout(camera, live().scene, now()),
        move(ms, host) {
          const t = ms / 1000, dt = prev ? Math.min(0.1, Math.max(0, t - prev)) : 0, sc = live().scene, st = sc.stage;
          prev = t;
          if (st.stage === "flight") last = st;
          if (st.stage === "boom" && boomRound !== sc.round) {
            boomRound = sc.round;
            boomAt = host;
            last = null;
          }
          const frame = frameAt(sc, last, boomAt, host);
          sc.seats.forEach((s, i) => seats[i] && setOut(seats[i], s.lives === 0));
          poseCircle(seats, frame.poses, dt);
          scene.updateMatrixWorld();
          bomb.update(frame.bomb, seats, t);
        },
        cue(sound, host) {
          const sc = live().scene;
          sound.tick(zuendstoffCues(sc, boomAt, host), sc.stage.stage === "final", host);
        },
        paint: (_ms, host) => hud.update(live().scene, host),
        overlay: () => hud.render(),
        dispose() {
          hud.dispose();
          disposeCircle(seats);
          disposeScene(scene);
        },
      };
    },
  });
}
