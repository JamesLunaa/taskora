#!/usr/bin/env bash
# Starts Taskora on its bundled Java runtime, with the classpath and options from the
# jpackage config (lib/app/taskora.cfg). Replaces jpackage's native launcher, whose helper
# process aborts while exiting ("pure virtual method called") and leaves a core dump on every
# start. build.gradle.kts copies this over bin/taskora after createDistributable.
set -euo pipefail

self=$(readlink -f "$0")
root=$(dirname "$(dirname "$self")")
app=$root/lib/app

classpath=()
options=()
main=
while IFS= read -r line || [ -n "$line" ]; do
    line=${line//\$APPDIR/$app}
    case $line in
        app.classpath=*) classpath+=("${line#app.classpath=}") ;;
        app.mainclass=*) main=${line#app.mainclass=} ;;
        java-options=*) options+=("${line#java-options=}") ;;
    esac
done < "$app/taskora.cfg"

IFS=:
# jpackage.app-path is what autostart puts in its Exec line.
exec "$root/lib/runtime/bin/java" "${options[@]}" -Djpackage.app-path="$self" \
    -cp "${classpath[*]}" "$main" "$@"
