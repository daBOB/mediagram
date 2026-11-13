import { useRef, useState } from "react";
import { useConnection, type Connection } from "../net";
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
  const canvas = useRef<HTMLCanvasElement>(null);
  const samples = useRef<number[]>([]);
  const [stats, setStats] = useState("n=0");
  const connRef = useRef<Connection | null>(null);
  const spike = (m: { points: number[]; at: number }) => {
    const c = canvas.current;
    if (!c) return;
    if (c.width !== innerWidth || c.height !== innerHeight) {
      c.width = innerWidth;
      c.height = innerHeight;
    }
    const g = c.getContext("2d")!;
    g.fillStyle = "#f6c731";
    for (let i = 0; i + 1 < m.points.length; i += 2) g.fillRect((m.points[i] / 10000) * c.width - 3, (m.points[i + 1] / 10000) * c.height - 3, 6, 6);
    requestAnimationFrame(() => {
      const ms = connRef.current!.serverNow() - m.at;
      console.log("spike", ms);
      const s = samples.current;
      s.push(ms);
      if (s.length > 200) s.shift();
      const sorted = [...s].sort((a, b) => a - b);
      setStats(`n=${s.length} median=${sorted[Math.floor(sorted.length / 2)].toFixed(0)} p95=${sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * 0.95))].toFixed(0)}`);
    });
  };
  const { conn, status } = useConnection(
    () => ({ t: "hello", role: "tv", name: null, token: null }),
    (msg) => {
      if (msg.t === "tv") setView(msg.view);
      if ((msg as { t: string }).t === "spike_ink") spike(msg as never);
    },
  );
  connRef.current = conn;

  return (
    <main className="tv">
      <canvas ref={canvas} style={{ position: "fixed", inset: 0, zIndex: 9999, pointerEvents: "none" }} />
      <div style={{ position: "fixed", top: 16, left: 16, zIndex: 10000, font: "bold 64px monospace", color: "#fff", background: "#000a", padding: "8px 16px" }}>{stats}</div>
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
