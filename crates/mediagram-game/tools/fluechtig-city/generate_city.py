"""Generates Flüchtig's big city: `cities/fluechtig-big-city.json` in the game crate.

usage: python3 generate_city.py [seed] [out.json]     (seed 5 is the city in the game)

The totals are those of the genre's classic board: 199 fields; taxi 346 links; bus 99 links
on 62 stations; U-Bahn 20 links on 14 stations; a ferry of 3 links on 4 piers. The layout is
ours, made from the seed: a warped street grid with squares, a river and bridges. Which seed
and settings play evenly is measured with `balance_sim.py`; a compact grid and many bridges
did it. Environment variables override the settings below.
"""
import json, math, os, random, sys
from collections import Counter, defaultdict

SEED = int(sys.argv[1]) if len(sys.argv) > 1 else 5
OUT = sys.argv[2] if len(sys.argv) > 2 else None
rnd = random.Random(SEED)
COLS, ROWS = int(os.environ.get("COLS", 18)), int(os.environ.get("ROWS", 11))
X0, X1, Y0, Y1 = 26, 1142, 22, 502            # where fields may sit on the 1168x540 plan
TARGET = {"taxi": 346, "bus": 99, "ubahn": 20}
BUS_STOPS, UBAHN_STOPS, BRIDGES = 62, 14, int(os.environ.get("BRIDGES", 16))
DX, DY = (X1 - X0) / (COLS - 1), (Y1 - Y0) / (ROWS - 1)

# ---- the river: a smooth curve across the plan, sampled densely
RIVER_KNOTS = [(-40, 205), (150, 190), (330, 262), (520, 318), (700, 262), (880, 212), (1040, 268), (1210, 300)]
def catmull(p, n=24):
    pts = [p[0]] + p + [p[-1]]
    out = []
    for i in range(1, len(pts) - 2):
        a, b, c, d = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        for s in range(n):
            t = s / n
            out.append(tuple(0.5 * (2 * b[k] + (c[k] - a[k]) * t + (2 * a[k] - 5 * b[k] + 4 * c[k] - d[k]) * t * t + (3 * b[k] - a[k] - 3 * c[k] + d[k]) * t ** 3) for k in (0, 1)))
    return out + [p[-1]]
RIVER = catmull(RIVER_KNOTS)
def river_y(x):
    for (ax, ay), (bx, by) in zip(RIVER, RIVER[1:]):
        if ax <= x <= bx:
            return ay + (by - ay) * (x - ax) / (bx - ax or 1)
    return RIVER[-1][1]

JIT = float(os.environ.get("JIT", 17))
SLACK = float(os.environ.get("SLACK", 0.66))
# ---- fields: a warped, jittered grid; one field left out; pushed off the water
skip = set()   # the grid's spare fields are left out: parks and squares
while len(skip) < COLS * ROWS - 199:
    cell = (rnd.randrange(1, ROWS - 1), rnd.randrange(1, COLS - 1))
    if all(abs(cell[0] - r) + abs(cell[1] - c) > 2 for r, c in skip):
        skip.add(cell)
def dry(x, y):
    """The spot moved off the water: at least 31 from the river's middle."""
    rx, ry = min(RIVER, key=lambda p: math.hypot(p[0] - x, p[1] - y))
    gap = math.hypot(x - rx, y - ry)
    if gap >= 31:
        return x, y
    return (rx + (x - rx) * 31 / gap, ry + (y - ry) * 31 / gap) if gap > 4 else (x, ry + (31 if y >= river_y(x) else -31))
pts, grid_row = [], []
for _ in range(199 - COLS * ROWS):   # a grid short of 199 gets squares: a field in the middle of a block
    r, c = rnd.randrange(1, ROWS - 2), rnd.randrange(1, COLS - 2)
    pts.append(dry(X0 + (c + 0.5) * DX + rnd.uniform(-6, 6), Y0 + (r + 0.5) * DY + rnd.uniform(-6, 6)))
    grid_row.append(r)
