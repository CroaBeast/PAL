import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "com.bitaspire.pal.protocol"

dependencies {
    api(project(":protocol"))
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("PAL-Protocol-Mojang")
}
