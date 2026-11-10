"""The board's ground, as the top-down bake sees it: terrain with the river,
pond and beach sunk into it, the water, town paving, park paths, plot lawns,
and the road's setts, inlays, icons and lettering. All of this is flat and
lives only in the bake scene. Kerbs and plot edging are the exceptions: they
are real geometry in the GLB too, textured from the same top-down atlas."""

import math

import bpy
import numpy as np

import board_ink as ink
import board_path as bp
import materials as m
import parts as p
from board_kit import (BANK, BEACH, BRIDGES, EDGING_GAP, EDGING_H, EDGING_W, FIELD_KINDS, ICON, ICONS, INK, INLAY,
                       KERB_H, KERB_W, KINDS, PATH_W, SEAM, TEXT, WATER_Z, area, inset, offset)

STEP = 0.4  # terrain grid; the river's banks get two cells, smooth-shaded


def terrain(L):
    """Grass at z = 0 with the river and pond channels and the beach sloping
    under the water level; a `zone` colour says where banks (R) and sand (G) are."""
    w, h = bp.BOARD
    xs, ys = np.arange(-w / 2, w / 2 + 1e-6, STEP), np.arange(-h / 2, h / 2 + 1e-6, STEP)
    X, Y = np.meshgrid(xs, ys)
    P = np.c_[X.ravel(), Y.ravel()]
    wet, sea = L.water(P), bp.SEA_Y - P[:, 1]
    z = np.maximum(np.clip((BANK - wet) / BANK, 0, 2), np.clip((BEACH - sea) / BEACH, 0, 2.5)) * WATER_Z  # the deeper wins
    nx, ny = len(xs), len(ys)
    me = bpy.data.meshes.new("ground_terrain")
    me.from_pydata(np.c_[P, z].tolist(), [], [(j * nx + i, j * nx + i + 1, (j + 1) * nx + i + 1, (j + 1) * nx + i)
                                             for j in range(ny - 1) for i in range(nx - 1)])
    me.shade_smooth()
    zone = me.color_attributes.new("zone", "FLOAT_COLOR", "POINT")
    bank, sand = (wet < BANK + 0.15).astype(float), np.clip((BEACH + 0.6 - sea) / 0.8, 0, 1)
    zone.data.foreach_set("color", np.c_[bank, sand, np.zeros_like(bank), np.ones_like(bank)].ravel().tolist())
    g = m.Graph("ground_terrain")
    att = g.nt.nodes.new("ShaderNodeAttribute")
    att.attribute_name = "zone"
    sep = g.nt.nodes.new("ShaderNodeSeparateColor")
    g.nt.links.new(att.outputs["Color"], sep.inputs[0])
    grass = g.image("sparse_grass", 4.0, "#5f8a3c", "OVERLAY", 0.45)
    banks = g.image("stone_wall", 1.6, "#9a9182")
    sandy = g.image("gravel_floor", 3.0, "#eadbb4", "OVERLAY", 0.35)
    me.materials.append(g.color(g.mix(sep.outputs["Green"], g.mix(sep.outputs["Red"], grass, banks), sandy)))
    return p.link(bpy.data.objects.new("ground_terrain", me))


def water():
    """One plane at the water level: it shows wherever the terrain dips under it.
    The river's blue-green deepens out to sea, with a faint ripple."""
    g = m.Graph("ground_water")
    deep = g.op("MINIMUM", g.op("MAXIMUM", g.op("DIVIDE", g.op("SUBTRACT", g.xyz.outputs["Y"], bp.SEA_Y - 1), 9.0), 0.0), 1.0)
    noise = g.nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = 0.35
    g.nt.links.new(g.obj, noise.inputs["Vector"])
    col = g.mix(deep, "#4f9aa6", "#1f5f86")
    g.color(g.mix(1.0, col, g.mix(noise.outputs["Fac"], "#d9d9d9", "#ffffff"), "MULTIPLY"))
    w, h = bp.BOARD
    return ink.flat("ground_water", ink.rect(-w / 2, -h / 2, w / 2, h / 2), lambda u, v: (u, v, WATER_Z), g.mat)


