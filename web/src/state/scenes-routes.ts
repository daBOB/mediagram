/**
 * The scene routes: marking a moment of a title, and taking a mark off.
 * Every write has passed the dispatcher's browser-write guard before it
 * gets here.
 */

import { jsonBody } from "../http/browser-write";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { bodiless } from "../response";
import { json, P } from "./route-shared";
import type { WatchState } from "./store";

const SCENES = new RegExp(`^/api/profiles/${P}/scenes$`);
const SCENE = new RegExp(`^/api/profiles/${P}/scenes/([A-Za-z0-9]{1,64})$`);

/** Answers a scene route, or `null` when the path is not one. */
export function sceneRoute(
  { method, path, body }: PlayerRequest,
  state: WatchState,
  isPlayable: (setId: string) => boolean,
): PlayerResponse | null {
  const scenes = SCENES.exec(path);
  if (scenes) {
    if (method !== "POST") return bodiless(405);
    const said = jsonBody(body);
    const setId = typeof said?.setId === "string" ? said.setId : "";
    if (!state.has(scenes[1]!) || !isPlayable(setId)) return bodiless(404);
    const id = typeof said?.at === "number" ? state.markScene(scenes[1]!, setId, said.at) : null;
    return id === null ? bodiless(400) : json(JSON.stringify({ id }), false, 201);
  }

  const scene = SCENE.exec(path);
  if (scene) {
    if (method !== "DELETE") return bodiless(405);
    return bodiless(state.removeScene(scene[1]!, scene[2]!) ? 204 : 404);
  }
  return null;
}
