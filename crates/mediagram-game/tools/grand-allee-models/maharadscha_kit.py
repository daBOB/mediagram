"""Maharadscha's own shapes, materials and layout constants
(the stages are built in `maharadscha.py`)."""

from functools import partial
import math
import materials as m
import parts as p


STOREY = 3.4

PLOT = (32, 14)

BAY = 2.2

TRIM = "#d9a27a"  # pink-red sandstone

def _sq(g, s):
    return g.op("MULTIPLY", s, s)

def _up(g):
    """1 on faces that look up: flat roofs and terraces."""
    sep = g.nt.nodes.new("ShaderNodeSeparateXYZ")
    g.nt.links.new(g.obj.node.outputs["Normal"], sep.inputs[0])
    return g.op("GREATER_THAN", sep.outputs["Z"], 0.5)

def _arch(g, xc, h, a, sill, spring):
    """1 inside a pointed (equilateral) arch of half-width `a`, open from
    `sill` up, its two arcs springing at `spring` (xc across, h up, metres)."""
    inside = g.op("MULTIPLY", g.op("LESS_THAN", g.op("ABSOLUTE", xc), a), g.op("GREATER_THAN", h, sill))
    rise = _sq(g, g.op("SUBTRACT", h, spring))
    arcs = g.op("MULTIPLY",
                g.op("LESS_THAN", g.op("ADD", _sq(g, g.op("SUBTRACT", xc, a)), rise), 4 * a * a),
                g.op("LESS_THAN", g.op("ADD", _sq(g, g.op("ADD", xc, a)), rise), 4 * a * a))
    return g.op("MULTIPLY", inside, g.op("MAXIMUM", g.op("LESS_THAN", h, spring), arcs))

def _cusped(g, xc, h, a, sill, spring):
    """A Mughal cusped arch: a smaller pointed arch ringed by round lobes, so
    the frame shows between them as points (cusps) round the head."""
    ai, r = 0.78 * a, 0.22 * a
    shape = g.op("MAXIMUM", _arch(g, xc, h, ai, sill, spring),
                 g.op("MULTIPLY", g.op("LESS_THAN", g.op("ABSOLUTE", xc), a), g.within(h, sill, spring)))
    for phi in (0.0, 0.38, 0.77):
        px, pz = ai - 2 * ai * math.cos(phi), spring + 2 * ai * math.sin(phi)
        d = g.op("ADD", _sq(g, g.op("SUBTRACT", g.op("ABSOLUTE", xc), -px)), _sq(g, g.op("SUBTRACT", h, pz)))
        shape = g.op("MAXIMUM", shape, g.op("LESS_THAN", d, r * r))
    return shape

def _finish(g, opening, body, shade, glow, roof, strength=0.08):
    """Openings dark with lamplight behind them; roofs in lime plaster."""
    up = _up(g)
    g.color(g.mix(up, g.mix(opening, body, shade), roof))
    g.nt.links.new(g.mix(g.op("MULTIPLY", opening, g.op("SUBTRACT", 1.0, up)), "#000000", glow), g.bsdf.inputs["Emission Color"])
    g.bsdf.inputs["Emission Strength"].default_value = strength
    return g.mat

