// The winner jingle, once, at the TV's effects level (silent when muted).
// Sound is extra: any failure leaves the ceremony silent.

import { effectsGain, getSettings } from "../settings/tv-settings";

const JINGLE = "/race/sound/winner-jingle.ogg";

export class CeremonySound {
  private ctx: AudioContext | null = null;
  private source: AudioBufferSourceNode | null = null;
  private disposed = false;

  async play() {
    const gain = effectsGain(getSettings());
    if (gain <= 0) return;
    const res = await fetch(JINGLE);
    if (!res.ok) throw new Error(`${JINGLE}: ${res.status}`);
    const data = await res.arrayBuffer();
    if (this.disposed) return;
    const ctx = (this.ctx = new AudioContext());
    const buffer = await ctx.decodeAudioData(data);
    if (this.disposed) return;
    const master = ctx.createGain();
    master.gain.value = gain * 0.8; // the race board's gain for the same jingle: as loud there as here
    master.connect(ctx.destination);
    const source = (this.source = ctx.createBufferSource());
    source.buffer = buffer;
    source.connect(master);
    source.start();
  }

  dispose() {
    this.disposed = true;
    try {
      this.source?.stop();
    } catch {
      // never started
    }
    void this.ctx?.close().catch(() => {});
  }
}
