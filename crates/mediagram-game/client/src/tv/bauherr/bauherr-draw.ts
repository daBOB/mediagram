import type { BauherrScene } from "../../protocol/BauherrScene";
import type { BauherrTileKind } from "../../protocol/BauherrTileKind";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { ANSWER_MARKS } from "../../ui/answers";

/** Logical canvas size: the 10 x 10 board and a margin; CSS scales it. */
export const W = 520;
export const H = 520;
const TILE = 50;
const BX = 10;
const BY = 10;

const KIND_FILL: Record<BauherrTileKind, string> = {
  bank: "#f2c94c",
  rathaus: "#b58ae6",
  bauplatz: "#8fd694",
  baustelle: "#f2a65a",
  eingang: "#7fb6f2",
  strasse: "#f1e9d8",
};
const KIND_LABEL: Record<BauherrTileKind, string> = {
  bank: "Bank",
  rathaus: "Rathaus",
  bauplatz: "Platz",
  baustelle: "Bau",
  eingang: "Tür",
  strasse: "",
};
const MARK_FILL = { blue: "#2c6bed", orange: "#f2862e", yellow: "#f6c731", pink: "#e5487f" } as const;
const OFFSETS = [[-9, -9], [9, -9], [-9, 9], [9, 9]];

export const taler = (n: number) => `${n.toLocaleString("de-DE")} Taler`;

const centre = (scene: BauherrScene, id: number) => {
  const t = scene.board.tiles[id];
  return { x: BX + (t.x + 0.5) * TILE, y: BY + (t.y + 0.5) * TILE };
};

/** Where a pawn is drawn: mid-hop while its owner walks, else on its field. */
function pawnAt(scene: BauherrScene, player: number, field: number, now: number) {
  const turn = scene.turn;
  if (turn.stage !== "walking" || turn.player !== player) return centre(scene, field);
  const progress = Math.min(Math.max((now - turn.started_at) / turn.hop_ms, 0), turn.value);
  const whole = Math.floor(progress);
  let at = turn.from;
  for (let i = 0; i < whole; i++) at = scene.board.tiles[at].exits[0];
  const a = centre(scene, at);
  if (whole >= turn.value) return a;
  const b = centre(scene, scene.board.tiles[at].exits[0]);
  const f = progress - whole;
  return { x: a.x + (b.x - a.x) * f, y: a.y + (b.y - a.y) * f };
}

function drawTiles(g: CanvasRenderingContext2D, scene: BauherrScene) {
  g.textAlign = "center";
  for (const t of scene.board.tiles) {
    const x = BX + t.x * TILE;
    const y = BY + t.y * TILE;
    g.fillStyle = KIND_FILL[t.kind];
    g.fillRect(x + 1, y + 1, TILE - 2, TILE - 2);
    g.fillStyle = "#3a2a1a";
    g.font = "bold 10px sans-serif";
    g.fillText(KIND_LABEL[t.kind], x + TILE / 2, y + TILE - 6);
    g.font = "9px sans-serif";
    g.textAlign = "left";
    g.fillText(String(t.id + 1), x + 4, y + 11);
    g.textAlign = "center";
  }
}

function drawPlots(g: CanvasRenderingContext2D, scene: BauherrScene, byId: Map<number, PlayerInfo>) {
  for (const p of scene.board.plots) {
    const estate = scene.estates.find((e) => e.plot === p.id);
    const x = BX + p.x * TILE;
    const y = BY + p.y * TILE;
    g.fillStyle = estate ? (byId.get(estate.owner)?.color ?? "#999") : "#d9d2c2";
    g.fillRect(x + 3, y + 3, p.w * TILE - 6, p.h * TILE - 6);
    g.fillStyle = "#1c1208";
    g.font = "bold 12px sans-serif";
    g.fillText(p.name, x + (p.w * TILE) / 2, y + (p.h * TILE) / 2 - 2);
    g.font = "10px sans-serif";
    g.fillText(estate ? `Stufe ${estate.levels} von ${p.levels.length}` : taler(p.land), x + (p.w * TILE) / 2, y + (p.h * TILE) / 2 + 12);
  }
  for (const e of scene.entrances) {
    const c = centre(scene, e.field);
    const owner = scene.estates.find((s) => s.plot === e.plot)?.owner;
    g.fillStyle = byId.get(owner ?? -1)?.color ?? "#999";
    g.strokeStyle = "#fff";
    g.lineWidth = 2;
    g.fillRect(c.x + 8, c.y - 22, 12, 12);
    g.strokeRect(c.x + 8, c.y - 22, 12, 12);
  }
}

function drawMarks(g: CanvasRenderingContext2D, scene: BauherrScene) {
  const turn = scene.turn;
  if (turn.stage !== "ask") return;
  g.lineWidth = 4;
  g.font = "bold 14px sans-serif";
  turn.marks.forEach((m) => {
    const c = centre(scene, m.field);
    g.strokeStyle = MARK_FILL[m.color];
    g.strokeRect(c.x - TILE / 2 + 2, c.y - TILE / 2 + 2, TILE - 4, TILE - 4);
    g.fillStyle = MARK_FILL[m.color];
    g.fillText(ANSWER_MARKS[m.color], c.x, c.y + 5);
  });
}

function drawPawns(g: CanvasRenderingContext2D, scene: BauherrScene, byId: Map<number, PlayerInfo>, now: number) {
  scene.seats.forEach((s, i) => {
    const c = pawnAt(scene, s.player, s.field, now);
    g.globalAlpha = s.out ? 0.35 : 1;
    g.fillStyle = byId.get(s.player)?.color ?? "#999";
    g.strokeStyle = "#fff";
    g.lineWidth = 3;
    g.beginPath();
    g.arc(c.x + OFFSETS[i % 4][0], c.y + OFFSETS[i % 4][1], 9, 0, Math.PI * 2);
    g.fill();
    g.stroke();
  });
  g.globalAlpha = 1;
}

export function drawBauherr(g: CanvasRenderingContext2D, scene: BauherrScene, players: PlayerInfo[], now: number) {
  const byId = new Map(players.map((p) => [p.id, p]));
  g.clearRect(0, 0, W, H);
  g.fillStyle = "#2b3a2c";
  g.fillRect(0, 0, W, H);
  g.fillStyle = "#c9dcb6";
  g.fillRect(BX, BY, 10 * TILE, 10 * TILE);
  drawTiles(g, scene);
  drawPlots(g, scene, byId);
  drawMarks(g, scene);
  drawPawns(g, scene, byId, now);
}
