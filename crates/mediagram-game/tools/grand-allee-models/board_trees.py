"""Trees for the board: round crowns in the Kurpark, by the sea and in the
gardens, slim poplars on the town side; closed low-poly geometry, no cut-outs.

Spots come from a jittered grid (fixed seed, so every build is the same) and
are kept off the road, water, plots, paths and buildings, and where neither
the tree's shadow (the low sun throws it ~3x its height to the east-north-east)
nor its crown as the TV sees it (55 degrees down, so it covers the ground
north of it) would fall on a plot, the Bank or the Rathaus; the crown keeps
off every field too, the shadow only off the special fields (their inlays)."""

import math
import random

import bmesh
import numpy as np
from mathutils import Matrix

import board_path as bp
import materials as m
import parts as p
from board_kit import BANK, BEACH, FIELD_KINDS, KERB_W, PATH_W, SUN, SUN_UP, TV_PITCH, to_line

KINDS = {"round": (4.6, 1.5), "poplar": (6.0, 0.9)}  # height, crown radius
MAX_TREES = 95


def spots(L, paths, avoid=(), seed=7, spacing=4.0):
    """[(kind, x, y, size factor)] for every tree that fits; `avoid`: more boxes to keep off (the town's houses)."""
    rng = random.Random(seed)
    w, h = bp.BOARD
    C = np.array([(x + rng.uniform(-1.4, 1.4), y + rng.uniform(-1.4, 1.4))
                  for x in np.arange(-w / 2 + 2, w / 2 - 1, spacing) for y in np.arange(-h / 2 + 2, h / 2 - 1, spacing)])
    size = np.array([rng.uniform(0.85, 1.15) for _ in C])
    town = ~L.inside(C) & (C[:, 1] < -15)
    kind = np.where(town & (np.array([rng.random() for _ in C]) < 0.5), "poplar", "round")
    keep = []
    for name, (height, radius) in KINDS.items():
        r, hh = radius * size, height * size
        ok = (kind == name) & (np.abs(C[:, 0]) < w / 2 - r) & (np.abs(C[:, 1]) < h / 2 - r)
        ok &= L.road(C) > bp.ROAD / 2 + KERB_W + r + 0.2
        ok &= L.water(C) > BANK + 0.6 * r
        ok &= C[:, 1] < bp.SEA_Y - BEACH - 0.5
        ok &= ~L.in_boxes(C, L.plot_boxes(1.0) + [(x0 - 1, y0 - 1, x1 + 1, y1 + 1) for x0, y0, x1, y1 in L.buildings]
                          + [(x0 - 1.8, y0 - 1.8, x1 + 1.8, y1 + 1.8) for x0, y0, x1, y1 in avoid])
        for line in paths:
            ok &= to_line(C, line) > PATH_W / 2 + 0.5
        keep_out = L.plot_boxes(0.3) + L.buildings
        special = np.array([(f["x"], f["y"]) for f in L.fields if FIELD_KINDS[f["id"]] != "."])
        for t in np.linspace(0.3, 1.0, 6):
            shadow = C + np.c_[SUN[0] * hh / math.tan(SUN_UP) * t, SUN[1] * hh / math.tan(SUN_UP) * t]
            crown = C + np.c_[0 * hh, (hh / math.tan(TV_PITCH) + r) * t]
            near = np.hypot(shadow[:, None, 0] - special[None, :, 0], shadow[:, None, 1] - special[None, :, 1]).min(1)
            ok &= (near > 3.4 + 0.5 * r) & ~L.in_boxes(shadow, keep_out)
            ok &= (L.road(crown) > bp.ROAD / 2 + KERB_W + 0.5 * r) & ~L.in_boxes(crown, keep_out)
        keep += [(name, x, y, s) for (x, y), s, good in zip(C, size, ok) if good]
    rng.shuffle(keep)
    chosen = []
    for t in keep:
        r = KINDS[t[0]][1] * t[3]
        if len(chosen) < MAX_TREES and all(math.dist(t[1:3], c[1:3]) > r + KINDS[c[0]][1] * c[3] + 0.6 for c in chosen):
            chosen.append(t)
    return sorted(chosen, key=lambda t: (t[2], t[1]))


def grow(chosen):
    """Trunks in one object, crowns in three (two greens for the round ones, a darker one for poplars)."""
    bark = m.plain("tree_bark", "#5a4332")
    greens = [m.textured(f"tree_leaves_{i}", "sparse_grass", 2.0, c, "OVERLAY", 0.5)
              for i, c in enumerate(("#4f7a32", "#628c3c", "#3f6a33"))]
    rng = random.Random(11)
    trunks, crowns = [], [[], [], []]
    for i, (kind, x, y, s) in enumerate(chosen):
        height, radius = KINDS[kind]
        if kind == "round":
            trunks.append((x, y, 2.6 * s, 0.17 * s))
            crowns[i % 2] += [("ico", (x + dx * s, y + dy * s, (3.3 + dz) * s), radius * s * f) for dx, dy, dz, f in
                              ((0, 0, 0, 1.0), (0.8, 0.4, -0.4, 0.75), (-0.7, -0.5, -0.3, 0.72))]
        else:
            trunks.append((x, y, 1.2 * s, 0.14 * s))
            crowns[2].append(("cone", (x, y, 0.8 * s), radius * s, (height - 0.8) * s, rng.uniform(0, 1)))

    def trunk_mesh(bm):
        for x, y, h, r in trunks:
            bmesh.ops.create_cone(bm, cap_ends=True, segments=6, radius1=r, radius2=r * 0.75, depth=h,
                                  matrix=Matrix.Translation((x, y, h / 2)))

    def crown_mesh(items):
        def build(bm):
            for it in items:
                if it[0] == "ico":
                    bmesh.ops.create_icosphere(bm, subdivisions=1, radius=it[2],
                                               matrix=Matrix.Translation(it[1]) @ Matrix.Diagonal((1, 1, 0.85, 1)))
                else:
                    _, (x, y, z), r, h, turn = it
                    bmesh.ops.create_cone(bm, cap_ends=True, segments=8, radius1=r, radius2=0.0, depth=h,
                                          matrix=Matrix.Translation((x, y, z + h / 2)) @ Matrix.Rotation(turn, 4, "Z"))
        return build

    out = [p.mesh_object("tree_trunks", trunk_mesh, bark)]
    out += [p.mesh_object(f"tree_crowns_{i}", crown_mesh(items), greens[i]) for i, items in enumerate(crowns) if items]
    return out
