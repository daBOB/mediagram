import { useEffect, useRef, useState } from "react";
import type { GameKind } from "../../protocol/GameKind";
import { gameArtUrl } from "../../ui/shelf";

/** One backdrop on screen; `id` keys it, so a game the row comes back to gets a fresh image that loads and fades in. */
type Layer = { id: number; kind: GameKind; loaded: boolean; failed: boolean; full: boolean };

/**
 * The focused game's backdrop, faded in over the last one that was fully in, which stays underneath
 * until the new one is (the one still fading in when the row moves on keeps fading, between them).
 * A game without a backdrop fades everything under it out to the felt instead.
 */
export function ShelfBackdrop({ kind }: { kind: GameKind }) {
  const seq = useRef(1);
  const [layers, setLayers] = useState<Layer[]>([{ id: 1, kind, loaded: false, failed: false, full: false }]);
  useEffect(() => {
    setLayers((ls) => {
      const top = ls[ls.length - 1];
      if (top.kind === kind) return ls;
      const base = [...ls].reverse().find((l) => l.full);
      const fading = top.loaded && !top.failed && !top.full ? top : undefined;
      const keep = [base, fading].filter((l): l is Layer => !!l);
      // back to a game still on screen: it stays, nothing new to load
      const back = keep.findIndex((l) => l.kind === kind);
      if (back >= 0) return keep.slice(0, back + 1);
      return [...keep, { id: ++seq.current, kind, loaded: false, failed: false, full: false }];
    });
  }, [kind]);
  const top = layers[layers.length - 1];
  const update = (id: number, patch: Partial<Layer>) => setLayers((ls) => ls.map((l) => (l.id === id ? { ...l, ...patch } : l)));
  // Faded in: it covers the ones below, which go. Faded out (under a top without a backdrop): it goes.
  const ended = (l: Layer, shown: boolean) =>
    setLayers((ls) => {
      const i = ls.findIndex((x) => x.id === l.id);
      if (i < 0) return ls;
      return shown ? [{ ...ls[i], full: true }, ...ls.slice(i + 1)] : ls.filter((x) => x.id !== l.id);
    });
  return (
    <div className="shelf-backdrop" aria-hidden="true">
      {layers.map((l) => {
        const shown = l.loaded && !l.failed && !top.failed;
        return (
          <img
            key={l.id}
            src={gameArtUrl(l.kind, "backdrop")}
            alt=""
            className={shown ? "is-shown" : undefined}
            onLoad={() => update(l.id, { loaded: true })}
            onError={() => update(l.id, { failed: true })}
            onTransitionEnd={() => ended(l, shown)}
          />
        );
      })}
    </div>
  );
}
