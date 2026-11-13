"""Puts the big city's streets and buses over the river on a few drawn bridges.

usage: python3 -I add_bridges.py CITY.json OUT.json --bridges 55-73 58-76 117-135 83-101 68-105 89-108 \
           --bus 58-76 68-105 105-106

Runs after `generate_city.py`, whose city lets streets and bus lines cross open water anywhere.
Every taxi and bus link across the river goes; each `--bridges` pair (north-south) becomes a
street, each `--bus` pair a bus ride (only if not one already). U-Bahn links stay, they run
under the river, and so does the ferry. Then buses drive on streets: where a ride's shortest
street route is over 1.8x its straight distance, a street is laid along it if it fits cleanly
(else the ride goes, when the bus network holds together without it), and a ride whose street
route passes another stop is split stop to stop. Every field stays where it is; every network
must still be one piece. Running it again on its own output changes nothing. The TV finds the
bridges itself (a street or bus ride with its ends on both sides of the river) and draws the
bus along the streets, so the file keeps `generate_city.py`'s format and gains no key.
"""
import argparse, heapq, json, math
from collections import defaultdict

DETOUR = 1.8

args = argparse.ArgumentParser()
args.add_argument("city")
args.add_argument("out")
pair = lambda s: tuple(int(f) for f in s.split("-"))
args.add_argument("--bridges", nargs="+", type=pair, required=True)
args.add_argument("--bus", nargs="*", type=pair, default=[])
opt = args.parse_args()

city = json.load(open(opt.city))
S, R = city["spots"], city["river"]
pt = lambda f: S[f - 1]
key = lambda e: (min(e), max(e))


def ccw(a, b, c):
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])


def cross(p, q, r, s):
    return ccw(p, q, r) * ccw(p, q, s) < 0 and ccw(r, s, p) * ccw(r, s, q) < 0


def wet(a, b):
    """The link runs over the river's middle line."""
    return any(cross(pt(a), pt(b), r, s) for r, s in zip(R, R[1:]))


def clear(a, b, gap=16):
    """No other field closer than `gap` to the segment."""
    (ax, ay), (bx, by) = pt(a), pt(b)
    L2 = (bx - ax) ** 2 + (by - ay) ** 2
    for f in range(1, len(S) + 1):
        if f in (a, b):
            continue
        px, py = pt(f)
        t = max(0, min(1, ((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / L2))
        if math.hypot(px - ax - t * (bx - ax), py - ay - t * (by - ay)) < gap:
            return False
    return True


def parts(links):
    """How many separate pieces the links make."""
    g = defaultdict(set)
    for a, b in links:
        g[a].add(b)
        g[b].add(a)
    seen, n = set(), 0
    for f in g:
        if f in seen:
            continue
        n += 1
        todo = [f]
        while todo:
            x = todo.pop()
            if x not in seen:
                seen.add(x)
                todo += g[x]
    return n


def route(a, b, streets):
    """The shortest street route from a to b: its length and fields."""
    g = defaultdict(list)
    for x, y in streets:
        w = math.dist(pt(x), pt(y))
        g[x].append((y, w))
        g[y].append((x, w))
    q, done = [(0, a, [a])], set()
    while q:
        c, f, p = heapq.heappop(q)
        if f == b:
            return c, p
        if f in done:
            continue
        done.add(f)
        for n, w in g[f]:
            if n not in done:
                heapq.heappush(q, (c + w, n, p + [n]))
    return math.inf, []


def add(links, e):
    if key(e) not in {key(x) for x in links}:
        links.append(e)


taxi = [tuple(e) for e in city["taxi"] if not wet(*e)]
bus = [tuple(e) for e in city["bus"] if not wet(*e)]
for e in opt.bridges:
    add(taxi, e)
for e in opt.bus:
    add(bus, e)

laid, dropped = [], []
for a, b in sorted(bus, key=lambda e: -route(*e, taxi)[0] / math.dist(pt(e[0]), pt(e[1]))):
    if route(a, b, taxi)[0] / math.dist(pt(a), pt(b)) <= DETOUR:
        continue
    fits = not wet(a, b) and clear(a, b) and not any(cross(pt(a), pt(b), pt(c), pt(d)) for c, d in taxi if len({a, b, c, d}) == 4)
    if fits:
        taxi.append((a, b))
        laid.append((a, b))
    elif parts([e for e in bus if e != (a, b)]) == parts(bus):
        bus.remove((a, b))
        dropped.append((a, b))

split = []
stops = {f for e in bus for f in e}
for a, b in list(bus):
    halts = [a] + [f for f in route(a, b, taxi)[1][1:-1] if f in stops] + [b]
    if len(halts) > 2:
        bus.remove((a, b))
        for e in zip(halts, halts[1:]):
            add(bus, e)
        split.append(halts)

ubahn = [tuple(e) for e in city["ubahn"]]
assert parts(taxi) == 1 and parts(bus) == 1, "a network fell apart"
assert parts(taxi + bus + ubahn) == 1
assert len({key(e) for e in taxi}) == len(taxi) and len({key(e) for e in bus}) == len(bus), "a link twice"
crossing = sorted(key(e) for e in taxi + bus if wet(*e))
assert {key(e) for e in crossing} <= {key(e) for e in opt.bridges}, f"a line over open water: {crossing}"

city = {k: v for k, v in city.items() if k != "bridges"}
city["taxi"], city["bus"] = sorted(map(list, taxi)), sorted(map(list, bus))
rows = [f' "{k}": {json.dumps(v, separators=(",", ":"))}' for k, v in city.items()]
open(opt.out, "w").write("{\n" + ",\n".join(rows) + "\n}\n")

print(f"streets laid for buses: {' '.join(f'{a}-{b}' for a, b in laid) or 'none'}; rides dropped: {dropped or 'none'}")
print(f"rides split at the stops they pass: {' '.join('-'.join(map(str, h)) for h in split) or 'none'}")
print(f"taxi {len(taxi)}, bus {len(bus)} on {len(stops)} stops, U-Bahn {len(ubahn)}, ferry {len(city['ferry'])}")
