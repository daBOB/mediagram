"""Bot-against-bot win rates of the hidden-move chase on different boards, same bots and rules for each.

usage: python3 balance_sim.py GAMES board[:rules] ...
  board = a city JSON (as generate_city.py writes) | a connections list ("a b kind" per line) | small
  rules = game5 | game4 | game3 | game2 (the big city's rules as Flüchtig plays them with that many
          hunter figures, `Rules::big_for`; game = game5, the default) | classic | small
  HUNTERS=pack makes the hunters close in as one pack instead of each taking a target.

The bots are simple (hunters close in on the possible hideouts, the fugitive keeps his distance),
so the numbers compare boards with each other; they are not what people at a table would score.
"""
import json, random, sys
from collections import defaultdict

# classic: a double move fills two rounds of the travel log, so the hunters lose a turn to it
CLASSIC = dict(name="classic", rounds=24, reveals={3, 8, 13, 18, 24}, hunters=5, hunter=dict(taxi=10, bus=8, ubahn=4),
               fugitive=dict(taxi=4, bus=3, ubahn=3, black=5, double=2), double_fills_two_rounds=True)
# game: the same numbers as Flüchtig plays them, where a double move is two rides in one round
GAME = dict(CLASSIC, name="game", double_fills_two_rounds=False)


def ladder(figures):
    """Flüchtig's rules for a table hunting with `figures` (2 to 5), as `Rules::big_for` sets them:
    fewer figures see the fugitive every third round and grant fewer shadow rides and double moves."""
    reveals = {3, 8, 13, 18, 24} if figures == 5 else set(range(3, 25, 3))
    black, double = {5: (5, 2), 4: (5, 2), 3: (2, 1), 2: (1, 0)}[figures]
    return dict(GAME, name=f"game{figures}", hunters=figures, reveals=reveals, fugitive=dict(GAME["fugitive"], black=black, double=double))


assert ladder(5)["reveals"] == GAME["reveals"] and ladder(5)["fugitive"] == GAME["fugitive"]
assert ladder(2)["fugitive"] == dict(taxi=4, bus=3, ubahn=3, black=1, double=0) and ladder(3)["reveals"] == {3, 6, 9, 12, 15, 18, 21, 24}
SMALL = dict(name="small", rounds=18, reveals={3, 8, 13, 18}, hunters=4, hunter=dict(taxi=10, bus=6, ubahn=3),
             fugitive=dict(taxi=4, bus=3, ubahn=2, black=3, double=1), double_fills_two_rounds=False)
SMALL_STARTS = ([3, 14, 19, 28, 35, 44, 16, 41], [6, 10, 24, 27, 33, 46, 39, 12])

def load(name):
    links, starts = defaultdict(set), None
    if name == "small":   # Flüchtig's 48-field city, as fluechtig_map.rs builds it
        ident = lambda r, c: r * 8 + c + 1
        no_taxi = {(11, 12), (36, 37), (5, 13), (38, 46), (20, 21)}
        for r in range(6):
            for c in range(8):
                if c + 1 < 8: links["taxi"].add((ident(r, c), ident(r, c + 1)))
                if r + 1 < 6 and (r != 2 or c in (1, 4, 6)): links["taxi"].add((ident(r, c), ident(r + 1, c)))
        links["taxi"] |= {(2, 11), (14, 23), (27, 36), (31, 40), (35, 44)}
        links["taxi"] -= no_taxi
        for kind, lines in (("bus", [[9, 11, 13, 15], [34, 36, 38, 40], [4, 21, 37], [2, 18, 26, 42], [7, 23, 31, 47]]), ("ubahn", [[9, 21, 47], [7, 21, 42]]), ("ferry", [[17, 27, 22, 32]])):
            for line in lines:
                links[kind] |= set(zip(line, line[1:]))
        starts = SMALL_STARTS
    elif name.endswith(".json"):
        city = json.load(open(name))
        for kind in ("taxi", "bus", "ubahn", "ferry"):
            links[kind] = {tuple(e) for e in city[kind]}
        if "hunter_starts" in city:
            starts = (city["hunter_starts"], city["fugitive_starts"])
    else:
        rename = {"underground": "ubahn", "water": "ferry"}
        for line in open(name):
            p = line.split()
            if len(p) >= 3 and p[0].isdigit():
                links[rename.get(p[2], p[2])].add((int(p[0]), int(p[1])))
    exits = defaultdict(list)   # field -> [(to, kind)]
    for kind, es in links.items():
        for a, b in es:
            exits[a].append((b, kind)); exits[b].append((a, kind))
    fields = sorted(exits)
    dist = {}                   # hunters' distances: every line but the ferry
    for s in fields:
        d, q = {s: 0}, [s]
        for n in q:
            for m, kind in exits[n]:
                if kind != "ferry" and m not in d:
                    d[m] = d[n] + 1; q.append(m)
        dist[s] = d
    return fields, exits, dist, starts or start_fields(fields, dist)

