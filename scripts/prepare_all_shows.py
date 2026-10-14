#!/usr/bin/env python3
"""
Dedicated bulk prepare runner for mediagram TV shows with up to 4 concurrent workers.
Walks TV series in ~/TK08/Videos/xstream and converts .mkv files to
direct-play faststart .mp4 with AAC audio using 4 concurrent workers:
    mediagram prepare <file> --mp4 --out <file_dir> --delete-source
This deletes the original .mkv as soon as the .mp4 replacement is checked and verified.
Skips folders actively being synced by rsync, looping until all shows are prepared.
"""

import os
import sys
import json
import time
import signal
import subprocess
import concurrent.futures
from datetime import datetime

MAX_CONCURRENT = 4
DATA_DIR = "/home/andre/.local/share/mediagram"
PLAN_PATH = os.path.join(DATA_DIR, "shows_upload_plan.json")
LOG_PATH = os.path.join(DATA_DIR, "shows_prepare.log")
STATUS_PATH = os.path.join(DATA_DIR, "shows_prepare_status.json")

# Optional argv[1]: an alternate plan file, so several prepare+upload pipelines
# can run disjoint slices of the library at once (pair with upload_slots).
# Its log and status files get the plan's own suffix so runners never write
# over each other's.
if len(sys.argv) > 1:
    PLAN_PATH = os.path.abspath(sys.argv[1])
    _suffix = "_" + os.path.splitext(os.path.basename(PLAN_PATH))[0]
    LOG_PATH = os.path.join(DATA_DIR, "shows_prepare" + _suffix + ".log")
    STATUS_PATH = os.path.join(DATA_DIR, "shows_prepare_status" + _suffix + ".json")

# Optional argv[2]: per-instance worker count, so two sliced instances can
# together stay at the machine's proven total of 4 concurrent conversions.
if len(sys.argv) > 2:
    MAX_CONCURRENT = int(sys.argv[2])

running = True
active_subprocesses = set()

def handle_signal(sig, frame):
    global running
    try:
        os.write(1, f"\n[SIGNAL] Received {sig}. Halting prepare runner...\n".encode())
    except Exception:
        pass
    running = False
    for p in list(active_subprocesses):
        try:
            p.terminate()
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

def is_rsync_active_on_path(show_dir):
    for root, _, files in os.walk(show_dir):
        for f in files:
            if f.startswith(".") and (f.endswith(".mkv") or "mkv." in f or f.endswith(".mp4") or "mp4." in f):
                return True
    return False

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

def get_mkv_files_to_prepare(show_path):
    mkvs = []
    for root, _, files in os.walk(show_path):
        for f in sorted(files):
            if f.startswith(".") or "prepared." in f:
                continue
            if f.endswith((".mkv", ".avi", ".ts", ".webm", ".m4v")):
                mkvs.append(os.path.join(root, f))
    return sorted(mkvs)

def update_status(current_idx, total, current_item, mkv_left, mp4_done, concurrency):
    data = {
        "current_index": current_idx,
        "total_shows": total,
        "current_show": current_item,
        "mkv_remaining": mkv_left,
        "mp4_prepared": mp4_done,
        "concurrency": concurrency,
        "last_updated": datetime.now().isoformat()
    }
    try:
        tmp_file = STATUS_PATH + ".tmp"
        with open(tmp_file, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        os.replace(tmp_file, STATUS_PATH)
    except Exception:
        pass

def prepare_single_file(file_path, item_idx, total_items, show_name):
    if not running:
        return -1
    file_dir = os.path.dirname(file_path)
    base_name = os.path.basename(file_path)
    cmd = [
        "mediagram",
        "prepare",
        file_path,
        "--mp4",
        "--out",
        file_dir,
        "--delete-source"
    ]
    t0 = time.time()
    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1
        )
    except Exception as e:
        log(f"  [{show_name}] Error spawning prepare on {base_name}: {e}")
        return -1

    active_subprocesses.add(proc)
    last_line = ""
    try:
        if proc.stdout:
            for line in iter(proc.stdout.readline, ""):
                line_str = line.strip()
                if line_str:
                    last_line = line_str
            proc.stdout.close()
        proc.wait()
        code = proc.returncode
    except Exception as e:
        code = -1
        last_line = str(e)
    finally:
        active_subprocesses.discard(proc)

    elapsed = time.time() - t0
    if code == 0:
        log(f"  [{show_name}] [{item_idx}/{total_items}] DONE: {base_name} in {elapsed:.1f}s")
    else:
        log(f"  [{show_name}] [{item_idx}/{total_items}] WARNING: {base_name} exited {code} ({last_line})")
    return code

def main():
    if not os.path.exists(PLAN_PATH):
        print(f"Error: Plan file not found: {PLAN_PATH}", file=sys.stderr)
        sys.exit(1)

    with open(PLAN_PATH, "r", encoding="utf-8") as f:
        plan = json.load(f)

    total = len(plan)
    log(f"Starting bulk media preparation runner across {total} shows with {MAX_CONCURRENT} concurrent workers.")

    pass_num = 1
    while running:
        log(f"=== Prepare Pass {pass_num} (concurrency={MAX_CONCURRENT}) ===")
        processed_in_pass = 0
        still_syncing = 0

        for idx, item in enumerate(plan, 1):
            if not running:
                break

            folder = item["folder"]
            show_path = item["path"]

            if not os.path.exists(show_path):
                continue

            if is_rsync_active_on_path(show_path):
                still_syncing += 1
                continue

            mkv_files = get_mkv_files_to_prepare(show_path)
            if not mkv_files:
                continue

            mkv_count, mp4_count = count_local_videos(show_path)
            log(f"[{idx}/{total}] PREPARING (up to {MAX_CONCURRENT} concurrent): {folder} ({len(mkv_files)} mkv files)...")
            update_status(idx, total, folder, mkv_count, mp4_count, MAX_CONCURRENT)

            t_show_start = time.time()
            with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_CONCURRENT) as executor:
                futures = {
                    executor.submit(prepare_single_file, f, i, len(mkv_files), folder): f
                    for i, f in enumerate(mkv_files, 1)
                }
                for future in concurrent.futures.as_completed(futures):
                    if not running:
                        executor.shutdown(wait=False, cancel_futures=True)
                        break
                    res = future.result()
                    if res == 0:
                        processed_in_pass += 1

            show_elapsed = time.time() - t_show_start
            rem_mkv, rem_mp4 = count_local_videos(show_path)
            log(f"[{idx}/{total}] FINISHED BATCH: {folder} in {show_elapsed:.1f}s — remaining: {rem_mkv} mkv, {rem_mp4} mp4")

        # Check if rsync is still running
        rsync_running = False
        try:
            ps_res = subprocess.run(["pgrep", "-f", "rsync.*bytefortress"], capture_output=True)
            rsync_running = (ps_res.returncode == 0)
        except Exception:
            pass

        total_mkv_remaining = 0
        for item in plan:
            p = item["path"]
            if os.path.exists(p):
                mkv, _ = count_local_videos(p)
                total_mkv_remaining += mkv

        if total_mkv_remaining == 0 and not rsync_running and still_syncing == 0:
            log("All shows have been prepared to MP4 and no shows are waiting on rsync. Exiting prepare runner.")
            break

        if not running:
            break

        pass_num += 1
        log(f"Pass {pass_num - 1} finished. {total_mkv_remaining} MKVs remaining across library. Waiting 15s before next pass...")
        time.sleep(15)

if __name__ == "__main__":
    main()
