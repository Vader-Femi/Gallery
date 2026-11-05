#!/usr/bin/env bash
# Runs the app's pure-Kotlin unit tests (domain logic, tokenizer, search maths, ...) on the JVM, without Gradle.
# The tests use JUnit 4's API; here it is replaced by a tiny stand-in (stubs/junit.kt) and a reflection runner.
#
# Usage:  tools/verify/test.sh
# Prints failures and "passed=N failed=M"; exits 1 if any failed.
#
# Android-only behaviour is not reproduced (the JVM's regex engine differs from Android's ICU-based one, for example),
# so a pass here is necessary, not sufficient. Gradle's own `./gradlew testDebugUnitTest` remains the real run.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CACHE="${VERIFY_CACHE:-$HOME/.cache/gallery-verify}"
[ -x "$CACHE/kotlinc/bin/kotlinc" ] && [ -f "$CACHE/android-35.jar" ] || "$HERE/setup.sh" || exit 2

APP="$ROOT/app"
J="$APP/src/main/java/com/femi/gallery"
T="$APP/src/test/java/com/femi/gallery"
SOURCES=()
while IFS= read -r f; do SOURCES+=("$f"); done < <(find "$J/domain" -name '*.kt' -not -path '*/domain/repository/*' -not -path '*/domain/usecase/*')
for f in "$J"/presentation/stories/StoryTitles.kt "$J"/presentation/viewer/MediaStripMath.kt "$J"/presentation/viewer/ViewerDragMath.kt "$J"/presentation/viewer/DetailsFormat.kt "$J"/core/perf/FrameStats.kt "$J"/domain/repository/EditRepository.kt "$J"/domain/usecase/EditUseCases.kt "$J"/data/db/Rows.kt "$J"/data/db/Daos.kt; do
    [ -f "$f" ] && SOURCES+=("$f")
done
TESTS=()
for dir in domain presentation/stories presentation/viewer core/perf; do
    [ -d "$T/$dir" ] && while IFS= read -r f; do TESTS+=("$f"); done < <(find "$T/$dir" -name '*.kt')
done

OUT="$(mktemp -d)"
CP="$CACHE/android-35.jar:$CACHE/kotlinc/lib/kotlinx-coroutines-core-jvm.jar"
ERRORS="$(cd "$APP" && "$CACHE/kotlinc/bin/kotlinc" -nowarn -cp "$CP" "$HERE/stubs/junit.kt" "$HERE/stubs/Runner.kt" "$HERE/stubs/TestSupport.kt" "$HERE/stubs/TestSupport2.kt" "$HERE/stubs/libs/room.kt" "$HERE/stubs/libs/mlkit_face.kt" "${SOURCES[@]}" "${TESTS[@]}" -d "$OUT" 2>&1 | grep -E "error:" || true)"
if [ -n "$ERRORS" ]; then echo "$ERRORS"; rm -rf "$OUT"; exit 1; fi

CLASSES=()
for f in "${TESTS[@]}"; do
    pkg="$(grep -m1 '^package' "$f" | awk '{print $2}')"
    while IFS= read -r c; do CLASSES+=("$pkg.$c"); done < <(grep -oE '^class [A-Za-z0-9]*Test[A-Za-z0-9]*' "$f" | awk '{print $2}')
done
# The tokenizer test reads the vocabulary relative to the app module.
RESULT="$(cd "$APP" && java -cp "$OUT:$CACHE/kotlinc/lib/kotlin-stdlib.jar:$CACHE/kotlinc/lib/kotlinx-coroutines-core-jvm.jar:$CACHE/android-35.jar" RunnerKt "${CLASSES[@]}" 2>&1)"
rm -rf "$OUT"
echo "$RESULT"
echo "$RESULT" | grep -q "failed=0" && exit 0 || exit 1
