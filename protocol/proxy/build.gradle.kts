import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "com.bitaspire.pal.protocol"

dependencies {
    api(project(":protocol"))
    implementation(project(":protocol:mojang"))
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("PAL-Protocol-Proxy")
}
