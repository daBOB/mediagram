import { useRef, useState, type ReactNode } from "react";
import { InkLog } from "../ink/ink-log";
import { useConnection, type Connection } from "../net";
import type { TvScene } from "../protocol/TvScene";
import type { TvView } from "../protocol/TvView";
import { TvDreifelder } from "./TvDreifelder";
import { TvFinal } from "./TvFinal";
import { TvZuendstoff } from "./TvZuendstoff";
import { TvLetztesWort } from "./TvLetztesWort";
import { TvKritzelei } from "./TvKritzelei";
import { TvKopfnuss } from "./TvKopfnuss";
import { TvFluechtig } from "./TvFluechtig";
import { TvGrandAllee } from "./TvGrandAllee";
import { TvLobby } from "./TvLobby";
import { TvPoster } from "./TvPoster";
import { TvCards } from "./TvCards";
import { TvPreisfrage } from "./TvPreisfrage";
import { TvQuiz } from "./TvQuiz";
import { TvRace } from "./TvRace";

type Kind = TvScene["kind"];
type SceneOf<K extends Kind> = Extract<TvScene, { kind: K }>;
type Draw<K extends Kind> = (scene: SceneOf<K>, view: TvView, conn: Connection, ink: InkLog) => ReactNode;

/** A game that does not cheer its winner first gives way to the awards ceremony once the room ranks it; it stays mounted underneath. */
const ending =
  <K extends Kind>(draw: Draw<K>): Draw<K> =>
  (scene, view, conn, ink) => (
    <>
      {draw(scene, view, conn, ink)}
      {view.ranking && <TvFinal ranking={view.ranking} players={view.players} conn={conn} />}
    </>
  );

/**
 * The screen for each scene kind. Keyed by every kind, so a game without its screen does not compile.
 * The 3D boards and Flüchtig cheer their winner a few seconds before showing the ranking themselves.
 */
const SCREENS: { [K in Kind]: Draw<K> } = {
  lobby: (scene, view, conn) => <TvLobby view={view} scene={scene} conn={conn} />,
  quiz: ending(({ scene }, view, conn) => <TvQuiz scene={scene} players={view.players} conn={conn} />),
  poster: ending(({ scene }, view, conn) => <TvPoster scene={scene} players={view.players} conn={conn} />),
  // a new table of players is a new game: build the board afresh
  race: ({ scene }, view, conn) => (
    <TvRace key={scene.seats.map((s) => s.player).join()} scene={scene} players={view.players} ranking={view.ranking} conn={conn} />
  ),
  cards: ({ scene }, view, conn) => (
    <TvCards key={scene.game + scene.seats.map((s) => s.player).join()} scene={scene} players={view.players} ranking={view.ranking} conn={conn} />
  ),
  kopfnuss: ending(({ scene }, view, conn) => <TvKopfnuss scene={scene} players={view.players} conn={conn} />),
  zuendstoff: ({ scene }, view, conn) => (
    <TvZuendstoff key={scene.seats.map((s) => s.player).join()} scene={scene} players={view.players} ranking={view.ranking} conn={conn} />
  ),
  letztes_wort: ending(({ scene }, view) => <TvLetztesWort scene={scene} players={view.players} />),
  dreifelder: ending(({ scene }, view, conn) => <TvDreifelder scene={scene} players={view.players} conn={conn} />),
  fluechtig: ({ scene }, view, conn) => <TvFluechtig scene={scene} players={view.players} ranking={view.ranking} conn={conn} />,
  grand_allee: ({ scene }, view, conn) => (
    <TvGrandAllee key={scene.seats.map((s) => s.player).join()} scene={scene} players={view.players} ranking={view.ranking} conn={conn} />
  ),
  preisfrage: ending(({ scene }, view, conn) => <TvPreisfrage scene={scene} players={view.players} conn={conn} />),
  kritzelei: ending(({ scene }, view, conn, ink) => <TvKritzelei scene={scene} players={view.players} conn={conn} ink={ink} />),
};

/**
 * The TV screen: the box shows it full-screen in a WebView of its loopback
 * `/tv` page, drawing whatever `TvView` the host sends.
 */
export function TvApp() {
  const [view, setView] = useState<TvView | null>(null);
  const ink = useRef(new InkLog()).current;
  const { conn, status } = useConnection(
    () => ({ t: "hello", role: "tv", name: null, token: null }),
    (msg) => {
      if (msg.t === "tv") setView(msg.view);
      else if (msg.t === "draw") ink.apply(msg);
    },
  );

  return (
    <main className="tv">
      {!view ? (
        <p className="tv-wait">Verbinde mit dem Spieltisch …</p>
      ) : (
        // TypeScript cannot tie the kind it looks up to the scene it passes, so the lookup is widened once.
        (SCREENS[view.scene.kind] as Draw<Kind>)(view.scene, view, conn, ink)
      )}
      {status !== "open" && view && <p className="banner">Verbindung unterbrochen, verbinde neu …</p>}
    </main>
  );
}
