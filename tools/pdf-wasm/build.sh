#!/usr/bin/env bash
set -eo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
src="$here/sources"
dest="$root/resources/src/commonMain/composeResources/files/web/pdf/vendor/pdf.js/6.3.289/wasm"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fetch() {
    curl --fail --location --retry 3 --silent --show-error "$1" -o "$work/download"
    echo "$2  $work/download" | sha256sum -c -
}

emscripten() {
    "$work/emsdk/emsdk" install "$1"
    "$work/emsdk/emsdk" activate "$1"
    source "$work/emsdk/emsdk_env.sh"
}

build() {
    export OUTPUT="$work/out" BUILD_TYPE=wasm
    mkdir "$OUTPUT"
    fetch https://codeload.github.com/emscripten-core/emsdk/tar.gz/708dcbcc9ee23bc7d2dad44fc90db377e38b771c \
        845d3a5829787426e4da307dd3116dd5a187c92f72e2cda9a2634c9987488488
    mkdir "$work/emsdk"
    tar -xzf "$work/download" -C "$work/emsdk" --strip-components=1
    (
        emscripten 5.0.0
        cd "$src/jbig2"
        INPUT="$PWD/src" PDFIUM="$src/pdfium" sh -e compile.sh
    )
    (
        emscripten 5.0.6
        cd "$src/openjpeg-wrapper"
        export INPUT="$PWD/src" OPENJPEG="$work/openjpeg"
        mkdir "$OPENJPEG"
        tar -C "$src/openjpeg" --exclude=.git -cf - . | tar -C "$OPENJPEG" -xf -
        patch -d "$OPENJPEG" -p1 < 0001-Add-support-for-buffer-based-stream-see-https-github.patch
        export EMCC_CFLAGS="-ffile-prefix-map=$OPENJPEG=/tmp/openjpeg"
        sh compile_lib.sh
        sh compile.sh
    )
    rm -rf "$work/emsdk"
    export CARGO_HOME="$work/cargo" RUSTUP_HOME="$work/rustup"
    export PATH="$CARGO_HOME/bin:$PATH" RUSTUP_TOOLCHAIN=1.97.1
    fetch https://static.rust-lang.org/rustup/archive/1.28.2/x86_64-unknown-linux-gnu/rustup-init \
        20a06e644b0d9bd2fbdbfd52d42540bdde820ea7df86e92e533c073da0cdd43c
    mv "$work/download" "$work/rustup-init"
    chmod +x "$work/rustup-init"
    "$work/rustup-init" -y --no-modify-path --profile minimal --default-toolchain 1.97.1 --target wasm32-unknown-unknown
    cargo install wasm-bindgen-cli --version 0.2.126 --locked
    fetch https://github.com/wasm-bindgen/wasm-pack/releases/download/v0.15.0/wasm-pack-v0.15.0-x86_64-unknown-linux-musl.tar.gz \
        c09f971ecaed9a2efc80fdcea7a00ef6b53c7fadc8c57d1f61b53a6aa66b668a
    tar -xzf "$work/download" -C "$CARGO_HOME/bin" --strip-components=1 --wildcards '*/wasm-pack'
    (
        mkdir "$work/qcms"
        cp -R "$src/qcms/src" "$src/qcms/Cargo.toml" "$src/qcms/LICENSE" "$work/qcms/"
        cp "$here/Cargo.lock" "$work/qcms/Cargo.lock"
        cd "$work/qcms"
        export RUSTFLAGS="--remap-path-prefix=$CARGO_HOME/registry/src=/usr/local/cargo/registry/src"
        wasm-pack build --target web --release --no-typescript --no-pack --out-name qcms --out-dir "$work/qcms-out" --locked
    )
    cd "$OUTPUT"
    cp "$work/qcms-out/qcms_bg.wasm" .
    sha256sum jbig2.wasm openjpeg.wasm qcms_bg.wasm
    sha256sum -c <<'HASHES'
e6bee67724a7b5436fe8162638e3708cfc8d52b6342db69a49715e30ff27cfdc  jbig2.wasm
004a0e62db930ba9ff2a22212f4554d0bb57a0635a8287caf70f98117cee14ba  openjpeg.wasm
663d86126d5f5fcb1c61490f94353e2a8375660b8c5498ab3ebab5a34b08800e  qcms_bg.wasm
HASHES
    mkdir -p "$dest"
    install -m 644 jbig2.wasm openjpeg.wasm qcms_bg.wasm "$dest/"
}

build
