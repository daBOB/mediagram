/**
 * Telling a viewer on the sofa from a viewer on the internet.
 *
 * The distinction decides whether a title may be played as it is. Direct play
 * hands over the original file, which for a film is 13.9 Mbit/s: nothing on a
 * LAN, and more than a household uplink carries. Offering it to a remote
 * viewer is offering a stall, so remote viewers get a transcode instead.
 *
 * Behind a reverse proxy every request arrives from loopback, so the address
 * the proxy forwards is the one that matters — but only where a proxy really
 * is in front. Believing the header otherwise lets anyone claim to be on the
 * LAN by setting it.
 */

/** Exactly the loopback addresses, in the forms Node reports them. */
const LOOPBACK = new Set(["127.0.0.1", "::1", "::ffff:127.0.0.1"]);

/**
 * True when `address` belongs to a network this machine shares.
 *
 * Carrier-grade NAT (100.64/10) is deliberately absent: it is the ISP's
 * range, not the household's, and a viewer behind it is somewhere else.
 */
export function isLocalAddress(address: string): boolean {
  const plain = address.startsWith("::ffff:") ? address.slice(7) : address;
  if (LOOPBACK.has(address) || LOOPBACK.has(plain)) return true;

  const octets = plain.split(".");
  if (octets.length === 4 && octets.every(isOctet)) {
    const [a, b] = octets.map(Number) as [number, number, number, number];
    if (a === 10) return true;
    if (a === 192 && b === 168) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 169 && b === 254) return true; // link-local, a DHCP-less LAN
    return false;
  }

  if (!plain.includes(":")) return false;
  const lower = plain.toLowerCase();
  // fe80::/10 link-local, fc00::/7 unique-local: both mean "this network".
  return /^fe[89ab]/.test(lower) || /^f[cd]/.test(lower);
}

function isOctet(text: string): boolean {
  return /^\d{1,3}$/.test(text) && Number(text) <= 255;
}

/**
 * The address to judge a request by.
 *
 * `forwarded` is the `X-Forwarded-For` header, and the **last** entry is the
 * one to read, not the first. A proxy that replaces the header writes a single
 * entry and both readings agree; a proxy that appends — Cloudflare does —
 * leaves whatever the caller sent in front of the address it observed itself,
 * so reading the first entry would believe the caller. A remote viewer sending
 * `192.168.0.10` would then be handed the original file and stall on it.
 *
 * This assumes exactly one proxy in front, which is what the operating
 * document describes. It is read at all only when one is trusted, because
 * otherwise the header is whatever the caller decided to send.
 */
export function clientAddress(
  socketAddress: string | null,
  forwarded: string | null,
  trustProxy: boolean,
): string {
  if (trustProxy && forwarded) {
    const chain = forwarded.split(",").map((part) => part.trim()).filter(Boolean);
    const observed = chain.at(-1);
    if (observed) return observed;
  }
  // An unknown address is not a local one: a caller the server cannot place
  // gets the treatment that assumes the worst about the link.
  return socketAddress ?? "";
}
