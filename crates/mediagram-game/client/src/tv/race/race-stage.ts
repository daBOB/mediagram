// Entry of the race board's lazily loaded chunk: the race's world, HUD, motion and cues on the shared TV stage.

import type { RaceScene } from "../../protocol/RaceScene";
import { getSettings, subscribeSettings } from "../settings/tv-settings";
import { BOARD_SOUNDS } from "../table/board-sounds";
import { startStage, type Live, type StageHandle } from "../table/stage-host";
import { Hud } from "./race-hud";
import { Motion, bannerTexts } from "./race-motion";
import { stageCues } from "./race-sound-cues";
import { buildWorld, showFigures } from "./race-world";

/** Draws the board into a canvas of its own inside `container`; `live()` returns the latest scene and players, `now()` the server clock. */
export function startRace(container: HTMLElement, live: Live<RaceScene>, now: () => number): Promise<StageHandle> {
  return startStage(container, live, now, {
    name: "race",
    canvasClass: "tv-race-canvas",
    sounds: BOARD_SOUNDS,
    fontWeights: ["700", "800"],
    figures: true,
    async build({ renderer, scene, looks }) {
      const seated = looks.map((l, i) => ({ ...l, corner: scene.seats[i].corner }));
      const world = await buildWorld(renderer, seated);
      const hud = new Hud(renderer, seated, bannerTexts(seated.map((s) => s.name)));
      const motion = new Motion(world, hud, new Map(scene.seats.map((s, i) => [s.player, seated[i].name])));
      const unsubscribe = subscribeSettings((s) => showFigures(world, s.figures));
      return {
        scene: world.scene,
        camera: world.camera,
        // the move markers too, or the first "welche Figur?" compiles mid-play; both figure styles too, so switching them
        // in the settings never compiles mid-game
        reveal(on) {
          for (const m of world.markers) m.ring.visible = m.target.visible = m.badge.visible = on;
          if (!on) showFigures(world, getSettings().figures);
          else for (const p of world.pawns.flat()) if (p.figure) p.figure.visible = true;
        },
        layout: () => hud.layout(),
        move(ms, host) {
          const t = ms / 1000;
          motion.update(live().scene, host, t);
          world.camera.position.set(Math.sin(t * 0.15) * 1.4, 17.6, 13.2);
          world.camera.lookAt(0, 0, 0.55);
        },
        cue(sound, host) {
          const sc = live().scene;
          sound.tick(stageCues(sc, host), sc.stage.stage === "final", host);
        },
        overlay: () => hud.render(),
        dispose() {
          unsubscribe();
          hud.dispose();
        },
      };
    },
  });
}
