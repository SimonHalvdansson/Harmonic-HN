plugins { application }

repositories { google(); mavenCentral() }
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-jvm:0.19.0")
    implementation("com.google.code.gson:gson:2.13.2")
}
application { mainClass.set("EmbedArticles") }
java { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