def start_fields(fields, dist, count=18):
    """As generate_city.py picks them: fields far from each other, dealt in turn to hunters and fugitive."""
    chosen = [fields[len(fields) // 2]]
    while len(chosen) < count:
        chosen.append(max((f for f in fields if f not in chosen), key=lambda f: min(dist[f][c] for c in chosen)))
    return sorted(chosen[0::2]), sorted(chosen[1::2])

def play(board, rules, rng):
    fields, exits, dist, (hunter_starts, fugitive_starts) = board
    at, hunters = rng.choice(fugitive_starts), [dict(at=f, **rules["hunter"]) for f in rng.sample(hunter_starts, rules["hunters"])]
    mine = dict(rules["fugitive"])
    hideouts = set(fugitive_starts) - {h["at"] for h in hunters}
    taken = lambda: {h["at"] for h in hunters}
    safety = lambda f: min(dist[h["at"]][f] for h in hunters)

    def steps(frm, stock):
        out = []
        for to, kind in exits[frm]:
            if to in taken():
                continue
            if kind != "ferry" and stock[kind] > 0: out.append((to, kind))
            if stock["black"] > 0: out.append((to, "black"))
        return out

    def best(frm, stock, after_reveal):
        options = steps(frm, stock)
        if not options:
            return None
        to = max(options, key=lambda o: safety(o[0]) * 10 + min(len(exits[o[0]]), 6) + rng.random())[0]
        kinds = [k for t, k in options if t == to]
        plain = [k for k in kinds if k != "black"]
        if "black" in kinds and (after_reveal or not plain):
            return to, "black"
        return to, max(plain, key=lambda k: stock[k])

    def spread(ticket):
        nonlocal hideouts
        hideouts = {to for f in hideouts for to, kind in exits[f] if (ticket == "black" or kind == ticket)} - taken()

    rnd, last_reveal = 0, -9
    while rnd < rules["rounds"]:
        rnd += 1
        move = best(at, mine, rnd == last_reveal + 1)
        if move is None:
            return "hunters"
        double = mine["double"] > 0 and safety(move[0]) <= 1 and rnd < rules["rounds"]
        for leg in range(2 if double else 1):
            if leg:
                if rules["double_fills_two_rounds"]: rnd += 1
                move = best(at, mine, False)
                if move is None: break
            at, ticket = move
            mine[ticket] -= 1
            spread(ticket)
            if rnd in rules["reveals"] and not (double and leg == 0 and not rules["double_fills_two_rounds"]):
                hideouts, last_reveal = {at}, rnd
        if double: mine["double"] -= 1
        moved, claimed = False, set()
        for h in hunters:
            others = {o["at"] for o in hunters if o is not h}
            options = [(to, kind) for to, kind in exits[h["at"]] if kind != "ferry" and h[kind] > 0 and to not in others]
            if not options:
                continue
            free = [f for f in hideouts if f not in claimed] or list(hideouts)
            target = min(free, key=lambda f: (dist[h["at"]][f], rng.random())) if free else at
            claimed.add(target)
            mean = lambda f: sum(dist[f][x] for x in hideouts) / max(len(hideouts), 1)
            if PACK:   # second kind of hunter: no targets, everyone closes in on the middle of the hideouts
                to, kind = min(options, key=lambda o: (mean(o[0]), rng.random()))
            else:
                to, kind = min(options, key=lambda o: (dist[o[0]][target], mean(o[0]), rng.random()))
            h["at"], moved = to, True
            h[kind] -= 1; mine[kind] += 1
            if to == at:
                return "hunters"
            hideouts.discard(to)
        if not moved:
            return "fugitive"
    return "fugitive"

import os
PACK = os.environ.get("HUNTERS") == "pack"
games = int(sys.argv[1])
for spec in sys.argv[2:]:
    name, _, which = spec.partition(":")
    rules = {"classic": CLASSIC, "small": SMALL, **{f"game{n}": ladder(n) for n in range(2, 6)}}.get(which, GAME)
    board = load(name)
    rng = random.Random(1)
    wins = sum(play(board, rules, rng) == "fugitive" for _ in range(games))
    print(f"{name.split('/')[-1]:28} {len(board[0]):3} fields, {rules['name']:7} rules: fugitive wins {100 * wins / games:5.1f} %")
