import { Suspense, lazy, useEffect, useRef, useState, type ReactNode } from "react";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import type { KopfnussScene } from "../../protocol/KopfnussScene";
import { arenaView } from "./arena-view";

const ArenaCanvas = lazy(async () => {
  const { startKopfnussArena } = await import("./arena-engine");
  return {
    default: function ArenaCanvas({ view, onReady }: { view: ReturnType<typeof arenaView>; onReady: (ready: boolean) => void }) {
      const canvas = useRef<HTMLCanvasElement>(null);
      const current = useRef(view);
      const ready = useRef(onReady);
      current.current = view;
      ready.current = onReady;
      useEffect(() => {
        if (!canvas.current) return;
        return startKopfnussArena(canvas.current, () => current.current, (value) => ready.current(value));
      }, []);
      return <canvas ref={canvas} className="kn-arena-canvas" aria-hidden="true" />;
    },
  };
});

export function KopfnussArena({ scene, players, children }: { scene: KopfnussScene; players: PlayerInfo[]; children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const view = arenaView(scene, players);
  const category = view.kind === "pick" || view.kind === "resolve";
  return (
    <main className={`tv kn-show${category ? " kn-category-show" : ""}${ready ? " is-3d" : ""}`} data-3d={ready ? "ready" : "fallback"}>
      <Suspense fallback={null}>
        <ArenaCanvas view={view} onReady={setReady} />
      </Suspense>
      <div className="kn-show-content">{children}</div>
    </main>
  );
}
