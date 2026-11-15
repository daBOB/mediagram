// Entry of the Grand Allee board's lazily loaded chunk: loads the layout and the hotels beside the avatars, and puts the board's
// world, HUD, motion and cues on the shared TV stage, with a frame log and the start's timing marks.

import * as THREE from "three";
import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import { BOARD_SOUNDS } from "../table/board-sounds";
import { startStage, type Live, type StageHandle } from "../table/stage-host";
import { FrameLog, mark, reportStart } from "./grand-allee-timing";
import { loadAssets } from "./grand-allee-assets";
import { buildBoard } from "./grand-allee-board";
import { CameraRig, type CameraPose } from "./grand-allee-camera";
import { Dice } from "./grand-allee-dice";
import { bankAt } from "./grand-allee-clock";
import { SceneWatch } from "./grand-allee-events";
import { buildHotel } from "./grand-allee-hotels";
import { GrandAlleeHud } from "./grand-allee-hud";
import { CAMERA, SKY } from "./grand-allee-look";
import { GrandAlleeMotion } from "./grand-allee-motion";
import { Pieces } from "./grand-allee-pieces";
import { Grounds } from "./grand-allee-plots";
import { grandAlleeCues } from "./grand-allee-sound-cues";

/** What the stand-in host may ask of the board: a fixed camera (null leaves the camera to the turn). */
export interface BoardOptions {
  camera?: () => CameraPose | null;
}

// frees what the board holds outside the GL context too: geometries, materials, textures and their decoded images (an
// ImageBitmap keeps its pixels until closed). Sprites share three's one sprite geometry, which stays.
function disposeScene(scene: THREE.Scene) {
  scene.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!(o as THREE.Sprite).isSprite) mesh.geometry?.dispose();
    if ((o as THREE.InstancedMesh).isInstancedMesh) (o as THREE.InstancedMesh).dispose();
    for (const material of [mesh.material ?? []].flat()) {
      for (const value of Object.values(material)) {
        if (!(value instanceof THREE.Texture)) continue;
        value.dispose();
        (value.image as { close?: () => void } | null)?.close?.();
      }
      material.dispose();
    }
  });
}

/** Draws the board into a canvas of its own inside `container`; `live()` returns the latest scene and players, `now()` the server clock. */
export function startGrandAllee(container: HTMLElement, live: Live<GrandAlleeScene>, now: () => number, opts: BoardOptions = {}): Promise<StageHandle> {
  mark("start");
  return startStage(container, live, now, {
    name: "grand allee",
    canvasClass: "tv-race-canvas",
    sounds: BOARD_SOUNDS,
    fontWeights: ["600", "700", "800"],
    figures: true,
    localClipping: true,
    uploadTextures: true,
    load: loadAssets,
    build({ renderer: r, scene: first, looks, assets }) {
      mark("assets");
      const board = assets.board, scene = new THREE.Scene();
      scene.background = new THREE.Color(SKY);
      const camera = new THREE.PerspectiveCamera(CAMERA.fov, 16 / 9, 5, 800);
      buildBoard(scene, board, first.board.fields, assets.boardModel);
      const hotels = assets.hotels.map((root, i) => buildHotel(scene, root, board.plots[i], first.board.hotels[i]?.stages.length ?? 0));
      const grounds = new Grounds(scene, board), pieces = new Pieces(scene, r, looks);
      const dice = new Dice(scene, board, assets.dice), hud = new GrandAlleeHud(r, looks);
      const motion = new GrandAlleeMotion(board, hotels, grounds, pieces, dice);
      // the rising stages, markers and coins, shown for the compile: compiling or uploading them at the first purchase would
      // stall the box GPU in the middle of play
      const showing = [...hotels.flatMap((h) => [h.body, h.tint]).flatMap((p) => (p ? [p.mesh, p.rise] : [])), ...pieces.badges];
      const rig = new CameraRig(), watch = new SceneWatch();
      let log: FrameLog | undefined, frames = 0, last = 0, painted = 0;
      return {
        scene,
        camera,
        reveal(on) {
          showing.forEach((m) => (m.visible = on));
          pieces.rings.count = pieces.coins.count = on ? 1 : 0;
          if (on) return;
          hud.compile();
          mark("compiled");
        },
        layout() {
          const { scene: s, players } = live();
          hud.layout(s, players, now(), performance.now() / 1000);
        },
        move(ms, host) {
          // started with play, so the compile's long tasks are not counted among play's
          log ??= new FrameLog();
          log.begin(ms);
          const t = ms / 1000, { scene: sc, players } = live(), seen = watch.next(sc, t);
          if (seen?.fresh) motion.restart();
          if (seen) {
            motion.changed(seen.changes, t);
            // the host pays the lap money as the walk starts; it shows when the pawn reaches the Bank, with its jingle
            const bank = bankAt(sc), walker = sc.turn.stage === "walking" ? sc.turn.player : -1;
            hud.flash(seen.changes.cash, (p) => t + (p === walker && bank !== null ? Math.max(0, bank - host) / 1000 : 0));
          }
          const pose = opts.camera?.() ?? rig.update(sc, board, host, last ? t - last : 0);
          last = t;
          camera.position.set(...pose.pos);
          camera.lookAt(...pose.at);
          const colors = new Map(players.map((p) => [p.id, p.color]));
          motion.update(sc, (id) => colors.get(id) ?? "#999999", host, t);
          log.phase("motion");
        },
        cue(sound, host) {
          const sc = live().scene;
          sound.tick(grandAlleeCues(sc, host), sc.turn.stage === "final", host);
        },
        paint(ms, host) {
          const { scene: sc, players } = live();
          painted = hud.repaints;
          hud.update(sc, players, host, ms / 1000);
          log?.phase("hud");
        },
        overlay() {
          log?.phase("board");
          hud.render();
          log?.phase("hudgl");
          log?.end(`${live().scene.turn.stage}${hud.repaints !== painted ? " hud-redrawn" : ""}${hotels.some((h) => h.rising) ? " rising" : ""}`);
          if (frames++ < 2) {
            mark(`frame${frames}`);
            if (frames === 2) reportStart();
          }
        },
        dispose() {
          hud.dispose();
          disposeScene(scene);
        },
      };
    },
  });
}
