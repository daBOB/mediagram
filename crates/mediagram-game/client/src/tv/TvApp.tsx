import { useRef, useState, type ReactNode } from "react";
import { InkLog } from "../ink/ink-log";
import { useConnection, type Connection } from "../net";
import type { TvScene } from "../protocol/TvScene";
import type { TvView } from "../protocol/TvView";
import { TvDreifelder } from "./TvDreifelder";
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

/** The screen for each scene kind. Keyed by every kind, so a game without its screen does not compile. */
const SCREENS: { [K in Kind]: Draw<K> } = {
  lobby: (scene, view, conn) => <TvLobby view={view} scene={scene} conn={conn} />,
  quiz: ({ scene }, view, conn) => <TvQuiz scene={scene} players={view.players} conn={conn} />,
  poster: ({ scene }, view, conn) => <TvPoster scene={scene} players={view.players} conn={conn} />,
  // a new table of players is a new game: build the board afresh
  race: ({ scene }, view, conn) => <TvRace key={scene.seats.map((s) => s.player).join()} scene={scene} players={view.players} conn={conn} />,
  cards: ({ scene }, view, conn) => <TvCards key={scene.game + scene.seats.map((s) => s.player).join()} scene={scene} players={view.players} conn={conn} />,
  kopfnuss: ({ scene }, view, conn) => <TvKopfnuss scene={scene} players={view.players} conn={conn} />,
  zuendstoff: ({ scene }, view, conn) => <TvZuendstoff scene={scene} players={view.players} conn={conn} />,
  letztes_wort: ({ scene }, view, conn) => <TvLetztesWort scene={scene} players={view.players} conn={conn} />,
  dreifelder: ({ scene }, view, conn) => <TvDreifelder scene={scene} players={view.players} conn={conn} />,
  fluechtig: ({ scene }, view, conn) => <TvFluechtig scene={scene} players={view.players} conn={conn} />,
  grand_allee: ({ scene }, view, conn) => <TvGrandAllee key={scene.seats.map((s) => s.player).join()} scene={scene} players={view.players} conn={conn} />,
  preisfrage: ({ scene }, view, conn) => <TvPreisfrage scene={scene} players={view.players} conn={conn} />,
  kritzelei: ({ scene }, view, conn, ink) => <TvKritzelei scene={scene} players={view.players} conn={conn} ink={ink} />,
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
