#!/usr/bin/env python3
"""
Dedicated upload runner for mediagram TV shows queue.
Features:
- Consumes already-prepared units (season subdirectories or flat show folders with 0 mkv and >0 mp4).
- Uploads immediately as each season or show is prepared, without waiting for other seasons.
- Uploads unit with `mediagram add-show <unit_dir> --tmdb <id> --yes --delete-source`.
- `--delete-source` deletes each episode from disk once its parts are in Telegram.
- Removes empty directories after all episodes are uploaded.
- Interleaved checkpoints to resolve pending sets via `mediagram resume`.
- Graceful SIGINT / SIGTERM handling.
"""

import os
import sys
import json
import time
import signal
import sqlite3
import subprocess
from datetime import datetime

DATA_DIR = "/home/andre/.local/share/mediagram"
PLAN_PATH = os.path.join(DATA_DIR, "shows_upload_plan.json")
LOG_PATH = os.path.join(DATA_DIR, "shows_batch_upload.log")
STATUS_PATH = os.path.join(DATA_DIR, "shows_upload_status.json")

# Optional argv[1]: an alternate plan file, so several upload runners can work
# disjoint slices of the library at once (see upload_slots in config.toml).
# Its log and status files get the plan's own suffix so runners never write
# over each other's.
if len(sys.argv) > 1:
    PLAN_PATH = os.path.abspath(sys.argv[1])
    _suffix = "_" + os.path.splitext(os.path.basename(PLAN_PATH))[0]
    LOG_PATH = os.path.join(DATA_DIR, "shows_batch_upload" + _suffix + ".log")
    STATUS_PATH = os.path.join(DATA_DIR, "shows_upload_status" + _suffix + ".json")

DB_PATH = os.path.join(DATA_DIR, "library.db")

running = True
active_proc = None
# tmdb ids owned by THIS plan; resume sweeps are scoped to them so a runner
# never starts a global sweep for a set the other runner is mid-upload on.
PLAN_TMDBS = set()

def handle_signal(sig, frame):
    global running, active_proc
    try:
        os.write(1, f"\n[SIGNAL] Received {sig}. Stopping uploader cleanly...\n".encode())
    except Exception:
        pass
    running = False
    if active_proc and active_proc.poll() is None:
        try:
            active_proc.terminate()
        except Exception:
            pass

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

def count_completed_in_db(tmdb_id):
    try:
        conn = sqlite3.connect(DB_PATH, timeout=10)
        cur = conn.cursor()
        cur.execute("SELECT count(*) FROM sets WHERE kind='ep' AND tmdb=? AND status='complete';", (tmdb_id,))
        count = cur.fetchone()[0]
        conn.close()
        return count
    except Exception as e:
        log(f"Warning: sqlite check failed: {e}")
        return 0

def get_pending_count():
    try:
        conn = sqlite3.connect(DB_PATH, timeout=10)
        cur = conn.cursor()
        if PLAN_TMDBS:
            marks = ",".join(["?"] * len(PLAN_TMDBS))
            cur.execute(
                f"SELECT count(*) FROM sets WHERE status!='complete' AND tmdb IN ({marks});",
                tuple(PLAN_TMDBS)
            )
        else:
            cur.execute("SELECT count(*) FROM sets WHERE status!='complete';")
        count = cur.fetchone()[0]
        conn.close()
        return count
    except Exception as e:
        log(f"Warning: sqlite pending check failed: {e}")
        return 0

def run_command_streaming(cmd, tag="add-show"):
    global active_proc
    try:
        active_proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1
        )
        if active_proc.stdout:
            for line in iter(active_proc.stdout.readline, ""):
                line_str = line.strip()
                if line_str:
                    log(f"  {tag}: {line_str}")
            active_proc.stdout.close()
        active_proc.wait()
        code = active_proc.returncode
        active_proc = None
        return code
    except Exception as e:
        log(f"Error running {cmd}: {e}")
        active_proc = None
        return -1

def another_uploader_running():
    # A live upload elsewhere in the library: either a `mediagram
    # add/add-show/resume` CLI process that is not OUR current child, or a
    # pid holding one of the two upload lock slots (which also covers `add`
    # handing its bytes to a background uploader that pgrep would miss).
    # `mediagram resume` sweeps every pending set, and with upload_slots > 1
    # a set merely mid-flight in another runner looks exactly like an
    # interrupted one. Never start a global resume while another upload lives.
    me = active_proc.pid if active_proc else None
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

def resume_pending_sets(max_retries=10):
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
                # The sibling runner can occupy the slots for hours; do not
                # stall this queue behind one straggler set — the next
                # checkpoint retries the sweep.
                log(f"Resume check: another uploader still active after {waits} waits; deferring to next checkpoint...")
                break
            log(f"Resume check: {pending} pending set(s) but another uploader is active; waiting 15s ({waits})...")
            time.sleep(15)
            continue
        retries += 1
        if retries > max_retries:
            log(f"Resume check: Reached max retries ({max_retries}) with {pending} sets still pending. Continuing...")
            break
        log(f"Resume check: {pending} unfinished set(s) in library.db. Running mediagram resume (attempt {retries}/{max_retries})...")
        code = run_command_streaming(["mediagram", "resume"], tag="resume")
        if code != 0:
            time.sleep(5)
        else:
            time.sleep(2)

