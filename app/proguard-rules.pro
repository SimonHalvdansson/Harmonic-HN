# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

#Got told to add this to resolve build errors
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn com.google.re2j.**

# Runtime implementations are instantiated after their on-demand feature is installed.
-keep class com.simon.harmonichackernews.localai.litert.LiteRtArticleEmbeddingBackend {
    public <init>();
}

-keep class com.simon.harmonichackernews.localai.litert.LiteRtInferenceEngine {
    public <init>();
}

# LiteRT-LM 0.19.1 has no consumer rules for its embedding JNI lookups (#405).
# GetNativeInputData looks up all three classes/getters, even for text-only input.
-keep class com.google.ai.edge.litertlm.InputData$Text {
    public java.lang.String getText();
}
-keep class com.google.ai.edge.litertlm.InputData$Audio {
    public byte[] getBytes();
}
-keep class com.google.ai.edge.litertlm.InputData$Image {
    public byte[] getBytes();
}
# Native code constructs the response; R8 cannot see this constructor call.
-keep class com.google.ai.edge.litertlm.EmbeddingResponse {
    public <init>(float[]);
}

# ML Kit deserializes this structured suggestion result in Play builds.
-keep class com.simon.harmonichackernews.network.NanoQuestionSuggestions { *; }
-keep class com.simon.harmonichackernews.network.NanoQuestionSuggestions_GeneratedProvider { *; }

# Optional Play-only system embedding adapter, discovered by the common Android host.
-keep class com.simon.harmonichackernews.AndroidSystemArticleEmbeddingEngine {
    public <init>();
}
