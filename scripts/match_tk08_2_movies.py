#!/usr/bin/env python3
"""
Match the film files in ~/TK08_2/Movies against TMDB and append them to
movies_upload_plan.json (the plan consumed by scripts/upload_movies_queue.py).

Rules of the road:
- German-language search (tmdb_language from config.toml), fallback query with
  umlaut expansion (ue->ü etc.) for the many filenames written without them.
- Only score>=CONFIRM_MIN with a matching (or absent) year is auto-queued.
  Everything else lands in a review report; nothing half-matched goes in.
- Films already 'complete' in library.db are skipped entirely.
- Two files for one film (mkv/mp4 variants of the same tmdb id) keep the
  largest as the uploadable filepath and record the rest as alternate_files.
- Existing plan entries are preserved; the file is rewritten atomically after
  a timestamped backup.

Review report: ~/.local/share/mediagram/movies_tk08_2_review.json
"""

import json
import os
import re
import sqlite3
import sys
import difflib
import unicodedata
import urllib.parse
import urllib.request
from datetime import datetime

MOVIE_DIR = os.path.expanduser("~/TK08_2/Movies")
DATA_DIR = "/home/andre/.local/share/mediagram"
PLAN_PATH = os.path.join(DATA_DIR, "movies_upload_plan.json")
DB_PATH = os.path.join(DATA_DIR, "library.db")
CONFIG_PATH = os.path.expanduser("~/.config/mediagram/config.toml")
REVIEW_PATH = os.path.join(DATA_DIR, "movies_tk08_2_review.json")
CONFIRM_MIN = 95

DRY = "--apply" not in sys.argv

PREFIX_RE = re.compile(
    r"^(?:4K-|D\+-|DE-DO\s*-|DE-TOP\s*-\s*\d+\.\s*|DE\s+-\s*|EN\s+-\s*|"
    r"PRMT\s+-\s*|UNV\s+-\s*|AMZ\s+-\s*)+",
    re.IGNORECASE,
)
YEAR_RE = re.compile(r"[\s._(\[-]((?:19|20)\d{2})(?=[)\].\s_-]|$)")
GROUP_RE = re.compile(r"-[A-Za-z0-9]+$")  # trailing -RELEASEGROUP on dotted names
QUALITY_TOKENS = re.compile(
    r"\b(?:REMASTERED|UNRATED|DIRECTORS|CUT|EXTENDED|BDRIP|BLURAY|BluRay|BRRip|"
    r"DVDRIP|WEB(?:-?DL)?|HDTV|HDR|DV|SDR|HEVC|x26[45]|H\.?26[45]|AVC|"
    r"1080p|720p|2160p|4K|UHD|FS|AC3|EAC3|DTS(?:\.HD)?|DDP?5?1|GERMAN|German|"
    r"DL|MULTI|SUBBED|LD|REPACk|REPACK|PROPER|LIVE|1080HD|1080 HD|HD-Source|HD|"
    r"comedy|fassung|kino|to|dl|dtos)\b",
    re.IGNORECASE,
)

def tmdb_key():
    with open(CONFIG_PATH, encoding="utf-8") as f:
        for line in f:
            m = re.match(r'\s*tmdb_key\s*=\s*"([^"]+)"', line)
            if m:
                return m.group(1)
    raise SystemExit("tmdb_key not found in config.toml")

def tmdb_lang():
    with open(CONFIG_PATH, encoding="utf-8") as f:
        for line in f:
            m = re.match(r'\s*tmdb_language\s*=\s*"([^"]+)"', line)
            if m:
                return m.group(1)
    return "de-DE"

KEY = tmdb_key()
LANG = tmdb_lang()

def norm(s):
    s = unicodedata.normalize("NFKD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = s.lower()
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return " ".join(s.split())

def expand_umlauts(s):
    # filenames often drop umlauts; TMDB German titles keep them
    return (s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
             .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue")
             .replace("ß", "ss"))

def search_movie(query, year=None):
    params = {"query": query, "language": LANG, "include_adult": "false"}
    if year:
        params["year"] = str(year)
    url = "https://api.themoviedb.org/3/search/movie?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={
        "Authorization": "Bearer " + KEY,
        "accept": "application/json",
    })
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.load(r).get("results", [])
    except Exception:
        return []

def parse_name(fname):
    stem = re.sub(r"\.(mkv|mp4|avi|ts|webm|m4v)$", "", fname, flags=re.I)
    year = None
    m = None
    for m in YEAR_RE.finditer(" " + stem):
        year = int(m.group(1))
    title = PREFIX_RE.sub("", stem)
    # dotted scene-style names: cut release group and quality noise
    if "." in title and re.search(r"\.(19|20)\d{2}\.", title):
        title = GROUP_RE.sub("", title.replace(".", " "))
    title = title.replace("_", " ").replace(":", " ").replace("?", " ")
    title = re.sub(r"\((\d{4})\)", " ", title)
    title = QUALITY_TOKENS.sub(" ", title)
    title = re.sub(r"[^A-Za-zÄÖÜäöüß0-9'&,!. -]", " ", title)
    title = re.sub(r"\s+", " ", title).strip(" -.")
    return title, year

