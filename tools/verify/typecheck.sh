#!/usr/bin/env bash
# Type-checks the parts of the app that do not need Compose, Hilt's generated code or the Gradle build: the domain layer,
# the index database, the MediaStore data source, the scans, the CLIP code and the settings store. It compiles them
# against a real android.jar, kotlinx.coroutines and small stubs of the other libraries (Hilt, Room, DataStore, WorkManager,
# ONNX Runtime, ...), so a wrong function name, a missing import or a type mismatch is found without building the app.
#
# Usage:  tools/verify/typecheck.sh [more .kt files, relative to the repository root]
# Prints the compiler's errors; prints "typecheck: clean" and exits 0 when there are none.
#
# It does NOT check: Compose screens, Room's SQL (Room validates that while building), Hilt wiring, resources, or
# anything that behaves differently on a phone (for instance Android's regex engine is not the JVM's).
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CACHE="${VERIFY_CACHE:-$HOME/.cache/gallery-verify}"
[ -x "$CACHE/kotlinc/bin/kotlinc" ] && [ -f "$CACHE/android.jar" ] || "$HERE/setup.sh" || exit 2

J="$ROOT/app/src/main/java/com/femi/gallery"
FILES=()
add() { for f in "$@"; do [ -f "$f" ] && FILES+=("$f"); done; }

while IFS= read -r f; do FILES+=("$f"); done < <(find "$J/domain" -name '*.kt')
add "$J"/di/ScopeModule.kt "$J"/di/DispatcherModule.kt "$J"/di/DatabaseModule.kt
for dir in data/db data/duplicates data/story data/clip; do
    [ -d "$J/$dir" ] && while IFS= read -r f; do FILES+=("$f"); done < <(find "$J/$dir" -name '*.kt')
done
add "$J"/data/mediastore/MediaStoreDataSource.kt "$J"/data/repository/ClipIndexRepositoryImpl.kt "$J"/data/edit/BitmapEditor.kt "$J"/core/lifecycle/*.kt
add "$J"/core/permission/MediaPermissions.kt "$J"/core/lifecycle/AppForeground.kt "$J"/presentation/settings/SettingsDataStore.kt "$J"/presentation/pictures/TimelineZoom.kt
add "$J"/data/color/*IndexHolder.kt "$J"/data/label/*IndexHolder.kt "$J"/data/map/*IndexHolder.kt
add "$J"/data/text/*IndexHolder.kt "$J"/data/location/*Holder.kt "$J"/data/location/LocationIndex*.kt
for extra in "$@"; do add "$ROOT/$extra"; done

OUT="$(mktemp -d)"
RESULT="$("$CACHE/kotlinc/bin/kotlinc" -nowarn \
    -cp "$CACHE/android.jar:$CACHE/kotlinc/lib/kotlinx-coroutines-core-jvm.jar" \
    "$HERE"/stubs/libs/*.kt $(find "$HERE/stubs/java" -name '*.java') "${FILES[@]}" -d "$OUT" 2>&1 | grep -E "error:" || true)"
rm -rf "$OUT"
if [ -z "$RESULT" ]; then echo "typecheck: clean (${#FILES[@]} files)"; exit 0; fi
echo "$RESULT"
exit 1
