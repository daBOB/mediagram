import { useEffect, useRef, useState } from "react";
import type { Connection } from "../net";
import { useServerNow } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import type { PosterScene } from "../protocol/PosterScene";
import { Token } from "../ui/Token";
import { blockWidth, drawPixelated } from "./pixelate";
import { TvFinal } from "./TvFinal";

const W = 480;
const H = 720;
// Same curve as the host's points_at_step (1000 down to 500 over the steps).
const MAX_POINTS = 1000;
const MIN_POINTS = 500;

function useImage(src: string): HTMLImageElement | null {
  const [img, setImg] = useState<HTMLImageElement | null>(null);
  useEffect(() => {
    setImg(null);
    const el = new Image();
    el.onload = () => setImg(el);
    el.src = src;
  }, [src]);
  return img;
}

function Poster({ src, block }: { src: string; block: number }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const img = useImage(src);
  useEffect(() => {
    const ctx = ref.current?.getContext("2d");
    if (ctx && img) drawPixelated(ctx, img, block);
  }, [img, block]);
  return <canvas ref={ref} className="poster-canvas" width={W} height={H} aria-label="Poster" />;
}

export function TvPoster({ scene, players, conn }: { scene: PosterScene; players: PlayerInfo[]; conn: Connection }) {
  const now = useServerNow(conn);
  const { stage } = scene;
  if (stage.stage === "final") return <TvFinal players={players} conn={conn} />;

  const reveal = stage.stage === "reveal" ? stage : null;
  const steps = stage.stage === "showing" ? stage.steps : 8;
  const step =
    stage.stage === "showing"
      ? Math.min(Math.max(Math.floor((now - stage.started_at) / stage.step_ms), 0), steps - 1)
      : steps - 1;
  const points = reveal ? reveal.points : MAX_POINTS - Math.round((step * (MAX_POINTS - MIN_POINTS)) / (steps - 1));
  const locked = new Set(stage.stage === "showing" ? stage.locked : []);
  const winner = reveal?.winner != null ? players.find((p) => p.id === reveal.winner) : undefined;

  return (
    <section className="tv-poster">
      <div className="poster-frame">
        <Poster src={stage.image} block={reveal ? 1 : blockWidth(step, W)} />
        {reveal && <div className="poster-title">{reveal.title}</div>}
      </div>
      <div className="poster-side">
        <p className="poster-kick">
          Poster-Raten · Runde {scene.round} von {scene.rounds}
        </p>
        {reveal ? (
          <p className="poster-big">{winner ? `${winner.name} +${points}` : "Niemand hat's erkannt"}</p>
        ) : (
          <>
            <p className="poster-big">{points} Punkte</p>
            <p className="poster-level">
              Schärfe {step + 1} von {steps}
            </p>
            <div className="poster-meter" aria-hidden="true">
              {Array.from({ length: steps }, (_, i) => (
                <i key={i} className={i <= step ? "on" : ""} />
              ))}
            </div>
          </>
        )}
        <div className="poster-chips">
          {players.map((p) => (
            <span key={p.id} className={`poster-chip${locked.has(p.id) ? " is-out" : ""}`}>
              <Token player={p} size="sm" />
              {p.name}
              {locked.has(p.id) && " ✕"}
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}
