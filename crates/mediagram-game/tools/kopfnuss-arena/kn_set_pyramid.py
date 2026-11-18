"""The Gipfelsturm (Climb): ten candy tiers under a pastel sky, with real treads the climbers stand on."""
import math
import random

import bpy

from kn_common import add_sphere, ball, box, cylinder, empty, place, prism, pydata, ring
from kn_lighting import light
from kn_dressing import fx_quad

WORLD = {"ambient": "#ffe6f0", "strength": .4, "reflections": .5}
TIERS, RISE, TREAD, BACK = 10, .62, .85, 5.6


def _label(name, body, at, size, mat, coll, parent=None):
    """A tread's number as a mesh, facing the front. Set dressing only: all game text stays HTML."""
    font = bpy.data.curves.new(name + "Font", "FONT")
    font.body = body
    font.align_x = "CENTER"
    font.size = size
    font.extrude = .012
    temp = bpy.data.objects.new(name + "Font", font)
    mesh = bpy.data.meshes.new_from_object(temp)
    bpy.data.objects.remove(temp)
    bpy.data.curves.remove(font)
    obj = place(name, mesh, mat, coll, parent, at)
    obj.rotation_euler.x = math.pi / 2
    return obj


def _cloud(at, size, seed, geo, root, M, tex=.3, segments=16):
    rng = random.Random(seed)
    verts, faces = [], []
    for _ in range(7):
        offset = [rng.uniform(-1.6, 1.6) * size, rng.uniform(-.5, .5) * size, rng.uniform(-.2, .4) * size]
        radius = size * rng.uniform(.5, 1.0)
        add_sphere(verts, faces, offset, (radius, radius, radius * .72), segments)  # puffs sit a little flat
    return place("SugarCloud", pydata("SugarCloud", verts, faces), M["cloud"], geo, root, at, smooth=True, tex=tex)


def _pyramid(root, geo, M):
    for i in range(TIERS):
        width, front, top = 15 - i * 1.3, -4.6 + i * TREAD, (i + 1) * RISE
        depth = BACK + i * .01 - front
        # each slab reaches a little into the one below, so no two faces share a plane
        box(f"PyramidTier_{i + 1}", (0, front + depth / 2, top - (RISE + .05) / 2), (width, depth, RISE + .05),
            M[f"tier{i}"], geo, root, .06)
        box("PyramidNosing", (0, front + .02, top - .04), (width + .04, .12, .1), M["gold"], geo, root, .03)
        _label(f"ClimbNumber_{i + 1}", str(i + 1), (-width / 2 + .9, front - .012, top - RISE / 2 - .17), .42,
              M["cream"], geo, root)
        last = i == TIERS - 1
        x = 0 if last else (1 if i % 2 else -1) * min(2.3, width * .23)
        spot = empty(f"ClimbSpot_{i + 1}", geo, root, at=(x, front + (1.0 if last else TREAD / 2), top))
        spot["step"] = i + 1
    cylinder("StartPad", (0, -6.7, .04), 1.7, .14, M["cream"], geo, root, 48)
    ring("StartPadRing", (0, -6.7, .11), 1.7, .05, M["gold"], geo, root, count=48)
    empty("ClimbSpot_0", geo, root, at=(0, -6.7, .11))["step"] = 0
    peak = TIERS * RISE
    cylinder("StarPole", (0, 4.9, peak + 1.6), .06, 3.3, M["brass"], geo, root, 12)  # up into the star, over a climber's head
    star = [(math.cos(a) * r, math.sin(a) * r + peak + 3.3) for a, r in
            ((math.pi / 2 + j * math.pi / 5, 1.0 if j % 2 == 0 else .46) for j in range(10))]
    prism("SummitStar", star, 4.78, 5.02, M["gold"], geo, root)
    return peak


def build(root, geo, fxc, lights, M):
    # The camera looks down on the climb, so the backdrop is sky below as well as above: an island over the clouds.
    # The dome only lights the bake and shows in its reflections; the TV paints the sky itself, as a gradient.
    ball("SkyDome", (0, 0, 0), (75, 75, 75), M["candySky"], lights, None, segments=48)
    cylinder("CandyIsland", (0, 0, -.5), 12.6, 1.0, M["candyFloor"], geo, root, 96, tex=.6)
    cylinder("CandyIslandBoard", (0, 0, -1.25), 13.4, .6, M["cream"], geo, root, 96, tex=.4)
    ring("CandyIslandRim", (0, 0, 0), 12.6, .09, M["gold"], geo, root, count=96, tex=.6)
    peak = _pyramid(root, geo, M)
    fx_quad("StarHalo", (0, 5.1, peak + 3.3), 5.0, 5.0, "halo", "#ffd34e", fxc, root, upright=True)
    for k in range(5):
        ring("Rainbow", (0, 7.4, 0), 8.6 + k * .46, .23, M[f"bow{k}"], geo, root, tex=.6,
             start=0, end=math.pi, count=72, vertical=True)
    rng = random.Random(11)
    for k in range(11):  # a wreath of cloud the island rises out of
        a = math.radians(k * 360 / 11 + 12)
        reach = rng.uniform(15.0, 17.5)
        _cloud((math.cos(a) * reach, math.sin(a) * reach, rng.uniform(-2.2, -1.2)), rng.uniform(2.3, 3.2), 20 + k, geo, root, M)
    for k in range(12):  # and the cloud sea behind it, falling away below
        at = (rng.uniform(-48, 48), rng.uniform(20, 70), rng.uniform(-14, -3))
        _cloud(at, rng.uniform(3.5, 6.0), 40 + k, geo, root, M, tex=.1, segments=10)  # far and soft: few texels, few faces
    for side in (-1, 1):
        _cloud((side * 9.6, 7.5, .2), 1.5, 60 + side, geo, root, M)
    for (x, y, r), tier in zip(((-10.5, -3, 1.2), (-11.2, 2, .9), (10.5, -1.5, 1.1), (11.3, 3.5, 1.3),
                                (-8.6, 7.6, 1.0), (8.3, 8, .9)), (2, 5, 7, 0, 4, 8)):
        ball("Gumdrop", (x, y, 0), (r, r, r * .95), M[f"tier{tier}"], geo, root, tex=.7)
    for (x, y, h), tier in zip(((-10.6, 5, 4.2), (10.8, 5.6, 3.6)), (9, 1)):
        cylinder("LollipopStick", (x, y, h / 2), .09, h, M["cream"], geo, root, 12)
        head = cylinder("LollipopHead", (x, y, h + .7), 1.15, .32, M[f"tier{tier}"], geo, root, 40)
        head.rotation_euler.x = math.pi / 2
        for radius in (.75, .38):
            ring("LollipopSwirl", (x, y - .17, h + .7), radius, .07, M["cream"], geo, root, vertical=True, count=32)
    light("LGT_Candy_Sun", lights, (9, -16, 20), "#fff2d8", 7000, 14, (0, 1, 3))
    light("LGT_Candy_Fill", lights, (-14, -6, 10), "#ffb6dc", 1800, 12, (0, 1, 3))
