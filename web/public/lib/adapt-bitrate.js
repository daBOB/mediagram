/**
 * What to do once the buffer says the link cannot keep up.
 *
 * Kept apart from the measuring because the two go wrong in different ways. A
 * measurement is wrong when it misreads a satisfied player as a starving one;
 * a decision is wrong when it restarts playback for a gain nobody would
 * notice. Restarting costs the viewer several seconds of black while a new
 * encode starts, so it has to buy more than that.
 */

/**
 * The lowest this player will ask a conversion to aim for.
 *
 * Matches the server's own floor. Below it the picture stops being worth
 * watching, and a viewer on a link that slow is better told than handed a
 * smear that still stalls.
 */
export const FLOOR_BITS = 600_000;

/** How much lower a new target must be before it justifies the interruption. */
const WORTH_RESTARTING = 0.15;

/**
 * @param {{state: "ok"|"behind"|"starving", fitting: number|null,
 *          currentCapBits: number|null}} situation
 *   `currentCapBits` is `null` while the original file is being played, since
 *   nothing caps it.
 * @returns {{targetBits: number, atFloor: boolean}|null}
 */
export function decideSwitch({ state, fitting, currentCapBits }) {
  if (state === "ok" || fitting === null) return null;

  const atFloor = fitting < FLOOR_BITS;
  const target = atFloor ? FLOOR_BITS : Math.round(fitting);

  // Playing the original: any conversion is an improvement, because the
  // original is whatever bitrate it was mastered at and nothing lowers it.
  if (currentCapBits === null) return { targetBits: target, atFloor };

  // Already converting. Only a real drop is worth another few seconds of
  // black, and asking for *more* while falling behind is never the answer.
  if (target > currentCapBits * (1 - WORTH_RESTARTING)) return null;
  if (currentCapBits <= FLOOR_BITS) return null;

  return { targetBits: target, atFloor };
}
