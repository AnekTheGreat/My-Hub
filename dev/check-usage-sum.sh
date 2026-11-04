#!/usr/bin/env sh
# Runs dev/usage-sum-check.kt against the shipped UsageSum.kt, on the JVM.
#
# It borrows the Kotlin compiler and stdlib that Gradle already downloaded, so the check needs no
# dependency of its own and nothing extra is added to the APK. Run it after touching the time
# arithmetic, and before shipping a build.
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$HERE/.." && pwd)
CACHE=${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java

findjar() {
    find "$CACHE/org.jetbrains.kotlin/$1" -name "$1-*.jar" ! -name '*sources*' 2>/dev/null \
        | sort | tail -1
}

COMPILER=$(findjar kotlin-compiler-embeddable)
# The stdlib has to be the compiler's OWN version: the Gradle cache can hold several (a Kotlin 2.x
# stdlib sitting beside this 1.9 one is unreadable to a 1.9 compiler, and "newest" then picks it).
KOTLIN=${COMPILER##*/kotlin-compiler-embeddable-}
KOTLIN=${KOTLIN%.jar}
STDLIB=$(findjar kotlin-stdlib)
if [ -n "$KOTLIN" ]; then
    STDLIB=$(find "$CACHE/org.jetbrains.kotlin/kotlin-stdlib/$KOTLIN" -name "kotlin-stdlib-$KOTLIN.jar" 2>/dev/null \
        | sort | tail -1)
fi
# The embeddable compiler keeps its collections in trove; without it the compiler itself dies.
TROVE=$(find "$CACHE/org.jetbrains.intellij.deps/trove4j" -name 'trove4j-*.jar' 2>/dev/null | sort | tail -1)
ANNOT=$(find "$CACHE/org.jetbrains/annotations" -name 'annotations-*.jar' 2>/dev/null | sort | tail -1)
# Kotlin 2.x's compiler calls into kotlinx.coroutines, which does not live inside the embeddable
# jar: without it the compiler dies with NoClassDefFoundError on kotlinx/coroutines/CoroutineScope
# before it has read a line of either source file.
CORO=$(find "$CACHE/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm" \
    -name 'kotlinx-coroutines-core-jvm-*.jar' ! -name '*sources*' 2>/dev/null | sort | tail -1)
if [ -z "$COMPILER" ] || [ -z "$STDLIB" ] || [ -z "$TROVE" ] || [ -z "$ANNOT" ] || [ -z "$CORO" ]; then
    echo "usage-sum: no Kotlin compiler in the Gradle cache - run a Gradle build once first" >&2
    exit 2
fi

OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

"$JAVA" -cp "$COMPILER:$STDLIB:$TROVE:$ANNOT:$CORO" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -nowarn -no-reflect -no-stdlib -classpath "$STDLIB" -d "$OUT" \
    "$ROOT/app/src/main/java/com/myhub/app/UsageSum.kt" "$HERE/usage-sum-check.kt"

"$JAVA" -cp "$OUT:$STDLIB" Usage_sum_checkKt
