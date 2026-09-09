#!/usr/bin/env bash
# Check actual maintained project inputs, not retired reports or naming scores.
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"
for path in settings.gradle build.gradle app/build.gradle gradlew gradle/wrapper/gradle-wrapper.properties README.md README_DEV.md CLAUDE.md; do
  if [[ ! -f "$path" ]]; then
    echo "Missing project input: $path" >&2
    exit 1
  fi
done
python3 scripts/check_repository.py
printf '%s\n' 'Project files and documentation passed. Build validation: ./gradlew :app:testDebugUnitTest :app:assembleDebug'
