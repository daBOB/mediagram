import { useMemo, type CSSProperties } from "react";
import type { FluechtigScene } from "../../protocol/FluechtigScene";
import type { FluechtigSpot } from "../../protocol/FluechtigSpot";
import type { PlayerInfo } from "../../protocol/PlayerInfo";

/** Bus and U-Bahn links curve, so they do not run over the fields between their stops. */
const BEND = { taxi: 0, bus: 22, ubahn: 42, schatten: 0 } as const;
// the river runs past the plan to both screen edges, which sit beyond the padded view
const RIVER = "M-240 270 L0 268 C 160 246, 320 294, 480 270 S 800 246, 960 272 L1200 272";
/** The plan with room above and below for the TV's panels. */
const VIEW = "-10 -64 980 668";

function linkPath(a: FluechtigSpot, b: FluechtigSpot, bend: number) {
  if (!bend) return `M${a.x} ${a.y}L${b.x} ${b.y}`;
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len = Math.hypot(dx, dy);
  return `M${a.x} ${a.y}Q${(a.x + b.x) / 2 - (dy / len) * bend} ${(a.y + b.y) / 2 + (dx / len) * bend} ${b.x} ${b.y}`;
}

/**
 * The city plan: links by transport, the fields, hunters in their players'
 * colours, the fields the fugitive may be on, and at the end the route they ran.
 */
export function FluechtigBoard({ scene, players }: { scene: FluechtigScene; players: PlayerInfo[] }) {
  const { board } = scene;
  const spot = (field: number) => board.spots[field - 1];
  const colour = (id: number) => players.find((p) => p.id === id)?.color ?? "#888";
  // the strongest transport at each field marks its ring: U-Bahn over bus over taxi
  const kind = useMemo(() => {
    const k = new Map<number, string>();
    for (const l of board.links) {
      for (const f of [l.a, l.b]) if (l.kind === "ubahn" || (l.kind === "bus" && k.get(f) !== "ubahn")) k.set(f, l.kind);
    }
    return k;
  }, [board]);
  const over = scene.turn.turn === "over";
  const hideouts = new Set(over ? [] : scene.hideouts);
  const targets = new Set(scene.targets);
  const mover = scene.turn.turn === "hunter" ? scene.hunters[scene.turn.index] : null;
  const seen = over ? undefined : [...scene.log].reverse().find((e) => e.seen_at !== null);
  const route = scene.route;
  const end = route ? spot(route[route.length - 1]) : null;

  return (
    <svg className="fl-board" viewBox={VIEW} role="img" aria-label="Stadtplan">
      <path className="fl-river" d={RIVER} />
      {(["ubahn", "bus", "taxi"] as const).map((k) =>
        board.links
          .filter((l) => l.kind === k)
          .map((l) => <path key={`${k}${l.a}-${l.b}`} className={`fl-link fl-link-${k}`} d={linkPath(spot(l.a), spot(l.b), BEND[k])} />),
      )}
      {route && <polyline className="fl-route" points={route.map((f) => `${spot(f).x},${spot(f).y}`).join(" ")} />}
      {board.spots.map((s, i) => {
        const f = i + 1;
        const target = targets.has(f) && mover;
        return (
          <g key={f}>
            {hideouts.has(f) && <circle className="fl-hideout" cx={s.x} cy={s.y} r={21} />}
            <circle
              className={`fl-field fl-field-${kind.get(f) ?? "taxi"}${target ? " fl-target" : ""}`}
              style={target ? ({ "--mover": colour(mover.player) } as CSSProperties) : undefined}
              cx={s.x}
              cy={s.y}
              r={14}
            />
            <text className="fl-num" x={s.x} y={s.y + 4}>
              {f}
            </text>
          </g>
        );
      })}
      {seen?.seen_at != null && (
        // under its field, but above on the bottom row, where the TV's lower panels sit
        <text className="fl-seen" x={spot(seen.seen_at).x} y={spot(seen.seen_at).y + (spot(seen.seen_at).y > 450 ? -26 : 34)}>
          gesehen R{seen.round}
        </text>
      )}
      {scene.hunters.map((h, i) => (
        <g key={i} className="fl-pawn" style={{ transform: `translate(${spot(h.at).x + 13}px, ${spot(h.at).y - 13}px)` }}>
          <circle r={11} fill={colour(h.player)} />
          <text y={4}>{i + 1}</text>
        </g>
      ))}
      {end && (
        <g className="fl-pawn fl-runner" style={{ transform: `translate(${end.x - 13}px, ${end.y - 13}px)` }}>
          <circle r={11} />
          <text y={4}>F</text>
        </g>
      )}
    </svg>
  );
}