for r in range(ROWS):
    for c in range(COLS):
        if (r, c) in skip:
            continue
        x, y = X0 + c * DX, Y0 + r * DY
        x += 13 * math.sin(y / 97 + 1.3) + rnd.uniform(-JIT, JIT)
        y += 11 * math.sin(x / 131 + 0.4) + rnd.uniform(-JIT, JIT) * min(0.85, DY / DX)
        pts.append(dry(x, y))
        grid_row.append(r)
for _ in range(60):   # no two fields closer than 38, and none on the water
    for i in range(len(pts)):
        for j in range(i + 1, len(pts)):
            (ax, ay), (bx, by) = pts[i], pts[j]
            d = math.hypot(bx - ax, by - ay)
            if d < 38:
                ux, uy, push = (bx - ax) / (d or 1), (by - ay) / (d or 1), (38 - d) / 2
                pts[i], pts[j] = (ax - ux * push, ay - uy * push), (bx + ux * push, by + uy * push)
    pts = [dry(min(max(x, X0), X1), min(max(y, Y0), Y1)) for x, y in pts]
pts = [(round(x), round(y)) for x, y in pts]
N = len(pts)
assert N == 199, N
dist = lambda a, b: math.hypot(pts[a][0] - pts[b][0], pts[a][1] - pts[b][1])

def ccw(a, b, c): return (c[1] - a[1]) * (b[0] - a[0]) > (b[1] - a[1]) * (c[0] - a[0])
def cross(p, q, r, s): return ccw(p, r, s) != ccw(q, r, s) and ccw(p, q, r) != ccw(p, q, s)
def wet(a, b):
    p, q = pts[a], pts[b]
    lo, hi = min(p[0], q[0]) - 12, max(p[0], q[0]) + 12
    return any(cross(p, q, r, s) for r, s in zip(RIVER, RIVER[1:]) if hi >= r[0] and s[0] >= lo)

def gabriel(ids, reach, slack=1.0):
    """Neighbour links: no third field inside the circle on the link (`slack` < 1 shrinks it and lets diagonals in)."""
    out = []
    for i, a in enumerate(ids):
        for b in ids[i + 1:]:
            d = dist(a, b)
            if d > reach:
                continue
            mx, my = (pts[a][0] + pts[b][0]) / 2, (pts[a][1] + pts[b][1]) / 2
            if all(math.hypot(pts[c][0] - mx, pts[c][1] - my) >= slack * d / 2 for c in ids if c != a and c != b):
                out.append((a, b))
    return out

def connected(ids, edges):
    adj = defaultdict(list)
    for a, b in edges:
        adj[a].append(b); adj[b].append(a)
    seen, todo = {ids[0]}, [ids[0]]
    while todo:
        for m in adj[todo.pop()]:
            if m not in seen:
                seen.add(m); todo.append(m)
    return len(seen) == len(ids)

def trim(ids, edges, want, keep=()):
    """Drops the longest links at the busiest corners until `want` are left; never cuts the net or leaves a dead end."""
    edges = list(edges)
    while len(edges) > want:
        deg = Counter(n for e in edges for n in e)
        order = sorted(edges, key=lambda e: -(LONG * dist(*e) / DX + BUSY * (deg[e[0]] + deg[e[1]]) + rnd.uniform(0, 2)))
        for e in order:
            if e in keep or deg[e[0]] <= 2 or deg[e[1]] <= 2:
                continue
            rest = [x for x in edges if x != e]
            if connected(ids, rest):
                edges = rest
                break
        else:
            raise SystemExit("cannot trim further")
    return edges

