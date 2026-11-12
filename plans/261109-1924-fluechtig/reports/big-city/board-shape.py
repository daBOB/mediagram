"""Aggregate shape figures of a board: how far lines carry, how fast the reachable area grows."""
import json, sys
from collections import defaultdict
def load(name):
    links = defaultdict(set)
    if name.endswith(".json"):
        city = json.load(open(name))
        for kind in ("taxi", "bus", "ubahn", "ferry"):
            links[kind] = {tuple(e) for e in city[kind]}
    else:
        rename = {"underground": "ubahn", "water": "ferry"}
        for line in open(name):
            p = line.split()
            if len(p) >= 3 and p[0].isdigit():
                links[rename.get(p[2], p[2])].add((int(p[0]), int(p[1])))
    return links
def bfs(adj, s):
    d, q = {s: 0}, [s]
    for n in q:
        for m in adj[n]:
            if m not in d:
                d[m] = d[n] + 1; q.append(m)
    return d
for name in sys.argv[1:]:
    links = load(name)
    taxi, every = defaultdict(set), defaultdict(set)
    for kind, es in links.items():
        for a, b in es:
            if kind == "taxi": taxi[a].add(b); taxi[b].add(a)
            if kind != "ferry": every[a].add(b); every[b].add(a)
    td = {f: bfs(taxi, f) for f in taxi}
    hop = lambda kind: sum(td[a][b] for a, b in links[kind]) / len(links[kind])
    ball = lambda k: sum(sum(1 for v in bfs(every, f).values() if 0 < v <= k) for f in every) / len(every)
    exits = sum(len(every[f]) for f in every) / len(every)
    both = len({n for e in links["bus"] for n in e} & {n for e in links["ubahn"] for n in e})
    print(f"{name.split('/')[-1]:24} a bus link spans {hop('bus'):.2f} taxi steps, a U-Bahn link {hop('ubahn'):.2f}; fields within 1/2/3 moves: {ball(1):.1f} / {ball(2):.1f} / {ball(3):.1f}; neighbours per field {exits:.2f}; U-Bahn stations that are bus stations: {both}")
