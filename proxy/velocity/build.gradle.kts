import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.java.TargetJvmVersion

dependencies {
    implementation(project(":proxy"))
    implementation("org.bstats:bstats-velocity:3.1.0")

    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    compileOnly("com.github.games647:fastlogin.core:1.12-SNAPSHOT")
    compileOnly("com.github.games647:fastlogin.velocity:1.12-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
}

configurations.configureEach {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("PAL-Velocity")
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

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}
