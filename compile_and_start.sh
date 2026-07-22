#!/usr/bin/env bash
set -euo pipefail

sbt "Compile / fastOptJS / webpack"

echo "Server starting on http://localhost:${PORT:-8000}"
python3 -m http.server "${PORT:-8000}"
