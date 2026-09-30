#!/usr/bin/env bash
# Reads what a title's mp4 actually carries once it sits in the channel, by
# probing only its head through a loopback `mediagram serve` — no download,
# no send, no pin — and compares that against the audio languages
# library.db recorded when the file first went up. A gap here means the
# remux that ran before subtitle streams were mapped also dropped an audio
# track, silently, on some earlier upload.
#
# Usage: uploaded-stream-headers.sh audio [library.db path]
#   audio   the only mode today: one TSV row per complete mp4 movie/ep/docu
#           set with more than one source audio language, then a totals
#           line. A subtitle mode is a later addition to this same script.
#
# Requires: an installed `mediagram` on PATH, `mediagram pull-index` already
# run on this machine, sqlite3, ffprobe, jq, curl, and no upload already
# running here — the Telegram session is one account's and shared.
set -euo pipefail

mode="${1:-}"
if [[ "$mode" != "audio" ]]; then
  echo "usage: $(basename "$0") audio [library.db path]" >&2
  echo "only the 'audio' mode is implemented" >&2
  exit 1
fi

db="${2:-${MEDIAGRAM_DATA_DIR:-$HOME/.local/share/mediagram}/library.db}"
addr="127.0.0.1:8799"
base_url="http://$addr"

for tool in mediagram sqlite3 ffprobe jq curl; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "missing '$tool' on PATH" >&2
    exit 1
  }
done
[[ -f "$db" ]] || {
  echo "no library.db at $db; run 'mediagram pull-index' first" >&2
  exit 1
}

# The Telegram session backing `mediagram serve` is the same one these
# commands use to send bytes; running beside them risks stepping on an
# in-flight upload rather than just reading past it.
if pgrep -f 'mediagram (add|add-course|add-show|add-docu|resume|finish-set)' >/dev/null 2>&1; then
  echo "an upload looks to be running on this machine; rerun once it finishes" >&2
  exit 1
fi

serve_pid=""
serve_log="$(mktemp)"
cleanup() {
  [[ -n "$serve_pid" ]] && kill "$serve_pid" 2>/dev/null || true
  rm -f "$serve_log"
}
trap cleanup EXIT

mediagram serve --addr "$addr" >"$serve_log" 2>&1 &
serve_pid=$!

ready=0
for _ in $(seq 1 20); do
  if curl -fsS -o /dev/null "$base_url/sets" 2>/dev/null; then
    ready=1
    break
  fi
  sleep 0.5
done
if [[ "$ready" -ne 1 ]]; then
  echo "mediagram serve did not come up on $addr:" >&2
  cat "$serve_log" >&2
  exit 1
fi

# Mirrors crates/mediagram/src/media/classify.rs's `lang_code`: ISO 639-2 (or
# already 639-1) to 639-1, `und`/empty dropped, an unrecognised tag passed
# through rather than lost.
lang_code_jq='
def lang_code:
  if . == null then null
  elif (. | length) == 0 then null
  elif (. | ascii_downcase) == "und" then null
  elif (. | length) == 2 then (. | ascii_downcase)
  else
    (. | ascii_downcase) as $l
    | {eng: "en", deu: "de", ger: "de", fra: "fr", fre: "fr", spa: "es",
       ita: "it", jpn: "ja", por: "pt", rus: "ru", kor: "ko", zho: "zh",
       chi: "zh", nld: "nl", dut: "nl", swe: "sv", dan: "da", nor: "no",
       fin: "fi", pol: "pl", tur: "tr", ara: "ar", hin: "hi"}[$l] // $l
  end;
'

rows=()
total=0
short=0

while IFS= read -r row; do
  set_id=$(jq -r '.set_id' <<<"$row")
  kind=$(jq -r '.kind' <<<"$row")
  title=$(jq -r '.title' <<<"$row")
  alang=$(jq -c '.alang | fromjson' <<<"$row")

  probe=$(ffprobe -v error -probesize 65536 -analyzeduration 0 -of json \
    -show_entries stream=codec_type,codec_name:stream_tags=language \
    "$base_url/sets/$set_id/stream" 2>/dev/null || echo '{"streams":[]}')

  line=$(jq -r "$lang_code_jq"'
      ( [.streams[] | select(.codec_type == "audio") | (.tags.language // null) | lang_code]
        | map(select(. != null)) | unique ) as $uploaded
      | ($ALANG | map(lang_code) | map(select(. != null)) | unique) as $expected
      | [$SET, $KIND, $TITLE, ($expected | join(",")), ($uploaded | join(",")),
         (($expected - $uploaded) | join(","))]
      | @tsv
    ' --argjson ALANG "$alang" --arg SET "$set_id" --arg KIND "$kind" --arg TITLE "$title" \
    <<<"$probe")

  rows+=("$line")
  total=$((total + 1))
  [[ -n "$(cut -f6 <<<"$line")" ]] && short=$((short + 1))
done < <(sqlite3 -readonly -json "$db" "
    SELECT set_id, kind, title, alang
    FROM sets
    WHERE status = 'complete' AND container = 'mp4'
      AND kind IN ('movie', 'ep', 'docu')
      AND json_array_length(alang) > 1
    ORDER BY set_id
  " | jq -c '.[]')

printf 'set_id\tkind\ttitle\talang\tuploaded_audio\tmissing_audio\n'
if [[ "$total" -gt 0 ]]; then
  printf '%s\n' "${rows[@]}"
fi
echo "# totals: $total candidate sets checked, $short missing at least one uploaded audio language"
