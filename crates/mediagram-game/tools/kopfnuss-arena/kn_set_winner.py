"""The winner's stage (Fact): a gold podium on walnut boards before a red velvet curtain, in one warm spotlight."""
import math

from kn_common import circle_points, cylinder, empty, ring, tube
from kn_lighting import light
from kn_dressing import beam, bulbs, curtain

WORLD = {"ambient": "#3a0f18", "strength": .1, "reflections": .15}
PODIUM = (0, -.4)
BURST = (0, 2.8, 1.0)  # the gold sunburst stands behind the podium


def build(root, geo, fxc, lights, M):
    cylinder("WinnerFloor", (0, 0, -.25), 18, .5, M["walnut"], geo, root, 96, tex=.6)
    curtain("WinnerCurtain", (-18, 7.6), (18, 7.6), -.1, 14, M["velvetRed"], geo, root, 22, .42, .45)
    x, y = PODIUM
    cylinder("WinnerPodiumBase", (x, y, .2), 2.15, .5, M["walnutDark"], geo, root, 64)
    ring("WinnerPodiumBand", (x, y, .45), 2.15, .06, M["brass"], geo, root, count=64)
    cylinder("WinnerPodium", (x, y, .76), 1.65, .68, M["gold"], geo, root, 64)
    cylinder("WinnerPodiumTop", (x, y, 1.17), 1.85, .16, M["cream"], geo, root, 64)
    empty("WinnerSpot", geo, root, at=(x, y, 1.25))

    tips = []
    for j in range(13):
        a = math.pi * (j + .5) / 13
        reach = 5.6 if j % 2 == 0 else 4.5
        inner = (BURST[0] + math.cos(a) * 2.5, BURST[1], BURST[2] + math.sin(a) * 2.5)
        tip = (BURST[0] + math.cos(a) * reach, BURST[1], BURST[2] + math.sin(a) * reach)
        tube("BurstRay", [inner, tip], .1, M["gold"], geo, root)
        tips.append((tip[0], tip[1] - .05, tip[2]))
    bulbs("BurstBulbs", tips, .17, M["warmGlow"], geo, root)
    ring("BurstArc", BURST, 2.5, .14, M["brass"], geo, root, start=0, end=math.pi, count=48, vertical=True)
    foot = circle_points((0, 0, .1), 8.2, math.radians(205), math.radians(335), 28)[0]
    bulbs("Footlights", foot, .16, M["warmGlow"], geo, root)
    tube("FootlightRail", foot, .06, M["brass"], geo, root, tex=.6)

    for side in (-1, 1):
        beam("WinnerBeam", (side * 6.5, -4, 14), (x, y, 1.2), 2.3, "#fff0c8", fxc, root)
    light("LGT_Winner_Key", lights, (0, -7, 15), "#ffeccc", 7500, .8, (x, y, 1.0), "SPOT", 30)
    light("LGT_Winner_Curtain", lights, (0, -3, 2.5), "#ff6a6a", 650, 9, (0, 7, 6))
    light("LGT_Winner_Rim", lights, (-9, 4, 6), "#ffb347", 900, 4, (x, y, 1.5))
