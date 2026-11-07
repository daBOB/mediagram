// A stand-in host for looking at the Bauherr board without a game: the real board code draws a scene this page makes up
// and changes on request (window.setScene). Used by preview.mjs; nothing here ships.
import { startBauherr } from "../../../client/src/tv/bauherr/bauherr-stage";
import { FIELD_KINDS, HOTEL_FIELDS, HOTEL_NAMES } from "../../../client/src/tv/bauherr/bauherr-layout";
import type { BauherrScene } from "../../../client/src/protocol/BauherrScene";
import type { BauherrTurn } from "../../../client/src/protocol/BauherrTurn";
import type { PlayerInfo } from "../../../client/src/protocol/PlayerInfo";

/** Stages per hotel, as the rules have them. */
const STAGES = [3, 4, 4, 6, 4, 6, 5, 5];
const KIND = { B: "bank", R: "rathaus", P: "bauplatz", S: "baustelle", F: "eingang", ".": "strasse" } as const;
const players: PlayerInfo[] = [["Lena", "#e4572e", "fox"], ["Jonas", "#3fae5a", "frog"], ["Oma Gerda", "#f2b631", "granny"], ["Papa", "#3d7be8", "panda"]].map(([name, color, avatar], id) => ({ id, name, color, avatar, score: 0, connected: true, leader: id === 0 }));
const board: BauherrScene["board"] = {
  tiles: Array.from(FIELD_KINDS, (k, id) => ({ id, kind: KIND[k as keyof typeof KIND], x: 0, y: 0, exits: [(id + 1) % 36] })),
  plots: HOTEL_NAMES.map((name, id) => ({
    id, name, x: 0, y: 0, w: 0, h: 0, fields: [...HOTEL_FIELDS[id]], land: 1000, entrance: 300,
    levels: Array.from({ length: STAGES[id] }, (_, i) => ({ name: `Stufe ${i + 1}`, cost: 1000, rate: 100 })),
  })),
};
const turn: BauherrTurn = { stage: "roll", player: 0 };
let scene: BauherrScene = { board, estates: [], entrances: [], seats: players.map((p, i) => ({ player: p.id, field: i * 9, cash: 12400 - i * 2000, worth: 15000, out: false })), round: 7, rounds: 24, turn };
const offset = 1_000_000;
const now = () => performance.now() + offset;
// the page's remote control: merge `patch` into the scene; `turn` may use "@now+ms" for times relative to now
Object.assign(window, {
  now,
  setScene(patch: Partial<BauherrScene>) { scene = { ...scene, ...patch }; },
  scene: () => scene,
  players,
});
// the first scene may come in the address: #{"estates":[...],"turn":{...}}; the turn's times are offsets from now, in ms
const initial = location.hash.length > 1 ? JSON.parse(decodeURIComponent(location.hash.slice(1))) : {};
if (initial.turn) for (const k of ["rolled_at", "lands_at", "started_at", "paid_at"]) if (k in initial.turn) initial.turn[k] += now();
scene = { ...scene, ...initial };
const box = document.getElementById("stage")!;
const flag = window as unknown as { ready?: boolean; failed?: string; info?: () => unknown };
// ?report posts the start's timing marks (ms since navigation) to the server's /log, for runs on the TV box
const report = (m: string) => void fetch("/log", { method: "POST", body: m });
// a fixed camera ({ pos, at }) for looking at one corner of the town: in the address as "cam", or set later on window.camPose
const flagCam = window as unknown as { camPose?: { pos: [number, number, number]; at: [number, number, number] } | null };
flagCam.camPose = initial.cam ?? null;
// ?stress=N draws the town N more times a frame, and ?report then also posts the frame rate over 15 s: the room the GPU has left
const stress = Number(new URLSearchParams(location.search).get("stress")) || 0;
startBauherr(box, () => ({ scene, players }), now, { camera: () => flagCam.camPose ?? null, extraPasses: stress }).then((h) => {
  flag.info = h.stats;
  flag.ready = true;
  if (location.search.includes("report")) {
    // two more frames, then the marks
    requestAnimationFrame(() => requestAnimationFrame(() => {
      const marks = performance.getEntriesByType("mark").filter((e) => e.name.startsWith("bauherr:")).map((e) => `${e.name.slice(8)} ${(e.startTime / 1000).toFixed(2)}`);
      report(`MARKS ${innerWidth}x${innerHeight}@${devicePixelRatio} ${marks.join(" | ")}`);
      // five seconds to settle, then count frames for fifteen
      setTimeout(() => {
        let n = 0, slow = 0, last = performance.now();
        const t0 = last, tick = (t: number) => {
          n++;
          if (t - last > 25) slow++;
          last = t;
          if (t - t0 < 15000) requestAnimationFrame(tick);
          else report(`FPS ${(n / ((t - t0) / 1000)).toFixed(1)} slow(>25ms) ${slow} stress ${stress} ${location.search}`);
        };
        requestAnimationFrame(tick);
      }, 5000);
    }));
  }
}, (e) => { flag.failed = String(e?.stack ?? e); report(`FAILED ${flag.failed}`); });
