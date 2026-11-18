import "@fontsource/bricolage-grotesque/latin-600.css";
import "@fontsource/bricolage-grotesque/latin-800.css";
import "@fontsource/atkinson-hyperlegible/latin-400.css";
import "@fontsource/atkinson-hyperlegible/latin-700.css";
import "./styles.css";
import "./lobby.css";
import { createRoot } from "react-dom/client";
import { TvKopfnuss } from "./tv/TvKopfnuss";
import type { KopfnussScene } from "./protocol/KopfnussScene";
import type { KopfnussStage } from "./protocol/KopfnussStage";
import type { PlayerInfo } from "./protocol/PlayerInfo";
import type { Connection } from "./net";

document.documentElement.dataset.surface = "tv";
const q = new URLSearchParams(location.search);
const stage = q.get("stage") ?? "pick";
const ids = ["fox", "witch", "ghost", "panda", "princess", "android"];
const colors = ["#f2862e", "#7746bb", "#52a8ff", "#e5487f", "#56e3cf", "#f6c731"];
const players: PlayerInfo[] = ids.map((id, i) => ({ id: i + 1, name: id, color: colors[i], score: 1200 * i, connected: true, leader: i === 0, avatar: id }));
const now = Date.now();
const conn = { serverNow: () => Date.now() } as unknown as Connection;
const question = { category: "Wissen", text: "Die Freiheitsstatue war ein Geschenk aus welchem Land?", options: ["Frankreich", "Jamaica", "Kanada", "Japan"], image: null };
const cats = ["Sport", "Jamaica", "Weltrekorde", "2016"];
const stages: Record<string, KopfnussStage> = {
  pick: { stage: "pick", categories: cats, done: [1, 3], closes_at: now + 43000 },
  resolve: { stage: "resolve", categories: cats, votes: [0, 2, 1, 0], chosen: 1, hits: [{ from: 1, to: 2, trick: "spiegel", bounced: false }], ends_at: now + 800000 },
  question: { stage: "question", category: "Wissen", quiz: { round: 1, rounds: 3, stage: { stage: "open", question, opens_at: now - 2000, closes_at: now + 46000, answered: [1, 2] } }, tricks: [] },
  reveal: { stage: "question", category: "Wissen", quiz: { round: 1, rounds: 3, stage: { stage: "reveal", question, correct: 0, results: players.map((p, i) => ({ player: p.id, choice: i % 4, ms: 1500 + i * 400, points: i % 4 === 0 ? 520 : 0 })), ends_at: now + 900000 } }, tricks: [] },
  climb: { stage: "climb", steps: 10, climbers: [{ player: 1, step: 0 }, { player: 2, step: 3 }, { player: 3, step: 3 }, { player: 4, step: 6 }, { player: 5, step: 8 }, { player: 6, step: 10 }], ends_at: now + 40000 },
  fact: { stage: "fact", winner: 5, text: "Frankreich schenkte die Freiheitsstatue den USA im Jahr 1886.", ends_at: now + 900000 },
};
const scene = { round: 1, rounds: 3, stage: stages[stage] } as KopfnussScene;
if (q.get("bare")) document.documentElement.classList.add("bare"); // the stage alone, without the HTML on top
createRoot(document.getElementById("root")!).render(<TvKopfnuss scene={scene} players={players} conn={conn} />);
const started = performance.now();
const poll = setInterval(() => {
  const canvas = document.querySelector<HTMLCanvasElement>("canvas.kn-arena-canvas");
  if (canvas?.dataset.ready === "true") {
    document.title = "ready";
    clearInterval(poll);
  } else if (performance.now() - started > 30000) {
    document.title = "ready-fallback " + (canvas?.dataset.ready ?? "no-canvas");
    clearInterval(poll);
  }
}, 200);