# ---- taxi: streets between neighbours; the river is crossed on bridges only
every = list(range(N))
LONG, BUSY = float(os.environ.get("LONG", 0.3)), float(os.environ.get("BUSY", 0.02))
streets = []
for e in sorted(gabriel(every, 2.1 * DX, slack=SLACK), key=lambda e: dist(*e)):   # shortest first, and no street crosses another
    if not any(cross(pts[e[0]], pts[e[1]], pts[a], pts[b]) for a, b in streets if len({a, b, *e}) == 4):
        streets.append(e)
crossings = sorted((e for e in streets if wet(*e)), key=lambda e: (pts[e[0]][0] + pts[e[1]][0]) / 2)
bridges = []
for k in range(BRIDGES):
    x = X0 + (k + 0.5) * (X1 - X0) / BRIDGES
    bridges.append(min((e for e in crossings if e not in bridges), key=lambda e: abs((pts[e[0]][0] + pts[e[1]][0]) / 2 - x) + 0.6 * dist(*e)))
streets = [e for e in streets if not wet(*e) or e in bridges]
for p in every:   # no dead ends: a field with one street gets its nearest dry neighbour too
    while sum(p in e for e in streets) < 2:
        q = min((q for q in every if q != p and (p, q) not in streets and (q, p) not in streets and not wet(p, q)), key=lambda q: dist(p, q))
        streets.append((min(p, q), max(p, q)))
if len(streets) < TARGET["taxi"]:
    raise SystemExit(f"only {len(streets)} streets")
taxi = trim(every, streets, TARGET["taxi"], keep=set(bridges))
LONG, BUSY = 1.0, 0.45   # line nets lose their longest links first

def spread(pool, count, first, noise=6):
    """`count` fields of `pool`, far from each other; `noise` loosens the even spacing."""
    chosen = [first]
    while len(chosen) < count:
        chosen.append(max((p for p in pool if p not in chosen), key=lambda p: min(dist(p, c) for c in chosen) + rnd.uniform(0, noise)))
    return sorted(chosen)

def near_bridge(e):
    mx, my = (pts[e[0]][0] + pts[e[1]][0]) / 2, (pts[e[0]][1] + pts[e[1]][1]) / 2
    return any(math.hypot(mx - (pts[a][0] + pts[b][0]) / 2, my - (pts[a][1] + pts[b][1]) / 2) < 50 for a, b in bridges)

def net(stops, want, reach, dry):
    """A line net over `stops`: neighbours first, then the next-shortest links until `want` are there."""
    links = [e for e in gabriel(stops, reach) if not (dry and wet(*e) and not near_bridge(e))]
    if len(links) < want:
        more = sorted(((a, b) for i, a in enumerate(stops) for b in stops[i + 1:] if (a, b) not in links and dist(a, b) <= reach and not (dry and wet(a, b) and not near_bridge((a, b)))), key=lambda e: dist(*e))
        for e in more:
            if len(links) == want:
                break
            if not any(cross(pts[e[0]], pts[e[1]], pts[a], pts[b]) for a, b in links if len({a, b, *e}) == 4):
                links.append(e)
    if not connected(stops, links):
        raise SystemExit("line net falls apart")
    return trim(stops, links, want)

taxi_deg = Counter(n for e in taxi for n in e)
E = lambda k, d: float(os.environ.get(k, d))
bus_stops = spread([p for p in every if taxi_deg[p] >= 3], BUS_STOPS, rnd.randrange(N), E("BUSNOISE", 6))
LONG = E("BUSLONG", 1.0)
bus = net(bus_stops, TARGET["bus"], E("BUSREACH", 3.4) * DX, dry=True)
LONG = 1.0
core = E("UCORE", 1.0)   # the U-Bahn keeps to the middle of the city: this share of the plan's width and height
cx, cy = (X0 + X1) / 2, (Y0 + Y1) / 2
inner = [p for p in bus_stops if abs(pts[p][0] - cx) <= core * (X1 - X0) / 2 and abs(pts[p][1] - cy) <= core * (Y1 - Y0) / 2]
ubahn_stops = spread(inner, UBAHN_STOPS, inner[rnd.randrange(len(inner))])
ubahn = net(ubahn_stops, TARGET["ubahn"], 9 * DX, dry=False)

