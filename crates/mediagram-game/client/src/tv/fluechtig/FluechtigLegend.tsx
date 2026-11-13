import type { CSSProperties } from "react";
import type { FluechtigTicket } from "../../protocol/FluechtigTicket";

/** What each line is called on the TV. */
export const LINE_NAME: Record<FluechtigTicket, string> = { taxi: "Taxi", bus: "Bus", ubahn: "U-Bahn", schatten: "Fähre" };

/**
 * A line as the plan draws it, for the rules cards and the legend: a street,
 * the bus as its stripe down a street, the U-Bahn dashed, the ferry's lane
 * on water.
 */
export function LineSwatch({ kind, width = 44 }: { kind: FluechtigTicket; width?: number }) {
  const line = (cls: string) => <line className={cls} x1={4} y1={8} x2={width - 4} y2={8} />;
  return (
    <svg className="fl-swatch" width={width} height={16} viewBox={`0 0 ${width} 16`} aria-hidden="true" style={{ "--u": 1 } as CSSProperties}>
      {kind === "schatten" && <rect width={width} height={16} rx={4} fill="var(--river)" />}
      {(kind === "taxi" || kind === "bus") && (
        <>
          {line("sp-casing")}
          {line("sp-street")}
        </>
      )}
      {kind === "bus" && line("sp-bus")}
      {kind === "ubahn" && line("sp-ubahn")}
      {kind === "schatten" && line("sp-ferry")}
    </svg>
  );
}

/** The hideout mark the plan rings the fugitive's possible fields with. */
export function HideoutMark({ size = 16 }: { size?: number }) {
  const r = size / 2;
  return (
    <svg width={size} height={size} viewBox={`${-r} ${-r} ${size} ${size}`} aria-hidden="true">
      <circle className="fl-hideout" r={r * 0.76} />
    </svg>
  );
}

/** The legend that stays on the TV, bottom right: the lines and the hideout mark. */
export function FluechtigLegend({ runner }: { runner: string }) {
  return (
    <div className="fl-hud fl-legend">
      {(["taxi", "ubahn", "bus", "schatten"] as const).map((k) => (
        <span key={k}>
          <LineSwatch kind={k} width={24} />
          {LINE_NAME[k]}
        </span>
      ))}
      <span className="fl-legend-hideout">
        <HideoutMark />
        {runner}?
      </span>
    </div>
  );
}
