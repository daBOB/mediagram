// The round, the turn banner, the dice's result and the players' tags (avatar, cash, hotels, and a flash of every cash change),
// drawn in the canvas: HTML over the WebGL canvas cost the TV box frames. The question and auction clocks are in
// grand-allee-hud-clocks.ts. Labels are sized and uploaded at layout and redrawn in place when their text changes.

import * as THREE from "three";
import type { GrandAlleeScene } from "../../protocol/GrandAlleeScene";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { taler } from "../../ui/taler";
import { HUD_FONT } from "../race/race-hud";
import { Clocks } from "./grand-allee-hud-clocks";
import { INK, avatar, drop, fit, label, paint, pill, place, type Label } from "./grand-allee-hud-label";
import { IntroCard } from "./grand-allee-intro";
import { TIMES } from "./grand-allee-look";
import type { SeatLook } from "./grand-allee-pieces";
import { describeTurn, onTurn } from "./grand-allee-status";

const GAIN = "#86e07a", LOSS = "#ff8a78";

export class GrandAlleeHud {
  private scene = new THREE.Scene();
  private camera = new THREE.OrthographicCamera(0, 1920, 1080, 0, -1, 1);
  private size = new THREE.Vector2();
  private u = 1;
  private round: Label;
  private banner: Label;
  private result: Label;
  private tags: Label[];
  private clocks: Clocks;
  private intro: IntroCard;
  private tagW = 330;
  private flashes = new Map<number, { amount: number; t: number }>();
  /** How many labels were drawn again, for the frame log: a redraw is a texture upload. */
  repaints = 0;

  constructor(private renderer: THREE.WebGLRenderer, private seats: SeatLook[]) {
    this.round = label(this.scene, 300, 66);
    this.banner = label(this.scene, 1500, 66);
    this.result = label(this.scene, 560, 104);
    this.tags = seats.map(() => label(this.scene, 330, 84));
    this.clocks = new Clocks(this.scene, seats);
    this.intro = new IntroCard(this.scene);
  }
  private labels() {
    return [this.round, this.banner, this.result, ...this.tags, ...this.clocks.labels];
  }

  /** Cash changes since the last scene, flashed on the tags for a while from `at(player)` (frame clock, s). */
  flash(cash: Map<number, number>, at: (player: number) => number) {
    for (const [player, amount] of cash) this.flashes.set(player, { amount, t: at(player) });
  }

  /** After a resize: the scale, every label sized, drawn and uploaded again. */
  layout(scene: GrandAlleeScene, players: PlayerInfo[], now: number, t: number) {
    this.renderer.getDrawingBufferSize(this.size);
    this.u = this.size.x / 1920;
    Object.assign(this.camera, { right: this.size.x, top: this.size.y });
    this.camera.updateProjectionMatrix();
    const n = this.seats.length;
    this.tagW = Math.min(330, (1920 - 84 - 14 * (n - 1)) / Math.max(1, n));
    this.tags.forEach((l) => (l.w = this.tagW));
    // the intro card is not uploaded here: a TV that comes after the intro never needs it
    for (const l of [...this.labels(), ...this.intro.labels]) fit(l, this.u);
    this.update(scene, players, now, t);
    for (const l of this.labels()) this.renderer.initTexture(l.tex);
  }

