/**
 * Which sections of the library a profile sees, set by whoever the rule says
 * (`set-access` in `profiles-rules.ts`): a kid's by its own grown-up, a
 * grown-up's by the admin, the admin's by nobody.
 *
 * A subclass only because `profiles-manage.ts` is at its line limit: it
 * answers in the same order and through the same `check` as every other
 * management action — input that cannot be used, somebody not there, a kid
 * acting, the PIN, then the rule.
 *
 * Not a lock, as a kid's age limit is not: the page filters the catalogue,
 * and nothing here stops a request for a title in a hidden section.
 */

import { ProfileManager, type Outcome } from "./profiles-manage";
import { writeHiddenSections } from "./profiles-write";
import { hiddenSectionsOf } from "./roles-record";

export class ProfileAccess extends ProfileManager {
  /** The sections `id` does not see, as a whole list; an empty one hides nothing. */
  setHiddenSections(actorId: string, pin: unknown, id: string, sections: unknown): Outcome {
    const hidden = hiddenSectionsOf(sections);
    if (hidden === null) return { reason: "invalid" };
    const refused = this.check(actorId, pin, "set-access", id);
    if (refused) return refused;
    writeHiddenSections(this.db!, id, hidden);
    return null;
  }
}
