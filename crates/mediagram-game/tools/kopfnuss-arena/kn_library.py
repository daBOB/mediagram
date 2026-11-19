"""The Kopfnuss stages' materials by name: which scan or shader each surface is made of, and in what colour."""
import bpy

from kn_materials import ANSWERS, RAINBOW, candy, flat, glitter, lacquer, metal, neon, scan, sky

BOW = ("#ff6fa5", "#ffb347", "#ffe45c", "#6fe0a8", "#6fb8ff")
GLOWS = (("gold", "#ffd34e", 14), ("pink", "#ff4e9a", 16), ("mint", "#56e3cf", 12), ("blue", "#52a8ff", 14),
         ("warm", "#ffe2b0", 22))


def build():
    """(Re)build the library. Old KN_ materials are dropped first so a rebuild never piles up copies; that is
    also why the builder always rebuilds every set."""
    for mat in [m for m in bpy.data.materials if m.name.startswith("KN_")]:
        bpy.data.materials.remove(mat)
    velvet = {"rough": (.75, 1.0), "Sheen_Weight": 1.0, "Sheen_Roughness": .35}
    M = dict(
        marble=scan("Marble", "marble_01", 3.0, rough=(.02, .35), Coat_Weight=.5, Coat_Roughness=.03),
        walnut=scan("WalnutWood", "walnut_veneer_02", 2.4, tint="#c08a5c", rough=(.15, .5), Coat_Weight=.35),
        walnutDark=scan("WalnutDark", "walnut_veneer_02", 2.4, tint="#5a3a26", rough=(.2, .55), Coat_Weight=.3),
        velvetRed=scan("VelvetRed", "velour_velvet", 1.6, tint="#e0566a", **velvet),
        velvetNight=scan("VelvetNight", "velour_velvet", 1.6, tint="#4458d8", **velvet),
        ivory=lacquer("Ivory", "#f4ead6", .28, .5),
        lilac=lacquer("LilacWall", "#cfc2e6", .5, .15),
        cream=lacquer("Cream", "#fff3d7", .35, .4),
        ink=lacquer("Ink", "#1b2440", .4, .3),
        stageFloor=lacquer("StageFloor", "#070b1c", .1, 1.0),
        candyFloor=lacquer("CandyFloor", "#ffd9e6", .12, 1.0),
        gold=metal("Gold", "#ffb840", .16),
        brass=metal("Brass", "#d9a441", .32),
        goldGlitter=glitter("GoldGlitter", "#ffad33"),
        # a little light of their own, or the puffs' shadow sides go grey under the pink sky
        cloud=flat("Cloud", "#ffffff", Roughness=1.0, Sheen_Weight=.6, Emission_Color=(1, .95, .97, 1), Emission_Strength=.35),
        candySky=sky("CandySky", ((0, "#ffd9c2"), (.42, "#f9b8dd"), (.72, "#b7b9ff"), (1, "#7f97ff")), low=-34.0, high=40.0),
        nightSky=sky("NightSky", ((0, "#1d2b6b"), (.4, "#0e1440"), (1, "#05061a")), high=14.0),
    )
    for key, color, strength in GLOWS:
        M[key + "Glow"] = neon(key.title() + "Glow", color, strength)
    for i, color in enumerate(ANSWERS):
        M[f"answer{i}"] = lacquer(f"Answer_{i}", color, .25, .8)
        M[f"answerGlow{i}"] = neon(f"AnswerGlow_{i}", color, 12)
    for i, color in enumerate(RAINBOW):
        M[f"tier{i}"] = candy(f"Tier_{i + 1}", color)
    for i, color in enumerate(BOW):
        M[f"bow{i}"] = neon(f"Rainbow_{i}", color, 2.4)
    return M
