import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

val bstatsVersion: String by project
val fastloginVersion: String by project
val floodgateVersion: String by project
val jbcryptVersion: String by project
val spigotVersion: String by project
val takionVersion: String by project

val apiProject = project(":api")
val protocolProject = project(":protocol")
val protocolMojangProject = project(":protocol:mojang")
val protocolBukkitProject = project(":protocol:bukkit")
val takionShaded: Configuration by configurations.creating
val legacyHashers: Configuration by configurations.creating
val bStats: Configuration by configurations.creating

dependencies {
    implementation(apiProject)
    implementation(protocolMojangProject)
    implementation(protocolBukkitProject)
    implementation("org.mindrot:jbcrypt:$jbcryptVersion")

    compileOnly("org.spigotmc:spigot-api:$spigotVersion")
    compileOnly("me.croabeast.takion:shaded:$takionVersion:all")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.github.games647:fastlogin.core:$fastloginVersion")
    compileOnly("com.github.games647:fastlogin.bukkit:$fastloginVersion")
    compileOnly("org.geysermc.floodgate:api:$floodgateVersion")
    compileOnly("org.bstats:bstats-bukkit:$bstatsVersion")

    takionShaded("me.croabeast.takion:shaded:$takionVersion:all")
    legacyHashers("org.mindrot:jbcrypt:$jbcryptVersion") { isTransitive = false }
    bStats("org.bstats:bstats-bukkit:$bstatsVersion")
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

    configurations = listOf(takionShaded, legacyHashers, bStats)

    relocate("me.croabeast", "com.bitaspire.libs")
    relocate("com.github.stefvanschie.inventoryframework", "com.bitaspire.libs.inventory")
    relocate("org.bstats", "com.bitaspire.libs.bstats")
    relocate("org.mindrot.jbcrypt", "com.bitaspire.libs.jbcrypt")

    exclude(
        "META-INF/**",
        "org/apache/commons/**",
        "org/intellij/**",
        "org/jetbrains/**",
        "INFO_BIN",
        "INFO_SRC",
        "LICENSE*",
        "NOTICE*",
        "README*"
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
