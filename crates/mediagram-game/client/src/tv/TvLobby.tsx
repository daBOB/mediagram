import { useEffect, useRef, useState } from "react";
import type { Connection } from "../net";
import type { GameKind } from "../protocol/GameKind";
import type { TvScene } from "../protocol/TvScene";
import type { TvView } from "../protocol/TvView";
import { gameBadge } from "../ui/lobby";
import { Token } from "../ui/Token";
import { TvSettingsPanel } from "./settings/TvSettingsPanel";

type Lobby = Extract<TvScene, { kind: "lobby" }>;

// The game the box's poster shelf picked (`/tv?game=durak`). Asked for once per page load: after
// a game the room keeps whatever the leader's phone has chosen since. The host checks the name.
let picked = new URLSearchParams(location.search).get("game") as GameKind | null;

export function TvLobby({ view, scene, conn }: { view: TvView; scene: Lobby; conn: Connection }) {
  useEffect(() => {
    if (!picked) return;
    const { quiz_mode, kids_only, adult } = scene.settings;
    if (picked !== scene.settings.game) conn.send({ t: "action", action: { t: "configure", game: picked, quiz_mode, kids_only, adult } });
    picked = null;
  }, [conn, scene.settings]);
  const seated = view.players.filter((p) => p.connected);
  const canStart = seated.length >= scene.min_players && !scene.blocked;
  const [settings, setSettings] = useState(false);
  const settingsBtn = useRef<HTMLButtonElement>(null);
  const startBtn = useRef<HTMLButtonElement>(null);
  const closeSettings = () => {
    setSettings(false);
    settingsBtn.current?.focus();
  };
  // Left/Right on the remote moves between the two buttons
  const hop = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowLeft") settingsBtn.current?.focus();
    else if (e.key === "ArrowRight") startBtn.current?.focus();
  };
  return (
    <section className="tv-lobby">
      <div className="tv-lobby-intro">
        <p className="kicker kicker-tv">Spieleabend</p>
        <h1 className="tv-headline">Handy-Kamera auf den Code</h1>
        <p className="tv-url">{view.join_url.replace(/^https?:\/\//, "").replace(/\/$/, "")}</p>
        <p className="badges">
          <span className="badge">{gameBadge(scene)}</span>
          {scene.settings.kids_only && <span className="badge badge-on">Nur für Kinder</span>}
          {scene.settings.adult && <span className="badge badge-on">Matratzensport · ab 18</span>}
        </p>
      </div>
      <figure className="qr-card">
        <img src="/qr.svg" alt={`QR-Code für ${view.join_url}`} />
      </figure>
      <div className="tv-seats">
        <div className="tv-seats-main">
          {view.players.length === 0 ? (
            <p className="tv-empty">Noch niemand am Tisch.</p>
          ) : (
            <ul className="seat-list">
              {view.players.map((p) => (
                <li key={p.id} className="seat">
                  <Token player={p} />
                  <span className="seat-name">
                    {p.name}
                    {p.leader && " ★"}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {scene.blocked && <p className="lobby-blocked lobby-blocked-tv">{scene.blocked}</p>}
          {seated.length > 0 && (
            <p className="tv-footnote">
              {seated.length} am Tisch
              {scene.leader_name && ` · ${scene.leader_name} startet, oder OK auf der Fernbedienung`}
            </p>
          )}
        </div>
        <div className="tv-seats-buttons" onKeyDown={hop}>
          <button ref={settingsBtn} className="cta cta-tv cta-tv-quiet" onClick={() => setSettings(true)}>
            Einstellungen
          </button>
          <button
            ref={startBtn}
            className="cta cta-tv"
            autoFocus
            aria-disabled={!canStart}
            onClick={() => canStart && conn.send({ t: "action", action: { t: "start_game" } })}
          >
            {seated.length < scene.min_players ? `Wartet auf ${scene.min_players - seated.length} Spieler` : "Los geht's"}
          </button>
        </div>
      </div>
      {settings && <TvSettingsPanel onClose={closeSettings} />}
    </section>
  );
}
