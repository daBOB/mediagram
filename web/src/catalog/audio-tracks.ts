/**
 * What audio a set actually carries, asked of the file rather than the index.
 *
 * The index has `sets.alang`, and it is the wrong answer for a chooser.
 * `collect_langs` in the uploader keeps *distinct* codes and drops streams
 * with no language tag, so a file holding `[und, en, en-commentary, de]` is
 * stored as `["en","de"]` — two entries for four streams, and neither
 * position is the ordinal `-map 0:a:N` needs. Picking "de" from that list
 * would play the commentary, and nothing about the result would look wrong.
 *
 * So the ordinal comes from the file. ffprobe reads it through this server's
 * own Range route, which means the bytes come from Telegram; the read is
 * bounded and the answer is cached, because a title's audio layout cannot
 * change under a player that opens the index read-only.
 */

/** One selectable audio stream. `index` is the ordinal for `-map 0:a:N`. */
export interface AudioTrack {
  index: number;
  /** The stream's language tag, or `null` when it carries none. */
  lang: string | null;
  codec: string | null;
  channels: number | null;
  /** The stream's own title, where it has one: "Commentary", "Director". */
  title: string | null;
  /** Whether the file marks this one as its default. */
  isDefault: boolean;
}

/** How long ffprobe may take before the answer is not worth waiting for. */
const PROBE_TIMEOUT_MS = 20_000;

/**
 * How much of the file ffprobe may read to find the streams.
 *
 * The default lets it read far more than a stream list needs, and every byte
 * is a Telegram round trip. A container declares its streams in its header;
 * if 8 MB does not reach them, a larger number will not save the answer.
 */
const PROBE_LIMIT = ["-probesize", "8M", "-analyzeduration", "8M"];

/**
 * Turns ffprobe's `-show_streams` JSON into tracks.
 *
 * Pure, and the reason the ordinal is trustworthy: `-select_streams a` yields
 * the audio streams in file order, so a stream's position in this array is
 * exactly its `0:a:N`. Kept apart from the running of ffprobe so that the one
 * part with a rule in it can be tested without spawning anything.
 */
export function parseAudioTracks(json: string): AudioTrack[] {
  let parsed: unknown;
  try {
    parsed = JSON.parse(json);
  } catch {
    return [];
  }

  const streams = (parsed as { streams?: unknown })?.streams;
  if (!Array.isArray(streams)) return [];

  return streams.map((raw, index) => {
    const stream = (raw ?? {}) as {
      codec_name?: unknown;
      channels?: unknown;
      tags?: { language?: unknown; title?: unknown };
      disposition?: { default?: unknown };
    };
    // `und` is the standard's way of saying "undetermined", which carries no
    // more meaning than the tag being absent. Both become null here so the
    // labelling has one case to handle rather than two.
    const lang = text(stream.tags?.language);
    return {
      index,
      lang: lang === "und" ? null : lang,
      codec: text(stream.codec_name),
      channels: typeof stream.channels === "number" ? stream.channels : null,
      title: text(stream.tags?.title),
      isDefault: stream.disposition?.default === 1,
    };
  });
}

function text(value: unknown): string | null {
  return typeof value === "string" && value !== "" ? value : null;
}

/** Runs ffprobe. Separated so a test can supply one that does not. */
export type Prober = (url: string) => Promise<string | null>;

/**
 * Reads a set's audio streams, once per set.
 *
 * Never throws and never reports a failure as an error: a probe that did not
 * work leaves a title with no chooser, which is what a single-track title
 * looks like anyway. Failures are cached too — a file ffprobe could not read
 * will not become readable on the next click, and retrying turns one slow
 * open into a slow open every time.
 */
export class AudioTrackReader {
  private readonly cache = new Map<string, AudioTrack[]>();

  constructor(
    private readonly endpoint: { readonly baseUrl: string },
    private readonly probe: Prober = ffprobeStreams,
  ) {}

  async read(setId: string): Promise<AudioTrack[]> {
    const held = this.cache.get(setId);
    if (held) return held;

    const url = `${this.endpoint.baseUrl}/api/sets/${encodeURIComponent(setId)}/stream`;
    let tracks: AudioTrack[] = [];
    try {
      const json = await this.probe(url);
      if (json !== null) tracks = parseAudioTracks(json);
    } catch {
      // Left empty on purpose. See the note above.
    }
    this.cache.set(setId, tracks);
    return tracks;
  }
}

/** The real prober: ffprobe, bounded, JSON on stdout, or `null`. */
async function ffprobeStreams(url: string): Promise<string | null> {
  const proc = Bun.spawn(
    [
      "ffprobe",
      "-v",
      "error",
      "-print_format",
      "json",
      "-show_streams",
      // Audio only, in file order. Both halves matter: the filter is what
      // makes a position an ordinal, and the order is what keeps it one.
      "-select_streams",
      "a",
      ...PROBE_LIMIT,
      url,
    ],
    { stdout: "pipe", stderr: "ignore" },
  );

  const timer = setTimeout(() => {
    try {
      proc.kill("SIGKILL");
    } catch {
      // Already gone.
    }
  }, PROBE_TIMEOUT_MS);

  try {
    const [json, code] = await Promise.all([new Response(proc.stdout).text(), proc.exited]);
    return code === 0 ? json : null;
  } finally {
    clearTimeout(timer);
  }
}
