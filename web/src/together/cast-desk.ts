/**
 * The web server's half of the cast handshake (`cast.js`). A caster's offer
 * goes out as a new together message and the TV answers by editing it; the
 * caster's request is held until that answer comes, for `CAST_ANSWER_MS` at
 * most, and answered once.
 */

import { CAST_ANSWER_MS, chose, offered, replaced } from "../../public/lib/together/cast.js";
import { isRequest, writeSession, type Member, type Session } from "../../public/lib/together/session.js";
import type { Playback } from "../../public/lib/together/moves.js";
import { isLive } from "../../public/lib/together/follow.js";
import type { Live, TogetherHub } from "./hub";

/** `none`: no TV answered in time, and the offer was withdrawn. Any other answer carries the offer's own message, whose id `replace` and `cancel` act on. */
export type OfferAnswer = { answer: "on" | "busy" | "refused" | "none"; live: Live | null; note: string };

const NONE: OfferAnswer = { answer: "none", live: null, note: "" };

/** The TV's answer a session holds; `null` while no TV has answered it. */
function answered(id: number, s: Session | null): OfferAnswer | null {
  const cast = s?.cast;
  if (!s || !cast || cast.state === "offer" || cast.state === "replace") return null;
  return { answer: cast.state, live: { id, session: s }, note: cast.state === "on" ? "" : cast.note };
}

export class CastDesk {
  constructor(
    private readonly hub: TogetherHub,
    private readonly now: () => number = Date.now,
    private readonly sleep: (ms: number) => Promise<void> = Bun.sleep,
  ) {}

  /** Only a TV that joined `household`, the caster's, answers. */
  async offer(me: Member, own: Playback, profile: string, household: string): Promise<OfferAnswer> {
    const live = await this.hub.create(me.screen, offered(crypto.randomUUID(), me, own, profile, household, this.now()));
    // The TV may answer before the send has returned.
    return answered(live.id, live.session) ?? this.wait(live.id);
  }

  /** The caster's answer to "busy": the same request, now asking the TV to switch. */
  async replace(id: number, me: Member, own: Playback, profile: string, household: string): Promise<OfferAnswer> {
    const s = await this.hub.latest(id);
    const stale = !s || !isRequest(s) || !isLive(s, null, this.now());
    // Nothing is moved for a message this server no longer knows, as after a restart: it is offered anew too.
    if (stale || !this.hub.move(id, me.screen, (current) => replaced(current, me.screen, own, this.now()))) return this.offer(me, own, profile, household);
    return this.wait(id);
  }

  cancel(id: number): void {
    void this.hub.withdraw(id).catch(() => {});
  }

  tracks(id: number, screen: string, choice: { audio?: string; text?: string }): Session | null {
    return this.hub.move(id, screen, (s) => chose(s, screen, choice));
  }

  /**
   * The first answer heard for `id`, or, when none is heard in time, the one
   * the channel holds by then: a push can be missed. Still unanswered, or
   * unreadable, the offer is withdrawn, so no TV takes it after the caster
   * was told it failed.
   */
  private async wait(id: number): Promise<OfferAnswer> {
    let stop = () => {};
    const heard = new Promise<OfferAnswer>((resolve) => {
      stop = this.hub.onKept((kept, session) => {
        const answer = kept === id ? answered(id, session) : null;
        if (answer) resolve(answer);
      });
    });
    const answer = await Promise.race([heard, this.sleep(CAST_ANSWER_MS).then(() => null)]).finally(stop);
    if (answer) return answer;
    const s = await this.hub.latest(id).catch(() => null);
    const read = answered(id, s);
    if (s && read) {
      // Kept as if heard, so the remote's next move builds on the TV's answer and not on the offer.
      this.hub.heard(id, writeSession(s));
      return read;
    }
    await this.hub.withdraw(id).catch(() => {});
    return NONE;
  }
}
