import type { Connection } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import type { Ranking } from "../protocol/Ranking";
import type { ZuendstoffScene } from "../protocol/ZuendstoffScene";
import { TvTableShell, type StageLoader } from "./table/TvTableShell";

const load: StageLoader<ZuendstoffScene> = (el, live, now) => import("./zuendstoff/zuendstoff-stage").then((m) => m.startZuendstoff(el, live, now));

/** Zündstoff: the players' avatars in a circle on the felt, throwing the bomb. */
export function TvZuendstoff({ scene, players, ranking, conn }: { scene: ZuendstoffScene; players: PlayerInfo[]; ranking: Ranking | null; conn: Connection }) {
  return (
    <TvTableShell scene={scene} players={players} conn={conn} ranking={ranking} load={load} name="zuendstoff table" className="tv-race"
      waiting="Der Tisch wird aufgebaut …" failed="Der Tisch konnte nicht geladen werden." />
  );
}
