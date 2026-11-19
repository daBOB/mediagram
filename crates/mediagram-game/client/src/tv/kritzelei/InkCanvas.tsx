import { useEffect, useRef } from "react";
import type { InkLog } from "../../ink/ink-log";
import { strokesOf } from "../../ink/ink-log";
import { paintAll, paintTail } from "../../ink/paint-ink";

/** The paper, 16:9, sized to its box at device pixel ratio and kept in step with the ink log. */
export function InkCanvas({ ink }: { ink: InkLog }) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;
    const all = () => paintAll(ctx, strokesOf(ink.ops), canvas.width, canvas.height);
    const fit = () => {
      const dpr = window.devicePixelRatio || 1;
      const w = Math.round(canvas.clientWidth * dpr);
      const h = Math.round(canvas.clientHeight * dpr);
      if (w === 0 || (w === canvas.width && h === canvas.height)) return;
      canvas.width = w; // resizing clears the bitmap
      canvas.height = h;
      all();
    };
    fit();
    all();
    const stop = ink.subscribe((change, from) => {
      if (change === "append" && paintTail(ctx, ink.ops, from, canvas.width, canvas.height)) return;
      all();
    });
    const watch = new ResizeObserver(fit);
    watch.observe(canvas);
    return () => {
      stop();
      watch.disconnect();
    };
  }, [ink]);

  return <canvas ref={ref} className="kz-paper" />;
}
