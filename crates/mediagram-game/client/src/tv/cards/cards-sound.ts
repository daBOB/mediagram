// The card table's sound on the TV: the race board's music beds and TV sound
// settings, a wooden knock where each card lands, and the winner's jingle.
// Sound is extra: if any of it fails to load, the table plays silent.

import type { CardsScene } from "../../protocol/CardsScene";
import { effectsGain, getSettings, musicVolume, subscribeSettings, type TvSettings } from "../settings/tv-settings";
import { DRAW_MS, DRAW_STAGGER_MS, PLAY_MS } from "./cards-motion";

const DIR = "/race/sound/";
const KNOCKS = [1, 2, 3, 4, 5].map((i) => `pawn-knock-${i}`);
const MUSIC = ["music-apple-cider", "music-cozy-puzzle-2"];
const KNOCK_GAIN = 0.4, JINGLE_GAIN = 0.8;
/** A cue this late is skipped: a TV joining mid-game stays quiet rather than replay it. */
const LATE_S = 0.03;
/** A frame drawn now is on the screen about two vsyncs later. */
const SCREEN_DELAY_MS = 33;

export class CardsSound {
  private readonly ctx = new AudioContext();
  private readonly music = new Audio();
  private readonly master = this.ctx.createGain();
  private musicTarget = musicVolume(getSettings());
  private readonly unsubscribe = subscribeSettings((s) => this.applySettings(s));
  private knocks: AudioBuffer[] = [];
  private jingle: AudioBuffer | null = null;
  private tracks: string[] = [];
  private track = 0;
  private heard = new Set<number>();
  private won = false;
  private fading = 0;
  private ending = false;
  private lagMs = NaN;
  private disposed = false;
  // a browser TV page without the WebView's autoplay setting starts muted until a key or tap
  private readonly unlock = () => {
    void this.ctx.resume();
    if (this.music.paused && this.music.src) this.music.play().catch(() => {});
  };

  async load(): Promise<this> {
    this.master.gain.value = effectsGain(getSettings());
    this.master.connect(this.ctx.destination);
    [this.knocks, [this.jingle]] = await Promise.all([
      Promise.all(KNOCKS.map((f) => this.decode(f))),
      Promise.all([this.decode("winner-jingle")]),
    ]);
    const tracks = await Promise.all(MUSIC.map(async (m) => URL.createObjectURL(await (await this.fetchFile(m)).blob())));
    if (this.disposed) {
      tracks.forEach((t) => URL.revokeObjectURL(t));
      throw new Error("card sound loaded after the table was gone");
    }
    this.tracks = tracks;
    addEventListener("keydown", this.unlock);
    addEventListener("pointerdown", this.unlock);
    return this;
  }

  startMusic() {
    this.music.volume = 0;
    this.music.onended = () => {
      this.track = (this.track + 1) % this.tracks.length;
      this.music.src = this.tracks[this.track];
      this.music.play().catch(() => {});
    };
    this.music.src = this.tracks[0];
    this.music.play().catch(() => {});
    this.fade(this.musicTarget, 2000);
  }

  /** Called every frame: schedules each new event's knocks once, and the winner's jingle. `now`: host time, ms. */
  update(scene: CardsScene, now: number) {
    this.measureLag();
    const lagMs = Number.isNaN(this.lagMs) ? (this.ctx.outputLatency || this.ctx.baseLatency || 0) * 1000 : this.lagMs;
    const at = (hostMs: number) => this.ctx.currentTime + (hostMs - now + SCREEN_DELAY_MS - lagMs) / 1000;
    for (const ev of scene.events) {
      if (this.heard.has(ev.id)) continue;
      this.heard.add(ev.id);
      const k = ev.kind;
      if (k.kind === "play") this.knock(at(ev.at + PLAY_MS));
      else if (k.kind === "draw") for (let j = 0; j < k.count; j++) this.knock(at(ev.at + j * DRAW_STAGGER_MS + DRAW_MS));
    }
    if (scene.ranking && !this.won) {
      this.won = true;
      this.ending = true;
      this.fade(0, 3500);
      this.play(this.jingle, this.ctx.currentTime, JINGLE_GAIN);
    }
  }

  private measureLag() {
    const { contextTime, performanceTime } = this.ctx.getOutputTimestamp();
    if (contextTime === undefined || !performanceTime) return;
    const lag = performanceTime + (this.ctx.currentTime - contextTime) * 1000 - performance.now();
    this.lagMs = Number.isNaN(this.lagMs) ? lag : this.lagMs + (lag - this.lagMs) * 0.05;
  }

  private knock(when: number) {
    if (when < this.ctx.currentTime - LATE_S) return;
    this.play(this.knocks[Math.floor(Math.random() * this.knocks.length)], Math.max(this.ctx.currentTime, when), KNOCK_GAIN, 0.94 + Math.random() * 0.12);
  }

  private play(buffer: AudioBuffer | null | undefined, when: number, gain: number, rate = 1) {
    if (!buffer) return;
    const src = this.ctx.createBufferSource();
    src.buffer = buffer;
    src.playbackRate.value = rate;
    const g = this.ctx.createGain();
    g.gain.value = gain;
    src.connect(g).connect(this.master);
    src.start(when);
  }

  private applySettings(s: TvSettings) {
    this.master.gain.setTargetAtTime(effectsGain(s), this.ctx.currentTime, 0.02);
    this.musicTarget = musicVolume(s);
    if (!this.ending) this.fade(this.musicTarget, 150);
  }

  dispose() {
    this.disposed = true;
    this.unsubscribe();
    clearInterval(this.fading);
    removeEventListener("keydown", this.unlock);
    removeEventListener("pointerdown", this.unlock);
    this.music.onended = null;
    this.music.pause();
    this.music.removeAttribute("src");
    this.music.load();
    this.tracks.forEach((t) => URL.revokeObjectURL(t));
    void this.ctx.close();
  }

  private async fetchFile(name: string): Promise<Response> {
    const res = await fetch(`${DIR}${name}.ogg`);
    if (!res.ok) throw new Error(`${name}: ${res.status}`);
    return res;
  }

  private async decode(name: string): Promise<AudioBuffer> {
    return this.ctx.decodeAudioData(await (await this.fetchFile(name)).arrayBuffer());
  }

  private fade(to: number, ms: number) {
    clearInterval(this.fading);
    const from = this.music.volume, start = performance.now();
    this.fading = window.setInterval(() => {
      const k = Math.min(1, (performance.now() - start) / ms);
      this.music.volume = from + (to - from) * k;
      if (k === 1) clearInterval(this.fading);
    }, 50);
  }
}
