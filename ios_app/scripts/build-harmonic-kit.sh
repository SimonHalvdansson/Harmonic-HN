#!/bin/zsh

set -euo pipefail

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    :
elif [[ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
elif [[ -x /usr/libexec/java_home ]] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
else
    print -u2 "A Java 21 runtime is required to build HarmonicKit. Set JAVA_HOME or install Android Studio/JDK 21."
    exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

readonly repository_root="$SRCROOT/.."
cd "$repository_root"
./gradlew :ui:embedAndSignAppleFrameworkForXcode

readonly framework_output="${SCRIPT_OUTPUT_FILE_0:?Xcode must declare the HarmonicKit framework output}"
if [[ ! -f "$framework_output" ]]; then
    print -u2 "Gradle did not produce the expected framework at $framework_output"
    exit 1
fi

# Xcode consumes this Make-style dependency file on the next build. It lets the shell phase stay
# incremental while still tracking every KMP source/resource and relevant Gradle input.
readonly dependency_file="$DERIVED_FILE_DIR/HarmonicKitInputs.d"
readonly temporary_dependency_file="$dependency_file.tmp"
mkdir -p "${dependency_file:h}"

escape_dependency_path() {
    print -rn -- "$1" | sed \
        -e 's/\\/\\\\/g' \
        -e 's/ /\\ /g' \
        -e 's/#/\\#/g' \
        -e 's/:/\\:/g' \
        -e 's/\$/$$/g'
}

tracked_files=(
    "$repository_root/build.gradle"
    "$repository_root/settings.gradle"
    "$repository_root/gradle.properties"
    "$repository_root/gradlew"
    "$repository_root/core/build.gradle.kts"
    "$repository_root/resources/build.gradle.kts"
    "$repository_root/ui/build.gradle.kts"
    "$SRCROOT/scripts/build-harmonic-kit.sh"
)
tracked_directories=(
    "$repository_root/gradle"
    "$repository_root/core/src"
    "$repository_root/resources/adblock"
    "$repository_root/resources/src"
    "$repository_root/ui/src"
)

{
    print -rn -- "$(escape_dependency_path "$framework_output"):"
    for input in "${tracked_files[@]}"; do
        [[ -f "$input" ]] || continue
        printf ' \\\n  %s' "$(escape_dependency_path "$input")"
    done
    for directory in "${tracked_directories[@]}"; do
        [[ -d "$directory" ]] || continue
        while IFS= read -r input; do
            printf ' \\\n  %s' "$(escape_dependency_path "$input")"
        done < <(find "$directory" -type f -print | LC_ALL=C sort)
    done
    print
} > "$temporary_dependency_file"
mv "$temporary_dependency_file" "$dependency_file"
