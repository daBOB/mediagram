#!/usr/bin/env bash
# Runs mediagram resume repeatedly until all pending sets are complete.

LOG_FILE="$HOME/.local/share/mediagram/resume_loop.log"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting resume loop for unfinished movies..." >> "$LOG_FILE"

while true; do
    # Check if any pending movie sets remain in library.db
    pending_count=$(sqlite3 "$HOME/.local/share/mediagram/library.db" "SELECT count(*) FROM sets WHERE status!='complete';")
    if [ "$pending_count" -eq 0 ]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] All pending sets are complete!" | tee -a "$LOG_FILE"
        break
    fi

    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $pending_count set(s) still pending. Running mediagram resume..." | tee -a "$LOG_FILE"
    mediagram resume >> "$LOG_FILE" 2>&1
    ec=$?

    if [ $ec -ne 0 ]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] mediagram resume exited with code $ec. Retrying in 5 seconds..." | tee -a "$LOG_FILE"
        sleep 5
    else
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] mediagram resume run finished cleanly." | tee -a "$LOG_FILE"
        sleep 2
    fi
done
