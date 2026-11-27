/**
 * The game the TV home screen opened this page on (`?game=<kind>`), when it is one of [playable];
 * null otherwise, so an unknown or not-yet-playable game leaves the room's pick as it was.
 */
export function initialPick(search: string, playable: string[]): string | null {
  const game = new URLSearchParams(search).get("game");
  return game && playable.includes(game) ? game : null;
}
