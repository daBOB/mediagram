import { useState } from "react";
import { useConnection } from "../net";
import type { TvView } from "../protocol/TvView";
import { TvDreifelder } from "./TvDreifelder";
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
  const { conn, status } = useConnection(
    () => ({ t: "hello", role: "tv", name: null, token: null }),
    (msg) => {
      if (msg.t === "tv") setView(msg.view);
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
      ) : (
        <TvQuiz scene={view.scene.scene} players={view.players} conn={conn} />
      )}
      {status !== "open" && view && <p className="banner">Verbindung unterbrochen, verbinde neu …</p>}
    </main>
  );
}
