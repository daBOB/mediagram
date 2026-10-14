#!/usr/bin/env python3
"""
Dedicated runner for ingesting, preparing, and uploading series from /home/andre/TK08_2/Series.
Series queue:
1. Das Model und der Schnüffler (TMDB 1998)
2. Ein Colt für alle Fälle (TMDB 5031)
3. Sons of Anarchy (TMDB 1409)
4. Seinfeld (TMDB 1400)

Workflow per series:
1. mediagram prepare <dir> --mp4 --out <dir> --delete-source
2. mediagram add-show <dir> --tmdb <id> --yes --delete-source
3. Clean up empty directories
4. mediagram resume check
"""

import os
import sys
import time
import signal
import subprocess
from datetime import datetime

QUEUE = [
    {
        "name": "Das Model und der Schnüffler",
        "path": "/home/andre/TK08_2/Series/Das Model und der Schnüffler",
        "tmdb": 1998
    },
    {
        "name": "Ein Colt für alle Fälle",
        "path": "/home/andre/TK08_2/Series/Ein Colt für alle Fälle",
        "tmdb": 5031
    },
    {
        "name": "Sons of Anarchy",
        "path": "/home/andre/TK08_2/Series/Sons of Anarchy",
        "tmdb": 1409
    },
    {
        "name": "Seinfeld",
        "path": "/home/andre/TK08_2/Series/Seinfeld",
        "tmdb": 1400
    }
]

LOG_PATH = "/home/andre/.local/share/mediagram/tk08_2_series.log"
STATUS_PATH = "/home/andre/.local/share/mediagram/tk08_2_series_status.json"

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
                if not os.listdir(full):
                    os.rmdir(full)
        if os.path.exists(path) and not os.listdir(path):
            os.rmdir(path)
    except Exception as e:
        log(f"Cleanup note on {path}: {e}")

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

def main():
    log("Starting TK08_2 Series ingestion pipeline.")

    for idx, item in enumerate(QUEUE, 1):
        if not running:
            break

        name = item["name"]
        path = item["path"]
        tmdb = item["tmdb"]

        if not os.path.exists(path):
            log(f"[{idx}/{len(QUEUE)}] Skipping {name} — directory does not exist: {path}")
            continue

        mkv_count, mp4_count = count_local_videos(path)
        total_videos = mkv_count + mp4_count

        if total_videos == 0:
            clean_empty_directories(path)
            continue

        log(f"[{idx}/{len(QUEUE)}] PROCESSING: {name} (TMDB {tmdb}) — {mkv_count} source files to prepare, {mp4_count} mp4 prepared.")

        # If subdirectories exist (e.g. Season 04, Season 05, etc.), process per season!
        subdirs = sorted([
            os.path.join(path, d) for d in os.listdir(path)
            if os.path.isdir(os.path.join(path, d)) and not d.startswith(".")
        ])

        if subdirs:
            for s_dir in subdirs:
                if not running:
                    break
                s_name = os.path.basename(s_dir)
                s_mkv, s_mp4 = count_local_videos(s_dir)
                if s_mkv == 0 and s_mp4 == 0:
                    clean_empty_directories(s_dir)
                    continue

                log(f"[{idx}/{len(QUEUE)}] {name} / {s_name}: {s_mkv} mkv, {s_mp4} mp4")

                # Prepare if needed
                if s_mkv > 0:
                    log(f"  Preparing {s_name}...")
                    prep_cmd = ["mediagram", "prepare", s_dir, "--mp4", "--out", s_dir, "--delete-source"]
                    run_command_streaming(prep_cmd, tag="prepare")

                if not running:
                    break

                # Upload season immediately
                log(f"  Uploading {s_name}...")
                upload_cmd = ["mediagram", "add-show", s_dir, "--tmdb", str(tmdb), "--yes", "--delete-source"]
                code = run_command_streaming(upload_cmd, tag="add-show")
                if code == 0:
                    clean_empty_directories(s_dir)
                log("Running resume check...")
                run_command_streaming(["mediagram", "resume"], tag="resume")

            clean_empty_directories(path)
            continue

        # 1. Prepare if any non-mp4 files exist
        if mkv_count > 0:
            log(f"[{idx}/{len(QUEUE)}] Running prepare for {name}...")
            prep_cmd = [
                "mediagram",
                "prepare",
                path,
                "--mp4",
                "--out",
                path,
                "--delete-source"
            ]
            t0 = time.time()
            code = run_command_streaming(prep_cmd, tag="prepare")
            elapsed = time.time() - t0
            if code != 0:
                log(f"[{idx}/{len(QUEUE)}] Prepare finished with code {code} in {elapsed:.1f}s")
            else:
                log(f"[{idx}/{len(QUEUE)}] Prepare finished successfully in {elapsed:.1f}s")

        if not running:
            break

        # 2. Upload via add-show with --delete-source
        log(f"[{idx}/{len(QUEUE)}] Uploading {name} via mediagram add-show...")
        upload_cmd = [
            "mediagram",
            "add-show",
            path,
            "--tmdb",
            str(tmdb),
            "--yes",
            "--delete-source"
        ]
        t0 = time.time()
        code = run_command_streaming(upload_cmd, tag="add-show")
        elapsed = time.time() - t0

        if code == 0:
            log(f"[{idx}/{len(QUEUE)}] Upload finished successfully in {elapsed:.1f}s")
            clean_empty_directories(path)
        else:
            log(f"[{idx}/{len(QUEUE)}] Upload finished with code {code} in {elapsed:.1f}s")

        # 3. Interleaved Resume check
        log("Running resume check...")
        run_command_streaming(["mediagram", "resume"], tag="resume")

    log("TK08_2 Series pipeline finished.")

if __name__ == "__main__":
    main()