def paving(L):
    """The town side: paving from the south road's outer kerb to the board's edge
    (the Bank's forecourt included) and a lawn strip along the south edge; a
    small square before the Rathaus."""
    w, h = bp.BOARD
    edge = offset(L.edge(L.outer, 22, 1), KERB_W)  # south road, Rathaus end to the Bank's bridge
    town = edge + [(-w / 2, -2.4), (-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, edge[0][1])]
    stone = m.textured("ground_paving", "sandstone_blocks_05", 2.2, "#d3c4a8", "OVERLAY", 0.55)
    at = lambda z: (lambda u, v: (u, v, z))  # noqa: E731
    ink.flat("ground_town", [town], at(0.003), stone)
    rx0, ry0, rx1, ry1 = L.buildings[1]
    ink.flat("ground_square", ink.rect(edge[0][0] + 0.5, ry0 - 2.5, rx1 + 1.5, ry1 + 1.0), at(0.003), stone)
    lawn = m.textured("ground_garden", "sparse_grass", 4.0, "#6a9a44", "OVERLAY", 0.45)
    ink.flat("ground_gardens", ink.rect(-w / 2, -h / 2, w / 2, -h / 2 + 7.0), at(0.005), lawn)


def plot_lawns(L):
    """A plot's land is a mown lawn in stripes, from the road's kerb back past its hotel."""
    g = m.Graph("ground_plot_lawn")
    band = g.op("LESS_THAN", g.op("FRACT", g.op("DIVIDE", g.xyz.outputs["X"], 1.4)), 0.5)
    g.color(g.image("sparse_grass", 4.0, g.mix(band, "#7fae52", "#6c9a45"), "OVERLAY", 0.35))
    ink.flat("ground_plot_lawns", [p["land"] for p in L.plots], lambda u, v: (u, v, 0.006), g.mat)