  update(scene: GrandAlleeScene, players: PlayerInfo[], now: number, t: number) {
    const u = this.u, { x: W, y: H } = this.renderer.getDrawingBufferSize(this.size), m = 42 * u;
    const who = onTurn(scene), lead = players.find((p) => p.id === who), text = describeTurn(scene, players, now);
    const roundText = `Runde ${scene.round} / ${scene.rounds}`;
    this.count(paint(this.round, roundText, (g, w, h) => {
      pill(g, 0, 0, w, h);
      g.font = `800 ${34 * u}px ${HUD_FONT}`;
      g.fillStyle = INK;
      g.textAlign = "center";
      g.textBaseline = "middle";
      g.fillText(roundText, w / 2, h / 2 + 2 * u);
    }));
    place(this.round, m, H - m);
    this.count(paint(this.banner, `${who}|${text.text}`, (g, w, h) => {
      g.font = `800 ${36 * u}px ${HUD_FONT}`;
      g.textBaseline = "middle";
      const name = lead && text.text.startsWith(lead.name) ? lead.name : "", rest = text.text.slice(name.length);
      const a = g.measureText(name).width, tw = Math.min(w - 40 * u, a + g.measureText(rest).width + 84 * u), x = (w - tw) / 2;
      pill(g, x, 0, tw, h);
      g.fillStyle = lead?.color ?? INK;
      g.fillText(name, x + 42 * u, h / 2 + 2 * u);
      g.fillStyle = INK;
      g.fillText(rest, x + 42 * u + a, h / 2 + 2 * u, tw - 84 * u - a);
    }));
    place(this.banner, (W - this.banner.canvas.width) / 2, H - m);

    // the dice's result, big, on the right above the tags, while there is one to show
    const show = text.die && text.die !== "…" ? text.die : "";
    this.result.mesh.visible = show !== "";
    if (show) {
      this.count(paint(this.result, show, (g, w, h) => {
        g.font = `800 ${60 * u}px ${HUD_FONT}`;
        const tw = Math.min(w, Math.max(200 * u, g.measureText(show).width + 90 * u));
        pill(g, w - tw, 0, tw, h);
        g.fillStyle = INK;
        g.textAlign = "center";
        g.textBaseline = "middle";
        g.fillText(show, w - tw / 2, h / 2 + 4 * u);
      }));
      place(this.result, W - m - this.result.canvas.width, m + 112 * u + this.result.canvas.height);
    }
    this.updateTags(scene, who, t, W, m);
    this.repaints += this.clocks.update(scene, players, now, W, H - m - this.banner.canvas.height, u);
    this.repaints += this.intro.update(scene, now, W, H, u);
  }

  private updateTags(scene: GrandAlleeScene, who: number, t: number, W: number, m: number) {
    const u = this.u, owned = new Map<number, number>();
    for (const e of scene.estates) owned.set(e.owner, (owned.get(e.owner) ?? 0) + 1);
    const gap = 14 * u, n = scene.seats.length, x0 = (W - (n * this.tagW * u + gap * (n - 1))) / 2;
    scene.seats.forEach((seat, i) => {
      const look = this.seats[i], tag = this.tags[i];
      if (!look || !tag) return;
      const hotels = owned.get(seat.player) ?? 0, f = this.flashes.get(seat.player), active = seat.player === who;
      const amount = f && t >= f.t && t - f.t < TIMES.flash ? f.amount : 0;
      // keyed on the raw numbers: the text is formatted only when the tag is drawn again
      this.count(paint(tag, `${active}|${seat.out}|${seat.cash}|${hotels}|${amount}`, (g, w, h) => {
        const line = seat.out ? "pleite" : `${taler(seat.cash, true)} · ${hotels} ${hotels === 1 ? "Hotel" : "Hotels"}`;
        const delta = amount ? `${amount > 0 ? "+" : "−"}${taler(Math.abs(amount), true)}` : "";
        g.globalAlpha = seat.out ? 0.5 : 1;
        pill(g, 0, 0, w, h, look.color, active ? 4 * u : 0);
        const d = h - 16 * u, x = 8 * u + d + 12 * u;
        avatar(g, look, 8 * u + d / 2, h / 2, d, u);
        g.textBaseline = "alphabetic";
        g.textAlign = "left";
        g.font = `800 ${24 * u}px ${HUD_FONT}`;
        const dw = delta ? g.measureText(delta).width + 12 * u : 0;
        g.fillStyle = INK;
        g.fillText(look.name, x, h / 2 - 3 * u, w - x - 18 * u - dw);
        if (delta) {
          g.fillStyle = amount > 0 ? GAIN : LOSS;
          g.textAlign = "right";
          g.fillText(delta, w - 18 * u, h / 2 - 3 * u);
          g.textAlign = "left";
        }
        g.globalAlpha *= 0.78;
        g.fillStyle = INK;
        g.font = `700 ${19 * u}px ${HUD_FONT}`;
        g.fillText(line, x, h / 2 + 22 * u, w - x - 18 * u);
      }));
      place(tag, x0 + i * (this.tagW * u + gap), m + tag.canvas.height);
    });
  }

  private count(painted: boolean) {
    if (painted) this.repaints++;
  }
  compile() {
    this.renderer.compile(this.scene, this.camera);
  }
  render() {
    this.renderer.render(this.scene, this.camera);
  }
  dispose() {
    [this.round, this.banner, this.result, ...this.tags].forEach(drop);
    this.clocks.dispose();
    this.intro.dispose();
  }
}
