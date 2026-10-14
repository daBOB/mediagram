#!/usr/bin/env python3
"""
Dedicated runner to prepare and upload Star Trek: Strange New Worlds Season 4.
Workflow:
1. mediagram prepare <dir> --mp4 --out <dir> --delete-source
2. mediagram add-show <dir> --tmdb 103516 --yes --delete-source
3. Clean up empty directories
4. mediagram resume check
"""

import os
import sys
import time
import signal
import subprocess
from datetime import datetime

TARGET_DIR = "/home/andre/TK08_2/Downloads/108WEB-StarTrekStrangeNewWorldsS04E01-WAYNE"
TMDB_ID = 103516
LOG_PATH = "/home/andre/.local/share/mediagram/strange_new_worlds.log"

running = True
active_proc = None

def handle_signal(sig, frame):
    global running, active_proc
    log(f"Received signal {sig}. Stopping runner...")
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

def run_command_streaming(cmd, tag="cmd"):
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

def clean_empty_directories(path):
    try:
        for root, dirs, files in os.walk(path, topdown=False):
            for d in dirs:
                full = os.path.join(root, d)
                if os.path.exists(full) and not os.listdir(full):
                    os.rmdir(full)
        if os.path.exists(path) and not os.listdir(path):
            os.rmdir(path)
    except Exception as e:
        log(f"Cleanup note on {path}: {e}")

def main():
    log(f"Starting preparation and upload for Star Trek: Strange New Worlds (TMDB {TMDB_ID}).")

    if not os.path.exists(TARGET_DIR):
        log(f"Target directory does not exist: {TARGET_DIR}")
        sys.exit(1)

    # 1. Clean any leftover Sample folders
    for root, dirs, files in os.walk(TARGET_DIR):
        for d in dirs:
            if "sample" in d.lower():
                sample_path = os.path.join(root, d)
                log(f"Removing sample folder: {sample_path}")
                import shutil
                shutil.rmtree(sample_path, ignore_errors=True)

    # 2. Check for MKV files needing preparation
    mkvs = []
    mp4s = []
    for root, _, files in os.walk(TARGET_DIR):
        for f in files:
            if f.startswith("."):
                continue
            if f.endswith((".mkv", ".avi", ".ts", ".webm", ".m4v")):
                mkvs.append(os.path.join(root, f))
            elif f.endswith(".mp4"):
                mp4s.append(os.path.join(root, f))

    log(f"Found {len(mkvs)} MKV(s) and {len(mp4s)} MP4(s).")

    if mkvs:
        log("Running mediagram prepare to convert to browser-ready faststart MP4 and delete source MKVs...")
        prep_cmd = [
            "mediagram",
            "prepare",
            TARGET_DIR,
            "--mp4",
            "--out",
            TARGET_DIR,
            "--delete-source"
        ]
        t0 = time.time()
        code = run_command_streaming(prep_cmd, tag="prepare")
        elapsed = time.time() - t0
        if code != 0:
            log(f"Prepare exited with code {code}.")
            if not running:
                sys.exit(1)
        else:
            log(f"Prepare completed successfully in {elapsed:.1f}s.")

    if not running:
        sys.exit(0)

    # 3. Upload show via add-show with --delete-source
    log("Uploading show via mediagram add-show...")
    upload_cmd = [
        "mediagram",
        "add-show",
        TARGET_DIR,
        "--tmdb",
        str(TMDB_ID),
        "--yes",
        "--delete-source"
    ]
    t0 = time.time()
    code = run_command_streaming(upload_cmd, tag="add-show")
    elapsed = time.time() - t0

    if code == 0:
        log(f"Upload completed successfully in {elapsed:.1f}s.")
        clean_empty_directories(TARGET_DIR)
    else:
        log(f"Upload exited with code {code}.")

    # 4. Resume check
    log("Running mediagram resume check...")
    run_command_streaming(["mediagram", "resume"], tag="resume")
    log("Finished.")

if __name__ == "__main__":
    main()
