#!/bin/bash

for file in 14*.txt; do
  # Skip if no matching files
  [ -e "$file" ] || continue

  # Try to get creation time (birth time). Falls back to modification time if unavailable.
  if stat --format='%w' "$file" 2>/dev/null | grep -vq '^-$'; then
    # GNU stat with birth time
    timestamp=$(stat --format='%w' "$file")
  else
    # Fallback to modification time
    timestamp=$(stat --format='%y' "$file")
  fi

  # Format timestamp to desired format
  formatted=$(date -d "$timestamp" +"%Y-%m-%d_%H-%M-%S")

  # New filename
  newname="matches_${formatted}.log"

  # Rename file
  mv -n "$file" "$newname"

  echo "Renamed: $file -> $newname"
done