def count_local_videos(show_dir):
    mkvs = []
    mp4s = []
    for root, _, files in os.walk(show_dir):
        for f in files:
            if f.startswith("."):
                continue
            if f.endswith((".mkv", ".avi", ".ts", ".webm", ".m4v")):
                mkvs.append(os.path.join(root, f))
            elif f.endswith(".mp4"):
                mp4s.append(os.path.join(root, f))
    return len(mkvs), len(mp4s)

def clean_empty_directories(path):
    try:
        for root, dirs, files in os.walk(path, topdown=False):
            for d in dirs:
                full = os.path.join(root, d)
                if not os.listdir(full):
                    os.rmdir(full)
        if os.path.exists(path) and not os.listdir(path):
            os.rmdir(path)
    except Exception as e:
        log(f"Cleanup note on {path}: {e}")

def is_actively_being_prepared(path):
    for root, _, files in os.walk(path):
        for f in files:
            if "prepared." in f:
                return True
    return False

def find_uploadable_units(show_path):
    if not os.path.exists(show_path):
        return []

    entries = sorted(os.listdir(show_path))
    subdirs = [
        os.path.join(show_path, e)
        for e in entries
        if os.path.isdir(os.path.join(show_path, e)) and not e.startswith(".")
    ]

    units = []
    if subdirs:
        for s in subdirs:
            if is_actively_being_prepared(s):
                continue
            mkv, mp4 = count_local_videos(s)
            if mp4 > 0:
                units.append((s, mp4 + mkv, os.path.basename(s)))
    else:
        if not is_actively_being_prepared(show_path):
            mkv, mp4 = count_local_videos(show_path)
            if mp4 > 0:
                units.append((show_path, mp4 + mkv, os.path.basename(show_path)))
    return units

def update_status(current_idx, total, completed_shows, current_item, step="idle"):
    data = {
        "current_index": current_idx,
        "total_shows": total,
        "completed_shows": completed_shows,
        "current_show": current_item,
        "step": step,
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

    total = len(plan)
    PLAN_TMDBS.update(item["tmdb_id"] for item in plan)
    log(f"Starting granular upload queue runner across {total} shows.")

    completed_shows = set()
    pass_num = 1

    while running:
        uploaded_in_pass = 0
        units_waiting_on_prepare = 0

        for idx, item in enumerate(plan, 1):
            if not running:
                break

            folder = item["folder"]
            show_path = item["path"]
            tmdb_id = item["tmdb_id"]

            if folder in completed_shows:
                continue

            if not os.path.exists(show_path):
                completed_shows.add(folder)
                continue

            mkv_count, mp4_count = count_local_videos(show_path)
            total_videos = mkv_count + mp4_count

            if total_videos == 0:
                clean_empty_directories(show_path)
                completed_shows.add(folder)
                continue

            uploadable_units = find_uploadable_units(show_path)

            if not uploadable_units:
                if mkv_count > 0:
                    units_waiting_on_prepare += 1
                continue

            for unit_path, unit_mp4_count, unit_name in uploadable_units:
                if not running:
                    break

                log(f"[{idx}/{total}] READY TO UPLOAD: {folder} / {unit_name} [tmdb-{tmdb_id}] ({unit_mp4_count} prepared mp4 episodes)")
                update_status(idx, total, len(completed_shows), f"{folder} / {unit_name}", step="uploading")

                upload_cmd = [
                    "mediagram",
                    "add-show",
                    unit_path,
                    "--tmdb",
                    str(tmdb_id),
                    "--yes",
                    "--delete-source"
                ]
                t0 = time.time()
                code = run_command_streaming(upload_cmd, tag="add-show")
                elapsed = time.time() - t0

                if code == 0:
                    log(f"[{idx}/{total}] UPLOAD FINISHED: {folder} / {unit_name} in {elapsed:.1f}s")
                    clean_empty_directories(unit_path)
                    uploaded_in_pass += 1
                else:
                    log(f"[{idx}/{total}] UPLOAD ISSUE: {folder} / {unit_name} exited with code {code}")

                if running:
                    resume_pending_sets()

            clean_empty_directories(show_path)
            rem_mkv, rem_mp4 = count_local_videos(show_path)
            if rem_mkv == 0 and rem_mp4 == 0:
                completed_shows.add(folder)

        # Check total remaining videos across all shows
        total_remaining = 0
        for item in plan:
            p = item["path"]
            if os.path.exists(p):
                m, mp = count_local_videos(p)
                total_remaining += (m + mp)

        if total_remaining == 0:
            log("All shows have been uploaded and removed from disk. Exiting upload runner.")
            break

        if not running:
            break

        pass_num += 1
        if uploaded_in_pass == 0:
            log(f"Pass {pass_num - 1} finished. {total_remaining} episodes remaining across library. Waiting 15s before next upload pass...")
            time.sleep(15)

    if running:
        log("Running final pending sets resume sweep...")
        resume_pending_sets()
        log("Upload queue runner finished cleanly.")

if __name__ == "__main__":
    main()
