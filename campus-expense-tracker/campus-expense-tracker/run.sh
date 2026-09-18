#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
task="${1:-run}"
if [ "$#" -gt 0 ]; then shift; fi
case "$task" in build|run|test) ;; *) echo 'Usage: bash run.sh [build|run|test] [application options]' >&2; exit 2;; esac
if [ -n "${JAVA_HOME:-}" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
else
    JAVAC=javac
    JAVA=java
fi
mkdir -p build/classes
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -Xlint:all,-options -d build/classes @sources.txt
case "$task" in
    build) echo 'Build successful.' ;;
    run) "$JAVA" -cp build/classes campus.Main "$@" ;;
    test)
        mkdir -p build/test-classes
        "$JAVAC" -source 8 -target 8 -encoding UTF-8 -Xlint:all,-options -cp build/classes -d build/test-classes src/test/java/campus/TrackerTests.java
        classpath='build/classes:build/test-classes'
        case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) classpath='build/classes;build/test-classes';; esac
        "$JAVA" -cp "$classpath" campus.TrackerTests
        ;;
esac
