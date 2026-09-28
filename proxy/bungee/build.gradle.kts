import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val bstatsVersion: String by project
val fastloginVersion: String by project

dependencies {
    implementation(project(":proxy"))
    implementation("org.bstats:bstats-bungeecord:$bstatsVersion")

    compileOnly("net.md-5:bungeecord-api:1.21-R0.4")
    compileOnly("com.github.games647:fastlogin.core:$fastloginVersion")
    compileOnly("com.github.games647:fastlogin.bungee:$fastloginVersion")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("PAL-Bungee")
    archiveClassifier.set("")

    relocate("org.bstats", "com.bitaspire.libs.bstats")

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
