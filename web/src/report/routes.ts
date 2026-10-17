/** `POST /api/report`: a viewer's broken-video report, checked here and sent to the account's own chat. */

import type { Database } from "bun:sqlite";
import manifest from "../../package.json";
import { playableSet } from "../catalog";
import { jsonBody } from "../http/browser-write";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { bodiless } from "../response";
import { json } from "../state/route-shared";
import type { WatchState } from "../state/store";
import type { ReportSender } from "../telegram/report-sender";
import { REPORT_REASONS, reportText, type ReportFacts } from "../../public/lib/report/report-text.js";

const UNREACHABLE = "Telegram isn't reachable";
const PLAYED = ["direct", "converted", "copied"] as const;

/** A string no longer than `max`, or `null` when the field is absent; `undefined` when it is wrong. */
function text(value: unknown, max: number): string | null | undefined {
  if (value === undefined || value === null) return null;
  return typeof value === "string" && value.length <= max ? value : undefined;
}

/** The facts a body states, or `null` when any field is wrong. */
function facts(body: Record<string, unknown>, db: Database, state: WatchState | undefined): ReportFacts | null {
  const { set, reason, title, position, played, held, profileId } = body;
  if (typeof set !== "string" || playableSet(db, set) === null) return null;
  const why = REPORT_REASONS.find((r) => r.id === reason);
  if (!why || typeof title !== "string" || title.length < 1 || title.length > 300) return null;
  const note = text(body.note, 2000);
  const error = text(body.error, 300);
  const profile = text(profileId, Infinity);
  if (note === undefined || error === undefined || profile === undefined) return null;
  if (position != null && (typeof position !== "number" || !Number.isFinite(position) || position < 0)) return null;
  if (played != null && !PLAYED.includes(played as (typeof PLAYED)[number])) return null;
  if (held !== undefined && typeof held !== "boolean") return null;
  return {
    reason: why.id, title, note, error, setId: set,
    position: (position as number | null | undefined) ?? null,
    played: (played as ReportFacts["played"]) ?? null,
    held: held ?? false,
    profile: state?.profiles().find((p) => p.id === profile)?.name ?? null,
    screen: "Browser", device: null, app: `Web ${manifest.version}`,
  };
}

export async function reportRoute(
  request: PlayerRequest, sender: ReportSender | undefined, db: Database, state: WatchState | undefined,
): Promise<PlayerResponse | null> {
  if (request.path !== "/api/report") return null;
  if (!sender) return bodiless(404);
  if (request.method !== "POST") return bodiless(405);
  const body = jsonBody(request.body);
  const checked = body && facts(body, db, state);
  if (!checked) return bodiless(400);
  const unreachable = () => json(JSON.stringify({ reason: UNREACHABLE }), false, 503);
  if (!sender.available()) return unreachable();
  try {
    await sender.send(reportText(checked));
  } catch (error) {
    console.warn("report: send failed", error);
    return unreachable();
  }
  return bodiless(204);
}
