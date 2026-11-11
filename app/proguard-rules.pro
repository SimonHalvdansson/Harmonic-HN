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

# Match configureAndroidComposeFocus before R8 inlines Compose's focus branches. Revisit when
# https://issuetracker.google.com/issues/469958424 is fixed in our Compose UI version.
-assumevalues class androidx.compose.ui.ComposeUiFlags {
    public static boolean isBypassUnfocusableComposeViewEnabled return false;
}

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

# LiteRT-LM 0.19.1 ships no consumer rules for its JNI surface:
# https://github.com/google-ai-edge/LiteRT-LM/issues/3739
# Native code resolves configuration getters, input/response classes, constructors,
# and streaming callback methods by name. Keep implementations and nested classes
# too: preserving only embeddings (#405) still lets conversation creation abort.
# Keep this library intact until upstream ships complete consumer rules; names
# alone are insufficient because R8 can also remove or optimize these members.
-keep class com.google.ai.edge.litertlm.** { *; }

# ML Kit deserializes this structured suggestion result in Play builds.
-keep class com.simon.harmonichackernews.network.NanoQuestionSuggestions { *; }
-keep class com.simon.harmonichackernews.network.NanoQuestionSuggestions_GeneratedProvider { *; }

# Optional Play-only system embedding adapter, discovered by the common Android host.
-keep class com.simon.harmonichackernews.AndroidSystemArticleEmbeddingEngine {
    public <init>();
}
