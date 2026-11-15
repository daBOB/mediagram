import type { Connection } from "../net";
import type { CardsScene } from "../protocol/CardsScene";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { TvTableShell, type StageLoader } from "./table/TvTableShell";

const load: StageLoader<CardsScene> = (el, live, now) => import("./cards/cards-stage").then((m) => m.startCards(el, live, now));

/** The card table. Every card game shares it; the scene names the game. */
export function TvCards({ scene, players, conn }: { scene: CardsScene; players: PlayerInfo[]; conn: Connection }) {
  return (
    <TvTableShell scene={scene} players={players} conn={conn} ranking={scene.ranking} load={load} name="card table" className="tv-cards"
      waiting="Der Spieltisch wird gedeckt …" failed="Der Spieltisch konnte nicht geladen werden." />
  );
}
