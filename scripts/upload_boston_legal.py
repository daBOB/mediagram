#!/usr/bin/env python3
"""
Dedicated priority uploader for Boston Legal (TMDB 4598) Seasons 2-5.
Uploads each season immediately using:
    mediagram add-show <season_dir> --tmdb 4598 --yes --delete-source
And deletes empty folders upon completion.
"""

import os
import sys
import time
import subprocess
from datetime import datetime

SHOW_DIR = "/home/andre/TK08/Videos/xstream/Boston Legal"
TMDB_ID = 4598
LOG_FILE = "/home/andre/.local/share/mediagram/boston_legal_upload.log"

def log(msg):
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    line = f"[{ts}] {msg}"
    print(line, flush=True)
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass

def run_cmd(cmd, tag="add-show"):
    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1
    )
    if proc.stdout:
        for line in iter(proc.stdout.readline, ""):
            line_str = line.strip()
            if line_str:
                log(f"  {tag}: {line_str}")
        proc.stdout.close()
    proc.wait()
    return proc.returncode

def main():
    log("=== Starting Boston Legal Dedicated Priority Upload ===")
    if not os.path.exists(SHOW_DIR):
        log(f"Error: {SHOW_DIR} does not exist.")
        return

    seasons = sorted([
        d for d in os.listdir(SHOW_DIR)
        if os.path.isdir(os.path.join(SHOW_DIR, d)) and not d.startswith(".")
    ])

    for s in seasons:
        s_path = os.path.join(SHOW_DIR, s)
        files = [f for f in os.listdir(s_path) if f.endswith(".mp4")]
        if not files:
            log(f"Skipping {s}: no mp4 files found.")
            continue

        log(f"Uploading {s} ({len(files)} mp4 episodes)...")
        cmd = [
            "mediagram",
            "add-show",
            s_path,
            "--tmdb",
            str(TMDB_ID),
            "--yes",
            "--delete-source"
        ]
        t0 = time.time()
        code = run_cmd(cmd, tag="add-show")
        elapsed = time.time() - t0

        if code == 0:
            log(f"FINISHED {s} in {elapsed:.1f}s")
            try:
                if not os.listdir(s_path):
                    os.rmdir(s_path)
            except Exception:
                pass
        else:
            log(f"WARNING: {s} exited with code {code} in {elapsed:.1f}s")

        # Resume check
        run_cmd(["mediagram", "resume"], tag="resume")

    # Final cleanup
    try:
        if os.path.exists(SHOW_DIR) and not os.listdir(SHOW_DIR):
            os.rmdir(SHOW_DIR)
            log(f"Cleaned up empty show dir: {SHOW_DIR}")
    except Exception:
        pass

    log("=== Boston Legal Upload Complete! ===")

if __name__ == "__main__":
    main()
