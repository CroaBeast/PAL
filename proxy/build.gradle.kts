dependencies {
    api(project(":protocol:proxy"))

    // Resolved at runtime: bungee.yml "libraries" on BungeeCord, ProxyStorageDrivers on Velocity.
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.53.1.0") { isTransitive = false }

    compileOnly("com.github.games647:fastlogin.core:1.12-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("PAL-Proxy")
}
