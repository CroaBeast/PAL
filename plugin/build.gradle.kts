import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

val apiProject = project(":api")
val protocolProject = project(":protocol")
val protocolMojangProject = project(":protocol:mojang")
val protocolBukkitProject = project(":protocol:bukkit")
val takionShaded: Configuration by configurations.creating
val legacyHashers: Configuration by configurations.creating

dependencies {
    implementation(apiProject)
    implementation(protocolMojangProject)
    implementation(protocolBukkitProject)
    implementation("org.mindrot:jbcrypt:0.4")

    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("me.croabeast.takion:shaded:1.5.1:all")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.github.games647:fastlogin.core:1.12-SNAPSHOT")
    compileOnly("com.github.games647:fastlogin.bukkit:1.12-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")

    takionShaded("me.croabeast.takion:shaded:1.5.1:all")
    legacyHashers("org.mindrot:jbcrypt:0.4")
}

val apiMainOutput = apiProject.extensions.getByType<SourceSetContainer>()["main"].output
val protocolMainOutput = protocolProject.extensions.getByType<SourceSetContainer>()["main"].output
val protocolMojangMainOutput = protocolMojangProject.extensions.getByType<SourceSetContainer>()["main"].output
val protocolBukkitMainOutput = protocolBukkitProject.extensions.getByType<SourceSetContainer>()["main"].output

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("PAL")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn(apiProject.tasks.named("classes"))
    dependsOn(protocolProject.tasks.named("classes"))
    dependsOn(protocolMojangProject.tasks.named("classes"))
    dependsOn(protocolBukkitProject.tasks.named("classes"))
    from(apiMainOutput)
    from(protocolMainOutput)
    from(protocolMojangMainOutput)
    from(protocolBukkitMainOutput)

    configurations = listOf(takionShaded, legacyHashers)

    exclude(
        "META-INF/**",
        "org/apache/commons/**",
        "org/intellij/**",
        "org/jetbrains/**"
    )
}

tasks.assemble {
    dependsOn(tasks.named("shadowJar"))
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
