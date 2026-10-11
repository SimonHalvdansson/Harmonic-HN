package com.simon.harmonichackernews.summary.local;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Runs the production JNI entry points with a recording engine; no model or network needed. */
public class GgufInference {
    private static native void nativeInitialize();
    private static native boolean nativeLoad(String path, int context);
    private static native boolean nativeStart(String system, String user, String prefix, int output);
    private static native String nativeLastError();

    private static String hex(String text) {
        return HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void expect(String expected) {
        if (!expected.equals(nativeLastError())) {
            throw new AssertionError("Expected " + expected + ", received " + nativeLastError());
        }
    }

    public static void main(String[] args) {
        System.load(args[0]);
        nativeInitialize();
        for (String text : new String[] {"", "ASCII", "café 日本語", "🚀 𐐀 🧑‍💻"}) {
            if (!nativeStart(text, text, text, 4)) throw new AssertionError("Start failed");
            expect(hex(text) + ":" + hex(text) + ":" + hex(text));
            if (!nativeLoad("/models/" + text + ".gguf", 512)) throw new AssertionError("Load failed");
            expect(hex("/models/" + text + ".gguf"));
        }
        if (!nativeStart("", "before\u0000after\uD800!\uDC00", "", 4)) throw new AssertionError("Start failed");
        expect(":" + hex("before\uFFFDafter\uFFFD!\uFFFD") + ":");
        System.out.println("Production JNI UTF-8: ASCII, BMP, supplementary characters, malformed UTF-16, NUL and model paths passed");
    }
}
