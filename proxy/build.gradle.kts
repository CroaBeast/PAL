dependencies {
    api(project(":protocol:proxy"))

    implementation("org.xerial:sqlite-jdbc:3.53.1.0")
    implementation("com.mysql:mysql-connector-j:8.0.33")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")
    implementation("org.postgresql:postgresql:42.7.3")

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
