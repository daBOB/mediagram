/**
 * The household's LAN chunk server, between this player's cache and Telegram.
 *
 * What matters: a chunk Telegram sent once is handed to the server signed the
 * way the Android app signs it, the next read of it never reaches Telegram,
 * and a server that is gone costs a read nothing but the server.
 */

import { afterEach, expect, test } from "bun:test";

import { LAN_CHUNK, LanChunkServer, lanSignature } from "../src/cache/lan-chunk-server";

const SET = "01SET0000000000000000001";
const TOKEN = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

const stops: (() => void)[] = [];
afterEach(() => {
  for (const stop of stops.splice(0)) stop();
});

/** A stand-in for `mediagram_cache`: chunks by path, PUTs refused unless signed. */
function fakeServer() {
  const chunks = new Map<string, Uint8Array>();
  let gets = 0;
  const server = Bun.serve({
    port: 0,
    async fetch(request) {
      const path = new URL(request.url).pathname;
      if (request.method === "GET") {
        gets++;
        const held = chunks.get(path);
        return held ? new Response(held) : new Response(null, { status: 404 });
      }
      const body = new Uint8Array(await request.arrayBuffer());
      const total = Number(request.headers.get("x-set-total"));
      if (request.headers.get("authorization") !== `MGC1 ${lanSignature(TOKEN, path, total, body)}`) {
        return new Response(null, { status: 401 });
      }
      chunks.set(path, body);
      return new Response(null, { status: 201 });
    },
  });
  stops.push(() => server.stop(true));
  return { url: `http://127.0.0.1:${server.port}`, chunks, gets: () => gets };
}

/** A set's parts and a Telegram that records what it was asked for. */
function telegramSet(lengths: number[]) {
  let off = 0;
  const spans = lengths.map((len, idx) => {
    const span = { idx, off, len };
    off += len;
    return span;
  });
  const parts = lengths.map((len, idx) => Uint8Array.from({ length: len }, (_, i) => (i * 7 + idx * 13) & 0xff));
  const asked: string[] = [];
  const fetchPart = (idx: number) => async (offset: number, length: number) => {
    asked.push(`${idx}:${offset}+${length}`);
    return parts[idx]!.slice(offset, offset + length);
  };
  return { spans, parts, asked, fetchPart };
}

test("the documented test vector signs to the documented signature", () => {
  const body = new TextEncoder().encode("hello");
  expect(lanSignature(TOKEN, "/v1/sets/abc123/chunks/0", 5, body)).toBe(
    "c1ac37f41c76c7c8434460c94458f58a817bf0859f9be493209d23a8ab9a4d85",
  );
});

test("a miss is fetched from Telegram once, stored signed, and the next read never reaches Telegram", async () => {
  const server = fakeServer();
  const set = telegramSet([2 * LAN_CHUNK, LAN_CHUNK]);
  const first = new LanChunkServer({ url: server.url, token: TOKEN });

  const got = await first.fetcherFor(SET, set.spans, 0, set.fetchPart)(10, 50);
  await first.settle();

  expect(got).toEqual(set.parts[0]!.slice(10, 60));
  expect(set.asked).toEqual([`0:0+${LAN_CHUNK}`]);
  expect(server.chunks.get(`/v1/sets/${SET}/chunks/0`)).toEqual(set.parts[0]!.slice(0, LAN_CHUNK));

  // A second player, or this one after a restart: nothing in flight, nothing remembered.
  const again = await new LanChunkServer({ url: server.url, token: TOKEN }).fetcherFor(SET, set.spans, 0, set.fetchPart)(10, 50);
  expect(again).toEqual(got);
  expect(set.asked).toHaveLength(1);
});

test("a chunk that straddles two parts is stitched from both, and a short last chunk is its true length", async () => {
  const server = fakeServer();
  const set = telegramSet([LAN_CHUNK + 100, LAN_CHUNK]);
  const lan = new LanChunkServer({ url: server.url, token: TOKEN });

  const tail = await lan.fetcherFor(SET, set.spans, 0, set.fetchPart)(LAN_CHUNK, 100);
  const last = await lan.fetcherFor(SET, set.spans, 1, set.fetchPart)(LAN_CHUNK - 100, 100);
  await lan.settle();

  expect(tail).toEqual(set.parts[0]!.slice(LAN_CHUNK));
  expect(last).toEqual(set.parts[1]!.slice(LAN_CHUNK - 100));
  expect(set.asked).toEqual([`0:${LAN_CHUNK}+100`, `1:0+${LAN_CHUNK - 100}`, `1:${LAN_CHUNK - 100}+100`]);
  const stitched = server.chunks.get(`/v1/sets/${SET}/chunks/1`)!;
  expect(stitched.length).toBe(LAN_CHUNK);
  expect(stitched.subarray(100)).toEqual(set.parts[1]!.subarray(0, LAN_CHUNK - 100));
  expect(server.chunks.get(`/v1/sets/${SET}/chunks/2`)!.length).toBe(100);
});

test("a server that cannot be reached is skipped, and reads come from Telegram unchanged", async () => {
  const gone = fakeServer();
  const url = gone.url;
  stops.pop()!();
  const set = telegramSet([LAN_CHUNK]);
  let clock = 1_000;
  const lan = new LanChunkServer({ url, token: TOKEN }, () => clock);
  const read = lan.fetcherFor(SET, set.spans, 0, set.fetchPart);

  expect(await read(0, 10)).toEqual(set.parts[0]!.slice(0, 10));
  expect(await read(20, 10)).toEqual(set.parts[0]!.slice(20, 30));
  await lan.settle();

  expect(set.asked).toHaveLength(2);
  expect(gone.gets()).toBe(0);
  clock += 60_000;
  expect(await read(0, 10)).toEqual(set.parts[0]!.slice(0, 10));
});

test("without a token the server is read but never written", async () => {
  const server = fakeServer();
  const set = telegramSet([LAN_CHUNK]);
  const lan = new LanChunkServer({ url: server.url, token: null });

  await lan.fetcherFor(SET, set.spans, 0, set.fetchPart)(0, 10);
  await lan.settle();

  expect(server.gets()).toBe(1);
  expect(server.chunks.size).toBe(0);
});
