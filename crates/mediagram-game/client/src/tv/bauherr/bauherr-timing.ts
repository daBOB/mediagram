// Timing of the board on the TV box, to see where a slow start or a stall goes: performance marks of the start, and a log of
// the slow frames of play. Both go to the console and, in a build made with VITE_BAUHERR_BEACON=<url> (a dev machine
// listening for POSTs), over a beacon: the box's WebView shows its console nowhere a script can read it.

const BEACON = (import.meta as { env?: Record<string, string | undefined> }).env?.VITE_BAUHERR_BEACON;
function emit(text: string) {
  console.info(text);
  if (BEACON) navigator.sendBeacon(BEACON, text);
}

export const mark = (name: string) => performance.mark(`bauherr:${name}`);

/** The start's marks, seconds since the board's scene appeared. */
export function reportStart() {
  const marks = performance.getEntriesByType("mark").filter((e) => e.name.startsWith("bauherr:"));
  const t0 = marks.find((e) => e.name === "bauherr:mount")?.startTime ?? 0;
  emit(`bauherr start ${marks.map((e) => `${e.name.slice(8)} ${((e.startTime - t0) / 1000).toFixed(2)}`).join(" | ")}`);
}

interface Spike {
  /** Seconds since the board's scene appeared. */
  at: number;
  dt: number;
  phases: string;
  what: string;
}
const SLOW_MS = 25, REPORT_MS = 30_000;
const sinceMount = () => (performance.now() - (performance.getEntriesByName("bauherr:mount")[0]?.startTime ?? 0)) / 1000;

/** Times every frame by phase and, every half minute, reports how many were slow and what the worst ones were doing. */
export class FrameLog {
  private last = 0;
  private at = 0;
  private phases: string[] = [];
  private frames = 0;
  private slow = 0;
  private worst: Spike[] = [];
  private since = performance.now();
  private tasks = 0;
  private dt = 0;

  constructor() {
    // tasks of 50 ms and more on the main thread, whoever caused them
    try {
      new PerformanceObserver((l) => (this.tasks += l.getEntries().length)).observe({ entryTypes: ["longtask"] });
    } catch {
      /* a WebView without long task entries: the frame times still tell */
    }
  }

  /** Starts a frame (`ms`: the animation frame's time). */
  begin(ms: number) {
    this.phases = [];
    this.at = performance.now();
    this.dt = this.last ? ms - this.last : 0;
    this.last = ms;
  }
  /** Ends a phase of the frame. */
  phase(name: string) {
    const now = performance.now();
    this.phases.push(`${name} ${(now - this.at).toFixed(1)}`);
    this.at = now;
  }
  /** Ends the frame; `what` says what the game was doing. */
  end(what: string) {
    this.frames++;
    if (this.dt > SLOW_MS) {
      this.slow++;
      this.worst.push({ at: sinceMount(), dt: this.dt, phases: this.phases.join(", "), what });
      this.worst.sort((a, b) => b.dt - a.dt).length = Math.min(this.worst.length, 6);
    }
    if (performance.now() - this.since > REPORT_MS) {
      emit(`bauherr frames ${this.frames} slow(>${SLOW_MS}ms) ${this.slow} longtasks ${this.tasks} worst: ${this.worst.map((s) => `t=${s.at.toFixed(1)}s ${s.dt.toFixed(0)}ms [${s.what}] ${s.phases}`).join(" | ")}`);
      this.frames = this.slow = this.tasks = 0;
      this.worst = [];
      this.since = performance.now();
    }
  }
}