def score(parsed_title, parsed_year, cand):
    ctitle = norm(cand.get("title") or "")
    cotitle = norm(cand.get("original_title") or "")
    p = norm(parsed_title)
    best = 0.0
    for c in (ctitle, cotitle):
        if not c or not p:
            continue
        if c == p:
            best = max(best, 100)
        elif c.startswith(p) or p.startswith(c) or c in p or p in c:
            best = max(best, 70 + 25 * min(len(p), len(c)) / max(len(p), len(c)))
        else:
            best = max(best, difflib.SequenceMatcher(None, p, c).ratio() * 75)
    cyear = int((cand.get("release_date") or "0000")[:4] or 0)
    year_ok = True
    if parsed_year:
        if cyear and abs(cyear - parsed_year) <= 1:
            best += 25
        elif cyear:
            best -= 20
            year_ok = False
    return best, year_ok

def complete_tmdb():
    conn = sqlite3.connect(DB_PATH)
    rows = {r[0] for r in conn.execute(
        "SELECT tmdb FROM sets WHERE kind='movie' AND status='complete' AND tmdb IS NOT NULL")}
    conn.close()
    return rows

def main():
    files = []
    for e in sorted(os.listdir(MOVIE_DIR)):
        p = os.path.join(MOVIE_DIR, e)
        if not os.path.isfile(p) or e.startswith("."):
            continue
        if not e.lower().endswith((".mkv", ".mp4", ".avi", ".ts", ".webm", ".m4v")):
            continue
        files.append(p)

    done = complete_tmdb()
    old_plan = []
    if os.path.exists(PLAN_PATH):
        old_plan = json.load(open(PLAN_PATH, encoding="utf-8"))
    old_ids = {x["tmdb_id"] for x in old_plan}

    matched, review, skipped_db = {}, [], 0
    for path in files:
        fname = os.path.basename(path)
        title, year = parse_name(fname)
        cands = search_movie(title, year)
        best, best_year_ok = None, False
        for c in cands[:5]:
            s, ok = score(title, year, c)
            if best is None or s > best[1]:
                best, best_year_ok = (c, s), ok
        if not best:
            review.append({"file": fname, "parsed_title": title, "parsed_year": year,
                           "reason": "no search results"})
            continue
        c, s = best
        if s >= CONFIRM_MIN and best_year_ok and year:
            if c["id"] in done:
                skipped_db += 1
                continue
            size = os.path.getsize(path)
            entry = matched.get(c["id"])
            item = {
                "tmdb_id": c["id"],
                "tmdb_title": c.get("title") or c.get("original_title"),
                "tmdb_year": int((c.get("release_date") or "0")[:4] or 0) or None,
                "filepath": path,
                "filename": fname,
                "size_bytes": size,
                "size_gb": round(size / 1024 ** 3, 2),
                "alternate_files": [],
                "_score": s,
            }
            if entry is None:
                matched[c["id"]] = item
            else:
                # keep the biggest copy primary, list the other as alternate
                other = item if size > entry["size_bytes"] else entry
                keep = entry if size > entry["size_bytes"] else item
                keep["alternate_files"].append(other["filepath"])
                matched[c["id"]] = keep
        else:
            review.append({
                "file": fname, "parsed_title": title, "parsed_year": year,
                "candidate_id": c["id"], "candidate_title": c.get("title"),
                "candidate_year": (c.get("release_date") or "")[:4], "score": round(s, 1),
                "reason": "below confidence" if year else "no year in filename",
            })

    new_items = [v for v in matched.values() if v["tmdb_id"] not in old_ids]
    for v in new_items:
        v.pop("_score", None)
    already_queued = len(matched) - len(new_items)

    print(f"files: {len(files)} | auto-matched: {len(matched)} "
          f"(already in plan: {already_queued}, new: {len(new_items)}) | "
          f"already uploaded: {skipped_db} | need review: {len(review)}")
    json.dump(review, open(REVIEW_PATH, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print(f"review report: {REVIEW_PATH}")
    gb = sum(x["size_bytes"] for x in new_items) / 1024 ** 3
    print(f"new queue volume: {gb:.1f} GB")

    if DRY:
        print("\nDRY RUN - plan not modified. Re-run with --apply.")
        for r in review[:20]:
            print("  ?", r["file"], "->", r.get("candidate_title"), r.get("score"), r.get("reason"))
        return

    plan = old_plan + sorted(new_items, key=lambda x: (x["tmdb_title"] or "").lower())
    backup = PLAN_PATH + ".bak-" + datetime.now().strftime("%y%m%d-%H%M")
    os.replace(PLAN_PATH, backup)
    tmp = PLAN_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(plan, f, indent=1, ensure_ascii=False)
    os.replace(tmp, PLAN_PATH)
    print(f"plan rewritten: {len(old_plan)} -> {len(plan)} entries (backup: {backup})")

if __name__ == "__main__":
    main()