def park_paths(L):
    """Gravel paths in the Kurpark: arcs round the pond, spokes towards the road's
    waist, promenades along the river; all cut back where they would reach a
    plot's land or the road."""
    cx, cy, r = bp.POND
    r += BANK + 1.5
    ok = lambda q, near=0.3: L.inside([q])[0] and not L.in_land([q], PATH_W / 2 + 0.3)[0] \
        and L.road([q])[0] > bp.ROAD / 2 + KERB_W + near  # noqa: E731
    lines = [[(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in range(a0, a1 + 1, 6)]
             for a0, a1 in ((30, 150), (210, 330))]
    for sign in (1, -1):
        spoke = [(cx, cy + sign * r)]
        while ok((cx, spoke[-1][1] + sign * 0.25)):
            spoke.append((cx, spoke[-1][1] + sign * 0.25))
        lines.append(spoke)
    river = bp.dense(bp.RIVER, 0.5)
    lines += [offset(river, side * (bp.RIVER_WIDTH / 2 + BANK + 1.2)) for side in (1, -1)]
    runs = []
    for i, line in enumerate(lines):
        run = []
        for q in line + [None]:  # promenades also keep off the pond's arcs
            if q is not None and ok(q, 0.8 if i > 3 else 0.3) and (i <= 3 or math.dist(q, (cx, cy)) > r + 0.5):
                run.append(q)
                continue
            if len(run) > 16:  # shorter pieces, cut off between two lands, would read as debris
                runs.append(run)
            run = []
    gravel = m.textured("ground_path", "gravel_floor", 2.0, "#d6d0c4", "OVERLAY", 0.3)
    ink.flat("ground_paths", [q for line in runs for q in ink.ribbon(line, PATH_W)], lambda u, v: (u, v, 0.004), gravel)
    return runs


def field_object(name, f, poly, z, material):
    """A field's flat piece, origin at the field's centre and turned with the road,
    so the setts' pattern runs along the road on every field."""
    c, s = math.cos(f["heading"]), math.sin(f["heading"])
    local = [((x - f["x"]) * c + (y - f["y"]) * s, -(x - f["x"]) * s + (y - f["y"]) * c) for x, y in poly]
    obj = ink.flat(name, [local], lambda u, v: (u, v, z), material)
    obj.location, obj.rotation_euler = (f["x"], f["y"], 0), (0, 0, f["heading"])
    return obj


def road(L):
    """Dark joints under all 40 fields, granite setts on each, and on the special
    fields a coloured inlay with a cream icon (and its shadow), upright for the TV;
    the Bank and the Rathaus also carry their names."""
    joint = m.textured("road_joint", "stone_wall", 1.0, "#4a443d", "OVERLAY", 0.4)
    setts = m.textured("road_setts", "cobblestone_square", 2.4, "#cfc6b6", "OVERLAY", 0.6)
    cream = m.plain("road_ink", INK)
    ink.flat("road_joints", [f["outline"] for f in L.fields], lambda u, v: (u, v, 0.006), joint)
    for f in L.fields:
        kind = FIELD_KINDS[f["id"]]
        field_object(f"road_field_{f['id']}", f, inset(f["outline"], SEAM), 0.01, setts)
        if kind == ".":
            continue
        tint = KINDS[kind]
        field_object(f"road_inlay_{f['id']}", f, inset(f["outline"], SEAM + INLAY), 0.014,
                     m.textured(f"road_inlay_{f['id']}", "cobblestone_square", 2.4, tint, "OVERLAY", 0.45))
        shade = m.plain(f"road_ink_shadow_{f['id']}", "#" + "".join(f"{int(tint[i:i + 2], 16) * 2 // 5:02x}" for i in (1, 3, 5)))
        size, dy = (ICON * 0.85, 1.25) if kind in TEXT else (ICON, 0.0)
        for mat, (ox, oy, z) in ((shade, (0.07, -0.09, 0.018)), (cream, (0, 0, 0.022))):
            ink.flat(f"road_icon_{f['id']}_{z}", ink.icon(ICONS[kind], size), lambda u, v: (f["x"] + u + ox, f["y"] + dy + v + oy, z), mat)
            if kind in TEXT:
                ink.text(f"road_text_{f['id']}_{z}", TEXT[kind], lambda u, v: (f["x"] + u + ox, f["y"] - 1.2 + v + oy, z),
                         mat, 0.95, 3.1, weight=0.03)


def strip(bm, uv, line, w, h, closed, z0=0.0):
    """A kerb along `line`, `w` wide to its left, `h` high: top and both walls,
    no bottom. Top faces take the atlas at their own (x, y); walls take it at
    the kerb's middle, so they show the kerb's colour, not the ground's."""
    out = offset(line, w, closed)
    mid = offset(line, w / 2, closed)
    rows = [[bm.verts.new((x, y, z)) for x, y in pts] for pts, z in ((line, z0), (line, z0 + h), (out, z0 + h), (out, z0))]
    n = len(line)
    for i in range(n if closed else n - 1):
        j = (i + 1) % n
        for r in range(3):
            face = bm.faces.new((rows[r][i], rows[r][j], rows[r + 1][j], rows[r + 1][i]))
            for loop in face.loops:
                k = i if loop.vert in (rows[r][i], rows[r + 1][i]) else j
                loop[uv].uv = (loop.vert.co.x, loop.vert.co.y) if r == 1 else mid[k]
    if not closed:
        for k in (0, n - 1):
            face = bm.faces.new([rows[r][k] for r in range(4)][:: -1 if k else 1])
            for loop in face.loops:
                loop[uv].uv = mid[k]


def ground_parts(L):
    """Kerbs along both curved road edges (the bridges have parapets instead) and
    a low edging round each plot's land where it does not meet the road (the kerb
    is its edge there). UVs are board (x, y); the bake turns them into atlas UVs."""
    kerb = m.textured("ground_kerb", "stone_wall", 1.2, "#cbc6bc", "OVERLAY", 0.3)
    a, b = BRIDGES
    runs = [(L.edge(L.outer, a + 1, b), 1), (L.edge(L.outer, b + 1, a), 1), (L.edge(L.inner, a + 1, b), -1), (L.edge(L.inner, b + 1, a), -1)]

    def kerbs(bm):
        uv = bm.loops.layers.uv.new("atlas")
        for line, side in runs:
            strip(bm, uv, line if side > 0 else line[::-1], KERB_W, KERB_H, False)

    def edging(bm):
        uv = bm.loops.layers.uv.new("atlas")
        for p in L.plots:
            land = p["land"]
            back = land[-5:] + land[:1]  # off the road: the frontage's end, back to the far side, round to its start
            strip(bm, uv, back[::-1] if area(land) > 0 else back, EDGING_W, EDGING_H, False)  # the land on the right

    return [ink.solid("ground_kerbs", kerbs, kerb), ink.solid("ground_edging", edging, kerb)]
