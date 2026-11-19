"""The quiz studio (Question, Reveal): a walnut stage on a black gloss floor, lit in the four answer colours."""
import math
import random

from kn_common import box, circle_points, cylinder, empty, fx, ring, tube
from kn_lighting import light
from kn_dressing import beam, bulbs, cyclorama, fx_quad, fx_ring
from kn_materials import ANSWERS

WORLD = {"ambient": "#101a3a", "strength": .12, "reflections": .1}
RINGS = ((5.2, .15, "pink", "#ff4e9a"), (10.2, .02, "gold", "#ffd34e"), (13.6, .02, "mint", "#56e3cf"))


def _stage(root, geo, fxc, M):
    cylinder("StudioFloor", (0, 0, -.25), 19.4, .5, M["stageFloor"], geo, root, 96, tex=.5)
    cylinder("InnerStage", (0, 0, .04), 9.5, .2, M["walnutDark"], geo, root, 96, tex=.7)
    ring("StageRim", (0, 0, .14), 9.5, .07, M["brass"], geo, root, count=96)
    for radius, z, glow, color in RINGS:
        ring("FloorLightRing", (0, 0, z), radius, .05, M[glow + "Glow"], geo, root, count=128)
        fx_ring("FloorRingGlow", z + .04, radius, .7, "ring", color, fxc, root)
    cyclorama("StudioCyc", 18.5, math.radians(8), math.radians(172), 14.1, M["nightSky"], geo, root, z=-.1)
    rng = random.Random(7)
    stars = [(math.cos(a) * 18.49, math.sin(a) * 18.49, z) for a, z in
             ((math.radians(rng.uniform(14, 166)), rng.uniform(.8, 12)) for _ in range(170))]
    bulbs("StarCloth", stars, .035, M["warmGlow"], geo, root, tex=.5)


def _sides(root, geo, fxc, M):
    for side in (-1, 1):
        for row in range(3):
            x, y, h = side * (9.9 + row * .95), 3.0 + row * .55, .6 + row * .3
            box("AudienceTerrace", (x, y, h / 2 - .05 - row * .01), (2.3, 5.0, h + .1 + row * .02), M["walnutDark"], geo, root, .04, tex=.5)
            box("TerraceStepLight", (x - side * 1.17, y, h - .05), (.04, 4.8, .04), M["warmGlow"], geo, root, 0, tex=.5)
            for k in range(5):
                box("AudienceSeat", (x + side * .2, y - 2 + k, h + .3), (.95, .72, .62), M["velvetNight"], geo, root, .14, tex=.4)
        for i in range(5):
            x = side * (7.5 + i * 1.05)
            name, color = ("pink", "#ff4e9a") if i % 2 else ("blue", "#52a8ff")
            cylinder("LightColumn", (x, 5.6, 2.25), .09, 4.1, M[name + "Glow"], geo, root, 16)
            for z in (.12, 4.36):
                cylinder("ColumnCap", (x, 5.6, z), .2, .24, M["brass"], geo, root, 20)
            fx_quad("ColumnHaze", (x, 5.45, 2.25), 1.0, 4.4, "column", color, fxc, root, upright=True)


def _arch(root, geo, fxc, M):
    def arc(radius, y, count=72, inset=0.0):
        return circle_points((0, y, .3), radius, inset, math.pi - inset, count, vertical=True)[0]
    tube("ShowArch", arc(9.1, 6.3), .3, M["brass"], geo, root, tex=.6)
    tube("ShowArchNeon", arc(8.8, 6.15), .06, M["pinkGlow"], geo, root)  # set into the arch's inner edge
    bulbs("ShowArchBulbs", arc(9.1, 5.96, 46, .04), .1, M["warmGlow"], geo, root)
    fx(tube("ShowArchChase", arc(9.1, 5.86), .22, None, fxc, root), "chase", "#ffe2b0")


def _cast(root, geo, fxc, M):
    for i in range(6):
        pad = empty(f"PlayerPad_{i}", geo, root, at=((i - 2.5) * 2.05, -3.0 - abs(i - 2.5) * .18, 0))
        name, color = ("gold", "#ffd34e") if i % 2 else ("mint", "#56e3cf")
        cylinder("PlayerDisc", (0, 0, .09), .76, .24, M["ink"], geo, pad, 40)
        ring("PlayerRing", (0, 0, .21), .78, .045, M[name + "Glow"], geo, pad, count=48)
        fx_quad("PadGlow", (0, 0, .25), 3.2, 3.2, "pad", color, fxc, pad)
        empty(f"PlayerSpot_{i}", geo, pad, at=(0, 0, .21))
    cylinder("HostPodium", (0, 2.3, .3), 1.25, .6, M["walnut"], geo, root)
    ring("HostPodiumBand", (0, 2.3, .2), 1.26, .05, M["brass"], geo, root, count=48)
    ring("HostPodiumRing", (0, 2.3, .61), 1.2, .05, M["goldGlow"], geo, root, count=48)
    reveal = empty("Reveal_Stage", fxc)
    for i in range(4):
        x = (i - 1.5) * 3.2
        podium = empty(f"AnswerPodium_{i}", geo, root, at=(x, -.15, 0))
        box("PodiumBase", (0, 0, .2), (2.75, 2.0, .2), M["brass"], geo, podium, .05)
        box("PodiumBody", (0, -.1, .62), (2.5, 1.78, .66), M["walnut"], geo, podium, .08)
        box("PodiumFront", (0, -1.0, .58), (2.34, .06, .52), M[f"answer{i}"], geo, podium, .02)
        box("PodiumLight", (0, -1.0, .87), (2.3, .08, .06), M[f"answerGlow{i}"], geo, podium, 0)
        box("PodiumTop", (0, -.1, .98), (2.62, 1.9, .07), M["stageFloor"], geo, podium, .03)
        halo = circle_points((x, .15, 1.15), 1.22, 0, math.pi, 48, vertical=True)[0]
        fx(tube("RevealHalo", halo, .07, None, fxc, reveal), "chase", "#ffd34e")


def build(root, geo, fxc, lights, M):
    _stage(root, geo, fxc, M)
    _sides(root, geo, fxc, M)
    _arch(root, geo, fxc, M)
    _cast(root, geo, fxc, M)
    for (sx, tx), color in zip(((-9, -4), (-4, -1.5), (4, 1.5), (9, 4)), (ANSWERS[3], ANSWERS[0], ANSWERS[2], ANSWERS[1])):
        beam("StageBeam", (sx, 5, 13), (tx, -1.5, 0), 2.4, color, fxc, root)
    light("LGT_Quiz_Key", lights, (0, -9, 13), "#ffe9c8", 2400, 1.5, (0, -1, 0), "SPOT", 62)
    light("LGT_Quiz_Pink", lights, (-11, 2, 8), "#ff5a9f", 1100, 6, (0, 0, 1))
    light("LGT_Quiz_Blue", lights, (11, 2, 9), "#5aa8ff", 1100, 6, (0, 0, 1))
