pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.jetbrains.kotlin.jvm")
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
        }
    }
}
dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "harmonic-article-category-prototypes"
