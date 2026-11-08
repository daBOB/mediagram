// Kopfnuss's sound on the TV: short game-show voices synthesised in Web Audio
// (no files to license), plus the race board's wooden knock for each step up
// the mountain and its goal jingle on the summit. Sound is extra: whatever
// fails to load or play leaves the game silent, never broken.

import type { KopfnussScene } from "../../protocol/KopfnussScene";
import { effectsGain, getSettings, subscribeSettings } from "../settings/tv-settings";
import { newSoundState, soundsDue, type SoundName } from "./kopfnuss-sound-cues";

const FILES: Partial<Record<SoundName, { urls: string[]; gain: number }>> = {
  step: { urls: [1, 2, 3, 4, 5].map((i) => `/race/sound/pawn-knock-${i}.ogg`), gain: 0.55 },
  summit: { urls: ["/race/sound/goal-jingle.ogg"], gain: 0.7 },
};

/** One synthesised note: pitch `f` (sliding to `to`), starting `t` s in, lasting `d` s. */
type Note = { f: number; to?: number; t: number; d: number; type?: OscillatorType; g?: number };

const VOICES: Partial<Record<SoundName, Note[]>> = {
  // two rising notes: a new round
  round: [{ f: 659, t: 0, d: 0.18 }, { f: 880, t: 0.12, d: 0.32 }],
  // a soft click as a phone finishes its picks
  pickDone: [{ f: 1319, t: 0, d: 0.07, type: "triangle", g: 0.25 }],
  // a C-major ta-da for the chosen category
  chosen: [{ f: 523, t: 0, d: 0.16 }, { f: 659, t: 0.1, d: 0.16 }, { f: 784, t: 0.2, d: 0.5 }],
  // a falling zap as a trick lands
  zap: [{ f: 1400, to: 220, t: 0, d: 0.24, type: "sawtooth", g: 0.12 }],
  // a spring for a trick that bounces off
  boing: [{ f: 160, to: 440, t: 0, d: 0.12 }, { f: 440, to: 150, t: 0.12, d: 0.32 }],
  // the answer buttons light up
  go: [{ f: 988, t: 0, d: 0.12, type: "square", g: 0.1 }],
  // the last three seconds
  tick: [{ f: 1000, t: 0, d: 0.05, type: "triangle", g: 0.25 }],
  // the answer is shown: a bell with an overtone
  reveal: [{ f: 1047, t: 0, d: 0.9, g: 0.3 }, { f: 1568, t: 0, d: 0.6, g: 0.1 }],
};

/** A frame drawn now is on the screen about two vsyncs later. */
const SCREEN_DELAY_MS = 33;

export class KopfnussSound {
  private readonly ctx = new AudioContext();
  /** Every sound runs through this one gain: the TV's effects level and mute. */
  private readonly master = this.ctx.createGain();
  private readonly buffers = new Map<SoundName, AudioBuffer[]>();
  private readonly state = newSoundState();
  private readonly unsubscribe = subscribeSettings((s) => this.master.gain.setTargetAtTime(effectsGain(s), this.ctx.currentTime, 0.02));
  // a browser TV page without the WebView's autoplay setting starts muted until a key or tap
  private readonly unlock = () => void this.ctx.resume();

  constructor() {
    this.master.gain.value = effectsGain(getSettings());
    this.master.connect(this.ctx.destination);
    addEventListener("keydown", this.unlock);
    addEventListener("pointerdown", this.unlock);
  }

  /** Loads the two file sounds; the synthesised ones need nothing. */
  async load(): Promise<void> {
    await Promise.all(
      Object.entries(FILES).map(async ([name, f]) => {
        const takes = await Promise.all(f.urls.map(async (u) => this.ctx.decodeAudioData(await (await fetch(u)).arrayBuffer())));
        this.buffers.set(name as SoundName, takes);
      }),
    );
  }

  /** Called every frame: starts the scene's due sounds, each once. `now`: host time, ms. */
  update(scene: KopfnussScene, now: number) {
    // started early by the speaker's delay, so a sound lands with its picture
    const lagMs = (this.ctx.outputLatency || this.ctx.baseLatency || 0) * 1000;
    for (const cue of soundsDue(this.state, scene, now, Math.max(150, lagMs + 50))) {
      const when = this.ctx.currentTime + Math.max(0, cue.at - now + SCREEN_DELAY_MS - lagMs) / 1000;
      this.play(cue.sound, when);
    }
  }

  dispose() {
    this.unsubscribe();
    removeEventListener("keydown", this.unlock);
    removeEventListener("pointerdown", this.unlock);
    void this.ctx.close();
  }

  private play(name: SoundName, when: number) {
    const takes = this.buffers.get(name);
    if (takes?.length) {
      const src = this.ctx.createBufferSource();
      src.buffer = takes[Math.floor(Math.random() * takes.length)];
      if (name === "step") src.playbackRate.value = 0.94 + Math.random() * 0.12;
      const gain = this.ctx.createGain();
      gain.gain.value = FILES[name]?.gain ?? 0.6;
      src.connect(gain).connect(this.master);
      src.start(when);
      return;
    }
    for (const n of VOICES[name] ?? []) this.note(n, when);
  }

  /** One note: a 5 ms attack, then an exponential fade over its length. */
  private note(n: Note, when: number) {
    const start = when + n.t;
    const osc = this.ctx.createOscillator();
    osc.type = n.type ?? "sine";
    osc.frequency.setValueAtTime(n.f, start);
    if (n.to) osc.frequency.exponentialRampToValueAtTime(n.to, start + n.d);
    const env = this.ctx.createGain();
    env.gain.setValueAtTime(0.0001, start);
    env.gain.exponentialRampToValueAtTime(n.g ?? 0.3, start + 0.005);
    env.gain.exponentialRampToValueAtTime(0.0001, start + n.d);
    osc.connect(env).connect(this.master);
    osc.start(start);
    osc.stop(start + n.d + 0.02);
  }
}
