/** Transport descriptions shared by the listener and feature routers. */

export interface PlayerRequest {
  method: string;
  path: string;
  range: string | null;
  /** `?seek=` on a transcode request, in seconds. */
  seek?: string | null;
  /**
   * `?vcodecs=` on a transcode request: video codecs the browser decodes
   * beyond the everywhere-list, comma-separated. See `codec-support.js`.
   */
  vcodecs?: string | null;
  /** `?maxrate=` on a transcode request, in bits per second. */
  maxrate?: string | null;
  /** `?audio=` on a transcode request: which audio stream, as `0:a:N`. */
  audio?: string | null;
  /** `?q=` on a search request. */
  query?: string | null;
  /**
   * `?final=1` on a progress write: the caller identifying it as the last
   * one for this playback — leaving a title, or pausing — rather than the
   * periodic autosave tick. Explicit because the HTTP method cannot be
   * trusted to say so: `sendBeacon`'s POST is how `flushProgress` usually
   * sends this, but an older browser that refuses `sendBeacon` a JSON body
   * falls back to the same PUT the tick already uses. See
   * `routes.ts`'s `writeWorthSyncing`.
   */
  final?: string | null;
  /** The address the request came from, already resolved through any proxy. */
  client?: string;
  /** The body of a write, already read and bounded. `null` for a read. */
  body?: string | null;
  /** `content-type`, lowercased, without parameters. */
  contentType?: string | null;
  /** `Origin`, where the browser sent one. */
  origin?: string | null;
  /** `Host`, to compare an `Origin` against. */
  host?: string | null;
  /** `Accept-Encoding`, for deciding whether a compressible body may be gzipped/brotlied. */
  acceptEncoding?: string | null;
  /** `If-None-Match`, compared against a response's own ETag to answer 304 without a body. */
  ifNoneMatch?: string | null;
  /** The raw `Cookie` header, for the Settings admin session. Nothing else reads it. */
  cookie?: string | null;
}

export interface PlayerResponse {
  status: number;
  headers: Record<string, string>;
  /** `null` for a bodiless response. Known finite lengths use content-length;
   * indefinite streams such as server events omit that header. */
  body: ReadableStream<Uint8Array> | Uint8Array | null;
}
