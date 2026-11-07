#!/bin/zsh

set -euo pipefail

readonly version_properties="${SCRIPT_INPUT_FILE_0:?Missing gradle.properties input}"
readonly plist_template="${SCRIPT_INPUT_FILE_1:?Missing Info.plist template input}"
readonly plist_output="${SCRIPT_OUTPUT_FILE_0:?Missing generated Info.plist output}"

read_version_property() {
    awk -F= -v key="$1" '
        { sub(/\r$/, "") }
        $1 ~ "^[[:space:]]*" key "[[:space:]]*$" {
            value = $2
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
            print value
        }
    ' "$version_properties"
}

readonly version_name="$(read_version_property 'harmonic[.]versionName')"
readonly version_code="$(read_version_property 'harmonic[.]versionCode')"

# Reject missing, duplicate, or malformed values instead of shipping fallback metadata.
if [[ ! "$version_name" =~ '^[0-9]+\.[0-9]+(\.[0-9]+)?$' ]]; then
    print -u2 "Invalid harmonic.versionName in $version_properties: $version_name"
    exit 1
fi
if [[ ! "$version_code" =~ '^[1-9][0-9]*$' ]]; then
    print -u2 "Invalid harmonic.versionCode in $version_properties: $version_code"
    exit 1
fi

mkdir -p "${plist_output:h}"
readonly temporary_plist="$plist_output.tmp"
trap 'rm -f "$temporary_plist"' EXIT
cp "$plist_template" "$temporary_plist"
/usr/bin/plutil -insert CFBundleShortVersionString -string "$version_name" "$temporary_plist"
/usr/bin/plutil -insert CFBundleVersion -string "$version_code" "$temporary_plist"
mv "$temporary_plist" "$plist_output"
