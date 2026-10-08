#!/usr/bin/env python3
"""
Sequential upload runner for mediagram movies queue.
Uploads missing movies one by one using `mediagram add <file> --tmdb <id> --watch --delete-source`.
`--delete-source` removes the file once the index confirms every part is in Telegram.
Dynamically checks library.db before each upload to avoid duplicates.
Performs a check every 10 movies to finish any unfinished/pending movies via `mediagram resume`.
"""

import os
import sys
import json
import time
import signal
import sqlite3
import subprocess
from datetime import datetime

PLAN_PATH = "/home/andre/.local/share/mediagram/movies_upload_plan.json"
DB_PATH = "/home/andre/.local/share/mediagram/library.db"
LOG_PATH = "/home/andre/.local/share/mediagram/movies_batch_upload.log"
STATUS_PATH = "/home/andre/.local/share/mediagram/movies_upload_status.json"
CHECK_INTERVAL = 10

running = True
active_proc = [None]   # current `mediagram add` child, for the uploader check
PLAN_TMDBS = set()

def handle_signal(sig, frame):
    global running
    print(f"\nReceived signal {sig}. Finishing current step and exiting cleanly...", flush=True)
    running = False

signal.signal(signal.SIGINT, handle_signal)
signal.signal(signal.SIGTERM, handle_signal)

