import { useEffect, useRef, useState } from "react";
import type { Connection } from "../net";
import type { CardsScene } from "../protocol/CardsScene";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import type { CardsHandle } from "./cards/cards-stage";
import { TvFinal } from "./TvFinal";
import { TvSettingsPanel } from "./settings/TvSettingsPanel";

/** How long the winner's banner stays on the table before the final ranking. */
const CHEER_MS = 4000;

/**
 * The card table: a full-screen WebGL canvas with the tags and banner drawn
 * inside it (no HTML over the canvas while playing). three.js loads with this
 * scene only. Every card game shares it; the scene names the game.
 */
export function TvCards({ scene, players, conn }: { scene: CardsScene; players: PlayerInfo[]; conn: Connection }) {
  const stage = useRef<HTMLDivElement>(null);
  const handle = useRef<CardsHandle | null>(null);
  const live = useRef({ scene, players });
  live.current = { scene, players };
  const [state, setState] = useState<"loading" | "ready" | "failed">("loading");
  const [cheered, setCheered] = useState(false);
  const [settings, setSettings] = useState(false);
  const cheeredRef = useRef(false);
  cheeredRef.current = cheered;
  // bumped when a new game starts on a table already swapped for the ranking
  const [round, setRound] = useState(0);
  const ranking = scene.ranking;

  const stop = () => {
    handle.current?.dispose();
    handle.current = null;
  };

  useEffect(() => {
    let gone = false;
    setState("loading");
    import("./cards/cards-stage")
      .then((m) => m.startCards(stage.current!, () => live.current, () => conn.serverNow()))
      .then((h) => {
        // the ranking may have taken over while the box was still building the table
        if (gone || cheeredRef.current) h.dispose();
        else {
          handle.current = h;
          setState("ready");
        }
      })
      .catch((e) => {
        console.error("card table", e);
        if (!gone) setState("failed");
      });
    return () => {
      gone = true;
      stop();
    };
  }, [conn, round]);

  // OK on the remote opens the settings over the table; the game runs on underneath
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Enter" && !e.repeat && !cheeredRef.current) setSettings(true);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const over = ranking !== null;
  useEffect(() => {
    if (over && !cheered) {
      const t = window.setTimeout(() => {
        stop(); // the ranking replaces the table: no more frames drawn behind it
        setSettings(false);
        setCheered(true);
      }, CHEER_MS);
      return () => clearTimeout(t);
    }
    if (!over && cheered) {
      // the same table started again without this TV seeing the lobby
      setCheered(false);
      setRound((r) => r + 1);
    }
  }, [over, cheered]);

  if (ranking && cheered) {
    const order = new Map(ranking.map((id, i) => [id, i]));
    const ranked = players.filter((p) => order.has(p.id)).sort((a, b) => order.get(a.id)! - order.get(b.id)!);
    return <TvFinal players={ranked} conn={conn} ranked jingle={false} />;
  }
  return (
    <section className="tv-cards" ref={stage}>
      {state !== "ready" && (
        <p className="tv-cards-wait">{state === "failed" ? "Der Spieltisch konnte nicht geladen werden." : "Der Spieltisch wird gedeckt …"}</p>
      )}
      {settings && <TvSettingsPanel onClose={() => setSettings(false)} />}
    </section>
  );
}
