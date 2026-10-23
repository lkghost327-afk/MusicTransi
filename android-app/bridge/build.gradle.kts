plugins {
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(17) }
sourceSets.main {
    kotlin.srcDir("../app/src/main/java/com/example/musictransi/source")
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("com.google.code.gson:gson:2.11.0")
}
application { mainClass.set("com.example.musictransi.bridge.MainKt") }
