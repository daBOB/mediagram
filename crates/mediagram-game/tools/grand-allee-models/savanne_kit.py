"""Savanne's own shapes, materials and layout constants
(the stages are built in `savanne.py`)."""

from functools import partial
import math
import random
import bmesh
from mathutils import Matrix, Vector
import materials as m
import parts as p


PLOT = (32, 14)

HUT = 1.8  # wall radius of a rondavel

def thatch(name, light="#c4a262", dark="#97773f"):
    """Straw: courses as horizontal bands and fine streaks running down the
    slope (noise stretched along z), no texture needed."""
    g = m.Graph(name)
    wave = g.nt.nodes.new("ShaderNodeTexWave")
    wave.wave_type, wave.bands_direction = "BANDS", "Z"
    wave.inputs["Scale"].default_value = 2.6
    wave.inputs["Distortion"].default_value = 7.0
    wave.inputs["Detail"].default_value = 3.0
    g.nt.links.new(g.obj, wave.inputs["Vector"])
    stretch = g.nt.nodes.new("ShaderNodeMapping")
    stretch.inputs["Scale"].default_value = (14.0, 14.0, 1.2)
    g.nt.links.new(g.obj, stretch.inputs["Vector"])
    streaks = g.nt.nodes.new("ShaderNodeTexNoise")
    streaks.inputs["Scale"].default_value = 1.0
    g.nt.links.new(stretch.outputs["Vector"], streaks.inputs["Vector"])
    straw = g.mix(g.op("ADD", g.op("MULTIPLY", wave.outputs["Fac"], 0.6), 0.3), dark, light)
    return g.color(g.mix(0.6, straw, streaks.outputs["Color"], "OVERLAY"))

def earth_wall(name, tint="#cc9c5e", base="#7e4a2c"):
    """Rendered earth walls with a darker red-earth band at the foot."""
    g = m.Graph(name)
    body = g.image("plastered_wall_04", 3.0, tint, "OVERLAY", 0.55)
    return g.color(g.mix(g.op("LESS_THAN", g.xyz.outputs["Z"], 1.0), body, base))

def strut(bm, a, b, r, segments=6):
    """A round timber between two points (branches, braces)."""
    a, b = Vector(a), Vector(b)
    axis = b - a
    rot = axis.to_track_quat("Z", "Y").to_matrix().to_4x4()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=segments, radius1=r, radius2=r * 0.7, depth=axis.length,
                          matrix=Matrix.Translation((a + b) / 2) @ rot)

def thatch_hip(name, x0, y0, x1, y1, z, rise, material, parent, overhang=1.0, thick=0.4):
    """A hip roof of thatch with a thick, rounded-off eave under its rim."""
    p.hip_roof(name, x0, y0, x1, y1, z, rise, material, parent, overhang=overhang)
    o, i = overhang, overhang - thick

    def eave(bm):
        top = [bm.verts.new(q) for q in ((x0 - o, y0 - o, z), (x1 + o, y0 - o, z), (x1 + o, y1 + o, z), (x0 - o, y1 + o, z))]
        low = [bm.verts.new(q) for q in ((x0 - i, y0 - i, z - thick), (x1 + i, y0 - i, z - thick),
                                         (x1 + i, y1 + i, z - thick), (x0 - i, y1 + i, z - thick))]
        for k in range(4):
            bm.faces.new((top[k], low[k], low[(k + 1) % 4], top[(k + 1) % 4]))
        bm.faces.new(low[::-1])

    p.mesh_object(name + "_eave", eave, material, parent)

def rondavel(name, x, y, walls, roof, stone, door, parent):
    """A round hut: a stone footing, earth walls, a thatched cone with a
    thick eave and a finial, a door to the road."""
    cylinder(name + "_footing", x, y, 0, 0.3, HUT + 0.25, stone, parent)
    cylinder(name + "_walls", x, y, 0.3, 2.6, HUT, walls, parent)
    cylinder(name + "_eave", x, y, 2.25, 2.62, HUT + 0.35, roof, parent, r1=HUT + 0.75)
    p.cone_roof(name + "_roof", x, y, 2.6, HUT + 0.75, 2.7, roof, parent)
    p.post(name + "_finial", x, y, 5.2, 5.75, 0.07, door, parent, segments=6)
    p.box(name + "_door", (x - 0.45, y - HUT - 0.06, 0.3), (x + 0.45, y - HUT + 0.3, 2.15), door, parent)

