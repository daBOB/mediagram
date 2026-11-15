import type { Connection } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import type { Ranking } from "../protocol/Ranking";
import type { RaceScene } from "../protocol/RaceScene";
import { TvTableShell, type StageLoader } from "./table/TvTableShell";

const load: StageLoader<RaceScene> = (el, live, now) => import("./race/race-stage").then((m) => m.startRace(el, live, now));

/** The race board. */
export function TvRace({ scene, players, ranking, conn }: { scene: RaceScene; players: PlayerInfo[]; ranking: Ranking | null; conn: Connection }) {
  return (
    <TvTableShell scene={scene} players={players} conn={conn} ranking={ranking} load={load} name="race board" className="tv-race"
      waiting="Das Spielbrett wird aufgebaut …" failed="Das Spielbrett konnte nicht geladen werden." />
  );
}