def palace_wall(name, plaster="#f3e2c2", trim=TRIM, shade="#33241b", glow="#ffa552", roof="#dccbaa"):
    """Lime plaster over a sandstone plinth: a wide pointed arch per bay on the
    ground floor (the arcade), a framed pointed window per bay above, sandstone
    string courses at every floor. The pattern mirrors at x = 0, so the east
    and west halves match."""
    g = m.Graph(name)
    x, y, z = (g.xyz.outputs[k] for k in "XYZ")
    s = g.op("DIVIDE", g.op("ADD", g.op("ABSOLUTE", x), y), BAY)
    xc = g.op("MULTIPLY", g.op("SUBTRACT", g.op("FRACT", s), 0.5), BAY)
    h = g.op("MULTIPLY", g.op("FRACT", g.op("DIVIDE", z, STOREY)), STOREY)
    ground = g.op("LESS_THAN", z, STOREY)
    upper = g.op("SUBTRACT", 1.0, ground)
    opening = g.op("MAXIMUM", g.op("MULTIPLY", ground, _cusped(g, xc, h, 0.78, 0.35, 1.75)),
                   g.op("MULTIPLY", upper, _arch(g, xc, h, 0.42, 0.9, 2.1)))
    frame = g.op("MAXIMUM", g.op("MULTIPLY", ground, _arch(g, xc, h, 0.92, 0.25, 1.75)),
                 g.op("MULTIPLY", upper, _arch(g, xc, h, 0.56, 0.75, 2.1)))
    band = g.op("MAXIMUM", g.op("LESS_THAN", h, 0.18), g.op("LESS_THAN", z, 0.35))
    body = g.mix(g.op("MAXIMUM", band, frame), g.image("plastered_wall_04", 3.0, plaster, "OVERLAY", 0.5),
                 g.image("sandstone_blocks_05", 3.5, trim, "OVERLAY", 0.75))
    return _finish(g, opening, body, shade, glow, roof)

def portal_wall(name, plaster="#f3e2c2", trim=TRIM, shade="#33241b", glow="#ffa552", roof="#dccbaa"):
    """The entrance iwan, centred at x = 0: one great pointed arch in a
    sandstone frame and a rectangular sandstone border round it, a lit door
    deep inside."""
    g = m.Graph(name)
    x, z = g.xyz.outputs["X"], g.xyz.outputs["Z"]
    opening = _cusped(g, x, z, 1.35, 0.0, 4.2)
    frame = _arch(g, x, z, 1.7, 0.0, 4.2)
    outer = g.op("MULTIPLY", g.op("LESS_THAN", g.op("ABSOLUTE", x), 2.0), g.op("LESS_THAN", z, 7.7))
    inner = g.op("MULTIPLY", g.op("LESS_THAN", g.op("ABSOLUTE", x), 1.82), g.op("LESS_THAN", z, 7.52))
    border = g.op("MAXIMUM", frame, g.op("SUBTRACT", outer, inner))
    body = g.mix(border, g.image("plastered_wall_04", 3.0, plaster, "OVERLAY", 0.5), g.image("sandstone_blocks_05", 3.5, trim, "OVERLAY", 0.75))
    door = g.op("MULTIPLY", g.op("LESS_THAN", g.op("ABSOLUTE", x), 0.7), g.op("LESS_THAN", z, 2.7))
    return _finish(g, opening, body, g.mix(door, shade, "#7a4e2c"), glow, roof, 0.12)

def parapet(name, x0, y0, x1, y1, z, material, parent, height=0.7, thick=0.3, merlon=0.9):
    """A parapet wall round a flat roof, with a row of merlons along the front."""

    def build(bm):
        p.add_box(bm, x0, y0, z, x1, y0 + thick, z + height)
        p.add_box(bm, x0, y1 - thick, z, x1, y1, z + height)
        p.add_box(bm, x0, y0 + thick, z, x0 + thick, y1 - thick, z + height)
        p.add_box(bm, x1 - thick, y0 + thick, z, x1, y1 - thick, z + height)
        n = round((x1 - x0) / merlon)
        for i in range(n):
            cx = x0 + (i + 0.5) * (x1 - x0) / n
            p.add_box(bm, cx - 0.17, y0, z + height, cx + 0.17, y0 + thick, z + height + 0.4)

    return p.mesh_object(name, build, material, parent)

def domed(name, x, y, z, radius, dome, gold, parent, segments=12, drum=0.0, drum_mat=None):
    """An onion dome (on an optional drum) with a gilt finial; returns the finial's top."""
    if drum:
        cylinder(name + "_drum", x, y, z, z + drum, radius * 0.9, drum_mat, parent, segments=segments)
        z += drum
    p.onion_dome(name, x, y, z, radius, dome, parent, segments).data.shade_smooth()
    tip = z + 1.7 * radius
    p.post(name + "_finial", x, y, tip - 0.1, tip + 0.5 + 0.2 * radius, 0.06 + 0.02 * radius, gold, parent, segments=6)
    return tip + 0.5 + 0.2 * radius

