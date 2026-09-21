/**
 * Telling a viewer on the sofa from a viewer on the internet.
 *
 * Two questions are asked of an address here, and they are not the same one.
 *
 * **What can the link carry?** — `isLocalAddress`. Direct play hands over the
 * original file, which for a film is 13.9 Mbit/s: nothing on a LAN, and more
 * than a household uplink carries. Offering it to a remote viewer is offering
 * a stall, so remote viewers get a transcode instead.
 *
 * **Whose device is on the other end?** — `isOwnNetwork`. This decides
 * whether a viewer may be shown what the player itself is doing, and it is a
 * question about trust rather than bandwidth. The two answers differ for a
 * tailnet peer: the household's own phone, which may see the panel, reaching
 * this machine over an uplink that cannot carry a film.
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
 * range, not the household's, and a viewer behind it reaches this machine
 * over an uplink. A tailnet peer lives there too — see `isOwnNetwork`, which
 * is the question that range does belong to.
 */
export function isLocalAddress(address: string): boolean {
  const plain = plainForm(address);
  if (LOOPBACK.has(address) || LOOPBACK.has(plain)) return true;

  const octets = octetsOf(address);
  if (octets) {
    const [a, b] = octets;
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

/**
 * True when `address` is one of this household's own devices.
 *
 * Everything `isLocalAddress` accepts, and the tailnet with it. A Tailscale
 * peer is handed an address in 100.64/10 and had to be admitted to the
 * tailnet before it could send a packet at all, so it is the same phone that
 * would be on the sofa if it were home — not a stranger. Its *link* is still
 * an uplink, which is why this is a second question and not a wider answer to
 * the first one.
 *
 * Carrier-grade NAT shares that range, so a neighbour behind the same ISP NAT
 * would read as own-network here. What that costs is bounded: this API has no
 * authentication, and anyone who can reach the port can already stream the
 * whole library. Beyond the library, the panel adds the host's paths and its
 * cache figures.
 *
 * Tailscale's IPv6 addresses need no case of their own: `fd7a:115c:a1e0::/48`
 * sits inside `fc00::/7`, which `isLocalAddress` already accepts.
 */
export function isOwnNetwork(address: string): boolean {
  if (isLocalAddress(address)) return true;

  const octets = octetsOf(address);
  if (!octets) return false;
  const [a, b] = octets;
  return a === 100 && b >= 64 && b <= 127;
}

/** `address` without the IPv4-mapped prefix Node reports it behind. */
function plainForm(address: string): string {
  return address.startsWith("::ffff:") ? address.slice(7) : address;
}

/** The four octets of a dotted quad, or `null` when it is not one. */
function octetsOf(address: string): [number, number, number, number] | null {
  const parts = plainForm(address).split(".");
  if (parts.length !== 4 || !parts.every(isOctet)) return null;
  return parts.map(Number) as [number, number, number, number];
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
