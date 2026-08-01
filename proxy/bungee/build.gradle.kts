import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

dependencies {
    implementation(project(":proxy"))

    compileOnly("net.md-5:bungeecord-api:1.21-R0.4")
    compileOnly("com.github.games647:fastlogin.core:1.12-SNAPSHOT")
    compileOnly("com.github.games647:fastlogin.bungee:1.12-SNAPSHOT")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("PAL-Bungee")
    archiveClassifier.set("")

    exclude(
        "META-INF/maven/**",
        "org/intellij/**",
        "org/jetbrains/**",
        "INFO_BIN",
        "INFO_SRC",
        "LICENSE*",
        "NOTICE*",
        "README*"
    )
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("bungee.yml") {
        expand(props)
    }
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}