def chhatri(name, x, y, z, size, stone, dome, gold, parent):
    """A domed kiosk on four pillars, the Rajput roof pavilion."""
    half, h = size / 2, size * 0.9

    def body(bm):
        p.add_box(bm, x - half - 0.15, y - half - 0.15, z, x + half + 0.15, y + half + 0.15, z + 0.3)
        for sx in (-1, 1):
            for sy in (-1, 1):
                cx, cy = x + sx * (half - 0.12), y + sy * (half - 0.12)
                p.add_box(bm, cx - 0.12, cy - 0.12, z + 0.3, cx + 0.12, cy + 0.12, z + 0.3 + h)
        p.add_box(bm, x - half - 0.3, y - half - 0.3, z + 0.3 + h, x + half + 0.3, y + half + 0.3, z + 0.48 + h)

    p.mesh_object(name, body, stone, parent)
    domed(name + "_dome", x, y, z + 0.48 + h, half * 0.95, dome, gold, parent)

def palm(name, x, y, height, bark, leaf, parent, fronds=7, reach=1.9):
    """A slim trunk under a crown of drooping fronds, each a thin folded leaf
    (a closed sliver of geometry, so it reads from any side, no cut-outs)."""
    p.post(name + "_trunk", x, y, 0, height + 0.1, 0.15, bark, parent, segments=6)

    def crown(bm):
        for i in range(fronds):
            a = 2 * math.pi * (i + 0.37 * (i % 3)) / fronds
            c, s = math.cos(a), math.sin(a)

            def at(r, dz, w=0.0, lift=0.012 * i):  # each frond a little higher, so no two lie in one plane
                return bm.verts.new((x + c * r - s * w, y + s * r + c * w, height + dz + lift))

            base, tip = at(0, 0), at(reach, -1.1)
            left, right = at(reach * 0.5, 0.3, 0.42), at(reach * 0.5, 0.3, -0.42)
            top, under = at(reach * 0.5, 0.42), at(reach * 0.5, 0.2)
            for spine in (top, under):
                for f in ((base, left, spine), (left, tip, spine), (tip, right, spine), (right, base, spine)):
                    bm.faces.new(f if spine is top else tuple(reversed(f)))

    p.mesh_object(name + "_crown", crown, leaf, parent)

def tent(name, x, y, w, d, z, cloth, pole, parent):
    """A shamiana: a cloth roof with a deep valance on four poles."""
    x0, y0, x1, y1 = x - w / 2, y - d / 2, x + w / 2, y + d / 2

    def poles(bm):
        for px in (x0 + 0.1, x1 - 0.1):
            for py in (y0 + 0.1, y1 - 0.1):
                p.add_box(bm, px - 0.06, py - 0.06, 0, px + 0.06, py + 0.06, z)

    def roof(bm):
        a, b, c, e = (bm.verts.new(q) for q in [(x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z)])
        apex = bm.verts.new((x, y, z + 0.9))
        for f in ((a, b, apex), (b, c, apex), (c, e, apex), (e, a, apex), (a, e, c, b)):
            bm.faces.new(f)
        for lo, hi in (((x0, y0), (x1, y0 + 0.04)), ((x0, y1 - 0.04), (x1, y1)),
                       ((x0, y0 + 0.04), (x0 + 0.04, y1 - 0.04)), ((x1 - 0.04, y0 + 0.04), (x1, y1 - 0.04))):
            p.add_box(bm, lo[0], lo[1], z - 0.4, hi[0], hi[1], z)

    p.mesh_object(name.removeprefix("tint_") + "_poles", poles, pole, parent)
    return p.mesh_object(name, roof, cloth, parent)

def fountain(name, x, y, z, height, spray, parent, subdiv=1):
    """A jet as a slim column of spray over a splash ring."""
    p.blob(name + "_spray", (x, y, z + height * 0.55), height * 0.6, spray, parent, squash=(0.28, 0.28, 1.0), subdiv=subdiv)
    p.blob(name + "_splash", (x, y, z), 0.45, spray, parent, squash=(1.0, 1.0, 0.3))


# the shared cylinder at this hotel's own segment count
cylinder = partial(p.cylinder, segments=16)
