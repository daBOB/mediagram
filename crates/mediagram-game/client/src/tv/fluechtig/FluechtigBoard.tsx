import { useMemo, useState, type CSSProperties } from "react";
import type { FluechtigScene } from "../../protocol/FluechtigScene";
import type { FluechtigSpot } from "../../protocol/FluechtigSpot";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { figureOf } from "../../ui/fluechtig-naming";
import { avatarFigureUrl } from "../../ui/Token";
import { fieldRings, planLines } from "./plan-lines";
import { stadtplanArt } from "./stadtplan-art";
import { StadtplanGround } from "./StadtplanGround";

/** Room above and below the plan for the TV's panels. */
const PANELS = 64;

/**
 * A piece on the plan: its player's avatar figure, standing beside its field
 * on a base in the player's colour, so the field's number stays readable. On
 * the top row it stands lower, clear of the TV's panels; at the plan's right
 * edge (`width`) it stands to the left of its field. `badge` is the figure's
 * number when its player has two, or the round a fugitive was last seen in.
 */
function Piece({ at, r, width, player, badge, look = "" }: { at: FluechtigSpot; r: number; width: number; player?: PlayerInfo; badge?: string; look?: string }) {
  const [failed, setFailed] = useState<string | null>(null);
  const figure = player?.avatar && failed !== player.avatar ? player.avatar : null;
  const wide = (badge?.length ?? 0) > 1;
  const tall = r * 4.3;
  const side = at.x > width - r * 3.5 ? -1 : 1;
  const feet = { x: at.x + side * r * 2, y: at.y + r * 0.55 + Math.max(0, r * 3.9 - at.y) };
  return (
    <g className={`fl-pawn ${look}`} style={{ transform: `translate(${feet.x}px, ${feet.y}px)` }}>
      <ellipse className="fl-pawn-base" rx={r * 1.05} ry={r * 0.5} fill={player?.color ?? "#888"} />
      {figure ? (
        <image href={avatarFigureUrl(figure)} x={-tall / 3} y={r * 0.25 - tall} width={(tall * 2) / 3} height={tall} onError={() => setFailed(figure)} />
      ) : (
        // no figure to show: a plain pawn with the player's initial
        <>
          <circle className="fl-pawn-head" cy={-r * 1.5} r={r * 1.15} fill={player?.color ?? "#888"} />
          <text className="fl-pawn-initial" y={-r * 1.1} fontSize={r * 1.2}>
            {[...(player?.name.trim() ?? "")][0]?.toUpperCase() ?? "?"}
          </text>
        </>
      )}
      {badge && (
        <>
          <circle className="fl-badge" cx={side * r} cy={r * 0.1} r={r * (wide ? 0.66 : 0.56)} />
          <text className="fl-badge-text" x={side * r} y={r * 0.37} fontSize={r * (wide ? 0.66 : 0.78)}>
            {badge}
          </text>
        </>
      )}
    </g>
  );
}

/**
 * The city plan: the fields, the hunters as their players' avatar figures, the
 * fields the fugitive may be on, where they were last seen, and at the end the
 * route they ran.
 */
export function FluechtigBoard({ scene, players }: { scene: FluechtigScene; players: PlayerInfo[] }) {
  const { board } = scene;
  // the city is the same for a whole chase: its ground is worked out once
  const city = `${board.width}x${board.height}/${board.spots.length}/${board.links.length}`;
  const { art, lines, rings } = useMemo(() => ({ art: stadtplanArt(board), lines: planLines(board), rings: fieldRings(board) }), [city]);
  const r = art.radius;
  const spot = (field: number) => board.spots[field - 1];
  const player = (id: number) => players.find((p) => p.id === id);
  const over = scene.turn.turn === "over";
  const hideouts = new Set(over ? [] : scene.hideouts);
  const targets = new Set(scene.targets);
  const mover = scene.turn.turn === "hunter" ? scene.hunters[scene.turn.index] : null;
  const seen = over ? undefined : [...scene.log].reverse().find((e) => e.seen_at !== null);
  const route = scene.route;
  const runner = player(scene.fugitive);

  return (
    <svg className="fl-board" viewBox={`-10 ${-PANELS} ${board.width + 20} ${board.height + 2 * PANELS}`} role="img" aria-label="Stadtplan">
      <StadtplanGround art={art} lines={lines} />
      {route && <polyline className="fl-route" points={route.map((f) => `${spot(f).x},${spot(f).y}`).join(" ")} />}
      {board.spots.map((s, i) => {
        const f = i + 1;
        const target = targets.has(f) && mover;
        return (
          <g key={f}>
            {hideouts.has(f) && <circle className="fl-hideout" cx={s.x} cy={s.y} r={r * 1.45} />}
            <circle
              className={`fl-field fl-field-${rings.get(f) ?? "taxi"}${target ? " fl-target" : ""}`}
              style={target ? ({ "--mover": player(mover.player)?.color ?? "#888" } as CSSProperties) : undefined}
              cx={s.x}
              cy={s.y}
              r={r}
            />
            <text className="fl-num" x={s.x} y={s.y + r * 0.33} fontSize={Math.min(12, r) - (f > 99 ? 1 : 0)}>
              {f}
            </text>
          </g>
        );
      })}
      {seen?.seen_at != null && <Piece at={spot(seen.seen_at)} r={r} width={board.width} player={runner} badge={`R${seen.round}`} look="fl-pawn-seen" />}
      {scene.hunters.map((h, i) => (
        <Piece key={i} at={spot(h.at)} r={r} width={board.width} player={player(h.player)} badge={figureOf(scene.hunters, i)?.toString()} look={mover === h ? "fl-pawn-now" : ""} />
      ))}
      {route && <Piece at={spot(route[route.length - 1])} r={r} width={board.width} player={runner} />}
    </svg>
  );
}
