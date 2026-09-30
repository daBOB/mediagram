#!/usr/bin/env bash
# Reads what a title's uploaded copy actually carries, by probing only its
# head through a loopback `mediagram serve` — no download, no send, no pin.
#
# Usage: uploaded-stream-headers.sh audio|subs [library.db path]
#   audio   one TSV row per complete mp4 movie/ep/docu set with more than one
#           source audio language, comparing that against what the upload
#           actually carries. A gap means the remux that ran before subtitle
#           streams were mapped also dropped an audio track, silently, on
#           some earlier upload.
#   subs    one TSV row per complete movie/ep/docu set whose `slang` lists
#           de or en, counting the upload's own de/en text subtitle tracks
#           and picture-only (PGS/VobSub/DVB/xsub) tracks. Paired with
#           `mediagram subtitles backfill --dry-run`'s source-side count, so
#           a title's row in each report says whether it already has
#           subtitles in the channel, could gain them from a local source,
#           or neither.
#
# Requires: an installed `mediagram` on PATH, `mediagram pull-index` already
# run on this machine, sqlite3, ffprobe, jq, curl, and no upload already
# running here — the Telegram session is one account's and shared.
set -euo pipefail

mode="${1:-}"
if [[ "$mode" != "audio" && "$mode" != "subs" ]]; then
  echo "usage: $(basename "$0") audio|subs [library.db path]" >&2
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

probe_set() {
  ffprobe -v error -probesize 65536 -analyzeduration 0 -of json \
    -show_entries stream=codec_type,codec_name:stream_tags=language \
    "$base_url/sets/$1/stream" 2>/dev/null || echo '{"streams":[]}'
}

rows=()
total=0
flagged=0

if [[ "$mode" == "audio" ]]; then
  while IFS= read -r row; do
    set_id=$(jq -r '.set_id' <<<"$row")
    kind=$(jq -r '.kind' <<<"$row")
    title=$(jq -r '.title' <<<"$row")
    alang=$(jq -c '.alang | fromjson' <<<"$row")

    probe=$(probe_set "$set_id")
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
    [[ -n "$(cut -f6 <<<"$line")" ]] && flagged=$((flagged + 1))
  done < <(sqlite3 -readonly -json "$db" "
      SELECT set_id, kind, title, alang
      FROM sets
      WHERE status = 'complete' AND container = 'mp4'
        AND kind IN ('movie', 'ep', 'docu')
        AND json_array_length(alang) > 1
      ORDER BY set_id
    " | jq -c '.[]')

  printf 'set_id\tkind\ttitle\talang\tuploaded_audio\tmissing_audio\n'
  [[ "$total" -gt 0 ]] && printf '%s\n' "${rows[@]}"
  echo "# totals: $total candidate sets checked, $flagged missing at least one uploaded audio language"
else
  # Subtitle codecs that are images rather than text — mirrors
  # crates/mediagram/src/media/prepare/plan.rs's `PICTURE_SUBTITLES`.
  picture_jq='["hdmv_pgs_subtitle","dvd_subtitle","dvb_subtitle","xsub"]'

  while IFS= read -r row; do
    set_id=$(jq -r '.set_id' <<<"$row")
    kind=$(jq -r '.kind' <<<"$row")
    title=$(jq -r '.title' <<<"$row")
    container=$(jq -r '.container' <<<"$row")

    probe=$(probe_set "$set_id")
    line=$(jq -r "$lang_code_jq"'
        (.streams | map(select(.codec_type == "subtitle"))) as $subs
        | ([$subs[] | select(.codec_name as $c | '"$picture_jq"' | index($c) != null)] | length) as $picture
        | ([$subs[] | select(.codec_name as $c | ('"$picture_jq"' | index($c)) == null)
            | (.tags.language // null) | lang_code | select(. == "de" or . == "en")] | length) as $text
        | [$SET, $KIND, $TITLE, $CONTAINER, $text, $picture]
        | @tsv
      ' --arg SET "$set_id" --arg KIND "$kind" --arg TITLE "$title" --arg CONTAINER "$container" \
      <<<"$probe")

    rows+=("$line")
    total=$((total + 1))
    [[ "$(cut -f5 <<<"$line")" -gt 0 ]] && flagged=$((flagged + 1))
  done < <(sqlite3 -readonly -json "$db" "
      SELECT set_id, kind, title, container
      FROM sets
      WHERE status = 'complete' AND kind IN ('movie', 'ep', 'docu')
        AND EXISTS (SELECT 1 FROM json_each(sets.slang) WHERE value IN ('de', 'en'))
      ORDER BY set_id
    " | jq -c '.[]')

  printf 'set_id\tkind\ttitle\tcontainer\tde_en_text\tpicture_only\n'
  [[ "$total" -gt 0 ]] && printf '%s\n' "${rows[@]}"
  echo "# totals: $total de/en-subtitled sets checked, $flagged already carry de/en text in the channel"
fi
