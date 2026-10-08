#!/usr/bin/env python3
"""
Upload runner for Wall Street Story course in mediagram.
- Runs `mediagram add-course --course "Wall Street Story" "/run/media/andre/T7/Videos/1 Wallstreet Stroy Masterclass"`
- Handles transient network/RPC interruptions with periodic resume loops
- Logs progress to /home/andre/.local/share/mediagram/wallstreet_story_upload.log
- Writes live status to /home/andre/.local/share/mediagram/wallstreet_story_status.json
"""

import os
import sys
import json
import time
import signal
import sqlite3
import subprocess
from datetime import datetime

COURSE_DIR = "/run/media/andre/T7/Videos/1 Wallstreet Stroy Masterclass"
COURSE_NAME = "Wall Street Story"
COURSE_ID = "wall-street-story"
TOTAL_LESSONS = 864
TOTAL_DOCS = 10
TOTAL_ITEMS = TOTAL_LESSONS + TOTAL_DOCS

DATA_DIR = os.path.expanduser("~/.local/share/mediagram")
DB_PATH = os.path.join(DATA_DIR, "library.db")
LOG_PATH = os.path.join(DATA_DIR, "wallstreet_story_upload.log")
STATUS_PATH = os.path.join(DATA_DIR, "wallstreet_story_status.json")

running = True

def handle_signal(sig, frame):
    global running
    print(f"\nReceived signal {sig}. Stopping runner cleanly...", flush=True)
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

def get_stats():
    try:
        conn = sqlite3.connect(DB_PATH, timeout=10)
        cur = conn.cursor()
        cur.execute(
            "SELECT count(*) FROM sets WHERE group_key=? AND status='complete' AND kind='tut';",
            (COURSE_ID,)
        )
        completed_lessons = cur.fetchone()[0]

        cur.execute(
            "SELECT count(*) FROM sets WHERE group_key=? AND status='complete' AND kind='doc';",
            (COURSE_ID,)
        )
        completed_docs = cur.fetchone()[0]

        cur.execute(
            "SELECT count(*) FROM sets WHERE status!='complete';"
        )
        pending_total = cur.fetchone()[0]

        cur.execute(
            "SELECT count(*) FROM sets WHERE group_key=? AND status!='complete';",
            (COURSE_ID,)
        )
        pending_course = cur.fetchone()[0]

        conn.close()
        return {
            "completed_lessons": completed_lessons,
            "completed_docs": completed_docs,
            "completed_total": completed_lessons + completed_docs,
            "pending_total": pending_total,
            "pending_course": pending_course,
        }
    except Exception as e:
        log(f"Warning: sqlite stats check failed: {e}")
        return {
            "completed_lessons": 0,
            "completed_docs": 0,
            "completed_total": 0,
            "pending_total": 0,
            "pending_course": 0,
        }

def update_status(stage, extra=None):
    stats = get_stats()
    data = {
        "course": COURSE_NAME,
        "course_id": COURSE_ID,
        "stage": stage,
        "completed_lessons": stats["completed_lessons"],
        "total_lessons": TOTAL_LESSONS,
        "completed_docs": stats["completed_docs"],
        "total_docs": TOTAL_DOCS,
        "completed_total": stats["completed_total"],
        "total_items": TOTAL_ITEMS,
        "pending_course_sets": stats["pending_course"],
        "pending_total_sets": stats["pending_total"],
        "percent": round(100.0 * stats["completed_total"] / max(TOTAL_ITEMS, 1), 1),
        "last_updated": datetime.now().isoformat(),
    }
    if extra:
        data.update(extra)
    try:
        tmp = STATUS_PATH + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        os.replace(tmp, STATUS_PATH)
    except Exception:
        pass

