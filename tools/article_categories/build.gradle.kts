plugins {
    application
    alias(libs.plugins.kotlin.jvm)
}

repositories { google(); mavenCentral() }
dependencies {
    implementation(libs.litertlm.jvm)
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(libs.ksoup)
}
// Compile the production extractor itself, so corpus refreshes cannot drift from the app.
kotlin.sourceSets.main {
    kotlin.srcDir("../../core/src/commonMain/kotlin")
    kotlin.include("com/simon/harmonichackernews/network/HtmlDescriptionExtractor.kt")
    kotlin.include("com/simon/harmonichackernews/categorization/ArticleEmbeddingInput.kt")
}
kotlin.compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
application { mainClass.set(providers.gradleProperty("mainClass").orElse("EmbedArticles")) }
java { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
