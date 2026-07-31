import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "com.bitaspire.pal.protocol"

subprojects {
    group = "com.bitaspire.pal.protocol"
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("PAL-Protocol")
}
