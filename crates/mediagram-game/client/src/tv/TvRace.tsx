import { useEffect, useRef, useState } from "react";
import type { Connection } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import type { RaceScene } from "../protocol/RaceScene";
import type { RaceHandle } from "./race/race-stage";
import { TvFinal } from "./TvFinal";
import { TvSettingsPanel } from "./settings/TvSettingsPanel";

/** How long the winner's banner stays on the board before the final ranking. */
const CHEER_MS = 4000;

/**
 * The race board: a full-screen WebGL canvas with the tags and banner drawn
 * inside it (no HTML over the canvas while playing — it cost the box frames).
 * three.js loads with this scene only.
 */
export function TvRace({ scene, players, conn }: { scene: RaceScene; players: PlayerInfo[]; conn: Connection }) {
  const stage = useRef<HTMLDivElement>(null);
  const handle = useRef<RaceHandle | null>(null);
  const live = useRef({ scene, players });
  live.current = { scene, players };
  const [state, setState] = useState<"loading" | "ready" | "failed">("loading");
  const [cheered, setCheered] = useState(false);
  const [settings, setSettings] = useState(false);
  const cheeredRef = useRef(false);
  cheeredRef.current = cheered;
  // bumped when a new game starts on a board already swapped for the ranking
  const [round, setRound] = useState(0);
  const final = scene.stage.stage === "final" ? scene.stage : null;

  const stop = () => {
    handle.current?.dispose();
    handle.current = null;
  };

  useEffect(() => {
    let gone = false;
    setState("loading");
    import("./race/race-stage")
      .then((m) => m.startRace(stage.current!, () => live.current, () => conn.serverNow()))
      .then((h) => {
        // the ranking may have taken over while the box was still building the board
        if (gone || cheeredRef.current) h.dispose();
        else {
          handle.current = h;
          setState("ready");
        }
      })
      .catch((e) => {
        console.error("race board", e);
        if (!gone) setState("failed");
      });
    return () => {
      gone = true;
      stop();
    };
  }, [conn, round]);

  // OK on the remote opens the settings over the board; the game runs on underneath
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      // not on a held OK: the one that started the game would open the panel over the new board
      if (e.key === "Enter" && !e.repeat && !cheeredRef.current) setSettings(true);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const over = final !== null;
  useEffect(() => {
    if (over && !cheered) {
      const t = window.setTimeout(() => {
        stop(); // the ranking replaces the board: no more frames drawn behind it
        setSettings(false); // else it would reopen over the next round's board
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

  if (final && cheered) {
    const order = new Map(final.ranking.map((id, i) => [id, i]));
    const ranked = players.filter((p) => order.has(p.id)).sort((a, b) => order.get(a.id)! - order.get(b.id)!);
    return <TvFinal players={ranked} conn={conn} ranked />;
  }
  return (
    <section className="tv-race" ref={stage}>
      {state !== "ready" && (
        <p className="tv-race-wait">
          {state === "failed" ? "Das Spielbrett konnte nicht geladen werden." : "Das Spielbrett wird aufgebaut …"}
        </p>
      )}
      {settings && <TvSettingsPanel onClose={() => setSettings(false)} />}
    </section>
  );
}