def log(msg):
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    line = f"[{ts}] {msg}"
    print(line, flush=True)
    try:
        with open(LOG_PATH, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass

def is_already_uploaded(tmdb_id):
    try:
        conn = sqlite3.connect(DB_PATH, timeout=10)
        cur = conn.cursor()
        cur.execute("SELECT status FROM sets WHERE kind='movie' AND tmdb=? AND status='complete';", (tmdb_id,))
        row = cur.fetchone()
        conn.close()
        return row is not None
    except Exception as e:
        log(f"Warning: sqlite check failed: {e}")
        return False

def get_pending_count():
    try:
        conn = sqlite3.connect(DB_PATH, timeout=10)
        cur = conn.cursor()
        if PLAN_TMDBS:
            marks = ",".join(["?"] * len(PLAN_TMDBS))
            cur.execute(
                f"SELECT count(*) FROM sets WHERE status!='complete' AND kind='movie' AND tmdb IN ({marks});",
                tuple(PLAN_TMDBS)
            )
        else:
            cur.execute("SELECT count(*) FROM sets WHERE status!='complete' AND kind='movie';")
        count = cur.fetchone()[0]
        conn.close()
        return count
    except Exception as e:
        log(f"Warning: sqlite pending check failed: {e}")
        return 0

def another_uploader_running():
    # Same guard as upload_shows_queue.py: `mediagram resume` sweeps every
    # pending set, and with upload_slots > 1 a set merely mid-flight in a
    # show runner looks interrupted. Never start a sweep while another
    # uploader lives — CLI process or background holder of a lock slot
    # (`add` hands its bytes to a background process pgrep would miss).
    me = active_proc[0].pid if active_proc[0] else None
    pids = set()
    try:
        out = subprocess.run(
            ["pgrep", "-f", "mediagram (add-show|add|resume)"],
            capture_output=True, text=True, check=False
        ).stdout.split()
        pids.update(int(p) for p in out if p.isdigit())
    except Exception:
        pass
    try:
        locks = " ".join([
            os.path.join(DATA_DIR, "upload.lock"),
            os.path.join(DATA_DIR, "upload.lock.1"),
        ])
        out = subprocess.run(
            ["sh", "-c", f"fuser {locks} 2>/dev/null"],
            capture_output=True, text=True, check=False
        ).stdout.split()
        pids.update(int(p) for p in out if p.isdigit())
    except Exception:
        pass
    pids.discard(os.getpid())
    if me:
        pids.discard(me)
    return len(pids) > 0

def resume_pending_movies(max_retries=10):
    retries = 0
    waits = 0
    while running:
        pending = get_pending_count()
        if pending == 0:
            log("Resume check: 0 unfinished sets in library.db.")
            break
        if another_uploader_running():
            waits += 1
            if waits > 20:
                # The show runners can occupy both slots for hours; waiting
                # out every sweep would stall the movie queue behind one
                # straggler set. Move on — the next checkpoint retries.
                log(f"Resume check: another uploader still active after {waits} waits; deferring to next checkpoint...")
                break
            log(f"Resume check: {pending} movie set(s) pending but another uploader is active; waiting 15s ({waits})...")
            time.sleep(15)
            continue
        retries += 1
        if retries > max_retries:
            log(f"Resume check: Reached max retries ({max_retries}) with {pending} sets still pending. Continuing queue...")
            break
        log(f"Resume check: {pending} unfinished set(s) in library.db. Running mediagram resume (attempt {retries}/{max_retries})...")
        proc = subprocess.run(
            ["mediagram", "resume"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False
        )
        for line in proc.stdout.strip().splitlines():
            log(f"  resume: {line}")
        if proc.returncode != 0:
            log(f"mediagram resume exited with code {proc.returncode}. Retrying in 5s...")
            time.sleep(5)
        else:
            log("mediagram resume completed cleanly.")
            time.sleep(2)

def update_status(current_idx, total, completed, failed, current_item, in_resume=False):
    data = {
        "current_index": current_idx,
        "total_movies": total,
        "completed_count": completed,
        "failed_count": failed,
        "current_movie": current_item,
        "in_resume_check": in_resume,
        "last_updated": datetime.now().isoformat()
    }
    try:
        tmp_file = STATUS_PATH + ".tmp"
        with open(tmp_file, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        os.replace(tmp_file, STATUS_PATH)
    except Exception:
        pass

def main():
    if not os.path.exists(PLAN_PATH):
        print(f"Error: Plan file not found: {PLAN_PATH}", file=sys.stderr)
        sys.exit(1)

    with open(PLAN_PATH, "r", encoding="utf-8") as f:
        plan = json.load(f)

    PLAN_TMDBS.update(x["tmdb_id"] for x in plan)
    total = len(plan)
    log(f"Resuming movies upload runner with 10-movie check. Total movies in plan: {total}")

    completed = 0
    failed = 0
    skipped = 0
    batch_attempted = 0

    for idx, item in enumerate(plan, 1):
        if not running:
            log("Stop signal received. Exiting runner loop.")
            break

        tmdb_id = item["tmdb_id"]
        title = item.get("tmdb_title", "Unknown")
        year = item.get("tmdb_year", "")
        filepath = item["filepath"]
        size_gb = item.get("size_gb", 0)

        # Check DB
        if is_already_uploaded(tmdb_id):
            skipped += 1
            continue

        if not os.path.exists(filepath):
            failed += 1
            log(f"[{idx}/{total}] ERROR: File not found: {filepath}")
            batch_attempted += 1
            continue

        log(f"[{idx}/{total}] START: '{title} ({year})' [tmdb-{tmdb_id}] ({size_gb:.2f} GB) - {os.path.basename(filepath)}")
        update_status(idx, total, completed, failed, item)

        cmd = [
            "mediagram",
            "add",
            filepath,
            "--tmdb",
            str(tmdb_id),
            "--watch",
            "--delete-source"
        ]

        start_time = time.time()
        try:
            proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True
            )
            active_proc[0] = proc
            out = proc.stdout.read()
            proc.stdout.close()
            rc = proc.wait()
            active_proc[0] = None

            elapsed = time.time() - start_time
            rate_mb = (size_gb * 1024) / elapsed if elapsed > 0 else 0

            for line in (out or "").strip().splitlines():
                log(f"  mediagram: {line}")

            if rc == 0:
                completed += 1
                log(f"[{idx}/{total}] DONE: '{title}' in {elapsed:.1f}s ({rate_mb:.2f} MB/s)")
            else:
                failed += 1
                log(f"[{idx}/{total}] FAIL: '{title}' exited with code {rc}")
        except Exception as e:
            active_proc[0] = None
            failed += 1
            log(f"[{idx}/{total}] EXCEPTION: '{title}': {e}")

        batch_attempted += 1
        update_status(idx, total, completed, failed, None)

        # Check every CHECK_INTERVAL movies
        if batch_attempted >= CHECK_INTERVAL:
            log(f"--- 10-movie batch checkpoint: checking for unfinished movies ---")
            update_status(idx, total, completed, failed, None, in_resume=True)
            resume_pending_movies()
            update_status(idx, total, completed, failed, None, in_resume=False)
            batch_attempted = 0

    # Final sweep
    if running:
        log("Queue finished all items. Performing final unfinished movies check...")
        resume_pending_movies()

    log(f"Runner finished. {completed} uploaded, {skipped} skipped, {failed} failed out of {total}.")

if __name__ == "__main__":
    main()
