#!/usr/bin/env bash
set -euo pipefail

# Use the repository root even when invoked from another directory.
cd "$(dirname "${BASH_SOURCE[0]}")"
fail() { echo "ERROR: $*" >&2; exit 1; }
mode="${1:-run}"
case "$mode" in
  run|--check|--build-only) ;;
  *) fail "Usage: ./start.sh [--check|--build-only]" ;;
esac

# System tools are prerequisites; Maven installs the project dependencies.
for tool in java javac mvn sed sort head; do
  command -v "$tool" >/dev/null 2>&1 ||
    fail "Missing $tool. Install JDK 21 and Maven 3.6.3+; see README.md."
done
java_info="$(java -version 2>&1)"
javac_info="$(javac -version 2>&1)"
[[ "$java_info" =~ version\ \"21[.\"] ]] || fail "Java 21 is required. Check java -version."
[[ "$javac_info" =~ javac\ 21([.]|$) ]] || fail "JDK 21 is required. Check javac -version."
maven_info="$(mvn -version 2>&1)" || fail "Maven could not start. Check JAVA_HOME and mvn -version."
maven_info="$(printf '%s\n' "$maven_info" | sed $'s/\033\\[[0-9;]*m//g')"
[[ "$maven_info" =~ Java\ version:\ 21([.,]|$) ]] ||
  fail "Maven must use JDK 21. Check JAVA_HOME and mvn -version."
maven_version="$(printf '%s\n' "$maven_info" | sed -n 's/^Apache Maven \([0-9.]*\).*/\1/p')"
[[ -n "$maven_version" ]] || fail "Cannot read Maven version."
[[ "$(printf '%s\n' 3.6.3 "$maven_version" | sort -V | head -n 1)" == 3.6.3 ]] ||
  fail "Maven 3.6.3 or newer is required."
echo "Prerequisites OK: JDK 21 and Maven $maven_version."
[[ "$mode" == --check ]] && exit 0

echo "Downloading dependencies and building the application..."
mvn -B -ntp -DskipTests clean package
# This Hello World checkpoint needs no database, schema, or seed data.
[[ "$mode" == --build-only ]] && exit 0
echo "Starting Trench Crusade Roster Builder."
echo "Open http://localhost:8080 after Spring reports that it has started."
echo "Press Ctrl+C to stop."
exec java -jar target/trench-roster.jar