# ---- ferry: four piers down the river, away from the bridges, bank to bank
bridge_ends = {n for e in bridges for n in e}
shore = [p for p in every if abs(pts[p][1] - river_y(pts[p][0])) < 40 and p not in bridge_ends]
piers = []
for k in range(4):
    x = X0 + (k + 0.5) * (X1 - X0) / 4
    north = k % 2 == 0
    piers.append(min((p for p in shore if (pts[p][1] < river_y(pts[p][0])) == north and p not in piers), key=lambda p: abs(pts[p][0] - x)))
ferry = list(zip(piers, piers[1:]))

# ---- number the fields row by row, as they sit on the plan
order = sorted(every, key=lambda p: (grid_row[p], pts[p][0]))
rank = {p: i + 1 for i, p in enumerate(order)}
ren = lambda es: sorted(tuple(sorted((rank[a], rank[b]))) for a, b in es)
city = {
    "size": [1168, 540],
    "spots": [list(pts[p]) for p in order],
    "taxi": ren(taxi), "bus": ren(bus), "ubahn": ren(ubahn), "ferry": [[rank[a], rank[b]] for a, b in ferry],
    "river": [[round(x), round(y)] for x, y in RIVER[::6]],
}

# ---- report
def shape(links):
    adj = defaultdict(set)
    for a, b in links:
        adj[a].add(b); adj[b].add(a)
    total = []
    for s in adj:
        d, q = {s: 0}, [s]
        for n in q:
            for m in adj[n]:
                if m not in d:
                    d[m] = d[n] + 1; q.append(m)
        total += [v for v in d.values() if v]
    return f"mean distance {sum(total) / len(total):.2f}, diameter {max(total)}"
print(f"seed {SEED}: fields {N}, taxi {len(taxi)}, bus {len(bus)} on {len(bus_stops)}, ubahn {len(ubahn)} on {len(ubahn_stops)}, ferry {len(ferry)} on {len(piers)}, bridges {len(bridges)}")
print("  all transports:", shape(city["taxi"] + city["bus"] + city["ubahn"] + [tuple(e) for e in city["ferry"]]), "(reference 4.71, 10)")
print("  taxi only:     ", shape(city["taxi"]), "(reference 8.31, 20)")
print("  taxi degrees:  ", dict(sorted(Counter(Counter(n for e in taxi for n in e).values()).items())), "(reference {2: 24, 3: 82, 4: 72, 5: 17, 6: 3, 7: 1})")
print("  closest fields: %.0f px" % min(dist(a, b) for a in every for b in every if a < b))
print("  longest links: taxi %.0f, bus %.0f, ubahn %.0f px" % (max(dist(*e) for e in taxi), max(dist(*e) for e in bus), max(dist(*e) for e in ubahn)))

# ---- starts: eighteen fields far from each other over every line but the ferry, dealt in turn to hunters and fugitive
reach = defaultdict(set)
for kind in ("taxi", "bus", "ubahn"):
    for a, b in city[kind]:
        reach[a].add(b); reach[b].add(a)
def hops(s):
    d, q = {s: 0}, [s]
    for n in q:
        for m in reach[n]:
            if m not in d:
                d[m] = d[n] + 1; q.append(m)
    return d
far = {f: hops(f) for f in reach}
fields = sorted(reach)
starts = [fields[len(fields) // 2]]
while len(starts) < 18:
    starts.append(max((f for f in fields if f not in starts), key=lambda f: min(far[f][c] for c in starts)))
city["hunter_starts"], city["fugitive_starts"] = sorted(starts[0::2]), sorted(starts[1::2])
if OUT:
    rows = [f' "{k}": {json.dumps(v, separators=(",", ":"))}' for k, v in city.items()]
    open(OUT, "w").write("{\n" + ",\n".join(rows) + "\n}\n")
