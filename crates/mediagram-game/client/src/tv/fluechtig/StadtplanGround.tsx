import { memo, type CSSProperties } from "react";
import type { planLines } from "./plan-lines";
import type { StadtplanArt } from "./stadtplan-art";

/**
 * The city plan's ground: blocks, parks, the river with its ferry, the
 * streets and the bus and U-Bahn lines. None of it changes during a chase,
 * so it is drawn once and left alone while pieces move above it.
 */
export const StadtplanGround = memo(function StadtplanGround({ art, lines }: { art: StadtplanArt; lines: ReturnType<typeof planLines> }) {
  const u = art.radius / 11.5;
  return (
    <g className="sp" style={{ "--u": u } as CSSProperties}>
      {art.tones.map((d, i) => (
        <path key={i} className={`sp-block sp-tone-${i}`} d={d} />
      ))}
      <path className="sp-yard" d={art.yards} />
      <path className="sp-park" d={art.parks} />
      {art.trees.map((t, i) => (
        <circle key={i} className="sp-tree" cx={t.x.toFixed(1)} cy={t.y.toFixed(1)} r={t.r.toFixed(1)} />
      ))}
      <path className="sp-bank" d={lines.river} />
      <path className="sp-water" d={lines.river} />
      <path className="sp-ferry" d={lines.ferry} />
      <path className="sp-jetty" d={lines.jetties} />
      {art.ferryName && (
        <text className="sp-ferry-name" x={art.ferryName.x} y={art.ferryName.y + 4 * u}>
          Fähre
        </text>
      )}
      {art.signs.map((s, i) => (
        <path key={i} className="sp-sign" d={s.d} transform={`translate(${s.x.toFixed(1)} ${s.y.toFixed(1)}) scale(${u.toFixed(2)})`} />
      ))}
      <path className="sp-casing" d={lines.streets} />
      <path className="sp-street" d={lines.streets} />
      {art.names.map((n) => (
        <text key={n.text} className="sp-name" x={n.x.toFixed(0)} y={(n.y + 5 * u).toFixed(0)}>
          {n.text}
        </text>
      ))}
      <path className="sp-ubahn" d={lines.ubahn} />
      <path className="sp-bus" d={lines.bus} />
    </g>
  );
});
