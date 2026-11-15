// A 3D board's sound on the TV: short effects through Web Audio, scheduled on the audio clock at the host's times, and quiet
// background music through an <audio> element (decoding minutes of music into Web Audio would hold ~77 MB on the box). Each
// game brings its effects and its cues. Sound is extra: if any of it fails to load, the board plays silent.

import { effectsGain, getSettings, musicVolume, subscribeSettings, type TvSettings } from "../settings/tv-settings";

const DIR = "/race/sound/";
/** Played in turn, so a long game hears ~5.5 minutes before a repeat. */
const MUSIC = ["music-apple-cider", "music-cozy-puzzle-2"];
/** A cue this late is skipped: a TV joining mid-turn stays quiet rather than replay it. */
const LATE_S = 0.03;
/** A frame drawn now is on the screen about two vsyncs later. */
const SCREEN_DELAY_MS = 33;

export interface Cue<N extends string> {
  sound: N;
  /** Host time, ms. */
  at: number;
}
/** The cues of one stage of play, with a key naming it so they are scheduled once however many frames show it. */
export interface Plan<N extends string> {
  key: string;
  cues: Cue<N>[];
}
/** Each effect's takes (one picked at random per play) and level; `vary`: no two plays quite alike in pitch. */
export type Effects<N extends string> = Record<N, { files: string[]; gain: number; vary?: boolean }>;

/**
 * Where on the audio clock (s) a cue at host time `at` starts so it is heard when its frame is on the screen: early by the
 * time the output takes. Null: too late to play.
 */
export function cueStart(at: number, now: number, audioNow: number, lagMs: number): number | null {
  const when = audioNow + (at - now + SCREEN_DELAY_MS - lagMs) / 1000;
  return when >= audioNow - LATE_S ? Math.max(audioNow, when) : null;
}

/** A new reading of the output lag folded into the running one: a single reading jitters by ±50 ms (the box's WebView: 320-430 ms). */
export const smoothLag = (lagMs: number, reading: number) => (Number.isNaN(lagMs) ? reading : lagMs + (reading - lagMs) * 0.05);

export class TvSound<N extends string> {
  private readonly ctx = new AudioContext();
  private readonly music = new Audio();
  /** Every effect runs through this one gain: the TV's effects level and mute. */
  private readonly master = this.ctx.createGain();
  private musicTarget = musicVolume(getSettings());
  private readonly unsubscribe = subscribeSettings((s) => this.applySettings(s));
  private buffers = new Map<N, AudioBuffer[]>();
  private tracks: string[] = [];
  private track = 0;
  private scheduled = "";
  private fading = 0;
  /** The game is over and the music is fading out: a level change must not bring it back. */
  private ending = false;
  private lagMs = NaN;
  private disposed = false;
  // a browser TV page without the WebView's autoplay setting starts muted until a key or tap
  private readonly unlock = () => {
    void this.ctx.resume();
    if (this.music.paused && this.music.src) this.music.play().catch(() => {});
  };

  constructor(private readonly effects: Effects<N>) {}

  async load(): Promise<this> {
    this.master.gain.value = effectsGain(getSettings());
    this.master.connect(this.ctx.destination);
    const entries = Object.entries(this.effects) as [N, { files: string[] }][];
    await Promise.all(entries.map(async ([name, e]) => this.buffers.set(name, await Promise.all(e.files.map((f) => this.decode(f))))));
    const tracks = await Promise.all(MUSIC.map(async (m) => URL.createObjectURL(await (await this.fetchFile(m)).blob())));
    // the board may have gone while this loaded (Back while building): free, add nothing
    if (this.disposed) {
      tracks.forEach((t) => URL.revokeObjectURL(t));
      throw new Error("board sound loaded after the board was gone");
    }
    this.tracks = tracks;
    addEventListener("keydown", this.unlock);
    addEventListener("pointerdown", this.unlock);
    return this;
  }

  /** Fades the music in and keeps it going, track after track. */
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

  /** Called every frame: schedules the cues of `plan`, once per key; `final` is set once the game is over. `now`: host time, ms. */
  tick(plan: Plan<N> | null, final: boolean, now: number) {
    this.measureLag();
    if (plan && plan.key !== this.scheduled) {
      this.scheduled = plan.key;
      const lagMs = Number.isNaN(this.lagMs) ? (this.ctx.outputLatency || this.ctx.baseLatency || 0) * 1000 : this.lagMs;
      for (const { sound, at } of plan.cues) {
        const when = cueStart(at, now, this.ctx.currentTime, lagMs);
        if (when !== null) this.play(sound, when);
      }
    }
    if (final) this.end();
  }

  /**
   * The game is over (once, however often it is said): the board gives way to the ranking 4 s after the win, so the music
   * fades out under the jingle. `fanfare` plays at once, at no host time.
   */
  end(fanfare?: N) {
    if (this.ending) return;
    this.ending = true;
    this.fade(0, 3500);
    if (fanfare) this.play(fanfare, this.ctx.currentTime);
  }

  /**
   * How long a sound started now takes to reach the speaker, smoothed over frames: the output timestamp says which context
   * time the speaker plays at which moment.
   */
  private measureLag() {
    const { contextTime, performanceTime } = this.ctx.getOutputTimestamp();
    if (contextTime === undefined || !performanceTime) return;
    this.lagMs = smoothLag(this.lagMs, performanceTime + (this.ctx.currentTime - contextTime) * 1000 - performance.now());
  }

  private applySettings(s: TvSettings) {
    this.master.gain.setTargetAtTime(effectsGain(s), this.ctx.currentTime, 0.02);
    this.musicTarget = musicVolume(s);
    // a short ramp, not a jump; during the end-of-game fade the music stays on its way to 0
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

  private play(name: N, when: number) {
    const takes = this.buffers.get(name);
    if (!takes?.length) return;
    const src = this.ctx.createBufferSource();
    src.buffer = takes[Math.floor(Math.random() * takes.length)];
    if (this.effects[name].vary) src.playbackRate.value = 0.94 + Math.random() * 0.12;
    const gain = this.ctx.createGain();
    gain.gain.value = this.effects[name].gain;
    src.connect(gain).connect(this.master);
    src.start(when);
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
