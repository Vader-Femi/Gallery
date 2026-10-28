#!/usr/bin/env bash
# Measures cold start time of the installed app with the two ways Android can have compiled it:
# "verify" (almost nothing compiled ahead of time, like a fresh install) and "speed" (everything
# compiled). If "speed" is much faster, a baseline profile will give that speed to every install.
#
# Usage:  bash tools/startup_bench.sh [runs]      (default 10 runs per mode; phone connected with adb)

PKG=com.femi.gallery
ACTIVITY=$PKG/.MainActivity
RUNS=${1:-10}

measure() {
    local mode=$1
    adb shell cmd package compile -m "$mode" -f "$PKG" > /dev/null || { echo "compile -m $mode failed"; return 1; }
    local times=() warm=0
    for _ in $(seq 1 "$RUNS"); do
        # A real cold start needs the process to be gone first; a start of an app that is still alive is WARM or HOT.
        adb shell am force-stop "$PKG"
        sleep 2
        local out
        out=$(adb shell am start -W -n "$ACTIVITY" | tr -d '\r')
        echo "$out" | grep -q "LaunchState: COLD" || warm=$((warm + 1))
        times+=("$(echo "$out" | awk '/TotalTime/ {print $2}')")
    done
    local sorted
    sorted=$(printf '%s\n' "${times[@]}" | sort -n)
    local median
    median=$(echo "$sorted" | awk '{a[NR]=$1} END {print a[int((NR+1)/2)]}')
    printf '%-8s median %4s ms   min %4s   max %4s   (runs: %s)\n' "$mode" "$median" \
        "$(echo "$sorted" | head -1)" "$(echo "$sorted" | tail -1)" "$(echo "$sorted" | tr '\n' ' ')"
    [ "$warm" -gt 0 ] && echo "  note: $warm run(s) were not cold starts; do not trust them"
}

measure verify
measure speed
