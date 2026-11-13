"""Throwaway: the big city with a few bridges instead of streets and bus lines across the open river.

usage: python3 pick_bridges.py CITY.json OUT.json [BRIDGES] [BUS_BRIDGES]

Keeps every field. Picks BRIDGES bank-to-bank pairs spread along the river (short, close to square
to the water, clear of other fields and of the streets that stay); BUS_BRIDGES of them join two bus
stops and carry a bus line too. Every other taxi and bus link across the river goes. U-Bahn links stay
(they run under the river) and so does the ferry. Writes OUT with a "bridges" list of [north, south].
"""
import json, math, os, sys
from collections import defaultdict

src, out = sys.argv[1], sys.argv[2]
K = int(sys.argv[3]) if len(sys.argv) > 3 else 6
KB = int(sys.argv[4]) if len(sys.argv) > 4 else 3
city = json.load(open(src))
S, R = city["spots"], city["river"]
pt = lambda f: S[f - 1]

def river_y(x):
    for (ax, ay), (bx, by) in zip(R, R[1:]):
        if ax <= x <= bx:
            return ay + (by - ay) * (x - ax) / (bx - ax)
    return R[-1][1]

def ccw(a, b, c): return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
def cross(p, q, r, s): return ccw(p, q, r) * ccw(p, q, s) < 0 and ccw(r, s, p) * ccw(r, s, q) < 0
def wet(a, b): return sum(cross(pt(a), pt(b), r, s) for r, s in zip(R, R[1:])) > 0
def north(f): return pt(f)[1] < river_y(pt(f)[0])
def clear(a, b, gap=16):
    """No other field closer than `gap` to the segment."""
    (ax, ay), (bx, by) = pt(a), pt(b)
    L2 = (bx - ax) ** 2 + (by - ay) ** 2
    for f in range(1, len(S) + 1):
        if f in (a, b): continue
        px, py = pt(f)
        t = max(0, min(1, ((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / L2))
        if math.hypot(px - ax - t * (bx - ax), py - ay - t * (by - ay)) < gap: return False
    return True

dry = {k: [tuple(e) for e in city[k] if not wet(*e)] for k in ("taxi", "bus")}
land = dry["taxi"] + dry["bus"]
stops = {f for e in city["bus"] for f in e}

def candidates():
    for a in range(1, len(S) + 1):
        for b in range(1, len(S) + 1):
            if not (north(a) and not north(b)) or math.dist(pt(a), pt(b)) > 105: continue
            if sum(cross(pt(a), pt(b), r, s) for r, s in zip(R, R[1:])) != 1 or not clear(a, b): continue
            if any(cross(pt(a), pt(b), pt(c), pt(d)) for c, d in land if len({a, b, c, d}) == 4): continue
            (ax, ay), (bx, by) = pt(a), pt(b)
            # how far from square to the river: the bank's slope against the bridge's direction
            mx = (ax + bx) / 2
            slope = (river_y(mx + 5) - river_y(mx - 5)) / 10
            skew = abs(math.atan2(bx - ax, by - ay) + math.atan(slope))
            # the deck: the stretch of the link within the river's banks, as the TV draws it
            deck = math.dist(pt(a), pt(b)) * sum(abs(ay + (by - ay) * t / 100 - river_y(ax + (bx - ax) * t / 100)) < 27 for t in range(101)) / 101
            if math.degrees(skew) > 20: continue
            yield (a, b), mx, math.dist(pt(a), pt(b)) + 2.5 * deck

cands = list(candidates())
x0, x1 = min(p[0] for p in S), max(p[0] for p in S)
bridges, buses = [], []
for k in range(K):
    target = x0 + (k + 0.5) * (x1 - x0) / K
    bus = k % (K // KB) == K // KB // 2 if KB else False  # every other bridge, from the second, carries a bus
    # its own stretch of the river, apart from the bridges already built
    pool = [c for c in cands if abs(c[1] - target) < (x1 - x0) / K * 0.6 and all(abs(c[1] - (pt(b[0])[0] + pt(b[1])[0]) / 2) > 60 for b in bridges)]
    if bus:
        pool = [c for c in pool if c[0][0] in stops and c[0][1] in stops] or pool
    (a, b), _, _ = min(pool, key=lambda c: c[2] + 0.5 * abs(c[1] - target))
    bridges.append((a, b))
    if bus and a in stops and b in stops: buses.append((a, b))

# hand-picked instead: BRIDGES="55-73 58-76 ..." and BUS="58-76 68-105 105-106" (bus links over bridges and on to a stop)
pair = lambda s: tuple(int(f) for f in s.split("-"))
if os.environ.get("BRIDGES"):
    bridges = [pair(s) for s in os.environ["BRIDGES"].split()]
    buses = [pair(s) for s in os.environ.get("BUS", "").split()]

taxi = dry["taxi"] + [e for e in bridges if e not in dry["taxi"]]
bus = dry["bus"] + buses

def parts(links):
    g = defaultdict(set)
    for a, b in links: g[a].add(b); g[b].add(a)
    seen, n = set(), 0
    for f in g:
        if f in seen: continue
        n += 1; todo = [f]
        while todo:
            x = todo.pop()
            if x not in seen: seen.add(x); todo += g[x]
    return n

# ---- buses drive on streets: a bus ride follows the shortest street route between its stops. Where that
# route is a long way round (no street where the bus used to cut through), lay the street if it fits
# cleanly, or else drop the ride if the bus network holds together without it.
import heapq
def route(a, b, streets):
    g = defaultdict(list)
    for x, y in streets:
        w = math.dist(pt(x), pt(y)); g[x].append((y, w)); g[y].append((x, w))
    q, done = [(0, a, [a])], set()
    while q:
        c, f, p = heapq.heappop(q)
        if f == b: return c, p
        if f in done: continue
        done.add(f)
        for n, w in g[f]:
            if n not in done: heapq.heappush(q, (c + w, n, p + [n]))
    return math.inf, []

DETOUR = 1.8
laid, dropped = [], []
for a, b in sorted(bus, key=lambda e: -route(*e, taxi)[0] / math.dist(pt(e[0]), pt(e[1]))):
    if route(a, b, taxi)[0] / math.dist(pt(a), pt(b)) <= DETOUR: continue
    fits = not wet(a, b) and clear(a, b) and not any(cross(pt(a), pt(b), pt(c), pt(d)) for c, d in taxi if len({a, b, c, d}) == 4)
    if fits:
        taxi.append((a, b)); laid.append((a, b))
    elif parts([e for e in bus if e != (a, b)]) == parts(bus):
        bus.remove((a, b)); dropped.append((a, b))
print(f"streets laid for buses {laid}; bus rides dropped {dropped}")

# a bus stops at every stop it passes: a ride whose street route runs through another stop becomes rides stop to stop
split = []
stops_now = {f for e in bus for f in e}
for a, b in list(bus):
    p = route(a, b, taxi)[1]
    halts = [a] + [f for f in p[1:-1] if f in stops_now] + [b]
    if len(halts) > 2:
        bus.remove((a, b))
        for x, y in zip(halts, halts[1:]):
            if (x, y) not in bus and (y, x) not in bus: bus.append((x, y))
        split.append((a, b, halts))
print(f"bus rides split at the stops they pass: {split}")

new = dict(city, taxi=sorted(map(list, taxi)), bus=sorted(map(list, bus)), bridges=[list(e) for e in bridges])
json.dump(new, open(out, "w"), separators=(",", ":"))

print(f"bridges {bridges}  (bus on {buses})")
print(f"taxi {len(city['taxi'])} -> {len(taxi)} links, {parts(taxi)} part(s); bus {len(city['bus'])} -> {len(bus)} links, {parts(bus)} part(s)")
print(f"all lines but the ferry: {parts(taxi + bus + [tuple(e) for e in city['ubahn']])} part(s)")
