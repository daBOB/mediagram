import { useRef, useState } from "react";
import { InkLog } from "../ink/ink-log";
import { useConnection } from "../net";
import type { TvView } from "../protocol/TvView";
import { TvDreifelder } from "./TvDreifelder";
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
      ) : view.scene.kind === "lobby" ? (
        <TvLobby view={view} scene={view.scene} conn={conn} />
      ) : view.scene.kind === "poster" ? (
        <TvPoster scene={view.scene.scene} players={view.players} conn={conn} />
      ) : view.scene.kind === "race" ? (
        // a new table of players is a new game: build the board afresh
        <TvRace key={view.scene.scene.seats.map((s) => s.player).join()} scene={view.scene.scene} players={view.players} conn={conn} />
      ) : view.scene.kind === "cards" ? (
        <TvCards key={view.scene.scene.game + view.scene.scene.seats.map((s) => s.player).join()} scene={view.scene.scene} players={view.players} conn={conn} />
      ) : view.scene.kind === "kopfnuss" ? (
        <TvKopfnuss scene={view.scene.scene} players={view.players} conn={conn} />
      ) : view.scene.kind === "dreifelder" ? (
        <TvDreifelder scene={view.scene.scene} players={view.players} conn={conn} />
      ) : view.scene.kind === "fluechtig" ? (
        <TvFluechtig scene={view.scene.scene} players={view.players} conn={conn} />
      ) : view.scene.kind === "grand_allee" ? (
        <TvGrandAllee key={view.scene.scene.seats.map((s) => s.player).join()} scene={view.scene.scene} players={view.players} conn={conn} />
      ) : view.scene.kind === "preisfrage" ? (
        <TvPreisfrage scene={view.scene.scene} players={view.players} conn={conn} />
      ) : view.scene.kind === "kritzelei" ? (
        <TvKritzelei scene={view.scene.scene} players={view.players} conn={conn} ink={ink} />
      ) : (
        <TvQuiz scene={view.scene.scene} players={view.players} conn={conn} />
      )}
      {status !== "open" && view && <p className="banner">Verbindung unterbrochen, verbinde neu …</p>}
    </main>
  );
}
