import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "com.bitaspire.pal.protocol"

dependencies {
    api(project(":protocol"))
    implementation(project(":protocol:mojang"))

    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("PAL-Protocol-Bukkit")
}
