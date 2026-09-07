#!/usr/bin/env bash

# format: "toml-key|groupPath|artifactId|host"
declare -a TARGETS=(
  "kordex-library|dev/kordex|annotations|https://snapshots-repo.kordex.dev"
  "kordex-data-api|dev/kordex|data/api|https://snapshots-repo.kordex.dev"
  "kord-core|dev/kord|kord-core|https://snapshots.kord.dev"
)

TOML_FILE="${1:-libs.versions.toml}"

if [[ ! -f "$TOML_FILE" ]]; then
  echo "Can't find $TOML_FILE. Pass the path as an argument." >&2
  exit 1
fi

for target in "${TARGETS[@]}"; do
  IFS='|' read -r key groupPath artifactId host <<< "$target"

  pinned=$(grep -E "^${key}[[:space:]]*=" "$TOML_FILE" | sed -E 's/.*"([^"]+)".*/\1/')
  if [[ -z "$pinned" ]]; then
    echo "== $key: not found in $TOML_FILE (skipping) =="
    continue
  fi

  base_version=$(echo "$pinned" | sed -E 's/-[0-9]{8}\.[0-9]{6}-[0-9]+$//')
  metadata_url="${host}/${groupPath}/${artifactId}/${base_version}-SNAPSHOT/maven-metadata.xml"

  echo "== $key =="
  echo "  pinned:   $pinned"
  echo "  fetching: $metadata_url"

  xml=$(curl -s -f "$metadata_url")
  if [[ $? -ne 0 || -z "$xml" ]]; then
    echo "  -> fetch failed (bad URL, wrong artifactId, or host unreachable)"
    echo
    continue
  fi

  latest_ts=$(echo "$xml" | grep -oE '<timestamp>[^<]+</timestamp>' | tail -1 | sed -E 's/<[^>]+>//g')
  latest_build=$(echo "$xml" | grep -oE '<buildNumber>[^<]+</buildNumber>' | tail -1 | sed -E 's/<[^>]+>//g')

  if [[ -z "$latest_ts" || -z "$latest_build" ]]; then
    echo "  -> couldn't parse timestamp/buildNumber from metadata. Raw response:"
    echo "$xml" | head -20
    echo
    continue
  fi

  latest_full="${base_version}-${latest_ts}-${latest_build}"

  if [[ "$latest_full" == "$pinned" ]]; then
    echo "  -> up to date"
  else
    echo "  -> NEW BUILD AVAILABLE: $latest_full"
  fi
  echo
done