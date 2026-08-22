#!/bin/sh
set -eu

pet_id="${1:-}"
case "$pet_id" in
  ""|*[!a-z0-9-]*|-*|*-|*--*)
    echo "pet id must be a lowercase slug like claude-pixel" >&2
    exit 2
    ;;
esac

codex_home="${CODEX_HOME:-/workspace/.omnibot}"
pet_dir="$codex_home/pets/$pet_id"
manifest="$pet_dir/pet.json"
spritesheet="$pet_dir/spritesheet.webp"

test -f "$manifest" || {
  echo "missing $manifest" >&2
  exit 3
}
test -f "$spritesheet" || {
  echo "missing $spritesheet" >&2
  exit 3
}
test "$(wc -c < "$spritesheet")" -ge 12 || {
  echo "spritesheet.webp is empty" >&2
  exit 3
}

node - "$manifest" "$pet_id" <<'NODE'
const fs = require("node:fs");
const [manifestPath, expectedId] = process.argv.slice(2);
const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
if (manifest.id !== expectedId) {
  throw new Error(`pet.json id ${manifest.id} does not match ${expectedId}`);
}
if (manifest.spritesheetPath !== "spritesheet.webp") {
  throw new Error("pet.json spritesheetPath must be spritesheet.webp");
}
if (!String(manifest.displayName || "").trim()) {
  throw new Error("pet.json displayName is missing");
}
console.log(`Validated ${manifest.displayName} (${manifest.id})`);
NODE