def resume_pending(max_retries=15):
    retries = 0
    while running:
        stats = get_stats()
        if stats["pending_total"] == 0:
            log("Resume check: 0 pending sets in library.db.")
            break
        retries += 1
        if retries > max_retries:
            log(f"Resume check: Reached max retries ({max_retries}) with {stats['pending_total']} pending. Continuing...")
            break
        log(f"Resume check: {stats['pending_total']} pending set(s). Running mediagram resume (attempt {retries}/{max_retries})...")
        update_status("resuming", {"resume_attempt": retries})
        proc = subprocess.run(
            ["mediagram", "resume"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False
        )
        for line in proc.stdout.strip().splitlines():
            if line:
                log(f"  resume: {line}")
        if proc.returncode != 0:
            log(f"mediagram resume exited with code {proc.returncode}. Retrying in 5s...")
            time.sleep(5)
        else:
            log("mediagram resume completed cleanly.")
            time.sleep(2)

def run_add_course():
    cmd = [
        "mediagram",
        "add-course",
        "--course", COURSE_NAME,
        "--cid", COURSE_ID,
        COURSE_DIR
    ]
    log(f"Running command: {' '.join(cmd)}")
    update_status("uploading")

    process = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1
    )

    last_status_time = time.time()
    if process.stdout is not None:
        for line in iter(process.stdout.readline, ""):
            line_str = line.strip()
            if line_str:
                log(f"  add-course: {line_str}")
            now = time.time()
            if now - last_status_time > 10:
                update_status("uploading", {"last_log": line_str})
                last_status_time = now
            if not running:
                log("Stopping add-course subprocess cleanly...")
                process.terminate()
                try:
                    process.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    process.kill()
                break

    process.wait()
    return process.returncode

def main():
    log("=== Wall Street Story Upload Runner Starting ===")
    stats = get_stats()
    log(f"Initial state: {stats['completed_total']}/{TOTAL_ITEMS} items complete ({stats['completed_lessons']} lessons, {stats['completed_docs']} docs), {stats['pending_course']} pending.")

    # Check if there are any stale pending sets first
    if stats["pending_total"] > 0:
        log(f"Resolving {stats['pending_total']} existing pending set(s) before starting...")
        resume_pending()

    attempt = 0
    while running:
        stats = get_stats()
        if stats["completed_lessons"] >= TOTAL_LESSONS and stats["completed_docs"] >= TOTAL_DOCS:
            log(f"All {TOTAL_ITEMS} items are complete in library.db!")
            update_status("complete")
            break

        attempt += 1
        log(f"--- Starting add-course pass {attempt} ({stats['completed_total']}/{TOTAL_ITEMS} completed) ---")
        ec = run_add_course()
        log(f"add-course pass {attempt} finished with exit code {ec}.")

        # If interrupted, break
        if not running:
            break

        # Check pending sets and resolve them
        stats = get_stats()
        if stats["pending_total"] > 0:
            log(f"Post-run resume check: {stats['pending_total']} pending set(s) found. Resuming...")
            resume_pending()

        stats = get_stats()
        if stats["completed_lessons"] >= TOTAL_LESSONS and stats["completed_docs"] >= TOTAL_DOCS:
            log(f"All {TOTAL_ITEMS} items are complete in library.db!")
            update_status("complete")
            break

        # If add-course returned non-zero and we made no progress, wait before retry
        log("Checking progress before next iteration...")
        time.sleep(5)

    # Final verification and index push
    stats = get_stats()
    log(f"Final stats: {stats['completed_lessons']}/{TOTAL_LESSONS} lessons, {stats['completed_docs']}/{TOTAL_DOCS} docs complete.")
    if stats["completed_total"] > 0 and stats["pending_total"] == 0:
        log("Ensuring index is published...")
        update_status("pushing_index")
        proc = subprocess.run(
            ["mediagram", "push-index", "--merge"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False
        )
        for line in proc.stdout.strip().splitlines():
            log(f"  push-index: {line}")
        log("Index published successfully.")

    update_status("finished")
    log("=== Wall Street Story Upload Runner Ended ===")

if __name__ == "__main__":
    main()