def acacia(name, x, y, height, spread, bark, leaves, parent, seed=0):
    """An umbrella thorn: a leaning trunk forking into bare branches under
    a few wide, flat crowns."""
    rnd = random.Random(seed)
    fork = Vector((x + rnd.uniform(-0.3, 0.3), y + rnd.uniform(-0.3, 0.3), height * 0.45))
    pads = []
    for k in range(3):
        a = 2 * math.pi * (k / 3 + rnd.uniform(-0.08, 0.08))
        d = spread * (0.45 if k else 0.15)
        pads.append(Vector((x + d * math.cos(a), y + d * math.sin(a), height * (0.92 + 0.08 * (k == 0)))))

    def wood(bm):
        strut(bm, (x, y, 0), fork, 0.2)
        for pad in pads:
            strut(bm, fork, pad - Vector((0, 0, 0.25)), 0.11)

    p.mesh_object(name + "_wood", wood, bark, parent)
    for k, pad in enumerate(pads):
        r = spread * (0.62 if k == 0 else 0.48)
        p.blob(f"{name}_crown_{k}", tuple(pad), r, leaves, parent, squash=(1.0, 0.85, 0.22), subdiv=2)

def grass_tufts(name, material, parent, keep_out, count=72, seed=3):
    """Dry grass clumps, each a fan of three leaning blades, scattered over
    the plot clear of every footprint the later stages build on
    (`keep_out`: rectangles and circles)."""
    rnd = random.Random(seed)

    def free(px, py):
        for zone in keep_out:
            if len(zone) == 4 and zone[0] <= px <= zone[2] and zone[1] <= py <= zone[3]:
                return False
            if len(zone) == 3 and math.hypot(px - zone[0], py - zone[1]) < zone[2]:
                return False
        return True

    def build(bm):
        placed = 0
        while placed < count:
            px, py = rnd.uniform(-15.4, 15.4), rnd.uniform(-6.6, 6.6)
            if not free(px, py):
                continue
            h, turn = rnd.uniform(0.5, 0.9), rnd.uniform(0, 2 * math.pi)
            for k in range(3):  # a blade: a thin three-sided pyramid leaning out
                a = turn + 2 * math.pi * k / 3
                tip = bm.verts.new((px + 0.3 * h * math.cos(a), py + 0.3 * h * math.sin(a), h))
                base = [bm.verts.new((px + 0.09 * math.cos(a + b), py + 0.09 * math.sin(a + b), 0))
                        for b in (0, 2.1, 4.2)]
                bm.faces.new(base[::-1])
                for j in range(3):
                    bm.faces.new((base[j], base[(j + 1) % 3], tip))
            placed += 1

    return p.mesh_object(name, build, material, parent)

def water_hole(prefix, cx, cy, rx, ry, bank, water, rock, parent, seed=5):
    """An irregular basin: a sandy bank, matte water a little above it, and
    rocks round the rim."""
    rnd = random.Random(seed)
    n = 18
    wobble = [1 + rnd.uniform(-0.12, 0.1) for _ in range(n)]

    def ring(scale, top):
        def build(bm):
            pts = [(cx + rx * scale * w * math.cos(2 * math.pi * k / n), cy + ry * scale * w * math.sin(2 * math.pi * k / n))
                   for k, w in enumerate(wobble)]
            lo = [bm.verts.new((px, py, 0)) for px, py in pts]
            hi = [bm.verts.new((px, py, top)) for px, py in pts]
            bm.faces.new(hi)
            bm.faces.new(lo[::-1])
            for k in range(n):
                bm.faces.new((lo[k], lo[(k + 1) % n], hi[(k + 1) % n], hi[k]))
        return build

    p.mesh_object(prefix + "_bank", ring(1.18, 0.08), bank, parent)
    p.mesh_object(prefix + "_water", ring(1.0, 0.12), water, parent)
    for k, a in enumerate((0.3, 1.4, 2.3, 3.3, 4.4, 5.5)):
        w = wobble[int(a / (2 * math.pi) * n) % n] * 1.12
        r = rnd.uniform(0.35, 0.6)
        p.blob(f"{prefix}_rock_{k}", (cx + rx * w * math.cos(a), cy + ry * w * math.sin(a), r * 0.3), r, rock, parent,
               squash=(1.3, 1.0, 0.65))


# the shared cylinder at this hotel's own segment count
cylinder = partial(p.cylinder, segments=12)
