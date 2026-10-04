/**
 * The wait after wrong PINs, kept per profile. Five wrong PINs in a row for
 * one profile and its PIN is not compared for a minute; a right PIN for that
 * profile wipes its count, and so does the wait running out.
 *
 * Per profile, because one count for the whole player was washed out by a
 * single right answer: four guesses at the admin, then one's own PIN, over
 * and over, never waited. Kept per profile, a right PIN only ever clears its
 * own guesses. Four digits are ten thousand guesses; at five a minute that is
 * more than a day of a child pressing buttons at one profile.
 *
 * In memory only: a restart forgets the counts, which costs a guesser a
 * restart and is not worth a table. One per `WatchState`, which is one per
 * server process — so wrong guesses on the laptop make the television wait
 * for that profile too.
 */

export const MAX_WRONG_PINS = 5;
export const WAIT_MS = 60_000;

export class PinWait {
  /** Profile id -> its wrong PINs in a row, and when its wait ends (0 for none). */
  private readonly counts = new Map<string, { wrong: number; until: number }>();

  /** `now` is the clock, so a test can move it instead of waiting. */
  constructor(private readonly now: () => number = () => Date.now()) {}

  /** Whole seconds until `id`'s PIN is compared again, rounded up; 0 is now. */
  secondsLeft(id: string): number {
    const count = this.counts.get(id);
    if (count === undefined || count.until === 0) return 0;
    const left = count.until - this.now();
    if (left > 0) return Math.ceil(left / 1000);
    // The wait has run out: that profile's count starts again from nothing.
    this.counts.delete(id);
    return 0;
  }

  failed(id: string): void {
    this.secondsLeft(id);
    const count = this.counts.get(id) ?? { wrong: 0, until: 0 };
    count.wrong += 1;
    if (count.wrong >= MAX_WRONG_PINS) count.until = this.now() + WAIT_MS;
    this.counts.set(id, count);
  }

  succeeded(id: string): void {
    this.counts.delete(id);
  }
}
