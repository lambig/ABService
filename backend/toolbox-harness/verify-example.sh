#!/bin/sh
# Build the candidate, then exercise only its POM/JAR from a separate project.
set -eu
module=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT HUP INT TERM
sh "$module/../gradlew" -p "$module" --no-daemon --console=plain clean test publishMavenJavaPublicationToCandidateRepository
logs="$module/build/consumer-results"
mkdir -p "$logs"
cp -R "$module/example/." "$scratch/"
repository="file:$module/build/repository"
run() {
    if [ -n "${BOUNDARY_JAVA_HOME:-}" ]; then
        sh "$scratch/gradlew" -p "$scratch" --no-daemon --console=plain \
            "-PharnessRepository=$repository" "-PboundaryJavaHome=$BOUNDARY_JAVA_HOME" \
            "-Pscenario=$1" clean check
    else
        sh "$scratch/gradlew" -p "$scratch" --no-daemon --console=plain \
            "-PharnessRepository=$repository" "-Pscenario=$1" clean check
    fi
}
run allowed > "$logs/allowed.log" 2>&1 || { cat "$logs/allowed.log"; exit 1; }
for scenario in direct reference; do
    if run "$scenario" > "$logs/$scenario.log" 2>&1; then
        echo "ERROR: $scenario unexpectedly passed"
        exit 1
    fi
    # A dependency or compilation failure is not proof of violation detection.
    grep 'expected caller class' "$logs/$scenario.log"
    grep 'example.shipping.Client' "$logs/$scenario.log" >/dev/null
    grep 'Shipment.dispatch' "$logs/$scenario.log" >/dev/null
done
run fixed > "$logs/fixed.log" 2>&1 || { cat "$logs/fixed.log"; exit 1; }
grep 'H1 boundary passed' "$logs/allowed.log"
grep 'H1 boundary passed' "$logs/fixed.log"
echo 'H1: allowed PASS; direct REJECTED; reference REJECTED; fixed PASS; runtime isolated'
