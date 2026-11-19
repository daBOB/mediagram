// Entry of the card table's lazily loaded chunk: the table's world, HUD, motion and cues on the shared TV stage.

import type { CardsScene } from "../../protocol/CardsScene";
import { startStage, type Live, type StageHandle } from "../table/stage-host";
import { CardsHud } from "./cards-hud";
import { seatSpot } from "./cards-layout";
import { CardsMotion } from "./cards-motion";
import { CARDS_SOUNDS, cardsCues } from "./cards-sound-cues";
import { textsFor } from "./cards-texts";
import { buildWorld } from "./cards-world";

/** Draws the table into a canvas of its own inside `container`; `live()` returns the latest scene and players, `now()` the server clock. */
export function startCards(container: HTMLElement, live: Live<CardsScene>, now: () => number): Promise<StageHandle> {
  return startStage(container, live, now, {
    name: "card",
    canvasClass: "tv-cards-canvas",
    sounds: CARDS_SOUNDS,
    fontWeights: ["700", "800"],
    figures: false,
    async build({ renderer, scene, looks }) {
      const world = await buildWorld(renderer, looks.length);
      const spots = looks.map((_, i) => seatSpot(i, looks.length));
      world.camera.position.set(0, 15.5, 10.5);
      world.camera.lookAt(0, 0, 0.4);
      const hud = new CardsHud(renderer, world.camera, looks, spots, textsFor(scene.game));
      const motion = new CardsMotion(world, hud, spots, scene.seats.map((s) => s.player));
      const heard = new Set<number>();
      return {
        scene: world.scene,
        camera: world.camera,
        // the badges too, or the first wish compiles mid-play
        reveal(on) {
          world.cards.showAll(on);
          for (const b of Object.values(world.badges)) b.visible = on;
        },
        layout: () => hud.layout(),
        move: (_ms, host) => motion.update(live().scene, host),
        cue(sound, host) {
          const sc = live().scene;
          sound.tick(cardsCues(sc, heard), false, host);
          // the table's ending has no host time: its jingle plays the moment the ranking arrives
          if (sc.ranking) sound.end("winnerJingle");
        },
        overlay: () => hud.render(),
        dispose: () => hud.dispose(),
      };
    },
  });
}
