/**
 * The player's own module graph — some 200 KB across three dozen files that
 * only playing something ever needs. Started once, kicked off in the
 * background right after the first shelf is drawn; a Play pressed before it
 * lands simply waits its turn on the promise already under way.
 */
let playerReady = null;

export function loadPlayer() {
  return (playerReady ??= import("./player.js")
    .then((mod) => {
      mod.initializePlayer();
      return mod;
    })
    .catch((error) => {
      // A later Play may as well try again — nothing about this profile or
      // catalog caused it, so nothing about them will fix it either.
      playerReady = null;
      throw error;
    }));
}
