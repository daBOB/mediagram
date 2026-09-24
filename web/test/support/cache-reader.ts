import type { CachedReader, CachedReadRequest } from "../../src/cache/reader";

/** Collect the production streaming reader when a test compares whole bytes. */
export async function collectRead(reader: CachedReader, request: CachedReadRequest): Promise<Uint8Array> {
  const chunks: Uint8Array[] = [];
  let length = 0;
  for await (const chunk of reader.readStream(request)) {
    chunks.push(chunk);
    length += chunk.length;
  }
  const bytes = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.length;
  }
  return bytes;
}
