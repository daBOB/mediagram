#!/usr/bin/env python3
"""Compare the mediagram local index against TMDB: find series with missing episodes."""
import json, os, re, sqlite3, sys, time, urllib.request

DB = os.path.expanduser("~/.local/share/mediagram/library.db")
CFG = os.path.expanduser("~/.config/mediagram/config.toml")
CACHE_DIR = "/tmp/mg-tmdb-cache"
LANG = "de-DE"

def tmdb_key():
    for line in open(CFG):
        m = re.match(r'\s*tmdb_key\s*=\s*"([^"]+)"', line)
        if m:
            return m.group(1)
    raise SystemExit("no tmdb_key in config")

KEY = tmdb_key()
os.makedirs(CACHE_DIR, exist_ok=True)
USE_CACHE = "--no-cache" not in sys.argv

def api(path):
    slug = re.sub(r"[^A-Za-z0-9]", "_", path)
    fp = os.path.join(CACHE_DIR, slug + ".json")
    if USE_CACHE and os.path.exists(fp):
        with open(fp) as f:
            return json.load(f)
    url = f"https://api.themoviedb.org/3{path}?language={LANG}"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {KEY}"})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                data = json.load(r)
            break
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503):
                time.sleep(2 * (attempt + 1)); continue
            if e.code == 404:
                return None
            raise
        except Exception:
            time.sleep(2 * (attempt + 1)); continue
    else:
        raise SystemExit(f"failed: {path}")
    with open(fp, "w") as f:
        json.dump(data, f)
    time.sleep(0.15)
    return data

con = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
rows = con.execute(
    "select tmdb, show, season, episode, status, title from sets where kind='ep' and tmdb is not null"
).fetchall()

lib = {}  # tmdb -> {name, seasons: {season -> {ep_num: (status, title)}}}
for tmdb, show, season, ep, status, title in rows:
    s = lib.setdefault(tmdb, {"name": show, "seasons": {}})
    nums = []
    if ep not in (None, ""):
        t = ep.strip()
        if t.startswith("["):
            try:
                nums = [int(float(x)) for x in json.loads(t)]
            except Exception:
                nums = []
        else:
            try:
                nums = [int(float(t))]
            except ValueError:
                nums = []
    if not nums:
        nums = [None]
    seas = season if season is not None else 0
    for n in nums:
        s["seasons"].setdefault(seas, {})[n] = (status, title)

report = []
now = time.strftime("%Y-%m-%d")
for tmdb, info in sorted(lib.items(), key=lambda kv: kv[1]["name"] or ""):
    det = api(f"/tv/{tmdb}")
    if det is None:
        report.append((info["name"], tmdb, "SHOW NOT FOUND ON TMDB", [], []))
        continue
    name = det.get("name") or info["name"]
    tmdb_seasons = {sd["season_number"]: sd for sd in det.get("seasons") or []}
    missing_seasons, missing_eps = [], []
    check_seasons = set(info["seasons"].keys()) | {n for n in tmdb_seasons if n >= 1}
    for sn in sorted(check_seasons):
        have = info["seasons"].get(sn, {})
        sd = tmdb_seasons.get(sn)
        if sd is None:
            if have:
                missing_seasons.append(("EXTRA-season in lib not on TMDB", sn, sorted(map(str, have))))
            continue
        if not have and sn >= 1:
            continue  # whole-season absence is reported below in the season loop
        sdet = api(f"/tv/{tmdb}/season/{sn}")
        eps = (sdet or {}).get("episodes") or []
        tmdb_nums = set()
        for e in eps:
            n = e.get("episode_number")
            if n is None:
                continue
            tmdb_nums.add(int(n))
            if int(n) not in have:
                air = e.get("air_date") or ""
                # specials (season 0) are optional; future episodes are not "missing"
                if sn == 0:
                    missing_eps.append((sn, n, e.get("name"), air, "special"))
                elif air and air > now:
                    missing_eps.append((sn, n, e.get("name"), air, "unaired"))
                else:
                    missing_eps.append((sn, n, e.get("name"), air, "MISSING"))
        # episodes in lib but not on TMDB
        extra = sorted((str(n) for n in have if n is not None and n not in tmdb_nums), key=lambda x: (len(x), x))
        if extra:
            missing_eps.append((sn, "EXTRA", f"in library but not on TMDB: {extra}", "", "extra"))
    # whole seasons on TMDB (>=1) entirely absent from lib
    lib_seasons = set(info["seasons"].keys())
    for sn, sd in sorted(tmdb_seasons.items()):
        if sn >= 1 and sn not in lib_seasons and sn not in {0}:
            air = sd.get("air_date") or ""
            tag = "unaired-season" if (air and air > now) else "MISSING-SEASON"
            missing_seasons.append((tag, sn, f"{sd.get('episode_count','?')} eps, {sd.get('name','')} ({air})"))
    report.append((name, tmdb, None, missing_seasons, missing_eps))

real_missing = 0
print("=" * 100)
for name, tmdb, err, seas, eps in report:
    hard_eps = [e for e in eps if e[4] == "MISSING"]
    hard_seas = [s for s in seas if s[0].startswith("MISSING") or s[0].startswith("EXTRA")]
    extra = [e for e in eps if e[4] == "extra"]
    unaired = [e for e in eps if e[4] in ("unaired", "special", "unaired-season")] + [s for s in seas if s[0] == "unaired-season"]
    if err or hard_eps or hard_seas or extra:
        real_missing += 1
        print(f"\n## {name}  (tmdb {tmdb}) — {len(hard_eps)} missing ep(s), {len(hard_seas)} season issue(s)")
        if err:
            print("   ", err)
        for s in hard_seas:
            print(f"   SEASON {s[1]}: {s[0]} — {s[2]}")
        for e in sorted(hard_eps, key=lambda x: (x[0], str(x[1]))):
            print(f"   S{e[0]:02d}E{int(e[1]):02d}  {e[2]}  (air {e[3]})")
        for e in extra:
            print(f"   note: {e[2]}")
print("=" * 100)
print(f"\nSeries with genuine missing/extra episodes or seasons: {real_missing} of {len(report)}")
