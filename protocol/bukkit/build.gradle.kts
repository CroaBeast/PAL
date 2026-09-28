import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val spigotVersion: String by project

group = "com.bitaspire.pal.protocol"

dependencies {
    api(project(":protocol"))
    implementation(project(":protocol:mojang"))

    compileOnly("org.spigotmc:spigot-api:$spigotVersion")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("PAL-Protocol-Bukkit")
}
