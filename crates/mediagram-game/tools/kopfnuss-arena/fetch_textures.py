"""Download the CC0 Poly Haven sources the Kopfnuss stages are built from.

Run with plain Python: python3 fetch_textures.py
Files land in ~/.cache/kopfnuss-arena (out of git, re-fetchable).
"""
import json
import os
import urllib.request

CACHE = os.path.expanduser("~/.cache/kopfnuss-arena")
AGENT = {"User-Agent": "kopfnuss-arena-build/1.0"}

# id -> (resolution, maps). Texture map names are Poly Haven's own.
TEXTURES = {
    "marble_01": ("2k", ("Diffuse", "Rough")),
    "walnut_veneer_02": ("2k", ("Diffuse", "Rough")),
    "velour_velvet": ("1k", ("Diffuse", "Rough")),
}
HDRIS = {"wooden_studio_05": "2k"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=AGENT), timeout=60) as response:
        return response.read()


def fetch(asset, name, entry):
    path = os.path.join(CACHE, f"{asset}_{name}.{entry['url'].rsplit('.', 1)[1]}")
    if not os.path.exists(path) or os.path.getsize(path) != entry["size"]:  # a cut-off download is fetched again
        data = get(entry["url"])
        if len(data) != entry["size"]:
            raise SystemExit(f"{asset} {name}: got {len(data)} bytes, expected {entry['size']}")
        with open(path, "wb") as out:
            out.write(data)
    print(os.path.basename(path), os.path.getsize(path))


def main():
    os.makedirs(CACHE, exist_ok=True)
    for asset, (res, maps) in TEXTURES.items():
        files = json.loads(get(f"https://api.polyhaven.com/files/{asset}"))
        for name in maps:
            fetch(asset, name, files[name][res]["jpg"])
    for asset, res in HDRIS.items():
        files = json.loads(get(f"https://api.polyhaven.com/files/{asset}"))
        fetch(asset, "hdri", files["hdri"][res]["hdr"])


if __name__ == "__main__":
    main()
