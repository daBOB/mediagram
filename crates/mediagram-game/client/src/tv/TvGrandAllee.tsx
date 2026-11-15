import type { Connection } from "../net";
import type { GrandAlleeScene } from "../protocol/GrandAlleeScene";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { mark } from "./grand-allee/grand-allee-timing";
import { TvTableShell, type StageLoader } from "./table/TvTableShell";

const load: StageLoader<GrandAlleeScene> = (el, live, now) => {
  mark("mount");
  return import("./grand-allee/grand-allee-stage").then((m) => m.startGrandAllee(el, live, now));
};

/** The Grand Allee board. Its ranking puts the broke by who lasted longer. */
export function TvGrandAllee({ scene, players, conn }: { scene: GrandAlleeScene; players: PlayerInfo[]; conn: Connection }) {
  const ranking = scene.turn.stage === "final" ? scene.turn.ranking : null;
  return (
    <TvTableShell scene={scene} players={players} conn={conn} ranking={ranking} load={load} name="grand allee board" className="tv-race"
      waiting="Das Spielbrett wird aufgebaut …" failed="Das Spielbrett konnte nicht geladen werden." />
  );
}
