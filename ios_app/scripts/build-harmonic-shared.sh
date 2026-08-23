#!/bin/zsh

set -euo pipefail

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    :
elif [[ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
elif [[ -x /usr/libexec/java_home ]] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
else
    print -u2 "A Java 21 runtime is required to build HarmonicShared. Set JAVA_HOME or install Android Studio/JDK 21."
    exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

readonly repository_root="$SRCROOT/.."
cd "$repository_root"
./gradlew :shared_ui:embedAndSignAppleFrameworkForXcode

readonly framework_output="${SCRIPT_OUTPUT_FILE_0:?Xcode must declare the HarmonicShared framework output}"
if [[ ! -f "$framework_output" ]]; then
    print -u2 "Gradle did not produce the expected framework at $framework_output"
    exit 1
fi

# Xcode consumes this Make-style dependency file on the next build. It lets the shell phase stay
# incremental while still tracking every shared source/resource and relevant Gradle input.
readonly dependency_file="$DERIVED_FILE_DIR/HarmonicSharedInputs.d"
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
    "$repository_root/shared_logic/build.gradle.kts"
    "$repository_root/shared_resources/build.gradle.kts"
    "$repository_root/shared_ui/build.gradle.kts"
    "$SRCROOT/scripts/build-harmonic-shared.sh"
)
tracked_directories=(
    "$repository_root/gradle"
    "$repository_root/shared_logic/src"
    "$repository_root/shared_resources/adblock"
    "$repository_root/shared_resources/src"
    "$repository_root/shared_ui/src"
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
