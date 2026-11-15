// The game-show buzzer, synthesised in Web Audio (no file to license or load): two
// detuned low square waves through a low-pass, a hard start and a short tail. The phone
// plays it on the press itself, the TV when the host takes a buzz. Sound is extra:
// wherever Web Audio is missing or blocked, the buzzer stays silent and the game goes on.

let ctx: AudioContext | null = null;

/** Creates and resumes the context. Safari starts audio only from a finger-up or click, never a
 *  finger-down, so the first such tap anywhere on the page unlocks the buzzer for later presses. */
function unlock() {
  try {
    ctx ??= new AudioContext();
    void ctx.resume();
  } catch {
    // no sound on this device
  }
}

if (typeof window !== "undefined") {
  for (const type of ["touchend", "click"]) window.addEventListener(type, unlock, { once: true, capture: true });
}

const LENGTH_S = 0.45;

/** Plays the buzzer once at `volume` (0..1). Never throws. */
export function playBuzzer(volume = 0.6) {
  unlock();
  if (!ctx) return;
  try {
    const t = ctx.currentTime;
    const gain = ctx.createGain();
    gain.gain.setValueAtTime(0, t);
    gain.gain.linearRampToValueAtTime(volume * 0.35, t + 0.01);
    gain.gain.setValueAtTime(volume * 0.35, t + LENGTH_S - 0.08);
    gain.gain.exponentialRampToValueAtTime(0.0001, t + LENGTH_S);
    const filter = ctx.createBiquadFilter();
    filter.type = "lowpass";
    filter.frequency.value = 1400;
    filter.connect(gain).connect(ctx.destination);
    for (const hz of [146, 151]) {
      const osc = ctx.createOscillator();
      osc.type = "square";
      osc.frequency.value = hz;
      osc.connect(filter);
      osc.start(t);
      osc.stop(t + LENGTH_S);
    }
  } catch {
    // no sound on this device
  }
}
