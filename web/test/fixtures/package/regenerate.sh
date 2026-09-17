#!/usr/bin/env bash
# Regenerates the round-trip fixture by running the real exporter.
#
# The point of the fixture is that the bytes come from the Rust side. A
# package written by the TypeScript tests would only prove the reader agrees
# with itself, and the two implementations of one format are exactly what
# needs checking.
#
# The library it describes is synthetic: made-up chat and message ids, two
# TMDB ids that happen to be in the local poster cache. Nothing here names a
# real channel, and the key is a fixture key for fixture data.
#
# Needs: cargo, a warm ~/.local/share/mediagram/tmdb-cache, and network for
# the poster images. Run from anywhere.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# A fixture key, in the repository on purpose: it seals fixture data.
KEY="c2VjcmV0LWZpeHR1cmUta2V5LTMyLWJ5dGVzLTEyMzQ="
LIVE="${MEDIAGRAM_LIVE_DB:-$HOME/.local/share/mediagram/library.db}"

mkdir -p "$WORK/data" "$WORK/served"

# The schema comes from the live index so the fixture cannot drift from it;
# every row is written here, so none of the live library travels.
bun -e '
const { Database } = require("bun:sqlite");
const live = new Database(process.argv[1], { readonly: true });
const schema = live
  .query("select sql from sqlite_master where sql is not null")
  .all()
  .map((r) => r.sql);
live.close();

const db = new Database(process.argv[2], { create: true });
for (const sql of schema) db.run(sql);

db.run("INSERT INTO meta(key, value) VALUES(?, ?)", ["schema_version", "4"]);
const sets = [
  ["01FIXTURE0000000000000001", "movie", 36648, null, "Blade: Trinity", 2004, "mkv", "hevc", "ac3", 7341],
  ["01FIXTURE0000000000000002", "ep", 240459, "Spartacus", "Dominus", 2025, "mp4", "h264", "aac", 2760],
];
for (const [id, kind, tmdb, show, title, year, container, vcodec, acodec, duration] of sets) {
  db.run(
    `INSERT INTO sets(set_id, kind, tmdb, show, title, year, season, episode, container,
                      vcodec, acodec, duration, total, part_count, status, created_at, spec_version)
     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, "complete", 1700000000, 4)`,
    [id, kind, tmdb, show, title, year, kind === "ep" ? 1 : null, kind === "ep" ? "1" : null,
     container, vcodec, acodec, duration, 1024 * 1024],
  );
  db.run(
    `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, sha256, status)
     VALUES(?, 0, 0, ?, -1000000000001, 42, ?, "done")`,
    [id, 1024 * 1024, "0".repeat(64)],
  );
}
db.close();
console.log("wrote a synthetic index with " + sets.length + " sets");
' "$LIVE" "$WORK/data/library.db"

# Posters resolve from the cache rather than from an API key.
cp -r "$(dirname "$LIVE")/tmdb-cache" "$WORK/data/" 2>/dev/null || true

cat > "$WORK/config.toml" <<TOML
api_id = 1
api_hash = "unused-by-export"
channel = "unused-by-export"
data_dir = "$WORK/data"
tmdb_language = "de-DE"
package_key = "$KEY"
publish_base_url = "https://packages.example.test/mediagram"
publish_cmd = ["cp", "{file}", "$WORK/served/"]
TOML

cd "$REPO"
cargo run --quiet --locked -- --config "$WORK/config.toml" \
  export-package --out "$WORK/stage" --publish

rm -f "$HERE"/*.tar.gz.enc "$HERE"/latest.json
cp "$WORK/served/latest.json" "$HERE/"
cp "$WORK/served"/*.tar.gz.enc "$HERE/"
echo "$KEY" > "$HERE/key.base64"

echo
echo "fixture in $HERE:"
ls -la "$HERE"
