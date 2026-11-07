#!/bin/zsh

set -euo pipefail

# The pinned XCFramework has no manifest. Its required-reason API declarations must
# live in the framework bundle itself, separately from Harmonic's declarations.
readonly framework="${TARGET_BUILD_DIR:?}/${FRAMEWORKS_FOLDER_PATH:?}/CLiteRTLM.framework"
readonly manifest="$SRCROOT/LiteRTLM/PrivacyInfo.xcprivacy"
[[ -f "$framework/CLiteRTLM" ]] || { print -u2 "Missing embedded LiteRT framework"; exit 1; }
/usr/bin/plutil -lint "$manifest"
/bin/cp "$manifest" "$framework/PrivacyInfo.xcprivacy"

# SwiftPM's embed step signs its copy. Adding a resource changes that signature,
# so re-sign the built copy before Xcode signs the containing app. Never modify
# the downloaded package artifact or its upstream signature/checksum.
if [[ "${CODE_SIGNING_ALLOWED:-YES}" == YES && -n "${EXPANDED_CODE_SIGN_IDENTITY:-}" ]]; then
    /usr/bin/codesign --force --sign "$EXPANDED_CODE_SIGN_IDENTITY" \
        --preserve-metadata=identifier,entitlements,flags --timestamp=none "$framework"
fi
